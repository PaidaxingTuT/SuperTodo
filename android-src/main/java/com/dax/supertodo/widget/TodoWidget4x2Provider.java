package com.dax.supertodo.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.View;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.StrikethroughSpan;
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

    @Override
    public void onAppWidgetOptionsChanged(Context context, AppWidgetManager appWidgetManager, int appWidgetId, android.os.Bundle newOptions) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions);
        updateAppWidget(context, appWidgetManager, appWidgetId);
    }

    public static void updateAppWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_4x2);

        int width = WidgetDataManager.getWidgetWidth(context, appWidgetManager, appWidgetId, 250);
        int height = WidgetDataManager.getWidgetHeight(context, appWidgetManager, appWidgetId, 120);

        List<TodoItem> items = WidgetDataManager.loadTasksForWidget(context, appWidgetId);
        int activeCount = 0;
        for (TodoItem it : items) {
            if (!it.done) activeCount++;
        }

        // 判定是否处于 1x1 极小紧凑尺寸 (例如宽 <= 110dp 且 高 <= 110dp)
        boolean isCompact = width <= 110 && height <= 110;

        if (isCompact) {
            // 极简 1x1 模式：仅展示大数字待办计数和文本标签，点击直接打开 App
            views.setViewVisibility(R.id.widget_header, View.GONE);
            views.setViewVisibility(R.id.widget_list, View.GONE);
            views.setViewVisibility(R.id.widget_empty_view, View.GONE);
            views.setViewVisibility(R.id.widget_compact_layout, View.VISIBLE);
            views.setTextViewText(R.id.compact_count_text, String.valueOf(activeCount));

            Intent mainIntent = new Intent(context, MainActivity.class);
            mainIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent mainPI = PendingIntent.getActivity(
                context,
                4000 + appWidgetId,
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

        // 设置小组件标题与当前分类标签
        String filterTitle = WidgetDataManager.getWidgetFilterTitle(context, appWidgetId);
        views.setTextViewText(R.id.widget_filter_badge, filterTitle);
        views.setTextViewText(R.id.widget_count_text, activeCount + " 项待办");

        // 宽度弹性响应：空间较小时按用户需求隐藏次要元素
        // 如果宽度极窄（< 150dp，如 1x2 或竖向拖拽），精简至仅显示标题与添加按钮
        if (width < 250) {
            views.setViewVisibility(R.id.widget_filter_badge, View.GONE);
        } else {
            views.setViewVisibility(R.id.widget_filter_badge, View.VISIBLE);
        }

        if (width < 210) {
            views.setViewVisibility(R.id.widget_count_text, View.GONE);
            views.setViewVisibility(R.id.btn_widget_settings, View.GONE);
        } else {
            views.setViewVisibility(R.id.widget_count_text, View.VISIBLE);
            views.setViewVisibility(R.id.btn_widget_settings, View.VISIBLE);
        }

        // 高度弹性响应：
        // 当变成 4x1, 3x1, 2x1（height <= 85dp）时，不再显示事项内容，只保留顶部控制栏
        boolean showItems = height > 85;

        if (!showItems) {
            // 4x1, 3x1, 2x1 矮栏模式：完全隐藏槽位、列表与空状态
            views.setViewVisibility(R.id.widget_slots_container, View.GONE);
            views.setViewVisibility(R.id.widget_list, View.GONE);
            views.setViewVisibility(R.id.widget_empty_view, View.GONE);
        } else {
            // 需要展示事项：根据高度动态计算槽位数
            // 4x2 默认尺寸（通常高度约为 110~175dp）严格默认只展示 2 项
            // 只有用户主动将其纵向拉高到 4x3（> 180dp）或 4x4（> 250dp）时，才逐步扩展为 3 项和 4 项
            int maxSlots;
            if (height <= 180) {
                maxSlots = 2;
            } else if (height <= 250) {
                maxSlots = 3;
            } else {
                maxSlots = 4;
            }

            // 次要标签（tag/due/star）是否展示（较窄或较扁时不挤压标题）
            boolean showItemDetails = width >= 210 && height >= 125;

            if (items.isEmpty()) {
                views.setViewVisibility(R.id.widget_slots_container, View.GONE);
                views.setViewVisibility(R.id.widget_list, View.GONE);
                views.setViewVisibility(R.id.widget_empty_view, View.VISIBLE);
            } else if (items.size() <= maxSlots || maxSlots <= 4) {
                // 在 maxSlots 范围内或常规尺寸：启用全填充槽位容器（layout_weight="1"），按权重完全占满纵向空间
                views.setViewVisibility(R.id.widget_slots_container, View.VISIBLE);
                views.setViewVisibility(R.id.widget_list, View.GONE);
                views.setViewVisibility(R.id.widget_empty_view, View.GONE);

                int count = Math.min(items.size(), maxSlots);
                int[] slotIds = { R.id.slot_item_1, R.id.slot_item_2, R.id.slot_item_3, R.id.slot_item_4 };
                int[] checkIds = { R.id.slot_1_checkbox, R.id.slot_2_checkbox, R.id.slot_3_checkbox, R.id.slot_4_checkbox };
                int[] titleIds = { R.id.slot_1_title, R.id.slot_2_title, R.id.slot_3_title, R.id.slot_4_title };
                int[] titleDoneIds = { R.id.slot_1_title_done, R.id.slot_2_title_done, R.id.slot_3_title_done, R.id.slot_4_title_done };
                int[] tagIds = { R.id.slot_1_tag, R.id.slot_2_tag, R.id.slot_3_tag, R.id.slot_4_tag };
                int[] dueIds = { R.id.slot_1_due, R.id.slot_2_due, R.id.slot_3_due, R.id.slot_4_due };
                int[] starIds = { R.id.slot_1_star, R.id.slot_2_star, R.id.slot_3_star, R.id.slot_4_star };
                int[] costIds = { R.id.slot_1_cost, R.id.slot_2_cost, R.id.slot_3_cost, R.id.slot_4_cost };

                for (int i = 0; i < 4; i++) {
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

                        Intent toggleIntent = new Intent(context, TodoWidget4x2Provider.class);
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

                        Intent openIntent = new Intent(context, TodoWidget4x2Provider.class);
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
                // 超出 4 项且高度充裕：切换为 ListView 滚动列表
                views.setViewVisibility(R.id.widget_slots_container, View.GONE);
                views.setViewVisibility(R.id.widget_list, View.VISIBLE);
                views.setViewVisibility(R.id.widget_empty_view, View.GONE);

                Intent serviceIntent = new Intent(context, TodoWidgetService.class);
                serviceIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
                serviceIntent.putExtra("is_4x4", false);
                serviceIntent.setData(Uri.parse(serviceIntent.toUri(Intent.URI_INTENT_SCHEME)));
                views.setRemoteAdapter(R.id.widget_list, serviceIntent);
                views.setEmptyView(R.id.widget_list, R.id.widget_empty_view);

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
            }
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
        appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.widget_list);
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
