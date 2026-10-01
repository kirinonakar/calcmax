import {installCanvas,surfaceFills} from './canvas-context.mjs';
import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {JSDOM} from 'jsdom';
import {clipSurfaceSegment,clipSurfacePolygon,surfaceProjection,surfaceFaces,surfaceZRange,surfaceSampleCount} from '../surface-geometry.js';
import {plot} from '../plot.js';
import {createGraphWorkspace} from '../graph-workspace.js';
const bounds={xmin:-1,xmax:1,ymin:-1,ymax:1,zmin:-1,zmax:1};
const mesh=[[[-1,-1,-2],[1,-1,0]],[[-1,1,0],[1,1,2]]];
const inside=p=>p.every((v,i)=>v>=[-1,-1,-1][i]-1e-12&&v<=[1,1,1][i]+1e-12);

test('automatic mesh density grows with range and zoom, has a budget cap, and manual control is exact',()=>{
  assert.equal(surfaceSampleCount(bounds),26);
  const wide={xmin:-5,xmax:5,ymin:-3,ymax:3};
  assert.equal(surfaceSampleCount(wide),80);
  assert.ok(surfaceSampleCount(wide,26,true,.4)<80);
  assert.equal(surfaceSampleCount(wide,26,true,3),96);
  assert.equal(surfaceSampleCount({...wide,xmin:-1000,xmax:1000}),96);
  assert.equal(surfaceSampleCount(wide,40,false,3),40);
  assert.equal(surfaceSampleCount(wide,1000,false),96);
});

test('surface clipping trims every axis at the boundary and preserves invalid sample breaks',()=>{
  assert.deepEqual(clipSurfaceSegment([-2,0,-2],[2,0,2],bounds),[[-1,0,-1],[1,0,1]]);
  assert.equal(clipSurfaceSegment([0,0,2],[1,1,2],bounds),null);
  assert.equal(clipSurfaceSegment(null,[0,0,0],bounds),null);
  assert.equal(clipSurfaceSegment([0,NaN,0],[0,0,0],bounds),null);
  const polygon=clipSurfacePolygon([[-2,-2,-2],[2,-2,0],[0,2,2]],bounds);
  assert.ok(polygon.length>=3);assert.ok(polygon.every(inside));
  assert.deepEqual(clipSurfacePolygon([[0,0,2],[1,0,3],[0,1,3]],bounds),[]);
  assert.deepEqual(surfaceFaces([[null,[1,-1,0]],[[-1,1,0],[1,1,0]]],bounds,surfaceProjection(bounds,35,32)),[]);
});

test('surface faces are clipped, shaded, and sorted from the back after rotation',()=>{
  for(const rotation of [0,35,90,180,270,359]){
    const projection=surfaceProjection(bounds,rotation,32),faces=surfaceFaces(mesh,bounds,projection);
    assert.equal(faces.length,2);
    assert.ok(faces.every(face=>face.points.every(inside)&&face.height>=0&&face.height<=1&&face.light>=.35&&face.light<=1));
    assert.ok(faces.every((face,i)=>!i||face.depth>=faces[i-1].depth));
  }
  const original=surfaceProjection(bounds,0,32).project([1,0,0]),rotated=surfaceProjection(bounds,90,32).project([1,0,0]);
  assert.ok(Math.abs(original[0]-1)<1e-12);assert.ok(Math.abs(rotated[0])<1e-12);assert.notEqual(original[2],rotated[2]);
  assert.deepEqual(surfaceZRange(42,42),[37.8,46.2]);assert.deepEqual(surfaceZRange(0,0),[-1,1]);
});

test('SVG axes and labels rotate with the same surface projection and all render modes work',()=>{
  const dom=new JSDOM('<div id="plot"></div>');globalThis.document=dom.window.document;installCanvas(dom);
  try{
    const container=document.getElementById('plot'),result={surface:mesh,zMin:-1,zMax:1};
    const render=(rotation,renderMode)=>plot(container,result,bounds,{surfaceView:{rotation,elevation:32,zoom:1,renderMode}});
    render(0,'wireframe');const initial=container.querySelector('[data-surface-axis="x"]').outerHTML;
    const start=container.querySelector('[data-surface-axis="x"]'),projection=surfaceProjection(bounds,0,32),[px,py]=projection.project([-1,-1,-1]);
    assert.equal(Number(start.getAttribute('x1')),400+px*376*.3);assert.equal(Number(start.getAttribute('y1')),230+py*376*.3);
    assert.equal(container.querySelectorAll('[data-surface-axis]').length,3);assert.equal(container.querySelectorAll('[data-surface-axis-label]').length,3);
    assert.ok(container.querySelectorAll('path').length);assert.equal(container.querySelectorAll('[data-surface-face]').length,0);
    render(90,'surface');assert.notEqual(container.querySelector('[data-surface-axis="x"]').outerHTML,initial);
    assert.equal(container.querySelectorAll('path').length,0);assert.equal(container.querySelectorAll('[data-surface-face]').length,2);
    const face=container.querySelector('[data-surface-face]');assert.equal(face.getAttribute('fill'),face.getAttribute('stroke'));
    render(180,'surface-wireframe');assert.notEqual(container.querySelector('[data-surface-face]').getAttribute('fill'),container.querySelector('[data-surface-face]').getAttribute('stroke'));
    assert.ok(!container.innerHTML.includes('NaN'));assert.ok(!container.innerHTML.includes('Infinity'));
    plot(container,{surface:[],zMin:0,zMax:0},bounds);assert.equal(container.querySelectorAll('[data-surface-axis]').length,3);
  }finally{dom.window.close();}
});

test('surface workspace exposes automatic/manual z bounds, exact paired sliders, validation, fit and saved options',async()=>{
  const dom=new JSDOM(readFileSync(new URL('../index.html',import.meta.url),'utf8'));globalThis.document=dom.window.document;installCanvas(dom);
  const $=id=>document.getElementById(id),requests=[],errors=[];let saves=0;
  const workspace=createGraphWorkspace({execute:async request=>{requests.push(request);return {ok:true,surface:mesh,zMin:-2,zMax:2,parameters:[]};},options:()=>({displayDigits:3}),onError:message=>errors.push(message),persist:()=>saves++,isBusy:()=>false});
  const input=(id,number,event='change')=>{$(id).value=String(number);$(id).dispatchEvent(new dom.window.Event(event));};
  try{
    input('graph-kind','surface');await workspace.run();assert.equal(requests.length,1);
    assert.equal($('graph-fit').textContent,'Fit Z');assert.equal($('graph-surface-controls').hidden,false);
    assert.equal($('graph-zmin').disabled,true);assert.equal($('graph-zmin-slider').disabled,true);
    assert.equal($('graph-zmin').value,'-2');assert.equal($('graph-zmax').value,'2');assert.equal(workspace.snapshot().ranges['graph-zmin'],undefined);
    input('graph-surface-render','surface');assert.equal($('graph-plot').querySelector('canvas').dataset.renderMode,'surface');assert.ok(surfaceFills($('graph-plot')).length);assert.equal(requests.length,1);
    $('graph-auto-z').checked=false;$('graph-auto-z').dispatchEvent(new dom.window.Event('change'));
    assert.equal($('graph-zmin').disabled,false);assert.equal($('graph-zmin-slider').parentElement,$('graph-zmax-slider').parentElement);
    input('graph-zmin',-.123456789);input('graph-zmax',.234567891);
    assert.equal(workspace.snapshot().ranges['graph-zmin'],-.123456789);assert.equal(workspace.snapshot().ranges['graph-zmax'],.234567891);
    assert.equal(requests.length,1,'z range changes only reproject the existing mesh');
    input('graph-zmin-slider',2,'input');assert.ok(workspace.snapshot().ranges['graph-zmin']<workspace.snapshot().ranges['graph-zmax']);
    const valid=$('graph-plot').innerHTML;input('graph-zmin',3);assert.equal(errors.length,1);assert.equal($('graph-plot').innerHTML,valid,'invalid range does not replace the valid graph');
    input('graph-rotation',91,'input');assert.equal(workspace.snapshot().surface.rotation,91,'rotation remains usable while a range is being corrected');
    $('graph-fit').click();assert.equal($('graph-auto-z').checked,true);assert.equal($('graph-zmin').value,'-2');assert.equal($('graph-zmax').value,'2');
    input('graph-min',1000);input('graph-max',1001);$('graph-reset').click();assert.equal($('graph-min').value,'-3');assert.equal($('graph-min-slider').min,'-15');assert.equal($('graph-max-slider').max,'15');
    const snapshot=workspace.snapshot();assert.equal(snapshot.surface.autoZ,true);assert.equal(snapshot.surface.renderMode,'surface');assert.ok(saves>0);
    workspace.dispose();
    const restored=createGraphWorkspace({execute:async()=>({ok:true,surface:mesh,zMin:-2,zMax:2,parameters:[]}),options:()=>({displayDigits:3}),onError:assert.fail,persist:()=>{},isBusy:()=>false,saved:{...snapshot,surface:{...snapshot.surface,autoZ:false},ranges:{...snapshot.ranges,'graph-zmin':-.5,'graph-zmax':.5}}});
    try{await restored.run();assert.equal($('graph-surface-render').value,'surface');assert.equal($('graph-auto-z').checked,false);assert.equal(restored.snapshot().ranges['graph-zmin'],-.5);}finally{restored.dispose();}
  }finally{workspace.dispose();dom.window.close();}
});

test('constant surfaces keep a centered, increasing automatic z range',async()=>{
  const dom=new JSDOM(readFileSync(new URL('../index.html',import.meta.url),'utf8'));globalThis.document=dom.window.document;installCanvas(dom);
  document.getElementById('graph-kind').value='surface';
  const workspace=createGraphWorkspace({execute:async()=>({ok:true,surface:[[[-1,-1,42],[1,-1,42]],[[-1,1,42],[1,1,42]]],zMin:42,zMax:42,parameters:[]}),options:()=>({displayDigits:10}),onError:assert.fail,persist:()=>{},isBusy:()=>false});
  try{await workspace.run();assert.equal(Number(document.getElementById('graph-zmin').value),37.8);assert.equal(Number(document.getElementById('graph-zmax').value),46.2);assert.ok(!document.getElementById('graph-plot').innerHTML.includes('NaN'));}finally{workspace.dispose();dom.window.close();}
});

test('surface color changes every render mode immediately and density resamples and restores',async()=>{
  let dom=new JSDOM(readFileSync(new URL('../index.html',import.meta.url),'utf8'));globalThis.document=dom.window.document;installCanvas(dom);
  let workspace;const requests=[];
  const setup=saved=>createGraphWorkspace({execute:async request=>{
    requests.push(request);const count=request.surfaceSamples;
    return {ok:true,parameters:[],zMin:0,zMax:0,surface:Array.from({length:count+1},(_,row)=>Array.from({length:count+1},(_,col)=>[-1+2*col/count,-1+2*row/count,0]))};
  },options:()=>({displayDigits:10}),onError:assert.fail,persist:()=>{},isBusy:()=>false,saved});
  const $=id=>document.getElementById(id),input=(id,value,event)=>{$(id).value=value;$(id).dispatchEvent(new dom.window.Event(event));};
  try{
    $('graph-kind').value='surface';workspace=setup({surface:{samples:12,autoDensity:false}});await workspace.run();assert.equal(requests[0].surfaceSamples,12);
    input('graph-surface-color','#ff0000','input');assert.ok($('graph-plot').querySelector('canvas').getContext('2d').commands.some(c=>c.op==='stroke'&&c.strokeStyle==='#ff0000'));assert.equal(requests.length,1);
    input('graph-surface-render','surface','change');assert.equal(surfaceFills($('graph-plot')).length,2*12*12);
    assert.match(surfaceFills($('graph-plot'))[0].fillStyle,/^rgb\(\d+,0,0\)$/);
    input('graph-surface-render','surface-wireframe','change');assert.match(surfaceFills($('graph-plot'))[0].fillStyle,/^rgb\(\d+,0,0\)$/);
    input('graph-surface-samples','40','input');input('graph-surface-samples','40','change');await workspace.run();
    assert.equal(requests.at(-1).surfaceSamples,40);assert.equal($('graph-surface-samples-value').textContent,'40 × 40');assert.equal(surfaceFills($('graph-plot')).length,2*40*40);
    const saved=workspace.snapshot();workspace.dispose();dom.window.close();
    dom=new JSDOM(readFileSync(new URL('../index.html',import.meta.url),'utf8'));globalThis.document=dom.window.document;installCanvas(dom);$('graph-kind').value='surface';workspace=setup(saved);
    assert.equal($('graph-surface-color').value,'#ff0000');assert.equal($('graph-surface-samples').value,'40');assert.equal($('graph-surface-render').value,'surface-wireframe');
    await workspace.run();assert.equal(requests.at(-1).surfaceSamples,40);
    assert.ok($('graph-surface-render').closest('.graph-surface-appearance'));
    assert.ok($('graph-rotation').closest('.graph-surface-camera'));
    assert.ok($('graph-zmin').closest('.graph-surface-z-range'));
    for(const id of ['graph-rotation','graph-elevation','graph-surface-zoom'])assert.equal($(id).parentElement,$(id+'-value').parentElement);
  }finally{workspace?.dispose();dom.window.close();}
});
