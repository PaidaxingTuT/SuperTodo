package com.dax.supertodo.reminder;

import android.app.Activity;
import android.os.Build;
import android.webkit.JavascriptInterface;
import org.json.JSONObject;

/**
 * 截止提醒的 JS 桥。
 *
 * <p>Web 端只需要一个开关，不参与扫描和计算，因此这里只暴露最小的三个方法。
 * 数据本身仍然走现有的 WidgetBridge.syncData 通道，避免出现两套数据源。</p>
 */
public class ReminderBridge {

    private static final int REQUEST_CODE_NOTIFICATION = 9001;

    private final Activity activity;

    /**
     * 创建桥接对象。
     *
     * @param activity 宿主 Activity，用于申请运行时权限
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
     * 在 Android 13 及以上申请通知权限。
     *
     * <p>系统权限对话框只能在 UI 线程弹出，因此由 setEnabled 切到主线程后调用。
     * 用户拒绝时提醒开关仍然保留，等用户去系统设置里授权后即可生效。</p>
     */
    private void requestNotificationPermissionIfNeeded() {
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
