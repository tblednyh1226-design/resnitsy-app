/* Final calendar UX hotfix: clean week header and make day mode impossible to flash week view. */
(function(){
  function cleanWeekActions(){
    document.querySelectorAll('#week .day .plus,#week .day .dayoff-mini').forEach(x=>x.remove());
  }

  const previousRenderWeek=window.renderWeekV4;
  if(typeof previousRenderWeek==='function'){
    window.renderWeekV4=async function(){
      const out=await previousRenderWeek.apply(this,arguments);
      cleanWeekActions();
      return out;
    };
  }

  /* Guard the global renderer itself. In day mode it never touches/shows week. */
  const previousRenderCal=window.renderCal;
  window.renderCal=async function(){
    if(view!=='day'){
      const out=await previousRenderCal.apply(this,arguments);
      cleanWeekActions();
      return out;
    }
    const week=document.getElementById('week');
    const day=document.getElementById('dayview');
    const wt=document.getElementById('weekTab');
    const dt=document.getElementById('dayTab');
    if(week)week.style.setProperty('display','none','important');
    if(day)day.classList.add('on');
    if(wt)wt.classList.remove('on');
    if(dt)dt.classList.add('on');
    const sub=document.getElementById('calSub');
    if(sub)sub.textContent='Выбранный день';
    return await window.renderDayV4();
  };

  function rebind(id,handler){
    const old=document.getElementById(id);
    if(!old)return;
    const fresh=old.cloneNode(true);
    old.replaceWith(fresh);
    fresh.onclick=handler;
  }

  async function moveDay(delta){
    focus=add(focus,delta);
    const week=document.getElementById('week');
    const day=document.getElementById('dayview');
    if(week)week.style.setProperty('display','none','important');
    if(day)day.classList.add('on');
    await window.renderDayV4();
    if(week)week.style.setProperty('display','none','important');
  }

  rebind('prev',async()=>{
    if(view==='week'){
      weekStart=add(weekStart,-7);focus=weekStart;await window.renderCal();
    }else await moveDay(-1);
  });
  rebind('next',async()=>{
    if(view==='week'){
      weekStart=add(weekStart,7);focus=weekStart;await window.renderCal();
    }else await moveDay(1);
  });

  /* Extra safety: if any legacy code tries to expose week while in day mode, hide it immediately. */
  const week=document.getElementById('week');
  if(week){
    new MutationObserver(()=>{if(view==='day'&&week.style.display!=='none')week.style.setProperty('display','none','important')}).observe(week,{attributes:true,attributeFilter:['style']});
  }
  cleanWeekActions();
})();
