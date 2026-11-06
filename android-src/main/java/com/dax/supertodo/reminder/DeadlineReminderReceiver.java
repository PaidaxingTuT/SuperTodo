package com.dax.supertodo.reminder;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * 截止提醒的广播接收器。
 *
 * <p>承担两件事：接收每日闹钟并执行扫描，以及设备重启后重新排定闹钟。
 * 用户重启手机后 AlarmManager 里排定的闹钟会全部失效，如果不在这里补排，
 * 提醒会在重启后永久停止。</p>
 */
public class DeadlineReminderReceiver extends BroadcastReceiver {

    /**
     * 处理每日扫描和开机补排。
     *
     * @param context 接收者上下文
     * @param intent 广播意图
     */
    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null) {
            return;
        }
        String action = intent.getAction();
        if (DeadlineReminderManager.ACTION_CHECK_DEADLINE.equals(action)) {
            if (!DeadlineReminderManager.isEnabled(context)) {
                return;
            }
            DeadlineReminderManager.checkAndNotify(context);
            // 每次触发后重新排下一天，AlarmManager 的一次性闹钟不会自动重复。
            DeadlineReminderManager.scheduleNext(context);
            return;
        }
        if (Intent.ACTION_BOOT_COMPLETED.equals(action)) {
            if (DeadlineReminderManager.isEnabled(context)) {
                DeadlineReminderManager.scheduleNext(context);
            }
        }
    }
}
