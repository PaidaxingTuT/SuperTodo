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
 * <p>负责把 Web 端已经同步给原生的小部件数据，转换成"三日内截止"的系统通知与实时活动胶囊。
 * 放在原生侧执行是因为 WebView 切到后台后 JS 定时器会被系统冻结，
 * 只有原生的 AlarmManager 和生命周期回调才能可靠触发。</p>
 */
public final class DeadlineReminderManager {

    /** 每日扫描的广播动作，由 DeadlineReminderReceiver 接收。 */
    public static final String ACTION_CHECK_DEADLINE = "com.dax.supertodo.ACTION_CHECK_DEADLINE";

    private static final String PREF_NAME = "supertodo_reminder_prefs";
    private static final String KEY_ENABLED = "reminder_enabled";
    private static final String KEY_LAST_DAY = "reminder_last_day";

    /** 使用全新的高优横幅渠道 ID，避免旧版本已创建的渠道缓存导致不弹横幅。 */
    private static final String CHANNEL_ID = "supertodo_deadline_live_v2";
    private static final int NOTIFICATION_ID = 20261106;
    private static final int ALARM_REQUEST_CODE = 20261106;

    private static final int REMIND_HOUR = 8;
    private static final int REMIND_MINUTE = 30;

    /** 提醒窗口天数：从今天起 3 天内到期（含今天与已逾期）的未完成事项都会进入提醒。 */
    private static final int REMIND_WINDOW_DAYS = 3;

    /** 逾期包容天数：逾期 7 天以内的未完成事项一并提醒，防止刚过期的要事被遗漏。 */
    private static final int OVERDUE_WINDOW_DAYS = -7;

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
     * 判断用户是否开启了截止提醒（默认开启，确保安装后即可生效）。
     *
     * @param context 任意上下文
     * @return true 表示已开启
     */
    public static boolean isEnabled(Context context) {
        return getPrefs(context).getBoolean(KEY_ENABLED, true);
    }

    /**
     * 开关截止提醒。
     *
     * <p>开启时立即排下一次闹钟并当场执行一次扫描提醒；关闭时取消闹钟和已展示的通知，
     * 避免用户关闭开关后状态栏里还残留一条旧提醒。</p>
     *
     * @param context 任意上下文
     * @param enabled 是否开启
     */
    public static void setEnabled(Context context, boolean enabled) {
        getPrefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply();
        if (enabled) {
            scheduleNext(context);
            checkAndNotifyOnLaunch(context);
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
     * 退化为 setAndAllowWhileIdle。</p>
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
        try {
            if (Build.VERSION.SDK_INT >= 31 && !alarmManager.canScheduleExactAlarms()) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, operation);
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, operation);
            }
        } catch (SecurityException ignore) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, operation);
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
     * 数据发生变化（Web 端 syncData 同步完成）时立即刷新提醒。
     *
     * <p>移除原本对 08:00-21:00 的硬拦截，保证用户在任何时间新增或修改三日内截止事项、
     * 或冷启动 WebView 首次同步完数据时都能立刻看到提醒。</p>
     *
     * @param context 任意上下文
     */
    public static void onDataChanged(Context context) {
        if (!isEnabled(context)) {
            return;
        }
        checkAndNotify(context, true);
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
        checkAndNotify(context, true);
    }

    /**
     * 手动触发测试提醒（供设置页「立即测试提醒」按钮调用）。
     *
     * <p>若当前列表中有 3 日内截止的事项，则直接展示真实提醒并弹出横幅；
     * 若当前没有任何近期截止事项，则构造一条演示提醒发送，方便验证通知权限与流体云胶囊样式。</p>
     *
     * @param context 任意上下文
     * @return 发送结果描述，供前端弹窗或 Toast 提示给用户
     */
    public static String triggerTestReminder(Context context) {
        if (context == null) {
            return "上下文不可用";
        }
        if (!hasNotificationPermission(context)) {
            return "NO_PERMISSION";
        }
        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager == null) {
            return "系统通知服务不可用";
        }
        ensureChannel(context);

        List<DueItem> dueItems = collectDueItems(context);
        boolean isDemo = false;
        if (dueItems.isEmpty()) {
            isDemo = true;
            dueItems = new ArrayList<>();
            dueItems.add(new DueItem("示例：提交项目周报", 0, 5));
            dueItems.add(new DueItem("示例：缴纳水电燃气费", 1, 4));
            dueItems.add(new DueItem("示例：取快递包裹", 2, 3));
        }

        notificationManager.cancel(NOTIFICATION_ID);
        Notification notification = buildNotification(context, dueItems, true);
        notificationManager.notify(NOTIFICATION_ID, notification);
        if (isDemo) {
            return "DEMO_SENT";
        }
        return "REAL_SENT:" + dueItems.size();
    }

    /**
     * 扫描当前数据并发送（或更新）截止提醒通知。
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
     * @param forceAlert true 表示强制重新响铃并弹横幅（应用打开与测试时使用）
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
        boolean shouldAlert = forceAlert || !today.equals(lastDay);
        if (shouldAlert) {
            notificationManager.cancel(NOTIFICATION_ID);
        }

        Notification notification = buildNotification(context, dueItems, shouldAlert);
        notificationManager.notify(NOTIFICATION_ID, notification);
        getPrefs(context).edit().putString(KEY_LAST_DAY, today).apply();
    }

    /**
     * 构建截止提醒通知。
     *
     * <p>针对 Android 16 (API 36+) 启用 ProgressStyle + 倒计时 Chronometer + Promoted Ongoing，
     * 避免使用会导致胶囊降级的 BigTextStyle；针对 API 35 及以下使用 BigTextStyle 展开多行明细并确保高优横幅弹出。</p>
     *
     * @param context 任意上下文
     * @param dueItems 已按截止时间排序的事项
     * @param alertNow 是否允许本次通知触发横幅与声音震动
     * @return 可发送的 Notification
     */
    private static Notification buildNotification(
            Context context,
            List<DueItem> dueItems,
            boolean alertNow) {
        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= 26) {
            builder = new Notification.Builder(context, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(context);
        }

        String title = buildTitle(dueItems);
        String summary = buildSummary(dueItems);
        String expanded = buildExpandedText(dueItems);
        DueItem nearest = dueItems.get(0);
        long targetWhen = resolveTargetTimestamp(nearest.getDaysLeft());

        PendingIntent contentIntent = buildContentIntent(context);
        builder.setSmallIcon(R.drawable.ic_stat_deadline)
                .setContentTitle(title)
                .setContentText(summary)
                .setSubText("超级清单 · 截止提醒")
                .setContentIntent(contentIntent)
                .setOngoing(true)
                .setAutoCancel(false)
                .setOnlyAlertOnce(!alertNow)
                .setWhen(targetWhen)
                .setShowWhen(true)
                .setUsesChronometer(true)
                .setCategory(Notification.CATEGORY_REMINDER)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setDefaults(alertNow ? Notification.DEFAULT_ALL : 0)
                .setPriority(Notification.PRIORITY_MAX);

        if (Build.VERSION.SDK_INT >= 24) {
            builder.setChronometerCountDown(true);
        }

        String criticalText = buildCriticalChipText(dueItems);
        boolean promotedApplied = applyPromotedLiveUpdate(builder, dueItems, criticalText);
        if (!promotedApplied) {
            builder.setStyle(new Notification.BigTextStyle()
                    .setBigContentTitle(title)
                    .bigText(expanded));
        }

        return builder.build();
    }

    /**
     * 计算最近一条事项截止日当天 23:59:59 的毫秒时间戳，供状态栏胶囊倒计时使用。
     *
     * @param daysLeft 距离今天的天数
     * @return 目标时间戳
     */
    private static long resolveTargetTimestamp(int daysLeft) {
        Calendar cal = Calendar.getInstance();
        int offset = Math.max(0, daysLeft);
        cal.add(Calendar.DAY_OF_YEAR, offset);
        cal.set(Calendar.HOUR_OF_DAY, 23);
        cal.set(Calendar.MINUTE, 59);
        cal.set(Calendar.SECOND, 59);
        return cal.getTimeInMillis();
    }

    /**
     * 构造状态栏胶囊上显示的简短关键文本（要求简短，不超过 7 个字符）。
     *
     * @param dueItems 待办列表
     * @return 短文本，如 "今日 2项" 或 "3日内 4项"
     */
    private static String buildCriticalChipText(List<DueItem> dueItems) {
        DueItem first = dueItems.get(0);
        if (first.getDaysLeft() < 0) {
            return "逾期 " + dueItems.size() + "项";
        }
        if (first.getDaysLeft() == 0) {
            return "今日 " + dueItems.size() + "项";
        }
        if (first.getDaysLeft() == 1) {
            return "明日 " + dueItems.size() + "项";
        }
        return dueItems.size() + "项待办";
    }

    /**
     * 在 Android 16 (API 36+) 上通过反射应用 ProgressStyle 与 setRequestPromotedOngoing，
     * 请求 ColorOS 16+ / Android 16 系统将通知提升为流体云/实时活动胶囊。
     *
     * @param builder 通知构建器
     * @param dueItems 到期事项列表
     * @param criticalText 胶囊短文本
     * @return true 表示成功应用了 API 36 实时活动样式
     */
    private static boolean applyPromotedLiveUpdate(
            Notification.Builder builder,
            List<DueItem> dueItems,
            String criticalText) {
        if (Build.VERSION.SDK_INT < 36) {
            return false;
        }
        try {
            builder.getClass()
                    .getMethod("setRequestPromotedOngoing", boolean.class)
                    .invoke(builder, true);
            builder.getClass()
                    .getMethod("setShortCriticalText", String.class)
                    .invoke(builder, criticalText);

            Class<?> progressStyleClass = Class.forName("android.app.Notification$ProgressStyle");
            Object progressStyle = progressStyleClass.getDeclaredConstructor().newInstance();
            int minDays = Math.max(0, Math.min(REMIND_WINDOW_DAYS, dueItems.get(0).getDaysLeft()));
            int progress = Math.max(15, (REMIND_WINDOW_DAYS - minDays + 1) * 25);
            progressStyleClass.getMethod("setProgress", int.class)
                    .invoke(progressStyle, Math.min(100, progress));
            try {
                progressStyleClass.getMethod("setStyledByProgress", boolean.class)
                        .invoke(progressStyle, false);
            } catch (Exception ignore) {
                // 部分预览版 SDK 方法签名差异可忽略
            }
            builder.getClass()
                    .getMethod("setStyle", Notification.Style.class)
                    .invoke(builder, progressStyle);
            return true;
        } catch (Exception ignore) {
            return false;
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
                if (daysLeft < OVERDUE_WINDOW_DAYS || daysLeft > REMIND_WINDOW_DAYS) {
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
     * @return 单条时显示"明天截止 · 交房租"，多条时显示"3 天内截止 · 3 项"
     */
    private static String buildTitle(List<DueItem> dueItems) {
        if (dueItems.size() == 1) {
            DueItem item = dueItems.get(0);
            String label = item.getDaysLeft() < 0
                    ? dayLabel(item.getDaysLeft())
                    : dayLabel(item.getDaysLeft()) + "截止";
            return label + " · " + truncate(item.getTitle(), 14);
        }
        DueItem first = dueItems.get(0);
        if (first.getDaysLeft() <= 0) {
            return "今日及逾期 · 共 " + dueItems.size() + " 项近期截止";
        }
        return REMIND_WINDOW_DAYS + " 天内截止 · 共 " + dueItems.size() + " 项";
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
     * @return 已逾期 N 天 / 今天 / 明天 / 后天 / N 天后
     */
    private static String dayLabel(int daysLeft) {
        if (daysLeft < 0) {
            return "已逾期" + Math.abs(daysLeft) + "天";
        }
        if (daysLeft == 0) {
            return "今天";
        }
        if (daysLeft == 1) {
            return "明天";
        }
        if (daysLeft == 2) {
            return "后天";
        }
        return daysLeft + "天后";
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
     * 创建高优先级通知渠道。Android 8 之后必须先有渠道才能发通知。
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
                "三日内截止与实时提醒",
                NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription("事项临近截止日期（3天内）或打开应用时弹出横幅与状态栏提醒");
        channel.enableVibration(true);
        channel.enableLights(true);
        channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
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
