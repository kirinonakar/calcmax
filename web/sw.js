/* All dependencies are same-origin static files. No special response headers. */
importScripts('./assets.js');
const CACHE=self.SYMVACAS_CACHE;
function isShell(request) {
  const url=new URL(request.url);
  return request.mode==='navigate'||/\.(?:html|css|js)$/.test(url.pathname)&&!url.pathname.includes('/vendor/');
}
self.addEventListener('install',event=>{
  const assets=self.SYMVACAS_ASSETS.map(path=>{
    const request=new Request(new URL(path,self.registration.scope));
    return isShell(request)||path==='./'?new Request(request,{cache:'reload'}):request;
  });
  event.waitUntil(caches.open(CACHE).then(cache=>cache.addAll(assets)).then(()=>self.skipWaiting()));
});
self.addEventListener('activate',event=>{
  event.waitUntil((async()=>{
    const previous=(await caches.keys()).filter(key=>key.startsWith('calcmax-static-')||key.startsWith('symvacas-static-')&&key!==CACHE);
    for(const key of previous)await caches.delete(key);
    await self.clients.claim();
    // Keep running pages intact. Existing workers can update during bootstrap;
    // the next navigation fetches the current shell from the network.
  })());
});
self.addEventListener('fetch',event=>{
  if(event.request.method!=='GET'||new URL(event.request.url).origin!==self.location.origin)return;
  const shell=isShell(event.request);
  event.respondWith(caches.open(CACHE).then(async cache=>{
    if(shell||event.request.cache==='reload')try{const response=await fetch(event.request,{cache:'reload'});if(response.ok)return response;}catch{}
    return (await cache.match(event.request))||fetch(event.request);
  }));
});
