// Slotelly fast network layer: avoids browser CORS preflight for Edge calls
// and routes the hottest calendar RPCs through one Edge endpoint.
(() => {
  const FAST_EDGE='https://acukaqoguzkrphauovhk.supabase.co/functions/v1/resnitsy-fast';
  const MASTER_EDGE='https://acukaqoguzkrphauovhk.supabase.co/functions/v1/resnitsy-master';
  const SERVICES_EDGE='https://acukaqoguzkrphauovhk.supabase.co/functions/v1/resnitsy-services';

  async function postPlain(url,payload){
    const r=await fetch(url,{method:'POST',headers:{'Content-Type':'text/plain;charset=UTF-8'},body:JSON.stringify(payload)});
    const text=await r.text(); let j;
    try{j=text?JSON.parse(text):null}catch{j=null}
    if(!r.ok) throw Error(j?.error||j?.message||text||`Ошибка сети ${r.status}`);
    return j;
  }

  // Existing code calls these globals. Keep the same return contracts.
  window.edge=async function(action,data={}){
    const j=await postPlain(MASTER_EDGE,{pin:PIN,action,...data});
    if(!j?.ok) throw Error(j?.error||'Ошибка');
    return j;
  };

  window.serviceApi=async function(action,data={}){
    const j=await postPlain(SERVICES_EDGE,{pin:PIN,action,...data});
    if(!j?.ok) throw Error(j?.error||'Ошибка');
    return j;
  };

  const originalRpc=window.rpc;
  window.rpc=async function(name,body={}){
    if(name==='master_app_calendar'){
      const j=await postPlain(FAST_EDGE,{pin:PIN,action:'calendar',from:body.p_from,to:body.p_to});
      if(!j?.ok) throw Error(j?.error||'Ошибка календаря');
      return j.data;
    }
    if(name==='master_app_set_availability'){
      const j=await postPlain(FAST_EDGE,{pin:PIN,action:'availability',slot:body.p_slot,value:body.p_value});
      if(!j?.ok) throw Error(j?.error||'Ошибка доступности');
      return true;
    }
    return originalRpc(name,body);
  };
})();
