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

/**
 * 4x4 大组件 Provider
 * 1~5 项时采用等权重槽位算法（100%均分纵向空间，彻底杜绝空白留隙）
 * 6 项及以上时启用滚动列表
 */
public class TodoWidget4x4Provider extends AppWidgetProvider {

    public static void updateAppWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_4x4);

        int width = WidgetDataManager.getWidgetWidth(context, appWidgetManager, appWidgetId, 280);
        int height = WidgetDataManager.getWidgetHeight(context, appWidgetManager, appWidgetId, 280);

        List<TodoItem> items = WidgetDataManager.loadTasksForWidget(context, appWidgetId);
        int activeCount = 0;
        int doneCount = 0;
        for (TodoItem it : items) {
            if (it.done) doneCount++;
            else activeCount++;
        }

        // 1. 判定是否处于 1x1 极小紧凑尺寸 (宽 <= 110dp 且 高 <= 110dp)
        boolean isCompact = width <= 110 && height <= 110;
        if (isCompact) {
            views.setViewVisibility(R.id.widget_header, View.GONE);
            views.setViewVisibility(R.id.widget_count_text, View.GONE);
            views.setViewVisibility(R.id.widget_slots_container, View.GONE);
            views.setViewVisibility(R.id.widget_list, View.GONE);
            views.setViewVisibility(R.id.widget_empty_view, View.GONE);
            views.setViewVisibility(R.id.widget_compact_layout, View.VISIBLE);
            views.setTextViewText(R.id.compact_count_text, String.valueOf(activeCount));

            Intent mainIntent = new Intent(context, MainActivity.class);
            mainIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent mainPI = PendingIntent.getActivity(
                context,
                1000 + appWidgetId,
                mainIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
            views.setOnClickPendingIntent(R.id.widget_compact_layout, mainPI);

            appWidgetManager.updateAppWidget(appWidgetId, views);
            return;
        }

        // 正常尺寸模式
        views.setViewVisibility(R.id.widget_compact_layout, View.GONE);
        views.setViewVisibility(R.id.widget_header, View.VISIBLE);

        // 获取分组与过滤标题
        String filterTitle = WidgetDataManager.getWidgetFilterTitle(context, appWidgetId);
        views.setTextViewText(R.id.widget_filter_badge, filterTitle);

        // 宽度弹性响应：根据宽度决定是否精简顶部操作栏与徽标
        if (width < 270) {
            views.setViewVisibility(R.id.widget_filter_badge, View.GONE);
        } else {
            views.setViewVisibility(R.id.widget_filter_badge, View.VISIBLE);
        }
        if (width < 220) {
            views.setViewVisibility(R.id.btn_widget_refresh, View.GONE);
            views.setViewVisibility(R.id.btn_widget_settings, View.GONE);
        } else {
            views.setViewVisibility(R.id.btn_widget_refresh, View.VISIBLE);
            views.setViewVisibility(R.id.btn_widget_settings, View.VISIBLE);
        }

        // 高度弹性响应：根据 height 决定统计条展示与最大槽位数
        boolean showCountHeader = height >= 140;
        if (showCountHeader) {
            views.setViewVisibility(R.id.widget_count_text, View.VISIBLE);
            views.setTextViewText(R.id.widget_count_text, activeCount + " 项待办 · " + doneCount + " 项已完成");
        } else {
            views.setViewVisibility(R.id.widget_count_text, View.GONE);
        }

        // 高度梯度：动态决定槽位数
        int maxSlots = 5;
        if (height < 130) {
            maxSlots = 1;
        } else if (height < 175) {
            maxSlots = 2;
        } else if (height < 225) {
            maxSlots = 3;
        } else if (height < 280) {
            maxSlots = 4;
        } else {
            maxSlots = 5;
        }

        // 是否精简列表项中的次要标签（当宽度较窄或高度较紧凑时）
        boolean showItemDetails = width >= 210 && height >= 160;

        if (items.isEmpty()) {
            views.setViewVisibility(R.id.widget_slots_container, View.GONE);
            views.setViewVisibility(R.id.widget_list, View.GONE);
            views.setViewVisibility(R.id.widget_empty_view, View.VISIBLE);
        } else if (items.size() <= maxSlots) {
            // 在 maxSlots 范围内：启用全填充槽位容器，按权重完全占满纵向空间
            views.setViewVisibility(R.id.widget_slots_container, View.VISIBLE);
            views.setViewVisibility(R.id.widget_list, View.GONE);
            views.setViewVisibility(R.id.widget_empty_view, View.GONE);

            int count = Math.min(items.size(), maxSlots);
            int[] slotIds = { R.id.slot_item_1, R.id.slot_item_2, R.id.slot_item_3, R.id.slot_item_4, R.id.slot_item_5 };
            int[] checkIds = { R.id.slot_1_checkbox, R.id.slot_2_checkbox, R.id.slot_3_checkbox, R.id.slot_4_checkbox, R.id.slot_5_checkbox };
            int[] titleIds = { R.id.slot_1_title, R.id.slot_2_title, R.id.slot_3_title, R.id.slot_4_title, R.id.slot_5_title };
            int[] titleDoneIds = { R.id.slot_1_title_done, R.id.slot_2_title_done, R.id.slot_3_title_done, R.id.slot_4_title_done, R.id.slot_5_title_done };
            int[] tagIds = { R.id.slot_1_tag, R.id.slot_2_tag, R.id.slot_3_tag, R.id.slot_4_tag, R.id.slot_5_tag };
            int[] dueIds = { R.id.slot_1_due, R.id.slot_2_due, R.id.slot_3_due, R.id.slot_4_due, R.id.slot_5_due };
            int[] starIds = { R.id.slot_1_star, R.id.slot_2_star, R.id.slot_3_star, R.id.slot_4_star, R.id.slot_5_star };
            int[] costIds = { R.id.slot_1_cost, R.id.slot_2_cost, R.id.slot_3_cost, R.id.slot_4_cost, R.id.slot_5_cost };

            for (int i = 0; i < 5; i++) {
                if (i < count) {
                    views.setViewVisibility(slotIds[i], View.VISIBLE);
                    TodoItem it = items.get(i);
                    String title = it.title != null ? it.title : "";

                    if (it.done) {
                        SpannableString ss = new SpannableString(title);
                        ss.setSpan(new StrikethroughSpan(), 0, ss.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        views.setViewVisibility(titleIds[i], View.GONE);
                        views.setViewVisibility(titleDoneIds[i], View.VISIBLE);
                        views.setTextViewText(titleDoneIds[i], ss);
                        views.setImageViewResource(checkIds[i], R.drawable.widget_ic_check_box_checked);
                    } else {
                        views.setViewVisibility(titleDoneIds[i], View.GONE);
                        views.setViewVisibility(titleIds[i], View.VISIBLE);
                        views.setTextViewText(titleIds[i], title);
                        views.setImageViewResource(checkIds[i], R.drawable.widget_ic_check_box_unchecked);
                    }

                    if (i == 0) {
                        if (count == 1 && it.note != null && !it.note.isEmpty()) {
                            views.setViewVisibility(R.id.slot_1_note, View.VISIBLE);
                            views.setTextViewText(R.id.slot_1_note, it.note);
                        } else {
                            views.setViewVisibility(R.id.slot_1_note, View.GONE);
                        }
                    }

                    String tag = "";
                    if (it.scene != null && !it.scene.isEmpty()) tag = it.scene;
                    else if (it.time != null && !it.time.isEmpty()) tag = it.time;
                    else if (it.type != null && !it.type.isEmpty() && !"全部".equals(it.type)) tag = it.type;

                    if (showItemDetails && !tag.isEmpty()) {
                        views.setViewVisibility(tagIds[i], View.VISIBLE);
                        views.setTextViewText(tagIds[i], tag);
                    } else {
                        views.setViewVisibility(tagIds[i], View.GONE);
                    }

                    if (showItemDetails && it.due != null && !it.due.isEmpty()) {
                        views.setViewVisibility(dueIds[i], View.VISIBLE);
                        views.setTextViewText(dueIds[i], it.due);
                    } else {
                        views.setViewVisibility(dueIds[i], View.GONE);
                    }

                    if (showItemDetails && it.star > 0) {
                        StringBuilder sb = new StringBuilder();
                        for (int s = 0; s < it.star; s++) sb.append("★");
                        views.setViewVisibility(starIds[i], View.VISIBLE);
                        views.setTextViewText(starIds[i], sb.toString());
                    } else {
                        views.setViewVisibility(starIds[i], View.GONE);
                    }

                    if (showItemDetails && it.hasCost && it.cost > 0) {
                        String costStr;
                        if (it.cost == (long) it.cost) {
                            costStr = "¥" + ((long) it.cost);
                        } else {
                            costStr = String.format(java.util.Locale.CHINA, "¥%.2f", it.cost);
                        }
                        views.setViewVisibility(costIds[i], View.VISIBLE);
                        views.setTextViewText(costIds[i], costStr);
                    } else {
                        views.setViewVisibility(costIds[i], View.GONE);
                    }

                    Intent toggleIntent = new Intent(context, TodoWidget4x4Provider.class);
                    toggleIntent.setAction(WidgetDataManager.ACTION_WIDGET_CLICK);
                    toggleIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
                    toggleIntent.putExtra("extra_action", "toggle_done");
                    toggleIntent.putExtra("extra_item_id", it.id);
                    PendingIntent togglePI = PendingIntent.getBroadcast(
                        context,
                        appWidgetId * 1000 + i * 2,
                        toggleIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE
                    );
                    views.setOnClickPendingIntent(checkIds[i], togglePI);

                    Intent openIntent = new Intent(context, TodoWidget4x4Provider.class);
                    openIntent.setAction(WidgetDataManager.ACTION_WIDGET_CLICK);
                    openIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
                    openIntent.putExtra("extra_action", "open_item");
                    openIntent.putExtra("extra_item_id", it.id);
                    PendingIntent openPI = PendingIntent.getBroadcast(
                        context,
                        appWidgetId * 1000 + i * 2 + 1,
                        openIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE
                    );
                    views.setOnClickPendingIntent(slotIds[i], openPI);
                } else {
                    views.setViewVisibility(slotIds[i], View.GONE);
                }
            }
        } else {
            // >= 6 项：切换为 ListView 滚动列表
            views.setViewVisibility(R.id.widget_slots_container, View.GONE);
            views.setViewVisibility(R.id.widget_list, View.VISIBLE);
            views.setViewVisibility(R.id.widget_empty_view, View.GONE);

            Intent serviceIntent = new Intent(context, TodoWidgetService.class);
            serviceIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
            serviceIntent.putExtra("is_4x4", true);
            serviceIntent.setData(Uri.parse(serviceIntent.toUri(Intent.URI_INTENT_SCHEME)));
            views.setRemoteAdapter(R.id.widget_list, serviceIntent);
            views.setEmptyView(R.id.widget_list, R.id.widget_empty_view);

            Intent clickIntent = new Intent(context, TodoWidget4x4Provider.class);
            clickIntent.setAction(WidgetDataManager.ACTION_WIDGET_CLICK);
            clickIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
            PendingIntent clickPI = PendingIntent.getBroadcast(
                context,
                appWidgetId,
                clickIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE
            );
            views.setPendingIntentTemplate(R.id.widget_list, clickPI);
        }

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

        // 点击刷新按钮
        Intent refreshIntent = new Intent(context, TodoWidget4x4Provider.class);
        refreshIntent.setAction(WidgetDataManager.ACTION_REFRESH_WIDGET);
        PendingIntent refreshPI = PendingIntent.getBroadcast(
            context,
            4000 + appWidgetId,
            refreshIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        views.setOnClickPendingIntent(R.id.btn_widget_refresh, refreshPI);

        // 点击标题区域打开 App
        Intent mainIntent = new Intent(context, MainActivity.class);
        mainIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent mainPI = PendingIntent.getActivity(
            context,
            1000 + appWidgetId,
            mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        views.setOnClickPendingIntent(R.id.widget_header_title, mainPI);

        appWidgetManager.updateAppWidget(appWidgetId, views);
        appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.widget_list);
    }

    public static void updateWidgets(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId);
        }
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        updateWidgets(context, appWidgetManager, appWidgetIds);
    }

    @Override
    public void onAppWidgetOptionsChanged(Context context, AppWidgetManager appWidgetManager, int appWidgetId, android.os.Bundle newOptions) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions);
        updateAppWidget(context, appWidgetManager, appWidgetId);
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
                WidgetDataManager.toggleItemDone(context, itemId);
                WidgetDataManager.notifyAllWidgets(context);
            } else if ("open_item".equals(itemAction) && itemId != null) {
                Intent mainIntent = new Intent(context, MainActivity.class);
                mainIntent.putExtra("widget_action", "open_item");
                mainIntent.putExtra("widget_item_id", itemId);
                mainIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                context.startActivity(mainIntent);
            }
        } else if (WidgetDataManager.ACTION_REFRESH_WIDGET.equals(action)) {
            AppWidgetManager mgr = AppWidgetManager.getInstance(context);
            if (mgr != null) {
                ComponentName cn = new ComponentName(context, TodoWidget4x4Provider.class);
                int[] ids = mgr.getAppWidgetIds(cn);
                if (ids != null && ids.length > 0) {
                    for (int id : ids) {
                        updateAppWidget(context, mgr, id);
                    }
                    mgr.notifyAppWidgetViewDataChanged(ids, R.id.widget_list);
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
