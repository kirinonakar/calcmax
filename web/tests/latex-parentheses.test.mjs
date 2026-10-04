import test from 'node:test';
import assert from 'node:assert/strict';
import {JSDOM} from 'jsdom';
import {latexInput,parse} from '../parser.js';
import {expressionDisplay,expressionInputDisplay} from '../expression-display.js';

test('LaTeX fraction fences stay single in pasted integrals, previews, and editable input',t=>{
  const dom=new JSDOM(''),previous=globalThis.document;
  globalThis.document=dom.window.document;
  t.after(()=>{globalThis.document=previous;dom.window.close();});
  for(const command of ['frac','dfrac','tfrac'])for(const delimiters of ['', '$', '$$', '\\[', '\\(']){
    const body=String.raw`\int_{0}^{1} \left( ${'\\'+command}{x}{x} \right) dx`;
    const closing={'\\[':'\\]','\\(':'\\)'}[delimiters]??delimiters;
    const source=latexInput(delimiters+body+closing);
    assert.equal(source,'integrate(((x)/(x)),x,0,1)');
    assert.equal(parse(source).args[0].args[0].value,'/');
    for(const frame of [expressionDisplay(source),expressionInputDisplay(source)]){
      assert.equal(frame.querySelectorAll('mfrac').length,1);
      assert.equal([...frame.querySelectorAll('mo')].filter(n=>n.textContent==='(').length,1);
      assert.equal([...frame.querySelectorAll('mo')].filter(n=>n.textContent===')').length,1);
    }
  }
  for(const body of [String.raw`\left(\left(\frac{x}{x}\right)\right)`,String.raw`((\frac{x}{x}))`]){
    const source=latexInput(body);
    assert.equal(source,'(((x)/(x)))');
    assert.equal([...expressionDisplay(source).querySelectorAll('mo')].filter(n=>n.textContent==='(').length,2);
  }
  assert.equal(latexInput(String.raw`\left(\frac{x}{2}\right)^2`),'((x)/(2))^2');
  assert.equal(latexInput(String.raw`\left(\frac{x}{2}^2\right)`),'(((x)/(2))^2)');
  assert.equal(latexInput('((x/2))'),'((x/2))');
});
