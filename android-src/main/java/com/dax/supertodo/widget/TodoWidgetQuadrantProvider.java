package com.dax.supertodo.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.StrikethroughSpan;
import android.view.View;
import android.widget.RemoteViews;
import com.dax.supertodo.MainActivity;
import com.dax.supertodo.R;

import java.util.List;

public class TodoWidgetQuadrantProvider extends AppWidgetProvider {

    public static final String ACTION_QUADRANT_CLICK = "com.dax.supertodo.ACTION_QUADRANT_CLICK";

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId);
        }
    }

    public static void updateAppWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_quadrant);

        renderQuadrant(context, views, appWidgetId, "q1");
        renderQuadrant(context, views, appWidgetId, "q2");
        renderQuadrant(context, views, appWidgetId, "q3");
        renderQuadrant(context, views, appWidgetId, "q4");

        // 点击四象限卡片打开应用四象限配置页
        Intent openAppIntent = new Intent(context, MainActivity.class);
        openAppIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        openAppIntent.putExtra("widget_action", "open_quadrant");
        openAppIntent.setData(Uri.parse("supertodo://quadrant?id=" + appWidgetId));
        PendingIntent openPI = PendingIntent.getActivity(
            context,
            appWidgetId,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        views.setOnClickPendingIntent(R.id.qw_card_q1, openPI);
        views.setOnClickPendingIntent(R.id.qw_card_q2, openPI);
        views.setOnClickPendingIntent(R.id.qw_card_q3, openPI);
        views.setOnClickPendingIntent(R.id.qw_card_q4, openPI);

        appWidgetManager.updateAppWidget(appWidgetId, views);
    }

    private static void renderQuadrant(Context context, RemoteViews views, int appWidgetId, String qKey) {
        List<TodoItem> items = WidgetDataManager.loadQuadrantItems(context, qKey);
        int total = items.size();

        int countRes = getResId(context, qKey + "_count", "id");
        int emptyRes = getResId(context, qKey + "_empty", "id");
        int boxRes = getResId(context, qKey + "_items_box", "id");

        if (countRes > 0) {
            views.setTextViewText(countRes, total + "/4");
        }

        if (total == 0) {
            if (emptyRes > 0) views.setViewVisibility(emptyRes, View.VISIBLE);
            if (boxRes > 0) views.setViewVisibility(boxRes, View.GONE);
            return;
        }

        if (emptyRes > 0) views.setViewVisibility(emptyRes, View.GONE);
        if (boxRes > 0) views.setViewVisibility(boxRes, View.VISIBLE);

        for (int i = 1; i <= 4; i++) {
            int itemRowRes = getResId(context, qKey + "_item_" + i, "id");
            int chkRes = getResId(context, qKey + "_chk_" + i, "id");
            int titleRes = getResId(context, qKey + "_title_" + i, "id");
            int doneRes = getResId(context, qKey + "_done_" + i, "id");

            if (itemRowRes <= 0) continue;

            if (i <= total) {
                TodoItem item = items.get(i - 1);
                views.setViewVisibility(itemRowRes, View.VISIBLE);

                String title = item.title != null ? item.title : "";
                if (item.done) {
                    SpannableString ss = new SpannableString(title);
                    ss.setSpan(new StrikethroughSpan(), 0, ss.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    if (titleRes > 0) views.setViewVisibility(titleRes, View.GONE);
                    if (doneRes > 0) {
                        views.setViewVisibility(doneRes, View.VISIBLE);
                        views.setTextViewText(doneRes, ss);
                    }
                    if (chkRes > 0) views.setImageViewResource(chkRes, R.drawable.widget_ic_check_box_checked);
                } else {
                    if (doneRes > 0) views.setViewVisibility(doneRes, View.GONE);
                    if (titleRes > 0) {
                        views.setViewVisibility(titleRes, View.VISIBLE);
                        views.setTextViewText(titleRes, title);
                    }
                    if (chkRes > 0) views.setImageViewResource(chkRes, R.drawable.widget_ic_check_box_unchecked);
                }

                // 点击勾选框切换完成状态
                Intent toggleIntent = new Intent(context, TodoWidgetQuadrantProvider.class);
                toggleIntent.setAction(ACTION_QUADRANT_CLICK);
                toggleIntent.putExtra("qKey", qKey);
                toggleIntent.putExtra("itemId", item.id);
                toggleIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
                toggleIntent.setData(Uri.parse("supertodo://quadrant/" + qKey + "/" + item.id));

                PendingIntent chkPI = PendingIntent.getBroadcast(
                    context,
                    (appWidgetId * 100) + (qKey.hashCode() % 10) * 10 + i,
                    toggleIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                );
                if (chkRes > 0) views.setOnClickPendingIntent(chkRes, chkPI);

            } else {
                views.setViewVisibility(itemRowRes, View.GONE);
            }
        }
    }

    private static int getResId(Context context, String name, String type) {
        return context.getResources().getIdentifier(name, type, context.getPackageName());
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        String action = intent != null ? intent.getAction() : null;
        if (action == null) return;

        if (ACTION_QUADRANT_CLICK.equals(action)) {
            String qKey = intent.getStringExtra("qKey");
            String itemId = intent.getStringExtra("itemId");
            if (qKey != null && itemId != null) {
                boolean changed = WidgetDataManager.toggleQuadrantItemDone(context, qKey, itemId);
                if (changed) {
                    AppWidgetManager mgr = AppWidgetManager.getInstance(context);
                    if (mgr != null) {
                        ComponentName cn = new ComponentName(context, TodoWidgetQuadrantProvider.class);
                        int[] ids = mgr.getAppWidgetIds(cn);
                        if (ids != null) {
                            for (int id : ids) {
                                updateAppWidget(context, mgr, id);
                            }
                        }
                    }
                }
            }
        } else if (WidgetDataManager.ACTION_REFRESH_WIDGET.equals(action)) {
            AppWidgetManager mgr = AppWidgetManager.getInstance(context);
            if (mgr != null) {
                ComponentName cn = new ComponentName(context, TodoWidgetQuadrantProvider.class);
                int[] ids = mgr.getAppWidgetIds(cn);
                if (ids != null) {
                    for (int id : ids) {
                        updateAppWidget(context, mgr, id);
                    }
                }
            }
        }
    }

    @Override
    public void onDeleted(Context context, int[] appWidgetIds) {
        // cleanup if needed
    }
}
