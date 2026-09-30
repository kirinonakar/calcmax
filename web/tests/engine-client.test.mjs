import test from 'node:test';
import assert from 'node:assert/strict';
import {EngineClient} from '../engine-client.js';

function runtime(t) {
  const workers=[],statuses=[];
  const original=Object.getOwnPropertyDescriptor(globalThis,'Worker');
  class Worker {
    constructor(){workers.push(this);this.terminated=false;}
    terminate(){this.terminated=true;}
    postMessage(data){this.request=data;}
    message(data){this.onmessage({data});}
  }
  globalThis.Worker=Worker;
  t.mock.timers.enable({apis:['setTimeout']});
  t.after(()=>{
    if(original)Object.defineProperty(globalThis,'Worker',original);
    else delete globalThis.Worker;
  });
  const engine=new EngineClient();
  engine.addEventListener('status',event=>statuses.push(event.detail));
  return {engine,workers,statuses,tick:ms=>t.mock.timers.tick(ms)};
}

test('stalled cold startup retries once and becomes ready without a page refresh',async t=>{
  const {engine,workers,statuses,tick}=runtime(t);
  workers[0].message({type:'status',message:'SymPy 계산 엔진 로딩…'});
  tick(119999);assert.equal(workers.length,1);
  tick(1);assert.equal(workers.length,2);assert.equal(workers[0].terminated,true);
  assert.equal(statuses.at(-1),'계산 엔진 로딩을 다시 시도합니다…');
  workers[0].message({type:'ready'});assert.equal(engine.ready,false,'queued messages from the old worker are ignored');
  workers[0].onerror({message:'stale error'});assert.equal(workers.length,2);
  workers[1].message({type:'ready'});assert.equal(engine.ready,true);
  tick(120000);assert.equal(workers.length,2);assert.equal(engine.ready,true,'readiness clears the startup timeout');
  const result=engine.execute({action:'calculate'});
  workers[1].message({type:'result',id:workers[1].request.id,result:{ok:true,exact:'2'}});
  assert.deepEqual(await result,{ok:true,exact:'2'});
});

test('two stalled attempts end with a retryable error instead of an infinite loop',t=>{
  const {engine,workers,statuses,tick}=runtime(t);
  tick(120000);tick(120000);
  assert.equal(workers.length,2);assert.equal(workers[1].terminated,true);assert.equal(engine.ready,false);
  assert.equal(statuses.at(-1),'계산 엔진 로딩 시간이 초과되었습니다. 다시 로딩을 눌러 주세요.');
  tick(120000);assert.equal(workers.length,2);
  engine.cancel();assert.equal(workers.length,3,'manual retry remains available after startup failure');
  workers[2].message({type:'ready'});assert.equal(engine.ready,true);
});

for(const kind of ['fatal','error'])test(`${kind} during startup retries once and then reports the failure`,t=>{
  const {engine,workers,statuses,tick}=runtime(t);
  const fail=worker=>kind==='fatal'?worker.message({type:'fatal',error:'download failed'}):worker.onerror({message:'download failed'});
  fail(workers[0]);assert.equal(workers.length,2);assert.equal(workers[0].terminated,true);
  fail(workers[1]);assert.equal(engine.ready,false);assert.equal(statuses.at(-1),'download failed');
  tick(240000);assert.equal(workers.length,2,'failed workers leave no live startup timers');
});

test('manual retry replaces the startup timer and ignores the cancelled worker',t=>{
  const {engine,workers,tick}=runtime(t);
  tick(60000);engine.cancel();tick(60000);
  assert.equal(workers.length,2,'the original timeout cannot restart the replacement worker');
  workers[0].message({type:'ready'});assert.equal(engine.ready,false);
  workers[1].message({type:'ready'});tick(240000);
  assert.equal(engine.ready,true);assert.equal(workers.length,2);
});

test('a runtime error settles a calculation without treating it as cold startup',async t=>{
  const {engine,workers,statuses,tick}=runtime(t);
  workers[0].message({type:'ready'});
  const result=engine.execute({action:'calculate'});
  workers[0].onerror({message:'runtime failed'});
  assert.deepEqual(await result,{ok:false,error:'runtime failed'});
  assert.equal(engine.pending,null);assert.equal(engine.ready,false);assert.equal(statuses.at(-1),'runtime failed');
  tick(240000);assert.equal(workers.length,1);
});

test('background previews leave editing unlocked and foreground work waits for them',async t=>{
  const {engine,workers}=runtime(t),busy=[];
  engine.addEventListener('busy',event=>busy.push(event.detail));
  workers[0].message({type:'ready'});
  const preview=engine.execute({tree:{kind:'number',value:'1'}},{background:true});
  const previewId=workers[0].request.id;
  assert.deepEqual(busy,[],'a preview does not lock the keypad');
  const commit=engine.execute({tree:{kind:'number',value:'2'}});
  assert.deepEqual(busy,[true],'explicit work locks controls while waiting');
  assert.equal(workers[0].request.id,previewId,'requests never overlap in the worker');
  workers[0].message({type:'result',id:previewId,result:{ok:true,exact:'1'}});
  assert.equal((await preview).exact,'1');
  await Promise.resolve();
  assert.notEqual(workers[0].request.id,previewId);
  workers[0].message({type:'result',id:workers[0].request.id,result:{ok:true,exact:'2'}});
  assert.equal((await commit).exact,'2');
  assert.equal(busy.at(-1),false);
});

test('completed previews and cancelled background work never leave controls busy',async t=>{
  const {engine,workers}=runtime(t),busy=[];
  engine.addEventListener('busy',event=>busy.push(event.detail));
  workers[0].message({type:'ready'});
  const preview=engine.execute({},{background:true});
  workers[0].message({type:'result',id:workers[0].request.id,result:{ok:true}});
  await preview;assert.deepEqual(busy,[]);
  const next=engine.execute({},{background:true});
  const commit=engine.execute({});engine.cancel();
  assert.equal((await next).ok,false);assert.equal((await commit).ok,false);
  assert.equal(busy.at(-1),false);assert.equal(engine.pending,null);
});
