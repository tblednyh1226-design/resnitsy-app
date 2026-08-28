/* Show active waitlist status directly in Clients directory. */
(function(){
  const base=window.renderClientRows;
  if(typeof base!=='function')return;
  window.renderClientRows=async function(q=''){
    const box=document.getElementById('clientList');
    box.innerHTML='<div class="sub">Загрузка клиентов…</div>';
    try{
      const [z,w]=await Promise.all([serviceApi('clients',{q}),waitApi('list',{})]);
      const rows=z.clients||[], waits=w.rows||[];
      const activeByClient=new Map();
      waits.filter(x=>x.status==='active'||x.status==='offered'||x.status==='new').forEach(x=>{
        const key=x.client_id||x.clientId;
        if(key)activeByClient.set(String(key),x);
      });
      const count=document.getElementById('clientCountV4');
      if(count)count.textContent=`Клиентов: ${rows.length}${q?' по запросу':''}`;
      box.innerHTML=rows.map(c=>{
        const nm=c.display_name||'Без имени',initial=nm.trim().charAt(0).toUpperCase()||'•',wl=activeByClient.get(String(c.id));
        const badge=wl?'<span class="client-wl-badge" title="Ловец окошек включён"><span class="client-wl-dot"></span>Ловец</span>':'';
        return `<button class="client-row cli-v4" data-id="${c.id}"><span class="client-avatar">${initial}${wl?'<i class="client-avatar-wl"></i>':''}</span><span class="client-main"><b>${nm}</b><span class="sub">${fmtPhone(c.phone)}</span>${badge}</span><span class="client-visits">${c.imported_visit_count||0} виз.<br>${c.dikidi_last_visit_raw||''}</span></button>`;
      }).join('')||'<div class="sub">Ничего не найдено</div>';
      document.querySelectorAll('.cli-v4').forEach(b=>b.onclick=()=>clientCard(b.dataset.id));
    }catch(e){box.innerHTML='<div class="card">Ошибка загрузки клиентов: '+e.message+'</div>'}
  };
  const st=document.createElement('style');
  st.textContent='.client-main{min-width:0}.client-wl-badge{display:inline-flex;align-items:center;gap:5px;width:max-content;margin-top:5px;padding:3px 8px;border-radius:999px;background:#fff0f5;color:#a92755;font-size:11px;font-weight:700}.client-wl-dot{width:7px;height:7px;border-radius:50%;background:#ff3b7a}.client-avatar{position:relative}.client-avatar-wl{position:absolute;right:0;top:1px;width:10px;height:10px;border-radius:50%;background:#ff3b7a;border:2px solid white;box-shadow:0 0 0 2px #ffddea}';
  document.head.appendChild(st);
})();
