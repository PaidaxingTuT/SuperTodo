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
 * 提供 RemoteViewsFactory 绑定待办事项列表数据（适配所有标准 Android / ColorOS / OriginOS / HyperOS 启动器）
 */
public class TodoWidgetService extends RemoteViewsService {

    @Override
    public RemoteViewsFactory onGetViewFactory(Intent intent) {
        return new TodoRemoteViewsFactory(this.getApplicationContext(), intent);
    }

    static class TodoRemoteViewsFactory implements RemoteViewsFactory {
        private final Context context;
        private final int appWidgetId;
        private final List<TodoItem> items = new ArrayList<>();

        public TodoRemoteViewsFactory(Context context, Intent intent) {
            this.context = context;
            this.appWidgetId = intent != null ? intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID) : AppWidgetManager.INVALID_APPWIDGET_ID;
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

        private int safeGetColor(int resId, int fallback) {
            try {
                return context.getColor(resId);
            } catch (Throwable t) {
                return fallback;
            }
        }

        @Override
        public RemoteViews getViewAt(int position) {
            if (position < 0 || position >= items.size()) return null;
            try {
                TodoItem item = items.get(position);
                RemoteViews rv = new RemoteViews(context.getPackageName(), R.layout.widget_item);

                String title = item.title != null ? item.title : "";

                // 标题与完成状态（带划线与灰显）
                if (item.done) {
                    SpannableString ss = new SpannableString(title);
                    ss.setSpan(new StrikethroughSpan(), 0, ss.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    rv.setTextViewText(R.id.item_title, ss);
                    rv.setTextColor(R.id.item_title, safeGetColor(R.color.widget_text_done, 0xFF8F959E));
                    rv.setImageViewResource(R.id.item_checkbox, R.drawable.widget_ic_check_box_checked);
                } else {
                    rv.setTextViewText(R.id.item_title, title);
                    rv.setTextColor(R.id.item_title, safeGetColor(R.color.widget_text_primary, 0xFF1F2329));
                    rv.setImageViewResource(R.id.item_checkbox, R.drawable.widget_ic_check_box_unchecked);
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
        public int getViewTypeCount() {
            return 1;
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public boolean hasStableIds() {
            return false;
        }
    }
}
