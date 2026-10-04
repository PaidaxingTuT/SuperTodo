const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
for (const file of ['index.html', 'web/widget_dialog.html', 'web/styles/style.css', 'web/styles/md3.css']) {
  const source = fs.readFileSync(file, 'utf8');
  const refs = file.endsWith('.html') ? [...source.matchAll(/(?:src|href|data-md3-href)="([^"#]+)"/g)].map(m=>m[1]) : [...source.matchAll(/url\(['"]?([^)'"\s]+)['"]?\)/g)].map(m=>m[1]);
  for (const ref of refs) {
    if (/^(https?:|data:|#)/.test(ref)) continue;
    const target = path.resolve(path.dirname(file), ref.split('?')[0]);
    assert.ok(fs.existsSync(target), `${file}: missing ${ref}`);
  }
}
assert.ok(fs.existsSync('debug.keystore'), 'the fixed signing key must remain at the repository root');
const inject = fs.readFileSync('scripts/inject_android_widget.js', 'utf8');
const widgetScripts = [...fs.readFileSync('web/widget_dialog.html', 'utf8').matchAll(/<script[^>]+src="([^"]+)"/g)].map(m=>m[1]);
for (const script of widgetScripts) assert.ok(inject.includes(`'${script}'`), `native assets omit ${script}`);
console.log('Web references, native widget dependencies and signing path: OK');
