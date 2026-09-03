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
    public boolean requestPinWidget(String size) {
        if (activity == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return false;
        }
        try {
            AppWidgetManager manager = activity.getSystemService(AppWidgetManager.class);
            if (manager == null || !manager.isRequestPinAppWidgetSupported()) {
                return false;
            }
            Class<?> providerClass = "4x4".equals(size) ? TodoWidget4x4Provider.class : TodoWidget4x2Provider.class;
            ComponentName provider = new ComponentName(activity, providerClass);
            return manager.requestPinAppWidget(provider, null, null);
        } catch (Exception e) {
            return false;
        }
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
        if (activity == null || webView == null) return;
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    String safeAction = action != null ? action : "";
                    String safeItemId = itemId != null ? itemId : "";
                    String script = "if(window.onNativeWidgetAction) window.onNativeWidgetAction('" + safeAction + "', '" + safeItemId + "');";
                    webView.evaluateJavascript(script, null);
                } catch (Exception ignore) {}
            }
        });
    }
}
