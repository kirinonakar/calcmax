import test from 'node:test';
import assert from 'node:assert/strict';
import {refreshOfflineCache} from '../cache-maintenance.js';

test('startup removes CalcMax caches and workers while preserving current and unrelated site data',async()=>{
  const deleted=[],unregistered=[],registered=[];
  const registrations=['https://example.test/calcmax/','https://example.test/symvacas/','https://example.test/other/']
    .map(scope=>({scope,unregister:async()=>unregistered.push(scope)}));
  const options={pageURL:'https://example.test/symvacas/',
    cacheStorage:{keys:async()=>['calcmax-static-40f3b172fa96ee22','symvacas-static-current','other-cache'],delete:async key=>deleted.push(key)},
    serviceWorker:{getRegistrations:async()=>registrations,register:async(...args)=>registered.push(args)}};
  await refreshOfflineCache(options);
  assert.deepEqual(deleted,['calcmax-static-40f3b172fa96ee22']);
  assert.deepEqual(unregistered,['https://example.test/calcmax/']);
  assert.deepEqual(registered,[['https://example.test/symvacas/sw.js',{scope:'https://example.test/symvacas/',updateViaCache:'none'}]]);
  options.serviceWorker.getRegistrations=async()=>[];registered.length=0;
  await refreshOfflineCache(options);
  assert.deepEqual(registered,[],'new visitors do not download offline engine packages before WASM starts');
});

test('cache cleanup works without service workers and does nothing for file URLs',async()=>{
  const deleted=[],cacheStorage={keys:async()=>['calcmax-static-old'],delete:async key=>deleted.push(key)};
  await refreshOfflineCache({pageURL:'https://example.test/symvacas/index.html',cacheStorage,serviceWorker:null});
  assert.deepEqual(deleted,['calcmax-static-old']);deleted.length=0;
  await refreshOfflineCache({pageURL:'file:///symvacas/index.html',cacheStorage,serviceWorker:null});
  assert.deepEqual(deleted,[]);
});

test('failed offline worker refresh still deletes legacy caches after unregistering their worker',async()=>{
  const calls=[];
  await assert.rejects(refreshOfflineCache({pageURL:'https://example.test/symvacas/',
    cacheStorage:{keys:async()=>['calcmax-static-old'],delete:async()=>calls.push('delete')},
    serviceWorker:{getRegistrations:async()=>[
      {scope:'https://example.test/calcmax/',unregister:async()=>calls.push('unregister')},
      {scope:'https://example.test/symvacas/'}],register:async()=>{throw new Error('offline');}}}),/offline/);
  assert.deepEqual(calls,['unregister','delete']);
});
