package com.dax.supertodo.widget;

import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.StrikethroughSpan;
import android.view.View;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;
import com.dax.supertodo.R;

import java.util.ArrayList;
import java.util.List;

/**
 * 桌面小部件列表数据源服务（支持 4x2 基础小组件与 4x4 大组件动态自适应布局）
 */
public class TodoWidgetService extends RemoteViewsService {

    @Override
    public RemoteViewsFactory onGetViewFactory(Intent intent) {
        return new TodoRemoteViewsFactory(this.getApplicationContext(), intent);
    }

    static class TodoRemoteViewsFactory implements RemoteViewsFactory {
        private final Context context;
        private final int appWidgetId;
        private final boolean is4x4;
        private final List<TodoItem> items = new ArrayList<>();

        public TodoRemoteViewsFactory(Context context, Intent intent) {
            this.context = context;
            this.appWidgetId = intent != null ? intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID) : AppWidgetManager.INVALID_APPWIDGET_ID;
            this.is4x4 = intent != null && intent.getBooleanExtra("is_4x4", false);
        }

        @Override
        public void onCreate() {
            // 初始化由 onDataSetChanged 处理
        }

        @Override
        public void onDataSetChanged() {
            items.clear();
            try {
                List<TodoItem> loaded = WidgetDataManager.loadTasksForWidget(context, appWidgetId);
                if (loaded != null) {
                    items.addAll(loaded);
                }
            } catch (Throwable ignore) {}
        }

        @Override
        public void onDestroy() {
            items.clear();
        }

        @Override
        public int getCount() {
            return items.size();
        }

        @Override
        public int getViewTypeCount() {
            // 支持 6 种布局：标准版(6+或4x2) + 4x4独享的5种动态自适应规格(1/2/3/4/5)
            return 6;
        }

        private int selectLayoutRes(int totalCount) {
            if (!is4x4) {
                return R.layout.widget_item;
            }
            switch (totalCount) {
                case 1:
                    return R.layout.widget_item_4x4_1;
                case 2:
                    return R.layout.widget_item_4x4_2;
                case 3:
                    return R.layout.widget_item_4x4_3;
                case 4:
                    return R.layout.widget_item_4x4_4;
                case 5:
                    return R.layout.widget_item_4x4_5;
                default:
                    return R.layout.widget_item;
            }
        }

        @Override
        public RemoteViews getViewAt(int position) {
            if (position < 0 || position >= items.size()) return null;
            try {
                TodoItem item = items.get(position);
                int layoutRes = selectLayoutRes(items.size());
                RemoteViews rv = new RemoteViews(context.getPackageName(), layoutRes);

                String title = item.title != null ? item.title : "";

                // 标题与完成状态（绝不在 Java 层调用 rv.setTextColor 强行写入整型值，保证 Launcher 随系统日夜切换时毫秒级自动变色！）
                if (item.done) {
                    SpannableString ss = new SpannableString(title);
                    ss.setSpan(new StrikethroughSpan(), 0, ss.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    rv.setViewVisibility(R.id.item_title, View.GONE);
                    rv.setViewVisibility(R.id.item_title_done, View.VISIBLE);
                    rv.setTextViewText(R.id.item_title_done, ss);
                    rv.setImageViewResource(R.id.item_checkbox, R.drawable.widget_ic_check_box_checked);
                } else {
                    rv.setViewVisibility(R.id.item_title_done, View.GONE);
                    rv.setViewVisibility(R.id.item_title, View.VISIBLE);
                    rv.setTextViewText(R.id.item_title, title);
                    rv.setImageViewResource(R.id.item_checkbox, R.drawable.widget_ic_check_box_unchecked);
                }

                // 备注（仅在 4x4 单项卡片中显示）
                if (item.note != null && !item.note.isEmpty()) {
                    try {
                        rv.setViewVisibility(R.id.item_note, View.VISIBLE);
                        rv.setTextViewText(R.id.item_note, item.note);
                    } catch (Throwable ignore) {}
                } else {
                    try {
                        rv.setViewVisibility(R.id.item_note, View.GONE);
                    } catch (Throwable ignore) {}
                }

                // 标签（优先显示当前场景或时间）
                String tag = "";
                if (item.scene != null && !item.scene.isEmpty()) {
                    tag = item.scene;
                } else if (item.time != null && !item.time.isEmpty()) {
                    tag = item.time;
                } else if (item.type != null && !item.type.isEmpty() && !"全部".equals(item.type)) {
                    tag = item.type;
                }

                if (!tag.isEmpty()) {
                    rv.setViewVisibility(R.id.item_tag, View.VISIBLE);
                    rv.setTextViewText(R.id.item_tag, tag);
                } else {
                    rv.setViewVisibility(R.id.item_tag, View.GONE);
                }

                // 截止日期
                if (item.due != null && !item.due.isEmpty()) {
                    rv.setViewVisibility(R.id.item_due, View.VISIBLE);
                    rv.setTextViewText(R.id.item_due, item.due);
                } else {
                    rv.setViewVisibility(R.id.item_due, View.GONE);
                }

                // 重要程度 ★
                if (item.star > 0) {
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < item.star; i++) sb.append("★");
                    rv.setViewVisibility(R.id.item_star, View.VISIBLE);
                    rv.setTextViewText(R.id.item_star, sb.toString());
                } else {
                    rv.setViewVisibility(R.id.item_star, View.GONE);
                }

                // 价格（右侧展示，有价格才显示）
                if (item.hasCost && item.cost > 0) {
                    String costStr;
                    if (item.cost == (long) item.cost) {
                        costStr = "¥" + ((long) item.cost);
                    } else {
                        costStr = String.format(java.util.Locale.CHINA, "¥%.2f", item.cost);
                    }
                    rv.setViewVisibility(R.id.item_cost, View.VISIBLE);
                    rv.setTextViewText(R.id.item_cost, costStr);
                } else {
                    rv.setViewVisibility(R.id.item_cost, View.GONE);
                }

                // 1. 点击左侧勾选框：原地切换完成状态
                Intent toggleIntent = new Intent();
                toggleIntent.putExtra("extra_action", "toggle_done");
                toggleIntent.putExtra("extra_item_id", item.id);
                rv.setOnClickFillInIntent(R.id.item_checkbox, toggleIntent);

                // 2. 点击右侧文字信息主体：打开 App 并定位编辑
                Intent openIntent = new Intent();
                openIntent.putExtra("extra_action", "open_item");
                openIntent.putExtra("extra_item_id", item.id);
                rv.setOnClickFillInIntent(R.id.item_body, openIntent);
                rv.setOnClickFillInIntent(R.id.item_title, openIntent);
                rv.setOnClickFillInIntent(R.id.item_title_done, openIntent);

                return rv;
            } catch (Throwable t) {
                return null;
            }
        }

        @Override
        public RemoteViews getLoadingView() {
            return null;
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public boolean hasStableIds() {
            return true;
        }
    }
}
