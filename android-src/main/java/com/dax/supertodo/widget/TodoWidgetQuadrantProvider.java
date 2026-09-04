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

    @Override
    public void onAppWidgetOptionsChanged(Context context, AppWidgetManager appWidgetManager, int appWidgetId, android.os.Bundle newOptions) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions);
        updateAppWidget(context, appWidgetManager, appWidgetId);
    }

    public static void updateAppWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_quadrant);

        int width = WidgetDataManager.getWidgetWidth(context, appWidgetManager, appWidgetId, 280);
        int height = WidgetDataManager.getWidgetHeight(context, appWidgetManager, appWidgetId, 280);

        List<TodoItem> itemsQ1 = WidgetDataManager.loadQuadrantItems(context, "q1");
        List<TodoItem> itemsQ2 = WidgetDataManager.loadQuadrantItems(context, "q2");
        List<TodoItem> itemsQ3 = WidgetDataManager.loadQuadrantItems(context, "q3");
        List<TodoItem> itemsQ4 = WidgetDataManager.loadQuadrantItems(context, "q4");

        // 1. 判定是否处于 1x1 极小紧凑尺寸 (宽 <= 110dp 且 高 <= 110dp)
        boolean isCompact = width <= 110 && height <= 110;
        if (isCompact) {
            views.setViewVisibility(R.id.qw_grid_container, View.GONE);
            views.setViewVisibility(R.id.qw_axis_x, View.GONE);
            views.setViewVisibility(R.id.qw_axis_y, View.GONE);
            views.setViewVisibility(R.id.qw_pill_top, View.GONE);
            views.setViewVisibility(R.id.qw_pill_bottom, View.GONE);
            views.setViewVisibility(R.id.qw_pill_left, View.GONE);
            views.setViewVisibility(R.id.qw_pill_right, View.GONE);
            views.setViewVisibility(R.id.qw_compact_layout, View.VISIBLE);

            // 统计未完成数量
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
            openAppIntent.setData(Uri.parse("supertodo://quadrant?id=" + appWidgetId));
            PendingIntent openPI = PendingIntent.getActivity(
                context,
                appWidgetId,
                openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
            views.setOnClickPendingIntent(R.id.qw_compact_layout, openPI);

            appWidgetManager.updateAppWidget(appWidgetId, views);
            return;
        }

        // 正常尺寸模式
        views.setViewVisibility(R.id.qw_compact_layout, View.GONE);
        views.setViewVisibility(R.id.qw_grid_container, View.VISIBLE);

        // 用户要求：必须保留十字坐标轴！无论拉大还是缩小，坐标轴都清晰分界四象限
        views.setViewVisibility(R.id.qw_axis_x, View.VISIBLE);
        views.setViewVisibility(R.id.qw_axis_y, View.VISIBLE);

        // 仅在宽与高均较充裕（>= 210dp）时展示四周“紧急/不紧急/重要/不重要”方向文字
        boolean showPills = width >= 210 && height >= 210;
        int pillVis = showPills ? View.VISIBLE : View.GONE;
        views.setViewVisibility(R.id.qw_pill_top, pillVis);
        views.setViewVisibility(R.id.qw_pill_bottom, pillVis);
        views.setViewVisibility(R.id.qw_pill_left, pillVis);
        views.setViewVisibility(R.id.qw_pill_right, pillVis);

        // 中间网格空隙间隔：根据尺寸微调
        int spacerVis = (width >= 190 && height >= 190) ? View.VISIBLE : View.GONE;
        views.setViewVisibility(R.id.qw_spacer_top_col, spacerVis);
        views.setViewVisibility(R.id.qw_spacer_mid_row, spacerVis);
        views.setViewVisibility(R.id.qw_spacer_bottom_col, spacerVis);

        // 2x2（例如 height < 200 或 width < 200）紧凑时，不要象限标题文本，避免遮挡和挤压空间
        // 标题隐藏后，通过让计划文字变色（各象限专属颜色）来实现即使没有标题也能清晰区分四象限
        boolean showHeaders = width >= 200 && height >= 200;

        // 控制各象限标题容器显隐
        int headerVis = showHeaders ? View.VISIBLE : View.GONE;
        views.setViewVisibility(R.id.qw_q1_header, headerVis);
        views.setViewVisibility(R.id.qw_q2_header, headerVis);
        views.setViewVisibility(R.id.qw_q3_header, headerVis);
        views.setViewVisibility(R.id.qw_q4_header, headerVis);

        // 高度梯度：计算每象限最多容纳的事项行数
        int maxPerQuadrant = 4;
        if (height < 150) {
            maxPerQuadrant = 1;
        } else if (height < 230) {
            maxPerQuadrant = 2;
        } else if (height < 300) {
            maxPerQuadrant = 3;
        } else {
            maxPerQuadrant = 4;
        }

        renderQuadrant(context, views, appWidgetId, "q1", itemsQ1, maxPerQuadrant, !showHeaders);
        renderQuadrant(context, views, appWidgetId, "q2", itemsQ2, maxPerQuadrant, !showHeaders);
        renderQuadrant(context, views, appWidgetId, "q3", itemsQ3, maxPerQuadrant, !showHeaders);
        renderQuadrant(context, views, appWidgetId, "q4", itemsQ4, maxPerQuadrant, !showHeaders);

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

    private static int countActive(List<TodoItem> list) {
        if (list == null) return 0;
        int c = 0;
        for (TodoItem it : list) {
            if (!it.done) c++;
        }
        return c;
    }

    private static void renderQuadrant(Context context, RemoteViews views, int appWidgetId, String qKey, List<TodoItem> items, int maxItems, boolean colorizeTitle) {
        if (items == null) items = WidgetDataManager.loadQuadrantItems(context, qKey);
        int total = items.size();

        int emptyRes = getResId(context, qKey + "_empty", "id");
        int boxRes = getResId(context, qKey + "_items_box", "id");

        if (total == 0) {
            if (emptyRes > 0) views.setViewVisibility(emptyRes, View.VISIBLE);
            if (boxRes > 0) views.setViewVisibility(boxRes, View.GONE);
            return;
        }

        if (emptyRes > 0) views.setViewVisibility(emptyRes, View.GONE);
        if (boxRes > 0) views.setViewVisibility(boxRes, View.VISIBLE);

        int qColorRes = getResId(context, "qw_" + qKey + "_border", "color");
        int quadrantColor = 0;
        if (qColorRes > 0) {
            try {
                quadrantColor = androidx.core.content.ContextCompat.getColor(context, qColorRes);
            } catch (Throwable t) {
                try {
                    quadrantColor = context.getResources().getColor(qColorRes);
                } catch (Throwable ignore) {}
            }
        }

        for (int i = 1; i <= 4; i++) {
            int itemRowRes = getResId(context, qKey + "_item_" + i, "id");
            int chkRes = getResId(context, qKey + "_chk_" + i, "id");
            int titleRes = getResId(context, qKey + "_title_" + i, "id");
            int doneRes = getResId(context, qKey + "_done_" + i, "id");

            if (itemRowRes <= 0) continue;

            if (i <= total && i <= maxItems) {
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
                        if (colorizeTitle && quadrantColor != 0) {
                            // 标题隐藏时，计划文字直接染上当前象限颜色（红/橙/绿/蓝），一目了然区分象限
                            views.setTextColor(titleRes, quadrantColor);
                        } else {
                            int defaultTextRes = getResId(context, "widget_text_primary", "color");
                            if (defaultTextRes > 0) {
                                try {
                                    int defColor = androidx.core.content.ContextCompat.getColor(context, defaultTextRes);
                                    views.setTextColor(titleRes, defColor);
                                } catch (Throwable ignore) {}
                            }
                        }
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
                if (titleRes > 0) views.setOnClickPendingIntent(titleRes, chkPI);
                if (doneRes > 0) views.setOnClickPendingIntent(doneRes, chkPI);
                if (itemRowRes > 0) views.setOnClickPendingIntent(itemRowRes, chkPI);

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
                    WidgetDataManager.notifyAllWidgets(context);
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
