/* Full waitlist history on card tap — works in active and archive tabs. */
(function(){
  const ACTIVE=new Set(['active','offered']);
  const eventNames={created:'Запрос создан',updated:'Изменено',offered:'Предложено окно',declined:'Не подошло / отказ',accepted:'Согласовано',expired:'Истёк срок',session_passed:'Запись прошла',cancelled:'Отменено',reactivated:'Снова активен',auto_no_match:'Подходящих окон пока нет'};
  const statusNames={active:'Ждёт',offered:'Предложено',accepted:'Согласовано',declined:'Не подошло',expired:'Истёк',cancelled:'Отменено'};
  const esc=s=>String(s||'').replace(/[&<>"']/g,m=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[m]));
  function dt(x){if(!x)return'';const d=new Date(x),date=new Intl.DateTimeFormat('ru-RU',{day:'2-digit',month:'2-digit',timeZone:'Europe/Moscow'}).format(d),time=d.toLocaleTimeString('ru-RU',{hour:'2-digit',minute:'2-digit',timeZone:'Europe/Moscow'});return `${date} · ${time}`}
  function requestSummary(r){const c=r.clients||{},range=r.date_from||r.date_to?`${r.date_from||'—'} — ${r.date_to||'—'}`:'Без диапазона дат',time=r.time_from||r.time_to?`${String(r.time_from||'').slice(0,5)}–${String(r.time_to||'').slice(0,5)}`:'любое время';return `<div class="wlh-head"><div><b>${esc(c.display_name||'Клиент')}</b><div class="sub">${esc(c.phone||'')}</div></div><span class="wlh-status">${esc(statusNames[r.status]||r.status||'')}</span></div><div class="wlh-request"><div><b>Пожелание</b></div><div>${esc(r.desired_text||'—')}</div><div class="sub">${esc(range)} · ${esc(time)}</div></div>`}
  async function openHistory(id){
    let row=null,events=[];
    try{
      const z=await waitApi('list',{});row=(z.rows||[]).find(x=>x.id===id)||null;
      events=(await waitApi('history',{id})).rows||[];
    }catch(e){return alert(e.message||'Не удалось загрузить историю')}
    if(!row)return alert('Запрос не найден');
    const tab=ACTIVE.has(row.status)?'active':'history';
    try{await rpc('master_mark_waitlist_seen',{p_pin:PIN,p_request_id:id});if(window.refreshWaitlistCounter)await window.refreshWaitlistCounter()}catch{}
    const timeline=events.length?events.map(e=>`<div class="wlh-event"><div class="wlh-dot"></div><div><div class="wlh-line"><b>${esc(eventNames[e.event_type]||e.event_type)}</b><span>${dt(e.created_at)}</span></div>${e.offered_at?`<div class="wlh-offer">Окно: ${dt(e.offered_at)}</div>`:''}${e.event_text?`<div class="wlh-text">${esc(e.event_text)}</div>`:''}</div></div>`).join(''):'<div class="sub" style="padding:12px 0">История пока пустая</div>';
    modal('История ловца','Все действия по этому запросу',`${requestSummary(row)}<div class="wlh-title">Хронология · ${events.length}</div><div class="wlh-timeline">${timeline}</div>`,`<button class="btn primary" id="wlhBack">Назад</button><button class="btn" id="wlhClient">Клиент</button>`);
    document.getElementById('wlhBack').onclick=()=>{closeModal();setTimeout(()=>window.openWaitlistOverview&&window.openWaitlistOverview(tab),30)};
    document.getElementById('wlhClient').onclick=()=>{if(row.client_id)clientCard(row.client_id)};
  }
  document.addEventListener('click',e=>{
    const card=e.target.closest?.('.wl-request-card');if(!card)return;
    if(e.target.closest('button,.wl-actions,details,summary,a,input,select,textarea'))return;
    e.preventDefault();e.stopPropagation();e.stopImmediatePropagation();
    if(card.dataset.request)openHistory(card.dataset.request);
  },true);
  const st=document.createElement('style');st.textContent=`.wl-request-card{cursor:pointer}.wlh-head{display:flex;justify-content:space-between;align-items:flex-start;gap:10px;padding:4px 0 10px}.wlh-head b{font-size:17px}.wlh-status{font-size:11px;padding:5px 8px;border-radius:999px;background:var(--soft);white-space:nowrap}.wlh-request{border:1px solid var(--line);border-radius:12px;background:#fff;padding:10px;display:grid;gap:4px}.wlh-title{font-weight:800;margin:14px 0 7px}.wlh-timeline{display:grid;gap:0}.wlh-event{display:grid;grid-template-columns:14px 1fr;gap:7px;padding:8px 0;border-bottom:1px solid #eee}.wlh-dot{width:8px;height:8px;border-radius:50%;background:var(--accent);margin-top:5px}.wlh-line{display:flex;justify-content:space-between;gap:8px;align-items:baseline}.wlh-line b{font-size:13px}.wlh-line span{font-size:10px;color:var(--muted);white-space:nowrap}.wlh-text{font-size:12px;margin-top:3px;line-height:1.35}.wlh-offer{font-size:11px;font-weight:700;margin-top:3px}`;document.head.appendChild(st);
})();
