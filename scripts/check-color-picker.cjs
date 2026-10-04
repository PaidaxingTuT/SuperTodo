const fs=require('fs'),vm=require('vm'),assert=require('assert');
const source=fs.readFileSync('app.js','utf8');
const fn=source.slice(source.indexOf('function hslToHex('),source.indexOf('function updateColorPicker('));
const context={};vm.createContext(context);vm.runInContext(fn,context);
for(const [h,s,l,hex] of [[0,100,50,'#ff0000'],[120,100,50,'#00ff00'],[240,100,50,'#0000ff'],[360,100,50,'#ff0000'],[0,0,0,'#000000'],[0,0,100,'#ffffff'],[0,0,50,'#808080']])assert.equal(context.hslToHex(h,s,l),hex);
console.log('Color conversion checks passed');
