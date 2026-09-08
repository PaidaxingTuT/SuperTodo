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
    private volatile int statusBarHeightDp = 0;

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
        return statusBarHeightDp;
    }

    public void setStatusBarHeightDp(int heightDp) {
        statusBarHeightDp = Math.max(0, heightDp);
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
    public void vibrate(int milliseconds) {
        if (activity == null) return;
        try {
            android.os.Vibrator v = (android.os.Vibrator) activity.getSystemService(android.content.Context.VIBRATOR_SERVICE);
            if (v != null && v.hasVibrator()) {
                int ms = Math.max(5, Math.min(1000, milliseconds));
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    v.vibrate(android.os.VibrationEffect.createOneShot(ms, android.os.VibrationEffect.DEFAULT_AMPLITUDE));
                } else {
                    v.vibrate(ms);
                }
            }
        } catch (Throwable ignore) {}
    }

    private long currentDownloadId = -1;
    private String currentDownloadFilename = "";
    private String currentDownloadPath = "";

    public synchronized void setDownloadTask(long id, String filename) {
        this.currentDownloadId = id;
        this.currentDownloadFilename = (filename != null && !filename.isEmpty()) ? filename : "SuperTodo-update.apk";
        this.currentDownloadPath = "";
    }

    public synchronized long getCurrentDownloadId() {
        return currentDownloadId;
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
            long id = dm.enqueue(req);
            setDownloadTask(id, filename);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    @JavascriptInterface
    public String getDownloadProgress() {
        if (activity == null || currentDownloadId <= 0) {
            return "{\"active\":false}";
        }
        try {
            android.app.DownloadManager dm = (android.app.DownloadManager) activity.getSystemService(android.content.Context.DOWNLOAD_SERVICE);
            if (dm == null) return "{\"active\":false}";
            android.app.DownloadManager.Query query = new android.app.DownloadManager.Query();
            query.setFilterById(currentDownloadId);
            android.database.Cursor cursor = dm.query(query);
            if (cursor != null) {
                try {
                    if (cursor.moveToFirst()) {
                        int bytesDownloaded = cursor.getInt(cursor.getColumnIndexOrThrow(android.app.DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
                        int bytesTotal = cursor.getInt(cursor.getColumnIndexOrThrow(android.app.DownloadManager.COLUMN_TOTAL_SIZE_BYTES));
                        int status = cursor.getInt(cursor.getColumnIndexOrThrow(android.app.DownloadManager.COLUMN_STATUS));
                        String resolvedPath = currentDownloadPath;
                        if (status == android.app.DownloadManager.STATUS_SUCCESSFUL) {
                            if (resolvedPath == null || resolvedPath.isEmpty()) {
                                int uriIdx = cursor.getColumnIndex(android.app.DownloadManager.COLUMN_LOCAL_URI);
                                if (uriIdx >= 0) {
                                    String uriStr = cursor.getString(uriIdx);
                                    if (uriStr != null && uriStr.startsWith("file://")) {
                                        resolvedPath = android.net.Uri.parse(uriStr).getPath();
                                    }
                                }
                            }
                            if (resolvedPath == null || resolvedPath.isEmpty()) {
                                java.io.File f = new java.io.File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS), currentDownloadFilename);
                                if (f.exists()) {
                                    resolvedPath = f.getAbsolutePath();
                                }
                            }
                            if (resolvedPath != null && !resolvedPath.isEmpty()) {
                                currentDownloadPath = resolvedPath;
                            }
                        }
                        String safePath = resolvedPath != null ? resolvedPath.replace("\\", "\\\\").replace("\"", "\\\"") : "";
                        return "{\"active\":true,\"status\":" + status + ",\"downloaded\":" + bytesDownloaded + ",\"total\":" + bytesTotal + ",\"path\":\"" + safePath + "\"}";
                    }
                } finally {
                    cursor.close();
                }
            }
        } catch (Throwable ignore) {}
        return "{\"active\":false}";
    }

    public void handleDownloadComplete(final long id) {
        if (id <= 0 || id != currentDownloadId || activity == null || webView == null) return;
        String resolvedPath = currentDownloadPath;
        boolean successful = false;
        try {
            android.app.DownloadManager dm = (android.app.DownloadManager) activity.getSystemService(android.content.Context.DOWNLOAD_SERVICE);
            if (dm != null) {
                android.app.DownloadManager.Query query = new android.app.DownloadManager.Query();
                query.setFilterById(id);
                android.database.Cursor cursor = dm.query(query);
                if (cursor != null) {
                    try {
                        if (cursor.moveToFirst()) {
                            int status = cursor.getInt(cursor.getColumnIndexOrThrow(android.app.DownloadManager.COLUMN_STATUS));
                            if (status == android.app.DownloadManager.STATUS_SUCCESSFUL) {
                                successful = true;
                                int uriIdx = cursor.getColumnIndex(android.app.DownloadManager.COLUMN_LOCAL_URI);
                                if (uriIdx >= 0) {
                                    String uriStr = cursor.getString(uriIdx);
                                    if (uriStr != null && uriStr.startsWith("file://")) {
                                        resolvedPath = android.net.Uri.parse(uriStr).getPath();
                                    }
                                }
                            }
                        }
                    } finally {
                        cursor.close();
                    }
                }
            }
        } catch (Throwable ignore) {}

        // DownloadManager 对失败任务也发送完成广播，失败时必须留给轮询分支处理。
        if (!successful) return;

        if (resolvedPath == null || resolvedPath.isEmpty()) {
            try {
                java.io.File f = new java.io.File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS), currentDownloadFilename);
                if (f.exists()) {
                    resolvedPath = f.getAbsolutePath();
                }
            } catch (Throwable ignore) {}
        }
        if (resolvedPath != null && !resolvedPath.isEmpty()) {
            currentDownloadPath = resolvedPath;
        }

        final String finalPath = resolvedPath != null ? resolvedPath : "";
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    String script = "if(window.onNativeDownloadCompleted){ window.onNativeDownloadCompleted('" + finalPath.replace("\\", "\\\\").replace("'", "\\'") + "'); }";
                    webView.evaluateJavascript(script, null);
                } catch (Throwable ignore) {}
            }
        });
    }

        @JavascriptInterface
    public boolean saveBackupFile(String jsonContent, String fileName) {
        if (activity == null || jsonContent == null || jsonContent.isEmpty()) return false;
        try {
            final String safeName = (fileName != null && !fileName.trim().isEmpty())
                    ? fileName.trim()
                    : ("超级清单备份_" + new java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault()).format(new java.util.Date()) + ".json");

            java.io.File targetFile = null;
            boolean written = false;

            // 1. 尝试直接写入公共 Download 目录
            try {
                java.io.File publicDownloads = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS);
                if (publicDownloads != null && (publicDownloads.exists() || publicDownloads.mkdirs())) {
                    java.io.File f = new java.io.File(publicDownloads, safeName);
                    java.io.FileOutputStream fos = new java.io.FileOutputStream(f);
                    fos.write(jsonContent.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    fos.flush();
                    fos.close();
                    targetFile = f;
                    written = true;
                }
            } catch (Throwable ignore) {}

            // 2. 外部私有目录兜底（兼容 Android 10+ 分区存储机制）
            if (!written) {
                try {
                    java.io.File extDownloads = activity.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS);
                    if (extDownloads != null && (extDownloads.exists() || extDownloads.mkdirs())) {
                        java.io.File f = new java.io.File(extDownloads, safeName);
                        java.io.FileOutputStream fos = new java.io.FileOutputStream(f);
                        fos.write(jsonContent.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                        fos.flush();
                        fos.close();
                        targetFile = f;
                        written = true;
                    }
                } catch (Throwable ignore) {}
            }

            // 3. 应用内部缓存目录最终兜底
            if (!written) {
                java.io.File cacheFile = new java.io.File(activity.getCacheDir(), safeName);
                java.io.FileOutputStream fos = new java.io.FileOutputStream(cacheFile);
                fos.write(jsonContent.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                fos.flush();
                fos.close();
                targetFile = cacheFile;
                written = true;
            }

            final java.io.File finalFile = targetFile;
            if (finalFile == null || !finalFile.exists()) return false;

            // 触发媒体扫描广播，使备份文件立即显示在系统文件管理器中
            try {
                android.media.MediaScannerConnection.scanFile(
                    activity,
                    new String[]{ finalFile.getAbsolutePath() },
                    new String[]{ "application/json" },
                    null
                );
            } catch (Throwable ignore) {}

            // 主线程调起系统分享选择器（可发送至微信/QQ/网盘/直接保存）
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    try {
                        android.widget.Toast.makeText(activity, "备份已保存：" + safeName, android.widget.Toast.LENGTH_SHORT).show();

                        android.net.Uri fileUri;
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                            String authority = activity.getPackageName() + ".fileprovider";
                            fileUri = androidx.core.content.FileProvider.getUriForFile(activity, authority, finalFile);
                        } else {
                            fileUri = android.net.Uri.fromFile(finalFile);
                        }

                        android.content.Intent shareIntent = new android.content.Intent(android.content.Intent.ACTION_SEND);
                        shareIntent.setType("application/json");
                        shareIntent.putExtra(android.content.Intent.EXTRA_STREAM, fileUri);
                        shareIntent.putExtra(android.content.Intent.EXTRA_SUBJECT, safeName);
                        shareIntent.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);

                        android.content.Intent chooser = android.content.Intent.createChooser(shareIntent, "导出备份：" + safeName);
                        chooser.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
                        activity.startActivity(chooser);
                    } catch (Throwable ignore) {}
                }
            });

            return true;
        } catch (Throwable e) {
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
