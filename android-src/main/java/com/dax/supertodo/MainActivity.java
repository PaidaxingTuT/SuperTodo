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

    private void handleIntent(Intent intent) {
        if (intent == null) return;
        String action = intent.getStringExtra("widget_action");
        String itemId = intent.getStringExtra("widget_item_id");
        if (widgetBridge != null && action != null) {
            widgetBridge.dispatchAction(action, itemId);
        }
    }
}
