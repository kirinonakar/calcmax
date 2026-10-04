import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {fileURLToPath} from 'node:url';
import {JSDOM} from 'jsdom';
import {loadPyodide} from '../vendor/pyodide.mjs';
import {installEngine} from '../engine-bootstrap.js';
import {getLanguage,setLanguage} from '../i18n.js';
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
  await t.test('draw operations use friendly counts, preserve k and match hypergeometric in WASM',async()=>{
    select('probability-category','draw');
    assert.equal($('probability-draw-k'),null);
    edit('probability-draw-population','100');edit('probability-draw-marked','10');edit('probability-draw-draws','20');
    for(const [operation,distributionOperation,k] of [['exactly','eq','3'],['atLeast','ge','3'],['atMost','le','3'],['atLeastOne','ge','1'],['allMarked','eq','10']]){
      chooseOperation(operation);
      if(['exactly','atLeast','atMost'].includes(operation)){
        if(operation==='exactly')edit('probability-draw-k',k);
        assert.equal($('probability-draw-k').value,k);
      }else assert.equal($('probability-draw-k'),null);
      const request=workspace.request();assert.equal('k' in request.values,['exactly','atLeast','atMost'].includes(operation));
      await workspace.run();assert.equal(latest.ok,true,latest.error);
      const drawValue=Number(latest.value);
      const reference=await engine.execute({action:'probability',category:'distribution',distribution:'hypergeometric',operation:distributionOperation,values:{population:'100',successes:'10',draws:'20',x:k}});
      assert.equal(reference.ok,true,reference.error);assert.equal(drawValue,Number(reference.value));
    }
    chooseOperation('exactly');assert.equal($('probability-draw-k').value,'3');
    const restored=createProbabilityWorkspace({state,engine,persist,requestOptions:()=>({precision:30})});
    assert.deepEqual(restored.request().values,{population:'100',draws:'20',marked:'10',k:'3'});
    setLanguage('ko');restored.render();
    assert.match($('probability-fields').textContent,/지정 인원 · 항목 수 K/);
    edit('probability-draw-k','1.5');await restored.run();assert.match($('probability-error').textContent,/뽑힌 지정 항목 수 k/);
    setLanguage('en');restored.render();
  });
  await t.test('new quantiles retain accuracy at tiny scales and high log-normal skew in WASM',async()=>{
    for(const [distribution,values] of [['gamma',{shape:'2',scale:'1e-60'}],['beta',{alpha:'0.01',beta:'3'}],['weibull',{shape:'2',scale:'1e-60'}],['lognormal',{mu:'-200',sigma:'1'}],['lognormal',{mu:'0',sigma:'20'}]]){
      const result=await engine.execute({action:'probability',category:'distribution',distribution,operation:'quantile',values:{...values,q:'0.5'}});
      assert.equal(result.ok,true,result.error);
      const check=await engine.execute({action:'probability',category:'distribution',distribution,operation:'le',values:{...values,x:result.value}});
      assert.equal(check.ok,true,check.error);assert.ok(Math.abs(Number(check.value)-0.5)<1e-14,distribution);
    }
  });
  await t.test('independent trial bounds preserve counts and run the requested event',async()=>{
    select('probability-category','repeat');
    edit('probability-repeat-n','10');edit('probability-repeat-p','20%');
    chooseOperation('atLeast');edit('probability-repeat-k','3');await workspace.run();
    assert.equal(latest.ok,true,latest.error);assert.equal(Number(latest.value),0.3222004736);
    assert.match($('probability-result').textContent,/Var\[X\]/);
    chooseOperation('atMost');assert.equal($('probability-repeat-k').value,'3');await workspace.run();
    assert.equal(Number(latest.value),0.8791261184);
    chooseOperation('between');assert.equal($('probability-repeat-k'),null);
    edit('probability-repeat-lower','2');edit('probability-repeat-upper','4');
    assert.equal('k' in workspace.request().values,false);await workspace.run();
    assert.equal(Number(latest.value),0.591396864);
    chooseOperation('atLeast');assert.equal($('probability-repeat-k').value,'3');
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
  await t.test('Bayes accepts specificity and plots include quantile ranges and missing moments',async()=>{
    select('probability-category','bayes');chooseOperation('posteriorSpecificity');
    assert.equal($('probability-bayes-falsePositive'),null);
    await workspace.run();assert.equal(latest.ok,true,latest.error);assert.ok(Math.abs(Number(latest.value)-1/6)<1e-14);
    chooseOperation('posterior');assert.equal($('probability-bayes-specificity'),null);
    await workspace.run();assert.ok(Math.abs(Number(latest.value)-1/6)<1e-14);
    select('probability-category','distribution');select('probability-distribution','t');edit('probability-t-df','1');await workspace.run();
    assert.equal(latest.ok,true,latest.error);assert.match($('probability-result').textContent,/undefined/);
    assert.equal($('probability-plot').hidden,false);assert.match($('probability-plot').textContent,/0.1%–99.9%/);
    setLanguage('ko');workspace.render();assert.match($('probability-result').textContent,/정의되지 않음/);setLanguage('en');workspace.render();
    select('probability-distribution','poisson');edit('probability-poisson-rate','100000');await workspace.run();
    assert.equal(latest.ok,true,latest.error);assert.equal($('probability-plot').hidden,false);
    assert.match($('probability-plot').textContent,/some counts are omitted/);
    chooseOperation('quantile');await workspace.run();assert.match(latest.formula,/≥/);assert.doesNotMatch(latest.formula,/ = /);
  });
  await t.test('new Catalog expressions evaluate through actual WASM',async()=>{
    const catalog=JSON.parse(readFileSync(new URL('../catalog.json',import.meta.url),'utf8'));
    for(const [name,args,expected] of [['hgeompdf',[10,2,2,2],1/45],['hgeomcdf',[10,2,2,1],44/45],['nbinompdf',[3,.5,2],3/16],['nbinomcdf',[3,.5,2],.5],['weibullpdf',[3,2,3],2/(3*Math.E)],['weibullcdf',[3,2,3],1-1/Math.E]]){
      assert.ok(catalog.Distributions.some(entry=>entry.startsWith(name+'(')));
      const result=await engine.execute({tree:{kind:'call',value:name,args:args.map(value=>({kind:'number',value:String(value)}))}});
      assert.equal(result.ok,true,result.error);assert.ok(Math.abs(Number(result.decimal)-expected)<1e-14,name);
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
