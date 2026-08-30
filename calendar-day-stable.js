/* Slotelly day calendar final compositor: render one clean layer, no duplicate slot/appointment stacks. */
(function(){
  function esc(s){return String(s??'').replace(/[&<>"']/g,m=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[m]))}
  function localDate(iso){return new Date(iso).toLocaleDateString('sv-SE',{timeZone:'Europe/Moscow'})}
  function localTime(iso){return new Date(iso).toLocaleTimeString('ru-RU',{timeZone:'Europe/Moscow',hour:'2-digit',minute:'2-digit'})}
  function overlap(a,b,c,d){return a<d&&b>c}
  function scheduleTimes(ds,cal){
    const snap=window.SLOTELLY_SNAPSHOT||{},sch=snap.settings?.schedule||snap.settings||{},groups=sch.work_time_groups||window.workdayOverrideGroups||[];
    const dow=new Date(ds+'T12:00:00+03:00').getDay(),g=(groups||[]).find(x=>(x.days||[]).includes(dow));
    let times=g?[...(g.times||[])]:[];
    if(!times.length && (!Array.isArray(sch.weekdays)||sch.weekdays.includes(dow))) times=['10:00','13:00','16:00','19:00'];
    const extra=(cal?.overrides||[]).filter(o=>o.is_available&&localDate(o.slot_start)===ds).map(o=>localTime(o.slot_start));
    return [...new Set([...times,...extra])].sort();
  }
  async function stableDay(){
    const target=focus,from=new Date(target+'T00:00:00+03:00').toISOString(),to=new Date(add(target,1)+'T00:00:00+03:00').toISOString();
    const cal=await rpc('master_app_calendar',{p_pin:PIN,p_from:from,p_to:to});window.lastDayCal=cal;window.lastCal=cal;
    const period=document.getElementById('period');if(period)period.innerHTML='<span class="period-weekday">'+ruDate(target,{weekday:'long'})+'</span><span class="period-date">'+ruDate(target,{day:'numeric',month:'long'})+'</span>';
    const dv=document.getElementById('dayview');if(!dv)return;dv.classList.add('on');dv.classList.remove('day-off-view');
    const start=8,end=22,rowH=64,total=(end-start)*rowH;
    const strip=Array.from({length:7},(_,i)=>add(target,i-3)).map(ds=>`<button class="day-date-btn ${ds===target?'on':''}" data-date="${ds}">${ruDate(ds,{weekday:'short'})}<br><b>${ruDate(ds,{day:'numeric'})}</b></button>`).join('');
    dv.innerHTML=`<div class="day-date-strip">${strip}</div><div class="day-canvas stable-day-canvas" style="height:${total}px"></div>`;
    dv.querySelectorAll('.day-date-btn').forEach(b=>b.onclick=()=>{focus=b.dataset.date;view='day';window.renderCal()});
    const canvas=dv.querySelector('.day-canvas');
    for(let h=start;h<=end;h++){const y=(h-start)*rowH,row=document.createElement('div');row.className='day-hour';row.style.top=y+'px';row.innerHTML=`<span class="day-hour-label">${String(h).padStart(2,'0')}:00</span>${h<end?'<span class="day-half-line"></span>':''}`;canvas.appendChild(row)}
    const aps=(cal.appointments||[]).filter(a=>a.status!=='cancelled'&&localDate(a.starts_at)===target),blocks=(cal.blocks||[]).filter(b=>localDate(b.starts_at)===target);
    const times=scheduleTimes(target,cal);
    times.forEach(t=>{const iso=new Date(target+'T'+t+':00+03:00').toISOString(),st=new Date(iso),en=new Date(st.getTime()+30*60000),ov=(cal.overrides||[]).find(o=>new Date(o.slot_start).getTime()===st.getTime());if(ov&&ov.is_available===false)return;if(aps.some(a=>overlap(st,en,new Date(a.starts_at),new Date(a.ends_at)))||blocks.some(b=>overlap(st,en,new Date(b.starts_at),new Date(b.ends_at))))return;const [hh,mm]=t.split(':').map(Number);if(hh<start||hh>=end)return;const b=document.createElement('button');b.className='day-slot available';b.style.top=(((hh*60+mm)-start*60)/60*rowH+2)+'px';b.innerHTML=`<span class="slot-book-time">${t}</span><span class="slot-state-pill on">Доступно</span>`;b.onclick=e=>{e.preventDefault();e.stopPropagation();if(typeof window.chooseSlotAction==='function')window.chooseSlotAction(target,t);else form(null,target,t)};canvas.appendChild(b)});
    for(const a of aps){const st=localTime(a.starts_at),[hh,mm]=st.split(':').map(Number),dur=Math.max(30,(new Date(a.ends_at)-new Date(a.starts_at))/60000);if(hh<start||hh>=end)continue;const b=document.createElement('button');b.className='day-appt '+(typeof apptClass==='function'?apptClass(a):(a.status||'new'));b.style.top=(((hh*60+mm)-start*60)/60*rowH)+'px';b.style.height=Math.max(34,dur/60*rowH)+'px';b.innerHTML=`<strong>${st} · ${esc(a.client_name||'Клиент')}</strong><small>${esc((a.services||[]).map(s=>s.name).join(' + '))}</small>`;b.onclick=()=>showAppt(a);canvas.appendChild(b)}
    for(const x of blocks.filter(b=>b.source==='manual_break')){const st=localTime(x.starts_at),en=localTime(x.ends_at),[sh,sm]=st.split(':').map(Number),[eh,em]=en.split(':').map(Number),dur=Math.max(15,eh*60+em-(sh*60+sm));if(sh<start||sh>=end)continue;const b=document.createElement('button');b.className='day-break';b.style.top=(((sh*60+sm)-start*60)/60*rowH)+'px';b.style.height=Math.max(30,dur/60*rowH)+'px';b.innerHTML=`<strong>${st}–${en} · Перерыв</strong><small>${esc(x.label||'Личное дело')}</small>`;b.onclick=e=>{e.stopPropagation();if(typeof window.breakForm==='function')window.breakForm(x)};canvas.appendChild(b)}
    canvas.onclick=e=>{if(e.target!==canvas)return;const r=canvas.getBoundingClientRect(),mins=Math.max(0,Math.min((end-start)*60-1,(e.clientY-r.top)/rowH*60)),hh=start+Math.floor(mins/60),mm=(mins%60)<30?'00':'30';form(null,target,`${String(hh).padStart(2,'0')}:${mm}`)};
  }
  window.renderDayV4=stableDay;
  const oldCal=window.renderCal;window.renderCal=async function(){if(view!=='day')return oldCal.apply(this,arguments);const week=document.getElementById('week'),day=document.getElementById('dayview'),wt=document.getElementById('weekTab'),dt=document.getElementById('dayTab');if(week)week.style.setProperty('display','none','important');if(day)day.classList.add('on');wt?.classList.remove('on');dt?.classList.add('on');const sub=document.getElementById('calSub');if(sub)sub.textContent='Выбранный день';return stableDay()};
})();
