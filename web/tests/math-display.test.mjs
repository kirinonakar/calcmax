import test from 'node:test';
import assert from 'node:assert/strict';
import {JSDOM} from 'jsdom';
import {mathDisplay} from '../math-display.js';
import {expressionDisplay} from '../expression-display.js';
import {markInputCursor} from '../input-cursor.js';

test('each root has one native radical and input cursors do not increase radicand height',()=>{
  const dom=new JSDOM();globalThis.document=dom.window.document;
  for(const source of ['sqrt()','sqrt(2)','nthroot(81,4)','sqrt(1/2)','sqrt(sqrt(2))','sqrt(2)^2']){
    for(let at=0;at<=source.length;at++){
      const math=expressionDisplay(source);markInputCursor(math,source,at);
      assert.equal(math.querySelectorAll('msqrt,mroot').length,source.match(/sqrt\(|nthroot\(/g).length,source);
      assert.equal(math.querySelectorAll('svg,.math-radical,.radical-stroke').length,0,'no duplicate or unbounded SVG radical');
      if(source==='sqrt()')assert.ok(math.querySelector('msqrt .input-slot'),'the empty input remains under the native roof');
      const caret=math.querySelector('.input-caret');assert.equal(caret.getAttribute('height'),'0');assert.equal(caret.getAttribute('depth'),'0');
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
      assert.equal(math.querySelectorAll('.input-caret').length,1);
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
    assert.ok(power.lastElementChild.querySelector('.input-caret'),'exponent cursor stays in the shifted script');
  }
  assert.equal(expressionDisplay('x^2').querySelector('.math-exponent').getAttribute('voffset'),'-0.12em');
  const derivative=expressionDisplay('diff(x^3,x,2)');
  const fraction=derivative.querySelector('mfrac');
  assert.equal(fraction.children[0].localName,'msup');
  assert.equal(fraction.children[0].textContent,'d2');
  assert.equal(fraction.children[1].querySelector('msup').textContent,'x2');
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
