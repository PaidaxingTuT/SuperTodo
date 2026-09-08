package com.dax.supertodo;

import android.app.DownloadManager;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.DownloadListener;
import com.dax.supertodo.widget.WidgetBridge;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    private WidgetBridge widgetBridge;
    private android.content.BroadcastReceiver downloadReceiver;
    private int lastTopDp = -1;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            if (getBridge() != null && getBridge().getWebView() != null) {
                // 注册小组件与 Web 端数据及事件交互桥梁
                widgetBridge = new WidgetBridge(this, getBridge().getWebView());
                getBridge().getWebView().addJavascriptInterface(widgetBridge, "AndroidWidgetBridge");

                // 原生下载监听（直接调系统下载器，不跳浏览器）
                getBridge().getWebView().setDownloadListener(new DownloadListener() {
                    @Override
                    public void onDownloadStart(String url, String userAgent, String contentDisposition, String mimeType, long contentLength) {
                        try {
                            DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
                            if (dm != null && url != null && !url.isEmpty() && !url.startsWith("blob:") && !url.startsWith("data:")) {
                                DownloadManager.Request req = new DownloadManager.Request(Uri.parse(url));
                                req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                                String fileName = android.webkit.URLUtil.guessFileName(url, contentDisposition, mimeType);
                                req.setTitle(fileName != null && !fileName.isEmpty() ? fileName : "SuperTodo 更新");
                                req.setDescription("正在下载更新安装包…");
                                if (fileName != null && !fileName.isEmpty()) {
                                    req.setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS, fileName);
                                }
                                req.setMimeType("application/vnd.android.package-archive");
                                long id = dm.enqueue(req);
                                if (widgetBridge != null) {
                                    widgetBridge.setDownloadTask(id, fileName);
                                }
                            }
                        } catch (Exception ignore) {}
                    }
                });

                // 注册系统下载管理器广播接收器，精准响应下载完成并触发安装
                try {
                    downloadReceiver = new android.content.BroadcastReceiver() {
                        @Override
                        public void onReceive(android.content.Context context, Intent intent) {
                            if (DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(intent.getAction())) {
                                long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
                                if (widgetBridge != null && id > 0 && id == widgetBridge.getCurrentDownloadId()) {
                                    widgetBridge.handleDownloadComplete(id);
                                }
                            }
                        }
                    };
                    android.content.IntentFilter filter = new android.content.IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                        registerReceiver(downloadReceiver, filter, android.content.Context.RECEIVER_EXPORTED);
                    } else {
                        registerReceiver(downloadReceiver, filter);
                    }
                } catch (Throwable ignore) {}
            }

            // 只补齐 WebView 尚未覆盖的状态栏区域，避免 Capacitor 已下移 WebView 时重复留白。
            androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(getWindow().getDecorView(), (v, insets) -> {
                androidx.core.graphics.Insets sb = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.statusBars());
                if (getBridge() != null && getBridge().getWebView() != null) {
                    android.webkit.WebView webView = getBridge().getWebView();
                    webView.post(() -> {
                        int[] webViewLocation = new int[2];
                        webView.getLocationOnScreen(webViewLocation);
                        int uncoveredPx = Math.max(0, sb.top - Math.max(0, webViewLocation[1]));
                        float density = getResources().getDisplayMetrics().density;
                        int topDp = Math.round(uncoveredPx / density);
                        if (topDp == lastTopDp) return;
                        lastTopDp = topDp;
                        if (widgetBridge != null) widgetBridge.setStatusBarHeightDp(topDp);
                        webView.evaluateJavascript(
                            "document.documentElement.style.setProperty('--safe-t', '" + topDp + "px');",
                            null
                        );
                    });
                }
                return insets;
            });
            androidx.core.view.ViewCompat.requestApplyInsets(getWindow().getDecorView());
        } catch (Exception ignore) {}

        handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (widgetBridge != null) {
            widgetBridge.notifyAppResumed();
        }
    }

    @Override
    public void onConfigurationChanged(android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        try {
            int nightMode = newConfig.uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
            boolean isNight = (nightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES);
            if (getBridge() != null && getBridge().getWebView() != null) {
                getBridge().getWebView().post(() -> {
                    getBridge().getWebView().evaluateJavascript(
                        "if(window.onNativeSystemThemeChanged) window.onNativeSystemThemeChanged(" + isNight + ");",
                        null
                    );
                });
            }
        } catch (Throwable ignore) {}
    }

    private void handleIntent(Intent intent) {
        if (intent == null) return;
        String action = intent.getStringExtra("widget_action");
        String itemId = intent.getStringExtra("widget_item_id");
        if (action == null && intent.getData() != null) {
            Uri data = intent.getData();
            if ("supertodo".equalsIgnoreCase(data.getScheme())) {
                String host = data.getHost();
                if ("quadrant".equalsIgnoreCase(host)) {
                    action = "open_quadrant";
                } else if ("item".equalsIgnoreCase(host)) {
                    action = "open_item";
                    itemId = data.getQueryParameter("id");
                }
            }
        }
        if (widgetBridge != null && action != null) {
            widgetBridge.dispatchAction(action, itemId);
        }

        // 响应来自其他应用（微信、QQ、文件管理器等）打开或发送的 JSON 备份文件
        handleIncomingFileIntent(intent);
    }

    private void handleIncomingFileIntent(Intent intent) {
        if (intent == null) return;
        String intentAction = intent.getAction();
        if (Intent.ACTION_VIEW.equals(intentAction) || Intent.ACTION_SEND.equals(intentAction)) {
            Uri targetUri = intent.getData();
            if (targetUri == null && Intent.ACTION_SEND.equals(intentAction)) {
                try {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                        targetUri = intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri.class);
                    } else {
                        targetUri = (Uri) intent.getParcelableExtra(Intent.EXTRA_STREAM);
                    }
                } catch (Throwable ignore) {}
            }

            if (targetUri != null) {
                // 排除应用自定义的 supertodo:// 内部协议
                if ("supertodo".equalsIgnoreCase(targetUri.getScheme())) {
                    return;
                }
                final Uri fileUri = targetUri;
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        final String jsonContent = readTextFromUri(fileUri);
                        if (jsonContent != null && !jsonContent.trim().isEmpty()) {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    if (widgetBridge != null) {
                                        widgetBridge.dispatchImportJson(jsonContent);
                                    }
                                }
                            });
                        }
                    }
                }).start();
            } else if (Intent.ACTION_SEND.equals(intentAction)) {
                String sharedText = intent.getStringExtra(Intent.EXTRA_TEXT);
                if (sharedText != null && sharedText.trim().startsWith("{") && sharedText.trim().endsWith("}")) {
                    if (widgetBridge != null) {
                        widgetBridge.dispatchImportJson(sharedText);
                    }
                }
            }
        }
    }

    private String readTextFromUri(Uri uri) {
        if (uri == null) return null;
        try (java.io.InputStream is = getContentResolver().openInputStream(uri);
             java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(is, java.nio.charset.StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            char[] buffer = new char[8192];
            int read;
            while ((read = reader.read(buffer, 0, buffer.length)) != -1) {
                sb.append(buffer, 0, read);
            }
            return sb.toString();
        } catch (Throwable t) {
            return null;
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (downloadReceiver != null) {
            try {
                unregisterReceiver(downloadReceiver);
            } catch (Throwable ignore) {}
            downloadReceiver = null;
        }
    }
}
