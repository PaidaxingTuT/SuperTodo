#!/usr/bin/env node
const assert = require('assert');
const fs = require('fs');

const activity = fs.readFileSync('android-src/main/java/com/dax/supertodo/MainActivity.java', 'utf8');
const bridge = fs.readFileSync('android-src/main/java/com/dax/supertodo/widget/WidgetBridge.java', 'utf8');

assert(activity.includes('webView.getLocationOnScreen(webViewLocation)'), '必须读取 WebView 的实际屏幕位置');
assert(activity.includes('Math.max(0, sb.top - Math.max(0, webViewLocation[1]))'), '安全区只能补齐未覆盖的状态栏高度');
assert(activity.includes("setProperty('--safe-t', '"), '计算结果必须同步到网页安全区变量');
assert(bridge.includes('return statusBarHeightDp;'), '网页桥接必须复用原生计算结果');
assert(!bridge.includes('getIdentifier("status_bar_height"'), '桥接层不得再次叠加完整状态栏高度');

console.log('status bar inset checks: OK');
