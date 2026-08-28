/* Final calendar UX hotfix: no action buttons in week header, seamless day navigation. */
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

  function rebind(id,handler){
    const old=document.getElementById(id);
    if(!old)return;
    const fresh=old.cloneNode(true);
    old.replaceWith(fresh);
    fresh.addEventListener('click',handler);
  }

  rebind('prev',async()=>{
    if(view==='week'){
      weekStart=add(weekStart,-7);
      focus=weekStart;
      await renderCal();
    }else{
      focus=add(focus,-1);
      const week=document.getElementById('week');
      const day=document.getElementById('dayview');
      if(week)week.style.display='none';
      if(day)day.classList.add('on');
      await renderDayV4();
    }
  });

  rebind('next',async()=>{
    if(view==='week'){
      weekStart=add(weekStart,7);
      focus=weekStart;
      await renderCal();
    }else{
      focus=add(focus,1);
      const week=document.getElementById('week');
      const day=document.getElementById('dayview');
      if(week)week.style.display='none';
      if(day)day.classList.add('on');
      await renderDayV4();
    }
  });

  cleanWeekActions();
})();
