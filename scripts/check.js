const fs = require('node:fs');
const path = require('node:path');
const { spawnSync } = require('node:child_process');
const root = path.resolve(__dirname, '..');
for (const file of fs.readdirSync(path.join(root, 'tests')).sort()) {
  if (!/\.(js|cjs)$/.test(file)) continue;
  const result = spawnSync(process.execPath, [path.join(root, 'tests', file)], { cwd: root, stdio: 'inherit' });
  if (result.status !== 0) process.exit(result.status || 1);
}
