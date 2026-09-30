import test from 'node:test';
import assert from 'node:assert/strict';
import {JSDOM} from 'jsdom';
import {readFileSync} from 'node:fs';
import {mathDisplay} from '../math-display.js';
import {expressionDisplay,expressionInputDisplay} from '../expression-display.js';
import {markInputCursor,followInputCursor,inputPointPosition} from '../input-cursor.js';

test('invalid and wrapped source preserves ranges and supports caret placement inside text',()=>{
  const dom=new JSDOM();globalThis.document=dom.window.document;
  for(const wordWrap of [false,true])for(const source of ['12+*3','1..2','sin(,*)','2+)3','@','12345678901234567890']){
    const input=expressionInputDisplay(source,{wordWrap});document.body.append(input);
    const shown=source.replaceAll('*','×');assert.equal(input.textContent,shown);
    for(let at=0;at<=source.length;at++){const marker=markInputCursor(input,source,at);assert.equal(marker.dataset.sourceStart,String(at));assert.equal(input.textContent,shown);}
    input.parentElement?.classList.contains('input-math')?input.parentElement.remove():input.remove();
  }
  const input=expressionInputDisplay('123+*4'),token=input.querySelector('[data-source-start="0"]');document.body.append(input);
  token.getBoundingClientRect=()=>({left:100,right:136,top:10,height:24});
  dom.window.Range.prototype.getBoundingClientRect=function(){return {left:100+this.startOffset*12,right:100+this.endOffset*12,top:10,height:24};};
  assert.equal(inputPointPosition(token,'123+*4',124,22),2);
  assert.equal(inputPointPosition(token,'123+*4',100,22),0);
  dom.window.close();
});

test('multiplication uses the math symbol in normal, wrapped, and malformed input',()=>{
  const dom=new JSDOM();globalThis.document=dom.window.document;
  for(const [source,wordWrap] of [['2*3',false],['2*3',true],['2+*3',false],['2+*3',true]]){
    const input=expressionInputDisplay(source,{wordWrap});document.body.append(input);
    assert.ok(input.textContent.includes('×'));assert.ok(!input.textContent.includes('*'));
    const operator=input.querySelector('[data-source-start="1"]')||input.querySelector('mo');
    assert.ok(operator);
    for(let at=0;at<=source.length;at++)assert.equal(markInputCursor(input,source,at).dataset.sourceStart,String(at));
  }
  dom.window.close();
});

test('word wrap retains fractions, powers, radicals, and absolute source ranges',()=>{
  const dom=new JSDOM();globalThis.document=dom.window.document;
  for(const source of ['1/2+sqrt(2)^3','(2+3)*nthroot(81,4)','sin(2)+3/4-5^2','1+2+3+4','123456789012345678901234567890']){
    const input=expressionInputDisplay(source,{wordWrap:true});document.body.append(input);
    assert.ok(input.querySelector('math'),'math remains MathML in wrapped mode');
    for(const tag of ['mfrac','msup','msqrt','mroot'])assert.equal(input.querySelectorAll(tag).length,expressionDisplay(source).querySelectorAll(tag).length,`${source}: ${tag}`);
    for(let at=0;at<=source.length;at++)assert.equal(markInputCursor(input,source,at).dataset.sourceStart,String(at));
    for(const node of input.querySelectorAll('[data-source-start]')){assert.ok(Number(node.getAttribute('data-source-start'))>=0);assert.ok(Number(node.getAttribute('data-source-end'))<=source.length);}
    assert.ok(input.querySelectorAll('.input-part').length>1,'top-level terms provide wrapping opportunities');
  }
  dom.window.close();
});

test('wrapped fractions and calculus use exactly the same display style and mathematical structure',()=>{
  const dom=new JSDOM();globalThis.document=dom.window.document;
  for(const source of ['1/2+3/4','integrate(x^2,x,0,1)+1/2','sum(k,(k,1,10))+sqrt(x^2+1)','limit(sin(x)/x,x,0)+diff(x^3,x,2)','2+-3']){
    const regular=expressionInputDisplay(source),wrapped=expressionInputDisplay(source,{wordWrap:true});
    document.body.append(regular,wrapped);
    for(const math of [...regular.querySelectorAll('math'),...wrapped.querySelectorAll('math')]){assert.equal(math.getAttribute('display'),'block');assert.equal(math.getAttribute('displaystyle'),'true');}
    for(const tag of ['mfrac','msup','msqrt','msubsup','munderover','munder'])assert.deepEqual([...wrapped.querySelectorAll(tag)].map(n=>n.outerHTML),[...regular.querySelectorAll(tag)].map(n=>n.outerHTML),`${source}: identical ${tag} renderer`);
    assert.equal(wrapped.textContent,regular.textContent,'wrapping preserves every displayed operator');
    assert.equal(wrapped.innerHTML,regular.innerHTML,'both modes use the identical renderer, source ranges, styles, and operator spacing');
  }
  dom.window.close();
});

test('input viewport follows the caret in both directions without moving when already visible',()=>{
  const viewport={clientWidth:200,clientHeight:80,clientLeft:0,clientTop:0,scrollLeft:0,scrollTop:0,getBoundingClientRect:()=>({left:20,top:10})};
  let rect={left:250,right:252,top:20,bottom:44};const marker={getBoundingClientRect:()=>rect};
  followInputCursor(viewport,marker);assert.equal(viewport.scrollLeft,44);
  rect={left:80,right:82,top:20,bottom:44};followInputCursor(viewport,marker);assert.equal(viewport.scrollLeft,44);
  rect={left:24,right:26,top:20,bottom:44};followInputCursor(viewport,marker);assert.equal(viewport.scrollLeft,36);
  rect={left:80,right:82,top:100,bottom:124};followInputCursor(viewport,marker,12,true);assert.equal(viewport.scrollLeft,36);assert.equal(viewport.scrollTop,34);
  rect={left:80,right:82,top:5,bottom:29};followInputCursor(viewport,marker,12,true);assert.equal(viewport.scrollTop,29);
});

test('each root has one native radical and input cursors do not increase radicand height',()=>{
  const dom=new JSDOM();globalThis.document=dom.window.document;
  for(const source of ['sqrt()','sqrt(2)','nthroot(81,4)','sqrt(1/2)','sqrt(sqrt(2))','sqrt(2)^2']){
    for(let at=0;at<=source.length;at++){
      const math=expressionDisplay(source);markInputCursor(math,source,at);
      assert.equal(math.querySelectorAll('msqrt,mroot').length,source.match(/sqrt\(|nthroot\(/g).length,source);
      assert.equal(math.querySelectorAll('svg,.math-radical,.radical-stroke').length,0,'no duplicate or unbounded SVG radical');
      if(source==='sqrt()')assert.ok(math.querySelector('msqrt .input-slot'),'the empty input remains under the native roof');
      const caret=math.parentElement.querySelector('.input-caret');
      assert.equal(caret.namespaceURI,'http://www.w3.org/1999/xhtml');
      assert.equal(caret.parentElement,math.parentElement,'caret uses a separate HTML containing block');
      assert.equal(math.querySelector('.input-caret'),null,'caret never participates in math layout');
      assert.equal(math.firstElementChild.outerHTML,expressionDisplay(source).firstElementChild.outerHTML,'input uses the same untouched native math at every cursor position');
    }
  }
  dom.window.close();
});

test('cursor insertion preserves root and superscript operand counts at every source position',()=>{
  const dom=new JSDOM();globalThis.document=dom.window.document;
  for(const source of ['nthroot(81,4)','sqrt(x^2+1)','sqrt(2)^2','nthroot(2,3)^2','x^(2+3)','x^(y^2)','1/nthroot(x+1,3)']){
    for(let at=0;at<=source.length;at++){
      const math=expressionDisplay(source);markInputCursor(math,source,at);
      for(const node of math.querySelectorAll('mroot,msup,mfrac'))assert.equal(node.children.length,2,`${source} at ${at}: ${node.outerHTML}`);
      assert.equal(math.parentElement.querySelectorAll('.input-caret').length,1);
    }
  }
  dom.window.close();
});

test('powers lower exponent ink and retain MathML scripts, source positions, and derivative orders',()=>{
  const dom=new JSDOM();globalThis.document=dom.window.document;
  for(const source of ['sqrt(2)^2','nthroot(2,3)^2','(sqrt(2)+1)^2']){
    const math=expressionDisplay(source),power=math.querySelector('msup');
    assert.equal(power.children.length,2);assert.equal(power.lastElementChild.localName,'mpadded');
    assert.equal(power.lastElementChild.getAttribute('voffset'),'-0.3em');
    assert.equal(power.lastElementChild.firstElementChild.textContent,'2');
    markInputCursor(math,source,source.length);
    assert.equal(math.parentElement.querySelector('.input-caret').getAttribute('data-source-start'),String(source.length),'overlay tracks the exponent source position');
    assert.equal(power.lastElementChild.querySelector('.input-caret'),null,'cursor does not alter shifted script metrics');
  }
  assert.equal(expressionDisplay('x^2').querySelector('.math-exponent').getAttribute('voffset'),'-0.12em');
  const derivative=expressionDisplay('diff(x^3,x,2)');
  const fraction=derivative.querySelector('mfrac');
  assert.equal(fraction.children[0].localName,'msup');
  assert.equal(fraction.children[0].textContent,'d2');
  assert.equal(fraction.children[1].querySelector('msup').textContent,'x2');
  dom.window.close();
});

test('cursor overlay follows text ranges without splitting numbers, and updates on repeated moves',()=>{
  const dom=new JSDOM();globalThis.document=dom.window.document;
  const style=document.createElement('style');style.textContent=readFileSync(new URL('../calculator.css',import.meta.url),'utf8');document.head.append(style);
  document.documentElement.dataset.workspace='scientific';
  const preview=document.createElement('div');preview.className='expression-preview';document.body.append(preview);
  const math=expressionDisplay('sqrt(123)'),token=math.querySelector('mn');preview.append(math);
  // jsdom does not implement MathMLElement.style/computed styles. Provide
  // the browser's resolved token font size and geometry below.
  Object.defineProperty(token,'style',{value:{}});
  const computedStyle=dom.window.getComputedStyle;
  dom.window.getComputedStyle=node=>node===token?{fontSize:'24px'}:computedStyle(node);
  const caret=()=>math.parentElement.querySelector('.input-caret');
  token.getBoundingClientRect=()=>({left:120,right:156,top:46,height:20});
  markInputCursor(math,'sqrt(123)',5);
  const frame=math.parentElement;frame.getBoundingClientRect=()=>({left:100,top:40});
  dom.window.Range.prototype.getBoundingClientRect=function(){
    return {left:120+this.startOffset*12,right:120+this.endOffset*12,top:76,height:24};
  };
  const original=math.firstElementChild.outerHTML;
  for(const [at,left] of [[5,20],[6,32],[7,44],[8,56]]){
    markInputCursor(math,'sqrt(123)',at);
    const css=dom.window.getComputedStyle(caret()),style=caret().style;
    assert.equal(style.left,`${left}px`);assert.equal(style.top,'4px');assert.equal(style.height,'24px');
    assert.equal(css.position,'absolute');assert.equal(css.display,'block');assert.equal(css.width,'2px');assert.equal(css.pointerEvents,'none');
    assert.equal(dom.window.getComputedStyle(frame).position,'relative');
    assert.equal(math.firstElementChild.outerHTML,original);assert.equal(frame.querySelectorAll('.input-caret').length,1);
    assert.equal(math.querySelector('.input-caret'),null,'cursor is outside the MathML baseline coordinate system');
  }
  // Empty collapsed ranges use the adjacent character's edge instead.
  const measure=dom.window.Range.prototype.getBoundingClientRect;
  dom.window.Range.prototype.getBoundingClientRect=function(){return this.collapsed?{height:0}:measure.call(this);};
  markInputCursor(math,'sqrt(123)',6);assert.equal(caret().style.left,'32px');assert.equal(caret().style.top,'4px');
  markInputCursor(math,'sqrt(123)',5);assert.equal(caret().style.left,'20px');
  token.style.fontSize='16px';dom.window.getComputedStyle=node=>node===token?{fontSize:'16px'}:computedStyle(node);
  token.getBoundingClientRect=()=>({left:120,right:156,top:46,height:12});
  frame.getBoundingClientRect=()=>({left:90,top:30});
  markInputCursor(math,'sqrt(123)',6);assert.equal(caret().style.left,'42px');assert.equal(caret().style.top,'14px');assert.equal(caret().style.height,'16px');
  markInputCursor(math,'sqrt(123)',5,8);assert.equal(caret(),null);assert.ok(token.classList.contains('selected'));
  dom.window.close();
});

test('ENG and SCI normalize numeric results without losing high precision or changing exact structures',()=>{
  const dom=new JSDOM();globalThis.document=dom.window.document;
  const display=(value,notation,digits=10)=>mathDisplay({kind:'number',value},digits,true,{notation});
  assert.equal(display('12345','eng').textContent,'12.345×103');
  assert.equal(display('12345','sci').textContent,'1.2345×104');
  assert.equal(display('12345','off').textContent,'12345');
  assert.equal(display('-0.000123456','eng',3).textContent,'-123.456×10-6');
  assert.equal(display('-0.000123456','sci',3).textContent,'-1.235×10-4');
  assert.equal(display('12345678901234567890','sci',19).textContent,'1.234567890123456789×1019');
  assert.equal(display('1.25e-100','eng').textContent,'125×10-102');
  assert.equal(display('1.25e-100','sci').textContent,'1.25×10-100');
  assert.equal(display('0','sci').textContent,'0');
  const fraction={kind:'fraction',args:[{kind:'number',value:'12345'},{kind:'number',value:'7'}]},snapshot=JSON.stringify(fraction);
  assert.equal(mathDisplay(fraction,10,false,{notation:'sci'}).querySelectorAll('msup').length,0,'exact fraction leaves are not converted to powers');
  assert.equal(JSON.stringify(fraction),snapshot,'display formatting leaves the engine value intact');
  dom.window.close();
});
