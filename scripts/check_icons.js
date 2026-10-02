const assert=require('node:assert/strict');
const fs=require('node:fs');
const vm=require('node:vm');
let defined=false;
const document={documentElement:{dataset:{colorMode:'light'}},querySelectorAll:()=>[],addEventListener(){}};
const context=vm.createContext({document,Morphicons:{defineMorphIcon(){defined=true;}},MutationObserver:class{observe(){}}});
vm.runInContext(fs.readFileSync('icons.js','utf8')+'\nglobalThis.icons=Icons;',context);
assert.ok(defined,'the local Morphicons element must be registered');
const calls=[];
const icon={dataset:{icon:'down'},matches:()=>true,morphTo:(path,spring)=>calls.push(['morph',path,spring]),set:path=>calls.push(['set',path])};
context.icons.set(icon,'up');
context.icons.set(icon,'down');
context.icons.set(icon,'down');
assert.equal(calls.length,2,'unchanged states must not replay the icon animation');
assert.equal(calls[1][2],'snappy');
context.icons.set(icon,'up',false);
assert.equal(calls[2][0],'set','restored list states must not replay icon entrances');
for(const file of ['index.html','app.js','widget_dialog.html']){
  const source=fs.readFileSync(file,'utf8');
  for(const [,name] of source.matchAll(/<morph-icon data-icon="([^"]+)"/g)){
    if(name!=='info') assert.notEqual(context.icons.html(name).match(/ icon="([^"]+)"/)[1],context.icons.html('info').match(/ icon="([^"]+)"/)[1],`unknown icon ${name} in ${file}`);
  }
  assert.doesNotMatch(source.replace(/<svg[^>]*>.*?subtask-track.*?<\/svg>/gs,''),/<svg\b|data:image\/svg/,'legacy interface SVG must not remain');
}
assert.doesNotMatch(fs.readFileSync('style.css','utf8'),/data:image\/svg/);
for(const file of ['style.css','widget_dialog.html']){
  assert.match(fs.readFileSync(file,'utf8'),/morph-icon\[data-icon="blank"\]\{visibility:hidden\}/,'empty checkbox glyphs must not leave a dot');
}
for(const file of ['index.html','widget_dialog.html']){
  const source=fs.readFileSync(file,'utf8');
  assert.ok(source.indexOf('src="morphicons.min.js"')<source.indexOf('src="icons.js"'));
  assert.ok(source.indexOf('src="icons.js"')<source.indexOf('src="animations.js"'));
}
console.log('Morphicons catalog, lifecycle and legacy-icon checks: OK');
