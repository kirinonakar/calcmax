import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {JSDOM} from 'jsdom';
import {plotGraph} from '../graph-canvas.js';
import {createGraphWorkspace} from '../graph-workspace.js';
import {surfaceProjection,surfaceFaces} from '../surface-geometry.js';
import {bindGraphGestures} from '../graph-view.js';
import {installCanvas,surfaceFills,curveStrokes} from './canvas-context.mjs';

const bounds={xmin:-2,xmax:2,ymin:-2,ymax:2};
function setup(html='<div id="plot"></div>'){
  const dom=new JSDOM(html);globalThis.document=dom.window.document;installCanvas(dom);return dom;
}
const page=()=>readFileSync(new URL('../index.html',import.meta.url),'utf8');
const options=()=>({displayDigits:10});

test('curve buttons form a horizontal row below the plot and selection still updates the graph',async()=>{
  const dom=setup(page()),$=id=>document.getElementById(id),style=document.createElement('style');
  style.textContent=readFileSync(new URL('../calculator.css',import.meta.url),'utf8');document.head.append(style);
  const workspace=createGraphWorkspace({execute:async()=>({ok:true,curves:[[[0,0],[1,1]],[[0,0],[1,-1]]],parameters:[]}),options,onError:assert.fail,persist:()=>{},isBusy:()=>false});
  try{
    await workspace.run();const picker=$('graph-formulas'),css=dom.window.getComputedStyle(picker);
    assert.ok($('graph-plot').compareDocumentPosition(picker)&dom.window.Node.DOCUMENT_POSITION_FOLLOWING);
    assert.equal(css.display,'flex');assert.equal(css.flexWrap,'nowrap');assert.equal(css.overflowX,'auto');
    assert.equal($('graph-selected').closest('label').hidden,true);
    assert.equal(picker.children[0].getAttribute('aria-pressed'),'true');
    picker.children[1].click();assert.equal($('graph-selected').value,'1');
    assert.equal(picker.children[1].getAttribute('aria-pressed'),'true');assert.equal(picker.children[0].getAttribute('aria-pressed'),'false');
    assert.equal(picker.children[1].style.getPropertyValue('--curve-color'),curveStrokes($('graph-plot')).find(c=>c.lineWidth===4).strokeStyle);
    picker.children[0].dispatchEvent(new dom.window.KeyboardEvent('keydown',{key:'Enter',cancelable:true}));
    assert.equal($('graph-selected').value,'0');assert.equal(picker.children[0].getAttribute('aria-pressed'),'true');
  }finally{workspace.dispose();dom.window.close();}
});

test('Cartesian clicks trace 2x-5 at the clicked x and keep the selected curve color',async()=>{
  const dom=setup(page()),$=id=>document.getElementById(id),container=$('graph-plot');
  container.getBoundingClientRect=()=>({left:0,top:0,width:800,height:460});
  container.style.setProperty('--accent','#ff0000');
  const curves=[Array.from({length:21},(_,i)=>[i-10,2*(i-10)-5]),Array.from({length:21},(_,i)=>[i-10,10-i])];
  const workspace=createGraphWorkspace({execute:async()=>({ok:true,curves,parameters:[]}),options,onError:assert.fail,persist:()=>{},isBusy:()=>false});
  try{
    $('graph-source').value='2x-5\n-x';await workspace.run();
    for(const selected of [0,1]){
      $('graph-selected').value=String(selected);$('graph-selected').onchange();
      const canvas=container.querySelector('canvas'),left=Number(canvas.dataset.plotLeft),width=Number(canvas.dataset.plotWidth);
      const clientX=left+width*(2.25+10)/20,clientY=42+(460-84)*.1;
      for(const type of ['pointerdown','pointerup']){
        const event=new dom.window.MouseEvent(type,{clientX,clientY,button:0,cancelable:true});
        Object.defineProperty(event,'pointerId',{value:1});container.dispatchEvent(event);
      }
      const ctx=canvas.getContext('2d'),dot=ctx.commands.find(c=>c.op==='fill'&&c.path.some(p=>p.op==='arc'&&p.args[2]===6));
      const arc=dot?.path.find(p=>p.op==='arc'),expectedY=selected===0?-.5:-2.25;
      assert.ok(dot,'a click displays a trace dot');
      assert.ok(Math.abs(arc.args[0]-clientX)<1e-10,'tracing must not jump sideways on sloping curves');
      assert.ok(Math.abs(arc.args[1]-(42+(5-expectedY)/10*(460-84)))<1e-10,'the dot lies on the curve at the clicked x');
      const curve=ctx.commands.find(c=>c.op==='stroke'&&c.lineWidth===4&&!c.lineDash.length);
      assert.equal(dot.fillStyle,curve.strokeStyle,'trace dots match the selected curve rather than the theme accent');
      assert.match($('graph-trace').textContent,/2\.25/);
    }
  }finally{workspace.dispose();dom.window.close();}
});

test('axis labels scale within readable CSS pixel limits and long decimal labels fit at every height',()=>{
  const dom=setup(),container=document.getElementById('plot');
  const longBounds={xmin:-123456789.123456,xmax:123456789.123456,ymin:-123456789.123456,ymax:123456789.123456};
  try{
    for(const width of [320,400,500,800,1600])for(const heightScale of [.5,1,2]){
      container.getBoundingClientRect=()=>({width});
      const canvas=plotGraph(container,{curves:[]},longBounds,{heightScale}),ctx=canvas.getContext('2d');
      const labels=ctx.commands.filter(c=>c.op==='fillText');
      assert.equal(parseFloat(ctx.font)*width/800,width<=400?10:width===500?11:12);
      assert.ok(Number(canvas.dataset.plotLeft)>42);
      for(const label of labels){
        const textWidth=ctx.measureText(label.args[0]).width,x=label.args[1];
        const left=x-(label.textAlign==='right'?textWidth:textWidth/2),right=label.textAlign==='right'?x:x+textWidth/2;
        assert.ok(left>=8-1e-8,`${label.args[0]} starts inside the canvas`);
        assert.ok(right<=792+1e-8,`${label.args[0]} ends inside the canvas`);
      }
      const clip=ctx.commands.find(c=>c.op==='clip').path.find(p=>p.op==='rect').args;
      assert.equal(clip[0],Number(canvas.dataset.plotLeft));assert.equal(clip[2],Number(canvas.dataset.plotWidth));
    }
    plotGraph(container,{curves:[]},bounds);
    assert.equal(Number(container.firstChild.dataset.plotLeft),42,'short labels restore the usual margin');
  }finally{dom.window.close();}
});

test('tracing and wheel zoom use the widened axis margin in the rendered plot',()=>{
  const dom=setup(),container=document.getElementById('plot');
  container.getBoundingClientRect=()=>({left:0,top:0,width:800,height:460});
  let current={...bounds,ymin:-123456789.123456,ymax:123456789.123456},traced;
  const canvas=plotGraph(container,{curves:[]},current),left=Number(canvas.dataset.plotLeft),width=Number(canvas.dataset.plotWidth);
  const dispose=bindGraphGestures(container,{getBounds:()=>current,onView:next=>{current=next;},onTrace:at=>{traced=at;}});
  try{
    const clientX=left+width*.25,clientY=230;
    for(const type of ['pointerdown','pointerup']){
      const event=new dom.window.MouseEvent(type,{clientX,clientY,button:0,cancelable:true});Object.defineProperty(event,'pointerId',{value:1});container.dispatchEvent(event);
    }
    assert.equal(traced.x,.25);assert.equal(traced.y,.5);
    const anchor=current.xmin+(current.xmax-current.xmin)*.25;
    container.dispatchEvent(new dom.window.WheelEvent('wheel',{clientX,clientY,deltaY:-100,cancelable:true}));
    assert.ok(Math.abs(current.xmin+(current.xmax-current.xmin)*.25-anchor)<1e-10);
  }finally{dispose();dom.window.close();}
});

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

test('Parameters collapse preserves slider values, keeps actions accessible, and restores the saved choice',async()=>{
  const dom=setup(page()),$=id=>document.getElementById(id);let names=['a'],saves=0;
  document.querySelector('[data-mode="graph"]').hidden=false;
  const create=saved=>createGraphWorkspace({execute:async()=>({ok:true,curves:[],parameters:names}),options,onError:assert.fail,persist:()=>saves++,isBusy:()=>false,saved});
  let workspace=create({parameters:{a:3}});
  try{
    assert.equal($('graph-parameter-actions').hidden,true);
    await workspace.run();
    const panel=$('graph-parameters'),toggle=$('graph-parameters-toggle'),slider=panel.querySelector('input[type="range"]');
    assert.equal(panel.hidden,false);assert.equal(toggle.getAttribute('aria-controls'),panel.id);assert.equal(toggle.getAttribute('aria-expanded'),'true');
    const initialSaves=saves;
    toggle.click();assert.equal(panel.hidden,true);assert.equal(toggle.getAttribute('aria-expanded'),'false');assert.equal(saves,initialSaves+1);
    assert.equal($('graph-parameter-actions').hidden,false);assert.equal($('graph-animate').closest('[hidden]'),null);assert.equal($('graph-reset-parameters').closest('[hidden]'),null);
    workspace.render();await workspace.run();assert.equal(panel.hidden,true);assert.equal(panel.querySelector('input[type="range"]'),slider);assert.equal(slider.value,'3');assert.equal(workspace.snapshot().parameters.a,3);
    const saved=workspace.snapshot();workspace.dispose();workspace=create(saved);await workspace.run();assert.equal(panel.hidden,true);assert.equal(toggle.getAttribute('aria-expanded'),'false');assert.equal(workspace.snapshot().parameters.a,3);
    names=[];await workspace.run();assert.equal($('graph-parameter-actions').hidden,true);assert.equal(panel.hidden,true);
    names=['a'];await workspace.run();assert.equal($('graph-parameter-actions').hidden,false);assert.equal(panel.hidden,true);
    toggle.click();assert.equal(panel.hidden,false);assert.equal(toggle.getAttribute('aria-expanded'),'true');assert.equal(panel.querySelector('input[type="range"]').value,'3');
  }finally{workspace.dispose();dom.window.close();}
});

test('Animate follows frame time, holds disabled values, and restores choices',async()=>{
  const dom=setup(page()),$=id=>document.getElementById(id),callbacks=new Map();let id=0,now=0;
  dom.window.requestAnimationFrame=callback=>{callbacks.set(++id,callback);return id;};dom.window.cancelAnimationFrame=id=>callbacks.delete(id);
  const tick=(milliseconds=34)=>{now+=milliseconds;const batch=[...callbacks.values()];callbacks.clear();for(const callback of batch)callback(now);};
  const create=snapshot=>createGraphWorkspace({execute:async()=>({ok:true,curves:[],parameters:['a','b']}),options,onError:assert.fail,persist:()=>{},isBusy:()=>false,saved:snapshot});
  let workspace=create({parameters:{a:2,b:3}});
  try{
    await workspace.run();const toggle=name=>document.querySelector(`[data-animate-parameter="${name}"]`);
    assert.equal(toggle('a').checked,true);toggle('a').checked=false;toggle('a').onchange();$('graph-animate').click();tick();tick();
    assert.equal(workspace.snapshot().parameters.a,2);assert.notEqual(workspace.snapshot().parameters.b,3);
    assert.ok(Math.abs(workspace.snapshot().parameters.b-3)<.2,'start from the current slider instead of jumping to its midpoint');
    toggle('b').checked=false;toggle('b').onchange();const held=workspace.snapshot().parameters;tick();assert.deepEqual(workspace.snapshot().parameters,held);
    toggle('a').checked=true;toggle('a').onchange();tick();assert.notEqual(workspace.snapshot().parameters.a,2);assert.equal(workspace.snapshot().parameters.b,held.b);
    const saved=workspace.snapshot();workspace.dispose();workspace=create(saved);await workspace.run();assert.equal(toggle('a').checked,true);assert.equal(toggle('b').checked,false);
  }finally{workspace.dispose();dom.window.close();}
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

test('Animate runs at 60 updates per second without debounce, reuses tables, and refines on Stop',async()=>{
  const dom=setup(page()),$=id=>document.getElementById(id),callbacks=new Map(),requests=[];let id=0,saves=0;
  dom.window.requestAnimationFrame=callback=>{callbacks.set(++id,callback);return id;};dom.window.cancelAnimationFrame=id=>callbacks.delete(id);
  const step=async time=>{const batch=[...callbacks.values()];callbacks.clear();for(const callback of batch)callback(time);for(let i=0;i<4;i++)await Promise.resolve();};
  const workspace=createGraphWorkspace({execute:async request=>{requests.push(request);return {ok:true,curves:[[[0,request.parameters.a],[1,1]]],parameters:['a']};},options,onError:assert.fail,persist:()=>saves++,isBusy:()=>false,saved:{parameters:{a:1}}});
  try{
    await workspace.run();const table=$('graph-table').firstChild,selection=$('graph-selected').firstChild,startSaves=saves;
    $('graph-animate').click();
    for(let frame=0;frame<60;frame++)await step(frame*1000/60);
    const animated=requests.filter(r=>r.samples===200);assert.ok(animated.length>=59&&animated.length<=61,`expected 60 updates, got ${animated.length}`);
    assert.equal($('graph-table').firstChild,table);assert.equal($('graph-selected').firstChild,selection);assert.equal(saves,startSaves,'animation does not write local storage for every frame');
    $('graph-animate').click();for(let i=0;i<4;i++)await Promise.resolve();assert.equal(requests.at(-1).samples,500);assert.notEqual($('graph-table').firstChild,table);
    const stopped=requests.length;await step(1200);assert.equal(requests.length,stopped);
  }finally{workspace.dispose();dom.window.close();}
});

test('slow Animate computations are serialized and the next frame uses current parameters',async()=>{
  const dom=setup(page()),$=id=>document.getElementById(id),callbacks=new Map(),requests=[],resolvers=[];let id=0;
  dom.window.requestAnimationFrame=callback=>{callbacks.set(++id,callback);return id;};dom.window.cancelAnimationFrame=id=>callbacks.delete(id);
  const step=async time=>{const batch=[...callbacks.values()];callbacks.clear();for(const callback of batch)callback(time);for(let i=0;i<4;i++)await Promise.resolve();};
  const workspace=createGraphWorkspace({execute:request=>{requests.push(request);return request.samples===500?Promise.resolve({ok:true,curves:[],parameters:['a']}):new Promise(resolve=>resolvers.push(resolve));},options,onError:assert.fail,persist:()=>{},isBusy:()=>false,saved:{parameters:{a:1}}});
  try{
    await workspace.run();$('graph-animate').click();await step(0);await step(34);await step(68);await step(102);assert.equal(requests.length,2);
    resolvers.shift()({ok:true,curves:[],parameters:['a']});for(let i=0;i<4;i++)await Promise.resolve();await step(136);assert.equal(requests.length,3);assert.equal(requests.at(-1).parameters.a,workspace.snapshot().parameters.a);
    assert.notEqual(requests.at(-1).parameters.a,requests[1].parameters.a);
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
    assert.equal($('graph-reset-ranges').hidden,false);assert.equal($('graph-reset-ranges').closest('details').querySelector(':scope > summary').textContent,'Range');
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
    const xmin=Number($('graph-min').value),xmax=Number($('graph-max').value),ymin=Number($('graph-ymin').value),ymax=Number($('graph-ymax').value),x=Number(canvas.dataset.plotLeft)+(0-xmin)/(xmax-xmin)*Number(canvas.dataset.plotWidth),y=230-42-(1-ymin)/(ymax-ymin)*146;
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
