import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {parse,latexInput} from '../parser.js';

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
test('LaTeX fractions, nested roots, and bounded integrals',()=>{
  assert.equal(latexInput('1/3+1/6'),'1/3+1/6');
  assert.equal(latexInput(String.raw`\[\frac{x^2+1}{x-1}\]`),'(x^2+1)/(x-1)');
  assert.equal(latexInput(String.raw`$$\int_{0}^{\infty} e^{-x^2} \times \cos(2x) \, dx$$`),'integrate(e^(-x^2)*cos(2x),x,0,oo)');
  assert.throws(()=>latexInput(String.raw`\frac{1}`));
});
