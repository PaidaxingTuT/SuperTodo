#!/usr/bin/env node
const assert = require('assert');
const fs = require('fs');

const app = fs.readFileSync('app.js', 'utf8');
const bridge = fs.readFileSync('android-src/main/java/com/dax/supertodo/widget/WidgetBridge.java', 'utf8');

assert(!app.includes('simulatedPct'), '原生下载不应使用模拟进度');
assert(app.includes('Math.max(displayedPct, realPct)'), '下载百分比必须保持单调不倒退');
assert(app.includes('if(updateDownloadFinished) return;'), '下载完成回调必须防重复');
assert(bridge.includes('if (!successful) return;'), '失败的完成广播不得触发安装');

console.log('update download checks: OK');
