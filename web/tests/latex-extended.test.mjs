import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {JSDOM} from 'jsdom';
import {latexInput,latexSymbolLabels,parse} from '../parser.js';
import {expressionDisplay,expressionInputDisplay} from '../expression-display.js';

export const latexCases=readFileSync(new URL('../../math/src/test/resources/latex-input.tsv',import.meta.url),'utf8').trim().split(/\r?\n/).map(line=>line.split('\t'));

test('extended LaTeX has the same editable conversion fixtures as Android',()=>{
  for(const [source,expected] of latexCases)for(const [open,close] of [['',''],['$','$'],['$$','$$'],['\\[','\\]'],['\\(','\\)']]) {
    assert.equal(latexInput(open+source+close),expected,source);
    parse(expected);
  }
  for(const name of Object.keys(latexSymbolLabels))assert.equal(latexInput(`\\${name}`),name);
});

test('invalid operator bounds, matrices, and missing differentials are rejected',()=>{
  for(const source of [String.raw`\sum k`,String.raw`\sum_{k}^{3} k`,String.raw`\prod_{k=1} k`,String.raw`\int_0 x dx`,String.raw`\int_0^1 x`,String.raw`\int x dxfoo`,String.raw`\begin{pmatrix}1&2\\3\end{pmatrix}`,String.raw`\begin{matrix}1&\end{matrix}`,String.raw`\begin{matrix}1`,String.raw`\begin{cases}x\end{cases}`,String.raw`\binom{5}`])assert.throws(()=>latexInput(source),undefined,source);
});

test('Greek symbols and pasted matrices render in previews and editable inputs',t=>{
  const dom=new JSDOM(''),previous=globalThis.document;
  globalThis.document=dom.window.document;
  t.after(()=>{globalThis.document=previous;dom.window.close();});
  for(const render of [expressionDisplay,expressionInputDisplay]) {
    const source=latexInput(String.raw`\alpha+\beta+\Gamma+\varphi`);
    assert.equal(render(source).textContent,'α+β+Γ+φ');
    const matrix=render(latexInput(String.raw`\begin{bmatrix}1&2\\3&4\end{bmatrix}`));
    assert.equal(matrix.querySelectorAll('mtr').length,2);
    assert.equal(matrix.querySelectorAll('mtd').length,4);
  }
});
