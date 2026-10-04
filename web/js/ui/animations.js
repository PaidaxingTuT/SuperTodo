'use strict';
// Local GSAP 3.15.0 + Motion 13.5.0. Keep animations outside saved task data.
const UiMotion = (() => {
  const reduced = matchMedia('(prefers-reduced-motion: reduce)');
  const motions = new WeakMap();
  const exits = new WeakMap();
  let rows = new Map();
  const budgets = new Map();
  // Measure the title and metadata only; expanding the checklist never changes this anchor.
  const anchorHeader=header=>{
    const body=header.parentElement, summary=body.querySelector('.subtask-list summary');
    if(summary) body.style.setProperty('--subtask-space',Math.ceil(summary.getBoundingClientRect().width)+8+'px');
    body.style.setProperty('--subtask-anchor',header.getBoundingClientRect().height/2+'px');
    const front=body.parentElement;
    if(front) front.style.setProperty('--subtask-drag-anchor',header.getBoundingClientRect().top-front.getBoundingClientRect().top+header.getBoundingClientRect().height/2+'px');
  };
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
    if(el.matches('.subtask-check input')) el=el.closest('.subtask-toggle');
    const ripple=getComputedStyle(el).getPropertyValue('--primary-soft').trim();
    animate(el,{scale:done?[1,.9,1.08,1]:[1,.92,1]},{duration:.24});
    if(done && enabled()) el.animate([{boxShadow:'0 0 0 0px transparent'},{boxShadow:`0 0 0 5px ${ripple}`},{boxShadow:'0 0 0 9px transparent'}],{duration:240,easing:'ease-out'});
  }
  function progress(summary,percent,done){
    const ring=summary.querySelector('.subtask-ring');
    if(!ring) return;
    const span=ring.querySelector('span');
    if(span) span.textContent=done;
    ring.dataset.complete='false';
    const finish=()=>{
      ring.dataset.complete=String(percent>=100);
      if('value' in ring) ring.value=percent;
      else if(typeof ring.setAttribute==='function') ring.setAttribute('value',percent);
      ring.style.removeProperty('--indicator-transition-duration');
    };
    if(enabled()){
      ring.style.setProperty('--indicator-transition-duration','0s');
      gsap.to(ring,{
        '--progress':percent,
        duration:.28,
        ease:'none',
        overwrite:true,
        onUpdate(){
          const val=parseFloat(ring.style.getPropertyValue('--progress'))||0;
          if('value' in ring) ring.value=val;
          else if(typeof ring.setAttribute==='function') ring.setAttribute('value',val);
        },
        onComplete:finish
      });
    }else{
      ring.style.setProperty('--progress',percent);
      if('value' in ring) ring.value=percent;
      else if(typeof ring.setAttribute==='function') ring.setAttribute('value',percent);
      finish();
    }
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
      gsap.set(steps,{height:0,opacity:0,overflow:'hidden',marginTop:0});
    }
    steps.style.overflow='hidden';
    gsap.to(steps,{
      height:open?'auto':0,opacity:open?1:0,marginTop:open?8:0,
      duration:.25,ease:'power2.inOut',overwrite:true,
      onComplete(){
        details.open=open;
        gsap.set(steps,{clearProps:'height,opacity,overflow,marginTop'});
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
    el.style.removeProperty('opacity'); el.style.removeProperty('transform'); el.style.removeProperty('clip-path');
    delete el.dataset.motionClosing;
    el.inert=false;
    // Seed the first frame before unhiding; Motion resolves keyframes asynchronously.
    if(drawerStart) el.style.transform=drawerStart;
    else if(el.hidden && enabled() && el.matches('.searchbar')){
      el.style.opacity='0';
      el.style.clipPath='inset(0 0 0 calc(100% - 56px) round 28px)';
    }
    else if(el.hidden && enabled() && el.matches('.modal-mask, .drawer-mask')) el.style.opacity='0';
    el.hidden=false;
    if(el.matches('.drawer')){
      animate(el,{transform:[drawerStart,'translateX(0%)']},{duration:.28});
    }else if(el.matches('.searchbar')){
      animate(el,{opacity:[0,1],clipPath:['inset(0 0 0 calc(100% - 56px) round 28px)','inset(0 0 0 0 round 28px)'],x:[8,0]},{duration:.28});
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
    const frames={opacity:[Number(getComputedStyle(el).opacity),0]};
    // Full-screen masks only fade: scaling them briefly exposes the page at the edges.
    if(el.matches('.drawer')){
      delete frames.opacity;
      frames.transform=[transform,'translateX(-100%)'];
    }
    else if(el.matches('.modal')) frames.transform=[transform,transform==='none'?'scale(.98)':transform+' scale(.98)'];
    else if(el.matches('.searchbar')){
      frames.clipPath=[getComputedStyle(el).clipPath,'inset(0 0 0 calc(100% - 56px) round 28px)'];
      frames.opacity=[frames.opacity[0],frames.opacity[0],0];
      frames.transform=[transform,'translateX(8px)'];
      // Keep the current frame visible while Motion replaces the entrance animation.
      motions.get(el)?.stop();
      motions.delete(el);
      el.style.opacity=frames.opacity[0];
      el.style.clipPath=frames.clipPath[0];
    }
    const control=animate(el,frames,el.matches('.searchbar')
      ? {duration:.28,ease:[.4,0,.2,1],opacity:{times:[0,.8,1]}}
      : {duration:(el.matches('.drawer')||el.matches('.drawer-mask')) ? .26 : .14});
    exits.set(el,control);
    // Let the library finish the last frame before hiding; reopening invalidates this exit.
    control.then(()=>{
      if(exits.get(el)!==control) return;
      exits.delete(el);
      el.hidden=true;
      delete el.dataset.motionClosing;
      el.inert=false;
      motions.get(el)?.stop();
      el.style.removeProperty('opacity'); el.style.removeProperty('transform'); el.style.removeProperty('clip-path');
    }).catch(()=>{});
    return control;
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
