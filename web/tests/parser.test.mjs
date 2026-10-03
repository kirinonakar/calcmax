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
  assert.equal(latexInput(String.raw`\[\frac{x^2+1}{x-1}\]`),'((x^2+1)/(x-1))');
  assert.equal(latexInput(String.raw`$$\int_{0}^{\infty} e^{-x^2} \times \cos(2x) \, dx$$`),'integrate(e^(-x^2)*cos(2x),x,0,oo)');
  assert.throws(()=>latexInput(String.raw`\frac{1}`));
});

test('LaTeX integral equations allow whitespace around bounds and differentials',()=>{
  for(const body of [
    String.raw`\int _{-2}^{a} f(x) dx = \int _{-2}^{0} f(x) dx`,
    String.raw`\int_{-2}^{a} f(x) dx = \int_{-2}^{0} f(x) dx`,
    String.raw`\int _ {-2} ^ {a} f(x) \, d x = \int _ {-2} ^ {0} f(x) \, d x`
  ])for(const source of [body,`$$${body}$$`,`$${body}$`,String.raw`\[${body}\]`,String.raw`\(${body}\)`]) {
    const converted=latexInput(source);
    assert.equal(converted,'integrate(f(x),x,-2,a)=integrate(f(x),x,-2,0)');
    const tree=parse(converted);
    assert.equal(tree.kind,'relation');
    assert.deepEqual(tree.args.map(side=>side.value),['integrate','integrate']);
    assert.deepEqual(tree.args.map(side=>side.args[3].value),['a','0']);
  }
  for(const source of [String.raw`\int _{-2} f(x) dx`,String.raw`\int _{-2}^{a} f(x)`,String.raw`\int _{}^{a} f(x) dx`]) {
    assert.throws(()=>latexInput(source),SyntaxError,source);
  }
});

test('LaTeX theta equations accept every supported math delimiter and preserve the variable',()=>{
  const body=String.raw`\cos\left(\frac{\pi}{2} + \theta\right) = -\frac{1}{5}`;
  for(const source of [body,`$$${body}$$`,`$${body}$`,String.raw`\[${body}\]`,String.raw`\(${body}\)`]) {
    const converted=latexInput(source);
    assert.equal(converted,'cos(((pi)/(2))+theta)=-((1)/(5))');
    const tree=parse(converted);
    assert.equal(tree.kind,'relation');
    assert.equal(tree.args[0].value,'cos');
    assert.equal(tree.args[0].args[0].args[1].kind,'symbol');
    assert.equal(tree.args[0].args[0].args[1].value,'theta');
  }
  assert.equal(latexInput(String.raw`\theta`),'theta');
  assert.equal(latexInput(String.raw`\theta^{2}`),'theta^(2)');
  assert.throws(()=>latexInput(String.raw`$$\thetaUnknown$$`),SyntaxError);
});

test('LaTeX logarithms keep the base and argument separate',()=>{
  const body=String.raw`a = 2 \log \frac{1}{\sqrt{10}} + \log_2 20 `;
  for(const source of [body,`$$${body}$$`,`$${body}$`,String.raw`\[${body}\]`,String.raw`\(${body}\)`]) {
    const converted=latexInput(source);
    assert.equal(converted,'a=2log(((1)/(sqrt(10))))+log(20,2)');
    const tree=parse(converted);
    assert.equal(tree.kind,'relation');
    assert.equal(tree.args[0].value,'a');
    const basedLog=tree.args[1].args[1];
    assert.equal(basedLog.kind,'call');
    assert.equal(basedLog.value,'log');
    assert.deepEqual(basedLog.args.map(node=>node.value),['20','2']);
  }
  for(const [source,expected] of [
    [String.raw`\log_{10}{100}`,'log((100),(10))'],
    [String.raw`\log_2 \left(20\right)`,'log((20),2)'],
    [String.raw`\log_2 \frac{1}{\sqrt{10}}`,'log(((1)/(sqrt(10))),2)'],
    [String.raw`\log_{\sqrt{2}} 4`,'log(4,(sqrt(2)))'],
    [String.raw`\log_2 x^2+1`,'log(x^2,2)+1'],
    [String.raw`\log_2 \log_3 9`,'log(log(9,3),2)'],
    [String.raw`\log\,100`,'log(100)'],
    [String.raw`\log(8,2)`,'log(8,2)']
  ])assert.equal(latexInput(source),expected,source);
  for(const source of [String.raw`\log_`,String.raw`\log_2`,String.raw`\log_{} 20`,String.raw`\log_2 +20`,String.raw`\log_{2 20`]) {
    assert.throws(()=>latexInput(source),SyntaxError,source);
  }
});

test('LaTeX indexed roots and fractional powers accept every supported math delimiter',()=>{
  const body=String.raw`\sqrt[3]{5} \times 25^{\frac{1}{3}}`;
  for(const source of [body,`$$${body}$$`,`$${body}$`,String.raw`\[${body}\]`,String.raw`\(${body}\)`]) {
    const converted=latexInput(source);
    assert.equal(converted,'nthroot(5,3)*25^(((1)/(3)))');
    const tree=parse(converted);
    assert.equal(tree.value,'*');
    assert.equal(tree.args[0].value,'nthroot');
    assert.deepEqual(tree.args[0].args.map(node=>node.value),['5','3']);
    assert.equal(tree.args[1].value,'^');
  }
});

test('LaTeX roots support nested groups, expression degrees, and negative radicands',()=>{
  assert.equal(latexInput(String.raw`\sqrt [3] {\sqrt{\frac{1}{2}}}`),'nthroot(sqrt(((1)/(2))),3)');
  assert.equal(latexInput(String.raw`\sqrt[1+1]{\sqrt[3]{64}}`),'nthroot(nthroot(64,3),1+1)');
  assert.equal(latexInput(String.raw`\sqrt[\frac{4}{2}]{16}`),'nthroot(16,((4)/(2)))');
  assert.equal(latexInput(String.raw`\sqrt[3]{-8}`),'nthroot(-8,3)');
});

test('LaTeX fractions remain whole power bases and recognize display and inline variants',()=>{
  for(const command of ['frac','dfrac','tfrac']) {
    const converted=latexInput(`\\${command}{1}{2}^2`);
    assert.equal(converted,'((1)/(2))^2');
    const tree=parse(converted);
    assert.equal(tree.value,'^');
    assert.equal(tree.args[0].args[0].value,'/');
  }
  assert.equal(latexInput(String.raw`\ln(2)`),'ln(2)');
  assert.equal(latexInput('25^{1/3}'),'25^(1/3)');
});

test('malformed or unsupported LaTeX is rejected before insertion',()=>{
  for(const source of [String.raw`\sqrt[3{5}`,String.raw`\sqrt[]{5}`,String.raw`\sqrt[3]{}`,String.raw`\sqrt[3]{5`,String.raw`\sqrt[3]`,String.raw`\frac{1}`,String.raw`$$\unknown{1}$$`,String.raw`$$\sqrt{5}$`]) {
    assert.throws(()=>latexInput(source),SyntaxError,source);
  }
});
