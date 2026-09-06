package com.dax.supertodo.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.RemoteViews;
import com.dax.supertodo.MainActivity;
import com.dax.supertodo.R;

import java.util.List;

public class TodoWidgetQuadrant1x1Provider extends AppWidgetProvider {

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
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_quadrant_1x1);

        List<TodoItem> itemsQ1 = WidgetDataManager.loadQuadrantItems(context, "q1");
        List<TodoItem> itemsQ2 = WidgetDataManager.loadQuadrantItems(context, "q2");
        List<TodoItem> itemsQ3 = WidgetDataManager.loadQuadrantItems(context, "q3");
        List<TodoItem> itemsQ4 = WidgetDataManager.loadQuadrantItems(context, "q4");

        int q1Count = countActive(itemsQ1);
        int q2Count = countActive(itemsQ2);
        int q3Count = countActive(itemsQ3);
        int q4Count = countActive(itemsQ4);

        views.setTextViewText(R.id.qw_compact_q1, String.valueOf(q1Count));
        views.setTextViewText(R.id.qw_compact_q2, String.valueOf(q2Count));
        views.setTextViewText(R.id.qw_compact_q3, String.valueOf(q3Count));
        views.setTextViewText(R.id.qw_compact_q4, String.valueOf(q4Count));

        Intent openAppIntent = new Intent(context, MainActivity.class);
        openAppIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        openAppIntent.putExtra("widget_action", "open_quadrant");
        openAppIntent.setData(Uri.parse("supertodo://quadrant1x1?id=" + appWidgetId));
        PendingIntent openPI = PendingIntent.getActivity(
            context,
            6000 + appWidgetId,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        views.setOnClickPendingIntent(R.id.qw_compact_layout, openPI);

        appWidgetManager.updateAppWidget(appWidgetId, views);
    }

    private static int countActive(List<TodoItem> list) {
        if (list == null) return 0;
        int count = 0;
        for (TodoItem it : list) {
            if (!it.done) count++;
        }
        return count;
    }
}
