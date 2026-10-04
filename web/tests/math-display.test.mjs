import test from 'node:test';
import assert from 'node:assert/strict';
import {JSDOM} from 'jsdom';
import {readFileSync} from 'node:fs';
import {expressionDisplay,expressionInputDisplay} from '../expression-display.js';
import {paintMathRoots} from '../math-roots.js';
import {markInputCursor} from '../input-cursor.js';
import {mathDisplay} from '../math-display.js';
import {resultMathDisplay,resultMathParts} from '../result-display.js';

const box=(left,top,width,height)=>({left,top,right:left+width,bottom:top+height,width,height});
const vertices=path=>path.match(/-?\d+(?:\.\d+)?(?:e[+-]?\d+)?/gi).map(Number).reduce((points,value,i)=>{
  if(i%2)points.at(-1).push(value);else points.push([value]);return points;
},[]);

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

function page(t){
  const dom=new JSDOM('');
  const previous=globalThis.document;
  globalThis.document=dom.window.document;
  t.after(()=>{globalThis.document=previous;dom.window.close();});
}

test('trigonometric powers sit on the function name in previews, input, and results',t=>{
  page(t);
  for(const source of ['sin(x)^2','(sin(x))^2','sin(x)²','cos(x)^3','tan(θ)^2']){
    for(const math of [expressionDisplay(source),expressionInputDisplay(source)]){
      const power=math.querySelector('msup');
      assert.equal(power.children[0].localName,'mi',source);
      assert.match(power.children[0].textContent,/^(sin|cos|tan)$/);
      assert.equal(power.parentNode.children[1].textContent,source.includes('θ')?'(θ)':'(x)');
    }
  }
  const result=mathDisplay({kind:'power',args:[{kind:'function',value:'sin',args:[{kind:'symbol',value:'theta'}]},{kind:'number',value:'2'}]});
  assert.equal(result.textContent,'sin2(θ)');
  assert.equal(result.querySelector('msup > mi').textContent,'sin');
  for(const source of ['sin(x)^(-1)','sin(x)^a','f(x)^2'])assert.equal(expressionDisplay(source).querySelector('msup').children[0].textContent,source.startsWith('f')?'f(x)':'sin(x)');
  assert.equal(expressionDisplay('sin(x^2)').querySelector('msup').children[0].textContent,'x');
});

test('root collections wrap at terms while retaining all signs and punctuation',t=>{
  const dom=new JSDOM(''),previous=globalThis.document;
  globalThis.document=dom.window.document;
  t.after(()=>{globalThis.document=previous;dom.window.close();});
  const number=value=>({kind:'number',value}),imaginary={kind:'product',args:[number('1.08395410131771066843'),{kind:'text',value:'I'}]};
  const tree={kind:'set',args:[number('-1.16730397826141868425'),{kind:'sum',args:[number('-0.1812324444698753839'),{kind:'unary',value:'-',args:[imaginary]}]}]};
  const before=JSON.stringify(tree),display=resultMathDisplay(tree,10,true);
  assert.equal(display.querySelectorAll('.result-part').length,3);
  assert.equal(display.textContent,'{-1.1673039783,-0.1812324445-1.0839541013·i}');
  assert.equal(display.firstElementChild.textContent,'{-1.1673039783,');
  assert.equal(JSON.stringify(tree),before);
  const fraction={kind:'fraction',args:[number('1'),number('2')]};
  assert.equal(resultMathDisplay(fraction).querySelectorAll('mfrac').length,1);
  const parts=resultMathParts({kind:'sum',args:[fraction,number('3')]});
  assert.equal(parts.length,2);assert.equal(parts[1][0].value,'+');assert.equal(parts[0][0],fraction);
});
