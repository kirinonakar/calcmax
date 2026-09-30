import test from 'node:test';
import assert from 'node:assert/strict';
import {fetchEngineAsset} from '../engine-fetch.js';

test('startup retries a stalled response body and bypasses the HTTP cache',async()=>{
  const requests=[];
  const response=await fetchEngineAsset(async(_,options)=>{
    requests.push(options);
    return requests.length===1?{ok:true,status:200,arrayBuffer:()=>new Promise(()=>{})}:new Response('wheel');
  },'sympy.whl',{},15);
  assert.equal(await response.text(),'wheel');
  assert.equal(requests.length,2);assert.equal(requests[0].signal.aborted,true);assert.equal(requests[1].cache,'reload');
});
test('a download that never resolves fails after two bounded attempts',async()=>{
  let requests=0;
  await assert.rejects(fetchEngineAsset(()=>{requests++;return new Promise(()=>{});},'engine.zip',{},10),/timed out/);
  assert.equal(requests,2);
});
test('missing assets are reported rather than passed to the interpreter',async()=>{
  await assert.rejects(fetchEngineAsset(async()=>new Response('missing',{status:404}),'engine.zip'),/404/);
});
