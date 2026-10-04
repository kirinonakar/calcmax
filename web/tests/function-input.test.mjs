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
  await enter('f(x)=x^3-8x+7');
  const previousAnswer=structuredClone(state.variables.Ans);
  await enter('diff(f(x),x)=g(x)');
  assert.deepEqual(state.functions.g.parameters,['x']);
  assert.deepEqual(state.variables.Ans,previousAnswer,'saving a derivative function preserves Ans');
  assert.match($('functions-list').textContent,/g\(x\)/);
  await enter('g(1)');
  assert.equal(state.history[0].exact,'-5');
  await enter('diff(f(x),x)=Ans');
  assert.deepEqual(state.variables.Ans.parameters,['x']);
  await enter('Ans=g(x)');
  assert.equal(state.history[0].exact,'3*x**2 - 8');
  assert.ok(state.functions.g.answerSource);
  await enter('Ans=h(x)');
  assert.ok(createAppState(readState()).functions.g,'the link survives reload while Ans is unchanged');
  await enter('A=5');
  assert.ok(state.functions.g,'variable storage preserves Ans and its functions');
  await enter('1/0');
  assert.match(errors.pop(),/zero/);
  assert.ok(state.functions.g,'failed calculations preserve Ans and its functions');
  await enter('Ans(1)');
  assert.equal(state.history[0].exact,'-5');
  assert.equal(state.functions.g,undefined,'an Ans update removes its function');
  assert.equal(state.functions.h,undefined,'all functions linked to the previous Ans expire');
  assert.doesNotMatch($('functions-list').textContent,/g\(x\)/);
  assert.equal(createAppState(readState()).functions.g,undefined,'expired functions stay absent after reload');
  await enter('diff(f(x),x)=g(x)');
  await enter('g(2)');
  assert.equal(state.history[0].exact,'4','directly stored derivatives remain permanent');
  await enter('diff(f(x),x)');
  await enter('Ans(1)+Ans(2)');
  assert.equal(state.history[0].exact,'-1','calls in one expression reuse the same answer');
  await enter('f(x)=2x+7');
  await enter('diff(f(x),x)=Ans');
  await enter('Ans=g(x)');
  await enter('Ans(123)');
  assert.equal(state.history[0].exact,'2','constant derivatives remain callable');
  assert.equal(state.functions.g,undefined);
  await enter('diff(f(x),x)=g(x)');
  await enter('g(-456)');
  assert.equal(state.history[0].exact,'2');
  await enter('f(x)=1/x');
  await enter('diff(f(x),x)=g(x)');
  await enter('g(2)');
  assert.equal(state.history[0].exact,'-1/4');
  await enter('f(x)=x^3');
  await enter('g(2)');
  assert.equal(state.history[0].exact,'-1/4','the stored derivative is independent of later f definitions');
  const saved=createAppState(readState());
  py.globals.set('payload',JSON.stringify({tree:parse('g(2)'),functions:saved.functions,variables:saved.variables}));
  assert.equal(JSON.parse(py.runPython('calc_engine.dispatch(payload)')).exact,'-1/4');
  await enter('Ans(x)=x^2+1');
  await enter('Ans(1)');
  assert.equal(state.history[0].exact,'2');
  assert.equal(state.functions.Ans,undefined);
  await enter('Ans(x)=x^2');
  await enter('Ans=k(x)');
  await enter('k(x)=x+3');
  await enter('1+1');
  await enter('k(1)');
  assert.equal(state.history[0].exact,'4','explicit redefinition removes the Ans lifetime link');
  const beforeIntegral=structuredClone(state.variables.Ans);
  await enter('integrate(x,x)=f(x)');
  assert.deepEqual(state.functions.f.parameters,['x']);
  assert.deepEqual(state.variables.Ans,beforeIntegral,'saving an integral preserves Ans');
  assert.match($('functions-list').textContent,/f\(x\)/);
  await enter('f(2)');
  assert.equal(state.history[0].exact,'2 + C','the integration constant remains symbolic');
  await enter('1+1');
  assert.ok(state.functions.f,'directly saved integrals survive Ans changes');
  await enter('diff(f(x),x)');
  assert.equal(state.history[0].exact,'x','a saved integral can be differentiated');
  const integralReload=createAppState(readState());
  py.globals.set('payload',JSON.stringify({tree:parse('f(2)'),functions:integralReload.functions,variables:integralReload.variables}));
  assert.equal(JSON.parse(py.runPython('calc_engine.dispatch(payload)')).exact,'2 + C');
  await enter('f(x)=integrate(x,x)');
  await enter('f(2)');
  assert.equal(state.history[0].exact,'2 + C','reversed integral storage binds the call argument');
  await enter('r(x)=diff(f(x),x)');
  await enter('r(3)');
  assert.equal(state.history[0].exact,'3','reversed derivative storage saves the computed derivative');
  await enter('p(x)=diff(x^3,x)');
  await enter('p(2)');
  assert.equal(state.history[0].exact,'12');
  await enter('1+1');
  assert.ok(state.functions.f);assert.ok(state.functions.r);assert.ok(state.functions.p);
  await enter('q(x):=integrate(x,x,0,2)');
  await enter('q(5)');
  assert.equal(state.history[0].exact,'2','definite integrals can be stored as constant functions');
  await enter('integrate(x,x)=Ans');
  await enter('Ans=h(x)');
  await enter('Ans(2)');
  assert.equal(state.history[0].exact,'2 + C');
  assert.equal(state.functions.h,undefined,'Ans-linked integral functions expire with Ans');
  assert.deepEqual(errors,[]);
  const beforeFailure=JSON.stringify(state.functions);
  await enter('g(0)');
  assert.match(errors.pop(),/Domain|zero/);
  for(const source of ['diff(f(x),x)=sin(x)','diff(f(x),x)=g(x,x)','diff(f(x),x)=g(1)','diff(f(x),x)=g()','integrate(x,x)=sin(x)','integrate(x,x)=f(x,x)','integrate(x,x)=f(1)','integrate(x,x)=f()','sin(x)=integrate(x,x)','f(x,x)=diff(x,x)','f(1)=integrate(x,x)','f()=diff(x,x)']){
    await enter(source);assert.ok(errors.pop(),source);
    assert.equal(JSON.stringify(state.functions),beforeFailure,source);
  }
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
  for(const source of ['f(x)=x^3-8x+7','g(x,y):=x+y','diff(f(x),x)=g(x)','diff(f(x),x)=Ans','Ans=g(x)','integrate(x,x)=f(x)','integrate(x,x)=Ans','f(x)=integrate(x,x)','g(x)=diff(x^2,x)','sin(theta)/(1-cos(theta)^2)=N','sin(theta)=N(theta)','2+3=N']){
    $('expression').value=source;calculator.preview();t.mock.timers.tick(500);
    assert.deepEqual(state.functions,{});
    assert.deepEqual(state.history,[]);
    assert.equal(state.variables.N,undefined);
  }
  assert.equal(engine.execute.mock.callCount(),0);
  await enter('f(x)=1/x');
  assert.equal(state.functions.f.source,'1/x','a definition stores the formula without evaluating it at the current x');
});
