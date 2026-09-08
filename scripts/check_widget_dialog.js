const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');

const source = fs.readFileSync('widget_dialog.html', 'utf8');
const androidAsset = fs.readFileSync('android-src/main/assets/widget_dialog.html', 'utf8');

assert.equal(androidAsset, source, 'Android asset must match widget_dialog.html');
assert.match(source, /-webkit-tap-highlight-color:\s*transparent/);
assert.match(source, /function applyWidgetTheme\(hex\)/);
assert.match(source, /applyWidgetTheme\(rootData\.theme\)/);
assert.doesNotMatch(source.match(/\.btn-close:active\s*\{[^}]*\}/s)[0], /transform/);
assert.doesNotMatch(source.match(/\.settings-row\.sortable-drag\s*\{[^}]*\}/s)[0], /transform:\s*none/);

const appScript = source.slice(source.lastIndexOf('<script>') + 8, source.lastIndexOf('</script>'));
new vm.Script(appScript);

const start = source.indexOf('    function initSortable(el) {');
const end = source.indexOf('\n\n    function removeFromCurated', start);
assert.ok(start >= 0 && end > start, 'initSortable function not found');

const sandbox = {};
vm.runInNewContext(`
  let sortableInstance = null;
  let rootData = { widget2x2: [{ id: 'a' }, { id: 'b' }, { id: 'c' }] };
  let saveCount = 0;
  function saveData() { saveCount += 1; }
  class Sortable {
    constructor(el, options) { globalThis.options = options; }
    destroy() {}
  }
  ${source.slice(start, end)}
  const sequences = [{ textContent: '1.' }, { textContent: '2.' }, { textContent: '3.' }];
  initSortable({ querySelectorAll: () => sequences });
  options.onEnd({ oldIndex: 0, newIndex: 2 });
  globalThis.result = {
    ids: rootData.widget2x2.map(item => item.id).join(','),
    labels: sequences.map(item => item.textContent).join(','),
    saveCount,
    forceFallback: options.forceFallback,
    fallbackOnBody: options.fallbackOnBody,
    delayOnTouchOnly: options.delayOnTouchOnly,
    draggable: options.draggable
  };
`, sandbox);

assert.deepEqual(JSON.parse(JSON.stringify(sandbox.result)), {
  ids: 'b,c,a',
  labels: '1.,2.,3.',
  saveCount: 1,
  forceFallback: true,
  fallbackOnBody: true,
  delayOnTouchOnly: true,
  draggable: '.settings-row'
});

console.log('2x2 widget dialog checks: OK');
