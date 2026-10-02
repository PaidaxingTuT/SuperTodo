'use strict';
// Local GSAP 3.15.0 + Motion 13.5.0. Keep animations outside saved task data.
const UiMotion = (() => {
  const reduced = matchMedia('(prefers-reduced-motion: reduce)');
  const motions = new WeakMap();
  const exits = new WeakMap();
  let rows = new Map();
  const budgets = new Map();
  // Measure the title and metadata only; expanding the checklist never changes this anchor.
  const anchorHeader=header=>header.parentElement.style.setProperty('--subtask-anchor',header.getBoundingClientRect().height/2+'px');
  const headers=new ResizeObserver(entries=>entries.forEach(entry=>anchorHeader(entry.target)));
  const enabled = () => !reduced.matches && typeof gsap !== 'undefined' && typeof Motion !== 'undefined';
  function animate(el, frames, options={}){
    if(!el || !enabled()) return;
    motions.get(el)?.stop();
    const control=Motion.animate(el,frames,{duration:.22,ease:[.2,.8,.2,1],...options});
    motions.set(el,control);
    return control;
  }
  function check(el,done){
    if(el.matches('.subtask-check input')) el=el.parentElement;
    animate(el,{scale:done?[1,.84,1.15,1]:[1,.9,1]},{duration:.28});
  }
  function progress(summary,percent,done){
    const ring=summary.querySelector('.subtask-ring');
    if(!ring) return;
    ring.querySelector('span').textContent=done;
    if(enabled()) gsap.to(ring,{'--progress':percent,duration:.42,ease:'power2.out',overwrite:true});
    else ring.style.setProperty('--progress',percent);
  }
  function width(el,percent){
    if(!el) return;
    el.dataset.progress=String(percent);
    if(enabled()) gsap.to(el,{width:percent+'%',duration:.28,ease:'power2.out',overwrite:true});
    else el.style.width=percent+'%';
  }
  function toggleDetails(details){
    const open=!(details.dataset.expanded ? details.dataset.expanded==='true' : details.open);
    details.dataset.expanded=String(open);
    const steps=details.querySelector('.subtask-steps');
    Icons.set(details.querySelector('.subtask-chevron'),open?'up':'down');
    if(!steps || !enabled()){ details.open=open; return; }
    gsap.killTweensOf(steps);
    if(open && !details.open){
      details.open=true;
      gsap.set(steps,{height:0,opacity:0,overflow:'hidden',marginTop:0,paddingTop:0,borderTopWidth:0});
    }
    steps.style.overflow='hidden';
    gsap.to(steps,{
      height:open?'auto':0,opacity:open?1:0,marginTop:open?6:0,paddingTop:open?4:0,borderTopWidth:open?1:0,
      duration:.25,ease:'power2.inOut',overwrite:true,
      onComplete(){
        details.open=open;
        gsap.set(steps,{clearProps:'height,opacity,overflow,marginTop,paddingTop,borderTopWidth'});
      }
    });
  }
  function beforeRender(){
    headers.disconnect();
    document.querySelectorAll('#content .csc-progress-bar').forEach(bar=>{
      budgets.set(bar.closest('.cost-summary-card').dataset.costKey,parseFloat(bar.style.width)||0);
      if(typeof gsap!=='undefined') gsap.killTweensOf(bar);
    });
    rows=new Map();
    document.querySelectorAll('#content [data-item]').forEach(row=>{
      const checkEl=row.querySelector('[data-done]');
      const key=row.dataset.item+'|'+(checkEl?.dataset.doneKey||'');
      rows.set(key,{open:row.querySelector('details')?.dataset.expanded==='true' || (row.querySelector('details')?.open && row.querySelector('details')?.dataset.expanded!=='false'),
        rings:Array.from(row.querySelectorAll('.subtask-ring'),el=>parseFloat(getComputedStyle(el).getPropertyValue('--progress'))||0)});
      row.querySelectorAll('*').forEach(el=>{ motions.get(el)?.stop(); if(typeof gsap!=='undefined') gsap.killTweensOf(el); });
    });
  }
  function afterRender(){
    Icons.mount();
    document.querySelectorAll('#content .has-subtasks .card-main').forEach(header=>{anchorHeader(header);headers.observe(header);});
    document.querySelectorAll('#content [data-item]').forEach(row=>{
      const checkEl=row.querySelector('[data-done]');
      const old=rows.get(row.dataset.item+'|'+(checkEl?.dataset.doneKey||''));
      const details=row.querySelector('details');
      if(old?.open && details){ details.open=true; details.dataset.expanded='true'; Icons.set(details.querySelector('.subtask-chevron'),'up',false); }
      row.querySelectorAll('.subtask-ring').forEach((ring,i)=>{
        const target=parseFloat(ring.style.getPropertyValue('--progress'))||0;
        if(old && enabled()){
          ring.style.setProperty('--progress',old.rings[i]??target);
          progress(ring.closest('summary'),target,ring.querySelector('span').textContent);
        }
      });
    });
    rows.clear();
    document.querySelectorAll('#content .csc-progress-bar').forEach(bar=>{
      const target=parseFloat(bar.dataset.progress)||0;
      const key=bar.closest('.cost-summary-card').dataset.costKey;
      if(enabled()){
        bar.style.width=(budgets.get(key)??0)+'%';
        width(bar,target);
      }else bar.style.width=target+'%';
    });
  }
  function show(el){
    const drawerStart=el.matches('.drawer') && enabled()
      ? (el.hidden?'translateX(-100%)':getComputedStyle(el).transform) : null;
    exits.delete(el);
    motions.get(el)?.stop();
    el.style.removeProperty('opacity'); el.style.removeProperty('transform');
    delete el.dataset.motionClosing;
    el.inert=false;
    // Seed the first frame before unhiding; Motion resolves keyframes asynchronously.
    if(drawerStart) el.style.transform=drawerStart;
    else if(el.hidden && enabled() && el.matches('.modal-mask, .drawer-mask')) el.style.opacity='0';
    el.hidden=false;
    if(el.matches('.drawer')){
      animate(el,{transform:[drawerStart,'translateX(0%)']},{duration:.28});
    }else if(el.matches('.modal')){
      animate(el,{opacity:[0,1],transform:['translate(-50%,-50%) scale(.97)','translate(-50%,-50%) scale(1)']});
    }else if(el.matches('.modal-mask, .drawer-mask')){
      animate(el,{opacity:[0,1]},{duration:.2});
    }
  }
  function hide(el){
    exits.delete(el);
    if(el.hidden || !enabled()){ motions.get(el)?.stop(); el.hidden=true; delete el.dataset.motionClosing; el.inert=false; return; }
    el.dataset.motionClosing='true';
    el.inert=true;
    const transform=getComputedStyle(el).transform;
    const frames={opacity:[getComputedStyle(el).opacity,0]};
    // Full-screen masks only fade: scaling them briefly exposes the page at the edges.
    if(el.matches('.drawer')){
      delete frames.opacity;
      frames.transform=[transform,'translateX(-100%)'];
    }
    else if(el.matches('.modal')) frames.transform=[transform,transform==='none'?'scale(.98)':transform+' scale(.98)'];
    const control=animate(el,frames,{duration:(el.matches('.drawer')||el.matches('.drawer-mask')) ? .26 : .14});
    exits.set(el,control);
    // Let the library finish the last frame before hiding; reopening invalidates this exit.
    control.then(()=>{
      if(exits.get(el)!==control) return;
      exits.delete(el);
      el.hidden=true;
      delete el.dataset.motionClosing;
      el.inert=false;
      motions.get(el)?.stop();
      el.style.removeProperty('opacity'); el.style.removeProperty('transform');
    }).catch(()=>{});
  }
  document.addEventListener('click',event=>{
    const summary=event.target.closest('.subtask-list summary');
    if(summary){ event.preventDefault(); toggleDetails(summary.closest('details')); }
    const button=event.target.closest('button, .seg-chip, .seg, .star-b');
    if(button) animate(button,{scale:[1,.96,1]},{duration:.16});
  },true);
  document.addEventListener('change',event=>{
    if(event.target.matches('input[type="checkbox"]')) check(event.target,event.target.checked);
  });
  return {check,progress,width,beforeRender,afterRender,animate,show,hide,isOpen:el=>!el.hidden && el.dataset.motionClosing!=='true'};
})();
document.documentElement.classList.add('motion-enabled');
