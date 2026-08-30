// Slotelly fast network layer v6: one shared snapshot, instant cached reads, controlled legacy init.
(() => {
  const FAST_EDGE='https://acukaqoguzkrphauovhk.supabase.co/functions/v1/resnitsy-fast';
  const MASTER_EDGE='https://acukaqoguzkrphauovhk.supabase.co/functions/v1/resnitsy-master';
  const SERVICES_EDGE='https://acukaqoguzkrphauovhk.supabase.co/functions/v1/resnitsy-services';
  const SNAP_KEY='slotelly_snapshot_v1';
  let snapshot=null;

  const sleep=ms=>new Promise(r=>setTimeout(r,ms));
  async function postPlain(url,payload,{tries=2,timeout=10000}={}){
    let last;
    for(let attempt=1;attempt<=tries;attempt++){
      const ctl=new AbortController(),timer=setTimeout(()=>ctl.abort(),timeout);
      try{
        const r=await fetch(url,{method:'POST',headers:{'Content-Type':'text/plain;charset=UTF-8'},body:JSON.stringify(payload),signal:ctl.signal,cache:'no-store'});
        const text=await r.text();let j;try{j=text?JSON.parse(text):null}catch{j=null}
        if(!r.ok)throw Error(j?.error||j?.message||text||`Ошибка сети ${r.status}`);
        return j;
      }catch(e){last=e;if(attempt<tries)await sleep(350*attempt)}finally{clearTimeout(timer)}
    }
    if(last?.name==='AbortError')throw Error('Нет ответа от сервера');
    throw last||Error('Ошибка сети');
  }
  function normalizePhone(x){return String(x||'').replace(/\D/g,'').replace(/^8(?=\d{10}$)/,'7')}
  function filterClients(rows,q=''){
    q=String(q||'').trim();if(!q)return rows||[];
    const low=q.toLowerCase(),digits=normalizePhone(q);
    return (rows||[]).filter(c=>String(c.display_name||'').toLowerCase().includes(low)||(digits&&normalizePhone(c.phone).includes(digits)));
  }
  function inSnapshot(from,to){if(!snapshot?.calendar||!snapshot.from||!snapshot.to)return false;return new Date(from)>=new Date(snapshot.from)&&new Date(to)<=new Date(snapshot.to)}
  function setBadgeFromSnapshot(){
    const n=Number(snapshot?.waitlist_count||0),badge=document.getElementById('homeWaitlistCount'),sub=document.getElementById('homeWaitlistSub');
    if(badge){badge.textContent=n;badge.hidden=!n;badge.classList.remove('is-new')}
    if(sub)sub.textContent=n?`Активных запросов: ${n}`:'Сейчас никто не ждёт окошко';
  }
  function applySnapshot(j,{persist=true,notify=true}={}){
    if(!j?.ok)return;
    snapshot=j;window.SLOTELLY_SNAPSHOT=j;
    if(Array.isArray(j.services))services=j.services;
    if(Array.isArray(j.categories))categories=j.categories;
    if(j.settings&&typeof j.settings==='object')settings=j.settings;
    if(persist){try{localStorage.setItem(SNAP_KEY,JSON.stringify({...j,_cached_at:Date.now()}))}catch{}}
    setBadgeFromSnapshot();
    if(notify)window.dispatchEvent(new CustomEvent('slotelly:snapshot',{detail:j}));
  }
  try{
    const raw=localStorage.getItem(SNAP_KEY),cached=raw?JSON.parse(raw):null;
    if(cached?.ok&&Date.now()-Number(cached._cached_at||0)<12*60*60*1000)applySnapshot(cached,{persist:false,notify:false});
  }catch{}

  const originalRpc=rpc;
  edge=async function(action,data={}){const j=await postPlain(MASTER_EDGE,{pin:PIN,action,...data});if(!j?.ok)throw Error(j?.error||'Ошибка');return j};

  const snapStart=weekStart||monday(focus||todayStr()),snapEnd=add(snapStart,7);
  const snapFrom=new Date(snapStart+'T00:00:00+03:00').toISOString(),snapTo=new Date(snapEnd+'T00:00:00+03:00').toISOString();
  const freshSnapshotPromise=postPlain(FAST_EDGE,{pin:PIN,action:'snapshot',from:snapFrom,to:snapTo},{tries:2,timeout:10000}).then(j=>{
    if(!j?.ok)throw Error(j?.error||'Ошибка запуска');
    applySnapshot(j);
    return j;
  });
  // If a valid local snapshot exists, UI can start immediately while refresh continues in background.
  const snapshotPromise=snapshot?Promise.resolve(snapshot):freshSnapshotPromise;
  freshSnapshotPromise.catch(e=>console.warn('Slotelly background refresh:',e));
  window.SLOTELLY_READY=snapshotPromise;
  window.SLOTELLY_FRESH=freshSnapshotPromise;

  serviceApi=async function(action,data={}){
    if(action==='clients'){
      if(!snapshot?.clients){try{await freshSnapshotPromise}catch{}}
      if(snapshot?.clients)return{ok:true,clients:filterClients(snapshot.clients,data.q)};
      const j=await postPlain(FAST_EDGE,{pin:PIN,action:'clients',...data});if(!j?.ok)throw Error(j?.error||'Ошибка клиентов');return j;
    }
    const j=await postPlain(SERVICES_EDGE,{pin:PIN,action,...data});if(!j?.ok)throw Error(j?.error||'Ошибка');return j;
  };
  rpc=async function(name,body={}){
    if(name==='master_app_bootstrap'){
      if(!snapshot){await freshSnapshotPromise}
      if(!snapshot)throw Error('Ошибка загрузки');
      return{services:snapshot.services||[],settings:snapshot.settings||{},categories:snapshot.categories||[]};
    }
    if(name==='master_app_calendar'){
      if(!snapshot){try{await freshSnapshotPromise}catch{}}
      if(inSnapshot(body.p_from,body.p_to))return snapshot.calendar;
      const j=await postPlain(FAST_EDGE,{pin:PIN,action:'calendar',from:body.p_from,to:body.p_to});if(!j?.ok)throw Error(j?.error||'Ошибка календаря');return j.data;
    }
    if(name==='master_app_set_availability'){
      const j=await postPlain(FAST_EDGE,{pin:PIN,action:'availability',slot:body.p_slot,value:body.p_value});if(!j?.ok)throw Error(j?.error||'Ошибка доступности');
      try{localStorage.removeItem(SNAP_KEY)}catch{};snapshot=null;return true;
    }
    return originalRpc(name,body);
  };

  window.addEventListener('slotelly:snapshot',setBadgeFromSnapshot);
  if(typeof window.slotellyLegacyInit==='function')window.slotellyLegacyInit().catch(e=>console.error('Slotelly init:',e));
})();
