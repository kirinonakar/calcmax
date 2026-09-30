/* All dependencies are same-origin static files. No special response headers. */
importScripts('./assets.js');
const CACHE=self.CALCMAX_CACHE;
self.addEventListener('install',event=>{
  event.waitUntil(caches.open(CACHE).then(cache=>cache.addAll(self.CALCMAX_ASSETS)).then(()=>self.skipWaiting()));
});
self.addEventListener('activate',event=>{
  event.waitUntil((async()=>{
    const previous=(await caches.keys()).filter(key=>key.startsWith('calcmax-static-')&&key!==CACHE);
    for(const key of previous)await caches.delete(key);
    await self.clients.claim();
    // Refresh old bootstraps once on upgrade; pagehide saves their drafts.
    // Do not await navigation inside activate.waitUntil: its fetch event is
    // held until activation finishes, so waiting here deadlocks page loading.
    // A closed tab or cancelled navigation must not fail activation either.
    if(previous.length)for(const client of await self.clients.matchAll({type:'window'})){
      if(client.url.startsWith(self.registration.scope))void client.navigate(client.url).catch(()=>{});
    }
  })());
});
self.addEventListener('fetch',event=>{
  if(event.request.method!=='GET'||new URL(event.request.url).origin!==self.location.origin)return;
  const url=new URL(event.request.url),shell=event.request.mode==='navigate'||/\.(?:html|css|js)$/.test(url.pathname)&&!url.pathname.includes('/vendor/');
  event.respondWith(caches.open(CACHE).then(async cache=>{
    if(shell||event.request.cache==='reload')try{const response=await fetch(event.request);if(response.ok)return response;}catch{}
    return (await cache.match(event.request))||fetch(event.request);
  }));
});
