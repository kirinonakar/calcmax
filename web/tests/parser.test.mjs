import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {parse,latexInput,latexSymbolLabels} from '../parser.js';

test('fraction compaction respects the surrounding insertion scope and existing fences',()=>{
  for(const [prefix,suffix,expected] of [
    ['2','','(1/5)'],['1/','','(1/5)'],['','^2','(1/5)'],['(2)+','+(3)','1/5'],['sin(',')','1/5']
  ])assert.equal(latexInput(String.raw`\frac{1}{5}`,{prefix,suffix}),expected);
  assert.equal(latexInput(String.raw`\frac{1}{2x}`),'1/(2x)');
  assert.equal(latexInput(String.raw`\frac{1}{(x)(x+1)}`),'1/((x)(x+1))');
  assert.equal(latexInput(String.raw`\frac{1}{(x^2)^3}`),'1/(x^2)^3');
});

test('LaTeX limits preserve the approach, function power, and expression scope',()=>{
  const body=String.raw`\lim_{x \to 0} \frac{3x^2}{\sin^2 x}`;
  for(const source of [body,`$$${body}$$`,`$${body}$`,String.raw`\[${body}\]`,String.raw`\(${body}\)`]){
    const converted=latexInput(source);
    assert.equal(converted,'limit(3x^2/sin(x)^2,x,0)');
    const tree=parse(converted);
    assert.equal(tree.value,'limit');
    assert.deepEqual(tree.args.slice(1).map(n=>n.value),['x','0']);
    assert.equal(tree.args[0].args[1].args[0].value,'sin');
  }
  for(const [source,expected] of [
    [String.raw`\lim _ {x \rightarrow \infty} \frac{1}{x}`,'limit(1/x,x,oo)'],
    [String.raw`\lim_{x \to 0^+} 1/x`,'limit(1/x,x,0,right)'],
    [String.raw`\lim_{x \to 0^{-}} 1/x`,'limit(1/x,x,0,left)'],
    [String.raw`\lim_{\theta \to \pi} \cos\theta`,'limit(cos(theta),theta,pi)'],
    [String.raw`(\lim_{x \to 0} x)+2`,'limit(x,x,0)+2'],
    [String.raw`\left(\lim_{x \to 0} x\right)+2`,'limit(x,x,0)+2'],
    [String.raw`\lim_{x \to 0} x=0`,'limit(x,x,0)=0'],
    [String.raw`\lim_{x \to 0} (x+1)`,'limit(x+1,x,0)'],
    [String.raw`\lim_{x \to 0} \frac{3x^2+1}{\sin^2 x+2}`,'limit((3x^2+1)/(sin(x)^2+2),x,0)'],
    [String.raw`\lim_{x \to 0} \frac{x}{2}^2`,'limit((x/2)^2,x,0)'],
    [String.raw`\lim_{x \to 0} x^{\frac{1}{2}}`,'limit(x^(1/2),x,0)'],
    [String.raw`\lim_{x \to 0} \frac{1}{x(x+1)}`,'limit(1/x(x+1),x,0)'],
    [String.raw`\lim_{x \to 0} \frac{1}{2x}`,'limit(1/(2x),x,0)'],
    [String.raw`\lim_{x \to 0} (x)(x+1)`,'limit((x)(x+1),x,0)'],
    [String.raw`\lim_{x \to 0} (x^2)^3`,'limit((x^2)^3,x,0)'],
    [String.raw`\frac{\lim_{x \to 0} x+1}{2}`,'limit(x+1,x,0)/2']
  ])assert.equal(latexInput(source),expected,source);
  for(const source of [String.raw`\lim`,String.raw`\lim_x x`,String.raw`\lim_{x 0} x`,String.raw`\lim_{x+1 \to 0} x`,String.raw`\lim_{x \to} x`,String.raw`\lim_{x \to 0}`])assert.throws(()=>latexInput(source),SyntaxError,source);
});

test('web parser matches every AST exported by the Kotlin parser',()=>{
  const cases=JSON.parse(readFileSync(new URL('./fixtures/math-cases.json',import.meta.url),'utf8'));
  for(const {source,tree} of cases) assert.deepEqual(parse(source),tree,source);
});
test('power precedence, limits, invalid input, and DMS',()=>{
  assert.equal(parse('-2^2').kind,'unary');
  assert.equal(parse('2^3^2').args[1].value,'^');
  assert.equal(parse('12°30′15″').kind,'sexagesimal');
  assert.equal(parse('rnd()').value,'rnd');
  for(const input of ['','1..2','sin()','1+','[1,]','process.exit()','1;2','('.repeat(100)+'1'+')'.repeat(100),'1'.repeat(8193)]) assert.throws(()=>parse(input),SyntaxError,input);
});

test('invalid LaTeX rejects unsupported syntax, operators and incomplete matrices',()=>{
  { // malformed or unsupported

  for(const source of [String.raw`\sqrt[3{5}`,String.raw`\sqrt[]{5}`,String.raw`\sqrt[3]{}`,String.raw`\sqrt[3]{5`,String.raw`\sqrt[3]`,String.raw`\frac{1}`,String.raw`$$\unknown{1}$$`,String.raw`$$\sqrt{5}$`]) {
    assert.throws(()=>latexInput(source),SyntaxError,source);
  }

  }
  { // invalid operator bounds

  for(const source of [String.raw`\sum k`,String.raw`\sum_{k}^{3} k`,String.raw`\prod_{k=1} k`,String.raw`\int_0 x dx`,String.raw`\int_0^1 x`,String.raw`\int x dxfoo`,String.raw`\begin{pmatrix}1&2\\3\end{pmatrix}`,String.raw`\begin{matrix}1&\end{matrix}`,String.raw`\begin{matrix}1`,String.raw`\begin{cases}x\end{cases}`,String.raw`\binom{5}`])assert.throws(()=>latexInput(source),undefined,source);

  }
});
const latexCases=readFileSync(new URL('../../math/src/test/resources/latex-input.tsv',import.meta.url),'utf8').trim().split(/\r?\n/).map(line=>line.split('\t'));

test('extended LaTeX has the same editable conversion fixtures as Android',()=>{
  for(const [source,expected] of latexCases)for(const [open,close] of [['',''],['$','$'],['$$','$$'],['\\[','\\]'],['\\(','\\)']]) {
    assert.equal(latexInput(open+source+close),expected,source);
    parse(expected);
  }
  for(const name of Object.keys(latexSymbolLabels))assert.equal(latexInput(`\\${name}`),name);
});
