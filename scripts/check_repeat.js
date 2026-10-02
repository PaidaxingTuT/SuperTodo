const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const context = vm.createContext({});
const repeat = fs.readFileSync('repeat.js', 'utf8');
vm.runInContext(repeat, context);
const create = context.createRepeatItem;
const now = new Date(2026, 9, 2, 12);
function item(extra = {}) {
  return {id:'original', title:'买药', note:'饭后', types:['待办'], scenes:['家里','学校'], time:'今年', cost:20, star:3, done:true, doneScenes:['家里','学校'], doneTypes:['待办'], repeatDays:7, due:'2026-10-02', ...extra};
}
const original = item();
const items = [original];
const next = create(items, original, now);
assert.equal(next.due, '2026-10-09');
assert.equal(next.done, false);
assert.equal(next.title, original.title);
assert.equal(next.cost, 20);
assert.deepEqual(Array.from(next.doneScenes), []);
assert.deepEqual(Array.from(next.doneTypes), []);
assert.equal(original.repeatNextId, next.id);
next.scenes.push('网上');
assert.equal(original.scenes.length, 2, 'successor arrays must be independent');
original.done = false;
create(items, original, now);
original.done = true;
create(items, original, now);
assert.equal(items.length, 2, 'undo/re-complete must not duplicate successors');
const restored = JSON.parse(JSON.stringify(items));
create(restored, restored[0], now);
assert.equal(restored.length, 2, 'saved marker must survive reload/backup');
for (const [due, days, expected] of [
  ['', 3, '2026-10-05'],
  ['2026-09-01', 7, '2026-10-06'],
  ['2026-10-10', 7, '2026-10-17'],
  ['2026-12-31', 1, '2027-01-01'],
  ['2028-02-28', 1, '2028-02-29'],
]) {
  const source = item({due, repeatDays:days});
  assert.equal(create([source], source, now).due, expected);
}
for (const days of [undefined, 0, -1, 1.5, 'oops', 36501, Infinity]) {
  const source = item({repeatDays:days});
  assert.equal(create([source], source, now), undefined);
}
assert.equal(create([], item({done:false}), now), undefined);
next.done = true;
assert.equal(create(items, next, now).due, '2026-10-16');

// Exercise the actual form and completion entry points without booting the app.
const app = fs.readFileSync('app.js', 'utf8');
function functionSource(name) {
  const start = app.indexOf(`function ${name}(`);
  const end = app.indexOf('\nfunction ', start + 1);
  assert.ok(start >= 0 && end > start);
  return app.slice(start, end);
}
const form = {
  '#fTitle':{value:'买药'}, '#fRepeatDays':{value:'7', validity:{badInput:false}, focus(){}},
  '#fNote':{value:''}, '#fCost':{value:''}, '#fDue':{value:''},
};
Object.assign(context, {Number, Date, state:{items:[]}, $:key=>form[key], $$:()=>[], segSelAll:()=>[], segSel:()=>'', alertDlg:()=>{}});
vm.runInContext(`let editStar=0; ${functionSource('gather')}`, context);
assert.equal(context.gather().repeatDays, 7);
for (const value of ['0', '-2', '1.2', '36501']) {
  form['#fRepeatDays'].value = value;
  assert.equal(context.gather(), null);
}
form['#fRepeatDays'].value = '';
assert.equal(context.gather().repeatDays, 0);
Object.assign(context, {
  UiMotion:{check(){}, animate(){}, progress(){}},
  document:{querySelector:()=>null}, itemScenes:it=>it.scenes, itemTypes:it=>it.types,
  syncItemDoneToQuadrant:()=>{}, triggerHaptic:()=>{}, save:()=>{}, currentItems:()=>[],
  sectionGroups:()=>[], calcCostSummary:()=>null, setTimeout:()=>1, clearTimeout:()=>{}, render:()=>{},
});
vm.runInContext(`let toggleDoneTimer=null; ${functionSource('toggleDone')}`, context);
const grouped = item({done:false, doneScenes:[], doneTypes:[]});
context.state = {items:[grouped], view:{name:'home'}};
context.toggleDone(grouped.id, 'scene', '家里');
assert.equal(context.state.items.length, 1, 'partial completion must not repeat');
context.toggleDone(grouped.id, 'scene', '学校');
assert.equal(context.state.items.length, 2, 'final scene completion must repeat');
assert.equal(context.state.items[1].done, false);
vm.runInContext(`${functionSource('itemSubtasks')} ${functionSource('toggleSubtask')} ${functionSource('subtaskProgressHTML')} ${functionSource('subtasksHTML')}`, context);
const steps = item({done:false, subtasks:[{id:'s1', title:'订酒店', done:false}, {id:'s2', title:'买车票', done:false}]});
context.state = {items:[steps], view:{name:'home'}};
context.toggleSubtask(steps.id,0,true);
assert.equal(steps.subtasks[0].done, true);
assert.equal(steps.subtasks[1].done, false);
assert.equal(steps.done, false, 'partially completed subtasks must leave parent unfinished');
context.toggleDone(steps.id);
assert.ok(steps.subtasks.every(task=>task.done), 'completing parent must check every step');
assert.ok(context.state.items[1].subtasks.every(task=>!task.done), 'next occurrence must reset every step');
context.toggleSubtask(steps.id,0,false);
assert.equal(steps.done, false, 'unchecking a completed step must reopen parent');
assert.deepEqual(Array.from(steps.doneScenes), []);
assert.equal(steps.subtasks[1].done, true, 'reopening must preserve other completed steps');
assert.equal(context.state.items.length, 2);
context.toggleSubtask(steps.id,0,true);
assert.equal(steps.done,true,'checking the final subtask must complete the parent');
assert.equal(context.state.items.length,2,'re-completing subtasks must not duplicate the next occurrence');
context.toggleSubtask(steps.id,0,false);
context.toggleSubtask(steps.id,99,true);
assert.equal(context.state.items.length, 2);
context.esc = text=>String(text).replace(/</g,'&lt;').replace(/"/g,'&quot;');
assert.match(context.subtasksHTML(steps), /已完成 1 项，共 2 项/);
assert.match(context.subtasksHTML(steps), /--progress:50/);
assert.equal(context.subtasksHTML({}), '');
const four=item({done:false,doneScenes:[],doneTypes:[],subtasks:Array.from({length:4},(_,i)=>({title:'步骤'+i,done:false}))});
context.state={items:[four],view:{name:'list',group:'家里'},groupBy:'scene'};
for(let i=0;i<3;i++)context.toggleSubtask(four.id,i,true);
assert.equal(four.done,false);
context.toggleSubtask(four.id,3,true);
assert.equal(four.done,true,'all four steps must complete the entire item across its groups');
assert.deepEqual(Array.from(four.doneScenes),four.scenes);
assert.deepEqual(Array.from(four.doneTypes),four.types);
assert.equal(context.state.items.length,2);
assert.ok(context.state.items[1].subtasks.every(task=>!task.done));
Object.assign(context,{uid:()=> 'form-item',hideModal:()=>{},gather:()=>({title:'清单',types:[],scenes:[],subtasks:[{title:'步骤',done:true}],repeatDays:0})});
context.state={items:[],view:{name:'home'}};
vm.runInContext(`let editId=null,pendingQuadrantAddKey=null; ${functionSource('saveForm')}`,context);
context.saveForm();
assert.equal(context.state.items[0].done,true,'saving a fully checked checklist must complete its parent');
context.gather=()=>({title:'清单',types:[],scenes:[],subtasks:[{title:'步骤',done:false}],repeatDays:0});
vm.runInContext("editId='form-item'",context);
context.saveForm();
assert.equal(context.state.items[0].done,false,'editing a completed checklist to add an unfinished step must reopen it');
assert.match(context.subtasksHTML({id:'safe',subtasks:[{title:'<script>',done:false}]}), /&lt;script>/);
assert.equal(fs.readFileSync('android-src/main/assets/repeat.js', 'utf8'), repeat);
console.log('Repeat checks: OK');
