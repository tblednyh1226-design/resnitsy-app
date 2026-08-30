// Slotelly fast network layer v5: one startup snapshot, local reads, controlled legacy init.
(() => {
  const FAST_EDGE='https://acukaqoguzkrphauovhk.supabase.co/functions/v1/resnitsy-fast';
  const MASTER_EDGE='https://acukaqoguzkrphauovhk.supabase.co/functions/v1/resnitsy-master';
  const SERVICES_EDGE='https://acukaqoguzkrphauovhk.supabase.co/functions/v1/resnitsy-services';
  let snapshot=null;

  const sleep=ms=>new Promise(r=>setTimeout(r,ms));
  async function postPlain(url,payload,{tries=2,timeout=9000}={}){
    let last;
    for(let attempt=1;attempt<=tries;attempt++){
      const ctl=new AbortController(),timer=setTimeout(()=>ctl.abort(),timeout);
      try{
        const r=await fetch(url,{method:'POST',headers:{'Content-Type':'text/plain;charset=UTF-8'},body:JSON.stringify(payload),signal:ctl.signal,cache:'no-store'});
        const text=await r.text();let j;try{j=text?JSON.parse(text):null}catch{j=null}
        if(!r.ok)throw Error(j?.error||j?.message||text||`Ошибка сети ${r.status}`);
        return j;
      }catch(e){last=e;if(attempt<tries)await sleep(450*attempt)}finally{clearTimeout(timer)}
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

  const originalRpc=rpc;
  edge=async function(action,data={}){const j=await postPlain(MASTER_EDGE,{pin:PIN,action,...data});if(!j?.ok)throw Error(j?.error||'Ошибка');return j};
  serviceApi=async function(action,data={}){
    if(action==='clients'){
      if(snapshot?.clients)return{ok:true,clients:filterClients(snapshot.clients,data.q)};
      const j=await postPlain(FAST_EDGE,{pin:PIN,action:'clients',...data});if(!j?.ok)throw Error(j?.error||'Ошибка клиентов');return j;
    }
    const j=await postPlain(SERVICES_EDGE,{pin:PIN,action,...data});if(!j?.ok)throw Error(j?.error||'Ошибка');return j;
  };
  rpc=async function(name,body={}){
    if(name==='master_app_bootstrap'){
      if(snapshot)return{services:snapshot.services||[],settings:snapshot.settings||{},categories:snapshot.categories||[]};
      const j=await snapshotPromise;if(!j?.ok)throw Error(j?.error||'Ошибка загрузки');return{services:j.services||[],settings:j.settings||{},categories:j.categories||[]};
    }
    if(name==='master_app_calendar'){
      if(inSnapshot(body.p_from,body.p_to))return snapshot.calendar;
      const j=await postPlain(FAST_EDGE,{pin:PIN,action:'calendar',from:body.p_from,to:body.p_to});if(!j?.ok)throw Error(j?.error||'Ошибка календаря');return j.data;
    }
    if(name==='master_app_set_availability'){
      const j=await postPlain(FAST_EDGE,{pin:PIN,action:'availability',slot:body.p_slot,value:body.p_value});if(!j?.ok)throw Error(j?.error||'Ошибка доступности');snapshot=null;return true;
    }
    return originalRpc(name,body);
  };

  const snapStart=weekStart||monday(focus||todayStr()),snapEnd=add(snapStart,7);
  const snapFrom=new Date(snapStart+'T00:00:00+03:00').toISOString(),snapTo=new Date(snapEnd+'T00:00:00+03:00').toISOString();
  const snapshotPromise=postPlain(FAST_EDGE,{pin:PIN,action:'snapshot',from:snapFrom,to:snapTo},{tries:2,timeout:10000}).then(j=>{
    if(!j?.ok)throw Error(j?.error||'Ошибка запуска');snapshot=j;window.SLOTELLY_SNAPSHOT=j;setBadgeFromSnapshot();window.dispatchEvent(new CustomEvent('slotelly:snapshot',{detail:j}));return j;
  });
  window.SLOTELLY_READY=snapshotPromise;
  window.addEventListener('slotelly:snapshot',setBadgeFromSnapshot);

  // The build removes the old eager init. Start it only after overrides are installed.
  if(typeof window.slotellyLegacyInit==='function')window.slotellyLegacyInit().catch(e=>console.error('Slotelly init:',e));
})();
