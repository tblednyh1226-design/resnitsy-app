// Slotelly fast network layer v2: override the actual global bindings used by legacy scripts.
(() => {
  const FAST_EDGE='https://acukaqoguzkrphauovhk.supabase.co/functions/v1/resnitsy-fast';
  const MASTER_EDGE='https://acukaqoguzkrphauovhk.supabase.co/functions/v1/resnitsy-master';
  const SERVICES_EDGE='https://acukaqoguzkrphauovhk.supabase.co/functions/v1/resnitsy-services';

  async function postPlain(url,payload){
    const ctl=new AbortController();
    const timer=setTimeout(()=>ctl.abort(),12000);
    try{
      const r=await fetch(url,{method:'POST',headers:{'Content-Type':'text/plain;charset=UTF-8'},body:JSON.stringify(payload),signal:ctl.signal,cache:'no-store'});
      const text=await r.text(); let j;
      try{j=text?JSON.parse(text):null}catch{j=null}
      if(!r.ok) throw Error(j?.error||j?.message||text||`Ошибка сети ${r.status}`);
      return j;
    }catch(e){
      if(e?.name==='AbortError') throw Error('Сервер не ответил за 12 секунд');
      throw e;
    }finally{clearTimeout(timer)}
  }

  const originalRpc=rpc;

  edge=async function(action,data={}){
    const j=await postPlain(MASTER_EDGE,{pin:PIN,action,...data});
    if(!j?.ok) throw Error(j?.error||'Ошибка');
    return j;
  };

  serviceApi=async function(action,data={}){
    const j=await postPlain(SERVICES_EDGE,{pin:PIN,action,...data});
    if(!j?.ok) throw Error(j?.error||'Ошибка');
    return j;
  };

  rpc=async function(name,body={}){
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

  // One lightweight probe on each fresh app start. It gives us an exact server-side trace.
  postPlain(FAST_EDGE,{pin:PIN,action:'ping'}).catch(()=>{});
})();
