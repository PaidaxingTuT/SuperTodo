package com.dax.supertodo.widget;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import com.dax.supertodo.R;

import java.util.ArrayList;
import java.util.List;

public class WidgetConfigActivity extends Activity {

    private int appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;

    private RadioGroup rgGroupBy;
    private RadioButton rbGroupAll, rbGroupScene, rbGroupTime;
    private Spinner spCategory;
    private Spinner spSortKey;
    private RadioGroup rgSortOrder;
    private RadioButton rbSortAsc, rbSortDesc;
    private CheckBox cbHideDone;
    private Button btnCancel, btnSave;

    private List<String> currentCategoryList = new ArrayList<>();
    private ArrayAdapter<String> categoryAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setResult(RESULT_CANCELED);

        Intent intent = getIntent();
        Bundle extras = intent.getExtras();
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
        rgGroupBy = findViewById(R.id.rg_group_by);
        rbGroupAll = findViewById(R.id.rb_group_all);
        rbGroupScene = findViewById(R.id.rb_group_scene);
        rbGroupTime = findViewById(R.id.rb_group_time);

        spCategory = findViewById(R.id.sp_category);
        spSortKey = findViewById(R.id.sp_sort_key);

        rgSortOrder = findViewById(R.id.rg_sort_order);
        rbSortAsc = findViewById(R.id.rb_sort_asc);
        rbSortDesc = findViewById(R.id.rb_sort_desc);

        cbHideDone = findViewById(R.id.cb_hide_done);
        btnCancel = findViewById(R.id.btn_config_cancel);
        btnSave = findViewById(R.id.btn_config_save);

        // 初始化排序选项
        String[] sortNames = new String[] {
            "默认顺序（添加先后）",
            "重要程度（★ 星级）",
            "截止日期（临近日）",
            "花费金额（数值）",
            "未完成优先（未完成置顶）"
        };
        ArrayAdapter<String> sortAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, sortNames);
        spSortKey.setAdapter(sortAdapter);

        // 分类适配器
        categoryAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, currentCategoryList);
        spCategory.setAdapter(categoryAdapter);

        rgGroupBy.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                updateCategoryOptions(null);
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

    private void updateCategoryOptions(String selectTarget) {
        currentCategoryList.clear();
        int checkedId = rgGroupBy.getCheckedRadioButtonId();

        if (checkedId == R.id.rb_group_scene) {
            currentCategoryList.add("全部场景");
            List<String> scenes = WidgetDataManager.loadTags(this, "scenes");
            for (String s : scenes) {
                if (!currentCategoryList.contains(s)) currentCategoryList.add(s);
            }
            currentCategoryList.add("未分组");
            spCategory.setEnabled(true);
        } else if (checkedId == R.id.rb_group_time) {
            currentCategoryList.add("全部时间");
            List<String> times = WidgetDataManager.loadTags(this, "times");
            for (String t : times) {
                if (!currentCategoryList.contains(t)) currentCategoryList.add(t);
            }
            currentCategoryList.add("未分组");
            spCategory.setEnabled(true);
        } else {
            currentCategoryList.add("全部事项");
            spCategory.setEnabled(false);
        }

        categoryAdapter.notifyDataSetChanged();

        if (selectTarget != null) {
            int pos = currentCategoryList.indexOf(selectTarget);
            if (pos >= 0) {
                spCategory.setSelection(pos);
            } else if (selectTarget.equals("全部")) {
                spCategory.setSelection(0);
            }
        }
    }

    private void loadSavedConfig() {
        String groupBy = WidgetDataManager.getWidgetGroupBy(this, appWidgetId);
        String category = WidgetDataManager.getWidgetCategory(this, appWidgetId);
        String sortKey = WidgetDataManager.getWidgetSortKey(this, appWidgetId);
        boolean sortAsc = WidgetDataManager.getWidgetSortAsc(this, appWidgetId);
        boolean hideDone = WidgetDataManager.getWidgetHideDone(this, appWidgetId);

        if (WidgetDataManager.GROUP_TIME.equals(groupBy)) {
            rbGroupTime.setChecked(true);
        } else if (WidgetDataManager.GROUP_ALL.equals(groupBy)) {
            rbGroupAll.setChecked(true);
        } else {
            rbGroupScene.setChecked(true);
        }

        updateCategoryOptions(category);

        int sortPos = 0;
        if (WidgetDataManager.SORT_STAR.equals(sortKey)) sortPos = 1;
        else if (WidgetDataManager.SORT_DUE.equals(sortKey)) sortPos = 2;
        else if (WidgetDataManager.SORT_COST.equals(sortKey)) sortPos = 3;
        else if (WidgetDataManager.SORT_UNDONE_FIRST.equals(sortKey)) sortPos = 4;
        spSortKey.setSelection(sortPos);

        if (sortAsc) {
            rbSortAsc.setChecked(true);
        } else {
            rbSortDesc.setChecked(true);
        }

        cbHideDone.setChecked(hideDone);
    }

    private void saveAndApply() {
        String groupBy = WidgetDataManager.GROUP_SCENE;
        int checkedGroup = rgGroupBy.getCheckedRadioButtonId();
        if (checkedGroup == R.id.rb_group_time) {
            groupBy = WidgetDataManager.GROUP_TIME;
        } else if (checkedGroup == R.id.rb_group_all) {
            groupBy = WidgetDataManager.GROUP_ALL;
        }

        String category = "全部";
        if (spCategory.getSelectedItem() != null) {
            category = spCategory.getSelectedItem().toString();
            if ("全部场景".equals(category) || "全部时间".equals(category) || "全部事项".equals(category)) {
                category = "全部";
            }
        }

        String sortKey = WidgetDataManager.SORT_DEFAULT;
        int sortPos = spSortKey.getSelectedItemPosition();
        if (sortPos == 1) sortKey = WidgetDataManager.SORT_STAR;
        else if (sortPos == 2) sortKey = WidgetDataManager.SORT_DUE;
        else if (sortPos == 3) sortKey = WidgetDataManager.SORT_COST;
        else if (sortPos == 4) sortKey = WidgetDataManager.SORT_UNDONE_FIRST;

        boolean sortAsc = rbSortAsc.isChecked();
        boolean hideDone = cbHideDone.isChecked();

        WidgetDataManager.saveWidgetConfig(
            this,
            appWidgetId,
            groupBy,
            category,
            sortKey,
            sortAsc,
            hideDone
        );

        WidgetDataManager.notifyAllWidgets(this);

        Intent resultValue = new Intent();
        resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        setResult(RESULT_OK, resultValue);
        finish();
    }
}
