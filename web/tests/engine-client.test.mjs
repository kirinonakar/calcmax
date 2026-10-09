import test from 'node:test';
import assert from 'node:assert/strict';
import {EngineClient} from '../engine-client.js';
import {createEngineUI} from '../engine-ui.js';
import {setLanguage} from '../i18n.js';

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
  t.mock.timers.enable({apis:['setTimeout','Date']});
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

test('a runtime error settles a calculation without treating it as cold startup',async t=>{
  const {engine,workers,statuses,tick}=runtime(t);
  workers[0].message({type:'ready'});
  const result=engine.execute({action:'calculate'});
  workers[0].onerror({message:'runtime failed'});
  assert.deepEqual(await result,{ok:false,error:'runtime failed'});
  assert.equal(engine.pending,null);assert.equal(engine.ready,false);assert.equal(statuses.at(-1),'runtime failed');
  tick(240000);assert.equal(workers.length,1);
});

test('calculations can finish after 20 seconds and the next request expires at 60 seconds',async t=>{
  const {engine,workers,tick}=runtime(t);
  workers[0].message({type:'ready'});
  const slow=engine.execute({action:'evaluate'});
  tick(25000);assert.equal(workers.length,1);assert.ok(engine.pending);
  workers[0].message({type:'result',id:workers[0].request.id,result:{ok:true,exact:'2'}});
  assert.equal((await slow).exact,'2');
  const stalled=engine.execute({action:'evaluate'});
  tick(59999);assert.equal(workers.length,1);assert.ok(engine.pending);
  tick(1);assert.equal(workers[0].terminated,true);assert.equal(workers.length,2);
  assert.deepEqual(await stalled,{ok:false,error:'계산 시간이 60초를 초과했습니다.'});
  workers[1].message({type:'ready'});
  const next=engine.execute({action:'evaluate'});
  workers[1].message({type:'result',id:workers[1].request.id,result:{ok:true}});
  assert.equal((await next).ok,true);
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

test('cancelled foreground work waiting for a preview cannot run on the restarted worker',async t=>{
  const {engine,workers}=runtime(t);
  workers[0].message({type:'ready'});
  const preview=engine.execute({},{background:true});
  const fit=engine.execute({action:'regression'});
  engine.cancel();
  // The replacement may be ready before the preview promise continuation runs.
  workers[1].message({type:'ready'});
  assert.equal((await preview).ok,false);
  assert.equal((await fit).ok,false);
  assert.equal(workers[1].request,undefined);
  const next=engine.execute({action:'regression'});
  workers[1].message({type:'result',id:workers[1].request.id,result:{ok:true}});
  assert.equal((await next).ok,true);
});

test('Python input pauses the deadline and resumes with the remaining execution budget',async t=>{
  const {engine,workers,tick}=runtime(t);
  workers[0].message({type:'ready'});
  let answer;
  const result=engine.execute({action:'python'},{onInput:()=>new Promise(resolve=>answer=resolve)});
  const id=workers[0].request.id;
  tick(5000);
  workers[0].message({type:'input',id,inputId:1,prompt:'a=',output:'before\n'});
  tick(60000);assert.equal(workers.length,1);assert.ok(engine.pending);
  answer('');await Promise.resolve();
  assert.deepEqual(workers[0].request,{type:'input',id,inputId:1,value:''});
  tick(54999);assert.equal(workers.length,1);
  tick(1);assert.equal(workers.length,2);
  assert.match((await result).error,/60/);
});

test('cancel and stale input replies cannot resume a replacement worker',async t=>{
  const {engine,workers}=runtime(t);
  workers[0].message({type:'ready'});
  let answer,signal;
  const result=engine.execute({action:'python'},{onInput:request=>{signal=request.signal;return new Promise(resolve=>answer=resolve);}});
  const id=workers[0].request.id;
  workers[0].message({type:'input',id:999,inputId:1});assert.equal(signal,undefined);
  workers[0].message({type:'input',id,inputId:1});
  engine.cancel();assert.equal(signal.aborted,true);
  workers[1].message({type:'ready'});
  answer('late');await Promise.resolve();
  assert.equal(workers[1].request,undefined);assert.equal((await result).ok,false);
});

test('solve and analysis buttons become Cancel after one second and recover after completion or cancellation',async t=>{
  const {engine,workers,tick}=runtime(t);
  class Button extends EventTarget {
    dataset={};style={};disabled=false;hidden=false;textContent='';
    click(){if(!this.disabled)this.dispatchEvent(new Event('click',{cancelable:true}));}
  }
  const actions=new Map(['equation','statistics','statistics-advanced','regression'].map(context=>[context,new Button()]));
  const elements=new Map(['stop','retry','status','mode'].map(id=>[id,new Button()]));
  elements.get('mode').value='scientific';
  const original=Object.getOwnPropertyDescriptor(globalThis,'document');
  globalThis.document={documentElement:{dataset:{}},getElementById:id=>elements.get(id),
    querySelector:selector=>actions.get(selector.match(/data-run="([^"]+)"/)[1])};
  let previewsCancelled=0,analysisRuns=0;
  const ui=createEngineUI({engine,onChange(){},onReady(){},cancelPreview(){previewsCancelled++;}});
  for(const button of actions.values())button.addEventListener('click',()=>analysisRuns++);
  t.after(()=>{ui.dispose();setLanguage('en');if(original)Object.defineProperty(globalThis,'document',original);else delete globalThis.document;});
  // A normal completed calculation leaves no delayed Cancel label behind.
  engine.ready=true;
  let result=engine.execute({},{context:'equation'});
  tick(999);assert.equal(actions.get('equation').textContent,'Solve');assert.equal(actions.get('equation').disabled,true);
  workers[0].message({type:'result',id:workers[0].request.id,result:{ok:true}});
  await result;tick(1);assert.equal(actions.get('equation').textContent,'Solve');
  for(const context of actions.keys()){
    elements.get('mode').value=context==='equation'?'equation':'statistics';
    const worker=workers.at(-1),button=actions.get(context),label=context==='equation'?'Solve':'Analyze';
    result=engine.execute({},{context});
    tick(999);assert.equal(button.textContent,label);assert.equal(button.disabled,true);
    tick(1);assert.equal(button.textContent,'Cancel');assert.equal(button.disabled,false);
    assert.equal(elements.get('stop').style.visibility,'hidden','no second cancel control appears');
    assert.equal([...actions.values()].filter(action=>action.dataset.cancelCalculation==='true').length,1);
    button.click();assert.equal(worker.terminated,true);assert.equal((await result).ok,false);
    assert.equal(button.textContent,label);assert.equal(button.disabled,true,'wait for engine restart');
    worker.message({type:'result',id:worker.request.id,result:{ok:true,exact:'stale'}});
    assert.equal(engine.pending,null,'late cancelled results are ignored');
    engine.ready=true;ui.updateStopButton();
    const next=engine.execute({},{context});
    tick(1000);assert.equal(button.textContent,'Cancel');
    const replacement=workers.at(-1);
    replacement.message({type:'result',id:replacement.request.id,result:{ok:true,exact:'2'}});
    assert.equal((await next).exact,'2');assert.equal(button.textContent,label);assert.equal(button.disabled,false);
  }
  assert.equal(analysisRuns,0,'Cancel must not trigger the original analysis click');assert.equal(previewsCancelled,4);
  setLanguage('ko');result=engine.execute({},{context:'equation'});tick(1000);
  assert.equal(actions.get('equation').textContent,'취소');elements.get('stop').onclick();await result;
  assert.equal(actions.get('equation').textContent,'풀기');
  engine.ready=true;elements.get('mode').value='statistics';
  result=engine.execute({});tick(1000);
  assert.equal(elements.get('stop').style.visibility,'hidden');
  elements.get('mode').value='scientific';ui.updateStopButton();
  assert.equal(elements.get('stop').style.visibility,'');assert.equal(elements.get('stop').disabled,false);
  elements.get('stop').onclick();await result;
});


test('unlimited requests survive the execution deadline and can still be cancelled',async t=>{
  const {engine,workers,tick}=runtime(t);
  workers[0].message({type:'ready'});
  const result=engine.execute({tree:{kind:'number',value:'2'},removeComputationLimit:true});
  tick(600000);
  assert.ok(engine.pending);assert.equal(workers.length,1);
  engine.cancel();
  assert.equal((await result).ok,false);assert.equal(workers[0].terminated,true);
  workers[1].message({type:'ready'});
  const limited=engine.execute({});tick(60000);
  assert.equal((await limited).ok,false);assert.equal(workers[1].terminated,true);
});

test('unlimited Python input resumes without reinstalling an execution timer',async t=>{
  const {engine,workers,tick}=runtime(t);
  workers[0].message({type:'ready'});
  const result=engine.execute({action:'python',removeComputationLimit:true},{onInput:async()=> 'answer'});
  workers[0].message({type:'input',id:engine.pending.id,inputId:1});
  await Promise.resolve();await Promise.resolve();
  tick(600000);
  assert.ok(engine.pending);assert.equal(workers.length,1);
  workers[0].message({type:'result',id:engine.pending.id,result:{ok:true}});
  assert.equal((await result).ok,true);
});
