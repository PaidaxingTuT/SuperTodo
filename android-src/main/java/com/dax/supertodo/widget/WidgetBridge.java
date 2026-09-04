package com.dax.supertodo.widget;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.os.Build;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

public class WidgetBridge {
    private final Activity activity;
    private final WebView webView;

    public WidgetBridge(Activity activity, WebView webView) {
        this.activity = activity;
        this.webView = webView;
    }

    @JavascriptInterface
    public void syncData(String json) {
        if (activity == null) return;
        WidgetDataManager.saveWidgetData(activity, json);
        WidgetDataManager.notifyAllWidgets(activity);
    }

    @JavascriptInterface
    public String getData() {
        if (activity == null) return "";
        return WidgetDataManager.getWidgetData(activity);
    }

    @JavascriptInterface
    public boolean isSupported() {
        return true;
    }

    @JavascriptInterface
    public int getStatusBarHeightDp() {
        if (activity == null) return 0;
        try {
            int resId = activity.getResources().getIdentifier("status_bar_height", "dimen", "android");
            if (resId > 0) {
                int px = activity.getResources().getDimensionPixelSize(resId);
                float density = activity.getResources().getDisplayMetrics().density;
                return Math.round(px / density);
            }
        } catch (Throwable ignore) {}
        return 0;
    }

    @JavascriptInterface
    public boolean isSystemNightMode() {
        try {
            int sysMode = android.content.res.Resources.getSystem().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
            if (sysMode == android.content.res.Configuration.UI_MODE_NIGHT_YES) return true;
            if (activity != null) {
                int appMode = activity.getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
                return appMode == android.content.res.Configuration.UI_MODE_NIGHT_YES;
            }
        } catch (Throwable ignore) {}
        return false;
    }

    @JavascriptInterface
    public boolean downloadFile(String url, String filename) {
        if (activity == null || url == null || url.isEmpty()) return false;
        try {
            android.app.DownloadManager dm = (android.app.DownloadManager) activity.getSystemService(android.content.Context.DOWNLOAD_SERVICE);
            if (dm == null) return false;
            android.net.Uri uri = android.net.Uri.parse(url);
            android.app.DownloadManager.Request req = new android.app.DownloadManager.Request(uri);
            req.setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            String title = (filename != null && !filename.isEmpty()) ? filename : "SuperTodo 更新";
            req.setTitle(title);
            req.setDescription("正在下载更新安装包…");
            if (filename != null && !filename.isEmpty()) {
                req.setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS, filename);
            }
            req.setMimeType("application/vnd.android.package-archive");
            dm.enqueue(req);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    @JavascriptInterface
    public boolean installApk(String filePath) {
        if (activity == null) return false;
        try {
            java.io.File apkFile = null;
            if (filePath != null && !filePath.trim().isEmpty()) {
                apkFile = new java.io.File(filePath);
            }
            if (apkFile == null || !apkFile.exists()) {
                java.io.File downloads = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS);
                if (downloads != null && downloads.exists()) {
                    java.io.File[] files = downloads.listFiles((dir, name) -> name.toLowerCase().endsWith(".apk") && name.toLowerCase().contains("supertodo"));
                    if (files != null && files.length > 0) {
                        java.util.Arrays.sort(files, (f1, f2) -> Long.compare(f2.lastModified(), f1.lastModified()));
                        apkFile = files[0];
                    }
                }
            }
            if (apkFile == null || !apkFile.exists()) {
                android.content.Intent openDownloads = new android.content.Intent(android.app.DownloadManager.ACTION_VIEW_DOWNLOADS);
                openDownloads.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
                activity.startActivity(openDownloads);
                return true;
            }

            android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_VIEW);
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);

            android.net.Uri contentUri;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                String authority = activity.getPackageName() + ".fileprovider";
                contentUri = androidx.core.content.FileProvider.getUriForFile(activity, authority, apkFile);
            } else {
                contentUri = android.net.Uri.fromFile(apkFile);
            }
            intent.setDataAndType(contentUri, "application/vnd.android.package-archive");
            activity.startActivity(intent);
            return true;
        } catch (Throwable t) {
            try {
                android.content.Intent openDownloads = new android.content.Intent(android.app.DownloadManager.ACTION_VIEW_DOWNLOADS);
                openDownloads.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
                activity.startActivity(openDownloads);
                return true;
            } catch (Throwable ignore) {}
            return false;
        }
    }

    @JavascriptInterface
    public boolean requestPinWidget(String size) {
        if (activity == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return false;
        }
        try {
            AppWidgetManager manager = activity.getSystemService(AppWidgetManager.class);
            if (manager == null || !manager.isRequestPinAppWidgetSupported()) {
                return false;
            }
            Class<?> providerClass;
            if ("quadrant".equals(size)) {
                providerClass = TodoWidgetQuadrantProvider.class;
            } else if ("4x4".equals(size)) {
                providerClass = TodoWidget4x4Provider.class;
            } else {
                providerClass = TodoWidget4x2Provider.class;
            }
            ComponentName provider = new ComponentName(activity, providerClass);
            return manager.requestPinAppWidget(provider, null, null);
        } catch (Exception e) {
            return false;
        }
    }

    private String pendingAction = null;
    private String pendingItemId = null;

    public synchronized void setPendingAction(String action, String itemId) {
        this.pendingAction = action;
        this.pendingItemId = itemId;
    }

    @JavascriptInterface
    public synchronized String getPendingAction() {
        return pendingAction != null ? pendingAction : "";
    }

    @JavascriptInterface
    public synchronized String getPendingItemId() {
        return pendingItemId != null ? pendingItemId : "";
    }

    @JavascriptInterface
    public synchronized void clearPendingAction() {
        this.pendingAction = null;
        this.pendingItemId = null;
    }

    public void notifyAppResumed() {
        if (activity == null || webView == null) return;
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    webView.evaluateJavascript("if(window.onNativeWidgetResume) window.onNativeWidgetResume();", null);
                } catch (Exception ignore) {}
            }
        });
    }

    public void dispatchAction(final String action, final String itemId) {
        setPendingAction(action, itemId);
        if (activity == null || webView == null) return;
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    String safeAction = action != null ? action : "";
                    String safeItemId = itemId != null ? itemId : "";
                    String script = "if(window.onNativeWidgetAction){ window.onNativeWidgetAction('" + safeAction + "', '" + safeItemId + "'); if(window.AndroidWidgetBridge) window.AndroidWidgetBridge.clearPendingAction(); }";
                    webView.evaluateJavascript(script, null);
                } catch (Exception ignore) {}
            }
        });
    }
}
