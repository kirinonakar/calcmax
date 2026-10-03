import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {fileURLToPath} from 'node:url';
import {JSDOM} from 'jsdom';
import {loadPyodide} from '../vendor/pyodide.mjs';
import {installEngine} from '../engine-bootstrap.js';
import {createCalculator} from '../calculator.js';
import {createFunctionsWorkspace} from '../functions-workspace.js';
import {createAppState,createPersistence} from '../app-state.js';
import {readState} from '../storage.js';
import {parse} from '../parser.js';

function page(t,execute=()=>assert.fail('Definitions must not execute the body')) {
  const dom=new JSDOM(readFileSync(new URL('../index.html',import.meta.url),'utf8'),{url:'http://localhost/'});
  const originals=new Map();
  for(const key of ['document','localStorage']){
    const original=Object.getOwnPropertyDescriptor(globalThis,key);
    originals.set(key,original);
    Object.defineProperty(globalThis,key,{value:dom.window[key],configurable:true});
  }
  const $=id=>document.getElementById(id),state=createAppState({variables:{Ans:parse('42'),x:parse('99')}}),errors=[];
  const engine={ready:true,execute:t.mock.fn(execute)},ui={toast:()=>{},openDialog:()=>{},clipboard:()=>{},pickFile:()=>{}};
  $('mode').value='scientific';
  const persistence=createPersistence({state,toast:ui.toast,snapshot:()=>({expression:$('expression').value,graph:{}}),onPersist:()=>{}});
  const functions=createFunctionsWorkspace({state,ui,persist:persistence.persist,refreshWorkspaceMath:()=>{},changeMode:()=>{},insert:()=>{}});
  const calculator=createCalculator({state,engine,ui,isBusy:()=>false,persist:persistence.persist,schedulePersist:persistence.schedulePersist,
    requestOptions:()=>({variables:state.variables,functions:state.functions}),error:message=>errors.push(message),
    changeMode:()=>{},updateButtons:()=>{},onFunctionsChanged:functions.render});
  t.after(()=>{
    $('expression').removeEventListener('select',calculator.renderInputCursor);
    calculator.dispose();persistence.dispose();dom.window.close();
    for(const [key,original] of originals){if(original)Object.defineProperty(globalThis,key,original);else delete globalThis[key];}
  });
  async function enter(source){$('expression').value=source;await calculator.evaluate();}
  return {$,state,engine,errors,calculator,enter};
}

test('calculator definitions and subsequent calls work in the actual WASM engine and survive reload',async t=>{
  const py=await loadPyodide({indexURL:fileURLToPath(new URL('../vendor/',import.meta.url))});
  await installEngine(py,{runtimeURL:new URL('../vendor/',import.meta.url),engineURL:new URL('../engine.zip',import.meta.url),fetcher:async url=>new Response(readFileSync(url))});
  const {$,state,engine,errors,calculator,enter}=page(t,async request=>{
    py.globals.set('payload',JSON.stringify({angle:'RAD',...request}));
    return JSON.parse(py.runPython('calc_engine.dispatch(payload)'));
  });
  await enter('f(x)=x^3-8x+7');
  assert.equal(engine.execute.mock.callCount(),0);
  assert.equal(state.functions.f.source,'x^3-8x+7');
  assert.deepEqual(state.functions.f.parameters,['x']);
  assert.deepEqual(state.functions.f.body,parse('x^3-8x+7'));
  assert.deepEqual(state.variables.Ans,parse('42'),'saving a function preserves Ans');
  assert.match($('answer').textContent,/f\(x\)/);
  assert.match($('functions-list').textContent,/f\(x\)/);
  assert.equal($('commit-indicator').textContent,'=');
  calculator.insert('+');
  assert.equal($('expression').value,'+','a definition does not chain from Ans');
  await enter('f(2)');
  assert.equal($('answer').textContent,'-1');
  assert.equal(state.history[0].exact,'-1');
  assert.equal(state.history[0].source,'f(2)');
  await enter('f(-1)+f(3)');
  assert.equal(state.history[0].exact,'24');
  await enter(' f ( t ) := t^2 + 1 ');
  assert.deepEqual(state.functions.f.parameters,['t']);
  assert.equal(state.functions.f.source,'t^2 + 1');
  await enter('f(2)');
  assert.equal(state.history[0].exact,'5');
  await enter('g(x,y)=f(x)+2y');
  await enter('g(2,3)');
  assert.equal(state.history[0].exact,'11');
  await enter(String.raw`h(u)=\frac{u^2}{2}`);
  await enter('h(4)');
  assert.equal(state.history[0].exact,'8');
  const restored=createAppState(readState());
  assert.deepEqual(restored.functions,state.functions);
  py.globals.set('payload',JSON.stringify({tree:parse('f(2)+g(2,3)+h(4)'),functions:restored.functions,variables:restored.variables}));
  const result=JSON.parse(py.runPython('calc_engine.dispatch(payload)'));
  assert.equal(result.ok,true,result.error);assert.equal(result.exact,'24');
  await enter('A=2+3');
  assert.equal(state.variables.A.value,'5','existing variable assignment still works');
  await enter('solve(x^2-5x+6=0,x)');
  assert.match(state.history[0].exact,/2.*3/,'nested equations stay mathematical expressions');
  assert.deepEqual(errors,[]);
});

test('invalid definitions leave stored functions and the previous answer intact',async t=>{
  const {state,errors,engine,enter}=page(t);
  await enter('f(x)=x+1');
  const original=JSON.stringify(state.functions),historyLength=state.history.length;
  for(const source of ['f(x,x)=x','sin(x)=x','f(x)=x+','f(x)=','f()=1','f(x)=1;2']){
    const before=errors.length;
    await enter(source);
    assert.equal(errors.length,before+1,source);
    assert.equal(JSON.stringify(state.functions),original,source);
    assert.equal(state.history.length,historyLength,source);
  }
  assert.equal(engine.execute.mock.callCount(),0);
  assert.deepEqual(state.variables.Ans,parse('42'));
});

test('typing a definition does not save or evaluate it until explicitly submitted',async t=>{
  t.mock.timers.enable({apis:['setTimeout']});
  const {$,state,engine,calculator,enter}=page(t);
  for(const source of ['f(x)=x^3-8x+7','g(x,y):=x+y']){
    $('expression').value=source;calculator.preview();t.mock.timers.tick(500);
    assert.deepEqual(state.functions,{});
    assert.deepEqual(state.history,[]);
  }
  assert.equal(engine.execute.mock.callCount(),0);
  await enter('f(x)=1/x');
  assert.equal(state.functions.f.source,'1/x','a definition stores the formula without evaluating it at the current x');
});
