import test from 'node:test';
import assert from 'node:assert/strict';
import {JSDOM} from 'jsdom';
import {expressionDisplay,expressionInputDisplay} from '../expression-display.js';
import {latexInput} from '../parser.js';
import {mathDisplay} from '../math-display.js';
import {markInputCursor} from '../input-cursor.js';

function page(t){
  const dom=new JSDOM('');
  const previous=globalThis.document;
  globalThis.document=dom.window.document;
  t.after(()=>{globalThis.document=previous;dom.window.close();});
}

test('theta and the supplied fraction render as symbols and function powers',t=>{
  page(t);
  const math=expressionDisplay(latexInput(String.raw`$$\frac{\sin\theta}{1-\cos^2\theta}$$`));
  const fraction=math.querySelector('mfrac');
  assert.ok(fraction);
  assert.equal(fraction.children[0].textContent,'sin(θ)');
  assert.equal(fraction.children[1].textContent,'1-cos2(θ)');
  assert.equal(fraction.querySelector('msup > mi').textContent,'cos');
  assert.equal(math.querySelectorAll('mi').length,4);
  assert.equal(expressionDisplay('theta+θ').textContent,'θ+θ');
});

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

test('leaving a function exponent puts the caret after the complete function',t=>{
  page(t);
  const source='sin(x)^2',frame=expressionInputDisplay(source);
  document.body.append(frame);
  const power=frame.querySelector('[data-function-power]'),name=power.querySelector('mi'),part=frame.querySelector('.input-part');
  const bounds=(left,top,width,height)=>({left,top,width,height,right:left+width,bottom:top+height});
  frame.getBoundingClientRect=()=>bounds(0,0,200,40);
  power.getBoundingClientRect=()=>bounds(10,2,100,35);
  part.getBoundingClientRect=()=>bounds(10,2,100,40);
  name.getBoundingClientRect=()=>bounds(10,12,25,20);
  const caret=markInputCursor(frame,source,source.length,source.length,{boundary:'after',structure:{start:0,end:source.length,exponentEnd:source.length}});
  assert.equal(caret.style.left,'112px');
  assert.equal(caret.style.top,'12px');
});
