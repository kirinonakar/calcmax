import test from 'node:test';
import assert from 'node:assert/strict';
import {createAppState} from '../app-state.js';

test('saved state rejects malformed collections and bounds preferences and retained entries',()=>{
  const state=createAppState({variables:[],functions:null,datasets:'invalid',datasetKinds:[],fields:[],matrixCells:null,rates:[],assumptions:'invalid',history:'invalid',favorites:{},recent:null,precision:999,digits:999,inputFont:1,outputFont:999,theme:'invalid',resultDisplayMode:'invalid'},'ko-KR');
  for(const key of ['variables','functions','datasets','datasetKinds','fields','matrixCells','rates','assumptions'])assert.deepEqual(state[key],{});
  for(const key of ['history','favorites','recent'])assert.deepEqual(state[key],[]);
  assert.equal(state.precision,200);assert.equal(state.digits,200);
  assert.equal(state.inputFont,10);assert.equal(state.outputFont,48);
  assert.equal(state.theme,'system');assert.equal(state.resultDisplayMode,'off');
  assert.equal(state.language,'ko');assert.equal(state.autoCloseBrackets,true);
  const saved={precision:8,digits:20,history:Array.from({length:501},(_,time)=>({time})),displayShortcuts:Array.from({length:13},(_,label)=>({label}))};
  const bounded=createAppState(saved);
  assert.equal(bounded.digits,8);assert.equal(bounded.history.length,500);assert.equal(bounded.displayShortcuts.length,6);
  assert.equal(saved.history.length,501,'normalization does not truncate the source backup');
});

test('previously translated regression selections restore their canonical model IDs',()=>{
  for(const [label,model] of [['다항','polynomial'],['다중','multiple'],['로지스틱','logistic']]){
    const saved={fields:{'regression-kind':label}};
    const state=createAppState(saved,'ko-KR');
    assert.equal(state.fields['regression-kind'],model);
    assert.equal(saved.fields['regression-kind'],label,'the source backup is not modified');
  }
  assert.equal(createAppState({fields:{'regression-kind':'power'}}).fields['regression-kind'],'power');
});
