/* Calendar UX: clean week header and atomic day-to-day navigation. */
(function(){
  function cleanWeekActions(){document.querySelectorAll('#week .day .plus,#week .day .dayoff-mini').forEach(x=>x.remove())}
  const previousRenderWeek=window.renderWeekV4;
  if(typeof previousRenderWeek==='function')window.renderWeekV4=async function(){const out=await previousRenderWeek.apply(this,arguments);cleanWeekActions();return out};

  const previousRenderCal=window.renderCal;
  window.renderCal=async function(){
    if(view!=='day'){const out=await previousRenderCal.apply(this,arguments);cleanWeekActions();return out}
    const week=document.getElementById('week'),day=document.getElementById('dayview'),wt=document.getElementById('weekTab'),dt=document.getElementById('dayTab');
    if(week)week.style.setProperty('display','none','important');if(day)day.classList.add('on');if(wt)wt.classList.remove('on');if(dt)dt.classList.add('on');
    const sub=document.getElementById('calSub');if(sub)sub.textContent='Выбранный день';
    return await window.renderDayV4();
  };

  function rebind(id,handler){const old=document.getElementById(id);if(!old)return;const fresh=old.cloneNode(true);old.replaceWith(fresh);fresh.onclick=handler}
  function freezeCurrentDay(){
    const day=document.getElementById('dayview');if(!day)return null;
    const r=day.getBoundingClientRect();if(!r.width||!r.height)return null;
    const clone=day.cloneNode(true);clone.id='dayFreezeFrame';clone.querySelectorAll('[id]').forEach(x=>x.removeAttribute('id'));
    Object.assign(clone.style,{position:'fixed',left:r.left+'px',top:r.top+'px',width:r.width+'px',height:r.height+'px',margin:'0',zIndex:'9998',background:getComputedStyle(day).backgroundColor||'#fff',overflow:'hidden',pointerEvents:'none',display:'block'});
    clone.classList.add('on');document.body.appendChild(clone);return clone;
  }
  async function moveDay(delta){
    const frame=freezeCurrentDay();
    try{
      focus=add(focus,delta);view='day';
      const week=document.getElementById('week'),day=document.getElementById('dayview');
      if(week)week.style.setProperty('display','none','important');if(day)day.classList.add('on');
      await window.renderDayV4();
      if(week)week.style.setProperty('display','none','important');
      await new Promise(resolve=>requestAnimationFrame(()=>requestAnimationFrame(resolve)));
    }finally{if(frame)frame.remove()}
  }

  rebind('prev',async()=>{if(view==='week'){weekStart=add(weekStart,-7);focus=weekStart;await window.renderCal()}else await moveDay(-1)});
  rebind('next',async()=>{if(view==='week'){weekStart=add(weekStart,7);focus=weekStart;await window.renderCal()}else await moveDay(1)});

  const week=document.getElementById('week');if(week)new MutationObserver(()=>{if(view==='day'&&week.style.display!=='none')week.style.setProperty('display','none','important')}).observe(week,{attributes:true,attributeFilter:['style']});
  cleanWeekActions();
})();
