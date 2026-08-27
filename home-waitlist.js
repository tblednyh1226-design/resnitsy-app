/* Home screen waitlist entry and overview */
(function(){
  function fmtDate(x){if(!x)return '—';return new Date(x+'T00:00:00').toLocaleDateString('ru-RU')}
  async function openWaitlistOverview(){
    try{
      const z=await waitApi('list',{});
      const rows=(z.rows||[]).filter(r=>r.status==='active'||r.status==='new');
      const body=rows.length?rows.map(r=>{
        const client=r.clients||{};
        const time=(r.time_from||r.time_to)?` · ${String(r.time_from||'').slice(0,5)}–${String(r.time_to||'').slice(0,5)}`:'';
        return `<button type="button" class="card homeWlRow" data-client="${r.client_id||''}"><b>${client.display_name||'Клиент'}</b><div class="sub">${r.desired_text||'Без услуги'}</div><div class="sub">${fmtDate(r.date_from)} — ${fmtDate(r.date_to)}${time}</div></button>`;
      }).join(''):'<div class="card"><b>Активных запросов нет</b><div class="sub">Добавить ловца можно из карточки клиента</div></div>';
      modal('Ловец окошек',`Активных запросов: ${rows.length}`,`<div class="list">${body}</div>`,'<button class="btn" id="wlHomeClose">Закрыть</button>');
      document.querySelectorAll('.homeWlRow').forEach(b=>b.onclick=()=>{const id=b.dataset.client;if(id)clientCard(id)});
      const close=document.getElementById('wlHomeClose');if(close)close.onclick=closeModal;
    }catch(e){modal('Ловец окошек','Ошибка',`<div class="card">${e.message||'Не удалось загрузить запросы'}</div>`,'<button class="btn" id="wlHomeClose">Закрыть</button>');const c=document.getElementById('wlHomeClose');if(c)c.onclick=closeModal}
  }
  const grid=document.querySelector('#home .homegrid');
  if(grid && !document.getElementById('homeWaitlistBtn')){
    const b=document.createElement('button');
    b.className='homebtn';b.id='homeWaitlistBtn';
    b.innerHTML='<strong>Ловец окошек</strong><span>Клиенты, которые ждут свободное время</span>';
    b.onclick=openWaitlistOverview;
    grid.appendChild(b);
  }
  window.openWaitlistOverview=openWaitlistOverview;
})();
