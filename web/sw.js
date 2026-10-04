/* All dependencies are same-origin static files. No special response headers. */
importScripts('./assets.js');
const CACHE=self.SYMVACAS_CACHE;
self.addEventListener('install',event=>{
  event.waitUntil(caches.open(CACHE).then(cache=>cache.addAll(self.SYMVACAS_ASSETS)).then(()=>self.skipWaiting()));
});
self.addEventListener('activate',event=>{
  event.waitUntil((async()=>{
    const previous=(await caches.keys()).filter(key=>key.startsWith('symvacas-static-')&&key!==CACHE);
    for(const key of previous)await caches.delete(key);
    await self.clients.claim();
    // Registration happens after WASM/SymPy is ready. Keep that running page
    // intact; the next navigation fetches the current shell from the network.
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
