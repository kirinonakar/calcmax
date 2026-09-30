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
    // A prior cache can contain the classic Worker bootstrap. Reload existing
    // app pages once after an upgrade so they cannot keep running that script.
    // pagehide saves their current drafts before navigation.
    if(previous.length)for(const client of await self.clients.matchAll({type:'window'})){
      if(client.url.startsWith(self.registration.scope))await client.navigate(client.url);
    }
  })());
});
self.addEventListener('fetch',event=>{
  if(event.request.method!=='GET'||new URL(event.request.url).origin!==self.location.origin)return;
  const url=new URL(event.request.url),shell=event.request.mode==='navigate'||/\.(?:html|css|js)$/.test(url.pathname)&&!url.pathname.includes('/vendor/');
  event.respondWith(caches.open(CACHE).then(async cache=>{
    if(shell)try{const response=await fetch(event.request);if(response.ok)return response;}catch{}
    return (await cache.match(event.request))||fetch(event.request);
  }));
});
