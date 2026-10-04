import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import vm from 'node:vm';

function runtime(oldCaches=[],navigate=async()=>{}) {
  const handlers={},deleted=[],navigated=[],calls=[];
  const self={SYMVACAS_CACHE:'symvacas-static-new',SYMVACAS_ASSETS:['./','./worker.js'],location:{origin:'https://example.test'},registration:{scope:'https://example.test/symvacas/'},addEventListener:(name,handler)=>handlers[name]=handler,skipWaiting:async()=>calls.push('skipWaiting'),clients:{claim:async()=>calls.push('claim'),matchAll:async()=>[{url:'https://example.test/symvacas/',navigate:async url=>{navigated.push(url);return navigate(url);}},{url:'https://example.test/elsewhere/',navigate:async url=>{navigated.push(url);return navigate(url);}}]}};
  const cache={addAll:async assets=>calls.push(assets),match:async()=>({ok:true,source:'cached'})};
  const caches={keys:async()=>[...oldCaches,'symvacas-static-new','unrelated-cache'],delete:async key=>deleted.push(key),open:async()=>cache};
  const context={self,caches,URL,importScripts:()=>{},fetch:async()=>({ok:true,source:'network'})};
  vm.runInNewContext(readFileSync(new URL('../sw.js',import.meta.url),'utf8'),context);
  const dispatch=async(name,request)=>{let pending;handlers[name]({request,waitUntil:value=>pending=value,respondWith:value=>pending=value});return await pending;};
  return {context,dispatch,deleted,navigated,calls};
}
test('cache upgrade replaces old offline assets without reloading the ready page',async()=>{
  const r=runtime(['symvacas-static-classic']);await r.dispatch('install');await r.dispatch('activate');
  assert.deepEqual(r.deleted,['symvacas-static-classic']);assert.deepEqual(r.navigated,[]);
  assert.ok(r.calls.includes('skipWaiting'));assert.ok(r.calls.includes('claim'));
  const first=runtime();await first.dispatch('activate');assert.deepEqual(first.navigated,[]);
});

test('updated shell comes from network; offline execution still uses cached assets',async()=>{
  const r=runtime(),shell={method:'GET',url:'https://example.test/symvacas/worker.js',mode:'cors'};
  assert.equal((await r.dispatch('fetch',shell)).source,'network');
  assert.equal((await r.dispatch('fetch',{...shell,url:'https://example.test/symvacas/vendor/sympy.whl',cache:'reload'})).source,'network','startup retries bypass a stale package cache');
  r.context.fetch=async()=>{throw new Error('offline');};
  assert.equal((await r.dispatch('fetch',shell)).source,'cached');
  assert.equal((await r.dispatch('fetch',{...shell,url:'https://example.test/symvacas/vendor/pyodide.asm.wasm'})).source,'cached');
});
