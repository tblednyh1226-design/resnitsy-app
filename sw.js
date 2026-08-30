const VERSION='slotelly-pwa-v1';
const CACHE=VERSION;
const SHELL=['./','./manifest.webmanifest','./app-icon.svg'];

self.addEventListener('install',event=>{
  self.skipWaiting();
  event.waitUntil(caches.open(CACHE).then(cache=>cache.addAll(SHELL)).catch(()=>{}));
});

self.addEventListener('activate',event=>{
  event.waitUntil((async()=>{
    const keys=await caches.keys();
    await Promise.all(keys.filter(k=>k!==CACHE).map(k=>caches.delete(k)));
    await self.clients.claim();
  })());
});

self.addEventListener('fetch',event=>{
  const req=event.request;
  if(req.method!=='GET') return;
  const url=new URL(req.url);
  if(url.origin!==location.origin) return;

  event.respondWith((async()=>{
    try{
      const fresh=await fetch(req,{cache:'no-store'});
      if(fresh && fresh.ok){
        const cache=await caches.open(CACHE);
        cache.put(req,fresh.clone()).catch(()=>{});
      }
      return fresh;
    }catch(err){
      const cached=await caches.match(req);
      if(cached) return cached;
      if(req.mode==='navigate'){
        const home=await caches.match('./');
        if(home) return home;
      }
      throw err;
    }
  })());
});
