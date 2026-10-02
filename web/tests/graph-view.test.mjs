import {installCanvas,surfaceFills} from './canvas-context.mjs';
import test from 'node:test';
import assert from 'node:assert/strict';
import {JSDOM} from 'jsdom';
import {transformBounds,pinchFactors,nearestPoint,bindGraphGestures} from '../graph-view.js';
import {graphExpressions,graphShadings,implicitFormula,cartesianFormula,graphExpressionTarget,createGraphWorkspace} from '../graph-workspace.js';
import {readFileSync} from 'node:fs';
import {plot} from '../plot.js';
import {expressionDisplay} from '../expression-display.js';
import {markInputCursor} from '../input-cursor.js';

test('pan and zoom preserve the cursor anchor, limit spans, and lock pinch axes as Android does',()=>{
  const bounds={xmin:-10,xmax:10,ymin:-5,ymax:5};
  assert.deepEqual(transformBounds(bounds,{x:.5,y:.5},{x:.5,y:.5},2),{xmin:-5,xmax:5,ymin:-2.5,ymax:2.5});
  assert.deepEqual(transformBounds(bounds,{x:.5,y:.5},{x:.6,y:.5},1),{xmin:-12,xmax:8,ymin:-5,ymax:5});
  assert.equal(pinchFactors({x:100,y:10},{x:200,y:20}).axis,'x');
  assert.deepEqual(pinchFactors({x:100,y:10},{x:200,y:20}),{axis:'x',zx:2,zy:1});
  const next=transformBounds(bounds,{x:0,y:1},{x:0,y:1},4);assert.equal(next.xmin,bounds.xmin);assert.equal(next.ymin,bounds.ymin);
  assert.equal(nearestPoint({curves:[[[0,1],null,[2,3]]],curveParameters:[[.1,null,.2]]},bounds,{x:.6,y:.2}).parameter,.2);
});
test('pointer pan commits once after dragging, taps trace, wheel zooms, and disposal removes gestures',()=>{
  const dom=new JSDOM('<div></div>'),container=dom.window.document.querySelector('div');container.getBoundingClientRect=()=>({left:0,top:0,width:800,height:460});
  let bounds={xmin:-10,xmax:10,ymin:-5,ymax:5},commits=0,traces=0;
  const dispose=bindGraphGestures(container,{getBounds:()=>bounds,onView:(next,commit)=>{bounds=next;if(commit)commits++;},onTrace:()=>traces++});
  const event=(type,x,y)=>{const e=new dom.window.MouseEvent(type,{clientX:x,clientY:y,button:0,cancelable:true});Object.defineProperty(e,'pointerId',{value:1});container.dispatchEvent(e);};
  event('pointerdown',400,230);event('pointermove',440,230);event('pointerup',440,230);assert.equal(commits,1);assert.ok(bounds.xmin<-10);
  event('pointerdown',400,230);event('pointerup',400,230);assert.equal(traces,1);
  const width=bounds.xmax-bounds.xmin;container.dispatchEvent(new dom.window.WheelEvent('wheel',{clientX:400,clientY:230,deltaY:-100,cancelable:true}));assert.ok(bounds.xmax-bounds.xmin<width);
  dispose();event('pointerdown',400,230);event('pointerup',400,230);assert.equal(traces,1);dom.window.close();
});
test('graph formulas strip prefixes and shading preserves commas nested in function arguments',()=>{
  assert.deepEqual(graphExpressions('y=sqrt(x)\n[shade] y<x^2','cartesian'),['y=sqrt(x)']);
  assert.equal(graphShadings('[shade] log(x,2), sqrt(x), 0..4','cartesian')[0].trees.length,2);
  assert.equal(graphShadings('[shade] x^2>y','cartesian')[0].side,'below');
});

test('implicit equations preserve both sides and separate contour segments in the renderer',()=>{
  assert.deepEqual(graphExpressions('x^2+y^2=1\nx=2','implicit'),['x^2+y^2=1','x=2']);
  assert.equal(implicitFormula('x^2+y^2=1'),'x^2+y^2=1');
  assert.equal(implicitFormula('x*y-1'),'x*y-1=0');
  assert.equal(implicitFormula('x^'),'x^');
  const dom=new JSDOM('<div id="plot"></div>');globalThis.document=dom.window.document;installCanvas(dom);
  plot(document.getElementById('plot'),{implicit:true,curves:[[[0,1],[1,0],null,[0,-1],[-1,0],null],[[.5,-1],[.5,1],null]]},{xmin:-2,xmax:2,ymin:-2,ymax:2});
  const paths=document.querySelectorAll('[data-curve]');assert.equal(paths.length,2);
  const circlePath=document.querySelector('[data-curve="0"]');assert.equal(circlePath.getAttribute('d').match(/M/g).length,2);
  assert.notEqual(paths[0].getAttribute('stroke'),paths[1].getAttribute('stroke'));dom.window.close();
});

test('Cartesian accepts implicit equations, exposes analysis, preserves both sides and saves its draft',async()=>{
  const dom=new JSDOM(readFileSync(new URL('../index.html',import.meta.url),'utf8'));globalThis.document=dom.window.document;installCanvas(dom);
  const $=id=>document.getElementById(id),requests=[];
  const workspace=createGraphWorkspace({execute:async request=>{requests.push(request);return {ok:true,implicit:true,curves:[[[0,1],[1,0],null]],parameters:[]};},options:()=>({displayDigits:10}),onError:message=>assert.fail(message),persist:()=>{},isBusy:()=>false});
  $('graph-source').value='x^2+y^2=1';$('graph-source').dispatchEvent(new dom.window.Event('input'));
  assert.equal($('graph-source').value,'x^2+y^2=1');
  assert.equal($('graph-analysis').hidden,false);assert.equal($('graph-viewport-ranges').hidden,true);
  assert.equal($('graph-cartesian-help').hidden,false);assert.match($('graph-cartesian-help').textContent,/Function \/ y=f\(x\).*Implicit \/ F\(x,y\)=0/);
  $('graph-min').value='-.5';$('graph-max').value='.5';$('graph-ymin').value='-1.5';$('graph-ymax').value='1.5';
  await workspace.run();assert.equal(requests[0].graphKind,'cartesian');assert.equal(requests[0].variable,'x');
  assert.equal(requests[0].min,-.5);assert.equal(requests[0].max,.5);assert.equal(requests[0].yMin,-1.5);assert.equal(requests[0].yMax,1.5);
  assert.equal(requests[0].trees[0].kind,'relation');assert.equal($('graph-formulas').textContent.includes('f1'),false);
  assert.equal(workspace.snapshot().sources.cartesian,'x^2+y^2=1');
  $('graph-kind').value='parametric';$('graph-kind').onchange();assert.equal($('graph-cartesian-help').hidden,true);
  $('graph-kind').value='cartesian';$('graph-kind').onchange();assert.equal($('graph-source').value,'x^2+y^2=1');
  workspace.dispose();dom.window.close();
});

test('shared range sliders update both endpoints, prevent crossing, and keep exact graph bounds',async()=>{
  const dom=new JSDOM(readFileSync(new URL('../index.html',import.meta.url),'utf8'));globalThis.document=dom.window.document;installCanvas(dom);
  const $=id=>document.getElementById(id),requests=[];let saves=0;
  const workspace=createGraphWorkspace({execute:async request=>{requests.push(request);return {ok:true,curves:[[[-1,-1],[0,0],[1,1]]],parameters:[]};},options:()=>({displayDigits:3}),onError:message=>assert.fail(message),persist:()=>saves++,isBusy:()=>false});
  try{
    const move=(id,number)=>{const slider=$(id+'-slider');slider.value=String(number);slider.dispatchEvent(new dom.window.Event('input'));slider.dispatchEvent(new dom.window.Event('change'));};
    for(const [low,high] of [['graph-min','graph-max'],['graph-ymin','graph-ymax'],['graph-xmin','graph-xmax']]){
      assert.equal($(low+'-slider').parentElement,$(high+'-slider').parentElement,'both handles share one track');
      assert.ok($(low+'-slider').getAttribute('aria-label'));assert.ok($(high+'-slider').getAttribute('aria-label'));
      move(low,-1.234567);move(high,2.345678);
      assert.equal($(low).value,'-1.235');assert.equal($(high).value,'2.346');
      assert.equal(workspace.snapshot().ranges[low],-1.234567);assert.equal(workspace.snapshot().ranges[high],2.345678);
      move(low,10);assert.ok(workspace.snapshot().ranges[low]<workspace.snapshot().ranges[high]);
      move(high,-10);assert.ok(workspace.snapshot().ranges[low]<workspace.snapshot().ranges[high]);
      move(low,-1.234567);move(high,2.345678);
    }
    assert.equal(saves,18);
    await workspace.run();assert.equal(requests.at(-1).min,-1.234567);assert.equal(requests.at(-1).max,2.345678);assert.equal(requests.at(-1).yMin,-1.234567);assert.equal(requests.at(-1).yMax,2.345678);
    $('graph-min').value='-100';$('graph-min').dispatchEvent(new dom.window.Event('change'));
    assert.equal($('graph-min-slider').min,'-100');assert.equal($('graph-max-slider').min,'-100');assert.equal($('graph-min-slider').value,'-100');
    $('graph-max').value='120';$('graph-max').dispatchEvent(new dom.window.Event('change'));
    assert.equal($('graph-min-slider').max,'120');assert.equal($('graph-max-slider').max,'120');
    $('graph-reset').click();assert.equal($('graph-min-slider').value,'-10');assert.equal($('graph-max-slider').value,'10');
    $('graph-zoom-in').click();assert.equal($('graph-min-slider').value,'-5');assert.equal($('graph-max-slider').value,'5');
    $('graph-kind').value='parametric';$('graph-kind').onchange();assert.equal($('graph-viewport-ranges').hidden,false);
    assert.equal($('graph-min-slider').value,'0');assert.equal(Number($('graph-max-slider').value),2*Math.PI);
    move('graph-xmin',-2);move('graph-xmax',2);assert.equal(workspace.snapshot().ranges['graph-xmin'],-2);assert.equal(workspace.snapshot().ranges['graph-xmax'],2);
  }finally{workspace.dispose();dom.window.close();}
});

test('shared range sliders restore saved endpoints outside the default slider domain',()=>{
  const dom=new JSDOM(readFileSync(new URL('../index.html',import.meta.url),'utf8'));globalThis.document=dom.window.document;installCanvas(dom);
  const $=id=>document.getElementById(id),ranges={'graph-min':1000.123456,'graph-max':1020.654321,'graph-ymin':-200,'graph-ymax':-100};
  const workspace=createGraphWorkspace({execute:async()=>({ok:true,curves:[],parameters:[]}),options:()=>({displayDigits:3}),onError:message=>assert.fail(message),persist:()=>{},isBusy:()=>false,saved:{ranges}});
  try{
    for(const [id,number] of Object.entries(ranges))assert.equal(Number($(id+'-slider').value),number);
    assert.equal($('graph-min-slider').max,$('graph-max-slider').max);assert.equal($('graph-ymin-slider').min,$('graph-ymax-slider').min);
    for(const [id,number] of Object.entries(ranges))assert.equal(workspace.snapshot().ranges[id],number);
  }finally{workspace.dispose();dom.window.close();}
});
test('Cartesian formulas and calculator transfers keep equations and distinguish explicit 3D surfaces',()=>{
  for(const source of ['x+1','y=x+1','y^2+x^2=1','x*y-1','x=2'])assert.deepEqual(graphExpressionTarget(source),{kind:'cartesian',source});
  assert.deepEqual(graphExpressionTarget('z=x^2+y^2'),{kind:'surface',source:'x^2+y^2'});
  assert.throws(()=>graphExpressionTarget('x<y'),/Graph an expression/);
  assert.equal(cartesianFormula('x+1'),'f1(x)=x+1');assert.equal(cartesianFormula('y=x+1'),'y=x+1');
  assert.equal(cartesianFormula('y^2+x^2=1'),'y^2+x^2=1');assert.equal(cartesianFormula('x*y-1'),'x*y-1=0');
});
test('typed graph parameters keep exact values, expand slider ranges, plot and restore',async()=>{
  const dom=new JSDOM(readFileSync(new URL('../index.html',import.meta.url),'utf8'));globalThis.document=dom.window.document;installCanvas(dom);
  const requests=[];let saves=0;
  const create=saved=>createGraphWorkspace({execute:async request=>{requests.push(request);return {ok:true,curves:[],parameters:['a','b']};},options:()=>({displayDigits:3}),onError:assert.fail,persist:()=>saves++,isBusy:()=>false,saved});
  let workspace=create({parameters:{a:1,b:2},animationEnabled:{a:false}});
  const field=name=>document.querySelector(`[data-parameter-value="${name}"]`),slider=()=>document.querySelector('#graph-parameters input[type="range"]');
  try{
    await workspace.run();const input=field('a');assert.match(input.getAttribute('aria-label'),/a/);
    input.value='12.345678901';input.dispatchEvent(new dom.window.Event('change'));
    assert.equal(workspace.snapshot().parameters.a,12.345678901);assert.deepEqual(workspace.snapshot().parameterRanges.a,[-5,12.345678901]);
    assert.equal(Number(slider().value),12.345678901);assert.equal(Number(slider().max),12.345678901);assert.ok(saves>=2);
    await workspace.run();assert.equal(requests.at(-1).parameters.a,12.345678901);assert.equal(field('a'),input);assert.equal(input.value,'12.345678901','editing uses full precision despite rounded captions');
    input.value='-2e1';input.dispatchEvent(new dom.window.KeyboardEvent('keydown',{key:'Enter',cancelable:true}));
    assert.equal(workspace.snapshot().parameters.a,-20);assert.deepEqual(workspace.snapshot().parameterRanges.a,[-20,12.345678901]);
    slider().value='0.123456789';slider().dispatchEvent(new dom.window.Event('input'));assert.equal(input.value,'0.123456789');
    const saved=workspace.snapshot();workspace.dispose();workspace=create(saved);await workspace.run();
    assert.equal(field('a').value,'0.123456789');assert.equal(Number(slider().min),-20);assert.equal(Number(slider().max),12.345678901);assert.equal(workspace.snapshot().animationEnabled.a,false);
    document.querySelector('[data-parameter-bound="1"]').value='-10';document.querySelector('[data-parameter-bound="1"]').dispatchEvent(new dom.window.Event('change'));
    assert.equal(field('a').value,'-10');assert.equal(workspace.snapshot().parameters.a,-10);
  }finally{workspace.dispose();dom.window.close();}
});
test('graph parameter drafts survive redraw and invalid input never changes the plotted value',async()=>{
  const dom=new JSDOM(readFileSync(new URL('../index.html',import.meta.url),'utf8'));globalThis.document=dom.window.document;installCanvas(dom);
  const errors=[];
  const workspace=createGraphWorkspace({execute:async()=>({ok:true,curves:[],parameters:['a']}),options:()=>({displayDigits:3}),onError:message=>errors.push(message),persist:()=>{},isBusy:()=>false,saved:{parameters:{a:1.23456789}}});
  try{
    await workspace.run();const input=document.querySelector('[data-parameter-value="a"]');input.focus();input.value='-';workspace.render();await workspace.run();
    assert.equal(document.activeElement,input);assert.equal(input.value,'','number input retains its unfinished draft instead of being overwritten');
    for(const invalid of ['', 'NaN', 'Infinity', '1e999', '1e10']){
      input.value=invalid;input.dispatchEvent(new dom.window.Event('change'));
      assert.equal(workspace.snapshot().parameters.a,1.23456789);assert.equal(input.getAttribute('aria-invalid'),'true');
    }
    assert.equal(errors.length,5);input.dispatchEvent(new dom.window.KeyboardEvent('keydown',{key:'Escape',cancelable:true}));
    assert.equal(input.value,'1.23456789');assert.equal(input.hasAttribute('aria-invalid'),false);
    input.value='0';input.dispatchEvent(new dom.window.Event('change'));assert.equal(workspace.snapshot().parameters.a,0);
    input.blur();workspace.render();assert.equal(input.value,'0');
  }finally{workspace.dispose();dom.window.close();}
});
test('analysis captures current parameter values and discards a result after those values change',async()=>{
  const dom=new JSDOM(readFileSync(new URL('../index.html',import.meta.url),'utf8'));globalThis.document=dom.window.document;installCanvas(dom);
  const requests=[],resolvers=[],$=id=>document.getElementById(id);
  const workspace=createGraphWorkspace({execute:async request=>{requests.push(request);return request.action==='graphAnalysis'?new Promise(resolve=>resolvers.push(resolve)):{ok:true,curves:[],parameters:['a']};},options:()=>({displayDigits:3}),onError:assert.fail,persist:()=>{},isBusy:()=>false,saved:{parameters:{a:2}}});
  try{
    $('graph-source').value='a*x';await workspace.run();const first=$('graph-analysis-run').onclick();
    assert.deepEqual(requests.at(-1).parameters,{a:2});
    const input=document.querySelector('[data-parameter-value="a"]');input.value='5';input.dispatchEvent(new dom.window.Event('change'));
    assert.deepEqual(requests.at(-1).parameters,{a:2},'an in-flight request retains its original values');
    resolvers.shift()({ok:true,analysis:'root',points:[[0,2]]});await first;assert.equal($('graph-analysis-result').textContent,'');
    const latest=$('graph-analysis-run').onclick();assert.deepEqual(requests.at(-1).parameters,{a:5});
    resolvers.shift()({ok:true,analysis:'root',points:[[0,5]]});await latest;assert.match($('graph-analysis-result').textContent,/5/);
  }finally{workspace.dispose();dom.window.close();}
});
test('moving a tangent while the engine is sampling queues the latest position instead of losing it',async()=>{
  const dom=new JSDOM(readFileSync(new URL('../index.html',import.meta.url),'utf8'));globalThis.document=dom.window.document;installCanvas(dom);
  let busy=false;const requests=[],$=id=>document.getElementById(id);
  const workspace=createGraphWorkspace({execute:async request=>{requests.push(request);return request.action==='graphAnalysis'?{ok:true,analysis:'tangent',points:[[request.a,request.a**2]],value:2*request.a}:{ok:true,curves:[],parameters:[]};},options:()=>({displayDigits:3}),onError:assert.fail,persist:()=>{},isBusy:()=>busy});
  try{
    $('graph-source').value='x^2';await workspace.run();busy=true;
    for(const value of ['.25','.5']){$('graph-tangent-slider').value=value;$('graph-tangent-slider').dispatchEvent(new dom.window.Event('input'));$('graph-tangent-slider').dispatchEvent(new dom.window.Event('change'));}
    assert.equal(requests.length,1);busy=false;workspace.flush();await Promise.resolve();await Promise.resolve();
    assert.equal(requests.length,2);assert.equal(requests[1].a,.5);assert.match($('graph-analysis-result').textContent,/0.5/);
  }finally{workspace.dispose();dom.window.close();}
});
test('MathML cursor is visible inside root and fractional tokens without losing empty-slot styling',()=>{
  const dom=new JSDOM();globalThis.document=dom.window.document;installCanvas(dom);
  const math=expressionDisplay('sqrt(123)');markInputCursor(math,'sqrt(123)',6);assert.equal(math.parentElement.querySelector('.input-caret').getAttribute('data-source-start'),'6');assert.equal(math.textContent,'123');
  const empty=expressionDisplay('sqrt()');markInputCursor(empty,'sqrt()',5);assert.ok(empty.querySelector('.input-slot'));
  const outside=expressionDisplay('sqrt(123)');markInputCursor(outside,'sqrt(123)',0);assert.equal(outside.parentElement.querySelector('.input-caret').parentElement,outside.parentElement,'root-boundary caret is outside the math layout');
  const nested=expressionDisplay('sqrt(5)/2');markInputCursor(nested,'sqrt(5)/2',0);assert.equal(nested.querySelector('mfrac').children.length,2,'caret wrappers preserve fraction arity');
  const selected=expressionDisplay('1/3');markInputCursor(selected,'1/3',0,1);assert.ok(selected.querySelector('.selected'));dom.window.close();
});
test('SVG redraw retains discontinuities and draws trace, tangent, integration shading, and adjustable surface projection',()=>{
  const dom=new JSDOM('<div id="plot"></div>');globalThis.document=dom.window.document;installCanvas(dom);const container=document.getElementById('plot'),bounds={xmin:-2,xmax:2,ymin:-2,ymax:2};
  plot(container,{curves:[[[-1,-1],null,[0,0],[1,1]]]},bounds,{trace:[1,1],analysis:{line:[[-2,-2],[2,2]],points:[[0,0]]},integral:[0,1],digits:2});
  assert.equal(container.querySelector('path').getAttribute('d').match(/M/g).length,2);assert.ok(container.querySelector('[data-trace]'));assert.ok(container.querySelector('[data-tangent]'));assert.ok(container.querySelector('[data-integral]'));
  const result={surface:[[[-1,-1,0],[1,-1,1]],[[-1,1,1],[1,1,0]]],zMin:0,zMax:1};plot(container,result,bounds);const before=container.querySelector('path').getAttribute('d');plot(container,result,bounds,{surfaceView:{rotation:90,elevation:70,zoom:2}});assert.notEqual(container.querySelector('path').getAttribute('d'),before);dom.window.close();
});

test('radian axes use adaptive pi ticks for decimal, zoomed, and panned bounds',()=>{
  const dom=new JSDOM('<div id="plot"></div>');globalThis.document=dom.window.document;installCanvas(dom);const container=document.getElementById('plot'),result={curves:[[[-1,-1],[0,0],[1,1]]]},bounds={xmin:-10,xmax:10,ymin:-5,ymax:5};
  const labels=()=>[...container.querySelectorAll('[data-axis="x"]')].map(label=>label.textContent);
  plot(container,result,bounds);const decimals=labels(),path=container.querySelector('path').getAttribute('d');assert.ok(decimals.every(label=>!label.includes('π')));
  plot(container,result,bounds,{radianAxis:true});assert.ok(labels().includes('π'));assert.ok(labels().includes('-π'));assert.ok(labels().includes('0'));assert.notDeepEqual(labels(),decimals);assert.equal(container.querySelector('path').getAttribute('d'),path);
  for(const [xmin,xmax] of [[-Math.PI/8,Math.PI/8],[.1,.9],[1000,1020]]){plot(container,result,{...bounds,xmin,xmax},{radianAxis:true});assert.ok(labels().length>0&&labels().length<=9);assert.ok(labels().every(label=>label==='0'||label.includes('π')));for(const label of container.querySelectorAll('[data-axis="x"]'))assert.ok(Number(label.getAttribute('x'))>=42-1e-8&&Number(label.getAttribute('x'))<=758+1e-8);}
  plot(container,result,bounds);assert.deepEqual(labels(),decimals);dom.window.close();
});
