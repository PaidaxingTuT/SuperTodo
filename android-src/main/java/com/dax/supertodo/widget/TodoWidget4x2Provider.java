package com.dax.supertodo.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.View;
import android.widget.RemoteViews;
import com.dax.supertodo.MainActivity;
import com.dax.supertodo.R;

import java.util.List;

public class TodoWidget4x2Provider extends AppWidgetProvider {

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId);
        }
    }

    public static void updateAppWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_4x2);

        // 设置小组件标题与当前分类标签
        String filterTitle = WidgetDataManager.getWidgetFilterTitle(context, appWidgetId);
        views.setTextViewText(R.id.widget_filter_badge, filterTitle);

        List<TodoItem> items = WidgetDataManager.loadTasksForWidget(context, appWidgetId);
        int activeCount = 0;
        for (TodoItem it : items) {
            if (!it.done) activeCount++;
        }
        views.setTextViewText(R.id.widget_count_text, activeCount + " 项待办");

        // 绑定列表数据源 Service
        Intent serviceIntent = new Intent(context, TodoWidgetService.class);
        serviceIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        serviceIntent.setData(Uri.parse(serviceIntent.toUri(Intent.URI_INTENT_SCHEME)));
        views.setRemoteAdapter(R.id.widget_list, serviceIntent);
        views.setEmptyView(R.id.widget_list, R.id.widget_empty_view);

        // 设置列表项点击事件模板（勾选完成或打开事项）
        Intent clickIntent = new Intent(context, TodoWidget4x2Provider.class);
        clickIntent.setAction(WidgetDataManager.ACTION_WIDGET_CLICK);
        clickIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        PendingIntent clickPI = PendingIntent.getBroadcast(
            context,
            appWidgetId,
            clickIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE
        );
        views.setPendingIntentTemplate(R.id.widget_list, clickPI);

        // 设置按钮（长按或点击设置齿轮均可配置）
        Intent configIntent = new Intent(context, WidgetConfigActivity.class);
        configIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        configIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent configPI = PendingIntent.getActivity(
            context,
            2000 + appWidgetId,
            configIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        views.setOnClickPendingIntent(R.id.btn_widget_settings, configPI);

        // 快速新增事项按钮（直达 App 新建弹窗）
        Intent addIntent = new Intent(context, MainActivity.class);
        addIntent.putExtra("widget_action", "add_item");
        addIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent addPI = PendingIntent.getActivity(
            context,
            3000 + appWidgetId,
            addIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        views.setOnClickPendingIntent(R.id.btn_widget_add, addPI);

        // 点击标题栏或空状态卡片打开 App
        Intent mainIntent = new Intent(context, MainActivity.class);
        mainIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent mainPI = PendingIntent.getActivity(
            context,
            4000 + appWidgetId,
            mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        views.setOnClickPendingIntent(R.id.widget_header_title, mainPI);
        views.setOnClickPendingIntent(R.id.widget_empty_view, mainPI);

        appWidgetManager.updateAppWidget(appWidgetId, views);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        String action = intent != null ? intent.getAction() : null;
        if (action == null) return;

        if (WidgetDataManager.ACTION_WIDGET_CLICK.equals(action)) {
            String itemAction = intent.getStringExtra("extra_action");
            String itemId = intent.getStringExtra("extra_item_id");
            if ("toggle_done".equals(itemAction) && itemId != null) {
                // 直接在小组件上打勾已完成
                WidgetDataManager.toggleItemDone(context, itemId);
                WidgetDataManager.notifyAllWidgets(context);
            } else if ("open_item".equals(itemAction) && itemId != null) {
                // 点击事项主体，打开 App 并跳转该事项
                Intent mainIntent = new Intent(context, MainActivity.class);
                mainIntent.putExtra("widget_action", "open_item");
                mainIntent.putExtra("widget_item_id", itemId);
                mainIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                context.startActivity(mainIntent);
            }
        } else if (WidgetDataManager.ACTION_REFRESH_WIDGET.equals(action)) {
            // 手动刷新或数据变更刷新
            AppWidgetManager mgr = AppWidgetManager.getInstance(context);
            if (mgr != null) {
                ComponentName cn = new ComponentName(context, TodoWidget4x2Provider.class);
                int[] ids = mgr.getAppWidgetIds(cn);
                if (ids != null && ids.length > 0) {
                    mgr.notifyAppWidgetViewDataChanged(ids, R.id.widget_list);
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
