package com.dax.supertodo.widget;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

public class Widget2x2DialogActivity extends Activity {

    private WebView mWebView;
    private String mMode = "list";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 设置全屏透明窗口
        Window window = getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                window.setStatusBarColor(Color.TRANSPARENT);
                window.setNavigationBarColor(Color.TRANSPARENT);
            }
        }

        Intent intent = getIntent();
        if (intent != null && intent.hasExtra("mode")) {
            mMode = intent.getStringExtra("mode");
        }

        FrameLayout root = new FrameLayout(this);
        root.setLayoutParams(new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ));
        root.setBackgroundColor(Color.TRANSPARENT);

        mWebView = new WebView(this);
        mWebView.setLayoutParams(new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ));
        mWebView.setBackgroundColor(Color.TRANSPARENT);
        mWebView.setOverScrollMode(View.OVER_SCROLL_NEVER);

        WebSettings settings = mWebView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setDefaultTextEncodingName("UTF-8");

        mWebView.addJavascriptInterface(new Widget2x2Bridge(), "Widget2x2Bridge");
        mWebView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return false;
            }
        });

        root.addView(mWebView);
        setContentView(root);

        loadDialogUrl(mMode);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent != null && intent.hasExtra("mode")) {
            mMode = intent.getStringExtra("mode");
            loadDialogUrl(mMode);
        }
    }

    private void loadDialogUrl(String mode) {
        if (mWebView == null) return;
        String safeMode = (mode != null && !mode.isEmpty()) ? mode : "list";
        mWebView.loadUrl("file:///android_asset/widget_dialog.html?mode=" + safeMode);
    }

    public class Widget2x2Bridge {
        @JavascriptInterface
        public String getWidgetData() {
            return WidgetDataManager.getWidgetData(Widget2x2DialogActivity.this);
        }

        @JavascriptInterface
        public void saveWidgetData(final String json) {
            runOnUiThread(() -> {
                WidgetDataManager.saveWidgetData(Widget2x2DialogActivity.this, json);
                WidgetDataManager.notifyAllWidgets(Widget2x2DialogActivity.this);
            });
        }

        @JavascriptInterface
        public void close() {
            runOnUiThread(() -> finish());
        }
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(0, android.R.anim.fade_out);
    }

    @Override
    public void onBackPressed() {
        if (mWebView != null) {
            mWebView.evaluateJavascript("if(typeof handleBackPressed === 'function') { handleBackPressed(); } else { window.Widget2x2Bridge.close(); }", null);
        } else {
            super.onBackPressed();
        }
    }
}
