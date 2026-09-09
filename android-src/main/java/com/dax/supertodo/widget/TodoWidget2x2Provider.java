package com.dax.supertodo.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.RemoteViews;
import com.dax.supertodo.MainActivity;
import com.dax.supertodo.R;

import java.util.List;

public class TodoWidget2x2Provider extends AppWidgetProvider {

    public static final String ACTION_2X2_COMPLETE = "com.dax.supertodo.ACTION_2X2_COMPLETE";

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId);
        }
    }

    @Override
    public void onAppWidgetOptionsChanged(Context context, AppWidgetManager appWidgetManager, int appWidgetId, android.os.Bundle newOptions) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions);
        updateAppWidget(context, appWidgetManager, appWidgetId);
    }

    public static void updateAppWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_2x2);
        int themeColor = WidgetDataManager.getWidgetThemeColor(context);
        views.setTextColor(R.id.widget_2x2_count, themeColor);
        views.setTextColor(R.id.btn_2x2_expand, themeColor);
        views.setTextColor(R.id.btn_2x2_complete, themeColor);
        views.setTextColor(R.id.widget_2x2_hero_label, themeColor);
        views.setTextViewText(R.id.widget_2x2_header_title, "超级清单");

        List<TodoItem> items = WidgetDataManager.load2x2Items(context);
        int activeCount = 0;
        TodoItem nextItem = null;
        for (TodoItem it : items) {
            if (!it.done) {
                activeCount++;
                if (nextItem == null) {
                    nextItem = it;
                }
            }
        }

        views.setTextViewText(R.id.widget_2x2_count, "(" + activeCount + ")");

        if (nextItem != null) {
            views.setTextViewText(R.id.widget_2x2_hero_label, "下一项待办:");
            views.setTextViewText(R.id.widget_2x2_hero_title, nextItem.title);
            views.setViewVisibility(R.id.widget_2x2_hero_title, View.VISIBLE);
            views.setViewVisibility(R.id.widget_2x2_empty_view, View.GONE);

            Intent completeIntent = new Intent(context, TodoWidget2x2Provider.class);
            completeIntent.setAction(ACTION_2X2_COMPLETE);
            completeIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
            PendingIntent completePI = PendingIntent.getBroadcast(
                context,
                5000 + appWidgetId,
                completeIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE
            );
            views.setOnClickPendingIntent(R.id.btn_2x2_complete, completePI);
        } else {
            views.setTextViewText(R.id.widget_2x2_hero_label, "待办清单:");
            views.setTextViewText(R.id.widget_2x2_empty_view, "已全部完成");
            views.setViewVisibility(R.id.widget_2x2_hero_title, View.GONE);
            views.setViewVisibility(R.id.widget_2x2_empty_view, View.VISIBLE);

            Intent emptyIntent = new Intent(context, Widget2x2DialogActivity.class);
            emptyIntent.putExtra("mode", "list");
            emptyIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
            emptyIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent emptyPI = PendingIntent.getActivity(
                context,
                5000 + appWidgetId,
                emptyIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
            views.setOnClickPendingIntent(R.id.btn_2x2_complete, emptyPI);
        }

        // 展开清单：打开专属桌面悬浮窗 (mode = list)
        Intent expandIntent = new Intent(context, Widget2x2DialogActivity.class);
        expandIntent.putExtra("mode", "list");
        expandIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        expandIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent expandPI = PendingIntent.getActivity(
            context,
            6000 + appWidgetId,
            expandIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        views.setOnClickPendingIntent(R.id.btn_2x2_expand, expandPI);
        views.setOnClickPendingIntent(R.id.widget_2x2_body, expandPI);
        views.setOnClickPendingIntent(R.id.widget_2x2_root, expandPI);

        // 设置：打开专属待办排序与挑选桌面悬浮窗 (mode = settings)
        Intent configIntent = new Intent(context, Widget2x2DialogActivity.class);
        configIntent.putExtra("mode", "settings");
        configIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        configIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent configPI = PendingIntent.getActivity(
            context,
            7000 + appWidgetId,
            configIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        views.setOnClickPendingIntent(R.id.btn_2x2_settings, configPI);

        appWidgetManager.updateAppWidget(appWidgetId, views);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        String action = intent != null ? intent.getAction() : null;
        if (action == null) return;

        if (ACTION_2X2_COMPLETE.equals(action)) {
            WidgetDataManager.completeNext2x2Item(context);
            WidgetDataManager.notifyAllWidgets(context);
        } else if (WidgetDataManager.ACTION_REFRESH_WIDGET.equals(action)) {
            AppWidgetManager mgr = AppWidgetManager.getInstance(context);
            if (mgr != null) {
                ComponentName cn = new ComponentName(context, TodoWidget2x2Provider.class);
                int[] ids = mgr.getAppWidgetIds(cn);
                if (ids != null && ids.length > 0) {
                    for (int id : ids) {
                        updateAppWidget(context, mgr, id);
                    }
                }
            }
        }
    }

    @Override
    public void onDeleted(Context context, int[] appWidgetIds) {
        for (int id : appWidgetIds) {
            WidgetDataManager.removeWidgetConfig(context, id);
        }
    }
}
