/* Send Telegram confirmation immediately after successful online booking. */
(function(){
  const originalFetch=window.fetch.bind(window);
  window.fetch=async function(input,init){
    const r=await originalFetch(input,init);
    try{
      const url=String(input||'');
      if(url.includes('/functions/v1/resnitsy-booking')&&String(init?.method||'GET').toUpperCase()==='POST'){
        const body=JSON.parse(String(init?.body||'{}'));
        if(body.action==='book'&&r.ok){
          const clone=r.clone(),data=await clone.json();
          if(data?.ok&&data?.id){
            originalFetch('https://acukaqoguzkrphauovhk.supabase.co/functions/v1/resnitsy-online-confirmation',{method:'POST',headers:{'Content-Type':'text/plain;charset=UTF-8'},body:JSON.stringify({appointmentId:data.id}),cache:'no-store'}).catch(()=>{});
          }
        }
      }
    }catch{}
    return r;
  };
})();