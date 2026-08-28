/* Per-weekday working time groups + all open custom slots in week view. */
(function(){
  const defs=[[1,'Пн'],[2,'Вт'],[3,'Ср'],[4,'Чт'],[5,'Пт'],[6,'Сб'],[0,'Вс']];
  let groups=[];
  function esc(s){return String(s||'').replace(/[&<>"']/g,m=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[m]))}
  async function load(){const z=await dayoffApi('weekly');weeklyWorkdays=Array.isArray(z.weekdays)?z.weekdays:weeklyWorkdays;groups=Array.isArray(z.work_time_groups)?z.work_time_groups:[];return z}
  function dayTimes(ds){const d=weekdayOf(ds);if(groups.length){const g=groups.find(x=>(x.days||[]).includes(d));return g?[...(g.times||[])]:[]}return weeklyWorkdays.includes(d)?['10:00','13:00','16:00','19:00']:[]}
  function localDate(iso){return new Date(iso).toLocaleDateString('sv-SE',{timeZone:'Europe/Moscow'})}
  function localTime(iso){return new Date(iso).toLocaleTimeString('ru-RU',{timeZone:'Europe/Moscow',hour:'2-digit',minute:'2-digit'})}

  const prevWeek=window.renderWeekV4;
  window.renderWeekV4=async function(){
    await load();
    const out=await prevWeek.apply(this,arguments);
    const cols=[...document.querySelectorAll('#week .day')],h=Math.max(420,document.getElementById('week').clientHeight||520);
    cols.forEach((col,i)=>{
      const ds=add(weekStart,i); if(isDayOff(lastCal,ds))return;
      col.querySelectorAll('.free').forEach(x=>x.remove());
      const aps=(lastCal.appointments||[]).filter(a=>ld(a.starts_at)===ds&&a.status!=='cancelled');
      const ovs=(lastCal.overrides||[]).filter(o=>localDate(o.slot_start)===ds);
      const openCustom=ovs.filter(o=>o.is_available).map(o=>localTime(o.slot_start));
      const times=[...new Set([...dayTimes(ds),...openCustom])].sort();
      times.forEach(t=>{
        const iso=new Date(ds+'T'+t+':00+03:00').toISOString(),o=ovs.find(x=>new Date(x.slot_start).getTime()===new Date(iso).getTime());
        if(o&&o.is_available===false)return;
        const moment=new Date(iso),busy=aps.some(a=>moment>=new Date(a.starts_at)&&moment<new Date(a.ends_at));if(busy)return;
        const m=Number(t.slice(0,2))*60+Number(t.slice(3)),b=document.createElement('button');
        b.className='free'; b.style.top=(42+(m-540)/840*(h-42))+'px'; b.textContent=t;
        b.onclick=e=>{e.stopPropagation();focus=ds;view='day';renderCal()};
        col.appendChild(b);
      });
    });
    return out;
  };

  function summary(g){return `<div class="wt-row"><div><b>${defs.filter(d=>(g.days||[]).includes(d[0])).map(d=>d[1]).join(' · ')}</b><div class="sub">${(g.times||[]).join(' · ')}</div></div><button class="btn wt-edit" data-id="${esc(g.id)}">Изменить</button></div>`}
  function block(){
    const card=document.createElement('div');card.className='card dayoff-settings-block wt-settings';card.dataset.worktime='1';
    card.innerHTML='<b>Рабочее время по дням</b><div class="sub">Задайте разные времена начала записи для разных дней недели. День, не выбранный ни в одном режиме, считается выходным.</div><div id="wtList">'+(groups.length?groups.map(summary).join(''):'<div class="sub" style="margin-top:10px">Пока используется стандартное время 10:00 · 13:00 · 16:00 · 19:00</div>')+'</div><button class="btn primary" id="wtAdd" style="width:100%;margin-top:10px">+ Добавить режим</button>';
    return card;
  }
  function bindBlock(){document.querySelectorAll('.wt-edit').forEach(b=>b.onclick=()=>edit(b.dataset.id));const a=document.getElementById('wtAdd');if(a)a.onclick=()=>edit(null)}
  function timesHtml(ts){return (ts.length?ts:['10:00']).map(t=>`<div class="wt-time-row"><input type="time" step="1800" class="wt-time" value="${esc(t)}"><button type="button" class="btn wt-del-time">×</button></div>`).join('')}
  function edit(id){
    const old=groups.find(g=>g.id===id),days=old?.days||[],times=old?.times||['10:00','13:00','16:00','19:00'];
    modal(id?'Рабочее время':'Новый режим','Выберите дни и время начала записей',`<div class="wt-days">${defs.map(([n,l])=>`<label class="wt-day ${days.includes(n)?'on':''}"><input type="checkbox" value="${n}" ${days.includes(n)?'checked':''}><span>${l}</span></label>`).join('')}</div><div class="sub" style="margin:12px 0 6px">Время начала записей</div><div id="wtTimes">${timesHtml(times)}</div><button type="button" class="btn" id="wtAddTime" style="width:100%;margin-top:6px">+ Добавить время</button>`, `<button class="btn primary" id="wtSave">Сохранить</button>${id?'<button class="btn" id="wtDelete">Удалить режим</button>':''}<button class="btn" id="wtCancel">Отмена</button>`);
    document.querySelectorAll('.wt-day input').forEach(x=>x.onchange=()=>x.closest('.wt-day').classList.toggle('on',x.checked));
    function bindTimes(){document.querySelectorAll('.wt-del-time').forEach(b=>b.onclick=()=>b.closest('.wt-time-row').remove())}bindTimes();
    document.getElementById('wtAddTime').onclick=()=>{document.getElementById('wtTimes').insertAdjacentHTML('beforeend',timesHtml(['10:00']));bindTimes()};
    document.getElementById('wtCancel').onclick=closeModal;
    if(id)document.getElementById('wtDelete').onclick=async()=>{groups=groups.filter(g=>g.id!==id);await persist();closeModal();await renderSettings()};
    document.getElementById('wtSave').onclick=async()=>{const sel=[...document.querySelectorAll('.wt-day input:checked')].map(x=>Number(x.value)),ts=[...new Set([...document.querySelectorAll('.wt-time')].map(x=>x.value).filter(Boolean))].sort();if(!sel.length)return alert('Выберите хотя бы один день');if(!ts.length)return alert('Добавьте хотя бы одно время');groups=groups.map(g=>({...g,days:(g.days||[]).filter(d=>!sel.includes(d))})).filter(g=>g.days.length);const ng={id:id||('g'+Date.now()),days:sel,times:ts};groups=id?groups.filter(g=>g.id!==id).concat(ng):groups.concat(ng);await persist();closeModal();await renderSettings()};
  }
  async function persist(){const z=await dayoffApi('setWorkTime',{groups});groups=z.work_time_groups||groups;const work=[...new Set(groups.flatMap(g=>g.days||[]))];const w=await dayoffApi('setWeekly',{weekdays:work});weeklyWorkdays=w.weekdays||work}

  const prevSettings=window.renderSettings;
  window.renderSettings=async function(){await prevSettings.apply(this,arguments);await load();const box=document.getElementById('settingsBox');if(!box)return;box.querySelectorAll('[data-worktime]').forEach(x=>x.remove());const card=block(),weekly=box.querySelector('.dayoff-settings-block');if(weekly)weekly.insertAdjacentElement('afterend',card);else box.appendChild(card);bindBlock()};

  const st=document.createElement('style');st.textContent='.wt-row{display:flex;align-items:center;justify-content:space-between;gap:8px;padding:10px 0;border-bottom:1px solid var(--line)}.wt-days{display:grid;grid-template-columns:repeat(7,1fr);gap:5px}.wt-day{border:1px solid var(--line);border-radius:10px;padding:9px 2px;text-align:center}.wt-day input{display:none}.wt-day.on{background:var(--soft);border-color:var(--accent);font-weight:700}.wt-time-row{display:grid;grid-template-columns:1fr 44px;gap:6px;margin:6px 0}.wt-time-row input{width:100%}';document.head.appendChild(st);
})();