(()=>{
  if(!('serviceWorker' in navigator)) return;
  let refreshing=false;
  navigator.serviceWorker.addEventListener('controllerchange',()=>{
    if(refreshing) return;
    refreshing=true;
    window.location.reload();
  });
  window.addEventListener('load',async()=>{
    try{
      const reg=await navigator.serviceWorker.register('./sw.js',{updateViaCache:'none'});
      await reg.update();
      setInterval(()=>reg.update().catch(()=>{}),15*60*1000);
    }catch(e){
      console.warn('PWA registration failed',e);
    }
  });
})();
