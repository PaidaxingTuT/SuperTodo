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
        restoreDownloadState();
    }

    @JavascriptInterface
    public void syncData(String json) {
        if (activity == null) return;
        try {
            String oldJson = WidgetDataManager.getWidgetData(activity);
            if (oldJson != null && !oldJson.isEmpty()) {
                org.json.JSONObject oldObj = new org.json.JSONObject(oldJson);
                org.json.JSONArray old2x2 = oldObj.optJSONArray("widget2x2");
                if (old2x2 != null && old2x2.length() > 0) {
                    org.json.JSONObject newObj = new org.json.JSONObject(json);
                    if (!newObj.has("widget2x2")) {
                        org.json.JSONArray newItems = newObj.optJSONArray("items");
                        if (newItems != null) {
                            java.util.Map<String, Boolean> statusMap = new java.util.HashMap<>();
                            for (int i = 0; i < newItems.length(); i++) {
                                org.json.JSONObject it = newItems.optJSONObject(i);
                                if (it != null) {
                                    statusMap.put(it.optString("id"), it.optBoolean("done", false));
                                }
                            }
                            for (int j = 0; j < old2x2.length(); j++) {
                                org.json.JSONObject w = old2x2.optJSONObject(j);
                                if (w != null && statusMap.containsKey(w.optString("id"))) {
                                    w.put("done", statusMap.get(w.optString("id")));
                                }
                            }
                        }
                        newObj.put("widget2x2", old2x2);
                        json = newObj.toString();
                    }
                }
            }
        } catch (Exception ignore) {}
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

    private static final String PREF_NAME = "supertodo_download";
    private static final String PREF_DL_ID = "download_id";
    private static final String PREF_DL_FILENAME = "download_filename";
    private static final String PREF_DL_PATH = "download_path";

    private long currentDownloadId = -1;
    private String currentDownloadFilename = "";
    private String currentDownloadPath = "";

    private volatile long nativeBytesDownloaded = 0;
    private volatile long nativeBytesTotal = 0;
    private volatile int nativeDownloadStatus = 0; // 0=none, 1=pending, 2=running, 8=successful, 16=failed
    private volatile String nativeDownloadFilePath = "";
    private Thread nativeDownloadThread = null;

    private android.content.SharedPreferences getDownloadPrefs() {
        return activity.getSharedPreferences(PREF_NAME, android.content.Context.MODE_PRIVATE);
    }

    public synchronized void setDownloadTask(long id, String filename) {
        this.currentDownloadId = id;
        this.currentDownloadFilename = (filename != null && !filename.isEmpty()) ? filename : "SuperTodo-update.apk";
        this.currentDownloadPath = "";
        android.content.SharedPreferences.Editor ed = getDownloadPrefs().edit();
        ed.putLong(PREF_DL_ID, id);
        ed.putString(PREF_DL_FILENAME, this.currentDownloadFilename);
        ed.putString(PREF_DL_PATH, "");
        ed.apply();
    }

    private synchronized void clearDownloadState() {
        this.currentDownloadId = -1;
        this.currentDownloadFilename = "";
        this.currentDownloadPath = "";
        android.content.SharedPreferences.Editor ed = getDownloadPrefs().edit();
        ed.remove(PREF_DL_ID);
        ed.remove(PREF_DL_FILENAME);
        ed.remove(PREF_DL_PATH);
        ed.apply();
    }

    private void restoreDownloadState() {
        if (activity == null) return;
        android.content.SharedPreferences prefs = getDownloadPrefs();
        long savedId = prefs.getLong(PREF_DL_ID, -1);
        if (savedId <= 0) return;
        String savedFilename = prefs.getString(PREF_DL_FILENAME, "SuperTodo-update.apk");
        String savedPath = prefs.getString(PREF_DL_PATH, "");
        try {
            android.app.DownloadManager dm = (android.app.DownloadManager) activity.getSystemService(android.content.Context.DOWNLOAD_SERVICE);
            if (dm == null) { clearDownloadState(); return; }
            android.app.DownloadManager.Query query = new android.app.DownloadManager.Query();
            query.setFilterById(savedId);
            android.database.Cursor cursor = dm.query(query);
            if (cursor == null) { clearDownloadState(); return; }
            try {
                if (!cursor.moveToFirst()) { clearDownloadState(); return; }
                int statusIdx = cursor.getColumnIndex(android.app.DownloadManager.COLUMN_STATUS);
                int status = statusIdx >= 0 ? cursor.getInt(statusIdx) : -1;
                if (status == android.app.DownloadManager.STATUS_SUCCESSFUL) {
                    synchronized (this) {
                        this.currentDownloadId = savedId;
                        this.currentDownloadFilename = savedFilename;
                        this.currentDownloadPath = savedPath;
                    }
                    handleDownloadComplete(savedId);
                } else if (status == android.app.DownloadManager.STATUS_FAILED || status == -1) {
                    clearDownloadState();
                } else {
                    // still running — restore state so polling works
                    synchronized (this) {
                        this.currentDownloadId = savedId;
                        this.currentDownloadFilename = savedFilename;
                        this.currentDownloadPath = savedPath;
                    }
                }
            } finally {
                cursor.close();
            }
        } catch (Throwable t) {
            clearDownloadState();
        }
    }

    public synchronized long getCurrentDownloadId() {
        return currentDownloadId;
    }

    @JavascriptInterface
    public boolean downloadFile(final String url, final String filename) {
        if (activity == null || url == null || url.trim().isEmpty()) return false;

        // Stop previous download thread if still alive
        if (nativeDownloadThread != null && nativeDownloadThread.isAlive()) {
            try {
                nativeDownloadThread.interrupt();
            } catch (Throwable ignore) {}
        }

        final String targetName = (filename != null && !filename.trim().isEmpty()) ? filename : "SuperTodo-update.apk";
        this.currentDownloadFilename = targetName;

        nativeBytesDownloaded = 0;
        nativeBytesTotal = 0;
        nativeDownloadStatus = 1; // STATUS_PENDING

        java.io.File dir = activity.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS);
        if (dir == null) {
            dir = activity.getCacheDir();
        }
        if (dir != null && !dir.exists()) {
            dir.mkdirs();
        }
        final java.io.File targetFile = new java.io.File(dir, targetName);
        nativeDownloadFilePath = targetFile.getAbsolutePath();
        this.currentDownloadPath = targetFile.getAbsolutePath();

        nativeDownloadThread = new Thread(new Runnable() {
            @Override
            public void run() {
                java.net.HttpURLConnection conn = null;
                java.io.InputStream in = null;
                java.io.FileOutputStream out = null;
                try {
                    nativeDownloadStatus = 2; // STATUS_RUNNING
                    java.net.URL currentUrl = new java.net.URL(url);
                    int redirects = 0;
                    while (redirects < 8) {
                        conn = (java.net.HttpURLConnection) currentUrl.openConnection();
                        conn.setConnectTimeout(15000);
                        conn.setReadTimeout(20000);
                        conn.setInstanceFollowRedirects(true);
                        conn.setRequestProperty("User-Agent", "SuperTodo-App/" + targetName);
                        int code = conn.getResponseCode();
                        if (code == java.net.HttpURLConnection.HTTP_MOVED_PERM 
                            || code == java.net.HttpURLConnection.HTTP_MOVED_TEMP 
                            || code == 307 
                            || code == 308) {
                            String loc = conn.getHeaderField("Location");
                            if (loc != null && !loc.trim().isEmpty()) {
                                conn.disconnect();
                                currentUrl = new java.net.URL(loc);
                                redirects++;
                                continue;
                            }
                        }
                        if (code < 200 || code >= 300) {
                            throw new java.io.IOException("HTTP response " + code);
                        }
                        break;
                    }

                    long cl = conn.getContentLengthLong();
                    if (cl > 0) {
                        nativeBytesTotal = cl;
                    }

                    if (targetFile.exists()) {
                        targetFile.delete();
                    }

                    in = new java.io.BufferedInputStream(conn.getInputStream());
                    out = new java.io.FileOutputStream(targetFile);
                    byte[] buffer = new byte[32768];
                    int len;
                    while ((len = in.read(buffer)) != -1) {
                        if (Thread.currentThread().isInterrupted()) {
                            throw new java.io.InterruptedIOException("Cancelled");
                        }
                        out.write(buffer, 0, len);
                        nativeBytesDownloaded += len;
                    }
                    out.flush();

                    nativeDownloadStatus = 8; // STATUS_SUCCESSFUL

                    // Try copying to public downloads folder as a convenience if accessible
                    try {
                        java.io.File pubDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS);
                        if (pubDir != null && pubDir.exists() && pubDir.canWrite()) {
                            java.io.File pubTarget = new java.io.File(pubDir, targetName);
                            copyFile(targetFile, pubTarget);
                        }
                    } catch (Throwable ignore) {}

                    // Notify webview on UI thread
                    if (activity != null && webView != null) {
                        final String finalPath = targetFile.getAbsolutePath();
                        activity.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    String script = "if(window.onDownloadSuccess){ window.onDownloadSuccess('" + finalPath.replace("\\", "\\\\").replace("'", "\\'") + "'); }";
                                    webView.evaluateJavascript(script, null);
                                } catch (Throwable ignore) {}
                            }
                        });
                    }
                } catch (Throwable t) {
                    if (!Thread.currentThread().isInterrupted()) {
                        nativeDownloadStatus = 16; // STATUS_FAILED
                    }
                } finally {
                    if (in != null) try { in.close(); } catch (Throwable ignore) {}
                    if (out != null) try { out.close(); } catch (Throwable ignore) {}
                    if (conn != null) try { conn.disconnect(); } catch (Throwable ignore) {}
                }
            }
        });
        nativeDownloadThread.start();
        return true;
    }

    @JavascriptInterface
    public String getDownloadProgress() {
        if (activity == null) {
            return "{\"active\":false}";
        }

        // Live streaming download progress from native background thread
        if (nativeDownloadStatus > 0) {
            boolean active = (nativeDownloadStatus == 1 || nativeDownloadStatus == 2 || nativeDownloadStatus == 8);
            String safePath = (nativeDownloadFilePath != null) ? nativeDownloadFilePath.replace("\\", "\\\\").replace("\"", "\\\"") : "";
            return "{\"active\":" + active 
                + ",\"status\":" + nativeDownloadStatus 
                + ",\"downloaded\":" + nativeBytesDownloaded 
                + ",\"total\":" + nativeBytesTotal 
                + ",\"path\":\"" + safePath + "\"}";
        }

        // Fallback to DownloadManager for system-initiated tasks
        if (currentDownloadId <= 0) {
            try {
                java.io.File pubDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS);
                java.io.File target = new java.io.File(pubDir, currentDownloadFilename);
                if (target.exists() && target.length() > 0) {
                    String safeFn = currentDownloadFilename != null ? currentDownloadFilename.replace("\\", "\\\\").replace("\"", "\\\"") : "";
                    return "{\"active\":true,\"status\":4,\"downloaded\":" + target.length() + ",\"total\":0,\"path\":\"\"}";
                }
            } catch (Throwable ignore) {}
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
                        long bytesDownloaded = 0;
                        long bytesTotal = 0;
                        int status = 0;
                        try {
                            int idxDownloaded = cursor.getColumnIndex(android.app.DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR);
                            if (idxDownloaded >= 0) bytesDownloaded = cursor.getLong(idxDownloaded);
                            int idxTotal = cursor.getColumnIndex(android.app.DownloadManager.COLUMN_TOTAL_SIZE_BYTES);
                            if (idxTotal >= 0) bytesTotal = cursor.getLong(idxTotal);
                            int idxStatus = cursor.getColumnIndex(android.app.DownloadManager.COLUMN_STATUS);
                            if (idxStatus >= 0) status = cursor.getInt(idxStatus);
                        } catch (Throwable ignore) {}

                        // Always read the real file size to compensate for DownloadManager provider lag on some ROMs
                        try {
                            java.io.File pubDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS);
                            java.io.File target = new java.io.File(pubDir, currentDownloadFilename);
                            if (target.exists()) {
                                bytesDownloaded = Math.max(bytesDownloaded, target.length());
                            }
                        } catch (Throwable ignore) {}

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
                                getDownloadPrefs().edit().putString(PREF_DL_PATH, resolvedPath).apply();
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
        clearDownloadState();
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
                if (nativeDownloadFilePath != null && !nativeDownloadFilePath.trim().isEmpty()) {
                    java.io.File nf = new java.io.File(nativeDownloadFilePath);
                    if (nf.exists()) {
                        apkFile = nf;
                    }
                }
            }
            if (apkFile == null || !apkFile.exists()) {
                java.io.File appDl = activity.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS);
                if (appDl != null && appDl.exists()) {
                    java.io.File[] files = appDl.listFiles((dir, name) -> name.toLowerCase().endsWith(".apk") && name.toLowerCase().contains("supertodo"));
                    if (files != null && files.length > 0) {
                        java.util.Arrays.sort(files, (f1, f2) -> Long.compare(f2.lastModified(), f1.lastModified()));
                        apkFile = files[0];
                    }
                }
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

    private void copyFile(java.io.File src, java.io.File dst) throws java.io.IOException {
        java.io.InputStream in = new java.io.FileInputStream(src);
        try {
            java.io.OutputStream out = new java.io.FileOutputStream(dst);
            try {
                byte[] buf = new byte[32768];
                int len;
                while ((len = in.read(buf)) > 0) {
                    out.write(buf, 0, len);
                }
            } finally {
                out.close();
            }
        } finally {
            in.close();
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

    private String pendingImportJson = null;

    public synchronized void setPendingImportJson(String json) {
        this.pendingImportJson = json;
    }

    @JavascriptInterface
    public synchronized String getPendingImportJson() {
        return pendingImportJson != null ? pendingImportJson : "";
    }

    @JavascriptInterface
    public synchronized void clearPendingImportJson() {
        this.pendingImportJson = null;
    }

    public void dispatchImportJson(final String jsonContent) {
        setPendingImportJson(jsonContent);
        if (activity == null || webView == null) return;
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    webView.evaluateJavascript("if(window.onNativeImportJson){ window.onNativeImportJson(); }", null);
                } catch (Exception ignore) {}
            }
        });
    }
}
