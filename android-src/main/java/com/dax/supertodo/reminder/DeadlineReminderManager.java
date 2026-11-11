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
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.os.Build;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
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
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

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
    private static final int MAX_WINDOW_DAYS = 365;
    private static final int MAX_DISPLAY_ITEMS = 4;
    private static final int COMPACT_CARD_MAX_ITEMS = 2;
    private static final float DENSE_CARD_TEXT_SCALE = 0.92f;
    private static final int COLLAPSED_PLACEHOLDER_PX = 1;

    /** 防止 ColorOS 在 contentText 为空时把 BigText 正文回退成胶囊第二行跑马灯。 */
    private static final String EMPTY_CHIP_TEXT = new String(Character.toChars(0x200B));

    private static final int COLOR_OVERDUE_LIGHT = 0xFFD93025;
    private static final int COLOR_OVERDUE_DARK = 0xFFFF7B72;
    private static final int COLOR_TODAY_LIGHT = 0xFFE37400;
    private static final int COLOR_TODAY_DARK = 0xFFFFB74D;
    private static final int COLOR_TOMORROW_LIGHT = 0xFF0B57D0;
    private static final int COLOR_TOMORROW_DARK = 0xFF8AB4F8;
    private static final int COLOR_SOON_LIGHT = 0xFF00796B;
    private static final int COLOR_SOON_DARK = 0xFF80CBC4;
    private static final int COLOR_LATER_LIGHT = 0xFF5F6368;
    private static final int COLOR_LATER_DARK = 0xFF9AA0A6;

    /** 逾期包容天数：逾期 7 天以内的未完成事项一并提醒，防止刚过期的要事被遗漏。 */
    private static final int OVERDUE_WINDOW_DAYS = -7;

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
     * 数据保存后刷新提醒, 让应用和小组件完成操作都能及时移除已完成事项。
     *
     * <p>普通数据同步只更新当天已有通知, 避免每次勾选或保存设置都重新响铃。</p>
     *
     * @param context 任意上下文
     */
    public static void onDataChanged(Context context) {
        if (!isEnabled(context)) {
            return;
        }
        checkAndNotify(context, false);
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
     * <p>使用 BigTextStyle 展示逐项截止日期, 并为 API 36+ 的胶囊提供数量摘要。
     * 不使用进度条与倒计时, 避免将待办清单误显示为行程进度。</p>
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

        String chipText = buildChipText(dueItems);
        String expandedTitle = buildTitle(dueItems);
        JSONObject options = getReminderOptions(context);
        CharSequence details = buildStyledExpandedText(context, dueItems, options);

        PendingIntent contentIntent = buildContentIntent(context);
        builder.setSmallIcon(R.drawable.ic_stat_deadline)
                .setColor(context.getColor(R.color.widget_primary))
                .setContentTitle(chipText)
                .setContentText(buildCollapsedPlaceholder())
                .setStyle(new Notification.BigTextStyle()
                        .setBigContentTitle(expandedTitle)
                        .bigText(details))
                .setContentIntent(contentIntent)
                .setOngoing(true)
                .setAutoCancel(false)
                .setOnlyAlertOnce(!alertNow)
                .setWhen(0)
                .setShowWhen(false)
                .setUsesChronometer(false)
                .setCategory(Notification.CATEGORY_REMINDER)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setDefaults(alertNow ? Notification.DEFAULT_ALL : 0)
                .setPriority(Notification.PRIORITY_MAX);

        applyPromotedLiveUpdate(builder, chipText);
        return builder.build();
    }

    /**
     * 保留胶囊数量摘要, 让展开后的系统卡片使用正文展示任务明细。
     *
     * @param builder 通知构建器
     * @param criticalText 收起状态显示的数量
     */
    private static void applyPromotedLiveUpdate(
            Notification.Builder builder,
            String criticalText) {
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
        } catch (ReflectiveOperationException exception) {
            android.util.Log.w("DeadlineReminder", "系统不支持实时活动数量摘要", exception);
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
            JSONObject options = root.optJSONObject("deadlineReminderOptions");
            int windowDays = options == null ? REMIND_WINDOW_DAYS
                    : Math.max(0, Math.min(MAX_WINDOW_DAYS, options.optInt("days", REMIND_WINDOW_DAYS)));
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
                if (daysLeft < OVERDUE_WINDOW_DAYS || daysLeft > windowDays) {
                    continue;
                }
                result.add(new DueItem(
                        item.optString("id", ""),
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
     * 生成缩小流体胶囊右侧的极简短文案, 控制在 4~5 个字以内避免胶囊过长。
     *
     * @param dueItems 窗口内事项
     * @return 形如 "2项待办" 的紧凑文案
     */
    private static String buildChipText(List<DueItem> dueItems) {
        return dueItems.size() + "项待办";
    }

    /**
     * 生成展开态流体云与通知卡片的完整标题。
     *
     * @param dueItems 窗口内事项
     * @return 形如 "剩余2项待办" 的展开标题
     */
    private static String buildTitle(List<DueItem> dueItems) {
        return "剩余" + dueItems.size() + "项待办";
    }

    /**
     * 构造 1px 字号的零宽占位符: 既让 ColorOS 判定 contentText 非空从而不把正文回退成胶囊跑马灯,
     * 又尽可能压缩熄屏通知第二行的高度, 避免把第一行标题顶到左上角。
     *
     * @return 带 1px 尺寸约束的零宽字符
     */
    private static CharSequence buildCollapsedPlaceholder() {
        SpannableString placeholder = new SpannableString(EMPTY_CHIP_TEXT);
        placeholder.setSpan(
                new AbsoluteSizeSpan(COLLAPSED_PLACEHOLDER_PX, false),
                0,
                EMPTY_CHIP_TEXT.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return placeholder;
    }

    /**
     * 复用同步缓存里的配置, 避免后台扫描依赖 WebView 存活。
     *
     * @param context 任意上下文
     * @return 正文展示配置, 无配置时返回默认空对象
     */
    private static JSONObject getReminderOptions(Context context) {
        try {
            JSONObject root = new JSONObject(WidgetDataManager.getWidgetData(context));
            JSONObject options = root.optJSONObject("deadlineReminderOptions");
            return options == null ? new JSONObject() : options;
        } catch (org.json.JSONException exception) {
            return new JSONObject();
        }
    }

    /**
     * 按条数上限和用户勾选顺序筛出要在流体云正文中展示的事项。
     *
     * @param dueItems 窗口内全部事项
     * @param options 用户的条数和手动选择配置
     * @return 最多 4 条待展示事项
     */
    private static List<DueItem> selectDisplayItems(List<DueItem> dueItems, JSONObject options) {
        int maxItems = Math.max(1, Math.min(MAX_DISPLAY_ITEMS,
                options.optInt("maxItems", MAX_DISPLAY_ITEMS)));
        JSONArray selectedIds = options.optJSONArray("selectedIds");
        Set<String> selected = new HashSet<>();
        if (selectedIds != null) {
            int limit = Math.min(selectedIds.length(), maxItems);
            for (int i = 0; i < limit; i++) {
                selected.add(selectedIds.optString(i));
            }
        }
        List<DueItem> chosen = new ArrayList<>();
        for (DueItem item : dueItems) {
            if (selectedIds != null && !item.id.isEmpty() && !selected.contains(item.id)) {
                continue;
            }
            if (chosen.size() >= maxItems) {
                break;
            }
            chosen.add(item);
        }
        return chosen;
    }

    /**
     * 构造纯文本课表式正文(单换行分隔, 左侧定宽时间标签 + 右侧事项名称), 确保最多 4 行不被流体云截断。
     *
     * @param dueItems 窗口内全部事项
     * @param options 用户的条数和手动选择配置
     * @return 纯文本多行明细
     */
    private static String buildExpandedText(List<DueItem> dueItems, JSONObject options) {
        List<DueItem> chosen = selectDisplayItems(dueItems, options);
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < chosen.size(); i++) {
            if (i > 0) {
                builder.append('\n');
            }
            DueItem item = chosen.get(i);
            builder.append(dueBadgeLabel(item.getDaysLeft()))
                    .append("  ")
                    .append(cleanTitle(item.getTitle()));
        }
        return builder.toString();
    }

    /**
     * 构造带语义色彩与粗体时间列的课表式富文本正文, 适配系统深浅色模式。
     *
     * @param context 任意上下文
     * @param dueItems 窗口内全部事项
     * @param options 用户的条数和手动选择配置
     * @return 可直接传给 BigTextStyle 的富文本
     */
    private static CharSequence buildStyledExpandedText(
            Context context,
            List<DueItem> dueItems,
            JSONObject options) {
        boolean darkMode = isNightMode(context);
        List<DueItem> chosen = selectDisplayItems(dueItems, options);
        SpannableStringBuilder builder = new SpannableStringBuilder();
        for (int i = 0; i < chosen.size(); i++) {
            if (i > 0) {
                builder.append('\n');
            }
            DueItem item = chosen.get(i);
            String badge = dueBadgeLabel(item.getDaysLeft());
            int badgeStart = builder.length();
            builder.append(badge);
            int badgeEnd = builder.length();
            builder.setSpan(
                    new StyleSpan(Typeface.BOLD),
                    badgeStart,
                    badgeEnd,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            builder.setSpan(
                    new ForegroundColorSpan(badgeColor(item.getDaysLeft(), darkMode)),
                    badgeStart,
                    badgeEnd,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            builder.append("  ").append(cleanTitle(item.getTitle()));
        }
        if (chosen.size() > COMPACT_CARD_MAX_ITEMS && builder.length() > 0) {
            builder.setSpan(
                    new RelativeSizeSpan(DENSE_CARD_TEXT_SCALE),
                    0,
                    builder.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return builder;
    }

    /**
     * 清理事项标题中的换行符, 保证每条事项严格只占一行。
     *
     * @param rawTitle 原始事项标题
     * @return 单行非空标题
     */
    private static String cleanTitle(String rawTitle) {
        String cleaned = rawTitle == null ? "" : rawTitle.replace('\n', ' ').replace('\r', ' ').trim();
        if (cleaned.isEmpty()) {
            return "未命名事项";
        }
        return cleaned;
    }

    /**
     * 生成课表左列紧凑的截止标签(常见场景统一为 4 个字宽度, 形成整齐纵列)。
     *
     * @param daysLeft 剩余天数
     * @return 逾期N天 / 今天截止 / 明天截止 / 后天截止 / N天后截止
     */
    private static String dueBadgeLabel(int daysLeft) {
        if (daysLeft < 0) {
            return "逾期" + Math.abs(daysLeft) + "天";
        }
        if (daysLeft == 0) {
            return "今天截止";
        }
        if (daysLeft == 1) {
            return "明天截止";
        }
        if (daysLeft == 2) {
            return "后天截止";
        }
        return daysLeft + "天后截止";
    }

    /**
     * 根据紧急程度和系统深浅色返回左列标签前景色。
     *
     * @param daysLeft 剩余天数
     * @param darkMode 系统是否处于深色模式
     * @return ARGB 颜色值
     */
    private static int badgeColor(int daysLeft, boolean darkMode) {
        if (daysLeft < 0) {
            return darkMode ? COLOR_OVERDUE_DARK : COLOR_OVERDUE_LIGHT;
        }
        if (daysLeft == 0) {
            return darkMode ? COLOR_TODAY_DARK : COLOR_TODAY_LIGHT;
        }
        if (daysLeft == 1) {
            return darkMode ? COLOR_TOMORROW_DARK : COLOR_TOMORROW_LIGHT;
        }
        if (daysLeft == 2) {
            return darkMode ? COLOR_SOON_DARK : COLOR_SOON_LIGHT;
        }
        return darkMode ? COLOR_LATER_DARK : COLOR_LATER_LIGHT;
    }

    /**
     * 判断当前系统是否处于深色模式, 以便选择高对比度的语义标签色。
     *
     * @param context 任意上下文
     * @return true 表示深色模式
     */
    private static boolean isNightMode(Context context) {
        int nightMask = context.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;
        return nightMask == Configuration.UI_MODE_NIGHT_YES;
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
        private final String id;
        private final String title;
        private final int daysLeft;
        private final int star;

        DueItem(String title, int daysLeft, int star) {
            this("", title, daysLeft, star);
        }

        DueItem(String id, String title, int daysLeft, int star) {
            this.id = id;
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
