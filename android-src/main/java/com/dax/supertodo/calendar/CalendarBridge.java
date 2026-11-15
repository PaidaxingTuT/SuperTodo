package com.dax.supertodo.calendar;

import android.Manifest;
import android.app.Activity;
import android.webkit.JavascriptInterface;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 系统日历同步的 JS 桥。
 *
 * <p>向 Web 端提供同步开关、日历读写权限申请以及状态全量同步接口，
 * 在后台线程执行 ContentResolver 读写，避免阻塞 WebView 主线程。</p>
 */
public class CalendarBridge {

    /** 日历读写权限请求码，与 MainActivity.onRequestPermissionsResult 对应。 */
    public static final int REQUEST_CODE_CALENDAR = 9002;

    private final Activity activity;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    /**
     * 创建日历桥接对象。
     *
     * @param activity 宿主 Activity，用于申请运行时权限和获取 ContentResolver
     */
    public CalendarBridge(Activity activity) {
        this.activity = activity;
    }

    /**
     * 开启或关闭系统日程同步。
     *
     * @param enabled 是否开启
     */
    @JavascriptInterface
    public void setEnabled(final boolean enabled) {
        if (activity == null) {
            return;
        }
        CalendarSyncManager.setEnabled(activity, enabled);
        if (enabled && !CalendarSyncManager.hasCalendarPermission(activity)) {
            activity.runOnUiThread(this::requestCalendarPermissionIfNeeded);
        }
    }

    /**
     * 查询系统日程同步开关的当前状态。
     *
     * @return true 表示已开启
     */
    @JavascriptInterface
    public boolean isEnabled() {
        return activity != null && CalendarSyncManager.isEnabled(activity);
    }

    /**
     * 查询系统日历读写权限是否已授予。
     *
     * @return true 表示权限齐全
     */
    @JavascriptInterface
    public boolean hasCalendarPermission() {
        return activity != null && CalendarSyncManager.hasCalendarPermission(activity);
    }

    /**
     * 手动请求系统日历读写权限。
     */
    @JavascriptInterface
    public void requestPermission() {
        if (activity == null) {
            return;
        }
        activity.runOnUiThread(this::requestCalendarPermissionIfNeeded);
    }

    /**
     * 将 Web 端当前待办状态异步同步到系统日历。
     *
     * @param stateJson Web 端完整 state JSON 字符串
     */
    @JavascriptInterface
    public void syncFromState(final String stateJson) {
        if (activity == null || stateJson == null || stateJson.isEmpty()) {
            return;
        }
        executor.execute(() -> CalendarSyncManager.syncFromStateJson(activity, stateJson));
    }

    /**
     * 在尚未授予日历读写权限时发起运行时权限申请。
     */
    public void requestCalendarPermissionIfNeeded() {
        if (activity == null || CalendarSyncManager.hasCalendarPermission(activity)) {
            return;
        }
        activity.requestPermissions(
                new String[] {
                    Manifest.permission.READ_CALENDAR,
                    Manifest.permission.WRITE_CALENDAR
                },
                REQUEST_CODE_CALENDAR
        );
    }
}
