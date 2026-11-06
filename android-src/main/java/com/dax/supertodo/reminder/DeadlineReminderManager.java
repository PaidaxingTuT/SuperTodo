package com.dax.supertodo.reminder;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import com.dax.supertodo.MainActivity;
import com.dax.supertodo.R;
import com.dax.supertodo.widget.WidgetDataManager;
import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 截止提醒管理器。
 *
 * <p>负责把 Web 端已经同步给原生的小部件数据，转换成"三日内截止"的系统通知。
 * 之所以放在原生侧而不是 JS 里，是因为 WebView 切到后台后 JS 定时器会被系统冻结，
 * 只有原生的 AlarmManager 才能在应用未打开时按时提醒。</p>
 */
public final class DeadlineReminderManager {

    /** 每日扫描的广播动作，由 DeadlineReminderReceiver 接收。 */
    public static final String ACTION_CHECK_DEADLINE = "com.dax.supertodo.ACTION_CHECK_DEADLINE";

    private static final String PREF_NAME = "supertodo_reminder_prefs";
    private static final String KEY_ENABLED = "reminder_enabled";
    private static final String KEY_LAST_DAY = "reminder_last_day";

    private static final String CHANNEL_ID = "supertodo_deadline_reminder";
    private static final int NOTIFICATION_ID = 20261106;
    private static final int ALARM_REQUEST_CODE = 20261106;

    private static final int REMIND_HOUR = 8;
    private static final int REMIND_MINUTE = 30;
    private static final int ACTIVE_END_HOUR = 21;

    /** 提醒窗口天数：从今天起 3 天内到期的事项会进入同一条提醒。 */
    private static final int REMIND_WINDOW_DAYS = 3;

    private static final int MAX_LISTED_ITEMS = 5;

    private static final String DUE_PATTERN = "yyyy-MM-dd";
    private static final long ONE_DAY_MILLIS = 24L * 60L * 60L * 1000L;

    private DeadlineReminderManager() {
    }

    /**
     * 读取提醒相关的偏好设置。
     *
     * @param context 任意上下文
     * @return 提醒专用的 SharedPreferences
     */
    public static SharedPreferences getPrefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    /**
     * 判断用户是否开启了截止提醒。
     *
     * @param context 任意上下文
     * @return true 表示已开启
     */
    public static boolean isEnabled(Context context) {
        return getPrefs(context).getBoolean(KEY_ENABLED, false);
    }

    /**
     * 开关截止提醒。
     *
     * <p>开启时立即排下一次闹钟，关闭时取消闹钟和已展示的通知，
     * 避免用户关闭开关后状态栏里还残留一条旧提醒。</p>
     *
     * @param context 任意上下文
     * @param enabled 是否开启
     */
    public static void setEnabled(Context context, boolean enabled) {
        getPrefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply();
        if (enabled) {
            scheduleNext(context);
        } else {
            cancelSchedule(context);
            cancelNotification(context);
            getPrefs(context).edit().remove(KEY_LAST_DAY).apply();
        }
    }

    /**
     * 检查系统是否授予了通知权限（Android 13 起为运行时权限）。
     *
     * @param context 任意上下文
     * @return true 表示可以发送通知
     */
    public static boolean hasNotificationPermission(Context context) {
        if (Build.VERSION.SDK_INT < 33) {
            return true;
        }
        return context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * 排出下一次每日扫描的闹钟。
     *
     * <p>优先使用精确闹钟，保证在 08:30 附近触发；当系统未授予精确闹钟权限时
     * 退化为 setAndAllowWhileIdle，允许最多几十分钟的延迟。</p>
     *
     * @param context 任意上下文
     */
    public static void scheduleNext(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            return;
        }
        PendingIntent operation = buildAlarmIntent(context);
        long triggerAt = nextTriggerMillis();
        if (Build.VERSION.SDK_INT >= 31 && !alarmManager.canScheduleExactAlarms()) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, operation);
        } else {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, operation);
        }
    }

    /**
     * 取消已排定的每日扫描闹钟。
     *
     * @param context 任意上下文
     */
    public static void cancelSchedule(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            return;
        }
        alarmManager.cancel(buildAlarmIntent(context));
    }

    /**
     * 数据发生变化时补一次检查。
     *
     * <p>只用于白天的场景：比如用户早上第一次打开应用、或新建了一个明天截止的事项。
     * 深夜打开应用不会触发，避免打扰。同一天内重复调用只会刷新通知内容，
     * 由 setOnlyAlertOnce 保证不会反复响铃。</p>
     *
     * @param context 任意上下文
     */
    public static void onDataChanged(Context context) {
        if (!isEnabled(context)) {
            return;
        }
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (hour < REMIND_HOUR || hour >= ACTIVE_END_HOUR) {
            return;
        }
        checkAndNotify(context);
    }

    /**
     * 每次回到前台时刷新提醒。
     *
     * <p>测试期行为：无论今天是否已经提醒过，都先撤掉旧通知再重新发送，
     * 保证每次打开应用都能看到提醒效果。窗口内没有事项时不会产生通知。</p>
     *
     * @param context 任意上下文
     */
    public static void checkAndNotifyOnLaunch(Context context) {
        if (!isEnabled(context)) {
            return;
        }
        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            notificationManager.cancel(NOTIFICATION_ID);
        }
        checkAndNotify(context, true);
    }

    /**
     * 扫描当前数据并发送（或更新）截止提醒通知。
     *
     * <p>窗口内没有事项时会主动清掉旧通知，防止昨天的提醒一直挂在状态栏上。
     * 同一天重复调用只会更新内容，不会重复响铃。</p>
     *
     * @param context 任意上下文
     */
    public static void checkAndNotify(Context context) {
        checkAndNotify(context, false);
    }

    /**
     * 扫描并发送截止提醒的内部实现。
     *
     * @param context 任意上下文
     * @param forceAlert true 表示强制重新响铃（应用每次打开时的测试行为）
     */
    private static void checkAndNotify(Context context, boolean forceAlert) {
        if (!isEnabled(context)) {
            return;
        }
        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager == null) {
            return;
        }
        List<DueItem> dueItems = collectDueItems(context);
        if (dueItems.isEmpty()) {
            cancelNotification(context);
            return;
        }
        if (!hasNotificationPermission(context)) {
            return;
        }
        ensureChannel(context);

        String today = todayString();
        String lastDay = getPrefs(context).getString(KEY_LAST_DAY, "");
        if (forceAlert || !today.equals(lastDay)) {
            // 强制提醒或跨越到新的一天时先撤掉旧通知，否则同 ID 的更新不会再次响铃。
            notificationManager.cancel(NOTIFICATION_ID);
        }

        Notification notification = buildNotification(context, dueItems);
        notificationManager.notify(NOTIFICATION_ID, notification);
        getPrefs(context).edit().putString(KEY_LAST_DAY, today).apply();
    }

    /**
     * 构建列表用的通知对象。
     *
     * @param context 任意上下文
     * @param dueItems 已按截止时间排序的事项
     * @return 可发送的 Notification
     */
    private static Notification buildNotification(Context context, List<DueItem> dueItems) {
        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= 26) {
            builder = new Notification.Builder(context, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(context);
        }

        String title = buildTitle(dueItems);
        String summary = buildSummary(dueItems);
        String expanded = buildExpandedText(dueItems);

        builder.setSmallIcon(R.drawable.ic_stat_deadline)
                .setContentTitle(title)
                .setContentText(summary)
                .setStyle(new Notification.BigTextStyle().bigText(expanded))
                .setContentIntent(buildContentIntent(context))
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setCategory(Notification.CATEGORY_REMINDER)
                .setPriority(Notification.PRIORITY_HIGH);

        applyPromotedOngoing(builder, dueItems.size() + " 项");
        return builder.build();
    }

    /**
     * 通过反射请求系统把通知提升为实时活动。
     *
     * <p>这些 API 只在 Android 16（API 36）存在，而工程的 compileSdk 可能低于 36，
     * 直接调用会编译失败，因此这里统一走反射。低版本系统上静默跳过，
     * 通知仍然以普通常驻通知的形式展示。</p>
     *
     * @param builder 通知构建器
     * @param criticalText 状态栏胶囊上显示的短文本
     */
    private static void applyPromotedOngoing(Notification.Builder builder, String criticalText) {
        if (Build.VERSION.SDK_INT < 36) {
            return;
        }
        try {
            builder.getClass()
                    .getMethod("setRequestPromotedOngoing", boolean.class)
                    .invoke(builder, true);
            builder.getClass()
                    .getMethod("setShortCriticalText", String.class)
                    .invoke(builder, criticalText);
        } catch (Exception ignore) {
            // 系统版本或厂商裁剪导致方法缺失时退回普通通知，不影响提醒本身。
        }
    }

    /**
     * 从原生缓存的数据里筛选出窗口内的未完成事项。
     *
     * @param context 任意上下文
     * @return 按截止日期升序、重要度降序排列的事项列表
     */
    private static List<DueItem> collectDueItems(Context context) {
        List<DueItem> result = new ArrayList<>();
        String raw = WidgetDataManager.getWidgetData(context);
        if (raw == null || raw.isEmpty()) {
            return result;
        }
        try {
            JSONObject root = new JSONObject(raw);
            JSONArray items = root.optJSONArray("items");
            if (items == null) {
                return result;
            }
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.optJSONObject(i);
                if (item == null) {
                    continue;
                }
                if (item.optBoolean("done", false)) {
                    continue;
                }
                String due = item.optString("due", "");
                if (!due.matches("\\d{4}-\\d{2}-\\d{2}")) {
                    continue;
                }
                int daysLeft = daysUntil(due);
                if (daysLeft < 0 || daysLeft > REMIND_WINDOW_DAYS) {
                    continue;
                }
                result.add(new DueItem(
                        item.optString("title", ""),
                        daysLeft,
                        item.optInt("star", 0)));
            }
        } catch (Exception ignore) {
            return result;
        }
        Collections.sort(result, Comparator
                .comparingInt(DueItem::getDaysLeft)
                .thenComparing(Comparator.comparingInt(DueItem::getStar).reversed()));
        return result;
    }

    /**
     * 计算目标日期距今天还有几天。
     *
     * @param due yyyy-MM-dd 格式的截止日期
     * @return 0 表示今天，1 表示明天，负数表示已逾期
     */
    private static int daysUntil(String due) {
        try {
            SimpleDateFormat format = new SimpleDateFormat(DUE_PATTERN, Locale.US);
            format.setLenient(false);
            Date dueDate = format.parse(due);
            if (dueDate == null) {
                return Integer.MAX_VALUE;
            }
            Calendar dueCalendar = Calendar.getInstance();
            dueCalendar.setTime(dueDate);
            clearTime(dueCalendar);
            Calendar todayCalendar = Calendar.getInstance();
            clearTime(todayCalendar);
            long diff = dueCalendar.getTimeInMillis() - todayCalendar.getTimeInMillis();
            return (int) Math.round((double) diff / ONE_DAY_MILLIS);
        } catch (Exception ignore) {
            return Integer.MAX_VALUE;
        }
    }

    /**
     * 把日历的时间部分清零，只保留日期，避免时分秒影响天数计算。
     *
     * @param calendar 待处理的日历
     */
    private static void clearTime(Calendar calendar) {
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
    }

    /**
     * 构造通知标题。
     *
     * @param dueItems 窗口内事项
     * @return 单条时显示"明天截止：交房租"，多条时显示"3 天内截止 · 3 项"
     */
    private static String buildTitle(List<DueItem> dueItems) {
        if (dueItems.size() == 1) {
            DueItem item = dueItems.get(0);
            return dayLabel(item.getDaysLeft()) + "截止 · " + truncate(item.getTitle(), 14);
        }
        return REMIND_WINDOW_DAYS + " 天内截止 · " + dueItems.size() + " 项";
    }

    /**
     * 构造通知折叠时的摘要文本。
     *
     * @param dueItems 窗口内事项
     * @return 最多三条事项的摘要
     */
    private static String buildSummary(List<DueItem> dueItems) {
        StringBuilder builder = new StringBuilder();
        int limit = Math.min(dueItems.size(), 3);
        for (int i = 0; i < limit; i++) {
            if (i > 0) {
                builder.append(" · ");
            }
            DueItem item = dueItems.get(i);
            builder.append(truncate(item.getTitle(), 10));
            builder.append("（").append(dayLabel(item.getDaysLeft())).append("）");
        }
        if (dueItems.size() > limit) {
            builder.append(" 等 ").append(dueItems.size()).append(" 项");
        }
        return builder.toString();
    }

    /**
     * 构造展开后的明细文本，每行一条事项。
     *
     * @param dueItems 窗口内事项
     * @return 明细文本
     */
    private static String buildExpandedText(List<DueItem> dueItems) {
        StringBuilder builder = new StringBuilder();
        int limit = Math.min(dueItems.size(), MAX_LISTED_ITEMS);
        for (int i = 0; i < limit; i++) {
            DueItem item = dueItems.get(i);
            if (i > 0) {
                builder.append("\n");
            }
            builder.append("· ")
                    .append(truncate(item.getTitle(), 18))
                    .append("（")
                    .append(dayLabel(item.getDaysLeft()))
                    .append("）");
        }
        if (dueItems.size() > limit) {
            builder.append("\n· 还有 ").append(dueItems.size() - limit).append(" 项");
        }
        return builder.toString();
    }

    /**
     * 把剩余天数转换成用户更容易理解的文案。
     *
     * @param daysLeft 剩余天数
     * @return 今天 / 明天 / N 天后
     */
    private static String dayLabel(int daysLeft) {
        if (daysLeft <= 0) {
            return "今天";
        }
        if (daysLeft == 1) {
            return "明天";
        }
        return daysLeft + " 天后";
    }

    /**
     * 截断过长的标题，避免通知文本被系统截断得很难看。
     *
     * @param text 原始文本
     * @param maxLength 最大字数
     * @return 截断后的文本
     */
    private static String truncate(String text, int maxLength) {
        String value = text == null ? "" : text.trim();
        if (value.isEmpty()) {
            return "未命名事项";
        }
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "…";
    }

    /**
     * 创建通知渠道。Android 8 之后必须先有渠道才能发通知。
     *
     * @param context 任意上下文
     */
    private static void ensureChannel(Context context) {
        if (Build.VERSION.SDK_INT < 26) {
            return;
        }
        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager == null || notificationManager.getNotificationChannel(CHANNEL_ID) != null) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "截止提醒",
                NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription("事项临近截止日期时提醒");
        channel.enableVibration(true);
        notificationManager.createNotificationChannel(channel);
    }

    /**
     * 构造点击通知后打开应用的 PendingIntent。
     *
     * @param context 任意上下文
     * @return 指向主界面的 PendingIntent
     */
    private static PendingIntent buildContentIntent(Context context) {
        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        return PendingIntent.getActivity(context, ALARM_REQUEST_CODE, intent, flags);
    }

    /**
     * 构造每日扫描用的闹钟 PendingIntent。
     *
     * @param context 任意上下文
     * @return 指向 DeadlineReminderReceiver 的 PendingIntent
     */
    private static PendingIntent buildAlarmIntent(Context context) {
        Intent intent = new Intent(context, DeadlineReminderReceiver.class);
        intent.setAction(ACTION_CHECK_DEADLINE);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        return PendingIntent.getBroadcast(context, ALARM_REQUEST_CODE, intent, flags);
    }

    /**
     * 取消已经展示的截止提醒通知。
     *
     * @param context 任意上下文
     */
    private static void cancelNotification(Context context) {
        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            notificationManager.cancel(NOTIFICATION_ID);
        }
    }

    /**
     * 计算下一次扫描的时间点（今天或明天 08:30）。
     *
     * @return 下一次触发的时间戳
     */
    private static long nextTriggerMillis() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, REMIND_HOUR);
        calendar.set(Calendar.MINUTE, REMIND_MINUTE);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        if (calendar.getTimeInMillis() <= System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_YEAR, 1);
        }
        return calendar.getTimeInMillis();
    }

    /**
     * 获取今天的 yyyy-MM-dd 字符串，用于按天去重。
     *
     * @return 今天的日期字符串
     */
    private static String todayString() {
        return new SimpleDateFormat(DUE_PATTERN, Locale.US).format(new Date());
    }

    /**
     * 窗口内的一条待提醒事项。
     */
    private static final class DueItem {
        private final String title;
        private final int daysLeft;
        private final int star;

        DueItem(String title, int daysLeft, int star) {
            this.title = title;
            this.daysLeft = daysLeft;
            this.star = star;
        }

        String getTitle() {
            return title;
        }

        int getDaysLeft() {
            return daysLeft;
        }

        int getStar() {
            return star;
        }
    }
}
