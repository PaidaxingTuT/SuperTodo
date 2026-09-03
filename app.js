'use strict';
/* ========== 存储 ========== */
const KEY='listapp.data.v2';
const DEFAULTS={types:['购物','待办','计划','旅游','愿望'],scenes:['家里','学校','出差','网上','线下'],times:['今年','明年','以后再说']};

/* ========== 状态 ========== */
let state={
  items:[], types:DEFAULTS.types.slice(), scenes:DEFAULTS.scenes.slice(), times:DEFAULTS.times.slice(),
  type:'全部',      // 当前类型
  groupBy:'scene',  // 分组维度 scene|time
  sortKey:'默认', sortAsc:true,
  view:{name:'home'},  // home | {name:'list', group, groupKey}
  search:'',
  theme:'#0b57d0',
  colorMode:'system',
  ai:{enabled:false,base:'',key:'',model:''}
};

/* 预设主色 */
const PALETTE=['#0b57d0','#0f6b3c','#b3261e','#7c2d92','#007372','#e8710a','#d01884','#37474f','#1565c0','#2e7d32'];

/* ========== 工具 ========== */
const $=s=>document.querySelector(s), $$=s=>Array.from(document.querySelectorAll(s));
const uid=()=>Date.now().toString(36)+Math.random().toString(36).slice(2,6);
const esc=s=>String(s==null?'':s).replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const money=n=>Number(n).toLocaleString('zh-CN',{maximumFractionDigits:2});
const isOverdue=d=>d&&new Date(d+'T23:59:59')<new Date();
const fmtDue=d=>d?d.split('-')[1]+'/'+d.split('-')[2]:'';

function save(){
  localStorage.setItem(KEY,JSON.stringify(state));
  syncToNativeWidget();
}
function load(){
  try{const d=JSON.parse(localStorage.getItem(KEY));if(d){
    state.items=d.items||[];
    if(Array.isArray(d.types)&&d.types.length)state.types=d.types;
    if(Array.isArray(d.scenes)&&d.scenes.length)state.scenes=d.scenes;
    if(Array.isArray(d.times)&&d.times.length)state.times=d.times;
    if(d.groupBy)state.groupBy=d.groupBy;
    if(d.theme)state.theme=d.theme;
    if(['system','light','dark'].includes(d.colorMode))state.colorMode=d.colorMode;
    state.sortKey=d.sortKey||'默认'; state.sortAsc=d.sortAsc!==false;
    if(d.ai)state.ai=Object.assign({enabled:false,base:'',key:'',model:''},d.ai);
  }}catch(e){}
  syncFromNativeWidget();
}

/* ========== 桌面小部件桥接（小米澎湃OS / Android） ========== */
function syncToNativeWidget(){
  try{
    if(window.AndroidWidgetBridge&&window.AndroidWidgetBridge.syncData){
      window.AndroidWidgetBridge.syncData(JSON.stringify(state));
    }
  }catch(e){}
}
function syncFromNativeWidget(){
  try{
    if(window.AndroidWidgetBridge&&window.AndroidWidgetBridge.getData){
      const raw=window.AndroidWidgetBridge.getData();
      if(!raw)return;
      const d=JSON.parse(raw);
      if(d&&Array.isArray(d.items)){
        let changed=false;
        d.items.forEach(natIt=>{
          const localIt=state.items.find(x=>x.id===natIt.id);
          if(localIt&&localIt.done!==natIt.done){
            localIt.done=natIt.done;
            changed=true;
          }
        });
        if(changed){
          localStorage.setItem(KEY,JSON.stringify(state));
          if(typeof render==='function') render();
        }
      }
    }
  }catch(e){}
}
window.onNativeWidgetResume=function(){ syncFromNativeWidget(); };
window.onNativeWidgetAction=function(action,itemId){
  syncFromNativeWidget();
  if(action==='add_item'){ if(typeof openAdd==='function') openAdd(); }
  else if(action==='open_item'&&itemId){ const it=state.items.find(x=>x.id===itemId); if(it&&typeof openEdit==='function') openEdit(it); }
};
document.addEventListener('visibilitychange',()=>{ if(document.visibilityState==='visible') syncFromNativeWidget(); });

/* ========== 主题 ========== */
function hexToHsl(hex){
  var r=parseInt(hex.slice(1,3),16)/255,g=parseInt(hex.slice(3,5),16)/255,b=parseInt(hex.slice(5,7),16)/255;
  var mx=Math.max(r,g,b),mn=Math.min(r,g,b),l=(mx+mn)/2;if(mx===mn)return[0,0,Math.round(l*100)];
  var d=mx-mn,s=d/(1-Math.abs(2*l-1)),h;
  if(mx===r)h=(g-b)/d+(g<b?6:0);else if(mx===g)h=(b-r)/d+2;else h=(r-g)/d+4;
  return[Math.round(h*60),Math.round(s*100),Math.round(l*100)];
}
function hslToCss(h,s,l){ return 'hsl('+h+','+s+'%,'+l+'%)' }
const colorModeQuery=window.matchMedia('(prefers-color-scheme: dark)');
function isDarkMode(){ return state.colorMode==='dark'||(state.colorMode==='system'&&colorModeQuery.matches) }
function applyTheme(hex){
  var h=hexToHsl(hex);
  var dark=isDarkMode();
  var soft=dark?hslToCss(h[0],Math.min(55,h[1]),26):hslToCss(h[0],Math.min(92,h[1]+5),Math.max(87,Math.min(94,92+(h[2]-55)*0.4)));
  var faint=dark?hslToCss(h[0],Math.min(35,h[1]),18):hslToCss(h[0],Math.min(42,h[1]),Math.max(95,Math.min(97,95)));
  var deep=hslToCss(h[0],Math.min(96,h[1]),Math.max(22,Math.round(h[2]*0.86)));
  var ink=dark?hslToCss(h[0],Math.min(92,h[1]+5),72):hex;
  document.documentElement.style.setProperty('--primary',hex);
  document.documentElement.style.setProperty('--primary-ink',ink);
  document.documentElement.style.setProperty('--primary-soft',soft);
  document.documentElement.style.setProperty('--primary-faint',faint);
  document.documentElement.style.setProperty('--primary-deep',deep);
  document.documentElement.style.setProperty('--on-primary','#fff');
  var m=document.querySelector('meta[name=theme-color]'); if(m)m.setAttribute('content',dark?'#111318':hex);
}
function applyColorMode(){
  const dark=isDarkMode(),root=document.documentElement,btn=$('#drawerTheme');
  root.dataset.colorMode=dark?'dark':'light';
  applyTheme(state.theme);
  if(btn){ const label=dark?'切换到日间模式':'切换到夜间模式'; btn.setAttribute('aria-label',label); btn.title=label; }
}
function toggleColorMode(){ state.colorMode=isDarkMode()?'light':'dark'; save(); applyColorMode(); }

/* ========== 分组逻辑 ========== */
function groupKey(it){ return state.groupBy==='scene' ? (it.scene||'未分组') : (it.time||'未分组') }
function groupOrder(){
  const base = state.groupBy==='scene'?state.scenes:state.times;
  const keys = ['未分组'];
  base.forEach(k=>keys.unshift(k));
  return keys;
}
function currentItems(){
  let list=state.items.slice();
  if(state.type!=='全部') list=list.filter(i=>i.type===state.type);
  if(state.search){const q=state.search.toLowerCase();list=list.filter(i=>(i.title+(i.note||'')).toLowerCase().includes(q))}
  return list;
}
function sortItems(list){
  if(state.sortKey==='默认'||state.sortKey==='创建') return list;
  const val=i=>state.sortKey==='花费'?(i.cost||0):state.sortKey==='重要'?(i.star||0):state.sortKey==='日期'?(i.due?new Date(i.due).getTime():Infinity):0;
  const asc=state.sortAsc;
  list.sort((a,b)=>{
    let x=val(a),y=val(b);
    if(x===y) return a.created-b.created;
    if(x===Infinity)return 1; if(y===Infinity)return -1;
    return asc?x-y:y-x;
  });
  return list;
}
function sectionGroups(){
  const items=currentItems();
  const map={};
  items.forEach(it=>{const k=groupKey(it);(map[k]=map[k]||[]).push(it)});
  const out=[];
  groupOrder().forEach(k=>{if(map[k]) out.push({key:k,items:map[k],done:map[k].filter(i=>i.done).length,active:map[k].filter(i=>!i.done).length})});
  return out;
}

/* ========== 返回键：历史栈导航 ========== */
let backSuppress=false, codeBack=false;
function pushLayer(){ history.pushState({l:1},'') }
function syncBack(){ codeBack=true; history.back() }
function backHome(){ state.view={name:'home'}; state.sortKey='默认'; render() }
function closeTopLayer(){
  backSuppress=true;
  if(!$('#dlgModal').hidden){ dlgClose(); backSuppress=false; return true; }
  if(!$('#modal').hidden){ hideModal(); backSuppress=false; return true; }
  if(!$('#ctxModal').hidden){ closeCtx(); backSuppress=false; return true; }
  if(!$('#aiModal').hidden){ closeAi(); backSuppress=false; return true; }
  if(!$('#tidyModal').hidden){ closeTidy(); backSuppress=false; return true; }
  if(!$('#infoModal').hidden){ closeInfo(); backSuppress=false; return true; }
  if(!$('#setModal').hidden){ closeSettings(); backSuppress=false; return true; }
  if(!$('#sortModal').hidden){ closeSort(); backSuppress=false; return true; }
  if(!$('#drawer').hidden){ closeDrawer(); backSuppress=false; return true; }
  if(state.view.name==='list'){ backHome(); backSuppress=false; return true; }
  backSuppress=false;
  return false;
}

/* Capacitor 原生返回键（APK 内） */
function setupNativeBack(){
  try{
    const C=window.Capacitor;
    if(!C||!C.isNativePlatform||!C.isNativePlatform()) return;
    if(C.Plugins&&C.Plugins.App){
      C.Plugins.App.addListener('backButton',()=>{
        if(!closeTopLayer()){ C.Plugins.App.exitApp(); }
      });
    }
  }catch(e){}
}

/* ========== 通用对话框（替代原生 alert/confirm/prompt） ========== */
let dlgType='alert', dlgCb=null, dlgOnCancel=null;
const DICONS={
  info:'<svg viewBox="0 0 24 24" fill="currentColor"><path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-6h2v6zm0-8h-2V7h2v2z"/></svg>',
  question:'<svg viewBox="0 0 24 24" fill="currentColor"><path d="M11 18h2v-2h-2v2zm1-16C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm0 18c-4.41 0-8-3.59-8-8s3.59-8 8-8 8 3.59 8 8-3.59 8-8 8zm0-14c-2.21 0-4 1.79-4 4h2c0-1.1.9-2 2-2s2 .9 2 2c0 2-3 1.75-3 5h2c0-2.25 3-2.5 3-5 0-2.21-1.79-4-4-4z"/></svg>',
  edit:'<svg viewBox="0 0 24 24" fill="currentColor"><path d="M3 17.25V21h3.75L17.8 9.94l-3.75-3.75L3 17.25zM20.7 7.04c.39-.39.39-1.02 0-1.41l-2.34-2.34c-.39-.39-1.02-.39-1.41 0l-1.83 1.83 3.75 3.75 1.83-1.83z"/></svg>',
  delete:'<svg viewBox="0 0 24 24" fill="currentColor"><path d="M6 19c0 1.1.9 2 2 2h8c1.1 0 2-.9 2-2V7H6v12zM19 4h-3.5l-1-1h-5l-1 1H5v2h14V4z"/></svg>',
  check:'<svg viewBox="0 0 24 24" fill="currentColor"><path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-2 15-5-5 1.41-1.41L10 14.17l7.59-7.59L19 8l-9 9z"/></svg>'
};
function dlgShow(opts){
  dlgType=opts.type||'alert';
  dlgCb=opts.onOk||null; dlgOnCancel=opts.onCancel||null;
  const defIc = dlgType==='confirm'?'question':dlgType==='input'?'edit':'info';
  const ic = opts.icon||defIc;
  const icEl=$('#dlgIcon');
  if(icEl){
    icEl.innerHTML=DICONS[ic]||DICONS.info;
    icEl.classList.toggle('danger', ic==='delete');
  }
  $('#dlgTitle').textContent=opts.title||'';
  const hasMsg=!!opts.msg;
  $('#dlgMsg').hidden=!hasMsg; $('#dlgMsg').textContent=opts.msg||'';
  const needInput=dlgType==='input';
  $('#dlgInputWrap').hidden=!needInput;
  if(needInput){ $('#dlgInput').value=opts.initial||''; $('#dlgInput').placeholder=opts.placeholder||''; }
  const hasCancel=dlgType==='confirm'||dlgType==='input';
  $('#dlgCancel').hidden=!hasCancel;
  $('#dlgCancel').textContent=opts.cancelText||'取消';
  $('#dlgOk').textContent=opts.okText||'确定';
  pushLayer();
  $('#dlgMask').hidden=false; $('#dlgModal').hidden=false;
  if(needInput) setTimeout(()=>$('#dlgInput').focus(),60);
}
function dlgClose(){
  $('#dlgMask').hidden=true; $('#dlgModal').hidden=true;
  if(!backSuppress) syncBack();
}
function alertDlg(title,msg){ dlgShow({title,msg,type:'alert',okText:'知道了'}); }
function confirmDlg(title,msg,onOk,okText,icon){ dlgShow({title,msg,type:'confirm',onOk,okText:okText||'确定',icon}); }
function inputDlg(title,placeholder,initial,onOk,onCancel){ dlgShow({title,type:'input',placeholder,initial,onOk,onCancel}); }

/* ========== 渲染 ========== */
function render(){
  renderTitle();
  renderDrawer();
  renderContent();
  initSortable();
}
function renderTitle(){
  const isHome=state.view.name==='home';
  const isList=!isHome;
  document.body.classList.toggle('home-view',isHome);
  if(isList){
    $('#backBtn').hidden=false;
    $('#hamburger').hidden=true;
    $('#appTitle').textContent = state.view.group;
  }else{
    $('#backBtn').hidden=true;
    $('#hamburger').hidden=false;
    $('#appTitle').textContent = state.type==='全部'?'超级清单':state.type;
  }
  $('#homeGroupby').hidden=!isHome;
  $$('#groupBySeg .seg').forEach(s=>s.classList.toggle('on',s.dataset.gb===state.groupBy));
  $('#appbarSortBtn').hidden=!isList;
  $('#appbarSortBtn').classList.toggle('on',state.sortKey!=='默认');
  $('#fabAi').hidden=!hasCloudKey();
}
function renderDrawer(){
  const nav=$('#drawerNav');
  const counts={};
  state.items.forEach(i=>counts[i.type]=(counts[i.type]||0)+(i.done?0:1));
  const totalActive=state.items.filter(i=>!i.done).length;
  let html=`<div class="dnav-title">类型</div>`;
  html+=`<button class="dnav-item dnav-all ${state.type==='全部'?'on':''}" data-t="全部"><span class="dnav-ic"></span>全部<span class="dnav-count">${totalActive}</span></button>`;
  state.types.forEach((t,i)=>{
    const active = state.type===t;
    const col = active ? colorHexToUri(state.theme) : '%235f6368';
    html+=`<button class="dnav-item ${active?'on':''}" data-t="${esc(t)}" data-kind="type" data-idx="${i}">
      <span class="dnav-ic" style="background-image:url('data:image/svg+xml;utf8,<svg xmlns=%22http://www.w3.org/2000/svg%22 viewBox=%220 0 24 24%22 fill=%22${col}%22><circle cx=%2212%22 cy=%2212%22 r=%229%22 fill=%22none%22 stroke=%22currentColor%22 stroke-width=%222%22/></svg>')"></span>${esc(t)}<span class="dnav-count">${(counts[t]||0)}</span></button>`;
  });
  html+=`<button class="dnav-add" id="dnavAdd" data-addtype="1">＋ 新增类型</button>`;
  nav.innerHTML=html;
}
function colorHexToUri(hex){ return '%23'+hex.slice(1) }
function renderContent(){
  const wrap=$('#content'), empty=$('#emptyState');
  if(state.view.name==='home'){ renderHome(wrap,empty); }
  else { renderList(wrap,empty); }
}
function renderHome(wrap,empty){
  const groups=sectionGroups();
  const items=currentItems().length;
  if(!items){ empty.hidden=false; renderEmptyText(); wrap.innerHTML=''; return }
  empty.hidden=true;
  let html='';
  groups.forEach(g=>{
    const undone=g.items.filter(i=>!i.done).slice().sort((a,b)=>(a.order??Infinity)-(b.order??Infinity)||a.created-b.created);
    const preview=undone.slice(0,3);
    html+=`<div class="section">
      <div class="section-head" data-open="${esc(g.key)}">
        <span class="sec-caret" style="background:url('data:image/svg+xml;utf8,<svg xmlns=%22http://www.w3.org/2000/svg%22 viewBox=%220 0 24 24%22 fill=%22%235f6368%22><path d=%22M8.59 16.59 13.17 12 8.59 7.41 10 6l6 6-6 6z/%22/></svg>') center/contain no-repeat"></span>
        <span class="sec-title">${esc(g.key)}</span>
        <span class="sec-count">${g.items.length}</span>
        <span class="sec-right">${undone.length?'未完成 '+undone.length:'全完成'}</span>
      </div>
      <div class="section-card" data-open="${esc(g.key)}">
        ${preview.map(it=>secItemHTML(it)).join('')}
        <div class="sec-more" data-open2="${esc(g.key)}">查看全部 ${g.items.length} 项 <span class="ci-arrow" style="background:url('data:image/svg+xml;utf8,<svg xmlns=%22http://www.w3.org/2000/svg%22 viewBox=%220 0 24 24%22 fill=%22%235f6368%22><path d=%22M8.59 16.59 13.17 12 8.59 7.41 10 6l6 6-6 6z/%22/></svg>') center/contain no-repeat;width:14px;height:14px"></span></div>
      </div>
    </div>`;
  });
  wrap.innerHTML=html;
}
function secItemHTML(it){
  return `<div class="sec-item" data-item="${it.id}">
    <span class="card-check ${it.done?'done':''}" data-done="${it.id}"></span>
    <div class="card-body">
      <div class="card-title ${it.done?'done':''}">${esc(it.title)}</div>
      <div class="card-meta">${secMeta(it)}</div>
    </div>
  </div>`;
}
function secMeta(it){
  let h='';
  if(it.cost) h+=`<span class="cost">¥${money(it.cost)}</span>`;
  if(it.star) h+=`<span class="star">${'★'.repeat(it.star)}</span>`;
  if(it.due) h+=`<span class="tag ${isOverdue(it.due)&&!it.done?'over':''}">${fmtDue(it.due)}</span>`;
  // 在分组内显示被分组的那一维之外的标签
  if(state.groupBy!=='scene' && it.scene) h+=`<span class="tag">${esc(it.scene)}</span>`;
  if(state.groupBy!=='time' && it.time) h+=`<span class="tag">${esc(it.time)}</span>`;
  return h;
}
function renderList(wrap,empty){
  const g=sectionGroups().find(x=>x.key===state.view.group);
  if(!g){ empty.hidden=false; renderEmptyText(); wrap.innerHTML=''; return }
  if(!g.items.length){ empty.hidden=false; renderEmptyText(); wrap.innerHTML=''; return }
  empty.hidden=true;
  const undone=g.items.filter(i=>!i.done);
  let list;
  if(state.sortKey==='默认'){
    list=undone.slice().sort((a,b)=>(a.order??Infinity)-(b.order??Infinity)||a.created-b.created).concat(g.items.filter(i=>i.done));
  } else {
    list=sortItems(undone.concat(g.items.filter(i=>i.done)));
  }
  const draggable = state.sortKey==='默认';
  let html='';
  list.forEach(it=>{
    const drag = draggable&&!it.done ? `<span class="drag-handle" data-drag="${it.id}"></span>` : '';
    html+=`<div class="item-row" data-item="${it.id}">
      <span class="card-check ${it.done?'done':''}" data-done="${it.id}"></span>
      <div class="card-body">
        <div class="card-title ${it.done?'done':''}">${esc(it.title)}</div>
        <div class="card-meta">${fullMeta(it)}</div>
      </div>
      ${drag}
      <span class="chev" style="background:url('data:image/svg+xml;utf8,<svg xmlns=%22http://www.w3.org/2000/svg%22 viewBox=%220 0 24 24%22 fill=%22%235f6368%22><path d=%22M8.59 16.59 13.17 12 8.59 7.41 10 6l6 6-6 6z/%22/></svg>') center/contain no-repeat"></span>
    </div>`;
  });
  wrap.innerHTML=html;
}
function fullMeta(it){
  let h='';
  if(it.type) h+=`<span class="tag type-blue">${esc(it.type)}</span>`;
  if(it.scene&&state.groupBy!=='scene') h+=`<span class="tag">${esc(it.scene)}</span>`;
  if(it.time&&state.groupBy!=='time') h+=`<span class="tag">${esc(it.time)}</span>`;
  if(it.cost) h+=`<span class="cost">¥${money(it.cost)}</span>`;
  if(it.star) h+=`<span class="star">${'★'.repeat(it.star)}</span>`;
  if(it.due) h+=`<span class="tag ${isOverdue(it.due)&&!it.done?'over':''}">${fmtDue(it.due)}</span>`;
  return h;
}
function renderEmptyText(){
  $('#emptyTitle').textContent = state.search?'无搜索结果':(state.view.name==='home'?'暂无事项':'该分组暂无事项');
  $('#emptySub').textContent = state.search?'换个关键词试试':'点击右下角的 + 添加';
}

/* ========== 事件 ========== */
function init(){
  load();
  applyColorMode();
  buildStars();
  render();
  setTimeout(setupNativeBack,300);
  setTimeout(()=>checkUpdate(true),900);
}

/* 抽屉 */
function openDrawer(){ pushLayer(); $('#drawerMask').hidden=false; $('#drawer').hidden=false; }
function closeDrawer(){ $('#drawerMask').hidden=true; $('#drawer').hidden=true; renderDrawer(); if(!backSuppress)syncBack(); }
document.addEventListener('DOMContentLoaded',()=>{
  init();
  window.addEventListener('popstate',()=>{ if(codeBack){ codeBack=false; return } closeTopLayer(); });
  $('#hamburger').addEventListener('click',openDrawer);
  $('#backBtn').addEventListener('click',()=>{ backHome(); syncBack(); });
  $('#drawerClose').addEventListener('click',closeDrawer);
  $('#drawerMask').addEventListener('click',closeDrawer);
  $('#drawerNav').addEventListener('click',e=>{
    if(suppressNavClick){ suppressNavClick=false; return }
    const add=e.target.closest('#dnavAdd');
    if(add){ addType(); return }
    const item=e.target.closest('.dnav-item'); if(!item)return;
    state.type=item.dataset.t; state.view={name:'home'};
    closeDrawer(); render();
  });
  // 抽屉类型项：长按 → 上下文菜单
  $('#drawerNav').addEventListener('touchstart',onNavPress,{passive:false});
  $('#drawerNav').addEventListener('touchend',onNavRelease);
  $('#drawerNav').addEventListener('touchmove',onNavMove);
  $('#groupBySeg').addEventListener('click',e=>{
    const seg=e.target.closest('.seg'); if(!seg)return;
    state.groupBy=seg.dataset.gb; state.view={name:'home'}; save(); render();
  });
  $('#drawerSettings').addEventListener('click',()=>{ closeDrawer(); openSettings(); });
  $('#drawerTheme').addEventListener('click',toggleColorMode);
  $('#drawerInfo').addEventListener('click',()=>{ closeDrawer(); openInfo(); });
  const onSystemColorChange=()=>{ if(state.colorMode==='system')applyColorMode(); };
  if(colorModeQuery.addEventListener)colorModeQuery.addEventListener('change',onSystemColorChange); else colorModeQuery.addListener(onSystemColorChange);

  /* 内容事件（委托） */
  $('#content').addEventListener('click',e=>{
    if(e.target.closest('.drag-handle'))return;
    const done=e.target.closest('[data-done]');
    if(done){ e.stopPropagation(); toggleDone(done.dataset.done); return }
    const item=e.target.closest('[data-item]');
    if(item){ e.stopPropagation(); const it=state.items.find(x=>x.id===item.dataset.item); if(it)openEdit(it); return }
    const open=e.target.closest('[data-open]');
    if(open){ enterGroup(open.dataset.open); return }
    const open2=e.target.closest('[data-open2]');
    if(open2){ enterGroup(open2.dataset.open2); return }
  });

  /* 搜索 */
  $('#searchBtn').addEventListener('click',()=>{ $('.appbar-top').hidden=true; $('#searchbar').hidden=false; $('#searchInput').focus(); });
  $('#searchBackBtn').addEventListener('click',()=>{ $('#searchbar').hidden=true; $('.appbar-top').hidden=false; state.search=''; $('#searchInput').value=''; render(); });
  $('#searchInput').addEventListener('input',e=>{ state.search=e.target.value.trim(); if(state.view.name!=='home')state.view={name:'home'}; render(); });

  /* 新增 / 速记 / 排序 */
  $('#appbarSortBtn').addEventListener('click',openSort);
  $('#fab').addEventListener('click',openAdd);
  $('#fabAi').addEventListener('click',openAi);
});

/* ========== 抽屉类型项：长按管理 ========== */
let navTimer=null, navPressItem=null, suppressNavClick=false, navMoved=false;
function onNavPress(e){
  const item=e.target.closest('.dnav-item'); if(!item)return;
  if(item.classList.contains('dnav-all'))return; // "全部"不可管理
  navPressItem=item; navMoved=false; suppressNavClick=false;
  clearTimeout(navTimer);
  navTimer=setTimeout(()=>{
    navPressItem.classList.add('press-hint');
    suppressNavClick=true;
    destroyTimer();
    openCtxMenu(item); // 打开上下文菜单
  },480);
  // 简单触觉反馈
  if(navigator.vibrate)navigator.vibrate(15);
}
function onNavMove(){ if(navPressItem){ navMoved=true; destroyTimer(); navPressItem.classList.remove('press-hint'); } }
function onNavRelease(){
  destroyTimer();
  if(navPressItem){ navPressItem.classList.remove('press-hint'); navPressItem=null; }
}
function destroyTimer(){ clearTimeout(navTimer); navTimer=null }

/* ========== 上下文菜单 ========== */
let ctxKind=null, ctxIdx=null, ctxName=null;
function openCtxMenu(item){
  pushLayer();
  ctxKind=item.dataset.kind; ctxIdx=+item.dataset.idx; ctxName=item.dataset.t;
  $('#ctxTitle').textContent='「'+ctxName+'」';
  $('#ctxMask').hidden=false; $('#ctxModal').hidden=false;
}
function closeCtx(){ $('#ctxMask').hidden=true; $('#ctxModal').hidden=true; if(!backSuppress)syncBack(); }
function ctxRename(){
  const arr = ctxKind==='type'?state.types:ctxKind==='scene'?state.scenes:state.times;
  const cur=arr[ctxIdx];
  inputDlg('重命名', '输入新名称', cur, (nm)=>{
    if(nm&&nm!==cur){
      const old=cur, name=nm;
      arr[ctxIdx]=name;
      state.items.forEach(it=>{ if(ctxKind==='type'&&it.type===old)it.type=name; });
      if(state.type===old)state.type=name;
      save(); render(); renderSetGroups();
    }
    closeCtx();
  }, closeCtx);
}
function ctxDelete(){ closeCtx(); deleteTag(ctxKind,ctxIdx); }
function deleteTag(kind,idx){
  const arr = kind==='type'?state.types:kind==='scene'?state.scenes:kind==='time'?state.times:null;
  if(!arr)return;
  if(arr.length<=1){ alertDlg('提示','至少保留一项'); return }
  const rem=arr[idx];
  confirmDlg('删除标签', `确定删除「${rem}」？相关事项中的该标签会被清空。`, ()=>{    arr.splice(idx,1);
    state.items.forEach(it=>{
      if(kind==='type'&&it.type===rem)it.type=state.types[0];
      else if(kind==='scene'&&it.scene===rem)it.scene='';
      else if(kind==='time'&&it.time===rem)it.time='';
    });
    if(state.type===rem)state.type='全部';
    save(); render(); renderSetGroups();
  }, '删除','delete');
}
function addType(){
  inputDlg('新增类型', '输入新类型名称', '', (nm)=>{
    if(nm){
      if(state.types.includes(nm)){ alertDlg('提示','该类型已存在'); return }
      state.types.push(nm); save(); render(); renderDrawer();
    }
  });
}

function enterGroup(key){ pushLayer(); state.view={name:'list',group:key}; state.sortKey='默认'; render(); }
function toggleDone(id){ const it=state.items.find(x=>x.id===id); if(!it)return; it.done=!it.done; save(); render(); }

/* ========== 添加/编辑弹窗 ========== */
let editId=null, editStar=0, modalOpen=false;
function buildStars(){ const s=$('#fStars'); for(let i=1;i<=5;i++){const b=document.createElement('button');b.type='button';b.className='star-b';b.dataset.v=i;b.textContent='★';s.appendChild(b);} }
function segHTML(kind){ const arr=kind==='type'?state.types:kind==='scene'?state.scenes:state.times; return arr.map(v=>`<button class="seg-chip" data-k="${kind}" data-v="${esc(v)}">${esc(v)}</button>`).join('')+`<button class="seg-chip mini" data-add="${kind}">+</button>`; }
function renderSuggest(sug){
  const box=$('#aiSuggestBox');
  if(!sug||(!sug.type&&!sug.scene&&!sug.time)){ box.hidden=true; box.innerHTML=''; return }
  box.hidden=false;
  const kindName={type:'类型',scene:'场景',time:'时间'};
  const add=(k,name)=>{ if(name) box.insertAdjacentHTML('beforeend',`<label class="ai-sug"><input type="checkbox" data-kind="${k}" value="${esc(name)}" checked>建议新建${kindName[k]}「${esc(name)}」</label>`); };
  box.innerHTML='';
  add('type',sug.type); add('scene',sug.scene); add('time',sug.time);
}
function openAdd(pref){
  editId=null; editStar=(pref&&pref.star)||0; modalOpen=true;
  $('#modalTitle').textContent='新建事项';
  $('#fTitle').value=(pref&&pref.title)||''; $('#fNote').value=(pref&&pref.note)||''; $('#fCost').value=(pref&&pref.cost!=null)?pref.cost:''; $('#fDue').value=(pref&&pref.due)||'';
  $('#fTypeSeg').innerHTML=segHTML('type'); $('#fSceneSeg').innerHTML=segHTML('scene'); $('#fTimeSeg').innerHTML=segHTML('time');
  $$('#fTypeSeg .seg-chip, #fSceneSeg .seg-chip, #fTimeSeg .seg-chip').forEach(c=>c.classList.remove('on'));
  // 预选当前类型
  if(state.type!=='全部'){ const tc=$('#fTypeSeg .seg-chip[data-v="'+state.type+'"]'); if(tc)tc.classList.add('on'); }
  // 预选当前所在分组
  if(state.view.name==='list' && state.view.group && state.view.group!=='未分组'){
    const kind=state.groupBy==='scene'?'Scene':'Time';
    const sel='#f'+kind+'Seg .seg-chip[data-v="'+esc(state.view.group)+'"]';
    const el=$(sel); if(el)el.classList.add('on');
  }
  // AI 预填覆盖默认预选
  if(pref){
    if(pref.type) setSeg('type',pref.type);
    if(pref.scene) setSeg('scene',pref.scene);
    if(pref.time) setSeg('time',pref.time);
  }
  renderSuggest(pref?pref.suggest:null);
  $$('.star-b').forEach((s,i)=>s.classList.toggle('on',i<editStar));
  $('#modalDelete').hidden=true;
  showModal();
}
function openEdit(it){
  editId=it.id; editStar=it.star||0; modalOpen=true;
  $('#modalTitle').textContent='编辑事项';
  $('#fTitle').value=it.title; $('#fNote').value=it.note||'';
  $('#fCost').value=(it.cost!==null&&it.cost!==undefined)?it.cost:''; $('#fDue').value=it.due||'';
  $('#fTypeSeg').innerHTML=segHTML('type'); $('#fSceneSeg').innerHTML=segHTML('scene'); $('#fTimeSeg').innerHTML=segHTML('time');
  setSeg('type',it.type); setSeg('scene',it.scene||''); setSeg('time',it.time||'');
  $$('.star-b').forEach((s,i)=>s.classList.toggle('on',i<editStar));
  $('#modalDelete').hidden=false;
  showModal();
}
function setSeg(kind,val){ const el=$('#f'+kind.charAt(0).toUpperCase()+kind.slice(1)+'Seg .seg-chip[data-v="'+val+'"]'); if(el)el.classList.add('on'); }
function showModal(){ pushLayer(); $('#modalMask').hidden=false; $('#modal').hidden=false; $('#fTitle').focus(); }
function hideModal(){ $('#modal').hidden=true; $('#modalMask').hidden=true; modalOpen=false; if(!backSuppress)syncBack(); }

function segSel(kind){ const el=document.querySelector('#f'+kind.charAt(0).toUpperCase()+kind.slice(1)+'Seg .seg-chip.on'); return el?el.dataset.v:''; }
function gather(){
  const title=$('#fTitle').value.trim(); if(!title){ $('#fTitle').focus(); return null }
  return { title, note:$('#fNote').value.trim(), type:segSel('type')||state.types[0], scene:segSel('scene'), time:segSel('time'),
    cost:isNaN(parseFloat($('#fCost').value))?null:parseFloat($('#fCost').value), due:$('#fDue').value||'', star:editStar };
}
function saveForm(){
  const g=gather(); if(!g)return;
  $$('#aiSuggestBox input:checked').forEach(cb=>{
    const kind=cb.dataset.kind, name=cb.value;
    addTagSilent(kind,name);
    if(kind==='type')g.type=name; else if(kind==='scene')g.scene=name; else g.time=name;
  });
  if(editId){ const it=state.items.find(x=>x.id===editId); if(it)Object.assign(it,g); }
  else state.items.push(Object.assign({id:uid(),done:false,created:Date.now()},g));
  save(); render(); hideModal();
}

/* ========== 排序弹窗 ========== */
function openSort(){
  pushLayer();
  const opts=[['默认','默认'],['花费','花费'],['重要','重要'],['日期','截止日期'],['创建','创建时间']];
  $('#sortOptions').innerHTML=opts.map(o=>`<label><input type="radio" name="sort" value="${o[0]}" ${state.sortKey===o[0]?'checked':''}><span>${o[1]}</span></label>`).join('');
  $('#sortAsc').checked=state.sortAsc; $('#sortAsc').disabled=state.sortKey==='默认';
  $('#sortMask').hidden=false; $('#sortModal').hidden=false;
}
function closeSort(){ $('#sortMask').hidden=true; $('#sortModal').hidden=true; if(!backSuppress)syncBack(); }
function openSettings(){
  pushLayer();
  renderPalette();
  renderSetGroups(); renderAiCfg(); $('#setMask').hidden=false; $('#setModal').hidden=false;
}
function closeSettings(){ $('#setMask').hidden=true; $('#setModal').hidden=true; if(!backSuppress)syncBack(); }

/* ========== 软件信息 ========== */
const APP_VERSION='v1.7.1-beta.3';
const REPO_URL='https://github.com/PaidaxingTuT/SuperTodo';
const REPO_API='https://api.github.com/repos/PaidaxingTuT/SuperTodo';
function openInfo(){ pushLayer(); $('#infoUpdate').textContent='点击检查'; $('#infoVer').textContent='SuperTodo · 版本 '+APP_VERSION.replace(/^v/,''); $('#infoMask').hidden=false; $('#infoModal').hidden=false; }
function closeInfo(){ $('#infoMask').hidden=true; $('#infoModal').hidden=true; if(!backSuppress)syncBack(); }
function verGt(a,b){
  const pa=String(a||'').replace(/^v/,'').split('.').map(Number);
  const pb=String(b||'').replace(/^v/,'').split('.').map(Number);
  for(let i=0;i<Math.max(pa.length,pb.length);i++){
    const x=pa[i]||0, y=pb[i]||0;
    if(x>y)return true; if(x<y)return false;
  }
  return false;
}
async function checkUpdate(silent){
  const el=$('#infoUpdate');
  if(el) el.textContent='正在检查…';
  try{
    const res=await fetch(REPO_API+'/releases/latest');
    if(res.status===404){ if(el)el.textContent='暂无已发布版本'; return }
    if(!res.ok) throw new Error('net');
    const d=await res.json();
    const latest=d.tag_name;
    if(verGt(latest,APP_VERSION)){
      if(el) el.textContent='发现新版本 '+latest;
      const asset=d.assets&&d.assets[0];
      confirmDlg('发现新版本', '有新版本 '+latest.replace(/^v/,'')+'，是否立即下载更新？', ()=>{
        if(asset&&asset.browser_download_url){ downloadFile(asset.browser_download_url, asset.name); }
        else window.open(REPO_URL+'/releases','_blank');
      }, '下载');
    }else{
      if(el) el.textContent='已是最新版本';
    }
  }catch(e){
    if(el) el.textContent='检查失败';
    if(!silent) alertDlg('检查失败','无法连接更新服务器，请稍后再试');
  }
}
function downloadFile(url,name){
  const isNative=!!(window.Capacitor&&window.Capacitor.isNativePlatform&&window.Capacitor.isNativePlatform());
  const target=(isNative?'https://ghfast.top/':'')+url;
  try{
    const a=document.createElement('a');
    a.href=target; a.download=name||'';
    a.rel='noopener';
    document.body.appendChild(a); a.click(); a.remove();
  }catch(e){ window.open(target,'_blank'); }
}

/* ========== 设置：配色 ========== */
function renderPalette(){
  const el=$('#palette');
  el.innerHTML=PALETTE.map(c=>`<button class="pal-sw ${state.theme.toLowerCase()===c?'on':''}" style="background:${c}" data-c="${c}"></button>`).join('');
  $('#customColor').value=state.theme;
}
function setTheme(hex){
  state.theme=hex; applyTheme(hex); save(); renderPalette();
}

/* ========== 设置：自定义标签 ========== */
function renderSetGroups(){
  const g=(id,arr,kind)=>{ const el=$(id); el.innerHTML=arr.map((v,i)=>`<span class="tag-chip" data-ren="${kind}:${i}">${esc(v)}<span class="x" data-del="${kind}:${i}">✕</span></span>`).join('')+`<button class="add-chip" data-add="${kind}">＋ 添加</button>`; };
  g('#setTypes',state.types,'type'); g('#setScenes',state.scenes,'scene'); g('#setTimes',state.times,'time');
}
function addTag(kind){
  const arr=kind==='type'?state.types:kind==='scene'?state.scenes:state.times;
  inputDlg('添加标签', '输入新标签名称', '', (name)=>{
    if(!name) return;
    if(arr.includes(name)){ alertDlg('提示','该标签已存在'); return }
    arr.push(name); save(); renderSetGroups(); render();
    // 若事项表单开着，刷新对应标签组并选中新标签
    if(modalOpen){
      const cap=kind.charAt(0).toUpperCase()+kind.slice(1);
      const el=$('#f'+cap+'Seg');
      if(el){
        el.innerHTML=segHTML(kind);
        const chip=el.querySelector('.seg-chip[data-v="'+name+'"]');
        if(chip)chip.classList.add('on');
      }
    }
  });
}
function renameTag(kind,idx){
  const arr=kind==='type'?state.types:kind==='scene'?state.scenes:state.times;
  const old=arr[idx];
  inputDlg('重命名', '输入新名称', old, (nm)=>{
    if(nm&&nm!==old){
      if(arr.includes(nm)){ alertDlg('提示','该名称已存在'); return }
      arr[idx]=nm;
      if(kind==='type'){ state.items.forEach(it=>{if(it.type===old)it.type=nm}); if(state.type===old)state.type=nm; }
      else if(kind==='scene'){ state.items.forEach(it=>{if(it.scene===old)it.scene=nm}); }
      else if(kind==='time'){ state.items.forEach(it=>{if(it.time===old)it.time=nm}); }
      save(); render(); renderSetGroups();
    }
  });
}

/* ========== AI：一句话速记 + 智能整理（云端） ========== */
/* ===== 云端解析（OpenAI 兼容） ===== */
function hasCloudKey(){ return !!(state.ai&&state.ai.enabled&&state.ai.base&&state.ai.key) }
function aiPrompt(){
  const now=new Date();
  const today=now.getFullYear()+'-'+String(now.getMonth()+1).padStart(2,'0')+'-'+String(now.getDate()).padStart(2,'0');
  return '你是清单应用的语义解析器。必须完整分析用户输入中的每个信息，只输出 JSON，不要解释。'+
  '当前日期是 '+today+'（用户本地日期），所有相对时间都以此计算。'+
  '现有类型：'+JSON.stringify(state.types)+'，现有场景：'+JSON.stringify(state.scenes)+'，现有时间：'+JSON.stringify(state.times)+'。'+
  '输出格式：{"title":"简短事项主体","type":"类型或空","scene":"场景或空","time":"时间或空","cost":数字(元)或null,"due":"YYYY-MM-DD或空","star":1-5或0,"suggest":{"type":"建议新建类型或空","scene":"建议新建场景或空","time":"建议新建时间或空"}}。'+
  '规则：1. title 只保留核心对象或任务，去掉时间、金额、地点、重要程度和“买/购买”等可由 type 表达的修饰；不要照抄整句。'+
  '2. 根据语义推断所有字段，例如“买/购入”对应购物类型；不得漏掉可以明确推断的信息。'+
  '3. 识别今天、明天、周末、月底、年底前、明年等相对时间并换算 due；“年底前/今年底/今年内”表示今年且 due 为当年 12-31。'+
  '4. type/scene/time 必须从现有列表精确选择；没有合适项时该字段留空，并在 suggest 中给出简短建议。'+
  '5. cost 只提取明确金额；star 按明确的重要程度映射到 1-5，未提及则为 0；不要臆造信息。'+
  '示例：输入“年底前买ps5”，若现有列表包含购物和今年，则 title="ps5"、type="购物"、time="今年"、due="'+now.getFullYear()+'-12-31"，其他未提及字段保持空或 null。';
}
async function parseWithCloud(text,signal){
  try{
    const base=state.ai.base.replace(/\/+$/,'');
    const res=await fetch(base+'/chat/completions',{
      method:'POST',
      headers:{'Content-Type':'application/json','Authorization':'Bearer '+state.ai.key},
      signal,
      body:JSON.stringify({
        model:state.ai.model||'gpt-4o-mini',
        messages:[{role:'system',content:aiPrompt()},{role:'user',content:text}],
        temperature:0,
        response_format:{type:'json_object'}
      })
    });
    if(!res.ok) return null;
    const data=await res.json();
    const raw=data.choices&&data.choices[0]&&data.choices[0].message&&data.choices[0].message.content;
    if(!raw) return null;
    return JSON.parse(raw);
  }catch(e){ return null }
}
function normTag(v,dim){ if(!v)return {v:'',s:''}; const list=dim==='types'?state.types:dim==='scenes'?state.scenes:state.times; if(list.includes(v))return {v,s:''}; return {v:'',s:v}; }
function normalizeCloud(r){
  const t=normTag(r.type,'types'), s=normTag(r.scene,'scenes'), m=normTag(r.time,'times');
  const cost=isNaN(parseFloat(r.cost))?null:parseFloat(r.cost);
  let due=''; if(typeof r.due==='string'&&/^\d{4}-\d{2}-\d{2}$/.test(r.due)) due=r.due;
  let star=0; if(+r.star>=1&&+r.star<=5) star=Math.round(+r.star);
  return { title:typeof r.title==='string'?r.title.trim():'', type:t.v, scene:s.v, time:m.v,
    cost, due, star, suggest:{type:t.s,scene:s.s,time:m.s} };
}
/* 主入口：开启 AI 增强且云端可用时才解析，否则返回 null */
async function parseAI(text,signal){
  if(!hasCloudKey()) return null;
  const cloud=await parseWithCloud(text,signal);
  if(cloud&&typeof cloud==='object') return normalizeCloud(cloud);
  return null;
}
function addTagSilent(kind,name){
  const arr=kind==='type'?state.types:kind==='scene'?state.scenes:state.times;
  if(!name||arr.includes(name))return;
  arr.push(name); save();
}
/* ===== AI 速记 UI ===== */
let aiRequestId=0, aiAbort=null;
function openAi(){
  aiRequestId++;
  if(aiAbort)aiAbort.abort();
  aiAbort=null;
  pushLayer();
  $('#aiInput').value=''; $('#aiLoading').hidden=true; $('#aiGo').disabled=false;
  $('#aiStatus').textContent = 'AI 增强已开启'+(state.ai.model?(' · '+state.ai.model):'');
  $('#aiMask').hidden=false; $('#aiModal').hidden=false; $('#aiInput').focus();
}
function closeAi(cancelRequest=true){
  if(cancelRequest){
    aiRequestId++;
    if(aiAbort)aiAbort.abort();
    aiAbort=null;
  }
  $('#aiMask').hidden=true; $('#aiModal').hidden=true;
  if(!backSuppress)syncBack();
}
async function runAi(){
  const text=$('#aiInput').value.trim();
  if(!text){ $('#aiInput').focus(); return }
  const requestId=++aiRequestId;
  if(aiAbort)aiAbort.abort();
  const controller=new AbortController();
  aiAbort=controller;
  $('#aiGo').disabled=true; $('#aiLoading').hidden=false; $('#aiStatus').textContent='正在解析…';
  const r=await parseAI(text,controller.signal);
  if(requestId!==aiRequestId||controller.signal.aborted||$('#aiModal').hidden)return;
  aiAbort=null;
  $('#aiGo').disabled=false; $('#aiLoading').hidden=true;
  if(!r){ $('#aiStatus').textContent='解析失败：请检查 AI 配置与网络后重试'; return }
  closeAi(false);
  openAdd(r);
}
/* ===== 智能整理（批量补标签） ===== */
let tidyRows=[];
async function openTidy(){
  if(!hasCloudKey()){ alertDlg('智能整理','需要先开启 AI 增强（设置 → AI · 云端增强）'); return }
  const cands=state.items.filter(it=>!it.done&&(!it.scene||!it.time));
  if(!cands.length){ alertDlg('智能整理','没有需要整理的事项'); return }
  $('#tidyMask').hidden=false; $('#tidyModal').hidden=false;
  pushLayer();
  $('#tidyLoading').hidden=false; $('#tidyList').innerHTML='';
  const rows=[];
  for(const it of cands){
    const r=await parseAI(it.title);
    rows.push({id:it.id,title:it.title,sugScene:r&&r.scene?r.scene:'',sugTime:r&&r.time?r.time:''});
  }
  tidyRows=rows;
  renderTidy();
  $('#tidyLoading').hidden=true;
}
function tidyOpts(arr){
  return ['<option value="">不设置</option>'].concat(arr.map(v=>`<option value="${esc(v)}">${esc(v)}</option>`)).join('');
}
function renderTidy(){
  const el=$('#tidyList');
  el.innerHTML=tidyRows.map(r=>`
    <div class="tidy-item" data-id="${r.id}">
      <div class="tidy-title">${esc(r.title)}</div>
      <div class="tidy-sels">
        <label>场景<select data-f="scene">${tidyOpts(state.scenes)}</select></label>
        <label>时间<select data-f="time">${tidyOpts(state.times)}</select></label>
      </div>
    </div>`).join('');
  $$('#tidyList .tidy-item').forEach(item=>{
    const r=tidyRows.find(x=>x.id===item.dataset.id);
    if(r&&r.sugScene){ const sel=item.querySelector('select[data-f=scene]'); if(sel)sel.value=r.sugScene; }
    if(r&&r.sugTime){ const sel=item.querySelector('select[data-f=time]'); if(sel)sel.value=r.sugTime; }
  });
}
function tidyApply(){
  let n=0;
  $$('#tidyList .tidy-item').forEach(item=>{
    const it=state.items.find(x=>x.id===item.dataset.id); if(!it)return;
    const sc=item.querySelector('select[data-f=scene]').value;
    const tm=item.querySelector('select[data-f=time]').value;
    if(sc&&sc!==it.scene){ it.scene=sc; n++; }
    if(tm&&tm!==it.time){ it.time=tm; n++; }
  });
  if(n){ save(); render(); }
  closeTidy();
  alertDlg('智能整理', n?('已整理 '+n+' 处标签'):'未做更改');
}
function closeTidy(){ $('#tidyMask').hidden=true; $('#tidyModal').hidden=true; if(!backSuppress)syncBack(); }
/* ===== AI 云端配置 ===== */
function renderAiCfg(){
  if(!state.ai)state.ai={enabled:false,base:'',key:'',model:''};
  $('#aiEnabled').checked=!!state.ai.enabled;
  $('#aiBase').value=state.ai.base||''; $('#aiKey').value=state.ai.key||''; $('#aiModel').value=state.ai.model||'';
  const dis=!state.ai.enabled;
  $('#aiBase').disabled=dis; $('#aiKey').disabled=dis; $('#aiModel').disabled=dis;
}
function saveAiField(field){
  if(!state.ai)state.ai={enabled:false,base:'',key:'',model:''};
  state.ai[field]=$('#ai'+field[0].toUpperCase()+field.slice(1)).value.trim();
  save(); render();
}

/* ========== 拖拽排序（SortableJS，仅默认排序下可用） ========== */
let sortable=null;
function initSortable(){
  if(sortable){ sortable.destroy(); sortable=null; }
  if(state.view.name!=='list'||state.sortKey!=='默认'||typeof Sortable==='undefined') return;
  sortable=new Sortable($('#content'),{
    handle:'.drag-handle',
    animation:160,
    easing:'cubic-bezier(.2,.7,.2,1)',
    ghostClass:'sortable-ghost',
    onEnd(){
      $$('#content .item-row').forEach((r,i)=>{ const it=state.items.find(x=>x.id===r.dataset.item); if(it&&!it.done)it.order=i; });
      save();
    }
  });
}

/* ========== 导出/导入/清空 ========== */
function exportData(){
  const blob=new Blob([JSON.stringify({items:state.items,types:state.types,scenes:state.scenes,times:state.times,theme:state.theme,colorMode:state.colorMode,ai:state.ai},null,2)],{type:'application/json'});
  const a=document.createElement('a'); a.href=URL.createObjectURL(blob);
  const d=new Date(), p=`${d.getFullYear()}${String(d.getMonth()+1).padStart(2,'0')}${String(d.getDate()).padStart(2,'0')}`;
  a.download=`超级清单备份_${p}.json`; a.click(); URL.revokeObjectURL(a.href);
}
function importData(e){
  const f=e.target.files[0]; if(!f)return;
  const r=new FileReader(); r.onload=()=>{ try{ const d=JSON.parse(r.result); state.items=d.items||[]; if(Array.isArray(d.types)&&d.types.length)state.types=d.types; if(Array.isArray(d.scenes)&&d.scenes.length)state.scenes=d.scenes; if(Array.isArray(d.times)&&d.times.length)state.times=d.times; if(d.theme)state.theme=d.theme; if(['system','light','dark'].includes(d.colorMode))state.colorMode=d.colorMode; if(d.ai)state.ai=Object.assign({enabled:false,base:'',key:'',model:''},d.ai); save(); applyColorMode(); render(); renderSetGroups(); renderPalette(); alertDlg('导入成功','数据已导入'); }catch(er){ alertDlg('导入失败','文件格式错误') } }; r.readAsText(f); e.target.value='';
}
function clearAll(){ confirmDlg('清空数据','确定清空全部数据？此操作不可撤销。',()=>{ state.items=[]; save(); render(); },'清空','delete'); }

/* ========== 弹窗事件（一次性绑定） ========== */
document.addEventListener('DOMContentLoaded',()=>{
  $('#modalClose').addEventListener('click',hideModal);
  $('#modalCancel').addEventListener('click',hideModal);
  $('#modalMask').addEventListener('click',hideModal);
  $('#modalSave').addEventListener('click',saveForm);
  $('#modalDelete').addEventListener('click',()=>{ if(!editId)return; state.items=state.items.filter(x=>x.id!==editId); save(); render(); hideModal(); });
  $('#modal').addEventListener('click',e=>{
    const add=e.target.closest('.seg-chip.mini');
    if(add){ addTag(add.dataset.add); return }
    const seg=e.target.closest('#fTypeSeg .seg-chip, #fSceneSeg .seg-chip, #fTimeSeg .seg-chip');
    if(seg){ $$('#f'+seg.dataset.k.charAt(0).toUpperCase()+seg.dataset.k.slice(1)+'Seg .seg-chip').forEach(c=>{ if(!c.classList.contains('mini'))c.classList.toggle('on',c===seg); }); return }
  });
  $('#fTitle').addEventListener('keydown',e=>{ if(e.key==='Enter'){e.preventDefault();$('#fNote').focus()} });
  $('#fNote').addEventListener('keydown',e=>{ if(e.key==='Enter'){e.preventDefault();saveForm()} });
  $('#fStars').addEventListener('click',e=>{ const st=e.target.closest('.star-b'); if(!st)return; editStar=parseInt(st.dataset.v); $$('.star-b').forEach((s,i)=>s.classList.toggle('on',i<editStar)); });
  $('#fCost').addEventListener('keydown',e=>{ if(e.key==='Enter'){e.preventDefault();saveForm()} });

  $('#sortClose').addEventListener('click',closeSort);
  $('#sortMask').addEventListener('click',closeSort);
  $('#sortOptions').addEventListener('change',e=>{ state.sortKey=e.target.value; $('#sortAsc').disabled=state.sortKey==='默认'; save(); closeSort(); render(); });
  $('#sortAsc').addEventListener('change',e=>{ state.sortAsc=e.target.checked; save(); render(); });

  $('#setClose').addEventListener('click',closeSettings);
  $('#setMask').addEventListener('click',closeSettings);
  $('#setModal').addEventListener('click',e=>{
    const x=e.target.closest('.x'); const ad=e.target.closest('.add-chip'); const ren=e.target.closest('.tag-chip[data-ren]');
    if(x){ e.stopPropagation(); const [kind,idx]=x.dataset.del.split(':'); deleteTag(kind,+idx); return }
    if(ren){ e.stopPropagation(); const [kind,idx]=ren.dataset.ren.split(':'); renameTag(kind,+idx); return }
    if(ad){ e.stopPropagation(); addTag(ad.dataset.add); return }
  });
  $('#palette').addEventListener('click',e=>{ const sw=e.target.closest('.pal-sw'); if(sw)setTheme(sw.dataset.c); });
  $('#customColorBtn').addEventListener('click',()=>$('#customColor').click());
  $('#customColor').addEventListener('input',e=>{ if(e.target.value)setTheme(e.target.value); });

  /* 上下文菜单 */
  $('#ctxClose').addEventListener('click',closeCtx);
  $('#ctxMask').addEventListener('click',closeCtx);
  $('#ctxRename').addEventListener('click',ctxRename);
  $('#ctxDelete').addEventListener('click',ctxDelete);

  /* 软件信息 */
  $('#infoClose').addEventListener('click',closeInfo);
  $('#infoMask').addEventListener('click',closeInfo);
  $('#infoUpdateRow').addEventListener('click',()=>checkUpdate());
  $('#infoRepo').addEventListener('click',()=>window.open(REPO_URL,'_blank'));

  /* 通用对话框 */
  $('#dlgOk').addEventListener('click',()=>{
    const cb=dlgCb;
    const val=dlgType==='input'?$('#dlgInput').value.trim():null;
    const hasCb=dlgType==='confirm'||dlgType==='input';
    dlgClose();
    if(hasCb&&cb) cb(val);
  });
  $('#dlgCancel').addEventListener('click',()=>{ const c=dlgOnCancel; dlgClose(); if(c)c(); });
  $('#dlgMask').addEventListener('click',()=>{ const c=dlgOnCancel; dlgClose(); if(c)c(); });

  $('#tidyBtn').addEventListener('click',()=>{ closeSettings(); openTidy(); });
  $('#exportBtn').addEventListener('click',exportData);
  $('#importBtn').addEventListener('click',()=>$('#importFile').click());
  $('#importFile').addEventListener('change',importData);
  $('#clearBtn').addEventListener('click',clearAll);

  /* AI 一句话速记 */
  $('#aiClose').addEventListener('click',closeAi);
  $('#aiCancel').addEventListener('click',closeAi);
  $('#aiMask').addEventListener('click',closeAi);
  $('#aiGo').addEventListener('click',runAi);
  $('#aiInput').addEventListener('keydown',e=>{ if(e.key==='Enter'){e.preventDefault();runAi()} });

  /* 智能整理 */
  $('#tidyClose').addEventListener('click',closeTidy);
  $('#tidyCancel').addEventListener('click',closeTidy);
  $('#tidyMask').addEventListener('click',closeTidy);
  $('#tidyApply').addEventListener('click',tidyApply);

  /* AI 云端配置（change 时即时保存） */
  $('#aiEnabled').addEventListener('change',e=>{
    state.ai.enabled=e.target.checked;
    save(); renderAiCfg(); render();
  });
  $('#setModal').addEventListener('change',e=>{
    const el=e.target.closest('[data-aifield]');
    if(el)saveAiField(el.dataset.aifield);
  });

  /* 桌面小部件 */
  const btn4x2=$('#pinWidget4x2Btn'), btn4x4=$('#pinWidget4x4Btn'), btnHelp=$('#widgetHelpBtn');
  if(btn4x2) btn4x2.addEventListener('click',()=>pinWidget('4x2'));
  if(btn4x4) btn4x4.addEventListener('click',()=>pinWidget('4x4'));
  if(btnHelp) btnHelp.addEventListener('click',showWidgetHelp);
});

function pinWidget(size){
  const name=size==='4x4'?'4×4':'4×2';
  if(window.AndroidWidgetBridge&&window.AndroidWidgetBridge.requestPinWidget){
    const ok=window.AndroidWidgetBridge.requestPinWidget(size);
    if(ok){
      alertDlg('已发送添加请求', '已向桌面发起添加 ' + name + ' 小部件请求。\n\n如您的手机（如 OPPO ColorOS / vivo OriginOS / 小米澎湃OS）未弹出确认框，说明系统拦截了应用自动添加，请直接在手机桌面「双指捏合 -> 小部件/插件/原子组件 -> 超级清单」拖动添加。');
    }else{
      confirmDlg('添加小部件', '当前桌面启动器拦截了直接添加请求。\n\n请在手机桌面「双指捏合」或长按空白处，点击「小部件/插件/原子组件」，找到「超级清单」选择 ' + name + ' 拖拽到桌面即可。', null, '知道了', 'info');
    }
  }else{
    alertDlg('小部件说明', '桌面小部件需在 Android APK 安装包内使用。\n\n已适配小米澎湃OS、OPPO ColorOS、vivo OriginOS 及各大安卓桌面：\n• 支持 4×2 与 4×4 标准尺寸\n• 桌面直接打勾完成状态\n• 按场景/时间筛选与自定义排序\n• 日夜间模式自动跟随');
  }
}

function showWidgetHelp(){
  dlgShow({
    title:'桌面小部件使用指南',
    msg:'【如何添加到桌面】\n• 小米 / Redmi（澎湃OS / MIUI）：桌面双指捏合 ->「小部件」-> 找到「超级清单」拖至桌面\n• OPPO / 一加 / realme（ColorOS）：桌面双指捏合或长按桌面空白处 ->「插件」或「卡片」-> 找到「超级清单」拖至桌面\n• vivo / iQOO（OriginOS）：桌面双指捏合或桌面滑出「原子组件库」-> 找到「超级清单」拖至桌面\n• 华为 / 荣耀 / 三星 / 原生 Android：桌面双指捏合 ->「微件/窗口小部件」-> 找到「超级清单」\n\n【功能特性】\n1. 切换分类与自定义排序：长按小部件点击「编辑小部件」，或点击小部件右上角 ⚙ 设置图标，可按场景/时间筛选指定分类并调整排序。\n2. 桌面快速打勾：直接点击列表左侧圆圈，即可在桌面标记完成/未完成，零延迟更新且无需打开 App。\n3. 日夜间自适应：深度契合澎湃OS、ColorOS、OriginOS 原生深色/浅色模式规范。',
    type:'alert',
    okText:'知道了'
  });
}
