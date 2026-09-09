#!/usr/bin/env node
const assert = require('assert');
const fs = require('fs');

const dir = 'android-src/main/java/com/dax/supertodo/widget/';
const read = name => fs.readFileSync(dir + name, 'utf8');
const manager = read('WidgetDataManager.java');
const widget2x2 = read('TodoWidget2x2Provider.java');
const widget4x2 = read('TodoWidget4x2Provider.java');
const widget4x4 = read('TodoWidget4x4Provider.java');
const quadrant = read('TodoWidgetQuadrantProvider.java');
const service = read('TodoWidgetService.java');
const layoutDir = 'android-src/main/res/layout/';
const widget2x2Layout = fs.readFileSync(layoutDir + 'widget_2x2.xml', 'utf8');
const quadrantLayout = fs.readFileSync(layoutDir + 'widget_quadrant.xml', 'utf8');

assert(manager.includes('optString("theme", "")'), '必须从同步 JSON 读取主题色');
assert(manager.includes('theme.matches("^#[0-9a-fA-F]{6}$")'), '主题色必须校验为六位十六进制');
assert(manager.includes('getThemedCheckedIcon'), '必须保留白色对勾并只替换圆形主色');
assert(widget2x2.includes('setTextColor(R.id.widget_2x2_count, themeColor)'), '2x2 数量徽标必须跟随主题色');
assert(widget2x2.includes('setTextColor(R.id.btn_2x2_complete, themeColor)'), '2x2 完成按钮必须跟随主题色');
assert(/id="@\+id\/btn_2x2_complete"[\s\S]*?background="@drawable\/widget_tag_bg"/.test(widget2x2Layout), '2x2 完成按钮必须与展开清单使用相同背景');
assert(!quadrantLayout.includes('android:background="#FFFFFF"'), '四象限方向标签背景不能硬编码为日间白色');

for (const source of [widget4x2, widget4x4, service]) {
    assert(source.includes('setTextColor') && source.includes('themeColor'), '列表标签必须跟随主题色');
    assert(source.includes('setImageViewBitmap') && source.includes('getThemedCheckedIcon'), '已完成图标必须跟随主题色');
}
assert(quadrant.includes('getThemedCheckedIcon(themeColor)'), '四象限完成图标必须跟随主题色');

const configActivity = read('WidgetConfigActivity.java');
const configLayout = fs.readFileSync(layoutDir + 'activity_widget_config.xml', 'utf8');
assert(configLayout.includes('android:text="小组件设置"'), '弹窗标题必须为《小组件设置》');
assert(configActivity.includes('WidgetDataManager.getWidgetThemeColor(this)'), '小组件设置弹窗必须动态获取当前主题色');
assert(configActivity.includes('btnSave.setBackground(ripple)'), '保存按钮必须动态应用主题色背景');
assert(configActivity.includes('cbHideDone.setButtonTintList'), '复选框勾选色必须动态应用主题色');
assert(configActivity.includes('pillBg.setColor(themeColor)'), '选中胶囊必须动态应用主题色');

console.log('widget theme checks: OK');

