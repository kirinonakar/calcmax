import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {JSDOM} from 'jsdom';
import {createAppDialogs} from '../app-dialogs.js';
import {createAppUI} from '../app-ui.js';
import {createAppState,createPersistence} from '../app-state.js';
import {defineFunction} from '../function-transfer.js';
import {setLanguage,t as translate} from '../i18n.js';

function page(t,saved,language='en'){
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
    const {state,dialogs,body}=page(t,{variables,datasets,datasetKinds,functions,assumptions:{A:['positive']},history:[{source:'1+2',exact:'3'}]},language);
    const label=language==='en'?(hasData?'Delete all variables':'Delete all'):(hasData?'변수 모두 삭제':'모두 삭제');
    const button=[...body.querySelectorAll('button')].find(button=>button.textContent===label);
    assert.ok(button);assert.equal(button.hidden,false);assert.equal(body.querySelectorAll('.list-row').length,3);
    button.click();
    assert.deepEqual(state.variables,{});assert.equal(body.querySelectorAll('.list-row').length,0);assert.equal(button.hidden,true);
    assert.deepEqual(state.datasets,datasets);assert.deepEqual(state.datasetKinds,datasetKinds);
    assert.ok(state.functions.f);assert.equal(state.functions.g,undefined,'Ans-linked functions expire when Ans is deleted');
    assert.deepEqual(state.assumptions,{A:['positive']});assert.equal(state.history.length,1);
    if(hasData)assert.ok(body.textContent.includes('samples'));
    const restored=createAppState(JSON.parse(localStorage.getItem('calcmax-web-v1')));
    assert.deepEqual(restored.variables,{});assert.deepEqual(restored.datasets,datasets);assert.ok(restored.functions.f);assert.equal(restored.functions.g,undefined);
    dialogs.variables();
    assert.equal([...body.querySelectorAll('button')].find(button=>button.textContent===label).hidden,true);
  });
}

test('delete all visibility follows saving and individually deleting variables in an empty dialog',t=>{
  const {body,state}=page(t,{});
  const buttons=()=>[...body.querySelectorAll('button')];
  const deleteAll=buttons().find(button=>button.textContent==='Delete all');assert.equal(deleteAll.hidden,true);
  const inputs=body.querySelectorAll('input');inputs[0].value='B';inputs[1].value='5';
  buttons().find(button=>button.textContent===translate('수식 저장')).click();
  assert.equal(state.variables.B.value,'5');assert.equal(deleteAll.hidden,false);
  body.querySelector('.list-row button:last-child').click();
  assert.deepEqual(state.variables,{});assert.equal(deleteAll.hidden,true);
});
