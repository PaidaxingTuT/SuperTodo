package com.dax.supertodo.widget;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.Spinner;
import android.widget.TextView;
import com.dax.supertodo.R;

import java.util.ArrayList;
import java.util.List;

/**
 * 小部件个性化配置页面（全面遵循 SuperTodo 现代卡片设计，与应用内弹窗风格统一）
 */
public class WidgetConfigActivity extends Activity {

    private int appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;

    // 分段胶囊选择器：展示维度
    private TextView btnPillScene, btnPillTime, btnPillAll;
    private String selectedGroupBy = "scene"; // "scene", "time", "all"

    // 具体分类
    private TextView tvCategoryLabel;
    private RelativeLayout layoutCategoryBox;
    private Spinner spCategory;
    private final List<String> currentCategoryList = new ArrayList<>();
    private ArrayAdapter<String> categoryAdapter;

    // 排序设置
    private Spinner spSortKey;
    private TextView btnPillAsc, btnPillDesc;
    private boolean selectedSortAsc = true;

    // 过滤与控制
    private CheckBox cbHideDone;
    private Button btnCancel, btnSave;
    private ImageView btnClose;
    private View rootBackdrop, configCard;

    private static final String[] SORT_LABELS = new String[] {
        "默认顺序（添加先后）",
        "重要程度（★ 星级）",
        "截止日期（临近日优先）",
        "花费金额（由高到低）",
        "未完成优先（未完成置顶）"
    };

    private static final String[] SORT_KEYS = new String[] {
        "默认",
        "重要程度",
        "截止时间",
        "金额",
        "未完成优先"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setResult(RESULT_CANCELED);

        Intent intent = getIntent();
        Bundle extras = intent != null ? intent.getExtras() : null;
        if (extras != null) {
            appWidgetId = extras.getInt(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID
            );
        }

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish();
            return;
        }

        setContentView(R.layout.activity_widget_config);

        initViews();
        loadSavedConfig();
    }

    private void initViews() {
        rootBackdrop = findViewById(R.id.config_root_backdrop);
        configCard = findViewById(R.id.config_card);
        btnClose = findViewById(R.id.btn_config_close);

        btnPillScene = findViewById(R.id.btn_pill_scene);
        btnPillTime = findViewById(R.id.btn_pill_time);
        btnPillAll = findViewById(R.id.btn_pill_all);

        tvCategoryLabel = findViewById(R.id.tv_category_label);
        layoutCategoryBox = findViewById(R.id.layout_category_box);
        spCategory = findViewById(R.id.sp_category);

        spSortKey = findViewById(R.id.sp_sort_key);
        btnPillAsc = findViewById(R.id.btn_pill_asc);
        btnPillDesc = findViewById(R.id.btn_pill_desc);

        cbHideDone = findViewById(R.id.cb_hide_done);
        View layoutFilterRow = findViewById(R.id.layout_filter_row);

        btnCancel = findViewById(R.id.btn_config_cancel);
        btnSave = findViewById(R.id.btn_config_save);

        // 1. 下拉框适配器（使用 SuperTodo 专属字体与内边距布局）
        ArrayAdapter<String> sortAdapter = new ArrayAdapter<>(this, R.layout.config_spinner_item, SORT_LABELS);
        sortAdapter.setDropDownViewResource(R.layout.config_spinner_dropdown_item);
        spSortKey.setAdapter(sortAdapter);

        categoryAdapter = new ArrayAdapter<>(this, R.layout.config_spinner_item, currentCategoryList);
        categoryAdapter.setDropDownViewResource(R.layout.config_spinner_dropdown_item);
        spCategory.setAdapter(categoryAdapter);

        // 2. 胶囊维度切换事件
        btnPillScene.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setGroupBy("scene", null);
            }
        });

        btnPillTime.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setGroupBy("time", null);
            }
        });

        btnPillAll.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setGroupBy("all", null);
            }
        });

        // 3. 排序方向胶囊切换
        btnPillAsc.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setSortAsc(true);
            }
        });

        btnPillDesc.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setSortAsc(false);
            }
        });

        // 4. 过滤设置整行点击
        layoutFilterRow.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                cbHideDone.setChecked(!cbHideDone.isChecked());
            }
        });

        // 5. 点击背景或右上角关闭直接退出
        btnClose.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        rootBackdrop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        configCard.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 阻断卡片内部点击穿透到背景
            }
        });

        btnCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        btnSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveAndApply();
            }
        });
    }

    private void setGroupBy(String groupBy, String targetCategory) {
        selectedGroupBy = groupBy;

        // 样式刷新
        int unselectedColor = getColor(R.color.config_pill_text_unselected);
        btnPillScene.setBackgroundResource("scene".equals(groupBy) ? R.drawable.config_pill_selected : R.drawable.config_pill_unselected);
        btnPillScene.setTextColor("scene".equals(groupBy) ? Color.WHITE : unselectedColor);

        btnPillTime.setBackgroundResource("time".equals(groupBy) ? R.drawable.config_pill_selected : R.drawable.config_pill_unselected);
        btnPillTime.setTextColor("time".equals(groupBy) ? Color.WHITE : unselectedColor);

        btnPillAll.setBackgroundResource("all".equals(groupBy) ? R.drawable.config_pill_selected : R.drawable.config_pill_unselected);
        btnPillAll.setTextColor("all".equals(groupBy) ? Color.WHITE : unselectedColor);

        // 分类下拉框动态显隐与填充
        currentCategoryList.clear();
        if ("scene".equals(groupBy)) {
            tvCategoryLabel.setVisibility(View.VISIBLE);
            layoutCategoryBox.setVisibility(View.VISIBLE);
            tvCategoryLabel.setText("选择场景分类");
            List<String> scenes = WidgetDataManager.loadTags(this, "scenes");
            if (scenes.isEmpty()) {
                currentCategoryList.add("全部场景");
                currentCategoryList.add("未分组");
            } else {
                currentCategoryList.addAll(scenes);
                if (!currentCategoryList.contains("未分组")) currentCategoryList.add("未分组");
            }
        } else if ("time".equals(groupBy)) {
            tvCategoryLabel.setVisibility(View.VISIBLE);
            layoutCategoryBox.setVisibility(View.VISIBLE);
            tvCategoryLabel.setText("选择时间分类");
            List<String> times = WidgetDataManager.loadTags(this, "times");
            if (times.isEmpty()) {
                currentCategoryList.add("全部时间");
                currentCategoryList.add("未分组");
            } else {
                currentCategoryList.addAll(times);
                if (!currentCategoryList.contains("未分组")) currentCategoryList.add("未分组");
            }
        } else {
            tvCategoryLabel.setVisibility(View.GONE);
            layoutCategoryBox.setVisibility(View.GONE);
        }

        categoryAdapter.notifyDataSetChanged();

        if (targetCategory != null && !targetCategory.isEmpty()) {
            int idx = currentCategoryList.indexOf(targetCategory);
            if (idx >= 0) spCategory.setSelection(idx);
        } else if (!currentCategoryList.isEmpty()) {
            spCategory.setSelection(0);
        }
    }

    private void setSortAsc(boolean asc) {
        selectedSortAsc = asc;
        int unselectedColor = getColor(R.color.config_pill_text_unselected);
        btnPillAsc.setBackgroundResource(asc ? R.drawable.config_pill_selected : R.drawable.config_pill_unselected);
        btnPillAsc.setTextColor(asc ? Color.WHITE : unselectedColor);

        btnPillDesc.setBackgroundResource(!asc ? R.drawable.config_pill_selected : R.drawable.config_pill_unselected);
        btnPillDesc.setTextColor(!asc ? Color.WHITE : unselectedColor);
    }

    private void loadSavedConfig() {
        SharedPreferences sp = getSharedPreferences("supertodo_widget_prefs", MODE_PRIVATE);
        String savedGroup = sp.getString("widget_" + appWidgetId + "_groupby", "scene");
        String savedCategory = sp.getString("widget_" + appWidgetId + "_category", "");
        String savedSortKey = sp.getString("widget_" + appWidgetId + "_sort_key", "默认");
        boolean savedSortAsc = sp.getBoolean("widget_" + appWidgetId + "_sort_asc", true);
        boolean savedHideDone = sp.getBoolean("widget_" + appWidgetId + "_hide_done", false);

        setGroupBy(savedGroup, savedCategory);
        setSortAsc(savedSortAsc);

        // 匹配排序方式选中项
        int sortIdx = 0;
        for (int i = 0; i < SORT_KEYS.length; i++) {
            if (SORT_KEYS[i].equals(savedSortKey)) {
                sortIdx = i;
                break;
            }
        }
        spSortKey.setSelection(sortIdx);

        cbHideDone.setChecked(savedHideDone);
    }

    private void saveAndApply() {
        String category = "";
        if (!"all".equals(selectedGroupBy) && spCategory.getSelectedItem() != null) {
            category = spCategory.getSelectedItem().toString();
        }

        int sortPos = spSortKey.getSelectedItemPosition();
        String sortKey = (sortPos >= 0 && sortPos < SORT_KEYS.length) ? SORT_KEYS[sortPos] : "默认";

        boolean hideDone = cbHideDone.isChecked();

        // 持久化当前小部件配置
        WidgetDataManager.saveWidgetConfig(
            this,
            appWidgetId,
            selectedGroupBy,
            category,
            sortKey,
            selectedSortAsc,
            hideDone
        );

        // 立即通知所有小部件原地刷新列表与标题
        WidgetDataManager.notifyAllWidgets(this);

        // 返回成功结果给 Launcher
        Intent resultValue = new Intent();
        resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        setResult(RESULT_OK, resultValue);
        finish();
    }
}
