import test from 'node:test';
import assert from 'node:assert/strict';
import {createAppState,restoreFields,createPersistence} from '../app-state.js';
import {readState} from '../storage.js';
import {readFileSync} from 'node:fs';

test('statistics section defaults and changed expansion survive storage and restore',()=>{
  const html=readFileSync(new URL('../index.html',import.meta.url),'utf8');
  const defaults=[...html.matchAll(/<details id="(statistics-[^"]+)" class="statistics-section"([^>]*)>/g)].map(([,id,attrs])=>({id,open:/\bopen\b/.test(attrs)}));
  assert.deepEqual(defaults.filter(section=>section.open).map(section=>section.id),['statistics-summary']);
  const sections=structuredClone(defaults);
  const oldDocument=globalThis.document,oldStorage=globalThis.localStorage;
  let saved;
  globalThis.localStorage={getItem:()=>saved,setItem:(_,value)=>{saved=value;}};
  globalThis.document={getElementById:()=>null,querySelectorAll:selector=>selector==='details.statistics-section[id]'?sections:[]};
  try {
    const state=createAppState();
    const persistence=createPersistence({state,snapshot:()=>({expression:'',graph:{}}),onPersist:()=>{},toast:()=>{}});
    sections.find(section=>section.id==='statistics-summary').open=false;
    sections.find(section=>section.id==='statistics-regression').open=true;
    sections.find(section=>section.id==='statistics-models').open=true;
    persistence.persist();
    sections.forEach((section,index)=>{section.open=defaults[index].open;});
    restoreFields(createAppState(readState()));
    assert.equal(sections.find(section=>section.id==='statistics-summary').open,false);
    assert.equal(sections.find(section=>section.id==='statistics-regression').open,true);
    assert.equal(sections.find(section=>section.id==='statistics-models').open,true);
    sections.forEach(section=>{section.open=false;});persistence.persist();
    sections.forEach(section=>{section.open=true;});restoreFields(createAppState(readState()));
    assert.ok(sections.every(section=>!section.open));
    assert.deepEqual(createAppState({statisticsSections:[]}).statisticsSections,{});
  } finally {
    globalThis.document=oldDocument;globalThis.localStorage=oldStorage;
  }
});

test('saved HMC settings migrate to NUTS without reusing the leapfrog count',()=>{
  const saved={fields:{'regression-bayesian-method':'hmc','regression-hmc-samples':'700','regression-hmc-warmup':'600','regression-hmc-leapfrog':'40','regression-hmc-seed':'13','regression-hmc-chains':'4'}};
  const state=createAppState(saved);
  assert.equal(state.fields['regression-bayesian-method'],'nuts');
  for(const [suffix,value] of [['samples','700'],['warmup','600'],['seed','13'],['chains','4']]){
    assert.equal(state.fields[`regression-nuts-${suffix}`],value);
    assert.equal(state.fields[`regression-hmc-${suffix}`],undefined);
  }
  assert.equal(state.fields['regression-nuts-max-depth'],undefined,'HTML supplies default depth 8');
  assert.equal(state.fields['regression-hmc-leapfrog'],undefined);
  assert.equal(saved.fields['regression-bayesian-method'],'hmc');
  assert.equal(createAppState({fields:{...saved.fields,'regression-nuts-samples':'900','regression-nuts-max-depth':'6'}}).fields['regression-nuts-samples'],'900');
});

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
