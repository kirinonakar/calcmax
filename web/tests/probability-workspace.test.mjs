import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {fileURLToPath} from 'node:url';
import {JSDOM} from 'jsdom';
import {loadPyodide} from '../vendor/pyodide.mjs';
import {installEngine} from '../engine-bootstrap.js';
import {getLanguage,setLanguage} from '../i18n.js';
import {probabilitySchema as schema} from '../probability-schema.js';
import {createProbabilityWorkspace} from '../probability-workspace.js';

test('probability controls run all user examples through actual WASM',async t=>{
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
  const expected=[0.5,0.5,1/6,1/13,0.3125,0.875,1/3,1/45,6*17296/2598960];
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


  const select=(id,value)=>{const input=$(id);input.value=value;input.dispatchEvent(new dom.window.Event('change'));};
  const edit=(id,value)=>{const input=$(id);input.value=value;input.dispatchEvent(new dom.window.Event('input'));};
  const chooseOperation=id=>{
    const operations=$('probability-category').value==='distribution'?schema.operations:schema.tools.find(tool=>tool.id===$('probability-category').value).operations;
    const op=operations.find(op=>op.id===id);
    [...$('probability-operations').children].find(button=>button.textContent===(getLanguage()==='ko'?(op.ko||op.label):op.label)).click();
  };
  await t.test('new distributions expose the proper fields and run every operation in WASM',async()=>{
    select('probability-category','distribution');
    for(const id of ['gamma','beta','lognormal','negativeBinomial','weibull']){
      select('probability-distribution',id);
      const definition=schema.distributions.find(d=>d.id===id);
      for(const field of definition.fields)assert.equal($(`probability-${id}-${field[0]}`).value,field[3]);
      assert.equal($('probability-hint').textContent,definition.hint);
      for(const op of schema.operations.filter(op=>(!op.discrete||definition.discrete)&&(!op.continuous||!definition.discrete))){
        chooseOperation(op.id);
        await workspace.run();assert.equal(latest.ok,true,`${id}/${op.id}: ${latest.error}`);
        assert.equal($('probability-error').hidden,true);
        assert.equal(workspace.request().operation,op.id);
        assert.equal(Boolean($('probability-result').querySelector('.probability-percent')),latest.isProbability);
        if(op.id==='quantile'){
          assert.equal($(`probability-${id}-x`),null);
          const quantile=Number(latest.value);
          const result=await engine.execute({action:'probability',category:'distribution',distribution:id,operation:'le',values:{...workspace.request().values,x:String(quantile)}});
          assert.equal(result.ok,true,result.error);
          if(definition.discrete)assert.ok(Number(result.value)>=0.95);
          else assert.ok(Math.abs(Number(result.value)-0.95)<1e-14);
        }
      }
    }
    select('probability-distribution','negativeBinomial');chooseOperation('eq');
    edit('probability-negativeBinomial-r','3');edit('probability-negativeBinomial-p','1/2');edit('probability-negativeBinomial-x','2');
    await workspace.run();assert.equal(Number(latest.value),0.1875);
    setLanguage('ko');workspace.render();
    assert.match($('probability-distribution').selectedOptions[0].textContent,/음이항/);
    assert.match($('probability-hint').textContent,/실패 횟수/);
    assert.match($('probability-result').textContent,/총 시행 수 = X \+ r/);
    chooseOperation('quantile');await workspace.run();
    assert.match($('probability-result').textContent,/총 시행 수 = X \+ r/);
    setLanguage('en');workspace.render();
  });

  await t.test('normal solver shows only the known parameter and survives reload and localization',async()=>{
    select('probability-category','normalSolver');
    assert.equal($('probability-normalSolver-mu'),null);
    edit('probability-normalSolver-sigma','10');edit('probability-normalSolver-x','80');edit('probability-normalSolver-q','95%');
    await workspace.run();assert.equal(latest.ok,true,latest.error);
    assert.ok(Math.abs(Number(latest.value)-63.5514637304853)<1e-12);
    assert.equal($('probability-result').querySelector('.probability-percent'),null);
    assert.equal(document.querySelector('[data-run="probability"]').textContent,'Calculate parameter');
    chooseOperation('sigmaLe');assert.equal($('probability-normalSolver-sigma'),null);
    edit('probability-normalSolver-mu','63.5514637304853');await workspace.run();
    assert.ok(Math.abs(Number(latest.value)-10)<1e-12);
    chooseOperation('muGe');edit('probability-normalSolver-q','5%');await workspace.run();
    assert.ok(Math.abs(Number(latest.value)-63.5514637304853)<1e-12);
    const restored=createProbabilityWorkspace({state,engine,persist,requestOptions:()=>({precision:30})});
    assert.equal(restored.request().operation,'muGe');assert.equal(restored.request().values.q,'5%');
    chooseOperation('sigmaLe');edit('probability-normalSolver-q','0.5');edit('probability-normalSolver-mu','80');
    setLanguage('ko');restored.render();await restored.run();
    assert.match($('probability-error').textContent,/하나로 정해지지/);
    assert.equal(document.querySelector('[data-run="probability"]').textContent,'모수 계산');
    setLanguage('en');restored.render();
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
