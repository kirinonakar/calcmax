// Remove the former application's offline assets without touching saved data.
export async function refreshOfflineCache({
  cacheStorage=globalThis.caches,serviceWorker=globalThis.navigator?.serviceWorker,
  pageURL=globalThis.location?.href
}={}) {
  if(!pageURL)return;
  const base=new URL('./',pageURL);
  if(!['http:','https:'].includes(base.protocol))return;
  try {
    if(serviceWorker){
      const registrations=await serviceWorker.getRegistrations();
      for(const registration of registrations){
        const scope=new URL(registration.scope);
        if(scope.origin===base.origin&&/\/calcmax(?:\/|$)/i.test(scope.pathname))
          await registration.unregister();
      }
      // Existing installations must update even if stale modules prevent WASM
      // startup. New installations still precache only after the engine is ready.
      if(registrations.some(registration=>registration.scope===base.href))
        await serviceWorker.register(new URL('sw.js',base).href,{scope:base.href,updateViaCache:'none'});
    }
  } finally {
    // Unregister the producer before deleting its cache. Also clean up when
    // refreshing the worker fails offline.
    if(cacheStorage)for(const key of await cacheStorage.keys())
      if(key.startsWith('calcmax-static-'))await cacheStorage.delete(key);
  }
}
