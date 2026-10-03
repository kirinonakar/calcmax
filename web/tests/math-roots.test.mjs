import test from 'node:test';
import assert from 'node:assert/strict';
import {JSDOM} from 'jsdom';
import {readFileSync} from 'node:fs';
import {expressionDisplay} from '../expression-display.js';
import {paintMathRoots,rootPath} from '../math-roots.js';
import {markInputCursor} from '../input-cursor.js';

const box=(left,top,width,height)=>({left,top,right:left+width,bottom:top+height,width,height});
const vertices=path=>path.match(/-?\d+(?:\.\d+)?(?:e[+-]?\d+)?/gi).map(Number).reduce((points,value,i)=>{
  if(i%2)points.at(-1).push(value);else points.push([value]);return points;
},[]);
test('radical outline joins unequal weights without caps across sizes and fractional pixel positions',()=>{
  for(const font of [12,16,24,30,48])for(const height of [font,2*font,5*font])for(const offset of [0,.25,.5,.75]){
    const root=box(offset,offset,8*font,height+.5*font),base=box(offset+.8*font,offset+.2*font,7.2*font,height);
    const path=rootPath(root,base,font);
    assert.equal((path.match(/M/g)||[]).length,1,'one connected contour');
    assert.match(path,/ Z$/,'the filled contour closes without overlapping line caps');
    const points=vertices(path);
    assert.equal(points.length,10,'each join has one vertex on each edge');
    assert.ok(points.flat().every(Number.isFinite));
    assert.equal(points[4][0],root.right);assert.equal(points[5][0],root.right);
    assert.ok(points[5][1]>=root.top&&points[4][1]<base.top,'roof clears the full radicand');
    assert.ok(points[2][1]>base.top&&points[2][1]<root.bottom,'hook covers tall radicands');
    for(let i=0;i<4;i++){
      const [x,y]=points[i],dx=points[i+1][0]-x,dy=points[i+1][1]-y;
      const opposite=points[9-i];
      const thickness=Math.abs(dx*(opposite[1]-y)-dy*(opposite[0]-x))/Math.hypot(dx,dy);
      assert.ok(Math.abs(thickness-.044*font*(i===1?2:1))<1e-8,'only the downstroke is twice the roof thickness');
    }
  }
});

test('root overlays retain MathML semantics, have bounded dimensions, and follow layout changes',()=>{
  const dom=new JSDOM();globalThis.document=dom.window.document;
  const style=document.createElement('style');style.textContent=readFileSync(new URL('../calculator.css',import.meta.url),'utf8');document.head.append(style);
  const source='nthroot(sqrt(1/2),3)',math=expressionDisplay(source),frame=document.createElement('span');
  frame.className='input-math';frame.append(math);document.body.append(frame);
  const roots=[...math.querySelectorAll('msqrt,mroot')],computed=dom.window.getComputedStyle;
  dom.window.getComputedStyle=node=>roots.includes(node)?{fontSize:'24px'}:node===math?{color:'rgb(22, 42, 38)'}:computed(node);
  let offset=.25,width=160;
  frame.getBoundingClientRect=()=>box(100,40,width,90);
  math.getBoundingClientRect=()=>box(100,40,width,90);
  roots.forEach((root,i)=>{
    root.getBoundingClientRect=()=>box(100+offset+i*20,40+i*10,width-i*20,90-i*10);
    root.firstElementChild.getBoundingClientRect=()=>box(100+offset+(i+1)*20,60+i*10,width-(i+1)*20,55-i*10);
  });
  const overlay=paintMathRoots(math);
  assert.equal(overlay.parentElement,frame);assert.equal(overlay.getAttribute('width'),'160');assert.equal(overlay.getAttribute('height'),'90');
  assert.equal(overlay.getAttribute('aria-hidden'),'true');assert.equal(overlay.getAttribute('focusable'),'false');
  assert.equal(overlay.querySelectorAll('path').length,2);assert.equal(math.querySelectorAll('svg').length,0,'no SVG participates in MathML sizing');
  const paths=[...overlay.querySelectorAll('path')];
  for(const path of paths){
    assert.equal(path.getAttribute('fill'),'currentColor');assert.equal(path.getAttribute('stroke'),'none');
    assert.match(path.getAttribute('d'),/ Z$/,'the radical uses a single filled outline');
    assert.equal(path.getAttribute('stroke-linecap'),null,'no rounded caps protrude at the joins');
  }
  assert.equal(math.querySelectorAll('mroot').length,1);assert.equal(math.querySelectorAll('msqrt').length,1);assert.equal(math.querySelectorAll('mfrac').length,1);
  assert.equal(math.textContent,'123','accessible math text is unchanged');
  for(const root of roots){assert.ok(root.classList.contains('math-root-painted'));assert.ok(root.firstElementChild.classList.contains('math-root-content'));}
  assert.equal(frame.style.getPropertyValue('--math-ink'),'rgb(22, 42, 38)','the radicand and overlay share the original math color');
  assert.equal(computed(overlay).pointerEvents,'none');
  const previous=overlay.firstElementChild.getAttribute('d');offset=.75;width=240;
  assert.equal(paintMathRoots(math),overlay);assert.notEqual(overlay.firstElementChild.getAttribute('d'),previous);
  assert.equal(overlay.getAttribute('width'),'240');assert.equal(frame.querySelectorAll('.math-root-overlay').length,1);
  markInputCursor(math,source,source.length);
  assert.equal(math.parentElement,frame,'cursor and roots share the HTML coordinate system');assert.equal(frame.querySelectorAll('.input-caret').length,1);
  dom.window.close();
});

test('unmeasurable roots retain the native fallback',()=>{
  const dom=new JSDOM();globalThis.document=dom.window.document;
  const math=expressionDisplay('sqrt()');document.body.append(math);
  assert.equal(paintMathRoots(math),null);assert.equal(math.querySelector('.math-root-painted'),null);
  dom.window.close();
});

test('resize notifications repaint roots and removed formulas release their observers',async t=>{
  const dom=new JSDOM();globalThis.document=dom.window.document;
  const observed=new Set(),frames=[];let onResize,size=100;
  const originalFrame=globalThis.requestAnimationFrame;
  globalThis.requestAnimationFrame=callback=>frames.push(callback);
  dom.window.ResizeObserver=class{constructor(callback){onResize=callback;}observe(node){observed.add(node);}unobserve(node){observed.delete(node);}};
  t.after(()=>{globalThis.requestAnimationFrame=originalFrame;dom.window.close();});
  const math=expressionDisplay('sqrt(8)'),root=math.firstElementChild,frame=document.createElement('span');
  frame.className='input-math';frame.append(math);document.body.append(frame);
  frame.getBoundingClientRect=math.getBoundingClientRect=()=>box(0,0,size,40);
  root.getBoundingClientRect=()=>box(0,0,size,40);
  root.firstElementChild.getBoundingClientRect=()=>box(20,10,size-20,25);
  dom.window.getComputedStyle=node=>node===root?{fontSize:'24px'}:{color:'black'};
  await Promise.resolve();
  const overlay=frame.querySelector('.math-root-overlay');assert.ok(overlay);assert.equal(observed.size,2);
  size=200;onResize();while(frames.length)frames.shift()();
  assert.equal(overlay.getAttribute('width'),'200');assert.equal(vertices(overlay.firstElementChild.getAttribute('d'))[4][0],200);
  frame.remove();await Promise.resolve();while(frames.length)frames.shift()();
  assert.equal(observed.size,0,'detached formulas cannot accumulate resize observers');
});
