package com.dax.supertodo.reminder;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.webkit.JavascriptInterface;

/**
 * 截止提醒的 JS 桥。
 *
 * <p>向 Web 端提供开关控制、权限申请、系统通知设置跳转以及「立即测试提醒」能力。
 * 数据本身仍然走现有的 WidgetBridge.syncData 通道，避免出现两套数据源。</p>
 */
public class ReminderBridge {

    /** 通知权限请求码，与 MainActivity.onRequestPermissionsResult 对应。 */
    public static final int REQUEST_CODE_NOTIFICATION = 9001;

    private final Activity activity;

    /**
     * 创建桥接对象。
     *
     * @param activity 宿主 Activity，用于申请运行时权限和发送通知
     */
    public ReminderBridge(Activity activity) {
        this.activity = activity;
    }

    /**
     * 开启或关闭截止提醒。
     *
     * @param enabled 是否开启
     */
    @JavascriptInterface
    public void setEnabled(final boolean enabled) {
        if (activity == null) {
            return;
        }
        activity.runOnUiThread(() -> {
            DeadlineReminderManager.setEnabled(activity, enabled);
            if (enabled) {
                requestNotificationPermissionIfNeeded();
            }
        });
    }

    /**
     * 查询提醒开关的当前状态。
     *
     * @return true 表示已开启
     */
    @JavascriptInterface
    public boolean isEnabled() {
        return activity != null && DeadlineReminderManager.isEnabled(activity);
    }

    /**
     * 查询通知权限是否已授予。
     *
     * @return true 表示可以发送通知
     */
    @JavascriptInterface
    public boolean hasNotificationPermission() {
        return activity != null && DeadlineReminderManager.hasNotificationPermission(activity);
    }

    /**
     * 手动请求一次通知权限。
     */
    @JavascriptInterface
    public void requestPermission() {
        if (activity == null) {
            return;
        }
        activity.runOnUiThread(this::requestNotificationPermissionIfNeeded);
    }

    /**
     * 立即触发一条测试提醒通知，并返回结果状态码供前端提示。
     *
     * @return "NO_PERMISSION"、"DEMO_SENT"、"REAL_SENT:N" 或错误信息
     */
    @JavascriptInterface
    public String testReminderNow() {
        if (activity == null) {
            return "ERROR";
        }
        if (!DeadlineReminderManager.hasNotificationPermission(activity)) {
            activity.runOnUiThread(this::requestNotificationPermissionIfNeeded);
            return "NO_PERMISSION";
        }
        return DeadlineReminderManager.triggerTestReminder(activity);
    }

    /**
     * 打开当前应用的系统通知设置页（方便用户在 ColorOS 中开启横幅与实时活动/流体云开关）。
     */
    @JavascriptInterface
    public void openNotificationSettings() {
        if (activity == null) {
            return;
        }
        activity.runOnUiThread(() -> {
            try {
                Intent intent = new Intent();
                if (Build.VERSION.SDK_INT >= 26) {
                    intent.setAction(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
                    intent.putExtra(Settings.EXTRA_APP_PACKAGE, activity.getPackageName());
                } else {
                    intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    intent.setData(Uri.fromParts("package", activity.getPackageName(), null));
                }
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                activity.startActivity(intent);
            } catch (Exception ignore) {
                try {
                    Intent fallback = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    fallback.setData(Uri.fromParts("package", activity.getPackageName(), null));
                    fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    activity.startActivity(fallback);
                } catch (Exception ignored) {
                    // 忽略极端ROM裁剪导致的跳转失败
                }
            }
        });
    }

    /**
     * 在 Android 13 及以上申请通知权限。
     *
     * <p>系统权限对话框只能在 UI 线程弹出，因此由调用方切到主线程后调用。
     * 用户在弹窗中点击允许后，MainActivity.onRequestPermissionsResult 会立即补发通知。</p>
     */
    public void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < 33) {
            return;
        }
        if (DeadlineReminderManager.hasNotificationPermission(activity)) {
            return;
        }
        activity.requestPermissions(
                new String[]{android.Manifest.permission.POST_NOTIFICATIONS},
                REQUEST_CODE_NOTIFICATION);
    }
}
