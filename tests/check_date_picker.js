const assert=require('node:assert/strict');
const fs=require('node:fs');
const vm=require('node:vm');
let config,selected,closed=false,scroll,view,viewDate;
const field={setAttribute(){},addEventListener(){},focus(){closed=false;}};
const picker={
  visible:true,selectDate(value){selected=value;},clear(){selected='';},hide(){closed=true;},
  setViewDate(value){viewDate=value;},setCurrentView(value){view=value;}
};
vm.runInNewContext(fs.readFileSync('web/js/features/date-picker.js','utf8')+'\nDuePicker.set("2026-12-31");',{
  Date:class extends Date{constructor(){super(2026,11,31,23,30);}},
  AirDatepicker:class{constructor(el,options){config=options;return picker;}},AirDatepickerZh:{},
  matchMedia:()=>({matches:true}),Icons:{html:()=>''},UiMotion:{animate(){}},
  document:{querySelector:selector=>selector==='#fDueDisplay'?field:{addEventListener(type,fn){scroll=fn;}}}
});
assert.equal(selected,'2026-12-31','existing ISO dates must reach the calendar unchanged');
assert.equal(view,'days');assert.equal(viewDate,'2026-12-31');
assert.equal(config.isMobile,true);assert.equal(config.minView,'days');
assert.equal(config.altFieldDateFormat,'yyyy-MM-dd');
for(const [index,expected] of [[0,[2026,11,31]],[1,[2027,0,1]],[2,[2027,0,7]]]){
  config.buttons[index].onClick(picker);
  assert.deepEqual([selected.getFullYear(),selected.getMonth(),selected.getDate()],expected);
  assert.equal(selected.getHours(),0);assert.equal(closed,true);
}
config.buttons[3].onClick(picker);assert.equal(selected,'');assert.equal(closed,true);
closed=false;scroll();assert.equal(closed,true);
closed=false;picker.visible=false;scroll();assert.equal(closed,false,'a hidden calendar must not be destroyed again');
console.log('Air Datepicker shortcuts, ISO format and year boundary checks: OK');
