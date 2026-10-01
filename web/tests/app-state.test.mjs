import test from 'node:test';
import assert from 'node:assert/strict';
import {JSDOM} from 'jsdom';
import {createAppState,restoreFields,restoreSelect,createPersistence} from '../app-state.js';

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
  const state=createAppState({variables:[],functions:null,datasets:'invalid',fields:[],matrixCells:null,rates:[],assumptions:'invalid',history:'invalid',favorites:{},recent:null,precision:999,digits:999,inputFont:1,outputFont:999,theme:'invalid',resultDisplayMode:'invalid'},'ko-KR');
  for(const key of ['variables','functions','datasets','fields','matrixCells','rates','assumptions'])assert.deepEqual(state[key],{});
  for(const key of ['history','favorites','recent'])assert.deepEqual(state[key],[]);
  assert.equal(state.precision,200);assert.equal(state.digits,200);
  assert.equal(state.inputFont,10);assert.equal(state.outputFont,48);
  assert.equal(state.theme,'system');assert.equal(state.resultDisplayMode,'off');
  assert.equal(state.language,'ko');assert.equal(state.autoCloseBrackets,true);
  const saved={precision:8,digits:20,history:Array.from({length:501},(_,time)=>({time})),displayShortcuts:Array.from({length:13},(_,label)=>({label}))};
  const bounded=createAppState(saved);
  assert.equal(bounded.digits,8);assert.equal(bounded.history.length,500);assert.equal(bounded.displayShortcuts.length,12);
  assert.equal(saved.history.length,501,'normalization does not truncate the source backup');
});

test('field restoration keeps valid choices and restores options populated after startup',t=>{
  const $=page(t),state=createAppState({fields:{expression:'A+B',enabled:true,mode:'removed-mode',dynamic:'2',setting:'saved',obsolete:'unused'}});
  restoreFields(state);
  assert.equal($('expression').value,'A+B');assert.equal($('enabled').checked,true);
  assert.equal($('mode').value,'scientific','obsolete choices leave the HTML default');
  assert.equal($('setting').value,'default','dialog values are managed by their own settings');
  $('dynamic').innerHTML='<option>1</option><option>2</option>';
  restoreSelect(state,'dynamic');assert.equal($('dynamic').value,'2');
  state.fields.mode='matrix';restoreSelect(state,'mode');assert.equal($('mode').value,'matrix');
});

test('persistence saves the CALC formula and graph draft while disabled history stays in memory',t=>{
  const $=page(t),history=[{source:'1+1',exact:'2'}],state=createAppState({history,persistHistory:false});
  $('enabled').checked=true;
  let updates=0;
  const graph={sources:{cartesian:'x^2'},parameters:{a:3}};
  const persistence=createPersistence({state,snapshot:()=>({expression:'A+B',graph}),onPersist:()=>updates++,toast:()=>assert.fail('storage should work')});
  persistence.persist();
  const stored=JSON.parse(localStorage.getItem('calcmax-web-v1'));
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
  assert.equal(JSON.parse(localStorage.getItem('calcmax-web-v1')).fields.expression,'456');
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
