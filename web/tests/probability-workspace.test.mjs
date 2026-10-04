import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {fileURLToPath} from 'node:url';
import {JSDOM} from 'jsdom';
import {loadPyodide} from '../vendor/pyodide.mjs';
import {installEngine} from '../engine-bootstrap.js';
import {setLanguage} from '../i18n.js';
import {probabilitySchema as schema} from '../probability-schema.js';
import {createProbabilityWorkspace,probabilityNumber} from '../probability-workspace.js';

test('probability controls run all eight user examples through actual WASM',async t=>{
  const py=await loadPyodide({indexURL:fileURLToPath(new URL('../vendor/',import.meta.url))});
  await installEngine(py,{runtimeURL:new URL('../vendor/',import.meta.url),engineURL:new URL('../engine.zip',import.meta.url),fetcher:async url=>new Response(readFileSync(url))});
  const dom=new JSDOM(readFileSync(new URL('../index.html',import.meta.url),'utf8'));
  globalThis.document=dom.window.document;
  const $=id=>document.getElementById(id),state={fields:{},digits:6};
  let latest;
  const engine={ready:true,execute:async request=>{py.globals.set('payload',JSON.stringify(request));latest=JSON.parse(py.runPython('calc_engine.dispatch(payload)'));return latest;}};
  const persist=()=>{for(const field of document.querySelectorAll('main input[id],main select[id]'))state.fields[field.id]=field.type==='checkbox'?field.checked:field.value;};
  const workspace=createProbabilityWorkspace({state,engine,persist,requestOptions:()=>({precision:30})});
  t.after(()=>{setLanguage('en');dom.window.close();});
  assert.ok([...$('mode').options].some(o=>o.value==='probability'));
  assert.equal($('probability-category').value,'basic');
  const expected=[0.5,0.5,1/6,1/13,0.3125,0.875,1/3,1/45];
  for(const [i,example] of schema.examples.entries()){
    $('probability-examples').children[i].click();
    const request=workspace.request();
    assert.equal(request.category,example.category);
    assert.equal(request.operation,example.operation);
    for(const [key,value] of Object.entries(example.values))assert.equal(request.values[key],value);
    await workspace.run();
    assert.equal(latest.ok,true,latest.error);
    assert.ok(Math.abs(Number(latest.value)-expected[i])<1e-14,example.id);
    assert.equal($('probability-result').hidden,false);
    assert.equal($('probability-result').querySelector('.probability-fraction').textContent,latest.fraction);
    assert.equal($('probability-error').hidden,true);
  }
  await t.test('exactly / at most / at least switch the event, preserving parameters',async()=>{
    workspace.applyExample(schema.examples.find(e=>e.id==='binomial'));
    const choose=op=>[...$('probability-operations').children].find(button=>button.textContent===schema.operations.find(o=>o.id===op).label).click();
    for(const [operation,expected] of [['eq',0.3125],['le',0.8125],['ge',0.5]]){
      choose(operation);await workspace.run();assert.equal(Number(latest.value),expected);
      assert.equal(workspace.request().values.n,'5');assert.equal(workspace.request().values.p,'1/2');assert.equal(workspace.request().values.x,'3');
    }
    choose('quantile');assert.ok($('probability-binomial-q'));assert.equal($('probability-binomial-x'),null);
    choose('eq');assert.equal($('probability-binomial-x').value,'3');
  });
  await t.test('editing invalidates the old result; reset, localization and errors work',async()=>{
    workspace.applyExample(schema.examples[0]);await workspace.run();
    const input=$('probability-basic-total');input.value='0';input.dispatchEvent(new dom.window.Event('input'));
    assert.equal($('probability-result').hidden,true);
    setLanguage('ko');workspace.render();await workspace.run();
    assert.equal($('probability-error').hidden,false);assert.match($('probability-error').textContent,/전체 경우 수/);
    assert.equal($('probability-examples').children[0].textContent,'동전 앞면');
    $('probability-reset').click();assert.equal($('probability-basic-total').value,'2');
    setLanguage('en');workspace.render();
  });
  await t.test('independence removes the need for an intersection input',async()=>{
    $('probability-category').value='events';$('probability-category').dispatchEvent(new dom.window.Event('change'));
    const conditional=[...$('probability-operations').children].find(b=>b.textContent==='P(A | B)');conditional.click();
    $('probability-independent').checked=true;$('probability-independent').dispatchEvent(new dom.window.Event('change'));
    assert.equal($('probability-events-intersection').disabled,true);
    await workspace.run();assert.equal(latest.ok,true);assert.equal(Number(latest.value),0.4);
  });
  await t.test('large binomial CDF and inverse work in WASM',async()=>{
    for(const [operation,values] of [['le',{n:'100000',p:'0.5',x:'50000'}],['quantile',{n:'100000',p:'0.5',q:'0.5'}]]){
      const result=await engine.execute({action:'probability',category:'distribution',distribution:'binomial',operation,values});
      assert.equal(result.ok,true,result.error);
      if(operation==='le')assert.ok(Math.abs(Number(result.value)-0.5012615631070984)<1e-14);
      else assert.equal(Number(result.value),50000);
    }
  });
});

test('a response from before an input edit never replaces the new draft',async t=>{
  const dom=new JSDOM(readFileSync(new URL('../index.html',import.meta.url),'utf8'));globalThis.document=dom.window.document;
  const $=id=>document.getElementById(id);let resolve;
  const state={fields:{},digits:6},workspace=createProbabilityWorkspace({state,engine:{execute:()=>new Promise(r=>{resolve=r;})},persist:()=>{},requestOptions:()=>({})});
  t.after(()=>dom.window.close());
  const pending=workspace.run();$('probability-basic-total').value='3';$('probability-basic-total').dispatchEvent(new dom.window.Event('input'));
  resolve({ok:true,value:'0.5',isProbability:true,percent:'50%',formula:'1 / 2'});await pending;
  assert.equal($('probability-result').hidden,true);
});

test('probability display preserves small nonzero values',()=>{
  assert.equal(probabilityNumber('0.0000000012345678',6),'1.23457e-9');
  assert.equal(probabilityNumber('0.3125',6),'0.3125');
  assert.equal(probabilityNumber('7.619853024160526e-24',6),'7.619853e-24');
});
