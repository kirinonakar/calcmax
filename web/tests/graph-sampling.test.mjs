import test from 'node:test';
import assert from 'node:assert/strict';
import {graphSamplingInterval,graphPreviewRequest,graphSamplingIdentity} from '../graph-sampling.js';

function clock(){
  let now=0,id=0;const timers=new Map();
  return {
    setTimer(callback,delay){const key=++id;timers.set(key,{callback,at:now+delay});return key;},
    clearTimer(key){timers.delete(key);},
    advance(ms){const end=now+ms;while(true){const entry=[...timers].sort((a,b)=>a[1].at-b[1].at)[0];if(!entry||entry[1].at>end)break;now=entry[1].at;timers.delete(entry[0]);entry[1].callback();}now=end;},
    get now(){return now;}
  };
}

test('continuous dragging samples every 80ms instead of waiting for release',()=>{
  const time=clock(),samples=[];let view=0;
  const sampler=graphSamplingInterval(()=>samples.push({at:time.now,view}),time);
  for(let i=0;i<30;i++){view=i;sampler.schedule();time.advance(16);}
  assert.deepEqual(samples.map(sample=>sample.at),[80,160,240,320,400,480]);
  assert.deepEqual(samples.map(sample=>sample.view),[4,9,14,19,24,29]);
  sampler.schedule();sampler.cancel();time.advance(100);
  assert.equal(samples.length,6);
  sampler.schedule();time.advance(80);assert.equal(samples.length,7);
});

const request={action:'graph',graphKind:'cartesian',trees:[{kind:'symbol',value:'x'}],min:-10,max:10,xMin:-10,xMax:10,yMin:-5,yMax:5,surfaceYMin:-5,surfaceYMax:5,samples:500,parameters:{a:1},derivativeSelected:0,precision:15};

test('preview fills a margin without changing the visible bounds or source request',()=>{
  const preview=graphPreviewRequest(request);
  assert.deepEqual([preview.min,preview.max,preview.xMin,preview.xMax,preview.yMin,preview.yMax],[-13,13,-13,13,-6.5,6.5]);
  assert.equal(preview.samples,200);assert.equal(request.min,-10);assert.equal(request.samples,500);
  assert.equal(graphSamplingIdentity(preview),graphSamplingIdentity(request));
  assert.equal(graphSamplingIdentity({...request,min:20,max:40,xMin:20,xMax:40,yMin:0,yMax:10}),graphSamplingIdentity(request));
});

test('sampling identity rejects changed expressions, derivatives, parameters and options',()=>{
  for(const changed of [{trees:[{kind:'symbol',value:'y'}]},{graphKind:'implicit'},{derivativeSelected:1},{secondDerivativeSelected:0},{parameters:{a:2}},{precision:30}]){
    assert.notEqual(graphSamplingIdentity({...request,...changed}),graphSamplingIdentity(request));
  }
  for(const graphKind of ['parametric','polar','differential','sequence']){
    const other={...request,graphKind,min:0,max:10};
    assert.notEqual(graphSamplingIdentity({...other,max:20}),graphSamplingIdentity(other));
    const preview=graphPreviewRequest(other);
    assert.deepEqual([preview.min,preview.max],[0,10]);
  }
});
