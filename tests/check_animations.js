const assert=require('node:assert/strict');
const fs=require('node:fs');
const vm=require('node:vm');
const reduced={matches:false}, clicks={}, tweens=new Map();
let motionCalls=0,headerResize;
const motionFrames=new Map();
const motionControls=new Map(),motionEnds=new Map();
function node(){
  return {hidden:false,dataset:{},matches:()=>false,style:{setProperty(key,value){this[key]=value;},removeProperty(key){delete this[key];}},querySelector(){return null;},animate(){}};
}
const context=vm.createContext({
  matchMedia:()=>reduced,
  document:{documentElement:{classList:{add(){}}},addEventListener:(type,fn)=>{clicks[type]=fn;},querySelectorAll:()=>[]},
  MutationObserver:class{observe(){}},
  ResizeObserver:class{constructor(callback){headerResize=callback;} observe(){} disconnect(){}},
  getComputedStyle:()=>({transform:'none',opacity:'1',getPropertyValue:()=>''}),
  Motion:{animate:(el,frames)=>{
    motionCalls++;motionFrames.set(el,frames);
    const control={stop(){},then(callback){motionEnds.set(control,callback);return {catch(){}};}};
    motionControls.set(el,control);return control;
  }},
  Icons:{mount(){},set(el,name){if(el)el.icon=name;}},
  gsap:{to:(el,options)=>tweens.set(el,options),fromTo:(el,from,options)=>tweens.set(el,options),
    getProperty:()=>45,killTweensOf:el=>tweens.delete(el),set:(el,properties)=>Object.assign(el.style,properties)}
});
vm.runInContext(fs.readFileSync('web/js/ui/animations.js','utf8')+'\nglobalThis.ui=UiMotion;',context);
const ui=context.ui;
const checkbox=node(),checkboxBox=node();
checkbox.matches=selector=>selector==='.subtask-check input';
checkbox.parentElement=checkboxBox;
checkbox.closest=()=>checkboxBox;
ui.check(checkbox,true);
assert.ok(motionFrames.has(checkboxBox),'checkbox and tick must animate together in the shared wrapper');
assert.equal(motionFrames.has(checkbox),false,'the input must not scale separately from its tick');
const steps=node(),arrow=node();
const details={open:false,dataset:{},querySelector:selector=>selector==='.subtask-steps'?steps:arrow};
const summary={closest:()=>details};
const event={target:{closest:selector=>selector==='.subtask-list summary'?summary:null},preventDefault(){this.prevented=true;}};
clicks.click(event);
assert.equal(event.prevented,true);
assert.equal(details.open,true);
assert.equal(tweens.get(steps).height,'auto');
assert.equal(tweens.get(steps).marginTop,8,'animated gap must match the final CSS gap');
assert.equal('paddingTop' in tweens.get(steps),false,'padding must not leave a minimum height at the end of collapsing');
clicks.click(event); // Close before opening finishes.
assert.equal(tweens.get(steps).height,0);
clicks.click(event); // Reverse closing immediately.
tweens.get(steps).onComplete();
assert.equal(details.open,true,'latest click must win');
clicks.click(event);
tweens.get(steps).onComplete();
assert.equal(details.open,false);
const ring=node(),number={textContent:'0'};
ring.querySelector=()=>number;
ui.progress({querySelector:()=>ring},75,3);
assert.equal(number.textContent,3);
assert.equal(tweens.get(ring)['--progress'],75);
ui.progress({querySelector:()=>ring},100,4);
assert.equal(ring.dataset.complete,'false','completion must wait for the progress animation');
tweens.get(ring).onComplete();
assert.equal(ring.dataset.complete,'true','100 percent must use an undashed circle without a seam');
ui.progress({querySelector:()=>ring},75,3);
assert.equal(ring.dataset.complete,'false','undoing a subtask must restore proportional progress');
const panel=node();
ui.hide(panel);
assert.equal('transform' in motionFrames.get(panel),false,'full-screen masks must fade without shrinking');
assert.equal(ui.isOpen(panel),false,'closing panels must stop intercepting back navigation');
assert.equal(panel.inert,true);
const oldExit=motionControls.get(panel);
ui.show(panel);
motionEnds.get(oldExit)();
assert.equal(panel.hidden,false,'a cancelled exit must not hide a reopened panel');
assert.equal(ui.isOpen(panel),true);
assert.equal(panel.inert,false);
const drawer=node();
drawer.matches=selector=>selector==='.drawer';
ui.hide(drawer);
assert.equal(motionFrames.get(drawer).transform[1],'translateX(-100%)','drawer exit must slide fully off screen');
assert.equal('opacity' in motionFrames.get(drawer),false,'drawer slide must not fade the text midway');
assert.equal(drawer.hidden,false,'drawer must remain visible until Motion finishes');
motionEnds.get(motionControls.get(drawer))();
assert.equal(drawer.hidden,true,'Motion completion must finalize the exit');
ui.show(drawer);
assert.equal(motionFrames.get(drawer).transform[0],'translateX(-100%)','drawer entry must slide from outside the screen');
assert.equal(drawer.style.transform,'translateX(-100%)','drawer first frame must be seeded before Motion resolves');
ui.hide(drawer);
ui.show(drawer);
assert.equal(motionFrames.get(drawer).transform[0],'none','reversing a visible drawer must continue from its current transform');
reduced.matches=true;
clicks.click(event);
assert.equal(details.open,true);
ui.progress({querySelector:()=>ring},100,4);
assert.equal(ring.style['--progress'],100);
ui.hide(panel);
assert.equal(panel.hidden,true);
reduced.matches=false;
const row={dataset:{item:'task'},querySelector:selector=>selector==='[data-done]'?{dataset:{doneKey:'家里'}}:null,querySelectorAll:()=>[]};
context.document.querySelectorAll=selector=>selector.includes('data-item')?[row]:[];
const before=motionCalls;
ui.beforeRender();
ui.afterRender();
row.dataset.item='other-page-task';
ui.afterRender();
assert.equal(motionCalls,before,'list redraws and page changes must not replay all title animations');
const bar=node();
bar.style.width='25%'; bar.dataset.progress='75';
bar.closest=()=>({dataset:{costKey:'预算'}});
context.document.querySelectorAll=selector=>selector.includes('csc-progress-bar')?[bar]:[];
ui.beforeRender();
bar.style.width='75%';
ui.afterRender();
assert.equal(bar.style.width,'25%','budget redraw must resume the current width');
assert.equal(tweens.get(bar).width,'75%','GSAP must animate budget redraws');
reduced.matches=true;
ui.afterRender();
assert.equal(bar.style.width,'75%');
let headerHeight=40;
const header={parentElement:node(),getBoundingClientRect:()=>({top:108,height:headerHeight})};
const front=node();
front.getBoundingClientRect=()=>({top:100});
header.parentElement.parentElement=front;
let summaryWidth=76;
header.parentElement.querySelector=()=>({getBoundingClientRect:()=>({width:summaryWidth})});
context.document.querySelectorAll=selector=>selector.includes('.card-main')?[header]:[];
ui.afterRender();
assert.equal(header.parentElement.style['--subtask-anchor'],'20px','the control must be centered on the title and metadata');
assert.equal(front.style['--subtask-drag-anchor'],'28px','drag handle must use the header center relative to the card');
assert.equal(header.parentElement.style['--subtask-space'],'84px','reserve only the actual control width plus its gap');
headerResize([{target:header}]);
assert.equal(header.parentElement.style['--subtask-anchor'],'20px','checklist expansion must not move the control');
headerHeight=60;
summaryWidth=90;
headerResize([{target:header}]);
assert.equal(header.parentElement.style['--subtask-anchor'],'30px','wrapped titles must keep their own center');
assert.equal(front.style['--subtask-drag-anchor'],'38px','drag handle must track wrapped headers, not expanded steps');
assert.equal(header.parentElement.style['--subtask-space'],'98px','wider counts must reserve enough space');
const themeSource=fs.readFileSync('web/js/app.js','utf8').match(/function toggleColorMode\(\)\{[\s\S]*?\n\}/)[0];
for(const [mode,dark,expected] of [['dark',true,'light'],['light',false,'dark'],['system',true,'light'],['system',false,'dark']]){
  const theme={state:{colorMode:mode},isDarkMode:()=>dark,save(){},applyColorMode(){}};
  vm.runInNewContext(themeSource+'\ntoggleColorMode();',theme);
  assert.equal(theme.state.colorMode,expected,'one click must always change the visible theme');
}
const themeClasses=new Set();
let finishTheme,flushed=false;
const themeRoot={dataset:{},classList:{add:name=>themeClasses.add(name),remove:name=>themeClasses.delete(name)},get offsetHeight(){flushed=true;return 100;}};
vm.runInNewContext(fs.readFileSync('web/js/app.js','utf8').match(/function applyColorMode\(\)\{[\s\S]*?\n\}/)[0]+'\napplyColorMode();',{
  state:{theme:'#0b57d0',colorMode:'dark'},isDarkMode:()=>true,document:{documentElement:themeRoot},$:()=>null,
  applyTheme(){assert.ok(themeClasses.has('theme-changing'),'theme colors must change with CSS transitions disabled');},
  renderColorModeSeg(){},requestAnimationFrame:callback=>{finishTheme=callback;}
});
assert.ok(flushed,'the new foreground and background must be applied before restoring transitions');
finishTheme();
assert.equal(themeClasses.size,0);
let dragStart;
const swipeInit=fs.readFileSync('web/js/app.js','utf8').match(/function initSwipeGestures\(\)\{[\s\S]*?(?=\nlet sortable)/)[0];
vm.runInNewContext(swipeInit+'\ninitSwipeGestures();',{
  $:()=>({addEventListener(type,callback){if(type==='dragstart')dragStart=callback;}}),
  window:{addEventListener(){}},document:{addEventListener(){}},
});
for(const sortableRow of [true,false]){
  let prevented=false;
  dragStart({target:{closest:selector=>selector==='.item-row.sortable-chosen'&&sortableRow},preventDefault(){prevented=true;}});
  assert.equal(prevented,!sortableRow,'native row sorting must survive the text-drag guard');
}
console.log('Animation interruption and reduced-motion checks: OK');
