import test from 'node:test';
import assert from 'node:assert/strict';
import {JSDOM} from 'jsdom';
import {resultMathDisplay,resultMathParts} from '../result-display.js';

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
