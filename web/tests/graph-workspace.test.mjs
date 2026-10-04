import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {JSDOM} from 'jsdom';
import {createGraphWorkspace,graphInputTree,cartesianFormula,graphShadings,graphExpressions,removeGraphSource} from '../graph-workspace.js';
import {installCanvas} from './canvas-context.mjs';
import {parse} from '../parser.js';

function setup(t,saved={}){
  const dom=new JSDOM(readFileSync(new URL('../index.html',import.meta.url),'utf8'));
  globalThis.document=dom.window.document;installCanvas(dom);
  const byId=id=>document.getElementById(id),requests=[];
  byId('graph-source').value='x';
  const workspace=createGraphWorkspace({
    execute:async request=>{requests.push(request);return {ok:true,curves:[[[-100,-100],[100,100]]],parameters:[]};},
    options:()=>({displayDigits:10}),onError:assert.fail,persist:()=>{},isBusy:()=>false,saved,
  });
  const edit=(id,number)=>{byId(id).value=String(number);byId(id).dispatchEvent(new dom.window.Event('input'));byId(id).dispatchEvent(new dom.window.Event('change'));};
  const expectDomain=(low,high)=>{
    for(const id of ['graph-analysis-a-slider','graph-analysis-b-slider','graph-tangent-slider']){
      assert.equal(Number(byId(id).min),low,id);assert.equal(Number(byId(id).max),high,id);
    }
  };
  t.after(()=>{workspace.dispose();dom.window.close();});
  return {dom,byId,workspace,requests,edit,expectDomain};
}

test('shading aliases parse chained and reversed bounds as one intersection',()=>{
  for(const prefix of ['[shade]','[s]']){
    const source=`${prefix} 1<x<3, 1<y<3`,[item]=graphShadings(source,'cartesian');
    assert.equal(item.mode,'region');
    assert.deepEqual(item.trees.map(tree=>tree.value),['1','3','1','3']);
    assert.deepEqual(item.constraints,[{axis:'x',side:'lower'},{axis:'x',side:'upper'},{axis:'y',side:'lower'},{axis:'y',side:'upper'}]);
    assert.deepEqual(graphExpressions(`x\n${source}\nx+1`,'cartesian'),['x','x+1']);
    assert.equal(removeGraphSource(`x\n${source}`,0,'cartesian',true),'x');
  }
  assert.deepEqual(graphShadings('[s] 3>=x>1, 3>y>=1','cartesian')[0].constraints.map(c=>c.side),['upper','lower','upper','lower']);
  assert.equal(graphShadings('[s] y<x^2','cartesian')[0].mode,'halfplane');
  assert.equal(graphShadings('[s] sin(x),cos(x);0..pi','cartesian')[0].mode,'band');
  for(const source of ['x<x+1','y<y+1','1<y<3,x,x^2,x^3','x=1'])assert.throws(()=>graphShadings(`[s] ${source}`,'cartesian'));
});

test('shading functions accept chained, reversed and one-sided x bounds',()=>{
  for(const prefix of ['[s]','[shade]'])for(const range of ['-pi<x<pi','pi>=x>=-pi']){
    const [item]=graphShadings(`${prefix} sin(x), cos(x), ${range}`,'cartesian');
    assert.equal(item.mode,'band');assert.deepEqual(item.trees.map(tree=>tree.value),['sin','cos']);
    const lower=item.xBounds.find(bound=>bound.side==='lower'),upper=item.xBounds.find(bound=>bound.side==='upper');
    assert.equal(lower.tree.kind,'unary');assert.equal(lower.tree.args[0].value,'pi');assert.equal(upper.tree.value,'pi');
  }
  assert.equal(graphShadings('[s] x, x>0','cartesian')[0].xBounds[0].side,'lower');
  for(const source of ['sin(x),cos(x),y<y+1','x, x^2, x^3, -pi<x<pi','x, 0, x<x+1'])assert.throws(()=>graphShadings(`[s] ${source}`,'cartesian'));
});

test('shading bands combine x and y restrictions regardless of input order',()=>{
  for(const source of ['sin(x), cos(x), -1<x<2, y<0','y<0, -1<x<2, sin(x), cos(x)']){
    const [item]=graphShadings(`[s] ${source}`,'cartesian');
    assert.equal(item.mode,'band');assert.deepEqual(item.trees.map(tree=>tree.value),['sin','cos']);
    assert.deepEqual(item.xBounds.map(bound=>bound.side),['lower','upper']);
    assert.equal(item.yBounds.length,1);assert.equal(item.yBounds[0].side,'upper');assert.equal(item.yBounds[0].tree.value,'0');
  }
  const [item]=graphShadings('[shade] x, 2, -1<y<0','cartesian');
  assert.deepEqual(item.yBounds.map(bound=>bound.side),['lower','upper']);
});

test('shading-only alias plots and removes without consuming a curve slot',async t=>{
  const {workspace,byId,requests,remove,errors}=removalPage(t,'[s] 1<x<3, 1<y<3');
  await workspace.run();
  assert.equal(requests[0].trees.length,0);assert.equal(requests[0].shadings[0].mode,'region');
  assert.equal(byId('graph-selected').children.length,0);
  remove(0);assert.equal(byId('graph-source').value,'');assert.deepEqual(errors,[]);
});

test('Cartesian y-intercept runs at zero without using range fields',async t=>{
  const {workspace,byId,requests,edit}=setup(t);
  edit('graph-analysis-a','');edit('graph-analysis-b','');
  byId('graph-analysis-action').value='yintercept';byId('graph-analysis-action').onchange();
  for(const id of ['graph-analysis-a','graph-analysis-b'])assert.equal(byId(id).closest('label').hidden,true);
  assert.equal(byId('graph-analysis-a-slider').parentElement.hidden,true);
  byId('graph-analysis-run').click();await Promise.resolve();await Promise.resolve();
  assert.equal(requests.at(-1).analysis,'yintercept');assert.equal(requests.at(-1).a,0);assert.equal(requests.at(-1).b,0);
  byId('graph-analysis-action').value='root';byId('graph-analysis-action').onchange();
  assert.equal(byId('graph-analysis-a').closest('label').hidden,false);
});

function expectCenteredViewport(byId){
  for(const [minId,maxId] of [['graph-min','graph-max'],['graph-ymin','graph-ymax']]){
    const first=byId(minId+'-slider'),second=byId(maxId+'-slider'),low=Number(first.min),high=Number(first.max);
    assert.ok(Math.abs((Number(first.value)-low)/(high-low)-.25)<1e-10,minId);
    assert.ok(Math.abs((Number(second.value)-low)/(high-low)-.75)<1e-10,maxId);
  }
}

test('Clear analysis removes integral errors and ignores failures from cleared requests',async t=>{
  const dom=new JSDOM(readFileSync(new URL('../index.html',import.meta.url),'utf8'));
  globalThis.document=dom.window.document;installCanvas(dom);
  const byId=id=>document.getElementById(id),errors=[];
  const message='Failed to distinguish the expression: Integral(sin(x), (x, -10.0, 10.0)) from zero. Try simplifying the input, using chop=True, or providing a higher maxn for evalf';
  let respond=async()=>({ok:false,error:message});
  const workspace=createGraphWorkspace({
    execute:request=>request.action==='graphAnalysis'?respond():Promise.resolve({ok:true,curves:[[[-10,0],[10,0]]],parameters:[]}),
    options:()=>({displayDigits:10}),persist:()=>{},isBusy:()=>false,
    onError:error=>{errors.push(error);byId('answer').textContent=error;},
    onClearError:()=>byId('answer').replaceChildren(),
  });
  t.after(()=>{workspace.dispose();dom.window.close();});
  byId('graph-source').value='sin(x)';await workspace.run();
  byId('graph-analysis-action').value='integral';
  await byId('graph-analysis-run').onclick();
  assert.equal(byId('graph-status').textContent,message);assert.equal(byId('answer').textContent,message);
  assert.equal(byId('graph-status').classList.contains('error'),true);
  byId('graph-analysis-clear').click();
  const expectCleared=()=>{
    for(const id of ['graph-status','graph-analysis-result','graph-trace','answer'])assert.equal(byId(id).textContent,'',id);
    assert.equal(byId('graph-status').classList.contains('error'),false);
  };
  expectCleared();
  for(const reject of [false,true]){
    let finish;respond=()=>new Promise((resolve,rejectPromise)=>{finish=reject?()=>rejectPromise(new Error(message)):()=>resolve({ok:false,error:message});});
    const pending=byId('graph-analysis-run').onclick();byId('graph-analysis-clear').click();finish();await pending;
    expectCleared();assert.equal(errors.length,1,'cleared requests must not restore errors');
  }
  respond=async()=>({ok:true,value:0,points:[]});await byId('graph-analysis-run').onclick();
  assert.notEqual(byId('graph-analysis-result').textContent,'');
  byId('graph-analysis-clear').click();expectCleared();
});

test('incomplete or invalid typed ranges preserve the slider domain until valid',t=>{
  const {byId,workspace,edit}=setup(t),slider=byId('graph-min-slider');
  edit('graph-min',40);edit('graph-max',60);
  for(const value of ['', '-', '60', '70', 'Infinity']){
    edit('graph-min',value);
    assert.equal(Number(slider.min),30);assert.equal(Number(slider.max),70);
  }
  edit('graph-min',-100.1234567890123);edit('graph-max',-80.1234567890123);
  assert.equal(workspace.snapshot().ranges['graph-min'],-100.1234567890123);
  assert.equal(Number(slider.min),-110.1234567890123);
  assert.equal(Number(slider.max),-70.1234567890123);
  assert.equal(slider.parentElement.style.getPropertyValue('--range-start'),'25%');
  assert.equal(slider.parentElement.style.getPropertyValue('--range-end'),'75%');
});

test('analysis and tangent domains follow pan, zoom, manual ranges, and Reset',async t=>{
  const {byId,workspace,edit,expectDomain}=setup(t);
  await workspace.run();expectDomain(-10,10);expectCenteredViewport(byId);
  edit('graph-analysis-a',-2);edit('graph-analysis-b',3);
  byId('graph-right').click();expectDomain(-7,13);expectCenteredViewport(byId);
  byId('graph-zoom-in').click();expectDomain(-2,8);expectCenteredViewport(byId);
  assert.equal(workspace.snapshot().ranges['graph-analysis-a'],-2);
  assert.equal(workspace.snapshot().ranges['graph-analysis-b'],3);
  edit('graph-min',40);edit('graph-max',60);expectDomain(40,60);
  assert.equal(Number(byId('graph-analysis-a-slider').value),40,'offscreen values clamp visually without changing the field');
  assert.equal(workspace.snapshot().ranges['graph-analysis-a'],-2);
  byId('graph-analysis-visible-range').click();
  assert.equal(workspace.snapshot().ranges['graph-analysis-a'],40);
  assert.equal(workspace.snapshot().ranges['graph-analysis-b'],60);
  byId('graph-analysis-action').value='tangent';byId('graph-analysis-action').onchange();
  edit('graph-analysis-a',55);assert.equal(Number(byId('graph-tangent-slider').value),55);
  byId('graph-reset').click();expectDomain(-10,10);expectCenteredViewport(byId);
  assert.equal(Number(byId('graph-tangent-slider').value),10);
});

test('restored offscreen analysis fields do not widen the visible domain',t=>{
  const {workspace,byId,expectDomain}=setup(t,{ranges:{'graph-min':20,'graph-max':30,'graph-analysis-a':-5,'graph-analysis-b':50}});
  expectDomain(20,30);
  assert.equal(workspace.snapshot().ranges['graph-analysis-a'],-5);
  assert.equal(Number(byId('graph-analysis-a-slider').value),20);
  assert.equal(Number(byId('graph-analysis-b-slider').value),30);
  byId('graph-analysis-a-slider').value='25';byId('graph-analysis-a-slider').oninput();
  expectDomain(20,30);assert.equal(workspace.snapshot().ranges['graph-analysis-a'],25);
});

function removalPage(t,source,kind='cartesian',execute){
  const dom=new JSDOM(readFileSync(new URL('../index.html',import.meta.url),'utf8'));
  globalThis.document=dom.window.document;installCanvas(dom);
  const byId=id=>document.getElementById(id),requests=[],errors=[];let saves=0;
  byId('graph-kind').value=kind;byId('graph-source').value=source;
  const workspace=createGraphWorkspace({
    execute:async request=>{requests.push(request);return execute?execute(request):{ok:true,curves:request.trees.map((_,i)=>[[0,i],[1,i+1]]),parameters:[]};},
    options:()=>({displayDigits:10}),onError:error=>errors.push(error),persist:()=>saves++,isBusy:()=>false,
  });
  t.after(()=>{workspace.dispose();dom.window.close();});
  const remove=index=>byId('graph-formulas').children[index].querySelector('.graph-formula-remove').click();
  return {byId,workspace,requests,errors,remove,saves:()=>saves};
}

test('delete buttons remove only their curve, preserve the selected curve, and persist',async t=>{
  const {byId,workspace,remove,saves}=removalPage(t,'x\n[shade] x, 0\nx+1\nx+2');
  await workspace.run();
  byId('graph-formulas').children[2].querySelector('.graph-formula-select').click();
  assert.equal(byId('graph-selected').value,'2');
  const before=saves();remove(0);
  assert.equal(byId('graph-source').value,'[shade] x, 0\nx+1\nx+2');
  assert.equal(byId('graph-selected').value,'1','the same remaining curve stays selected');
  assert.notEqual(byId('graph-other').value,byId('graph-selected').value);
  assert.equal(byId('graph-plot').children.length,0,'old curves disappear immediately');
  assert.ok(saves()>before);
  assert.equal(workspace.snapshot().sources.cartesian,byId('graph-source').value);
  await workspace.run();
  remove(2);
  assert.equal(byId('graph-source').value,'x+1\nx+2','shading has its own delete button');
});

for(const [kind,source] of [['cartesian','x'],['surface','x+y']]){
  test(`deleting the last ${kind} graph clears the plot without an empty-input error`,async t=>{
    const {byId,workspace,remove,requests,errors}=removalPage(t,source,kind);
    await workspace.run();remove(0);await workspace.run();
    assert.equal(byId('graph-source').value,'');
    for(const id of ['graph-plot','graph-formulas','graph-table','graph-trace','graph-analysis-result'])assert.equal(byId(id).children.length,0,id);
    assert.equal(byId('graph-status').textContent,'');
    assert.equal(requests.length,1,'empty lists do not request computation');
    assert.deepEqual(errors,[]);
  });
}

test('an in-flight plot cannot restore a deleted graph',async t=>{
  let resolve;
  const {workspace,byId,remove}=removalPage(t,'x','cartesian',()=>new Promise(done=>resolve=done));
  const pending=workspace.run();remove(0);
  resolve({ok:true,curves:[[[0,0],[1,1]]],parameters:[]});
  await pending;
  assert.equal(byId('graph-plot').children.length,0);
  assert.equal(byId('graph-formulas').children.length,0);
});

test('typed parameter values and bounds center sliders, and reset restores value and bounds',async t=>{
  const dom=new JSDOM(readFileSync(new URL('../index.html',import.meta.url),'utf8'));
  globalThis.document=dom.window.document;installCanvas(dom);
  const byId=id=>document.getElementById(id);let saves=0;
  byId('graph-source').value='a*x';
  const workspace=createGraphWorkspace({execute:async()=>({ok:true,curves:[[[0,0],[1,1]]],parameters:['a']}),options:()=>({displayDigits:3}),onError:assert.fail,persist:()=>saves++,isBusy:()=>false});
  t.after(()=>{workspace.dispose();dom.window.close();});
  await workspace.run();
  const edit=(selector,value)=>{const input=byId('graph-parameters').querySelector(selector);input.value=String(value);input.dispatchEvent(new dom.window.Event('change'));};
  const expectSlider=(low,high,value)=>{
    const slider=byId('graph-parameters').querySelector('input[type="range"]');
    assert.ok(Math.abs(Number(slider.min)-low)<1e-12);assert.ok(Math.abs(Number(slider.max)-high)<1e-12);assert.equal(Number(slider.value),value);
    assert.equal(workspace.snapshot().parameters.a,value);
    workspace.snapshot().parameterRanges.a.forEach((bound,i)=>assert.ok(Math.abs(bound-[low,high][i])<1e-12));
  };
  edit('[data-parameter-value]',12.345678901);expectSlider(7.345678901,17.345678901,12.345678901);
  edit('[data-parameter-value]',10);expectSlider(5,15,10);
  edit('[data-parameter-bound="0"]',-5);expectSlider(-5,15,5);
  edit('[data-parameter-bound="1"]',35);expectSlider(-5,35,15);
  edit('[data-parameter-value]',100);expectSlider(80,120,100);
  const before=saves;byId('graph-reset-parameters').click();expectSlider(-5,5,1);assert.ok(saves>before);
});

test('Plot and Analyze stay disabled across animation frames and recover after Stop',async()=>{
  const dom=new JSDOM(readFileSync(new URL('../index.html',import.meta.url),'utf8'));
  globalThis.document=dom.window.document;installCanvas(dom);
  const byId=id=>document.getElementById(id),frames=new Map(),requests=[],resolvers=[];
  let busy=false,ready=true,frameId=0,workspace;
  dom.window.requestAnimationFrame=callback=>{frames.set(++frameId,callback);return frameId;};
  dom.window.cancelAnimationFrame=id=>frames.delete(id);
  workspace=createGraphWorkspace({
    execute:request=>{
      assert.equal(resolvers.length,0,'frame computations must remain serialized');
      requests.push(request);busy=true;workspace.updateButtons();
      return new Promise(resolve=>resolvers.push(resolve));
    },
    options:()=>({displayDigits:10}),onError:assert.fail,persist:()=>{},isBusy:()=>busy,isReady:()=>ready,
    saved:{parameters:{a:1}},
  });
  const complete=async()=>{
    const resolve=resolvers.shift();busy=false;workspace.updateButtons();
    resolve({ok:true,curves:[[[0,0],[1,1]]],parameters:['a']});
    for(let i=0;i<4;i++)await Promise.resolve();
  };
  try{
    byId('graph-source').value='a*x';const initial=workspace.run();await complete();await initial;
    const buttons=[document.querySelector('[data-run="graph"]'),byId('graph-analysis-run')],changes=buttons.map(()=>[]);
    const disabled=Object.getOwnPropertyDescriptor(dom.window.HTMLButtonElement.prototype,'disabled');
    buttons.forEach((button,index)=>Object.defineProperty(button,'disabled',{
      get(){return disabled.get.call(this);},
      set(next){changes[index].push(next);disabled.set.call(this,next);},
    }));
    byId('graph-animate').click();buttons.forEach(button=>assert.equal(button.disabled,true));
    for(let frame=0;frame<60;frame++){
      const batch=[...frames.values()];frames.clear();for(const callback of batch)callback(frame*1000/60);
      assert.equal(busy,true);workspace.updateButtons();buttons.forEach(button=>assert.equal(button.disabled,true));
      await complete();buttons.forEach(button=>assert.equal(button.disabled,true));
    }
    assert.equal(requests.filter(request=>request.samples===200).length,60,'animation still updates at 60 frames per second');
    changes.forEach(values=>assert.deepEqual(values,[true],'engine busy/idle cycles must not toggle or rewrite button state'));
    ready=false;workspace.updateButtons();ready=true;workspace.updateButtons();
    changes.forEach(values=>assert.deepEqual(values,[true]));
    assert.equal(byId('graph-animate').disabled,false,'Stop remains available');
    byId('graph-animate').click();assert.equal(requests.at(-1).samples,500);
    buttons.forEach(button=>assert.equal(button.disabled,true,'final refinement is still running'));
    await complete();buttons.forEach(button=>assert.equal(button.disabled,false));
    changes.forEach(values=>assert.deepEqual(values,[true,false]));
    const manual=workspace.run();buttons.forEach(button=>assert.equal(button.disabled,true));
    await complete();await manual;buttons.forEach(button=>assert.equal(button.disabled,false));
    ready=false;workspace.updateButtons();buttons.forEach(button=>assert.equal(button.disabled,true));
    ready=true;workspace.updateButtons();buttons.forEach(button=>assert.equal(button.disabled,false));
    byId('graph-animate').click();workspace.activate(false);buttons.forEach(button=>assert.equal(button.disabled,false,'leaving Graph releases the animation lock'));
  }finally{workspace.dispose();dom.window.close();}
});

test('named Cartesian graph inputs keep their label and normalize only function definitions',()=>{
  for(const source of ['f(x)=x+1','g(x)=sin(x)','f2(x)=2*x^2']){
    assert.deepEqual(graphInputTree(source),parse(source).args[1]);
    assert.equal(cartesianFormula(source),source);
  }
  for(const source of ['y=x+1','sin(x)=0','x^2+y^2=1'])assert.deepEqual(graphInputTree(source),parse(source));
  assert.deepEqual(graphInputTree('f(x)=x+1','implicit'),parse('f(x)=x+1'));
});
