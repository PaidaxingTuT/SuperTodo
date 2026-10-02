# 日期选择器迁移：Air Datepicker 3.6.0

已替换 Flatpickr；视图导航由 Air Datepicker 提供，保留原有 DuePicker.set / close 接口。

## 资源文件

本地引入 air-datepicker.js、air-datepicker.css、air-datepicker-zh.js、date-picker.js。
删除 flatpickr.min.js、flatpickr.min.css、flatpickr-zh.js、flatpickr-LICENSE.md。
许可保留在 air-datepicker-LICENSE，CI 已同步打包新资源，无需运行时访问 CDN。

Air Datepicker 官方资源：
- https://cdn.jsdelivr.net/npm/air-datepicker@3.6.0/air-datepicker.js
- https://cdn.jsdelivr.net/npm/air-datepicker@3.6.0/air-datepicker.css

中文资源取自官方 locale/zh.js（CommonJS），本地 air-datepicker-zh.js 改为经典脚本变量 AirDatepickerZh，无需打包器。
官方文档：https://air-datepicker.com/docs

## HTML 与资源加载顺序

```html
<link rel="stylesheet" href="air-datepicker.css">
<link rel="stylesheet" href="style.css?v=air-date-picker">

<label class="fld-label" for="fDueDisplay">截止日期</label>
<div class="due-field">
  <input type="hidden" id="fDue">
  <input type="text" id="fDueDisplay" placeholder="选择日期" autocomplete="off"
         readonly aria-haspopup="dialog" aria-expanded="false">
  <morph-icon data-icon="calendar" aria-hidden="true"></morph-icon>
</div>

<!-- 放在现有 icons.js、animations.js 后，以及 app.js 前 -->
<script src="air-datepicker.js"></script>
<script src="air-datepicker-zh.js"></script>
<script src="date-picker.js"></script>
```

## 完整主题 CSS

复用项目现有 --surface、--text、--primary 等变量，自动适配日夜模式。

```css
.due-field{position:relative}
.due-field input{width:100%;padding-right:42px!important;cursor:pointer}
.due-field>morph-icon{position:absolute;right:14px;top:50%;transform:translateY(-50%);width:20px;height:20px;color:var(--text3)}
.air-datepicker{
  --adp-width:334px;--adp-z-index:1200;--adp-padding:12px;
  --adp-background-color:var(--surface);--adp-color:var(--text);
  --adp-background-color-hover:var(--primary-faint);--adp-background-color-active:var(--primary-soft);
  --adp-accent-color:var(--primary-ink);--adp-color-other-month-hover:var(--text2);
  --adp-color-secondary:var(--text2);--adp-color-other-month:var(--text3);
  --adp-color-current-date:var(--primary-ink);--adp-color-disabled:var(--text3);
  --adp-border-color:var(--line);--adp-border-color-inner:var(--line);
  --adp-border-radius:20px;--adp-box-shadow:var(--shadow2);
  --adp-cell-border-radius:12px;--adp-cell-background-color-hover:var(--primary-faint);
  --adp-cell-background-color-selected:var(--primary);--adp-cell-background-color-selected-hover:var(--primary-deep);
  --adp-cell-background-color-in-range:var(--primary-faint);--adp-cell-background-color-in-range-hover:var(--primary-soft);
  --adp-day-cell-height:44px;--adp-month-cell-height:64px;--adp-year-cell-height:64px;
  --adp-nav-height:48px;--adp-nav-arrow-color:var(--text2);--adp-nav-action-size:44px;
  --adp-nav-color-secondary:var(--text2);
  --adp-day-name-color:var(--text3);--adp-font-family:inherit;--adp-font-size:14px;
  width:min(var(--adp-width),calc(100vw - 24px));
}
.air-datepicker.-is-mobile-{--adp-mobile-width:354px;--adp-mobile-day-cell-height:44px;--adp-mobile-month-cell-height:64px;--adp-mobile-year-cell-height:64px}
.air-datepicker-overlay{--adp-overlay-z-index:1100}
.air-datepicker--pointer{display:none}
.air-datepicker-nav--title{min-height:44px;font-size:16px;font-weight:600;border-radius:12px}
.air-datepicker-nav--title i{color:var(--text2)}
.air-datepicker-nav--action{border-radius:12px}
.air-datepicker-nav morph-icon{width:20px;height:20px}
.air-datepicker-nav morph-icon svg{width:100%;height:100%}
.air-datepicker-cell.-selected-{color:var(--on-primary)}
.air-datepicker-cell.-other-month-,.air-datepicker-cell.-other-decade-{opacity:.45}
.air-datepicker-cell.-current-:not(.-selected-){box-shadow:inset 0 0 0 1px var(--primary-ink)}
.air-datepicker--buttons{padding:12px;gap:6px}
.air-datepicker-buttons{display:flex;gap:6px}
.air-datepicker-button{flex:1;min-height:44px;padding:0 4px;border-radius:10px;background:var(--primary-faint);color:var(--primary-ink);font-size:12px;font-weight:600}
.air-datepicker-button:last-child{background:var(--surface-alt);color:var(--text2)}
.air-datepicker-button:hover{background:var(--primary-soft)}
.air-datepicker [role="button"]:focus-visible,.air-datepicker-button:focus-visible{outline:2px solid var(--primary-ink);outline-offset:2px}
@media(prefers-reduced-motion:reduce){.air-datepicker{transition:none}}
```

## 完整初始化与按钮绑定 JS

```javascript
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
```

## 与现有表单对接

编辑或新增表单时调用 DuePicker.set(item.due || '')；关闭表单时调用 DuePicker.close()。
保存仍读取 document.querySelector('#fDue').value，格式为 YYYY-MM-DD。
标题点击由库切换 days → months → years；年份与月份选中后由库下钻。
手机宽度下使用库自带居中触屏面板，按钮高度 44px，月份和年份格高度 64px。
视图切换使用项目现有 UiMotion，遵循减少动画设置。

验证：三级切换、2027 年六月 15 日选择、快捷日期、清除、取消保留原日期、390×844 手机布局、关闭重复调用及跨年快捷日期测试。
