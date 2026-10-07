import test from 'node:test';
import assert from 'node:assert/strict';
import {Worker} from 'node:worker_threads';
import {statisticsHierarchy,clusteredHeatMap} from '../statistics-cluster.js';

test('clustered heat maps reorder both axes without altering labels, values or missing cells',()=>{
  const data={columns:['a','b','c','d'],rows:[
    {label:'A',values:[0,10,1,11]},{label:'B',values:[10,20,11,21]},
    {label:'C',values:[1,11,2,12]},{label:'D',values:[11,21,12,22]},
    {label:'Missing',values:[null,null,null,null]}
  ]};
  const result=clusteredHeatMap(data);
  assert.deepEqual(result.columns,['a','c','b','d']);assert.deepEqual(result.rows.map(row=>row.label),['A','C','B','D','Missing']);
  assert.equal(result.rowLinks.length,3);assert.equal(result.columnLinks.length,3);
  for(const row of result.rows)for(let i=0;i<result.columns.length;i++)assert.equal(row.values[i],data.rows.find(original=>original.label===row.label).values[data.columns.indexOf(result.columns[i])]);
  assert.deepEqual(data.columns,['a','b','c','d']);assert.equal(data.rows[1].label,'B');
});

test('empty, constant, disconnected and extreme finite vectors have stable finite forests',()=>{
  assert.deepEqual(statisticsHierarchy([]),{order:[],links:[]});
  for(const values of [[[5],[5],[5]],[[null,1],[2,null],[null,null]],[[-1e308],[0],[1e308]],[[1e-300],[2e-300],[3e-300]]]){
    const result=statisticsHierarchy(values);assert.equal(result.order.length,values.length);assert.equal(new Set(result.order).size,values.length);
    assert.ok(result.links.every(link=>Object.values(link).every(Number.isFinite)));
  }
  assert.equal(statisticsHierarchy([[null,1],[2,null],[null,null]]).links.length,0);
});

test('the module worker computes the same clustered matrix as the direct implementation',async t=>{
  const target=new URL('../statistics-cluster-worker.js',import.meta.url).href;
  const script=`import {parentPort} from 'node:worker_threads';globalThis.postMessage=data=>parentPort.postMessage(data);await import(${JSON.stringify(target)});parentPort.on('message',data=>globalThis.onmessage({data}));`;
  const worker=new Worker(new URL(`data:text/javascript,${encodeURIComponent(script)}`));t.after(()=>worker.terminate());
  const data={columns:['a','b'],rows:[{label:'1',values:[0,10]},{label:'2',values:[11,21]},{label:'3',values:[1,11]}]},options={linkage:'average'};
  const result=new Promise((resolve,reject)=>{worker.once('message',resolve);worker.once('error',reject);});
  worker.postMessage({data,options});assert.deepEqual(await result,{result:clusteredHeatMap(data,options)});
});

test('average, complete and Ward linkages follow their Lance–Williams merge heights',()=>{
  const average=statisticsHierarchy([[0],[10],[1],[11]],{linkage:'average'});
  assert.deepEqual(average.order,[0,2,1,3]);
  assert.ok(Math.abs(average.links[0].height-1/11)<1e-12);
  assert.ok(Math.abs(average.links[1].height-1/11)<1e-12);
  assert.ok(Math.abs(average.links[2].height-10/11)<1e-12);
  const complete=statisticsHierarchy([[0],[10],[1],[11]],{linkage:'complete'});
  assert.ok(Math.abs(complete.links[2].height-1)<1e-12);
  const ward=statisticsHierarchy([[0],[2],[10],[12]],{linkage:'ward'});
  assert.deepEqual(ward.order,[0,1,2,3]);
  assert.ok(Math.abs(ward.links[0].height-1/6)<1e-12);
  assert.ok(Math.abs(ward.links[1].height-1/6)<1e-12);
  assert.ok(Math.abs(ward.links[2].height-Math.sqrt(25/18))<1e-12);
});
