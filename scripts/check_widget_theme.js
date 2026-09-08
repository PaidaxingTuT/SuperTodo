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

assert(manager.includes('optString("theme", "")'), '必须从同步 JSON 读取主题色');
assert(manager.includes('theme.matches("^#[0-9a-fA-F]{6}$")'), '主题色必须校验为六位十六进制');
assert(manager.includes('getThemedCheckedIcon'), '必须保留白色对勾并只替换圆形主色');
assert(widget2x2.includes('setTextColor(R.id.widget_2x2_count, themeColor)'), '2x2 数量徽标必须跟随主题色');

for (const source of [widget4x2, widget4x4, service]) {
    assert(source.includes('setTextColor') && source.includes('themeColor'), '列表标签必须跟随主题色');
    assert(source.includes('setImageViewBitmap') && source.includes('getThemedCheckedIcon'), '已完成图标必须跟随主题色');
}
assert(quadrant.includes('getThemedCheckedIcon(themeColor)'), '四象限完成图标必须跟随主题色');

console.log('widget theme checks: OK');
