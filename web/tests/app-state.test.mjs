import test from 'node:test';
import assert from 'node:assert/strict';
import {JSDOM} from 'jsdom';
import {createAppState,createPersistence} from '../app-state.js';
import {defineFunction,encodeFunctions,decodeFunctions} from '../function-transfer.js';
import {readFileSync} from 'node:fs';
import {createAppDialogs} from '../app-dialogs.js';
import {createAppUI} from '../app-ui.js';
import {setLanguage} from '../i18n.js';

function page(t) {
  const dom=new JSDOM(`<main>
    <textarea id="expression">123</textarea><input id="enabled" type="checkbox">
    <select id="mode"><option value="scientific">Scientific</option><option value="matrix">Matrix</option></select>
    <select id="dynamic"></select>
  </main><dialog><input id="setting" value="default"></dialog>`,{url:'http://localhost/'});
  for(const key of ['document','localStorage']){
    const original=Object.getOwnPropertyDescriptor(globalThis,key);
    Object.defineProperty(globalThis,key,{value:dom.window[key],configurable:true});
    t.after(()=>{if(original)Object.defineProperty(globalThis,key,original);else delete globalThis[key];});
  }
  t.after(()=>dom.window.close());
  return id=>document.getElementById(id);
}

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

test('Ans functions survive matching restored answers and expire on replacement or clearing',t=>{
  page(t);
  const answer={kind:'snapshot_symbol',value:'x'},linked={...defineFunction('g',['x'],'x'),answerSource:answer};
  const state=createAppState({variables:{Ans:{value:'x',kind:'snapshot_symbol'}},functions:{g:linked,f:defineFunction('f',['x'],'x+1')}});
  assert.ok(state.functions.g,'JSON key order does not change the answer');
  assert.deepEqual(decodeFunctions(encodeFunctions(state.functions)).functions.g.answerSource,answer,'function transfer preserves the Ans link');
  const persistence=createPersistence({state,snapshot:()=>({expression:'',graph:{}}),onPersist:()=>{},toast:()=>{}});
  state.variables.A={kind:'number',value:'2'};persistence.persist();assert.ok(state.functions.g);
  state.variables.Ans={kind:'number',value:'2'};persistence.persist();assert.equal(state.functions.g,undefined);assert.ok(state.functions.f);
  assert.equal(JSON.parse(localStorage.getItem('symvacas-web-v1')).functions.g,undefined);
  state.variables.Ans=answer;state.functions.g=linked;state.variables={};persistence.persist();assert.equal(state.functions.g,undefined);
  const saved={variables:{},functions:{g:linked}};assert.equal(createAppState(saved).functions.g,undefined);assert.ok(saved.functions.g,'restoration does not modify the source backup');
  persistence.dispose();
});

test('persistence saves the CALC formula and graph draft while disabled history stays in memory',t=>{
  const $=page(t),history=[{source:'1+1',exact:'2'}],state=createAppState({history,persistHistory:false});
  $('enabled').checked=true;
  let updates=0;
  const graph={sources:{cartesian:'x^2'},parameters:{a:3}};
  const persistence=createPersistence({state,snapshot:()=>({expression:'A+B',graph}),onPersist:()=>updates++,toast:()=>assert.fail('storage should work')});
  persistence.persist();
  const stored=JSON.parse(localStorage.getItem('symvacas-web-v1'));
  assert.equal(stored.fields.expression,'A+B','temporary numeric CALC input must not replace the formula');
  assert.equal(stored.fields.enabled,true);assert.deepEqual(stored.graph,graph);
  assert.deepEqual(stored.history,[]);assert.deepEqual(state.history,history);assert.equal(updates,1);
  persistence.dispose();
});

test('draft saves are debounced, immediate persistence flushes, and disposal cancels a pending save',t=>{
  const $=page(t),state=createAppState();
  t.mock.timers.enable({apis:['setTimeout']});
  let writes=0;
  const persistence=createPersistence({state,snapshot:()=>({expression:$('expression').value,graph:{}}),onPersist:()=>writes++,toast:()=>{}});
  persistence.schedulePersist();t.mock.timers.tick(100);$('expression').value='456';persistence.schedulePersist();
  t.mock.timers.tick(149);assert.equal(writes,0);t.mock.timers.tick(1);assert.equal(writes,1);
  assert.equal(JSON.parse(localStorage.getItem('symvacas-web-v1')).fields.expression,'456');
  persistence.schedulePersist();persistence.persist();t.mock.timers.tick(150);assert.equal(writes,2);
  persistence.schedulePersist();persistence.dispose();t.mock.timers.tick(150);assert.equal(writes,2);
});

test('storage failures warn once and still retain the session draft and notify preview scheduling',t=>{
  page(t);
  const state=createAppState(),warnings=[];
  let updates=0;
  t.mock.method(Object.getPrototypeOf(localStorage),'setItem',()=>{throw new Error('quota');});
  const persistence=createPersistence({state,snapshot:()=>({expression:'x+1',graph:{}}),onPersist:()=>updates++,toast:message=>warnings.push(message)});
  persistence.persist();persistence.persist();
  assert.equal(warnings.length,1);assert.equal(state.fields.expression,'x+1');assert.equal(updates,2);
  persistence.dispose();
});

function variablesPage(t,saved,language='en'){
  const dom=new JSDOM(readFileSync(new URL('../index.html',import.meta.url),'utf8'),{url:'http://localhost/'});
  for(const key of ['document','window','NodeFilter','localStorage']){
    const original=Object.getOwnPropertyDescriptor(globalThis,key);
    Object.defineProperty(globalThis,key,{value:dom.window[key],configurable:true});
    t.after(()=>{if(original)Object.defineProperty(globalThis,key,original);else delete globalThis[key];});
  }
  dom.window.matchMedia=()=>({matches:false,addEventListener:()=>{}});
  dom.window.HTMLDialogElement.prototype.showModal=function(){this.open=true;};
  const state=createAppState({...saved,language,languageChosen:true});setLanguage(language);
  const ui=createAppUI(),persistence=createPersistence({state,snapshot:()=>({expression:'A+1',graph:{}}),onPersist:()=>{},toast:assert.fail});
  t.after(()=>{persistence.dispose();ui.dispose();dom.window.close();setLanguage('en');});
  const dialogs=createAppDialogs({state,ui,persist:persistence.persist,calculator:{},refreshDisplays:()=>{}});
  dialogs.variables();
  return {state,dialogs,body:document.getElementById('dialog-body')};
}

for(const language of ['en','ko'])for(const hasData of [false,true]){
  test(`delete all clears stored variables and persists the result (${language}, datasets=${hasData})`,t=>{
    const answer={kind:'number',value:'3'},variables={A:{kind:'number',value:'1'},M:{kind:'number',value:'2'},Ans:answer};
    const datasets=hasData?{samples:'1\n2\n3'}:{},datasetKinds=hasData?{samples:'list'}:{};
    const functions={f:defineFunction('f',['x'],'x+1'),g:{...defineFunction('g',['x'],'x'),answerSource:answer}};
    const {state,dialogs,body}=variablesPage(t,{variables,datasets,datasetKinds,functions,assumptions:{A:['positive']},history:[{source:'1+2',exact:'3'}]},language);
    const label=language==='en'?(hasData?'Delete all variables':'Delete all'):(hasData?'변수 모두 삭제':'모두 삭제');
    const button=[...body.querySelectorAll('button')].find(button=>button.textContent===label);
    assert.ok(button);assert.equal(button.hidden,false);assert.equal(body.querySelectorAll('.list-row').length,3);
    button.click();
    assert.deepEqual(state.variables,{});assert.equal(body.querySelectorAll('.list-row').length,0);assert.equal(button.hidden,true);
    assert.deepEqual(state.datasets,datasets);assert.deepEqual(state.datasetKinds,datasetKinds);
    assert.ok(state.functions.f);assert.equal(state.functions.g,undefined,'Ans-linked functions expire when Ans is deleted');
    assert.deepEqual(state.assumptions,{A:['positive']});assert.equal(state.history.length,1);
    if(hasData)assert.ok(body.textContent.includes('samples'));
    const restored=createAppState(JSON.parse(localStorage.getItem('symvacas-web-v1')));
    assert.deepEqual(restored.variables,{});assert.deepEqual(restored.datasets,datasets);assert.ok(restored.functions.f);assert.equal(restored.functions.g,undefined);
    dialogs.variables();
    assert.equal([...body.querySelectorAll('button')].find(button=>button.textContent===label).hidden,true);
  });
}
