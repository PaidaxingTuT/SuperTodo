package com.dax.supertodo.widget;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.graphics.drawable.StateListDrawable;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import com.dax.supertodo.R;

import java.util.ArrayList;
import java.util.List;

/**
 * 小组件个性化配置页面（全面遵循 SuperTodo 现代卡片设计，与应用内弹窗风格统一）
 */
public class WidgetConfigActivity extends Activity {

    private int appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;
    private int themeColor;
    private boolean isMd3;
    private int md3Surface, md3Field, md3Text, md3Supporting, md3Outline;
    private int md3Primary, md3Secondary, md3OnSecondary;
    private Switch hideDoneSwitch;

    // 分段胶囊选择器：展示维度
    private TextView btnPillScene, btnPillTime, btnPillAll;
    private String selectedGroupBy = "scene"; // "scene", "time", "all"

    // 具体分类
    private TextView tvCategoryLabel;
    private RelativeLayout layoutCategoryBox;
    private Spinner spCategory;
    private final List<String> currentCategoryList = new ArrayList<>();
    private ArrayAdapter<String> categoryAdapter;

    // 标签分类（与上方场景/时间/全部条件取交集）
    private Spinner spTypeCategory;
    private final List<String> typeCategoryList = new ArrayList<>();
    private ArrayAdapter<String> typeCategoryAdapter;

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

        themeColor = WidgetDataManager.getWidgetThemeColor(this);

        isMd3 = WidgetDataManager.isMd3Style(this);
        if (isMd3) initMd3Colors();
        setContentView(isMd3 ? R.layout.activity_widget_config_md3 : R.layout.activity_widget_config);

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
        spTypeCategory = findViewById(R.id.sp_type_category);

        spSortKey = findViewById(R.id.sp_sort_key);
        btnPillAsc = findViewById(R.id.btn_pill_asc);
        btnPillDesc = findViewById(R.id.btn_pill_desc);

        cbHideDone = findViewById(R.id.cb_hide_done);
        View layoutFilterRow = findViewById(R.id.layout_filter_row);

        btnCancel = findViewById(R.id.btn_config_cancel);
        btnSave = findViewById(R.id.btn_config_save);

        if (isMd3) {
            applyMd3Text(configCard);
            configCard.setBackground(md3Shape(md3Surface, 28));
            btnClose.setColorFilter(md3Supporting);
            btnClose.setBackground(md3Ripple(Color.TRANSPARENT, 24));
            btnCancel.setBackground(md3Ripple(Color.TRANSPARENT, 24));
            btnCancel.setTextColor(md3Text);
            btnCancel.setBackgroundTintList(null);
            btnSave.setBackgroundTintList(null);
            rootBackdrop.post(() -> {
                ViewGroup.LayoutParams params = configCard.getLayoutParams();
                params.width = Math.min(dp(560), rootBackdrop.getWidth() - rootBackdrop.getPaddingLeft() - rootBackdrop.getPaddingRight());
                configCard.setLayoutParams(params);
            });
            btnSave.setTextColor(contrastText(md3Primary));
            for (Spinner spinner : new Spinner[] {spCategory, spTypeCategory, spSortKey}) {
                View field = (View) spinner.getParent();
                field.setBackground(md3Shape(md3Field, 4));
                // 填充式输入框底线，保持与应用内 MD3 输入样式一致。
                View line = new View(this);
                line.setBackgroundColor(md3Outline);
                RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(-1, dp(1));
                params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
                ((RelativeLayout) field).addView(line, params);
                spinner.setPopupBackgroundDrawable(md3Shape(md3Field, 4));
                spinner.setContentDescription(spinner == spCategory ? "具体分类" :
                    spinner == spTypeCategory ? "标签分类" : "排序方式");
            }
            for (int id : new int[] {R.id.config_group_segments, R.id.config_sort_segments}) {
                View group = findViewById(id);
                GradientDrawable outline = md3Shape(Color.TRANSPARENT, 24);
                outline.setStroke(dp(1), md3Outline);
                group.setBackground(outline);
                group.setClipToOutline(true);
            }
            layoutFilterRow.setBackground(md3Ripple(md3Field, 16));
            hideDoneSwitch = findViewById(R.id.config_hide_done_switch);
            StateListDrawable track = new StateListDrawable();
            GradientDrawable checkedTrack = md3Shape(md3Primary, 16);
            checkedTrack.setSize(dp(52), dp(32));
            GradientDrawable uncheckedTrack = md3Shape(md3Field, 16);
            uncheckedTrack.setSize(dp(52), dp(32));
            uncheckedTrack.setStroke(dp(2), md3Outline);
            track.addState(new int[] {android.R.attr.state_checked}, checkedTrack);
            track.addState(new int[] {}, uncheckedTrack);
            hideDoneSwitch.setTrackTintList(null);
            hideDoneSwitch.setThumbTintList(null);
            hideDoneSwitch.setTrackDrawable(track);
            StateListDrawable thumb = new StateListDrawable();
            GradientDrawable checkedThumb = md3Shape(contrastText(md3Primary), 12);
            checkedThumb.setSize(dp(24), dp(24));
            GradientDrawable uncheckedThumb = md3Shape(md3Outline, 12);
            uncheckedThumb.setSize(dp(24), dp(24));
            thumb.addState(new int[] {android.R.attr.state_checked}, checkedThumb);
            thumb.addState(new int[] {}, uncheckedThumb);
            hideDoneSwitch.setThumbDrawable(thumb);
            hideDoneSwitch.setBackground(md3Ripple(Color.TRANSPARENT, 24));
            hideDoneSwitch.setOnCheckedChangeListener((button, checked) -> cbHideDone.setChecked(checked));
        } else {
            configCard.setBackgroundResource(R.drawable.config_card_bg);
        }

        // 主题颜色同步：保存按钮波纹背景与复选框强调色
        GradientDrawable saveBg = new GradientDrawable();
        saveBg.setShape(GradientDrawable.RECTANGLE);
        saveBg.setColor(isMd3 ? md3Primary : themeColor);
        saveBg.setCornerRadius(getResources().getDisplayMetrics().density * (isMd3 ? 22f : 12f));
        RippleDrawable ripple = new RippleDrawable(
            ColorStateList.valueOf(Color.parseColor("#33FFFFFF")),
            saveBg,
            null
        );
        btnSave.setBackground(ripple);
        cbHideDone.setButtonTintList(ColorStateList.valueOf(themeColor));

        // 1. 下拉框适配器（使用 SuperTodo 专属字体与内边距布局）
        ArrayAdapter<String> sortAdapter = configAdapter(java.util.Arrays.asList(SORT_LABELS), spSortKey);
        sortAdapter.setDropDownViewResource(R.layout.config_spinner_dropdown_item);
        spSortKey.setAdapter(sortAdapter);

        categoryAdapter = configAdapter(currentCategoryList, spCategory);
        categoryAdapter.setDropDownViewResource(R.layout.config_spinner_dropdown_item);
        spCategory.setAdapter(categoryAdapter);

        typeCategoryAdapter = configAdapter(typeCategoryList, spTypeCategory);
        typeCategoryAdapter.setDropDownViewResource(R.layout.config_spinner_dropdown_item);
        spTypeCategory.setAdapter(typeCategoryAdapter);

        // 2. 胶囊维度切换事件
        btnPillScene.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setGroupBy("scene", null, getSelectedTypeCategory());
            }
        });

        btnPillTime.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setGroupBy("time", null, getSelectedTypeCategory());
            }
        });

        btnPillAll.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setGroupBy("all", null, getSelectedTypeCategory());
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
                if (isMd3) hideDoneSwitch.toggle();
                else cbHideDone.setChecked(!cbHideDone.isChecked());
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

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    // 与 web/styles/md3.css 使用相同的 HSL 色彩角色。
    private int tonal(float saturation, float lightness) {
        float[] hsv = new float[3];
        Color.colorToHSV(themeColor, hsv);
        float value = lightness + saturation * Math.min(lightness, 1 - lightness);
        hsv[1] = value == 0 ? 0 : 2 * (1 - lightness / value);
        hsv[2] = value;
        return Color.HSVToColor(hsv);
    }

    private void initMd3Colors() {
        boolean dark = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
            == Configuration.UI_MODE_NIGHT_YES;
        md3Surface = tonal(dark ? .10f : .18f, dark ? .17f : .92f);
        md3Field = tonal(dark ? .10f : .16f, dark ? .21f : .90f);
        md3Text = tonal(dark ? .18f : .12f, dark ? .92f : .12f);
        md3Supporting = tonal(dark ? .12f : .10f, dark ? .79f : .34f);
        md3Outline = tonal(dark ? .08f : .09f, dark ? .60f : .48f);
        md3Secondary = tonal(dark ? .18f : .24f, dark ? .27f : .88f);
        md3OnSecondary = tonal(dark ? .28f : .25f, dark ? .90f : .26f);
        float max = Math.max(Color.red(themeColor), Math.max(Color.green(themeColor), Color.blue(themeColor))) / 255f;
        float min = Math.min(Color.red(themeColor), Math.min(Color.green(themeColor), Color.blue(themeColor))) / 255f;
        float saturation = max == min ? 0 : (max - min) / (1 - Math.abs(max + min - 1));
        md3Primary = tonal(Math.min(.68f, saturation), dark ? .80f : .28f);
    }

    private int contrastText(int color) {
        double luminance = 0;
        double[] weights = {.2126, .7152, .0722};
        int[] channels = {Color.red(color), Color.green(color), Color.blue(color)};
        for (int i = 0; i < channels.length; i++) {
            double value = channels[i] / 255.0;
            luminance += weights[i] * (value <= .04045 ? value / 12.92 : Math.pow((value + .055) / 1.055, 2.4));
        }
        return luminance > .179 ? Color.BLACK : Color.WHITE;
    }

    private GradientDrawable md3Shape(int color, float radius) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(dp(radius));
        return shape;
    }

    private RippleDrawable md3Ripple(int color, float radius) {
        return new RippleDrawable(ColorStateList.valueOf((md3Text & 0x00FFFFFF) | 0x1F000000),
            md3Shape(color, radius), md3Shape(Color.WHITE, radius));
    }

    private void applyMd3Text(View view) {
        if (view instanceof TextView) {
            TextView text = (TextView) view;
            text.setTextColor("section".equals(view.getTag()) || "supporting".equals(view.getTag()) ?
                md3Supporting : md3Text);
            text.setTypeface(Typeface.create("section".equals(view.getTag()) ?
                "sans-serif-medium" : "sans-serif", Typeface.NORMAL));
        }
        if (view instanceof ImageView) ((ImageView) view).setColorFilter(md3Supporting);
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) applyMd3Text(group.getChildAt(i));
        }
    }

    private ArrayAdapter<String> configAdapter(List<String> items, Spinner spinner) {
        return new ArrayAdapter<String>(this, R.layout.config_spinner_item, items) {
            private View style(View view, int position, boolean dropdown) {
                if (!isMd3) return view;
                TextView text = (TextView) view;
                boolean selected = dropdown && position == spinner.getSelectedItemPosition();
                text.setText((selected ? "✓  " : "") + getItem(position));
                text.setTextColor(selected ? md3OnSecondary : md3Text);
                text.setTextSize(16);
                text.setMinHeight(dp(56));
                text.setGravity(android.view.Gravity.CENTER_VERTICAL);
                text.setPadding(dp(16), dp(12), dp(16), dp(12));
                text.setBackground(dropdown ? md3Ripple(selected ? md3Secondary : md3Field, 0) : null);
                text.setSelected(selected);
                return text;
            }

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                return style(super.getView(position, convertView, parent), position, false);
            }

            @Override
            public View getDropDownView(int position, View convertView, ViewGroup parent) {
                return style(super.getDropDownView(position, convertView, parent), position, true);
            }
        };
    }

    private void updatePill(TextView view, boolean selected) {
        if (isMd3) {
            ViewGroup group = (ViewGroup) view.getParent();
            int index = group.indexOfChild(view);
            boolean rtl = group.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
            float start = index == 0 ? dp(24) : 0;
            float end = index == group.getChildCount() - 1 ? dp(24) : 0;
            float left = rtl ? end : start;
            float right = rtl ? start : end;
            GradientDrawable background = md3Shape(selected ? md3Secondary : Color.TRANSPARENT, 0);
            background.setCornerRadii(new float[] {left, left, right, right, right, right, left, left});
            background.setStroke(dp(1), md3Outline);
            view.setBackground(new RippleDrawable(ColorStateList.valueOf((md3Text & 0x00FFFFFF) | 0x1F000000),
                background, null));
            view.setTextColor(selected ? md3OnSecondary : md3Supporting);
            view.setSelected(selected);
            view.setFocusable(true);
            String label = view.getText().toString().replace("✓  ", "");
            view.setText((selected ? "✓  " : "") + label);
            view.setAccessibilityDelegate(new View.AccessibilityDelegate() {
                @Override
                public void onInitializeAccessibilityNodeInfo(View host, android.view.accessibility.AccessibilityNodeInfo info) {
                    super.onInitializeAccessibilityNodeInfo(host, info);
                    info.setCheckable(true);
                    info.setChecked(host.isSelected());
                }
            });
            return;
        }
        int unselectedColor = getColor(R.color.config_pill_text_unselected);
        if (selected) {
            GradientDrawable pillBg = new GradientDrawable();
            pillBg.setShape(GradientDrawable.RECTANGLE);
            pillBg.setColor(themeColor);
            float radius = getResources().getDisplayMetrics().density * 10f;
            pillBg.setCornerRadius(radius);
            view.setBackground(pillBg);
            view.setTextColor(Color.WHITE);
        } else {
            view.setBackgroundResource(R.drawable.config_pill_unselected);
            view.setTextColor(unselectedColor);
        }
    }

    private void setGroupBy(String groupBy, String targetCategory, String targetTypeCategory) {
        selectedGroupBy = groupBy;

        // 样式刷新（动态同步主题色）
        updatePill(btnPillScene, "scene".equals(groupBy));
        updatePill(btnPillTime, "time".equals(groupBy));
        updatePill(btnPillAll, "all".equals(groupBy));

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

        typeCategoryList.clear();
        typeCategoryList.add("全部标签");
        typeCategoryList.addAll(WidgetDataManager.loadAvailableTypes(this));
        typeCategoryAdapter.notifyDataSetChanged();
        int typeIdx = typeCategoryList.indexOf(targetTypeCategory);
        spTypeCategory.setSelection(typeIdx >= 0 ? typeIdx : 0);
    }

    private String getSelectedTypeCategory() {
        return spTypeCategory != null && spTypeCategory.getSelectedItem() != null
            ? spTypeCategory.getSelectedItem().toString() : "全部标签";
    }

    private void setSortAsc(boolean asc) {
        selectedSortAsc = asc;
        updatePill(btnPillAsc, asc);
        updatePill(btnPillDesc, !asc);
    }

    private void loadSavedConfig() {
        String savedGroup = WidgetDataManager.getWidgetGroupBy(this, appWidgetId);
        String savedCategory = WidgetDataManager.getWidgetCategory(this, appWidgetId);
        String savedTypeCategory = WidgetDataManager.getWidgetTypeCategory(this, appWidgetId);
        String savedSortKey = WidgetDataManager.getWidgetSortKey(this, appWidgetId);
        boolean savedSortAsc = WidgetDataManager.getWidgetSortAsc(this, appWidgetId);
        boolean savedHideDone = WidgetDataManager.getWidgetHideDone(this, appWidgetId);

        setGroupBy(savedGroup, savedCategory, savedTypeCategory);
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
        if (isMd3) hideDoneSwitch.setChecked(savedHideDone);
    }

    private void saveAndApply() {
        String category = "";
        if (!"all".equals(selectedGroupBy) && spCategory.getSelectedItem() != null) {
            category = spCategory.getSelectedItem().toString();
        }
        String typeCategory = spTypeCategory.getSelectedItem() != null
            ? spTypeCategory.getSelectedItem().toString() : "全部标签";

        int sortPos = spSortKey.getSelectedItemPosition();
        String sortKey = (sortPos >= 0 && sortPos < SORT_KEYS.length) ? SORT_KEYS[sortPos] : "默认";

        boolean hideDone = cbHideDone.isChecked();

        // 持久化当前小部件配置
        WidgetDataManager.saveWidgetConfig(
            this,
            appWidgetId,
            selectedGroupBy,
            category,
            typeCategory,
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
