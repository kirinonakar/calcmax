import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import vm from 'node:vm';
import {setImmediate} from 'node:timers/promises';

function runtime(oldCaches=[],navigate=async()=>{}) {
  const handlers={},deleted=[],navigated=[],calls=[];
  const self={CALCMAX_CACHE:'calcmax-static-new',CALCMAX_ASSETS:['./','./worker.js'],location:{origin:'https://example.test'},registration:{scope:'https://example.test/calcmax/'},addEventListener:(name,handler)=>handlers[name]=handler,skipWaiting:async()=>calls.push('skipWaiting'),clients:{claim:async()=>calls.push('claim'),matchAll:async()=>[{url:'https://example.test/calcmax/',navigate:async url=>{navigated.push(url);return navigate(url);}},{url:'https://example.test/elsewhere/',navigate:async url=>{navigated.push(url);return navigate(url);}}]}};
  const cache={addAll:async assets=>calls.push(assets),match:async()=>({ok:true,source:'cached'})};
  const caches={keys:async()=>[...oldCaches,'calcmax-static-new','unrelated-cache'],delete:async key=>deleted.push(key),open:async()=>cache};
  const context={self,caches,URL,importScripts:()=>{},fetch:async()=>({ok:true,source:'network'})};
  vm.runInNewContext(readFileSync(new URL('../sw.js',import.meta.url),'utf8'),context);
  const dispatch=async(name,request)=>{let pending;handlers[name]({request,waitUntil:value=>pending=value,respondWith:value=>pending=value});return await pending;};
  return {context,dispatch,deleted,navigated,calls};
}
test('cache upgrade replaces old bootstrap and reloads only CalcMax without blocking activation',async()=>{
  const r=runtime(['calcmax-static-classic']);await r.dispatch('install');await r.dispatch('activate');
  assert.deepEqual(r.deleted,['calcmax-static-classic']);assert.deepEqual(r.navigated,['https://example.test/calcmax/']);
  assert.ok(r.calls.includes('skipWaiting'));assert.ok(r.calls.includes('claim'));
  const first=runtime();await first.dispatch('activate');assert.deepEqual(first.navigated,[]);
});

test('upgrade activation finishes while navigation waits for its fetch handler',async()=>{
  // Browsers hold fetch events until activate.waitUntil settles. A navigation
  // started during activation therefore cannot finish until activation does.
  let activated=false,navigationFinished=false,activation;
  const r=runtime(['calcmax-static-old'],async url=>{
    await activation;
    const response=await r.dispatch('fetch',{method:'GET',url,mode:'navigate'});
    assert.equal(response.source,'network');
    navigationFinished=true;
  });
  activation=r.dispatch('activate').then(()=>{activated=true;});
  await setImmediate();
  assert.equal(r.navigated.length,1,'the cached page is refreshed once');
  assert.equal(activated,true,'activation must not await a navigation that needs activation');
  await setImmediate();
  assert.equal(navigationFinished,true,'the new page can load without cancelling and refreshing');
});

test('a closed tab or cancelled upgrade navigation does not reject activation',async()=>{
  const r=runtime(['calcmax-static-old'],async()=>{throw new Error('Navigation cancelled');});
  await r.dispatch('activate');
  await setImmediate();
  assert.ok(r.calls.includes('claim'));
  assert.equal((await r.dispatch('fetch',{method:'GET',url:'https://example.test/calcmax/vendor/sympy.whl',mode:'cors'})).source,'cached');
});
test('updated shell comes from network; offline execution still uses cached assets',async()=>{
  const r=runtime(),shell={method:'GET',url:'https://example.test/calcmax/worker.js',mode:'cors'};
  assert.equal((await r.dispatch('fetch',shell)).source,'network');
  assert.equal((await r.dispatch('fetch',{...shell,url:'https://example.test/calcmax/vendor/sympy.whl',cache:'reload'})).source,'network','startup retries bypass a stale package cache');
  r.context.fetch=async()=>{throw new Error('offline');};
  assert.equal((await r.dispatch('fetch',shell)).source,'cached');
  assert.equal((await r.dispatch('fetch',{...shell,url:'https://example.test/calcmax/vendor/pyodide.asm.wasm'})).source,'cached');
});
