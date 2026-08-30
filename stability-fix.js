// Slotelly runtime stability layer: stop render storms and open waitlist immediately.
(()=>{
  const esc=s=>String(s??'').replace(/[&<>"']/g,m=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[m]));

  // Never hit the dayoff backend just to paint the calendar.
  try{
    if(typeof loadWeeklySchedule==='function'){
      loadWeeklySchedule=async function(force=false){
        const days=window.SLOTELLY_SNAPSHOT?.settings?.schedule?.weekdays;
        if(Array.isArray(days)){weeklyWorkdays=days;return}
        const fallback=settings?.schedule?.weekdays;
        if(Array.isArray(fallback)){weeklyWorkdays=fallback;return}
        if(force){
          try{const z=await dayoffApi('weekly');weeklyWorkdays=Array.isArray(z.weekdays)?z.weekdays:[0,1,2,3,4,5,6]}catch{weeklyWorkdays=[0,1,2,3,4,5,6]}
        }
      };
    }
  }catch(e){console.warn('Slotelly schedule stabilization',e)}

  // Collapse concurrent calendar renders into one.
  try{
    if(typeof renderCal==='function'){
      const baseRenderCal=renderCal;let rendering=false;
      renderCal=async function(){
        if(rendering)return;
        rendering=true;
        try{await baseRenderCal()}finally{rendering=false}
      };
    }
  }catch(e){console.warn('Slotelly calendar stabilization',e)}

  // One POST, no CORS preflight, with timeout.
  try{
    if(typeof waitApi==='function'){
      waitApi=async function(action,data={}){
        const ctl=new AbortController(),timer=setTimeout(()=>ctl.abort(),8000);
        try{
          const r=await fetch(WAIT_EDGE,{method:'POST',headers:{'Content-Type':'text/plain;charset=UTF-8'},body:JSON.stringify({pin:PIN,action,...data}),signal:ctl.signal,cache:'no-store'});
          const j=await r.json();if(!r.ok||!j.ok)throw Error(j.error||'Ошибка ловца окошек');return j;
        }catch(e){if(e?.name==='AbortError')throw Error('Ловец не ответил за 8 секунд');throw e}finally{clearTimeout(timer)}
      };
    }
  }catch(e){console.warn('Slotelly waitlist transport',e)}

  const statusLabel=s=>({active:'Ждёт',offered:'Предложено',accepted:'Согласился',expired:'Истёк',cancelled:'Отменено',new:'Новый'})[s]||s||'';
  const dateTxt=x=>x?new Date(x+'T12:00:00+03:00').toLocaleDateString('ru-RU',{day:'2-digit',month:'2-digit',timeZone:'Europe/Moscow'}):'—';

  async function openFastWaitlist(initial='active'){
    try{
      modal('Ловец окошек','Загрузка…','<div class="sub" style="padding:18px">Загружаю список…</div>','<button class="btn" id="wlFastClose">Закрыть</button>');
      document.getElementById('wlFastClose').onclick=closeModal;
      const z=await waitApi('list',{}),all=z.rows||[],active=all.filter(r=>['active','offered','new'].includes(r.status)),archive=all.filter(r=>!['active','offered','new'].includes(r.status));
      let tab=initial;
      const body=document.getElementById('mb'),sub=document.getElementById('ms');
      sub.textContent=`${active.length} ждут свободное время`;
      const render=()=>{
        const rows=tab==='active'?active:archive;
        body.innerHTML=`<div class="wl-tabs"><button class="btn wl-fast-tab ${tab==='active'?'on':''}" data-tab="active">Активные ${active.length}</button><button class="btn wl-fast-tab ${tab==='history'?'on':''}" data-tab="history">История ${archive.length}</button></div><div class="wl-request-list">${rows.length?rows.map(r=>{const c=r.clients||{};return `<div class="wl-request-card" data-id="${esc(r.id)}"><div class="wl-card-top"><div class="wl-avatar">${esc((c.display_name||'К').trim().charAt(0).toUpperCase()||'К')}</div><div class="wl-person"><b>${esc(c.display_name||'Клиент')}</b><div class="sub">${esc(c.phone||'')}</div></div><span class="wl-status ${esc(r.status)}">${esc(statusLabel(r.status))}</span></div><div class="wl-summary"><span>📅 ${dateTxt(r.date_from)}–${dateTxt(r.date_to)}</span>${r.time_from||r.time_to?`<span>🕐 ${esc(String(r.time_from||'').slice(0,5))}–${esc(String(r.time_to||'').slice(0,5))}</span>`:''}</div><div class="wl-service-line">${esc(r.desired_text||'Услуга не указана')}</div><details class="wl-fast-history" data-id="${esc(r.id)}"><summary>История</summary><div class="wl-fast-history-body sub" style="padding-top:6px">Нажмите, чтобы загрузить</div></details><div class="wl-actions">${r.client_id?`<button class="btn wl-fast-client" data-client="${esc(r.client_id)}">Клиент</button>`:''}</div></div>`}).join(''):'<div class="sub" style="padding:18px">Нет запросов</div>'}</div>`;
        body.querySelectorAll('.wl-fast-tab').forEach(b=>b.onclick=()=>{tab=b.dataset.tab;render()});
        body.querySelectorAll('.wl-fast-client').forEach(b=>b.onclick=()=>clientCard(b.dataset.client));
        body.querySelectorAll('.wl-fast-history').forEach(d=>d.ontoggle=async()=>{if(!d.open||d.dataset.loaded)return;d.dataset.loaded='1';const host=d.querySelector('.wl-fast-history-body');host.textContent='Загрузка…';try{const h=await waitApi('history',{id:d.dataset.id}),rows=h.rows||[];host.innerHTML=rows.length?rows.map(x=>`<div style="padding:5px 0;border-bottom:1px solid var(--line)"><b>${esc(x.event_text||x.event_type||'Событие')}</b><div class="sub">${x.created_at?new Date(x.created_at).toLocaleString('ru-RU',{timeZone:'Europe/Moscow'}):''}</div></div>`).join(''):'История пуста'}catch(e){host.textContent=e.message}});
      };
      render();
    }catch(e){const body=document.getElementById('mb');if(body)body.innerHTML=`<div class="card">${esc(e.message)}</div>`;else alert(e.message)}
  }

  window.openWaitlistOverview=openFastWaitlist;
  const bindWait=()=>{const b=document.getElementById('homeWaitlistBtn');if(b)b.onclick=()=>openFastWaitlist('active')};
  bindWait();window.addEventListener('slotelly:snapshot',bindWait);
})();
