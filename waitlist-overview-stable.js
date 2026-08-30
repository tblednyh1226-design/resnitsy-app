/* Reliable Slotelly waitlist overview. Independent from PWA updater. */
(function(){
  const esc=s=>String(s??'').replace(/[&<>"']/g,m=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[m]));
  const statusLabel=s=>({active:'Ждёт',new:'Новый',offered:'Предложено',accepted:'Согласился',expired:'Истёк',cancelled:'Отменено'})[s]||s||'';
  function dateTxt(x){if(!x)return'—';return new Date(x+'T12:00:00+03:00').toLocaleDateString('ru-RU',{day:'2-digit',month:'2-digit',timeZone:'Europe/Moscow'})}
  async function api(action,data={}){
    const ctl=new AbortController(),timer=setTimeout(()=>ctl.abort(),8000);
    try{const r=await fetch(WAIT_EDGE,{method:'POST',headers:{'Content-Type':'text/plain;charset=UTF-8'},body:JSON.stringify({pin:PIN,action,...data}),signal:ctl.signal,cache:'no-store'});const j=await r.json();if(!r.ok||!j.ok)throw Error(j.error||'Ошибка ловца');return j}
    catch(e){if(e?.name==='AbortError')throw Error('Ловец не ответил за 8 секунд');throw e}finally{clearTimeout(timer)}
  }
  async function openOverview(initial='active'){
    modal('Ловец окошек','Клиенты, которые ждут свободное время','<div id="wlStableBody" class="sub" style="padding:18px">Загружаю список…</div>','<button class="btn" id="wlStableClose">Закрыть</button>');
    document.getElementById('wlStableClose').onclick=closeModal;
    const host=document.getElementById('wlStableBody');
    try{
      const z=await api('list',{}),all=z.rows||[],active=all.filter(r=>['active','new','offered'].includes(r.status)),archive=all.filter(r=>!['active','new','offered'].includes(r.status));let tab=initial;
      const render=()=>{const rows=tab==='active'?active:archive;host.innerHTML=`<div style="display:grid;grid-template-columns:1fr 1fr;gap:6px;margin-bottom:10px"><button class="btn ${tab==='active'?'primary':''}" data-wltab="active">Активные ${active.length}</button><button class="btn ${tab==='history'?'primary':''}" data-wltab="history">История ${archive.length}</button></div><div class="list">${rows.length?rows.map(r=>{const c=r.clients||{};return `<div class="card"><div style="display:flex;justify-content:space-between;gap:8px"><b>${esc(c.display_name||'Клиент')}</b><span class="sub">${esc(statusLabel(r.status))}</span></div><div class="sub" style="margin-top:4px">${esc(c.phone||'')}</div><div style="margin-top:7px">${esc(r.desired_text||'Услуга не указана')}</div><div class="sub">${dateTxt(r.date_from)}–${dateTxt(r.date_to)}${r.time_from||r.time_to?' · '+esc(String(r.time_from||'').slice(0,5))+'–'+esc(String(r.time_to||'').slice(0,5)):''}</div>${r.client_id?`<button class="btn wlStableClient" data-client="${esc(r.client_id)}" style="width:100%;margin-top:8px">Открыть клиента</button>`:''}</div>`}).join(''):'<div class="sub" style="padding:14px">Нет запросов</div>'}</div>`;host.querySelectorAll('[data-wltab]').forEach(b=>b.onclick=()=>{tab=b.dataset.wltab;render()});host.querySelectorAll('.wlStableClient').forEach(b=>b.onclick=()=>clientCard(b.dataset.client))};
      render();
    }catch(e){host.innerHTML=`<div class="card">${esc(e.message)}</div><button class="btn primary" id="wlStableRetry" style="width:100%;margin-top:8px">Повторить</button>`;document.getElementById('wlStableRetry').onclick=()=>openOverview(initial)}
  }
  window.openWaitlistOverview=openOverview;
  function bind(){const b=document.getElementById('homeWaitlistBtn');if(b)b.onclick=()=>openOverview('active')}
  bind();document.addEventListener('click',e=>{const b=e.target.closest('#homeWaitlistBtn');if(b){e.preventDefault();openOverview('active')}});
})();