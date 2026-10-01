import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {JSDOM} from 'jsdom';
import {plotGraph} from '../graph-canvas.js';
import {createGraphWorkspace} from '../graph-workspace.js';
import {surfaceProjection,surfaceFaces} from '../surface-geometry.js';
import {installCanvas,surfaceFills} from './canvas-context.mjs';

const bounds={xmin:-2,xmax:2,ymin:-2,ymax:2};
function setup(html='<div id="plot"></div>'){
  const dom=new JSDOM(html);globalThis.document=dom.window.document;installCanvas(dom);return dom;
}
const page=()=>readFileSync(new URL('../index.html',import.meta.url),'utf8');
const options=()=>({displayDigits:10});

test('Canvas reuses one element, scales for DPR, clips paths and preserves null breaks and overlays',()=>{
  const dom=setup(),container=document.getElementById('plot');
  container.getBoundingClientRect=()=>({width:400});dom.window.devicePixelRatio=2;
  try{
    const result={curves:[[[0,1],[1,0],null,[0,-1],[-1,0]]]};
    const canvas=plotGraph(container,result,bounds,{analysis:{line:[[-2,-2],[2,2]],points:[[0,0]]},trace:[1,1],integral:[0,1]});
    const ctx=canvas.getContext('2d'),curve=ctx.commands.find(c=>c.op==='stroke'&&c.lineWidth===4);
    assert.equal(curve.path.filter(p=>p.op==='moveTo').length,2);
    assert.equal(curve.path.filter(p=>p.op==='lineTo').length,2);
    assert.ok(ctx.commands.some(c=>c.op==='clip'));
    assert.ok(ctx.commands.some(c=>c.op==='fill'&&c.path.some(p=>p.op==='closePath')));
    assert.ok(ctx.commands.some(c=>c.op==='arc'&&c.args[2]===6));
    assert.ok(ctx.commands.some(c=>c.op==='setLineDash'&&c.args[0][0]===6));
    assert.equal(canvas.width,800);assert.equal(canvas.height,460);
    assert.equal(plotGraph(container,result,bounds),canvas);assert.equal(container.children.length,1);
    assert.equal(container.querySelector('svg'),null);
    container.getBoundingClientRect=()=>({width:800});plotGraph(container,result,bounds);assert.equal(canvas.width,1600);
    assert.ok(ctx.commands.filter(c=>['moveTo','lineTo','arc'].includes(c.op)).every(c=>c.args.every(Number.isFinite)));
  }finally{dom.window.close();}
});

test('Canvas pi axes, sequence dots, surfaces and underside projection retain their features',()=>{
  const dom=setup(),container=document.getElementById('plot'),box={xmin:-1,xmax:1,ymin:-1,ymax:1,zmin:-1,zmax:1};
  try{
    let canvas=plotGraph(container,{curves:[[[0,0],[1,1]]]},{...bounds,xmin:-10,xmax:10},{radianAxis:true,dots:true});
    let ctx=canvas.getContext('2d');assert.ok(ctx.commands.some(c=>c.op==='fillText'&&c.args[0]==='π'));assert.ok(ctx.commands.some(c=>c.op==='arc'&&c.args[2]===3));
    const mesh=[[[-1,-1,0],[1,-1,1]],[[-1,1,1],[1,1,0]]];
    for(const elevation of [-90,-45,0,45,90]){
      plotGraph(container,{surface:mesh,zMin:0,zMax:1},box,{surfaceView:{elevation,renderMode:'surface-wireframe',color:'#ff0000'}});
      assert.equal(surfaceFills(container).length,2);
      assert.ok(surfaceFills(container).every(c=>/^rgb\(\d+,0,0\)$/.test(c.fillStyle)));
      assert.ok(ctx.commands.some(c=>c.op==='fillText'&&c.args[0]==='z'));
      const projection=surfaceProjection(box,35,elevation),faces=surfaceFaces(mesh,box,projection);
      assert.ok(faces.every((face,i)=>!i||faces[i-1].depth<=face.depth));
      assert.ok(mesh.flat().flatMap(p=>projection.project(p)).every(Number.isFinite));
    }
    assert.ok(surfaceProjection(box,0,-90).project([0,0,1])[2]<0);
    assert.ok(surfaceProjection(box,0,90).project([0,0,1])[2]>0);
    plotGraph(container,{surface:mesh,zMin:0,zMax:1},box,{surfaceView:{renderMode:'wireframe'}});
    assert.equal(surfaceFills(container).length,0);assert.equal(container.querySelectorAll('canvas').length,1);
  }finally{dom.window.close();}
});

test('drag events draw once per frame and reuse formula and coordinate-table DOM',async()=>{
  const dom=setup(page()),$=id=>document.getElementById(id),frames=[];
  dom.window.requestAnimationFrame=callback=>{frames.push(callback);return frames.length;};dom.window.cancelAnimationFrame=()=>{};
  $('graph-plot').getBoundingClientRect=()=>({left:0,top:0,width:800,height:460});
  const workspace=createGraphWorkspace({execute:async()=>({ok:true,curves:[[[0,0],[1,1]]],parameters:[]}),options,onError:assert.fail,persist:()=>{},isBusy:()=>false});
  try{
    await workspace.run();const table=$('graph-table').firstChild,formula=$('graph-formulas').firstChild,ctx=$('graph-plot').firstChild.getContext('2d'),initial=ctx.frames;
    const pointer=(type,x,y)=>{const event=new dom.window.MouseEvent(type,{clientX:x,clientY:y,button:0,cancelable:true});Object.defineProperty(event,'pointerId',{value:1});$('graph-plot').dispatchEvent(event);};
    pointer('pointerdown',400,230);pointer('pointermove',430,230);pointer('pointermove',460,230);pointer('pointermove',490,230);
    assert.equal(ctx.frames,initial);assert.equal(frames.length,1);frames.shift()();assert.equal(ctx.frames,initial+1);
    pointer('pointerup',490,230);assert.equal($('graph-table').firstChild,table);assert.equal($('graph-formulas').firstChild,formula);
  }finally{workspace.dispose();dom.window.close();}
});

test('Animate moves only enabled parameters, handles all-off, and restores choices',async()=>{
  const dom=setup(page()),$=id=>document.getElementById(id);let tick;
  const originalInterval=globalThis.setInterval,originalClear=globalThis.clearInterval;
  globalThis.setInterval=callback=>{tick=callback;return 1;};globalThis.clearInterval=()=>{};
  let saved;
  const create=snapshot=>createGraphWorkspace({execute:async()=>({ok:true,curves:[],parameters:['a','b']}),options,onError:assert.fail,persist:()=>{},isBusy:()=>false,saved:snapshot});
  let workspace=create({parameters:{a:2,b:3}});
  try{
    await workspace.run();const toggle=name=>document.querySelector(`[data-animate-parameter="${name}"]`);
    assert.equal(toggle('a').checked,true);toggle('a').checked=false;toggle('a').onchange();$('graph-animate').click();tick();
    assert.equal(workspace.snapshot().parameters.a,2);assert.notEqual(workspace.snapshot().parameters.b,3);
    toggle('b').checked=false;toggle('b').onchange();const held=workspace.snapshot().parameters;tick();assert.deepEqual(workspace.snapshot().parameters,held);
    toggle('a').checked=true;toggle('a').onchange();tick();assert.notEqual(workspace.snapshot().parameters.a,2);assert.equal(workspace.snapshot().parameters.b,held.b);
    saved=workspace.snapshot();workspace.dispose();workspace=create(saved);await workspace.run();assert.equal(toggle('a').checked,true);assert.equal(toggle('b').checked,false);
  }finally{workspace.dispose();globalThis.setInterval=originalInterval;globalThis.clearInterval=originalClear;dom.window.close();}
});

test('graph requests are serialized and the next request captures the latest viewport',async()=>{
  const dom=setup(page()),$=id=>document.getElementById(id),requests=[],resolvers=[];
  const workspace=createGraphWorkspace({execute:request=>{requests.push(request);return new Promise(resolve=>resolvers.push(resolve));},options,onError:assert.fail,persist:()=>{},isBusy:()=>false});
  try{
    const first=workspace.run();$('graph-ymin').value='-20';await workspace.run();$('graph-ymin').value='-30';await workspace.run();assert.equal(requests.length,1);
    resolvers.shift()({ok:true,curves:[],parameters:[]});await first;
    const latest=workspace.run();assert.equal(requests.length,2);assert.equal(requests[1].yMin,-30);resolvers.shift()({ok:true,curves:[],parameters:[]});await latest;
  }finally{workspace.dispose();dom.window.close();}
});

test('surface elevation slider, dragging and saved state support viewing from below',async()=>{
  const dom=setup(page()),$=id=>document.getElementById(id);
  $('graph-kind').value='surface';$('graph-plot').getBoundingClientRect=()=>({left:0,top:0,width:800,height:460});
  const workspace=createGraphWorkspace({execute:async()=>({ok:true,surface:[[[-1,-1,0],[1,-1,1]],[[-1,1,1],[1,1,0]]],zMin:0,zMax:1,parameters:[]}),options,onError:assert.fail,persist:()=>{},isBusy:()=>false,saved:{surface:{elevation:-45}}});
  try{
    await workspace.run();assert.equal($('graph-elevation').min,'-90');assert.equal($('graph-elevation').max,'90');assert.equal($('graph-elevation').value,'-45');
    $('graph-elevation').value='-60';$('graph-elevation').dispatchEvent(new dom.window.Event('input'));assert.equal(workspace.snapshot().surface.elevation,-60);
    for(const [type,y] of [['pointerdown',230],['pointermove',-500],['pointerup',-500]]){const event=new dom.window.MouseEvent(type,{clientX:400,clientY:y,button:0,cancelable:true});Object.defineProperty(event,'pointerId',{value:1});$('graph-plot').dispatchEvent(event);}
    assert.equal(workspace.snapshot().surface.elevation,-90);
  }finally{workspace.dispose();dom.window.close();}
});

test('3D range reset restores x/y and automatic z, resamples and preserves the camera',async()=>{
  const dom=setup(page()),$=id=>document.getElementById(id),requests=[];
  $('graph-kind').value='surface';
  const workspace=createGraphWorkspace({execute:async request=>{requests.push(request);return {ok:true,surface:[[[-1,-1,0],[1,-1,1]],[[-1,1,1],[1,1,0]]],zMin:0,zMax:1,parameters:[]};},options,onError:assert.fail,persist:()=>{},isBusy:()=>false,saved:{surface:{rotation:123,elevation:-45,zoom:2,autoZ:false},ranges:{'graph-min':100,'graph-max':101,'graph-ymin':200,'graph-ymax':201,'graph-zmin':3,'graph-zmax':4}}});
  try{
    await workspace.run();const camera=workspace.snapshot().surface;
    assert.equal($('graph-reset-ranges').hidden,false);assert.equal($('graph-reset-ranges').parentElement.querySelector('h2').textContent,'Range');
    $('graph-reset-ranges').click();let saved=workspace.snapshot();assert.equal(saved.ranges['graph-min'],-3);assert.equal(saved.ranges['graph-max'],3);assert.equal(saved.ranges['graph-ymin'],-3);assert.equal(saved.ranges['graph-ymax'],3);assert.equal(saved.surface.autoZ,true);
    assert.equal(saved.surface.rotation,camera.rotation);assert.equal(saved.surface.elevation,camera.elevation);assert.equal(saved.surface.zoom,camera.zoom);
    assert.equal($('graph-auto-z').checked,true);assert.equal($('graph-zmin').disabled,true);assert.equal(saved.ranges['graph-zmin'],undefined);
    await workspace.run();assert.equal(requests.at(-1).min,-3);assert.equal(requests.at(-1).max,3);assert.equal(requests.at(-1).surfaceYMin,-3);assert.equal(requests.at(-1).surfaceYMax,3);
  }finally{workspace.dispose();dom.window.close();}
});

test('height cycles 1x to half to 2x, persists scale, and tracing uses the current viewport',async()=>{
  const dom=setup(page()),$=id=>document.getElementById(id),requests=[];
  $('graph-plot').getBoundingClientRect=()=>({left:0,top:0,width:800,height:Number($('graph-plot').querySelector('canvas')?.dataset.plotHeight)||460});
  const create=saved=>createGraphWorkspace({execute:async request=>{requests.push(request);return {ok:true,curves:[[[0,1],[1,0]]],parameters:[]};},options,onError:assert.fail,persist:()=>{},isBusy:()=>false,saved});
  let workspace=create();
  try{
    await workspace.run();const canvas=$('graph-plot').firstChild,fullHeight=canvas.height,range=workspace.snapshot().ranges;
    $('graph-height-toggle').click();assert.equal(canvas.height,fullHeight/2);assert.equal($('graph-height-toggle').getAttribute('aria-pressed'),'true');assert.equal($('graph-height-toggle').getAttribute('aria-label'),'Double height');assert.deepEqual(workspace.snapshot().ranges,range);assert.equal(requests.length,1);
    const xmin=Number($('graph-min').value),xmax=Number($('graph-max').value),ymin=Number($('graph-ymin').value),ymax=Number($('graph-ymax').value),x=42+(0-xmin)/(xmax-xmin)*716,y=230-42-(1-ymin)/(ymax-ymin)*146;
    for(const type of ['pointerdown','pointerup']){const event=new dom.window.MouseEvent(type,{clientX:x,clientY:y,button:0,cancelable:true});Object.defineProperty(event,'pointerId',{value:1});$('graph-plot').dispatchEvent(event);}
    assert.ok($('graph-trace').textContent.includes('1'));
    const saved=workspace.snapshot();workspace.dispose();workspace=create(saved);await workspace.run();assert.equal(canvas.height,fullHeight/2);assert.equal(workspace.snapshot().halfHeight,true);
    $('graph-kind').value='surface';$('graph-kind').onchange();await workspace.run();const surfaceCanvas=$('graph-plot').firstChild;assert.equal(surfaceCanvas.height,fullHeight/2);
    $('graph-height-toggle').click();assert.equal(surfaceCanvas.height,fullHeight*2);assert.equal(workspace.snapshot().heightScale,2);assert.equal($('graph-height-toggle').getAttribute('aria-label'),'Full height');
    const doubled=workspace.snapshot();workspace.dispose();workspace=create(doubled);await workspace.run();const restoredCanvas=$('graph-plot').firstChild;assert.equal(restoredCanvas.height,fullHeight*2);assert.equal(workspace.snapshot().heightScale,2);
    $('graph-height-toggle').click();assert.equal(restoredCanvas.height,fullHeight);assert.equal(workspace.snapshot().heightScale,1);assert.equal(workspace.snapshot().halfHeight,false);assert.equal($('graph-height-toggle').getAttribute('aria-label'),'Half height');
    workspace.dispose();workspace=create({halfHeight:true});await workspace.run();assert.equal(workspace.snapshot().heightScale,.5);assert.equal(restoredCanvas.height,fullHeight/2);
  }finally{workspace.dispose();dom.window.close();}
});
