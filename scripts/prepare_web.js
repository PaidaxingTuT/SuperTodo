// Prepare the same directory layout for the static server and Capacitor.
const fs = require('node:fs');
const path = require('node:path');
const root = path.resolve(__dirname, '..');
const output = path.join(root, 'dist');
fs.mkdirSync(output, { recursive: true });
for (const file of ['index.html', 'CHANGELOG.md']) {
  fs.copyFileSync(path.join(root, file), path.join(output, file));
}
fs.cpSync(path.join(root, 'web'), path.join(output, 'web'), { recursive: true });
console.log('Web assets prepared in dist/');
