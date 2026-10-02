'use strict';
// Morphicons owns rendering, spring interpolation and interruption. No icon font or CDN.
Morphicons.defineMorphIcon();
const Icons = (() => {
  const paths = {
    menu:'M4 6H20 M4 12H20 M4 18H20',
    close:'M6 6L18 18 M18 6L6 18',
    back:'M14 5L7 12L14 19 M7 12H21',
    search:'M17 10A7 7 0 1 1 3 10A7 7 0 1 1 17 10 M15 15L21 21',
    sort:'M4 6H20 M4 12H15 M4 18H10',
    grid:'M3 3H10V10H3Z M14 3H21V10H14Z M3 14H10V21H3Z M14 14H21V21H14Z',
    list:'M5 3H19V21H5Z M8 8H16 M8 12H16 M8 16H13',
    circle:'M21 12A9 9 0 1 1 3 12A9 9 0 1 1 21 12',
    down:'M6 9L12 15L18 9', up:'M6 15L12 9L18 15', right:'M9 6L15 12L9 18',
    check:'M5 12L10 17L19 7', blank:'M11.99 12L12 12L12.01 12',
    plus:'M12 5V19 M5 12H19',
    calendar:'M4 5H20V21H4Z M8 3V7 M16 3V7 M4 10H20 M8 14H8.1 M12 14H12.1 M16 14H16.1 M8 17H8.1 M12 17H12.1',
    moon:'M20 15A9 9 0 0 1 9 4A9 9 0 1 0 20 15Z',
    sun:'M16 12A4 4 0 1 1 8 12A4 4 0 1 1 16 12 M12 2V4 M12 20V22 M2 12H4 M20 12H22 M5 5L6.5 6.5 M17.5 17.5L19 19 M5 19L6.5 17.5 M17.5 6.5L19 5',
    info:'M21 12A9 9 0 1 1 3 12A9 9 0 1 1 21 12 M12 11V17 M12 7V7.1',
    question:'M21 12A9 9 0 1 1 3 12A9 9 0 1 1 21 12 M9 9A3 3 0 0 1 15 9C15 11 12 11 12 14 M12 17V17.1',
    settings:'M9 3H15L16 6L19 7L22 12L20 15L19 18L15 21H9L8 18L5 17L2 12L4 9L5 6L9 3Z M16 12A4 4 0 1 1 8 12A4 4 0 1 1 16 12',
    edit:'M15 4L20 9 M4 20L5 15L17 3L21 7L9 19Z',
    delete:'M3 6H21 M9 6V3H15V6 M5 6L6 21H18L19 6 M10 10V17 M14 10V17',
    refresh:'M20 7A9 9 0 1 0 21 14 M20 3V8H15',
    history:'M4 7A9 9 0 1 1 3 14 M4 3V8H9 M12 7V12L16 14',
    github:'M9 21V18C5 19 5 16 3 15 M15 21V17C15 16 16 15 18 14C20 12 20 9 18 7V3L14 5H10L6 3V7C4 9 4 12 6 14C8 15 9 16 9 18',
    download:'M12 3V15 M7 10L12 15L17 10 M4 15V21H20V15',
    contrast:'M21 12A9 9 0 1 1 3 12A9 9 0 1 1 21 12 M12 3V21 M12 7H18 M12 12H21 M12 17H18',
    rows:'M3 4H21V10H3Z M3 14H21V20H3Z',
    frame:'M3 3H21V21H3Z M7 7H17V17H7Z',
    text:'M4 7V4H20V7 M12 4V20 M8 20H16',
    drag:'M8 5H8.1 M16 5H16.1 M8 12H8.1 M16 12H16.1 M8 19H8.1 M16 19H16.1',
    sparkle:'M12 3L15 9L21 12L15 15L12 21L9 15L3 12L9 9Z M20 2V6 M18 4H22'
  };
  function html(name, cls=''){
    return `<morph-icon data-icon="${name}" class="${cls}" icon="${paths[name]||paths.info}" size="24" stroke-width="1.8" spring="snappy" reduced-motion="user" aria-hidden="true"></morph-icon>`;
  }
  function set(el,name,animated=true){
    const icon=el?.matches('morph-icon')?el:el?.querySelector('morph-icon');
    if(!icon || icon.dataset.icon===name) return;
    icon.dataset.icon=name;
    if(animated) icon.morphTo(paths[name],'snappy');
    else icon.set(paths[name]);
  }
  const slots = {
    '#hamburger .ic':'menu', '#backBtn .ic':'back', '#searchBtn .ic':'search', '#searchBackBtn .ic':'close', '#appbarSortBtn .ic':'sort',
    '.drawer-logo':'grid', '.drawer-close':'close', '.df-theme':'moon', '.df-info':'info', '.df-ic':'settings',
    '.dnav-all .dnav-ic':'list', '.dnav-trash .dnav-ic':'delete', '.dnav-item[data-kind="type"] .dnav-ic':'circle',
    '.md-close':'close', '.fab-ai':'sparkle', '.fab:not(.fab-ai)':'plus', '.empty-icon':'list', '.trash-empty-ic':'delete',
    '.sec-caret, .ci-arrow, .chev, .sec-arrow, .ctx-action-arrow':'right', '.drag-handle, .dnav-drag':'drag',
    '.swipe-ic-check':'check', '.swipe-ic-trash':'delete', '.subtask-chevron':'down', '.card-check, .task-check, .qw-chk, .pal-sw':'blank'
  };
  function mount(root=document){
    const glyphs=[...(root.matches?.('morph-icon[data-icon]')?[root]:[]),...root.querySelectorAll('morph-icon[data-icon]')];
    glyphs.forEach(icon=>{if(!icon.hasAttribute('icon')) icon.setAttribute('icon',paths[icon.dataset.icon]||paths.info);});
    for(const [selector,name] of Object.entries(slots)){
      const elements=[...(root.matches?.(selector)?[root]:[]),...root.querySelectorAll(selector)];
      elements.forEach(el=>{if(!el.querySelector('morph-icon')) el.insertAdjacentHTML('beforeend',html(name));});
    }
    root.querySelectorAll('.card-check, .task-check, .qw-chk, .pal-sw').forEach(el=>set(el,el.classList.contains('done')||el.classList.contains('on')||el.closest('.task-row.done')?'check':'blank',false));
    root.querySelectorAll('.subtask-check input').forEach(input=>{
      if(!input.nextElementSibling?.matches('morph-icon')) input.insertAdjacentHTML('afterend',html(input.checked?'check':'blank','subtask-tick'));
    });
    root.querySelectorAll('.subtask-list').forEach(details=>set(details.querySelector('.subtask-chevron'),details.open?'up':'down',false));
    root.querySelectorAll('.df-theme').forEach(el=>set(el,document.documentElement.dataset.colorMode==='dark'?'sun':'moon'));
  }
  mount();
  new MutationObserver(records=>{
    records.forEach(record=>{
      if(record.type==='childList') record.addedNodes.forEach(el=>{if(el.nodeType===1 && (el.matches('morph-icon')||!el.closest('morph-icon'))) mount(el);});
      else if(record.target.matches('.card-check, .task-check, .qw-chk, .pal-sw')) set(record.target,record.target.classList.contains('done')||record.target.classList.contains('on')||record.target.closest('.task-row.done')?'check':'blank');
      else if(record.target===document.documentElement) document.querySelectorAll('.df-theme').forEach(el=>set(el,record.target.dataset.colorMode==='dark'?'sun':'moon'));
    });
  }).observe(document.documentElement,{childList:true,subtree:true,attributes:true,attributeFilter:['class','data-color-mode']});
  document.addEventListener('change',event=>{
    if(event.target.matches('.subtask-check input')) set(event.target.nextElementSibling,event.target.checked?'check':'blank');
  },true);
  return {html,set,mount};
})();
