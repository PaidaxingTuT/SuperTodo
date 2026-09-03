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
    private int lastTopDp = -1;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            if (getBridge() != null && getBridge().getWebView() != null) {
                // 原生下载监听（直接调系统下载器，不跳浏览器）
                getBridge().getWebView().setDownloadListener(new DownloadListener() {
                    @Override
                    public void onDownloadStart(String url, String userAgent, String contentDisposition, String mimeType, long contentLength) {
                        try {
                            DownloadManager.Request req = new DownloadManager.Request(Uri.parse(url));
                            req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                            req.setTitle("SuperTodo 更新");
                            ((DownloadManager) getSystemService(DOWNLOAD_SERVICE)).enqueue(req);
                        } catch (Exception ignore) {}
                    }
                });

                // 注册小组件与 Web 端数据及事件交互桥梁
                widgetBridge = new WidgetBridge(this, getBridge().getWebView());
                getBridge().getWebView().addJavascriptInterface(widgetBridge, "AndroidWidgetBridge");
            }

            // 解决 Android 15 (Target SDK 35) / 小米澎湃 3 (HyperOS 2/3) 强制 Edge-to-Edge 导致状态栏与顶部栏重叠遮挡（防高频调用与内存卡顿）
            androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(getWindow().getDecorView(), (v, insets) -> {
                androidx.core.graphics.Insets sb = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.statusBars());
                if (sb.top > 0) {
                    float density = getResources().getDisplayMetrics().density;
                    int topDp = Math.round(sb.top / density);
                    if (topDp != lastTopDp) {
                        lastTopDp = topDp;
                        if (getBridge() != null && getBridge().getWebView() != null) {
                            getBridge().getWebView().post(() -> {
                                getBridge().getWebView().evaluateJavascript(
                                    "document.documentElement.style.setProperty('--safe-t', '" + topDp + "px');",
                                    null
                                );
                            });
                        }
                    }
                }
                return insets;
            });
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
        if (widgetBridge != null && action != null) {
            widgetBridge.dispatchAction(action, itemId);
        }
    }
}
