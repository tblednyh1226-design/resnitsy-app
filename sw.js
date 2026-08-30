const VERSION='slotelly-pwa-v2';
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

  // HTML/navigation: try fresh briefly, then instantly fall back to cache.
  if(req.mode==='navigate'){
    event.respondWith((async()=>{
      const cached=await caches.match(req) || await caches.match('./');
      try{
        const fresh=await Promise.race([
          fetch(req,{cache:'no-store'}),
          new Promise((_,reject)=>setTimeout(()=>reject(new Error('network timeout')),1800))
        ]);
        if(fresh && fresh.ok){
          const cache=await caches.open(CACHE);
          cache.put(req,fresh.clone()).catch(()=>{});
          return fresh;
        }
      }catch(e){}
      if(cached) return cached;
      return fetch(req);
    })());
    return;
  }

  // Static JS/CSS/images: show cached file immediately and refresh it in background.
  event.respondWith((async()=>{
    const cached=await caches.match(req);
    const network=fetch(req,{cache:'no-store'}).then(async fresh=>{
      if(fresh && fresh.ok){
        const cache=await caches.open(CACHE);
        cache.put(req,fresh.clone()).catch(()=>{});
      }
      return fresh;
    }).catch(()=>null);
    if(cached){
      event.waitUntil(network);
      return cached;
    }
    const fresh=await network;
    if(fresh) return fresh;
    throw new Error('offline');
  })());
});
