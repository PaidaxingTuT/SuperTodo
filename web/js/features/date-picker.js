"use strict";
// Air Datepicker owns the days -> months -> years navigation and keyboard controls.
const DuePicker=(()=>{
  const field=document.querySelector('#fDueDisplay');
  const picker=new AirDatepicker(field,{
    locale:AirDatepickerZh,view:'days',minView:'days',
    dateFormat:'yyyy-MM-dd',altField:'#fDue',altFieldDateFormat:'yyyy-MM-dd',
    autoClose:true,toggleSelected:false,fixedHeight:true,
    isMobile:matchMedia('(max-width: 600px)').matches,position:'top right',
    prevHtml:Icons.html('back'),nextHtml:Icons.html('right'),
    buttons:[[0,'今天'],[1,'明天'],[7,'一周后'],[null,'清除']].map(([offset,label])=>({
      content:label,tagName:'button',attrs:{type:'button'},
      onClick(dp){
        if(offset===null) dp.clear();
        else{
          const date=new Date();date.setHours(0,0,0,0);
          date.setDate(date.getDate()+offset);
          dp.selectDate(date);dp.setViewDate(date);
        }
        field.focus();if(dp.visible)dp.hide();
      }
    })),
    onShow(finished){
      if(finished) return;
      picker.setCurrentView('days',{silent:true});
      field.setAttribute('aria-expanded','true');
      const calendar=picker.$datepicker;
      calendar.setAttribute('role','dialog');calendar.setAttribute('aria-label','选择截止日期');
      calendar.querySelectorAll('.air-datepicker-nav--action, .air-datepicker-nav--title').forEach(button=>{
        button.setAttribute('role','button');button.tabIndex=0;
        button.setAttribute('aria-label',button.dataset.action==='prev'?'上一页':button.dataset.action==='next'?'下一页':'切换月份或年份视图');
        if(!button.dataset.keyboardBound){
          button.dataset.keyboardBound='true';
          button.addEventListener('keydown',event=>{
            if(event.key==='Enter'||event.key===' '){event.preventDefault();button.click();}
          });
        }
      });
      Icons.mount(calendar);
    },
    onHide(){field.setAttribute('aria-expanded','false');},
    onChangeView(){
      requestAnimationFrame(()=>{
        const body=picker.$datepicker.querySelector('.air-datepicker-body:not(.-hidden-)');
        if(picker.visible && body) UiMotion.animate(body,{opacity:[0,1],scale:[.97,1]},{duration:.16});
      });
    }
  });
  field.addEventListener('click',()=>{if(!picker.visible)picker.show();});
  const close=()=>{if(picker.visible)picker.hide();};
  document.querySelector('#modalBody').addEventListener('scroll',close,{passive:true});
  return {
    set(value){
      picker.clear({silent:true});
      if(value) picker.selectDate(value,{silent:true});
      picker.setViewDate(value||new Date());
      picker.setCurrentView('days',{silent:true});
    },
    close
  };
})();
