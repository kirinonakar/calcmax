import test from 'node:test';
import assert from 'node:assert/strict';
import {parse,latexInput} from '../parser.js';
import {equationCommand} from '../workspace-commands.js';

test('equation workspace converts LaTeX before adding the solver call',()=>{
  const body=String.raw`\int _{-2}^{a} f(x) dx = \int _{-2}^{0} f(x) dx`;
  for(const source of [body,`$$${body}$$`,`$${body}$`,String.raw`\[${body}\]`,String.raw`\(${body}\)`]) {
    const command=equationCommand({source,variable:'a'});
    assert.equal(command,'solve(integrate(f(x),x,-2,a)=integrate(f(x),x,-2,0),a)');
    assert.equal(parse(latexInput(command)).args[0].kind,'relation');
    assert.equal(equationCommand({kind:'nsolve',source,variable:'a',extra:'9'}),
      'nsolve(integrate(f(x),x,-2,a)=integrate(f(x),x,-2,0),a,9)');
  }
});

test('each equation in a system converts its own math delimiters',()=>{
  const source=String.raw`$$\int _{-2}^{a} f(x) dx = \int _{-2}^{0} f(x) dx$$`+'\n'+String.raw`$\frac{b}{2}=1$`;
  const command=equationCommand({source,variable:'a,b'});
  assert.equal(command,'solve([integrate(f(x),x,-2,a)=integrate(f(x),x,-2,0),((b)/(2))=1],[a,b])');
  assert.equal(parse(command).args[0].args.length,2);
});
