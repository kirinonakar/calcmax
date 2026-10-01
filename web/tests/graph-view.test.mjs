import {installCanvas,surfaceFills} from './canvas-context.mjs';
import test from 'node:test';
import assert from 'node:assert/strict';
import {JSDOM} from 'jsdom';
import {transformBounds,pinchFactors,nearestPoint,bindGraphGestures} from '../graph-view.js';
import {graphExpressions,graphShadings,implicitFormula,createGraphWorkspace} from '../graph-workspace.js';
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
  assert.deepEqual(graphExpressions('y=sqrt(x)\n[shade] y<x^2','cartesian'),['sqrt(x)']);
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

test('implicit workspace sends x and y bounds, hides function analysis, and saves its draft',async()=>{
  const dom=new JSDOM(readFileSync(new URL('../index.html',import.meta.url),'utf8'));globalThis.document=dom.window.document;installCanvas(dom);
  const $=id=>document.getElementById(id),requests=[];
  const workspace=createGraphWorkspace({execute:async request=>{requests.push(request);return {ok:true,implicit:true,curves:[[[0,1],[1,0],null]],parameters:[]};},options:()=>({displayDigits:10}),onError:message=>assert.fail(message),persist:()=>{},isBusy:()=>false});
  $('graph-kind').value='implicit';$('graph-kind').onchange();
  assert.equal($('graph-source').value,'x^2+y^2=1');
  assert.equal($('graph-analysis').hidden,true);assert.equal($('graph-viewport-ranges').hidden,true);
  $('graph-min').value='-.5';$('graph-max').value='.5';$('graph-ymin').value='-1.5';$('graph-ymax').value='1.5';
  await workspace.run();assert.equal(requests[0].graphKind,'implicit');assert.equal(requests[0].variable,'x');
  assert.equal(requests[0].min,-.5);assert.equal(requests[0].max,.5);assert.equal(requests[0].yMin,-1.5);assert.equal(requests[0].yMax,1.5);
  assert.equal(requests[0].trees[0].kind,'relation');assert.equal($('graph-formulas').textContent.includes('f1'),false);
  assert.equal(workspace.snapshot().sources.implicit,'x^2+y^2=1');
  $('graph-kind').value='cartesian';$('graph-kind').onchange();assert.equal($('graph-source').value,'sin(x)\ncos(x)');
  $('graph-kind').value='implicit';$('graph-kind').onchange();assert.equal($('graph-source').value,'x^2+y^2=1');
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
