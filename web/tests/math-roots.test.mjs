import test from 'node:test';
import assert from 'node:assert/strict';
import {JSDOM} from 'jsdom';
import {readFileSync} from 'node:fs';
import {expressionDisplay} from '../expression-display.js';
import {paintMathRoots,rootPath} from '../math-roots.js';
import {markInputCursor} from '../input-cursor.js';

const box=(left,top,width,height)=>({left,top,right:left+width,bottom:top+height,width,height});
test('radical hook and roof stay connected across sizes and fractional pixel positions',()=>{
  for(const font of [12,16,24,30,48])for(const height of [font,2*font,5*font])for(const offset of [0,.25,.5,.75]){
    const root=box(offset,offset,8*font,height+.5*font),base=box(offset+.8*font,offset+.2*font,7.2*font,height);
    const path=rootPath(root,base,font);
    assert.equal((path.match(/M/g)||[]).length,1,'one connected contour');
    assert.match(path,/ L [\d.]+ [\d.]+ H [\d.]+$/,'roof extends directly from the hook endpoint');
    const coordinates=path.match(/-?\d+(?:\.\d+)?/g).map(Number);
    assert.ok(coordinates.every(Number.isFinite));assert.equal(coordinates.at(-1),root.right);
    assert.ok(coordinates[7]>=root.top&&coordinates[7]<base.top,'roof clears the full radicand');
    assert.ok(coordinates[5]>base.top&&coordinates[5]<root.bottom,'hook covers tall radicands');
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
  assert.equal(overlay.querySelectorAll('path').length,4);assert.equal(math.querySelectorAll('svg').length,0,'no SVG participates in MathML sizing');
  const paths=[...overlay.querySelectorAll('path')];
  for(let i=0;i<paths.length;i+=2){
    const contour=paths[i],downstroke=paths[i+1];
    const coordinates=path=>path.getAttribute('d').match(/-?\d+(?:\.\d+)?/g).map(Number);
    assert.deepEqual(coordinates(downstroke),coordinates(contour).slice(2,6),'the thick downstroke shares the connected contour coordinates and excludes the left lead-in');
    assert.equal((downstroke.getAttribute('d').match(/L/g)||[]).length,1,'only the descending diagonal is reinforced');
    assert.ok(!downstroke.getAttribute('d').includes('H'),'the roof keeps its original thickness');
    assert.equal(Number(contour.getAttribute('stroke-width')),.044*24);
    assert.equal(Number(downstroke.getAttribute('stroke-width')),Number(contour.getAttribute('stroke-width'))*2);
    assert.equal(downstroke.getAttribute('stroke-linejoin'),'round');assert.equal(downstroke.getAttribute('stroke-linecap'),'round');
    assert.equal(downstroke.getAttribute('stroke'),'currentColor');
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
  assert.equal(overlay.getAttribute('width'),'200');assert.match(overlay.firstElementChild.getAttribute('d'),/H 200$/);
  frame.remove();await Promise.resolve();while(frames.length)frames.shift()();
  assert.equal(observed.size,0,'detached formulas cannot accumulate resize observers');
});
