(()=>{
  const BUILD='2026-08-30.2210';
  let deferredPrompt=null;
  const isStandalone=()=>window.matchMedia('(display-mode: standalone)').matches||window.navigator.standalone===true;

  async function hardRefreshIfNeeded(){
    try{
      const last=localStorage.getItem('slotelly_build');
      if(last===BUILD) return;
      localStorage.setItem('slotelly_build',BUILD);
      if('caches' in window){const keys=await caches.keys();await Promise.all(keys.filter(k=>k.startsWith('slotelly-pwa-')).map(k=>caches.delete(k)));}
      if('serviceWorker' in navigator){const regs=await navigator.serviceWorker.getRegistrations();await Promise.all(regs.map(async reg=>{try{await reg.update()}catch(e){}}));}
      if(!location.search.includes('slotelly_refresh='+BUILD)){
        const u=new URL(location.href);u.searchParams.set('slotelly_refresh',BUILD);location.replace(u.toString());
      }
    }catch(e){console.warn('Slotelly recovery failed',e)}
  }

  function ensureInstallButton(){
    if(isStandalone()||document.getElementById('slotellyInstallBtn')) return;
    const btn=document.createElement('button');btn.id='slotellyInstallBtn';btn.type='button';btn.textContent='Установить Slotelly';
    btn.style.cssText='position:fixed;left:50%;transform:translateX(-50%);bottom:76px;z-index:9999;border:0;border-radius:16px;padding:12px 18px;font:600 15px system-ui,-apple-system,BlinkMacSystemFont,"Segoe UI",sans-serif;background:#8f4f68;color:#fff;box-shadow:0 8px 24px rgba(0,0,0,.18);max-width:calc(100vw - 32px);white-space:nowrap;';
    btn.addEventListener('click',async()=>{if(deferredPrompt){deferredPrompt.prompt();try{await deferredPrompt.userChoice}catch(e){}deferredPrompt=null;btn.remove();return}const inApp=/wv|Telegram|Instagram|FBAN|FBAV/i.test(navigator.userAgent);alert(inApp?'Откройте эту страницу в Chrome через меню ⋮ → «Открыть в Chrome», затем нажмите «Установить Slotelly».':'В Chrome откройте меню ⋮ и выберите «Установить приложение» или «Добавить на главный экран».')});
    document.body.appendChild(btn);
  }

  window.addEventListener('beforeinstallprompt',e=>{e.preventDefault();deferredPrompt=e;ensureInstallButton()});
  window.addEventListener('appinstalled',()=>{deferredPrompt=null;document.getElementById('slotellyInstallBtn')?.remove()});
  window.addEventListener('load',()=>{
    ensureInstallButton();
    if(!('serviceWorker' in navigator)){hardRefreshIfNeeded();return}
    let refreshing=false;navigator.serviceWorker.addEventListener('controllerchange',()=>{if(refreshing)return;refreshing=true;window.location.reload()});
    (async()=>{try{const reg=await navigator.serviceWorker.register('./sw.js?v=4',{updateViaCache:'none'});await reg.update();await hardRefreshIfNeeded();setInterval(()=>reg.update().catch(()=>{}),5*60*1000)}catch(e){console.warn('PWA registration failed',e);await hardRefreshIfNeeded()}})();
  });
})();