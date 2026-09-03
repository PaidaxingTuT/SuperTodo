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

public class TodoWidgetService extends RemoteViewsService {
    @Override
    public RemoteViewsFactory onGetViewFactory(Intent intent) {
        return new TodoViewsFactory(this.getApplicationContext(), intent);
    }

    private static class TodoViewsFactory implements RemoteViewsFactory {
        private final Context context;
        private final int appWidgetId;
        private List<TodoItem> items = new ArrayList<>();

        public TodoViewsFactory(Context context, Intent intent) {
            this.context = context;
            this.appWidgetId = intent != null ? intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID) : AppWidgetManager.INVALID_APPWIDGET_ID;
        }

        @Override
        public void onCreate() {
            loadData();
        }

        @Override
        public void onDataSetChanged() {
            loadData();
        }

        private void loadData() {
            items = WidgetDataManager.loadTasksForWidget(context, appWidgetId);
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
        public RemoteViews getViewAt(int position) {
            if (position < 0 || position >= items.size()) return null;
            TodoItem item = items.get(position);
            RemoteViews rv = new RemoteViews(context.getPackageName(), R.layout.widget_item);

            // 标题与完成状态（带划线与灰显）
            if (item.done) {
                SpannableString ss = new SpannableString(item.title);
                ss.setSpan(new StrikethroughSpan(), 0, ss.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                rv.setTextViewText(R.id.item_title, ss);
                rv.setTextColor(R.id.item_title, context.getColor(R.color.widget_text_done));
                rv.setImageViewResource(R.id.item_checkbox, R.drawable.widget_ic_check_box_checked);
            } else {
                rv.setTextViewText(R.id.item_title, item.title);
                rv.setTextColor(R.id.item_title, context.getColor(R.color.widget_text_primary));
                rv.setImageViewResource(R.id.item_checkbox, R.drawable.widget_ic_check_box_unchecked);
            }

            // 标签（优先显示当前场景或时间）
            String tag = "";
            if (!item.scene.isEmpty()) {
                tag = item.scene;
            } else if (!item.time.isEmpty()) {
                tag = item.time;
            } else if (!item.type.isEmpty() && !"全部".equals(item.type)) {
                tag = item.type;
            }

            if (!tag.isEmpty()) {
                rv.setViewVisibility(R.id.item_tag, View.VISIBLE);
                rv.setTextViewText(R.id.item_tag, tag);
            } else {
                rv.setViewVisibility(R.id.item_tag, View.GONE);
            }

            // 截止日期
            if (!item.due.isEmpty()) {
                rv.setViewVisibility(R.id.item_due, View.VISIBLE);
                rv.setTextViewText(R.id.item_due, item.due);
            } else {
                rv.setViewVisibility(R.id.item_due, View.GONE);
            }

            // 星级 / 重要程度
            if (item.star > 0) {
                rv.setViewVisibility(R.id.item_star, View.VISIBLE);
                rv.setTextViewText(R.id.item_star, "★ " + item.star);
            } else {
                rv.setViewVisibility(R.id.item_star, View.GONE);
            }

            // 勾选按钮事件 FillInIntent（直接在小组件上打勾已完成，不打开 App）
            Intent toggleIntent = new Intent();
            toggleIntent.putExtra("extra_action", "toggle_done");
            toggleIntent.putExtra("extra_item_id", item.id);
            rv.setOnClickFillInIntent(R.id.item_checkbox, toggleIntent);

            // 事项文字区域点击事件 FillInIntent（点击打开 App 查看该事项）
            Intent openIntent = new Intent();
            openIntent.putExtra("extra_action", "open_item");
            openIntent.putExtra("extra_item_id", item.id);
            rv.setOnClickFillInIntent(R.id.item_body, openIntent);

            return rv;
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
