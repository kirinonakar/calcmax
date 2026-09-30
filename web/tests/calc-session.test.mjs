import test from 'node:test';
import assert from 'node:assert/strict';
import {parse} from '../parser.js';
import {calcVariables,calcBindings} from '../calc-session.js';

test('CALC prompts input variables once, excluding constants and calculus binders',()=>{
  assert.deepEqual(calcVariables(parse('x^2+y+x')),['x','y']);
  assert.deepEqual(calcVariables(parse('2+sin(pi/6)+c0')),[]);
  assert.deepEqual(calcVariables(parse('integrate(a*x,x,0,b)')),['a','b']);
  assert.deepEqual(calcVariables(parse('integrate(a*x,(x,0,b))')),['a','b']);
  assert.deepEqual(calcVariables(parse('solve(a*x=2,x)')),['a']);
  assert.deepEqual(calcVariables(parse('diff(a*x^2,x)+x')),['a','x']);
});
test('CALC traverses result AST definitions and makes frozen symbols editable only for calculation',()=>{
  const variables={C:{kind:'binary',value:'+',args:[{kind:'snapshot_symbol',value:'A'},{kind:'snapshot_symbol',value:'B'}]},A:parse('2'),B:parse('3'),Ans:{kind:'snapshot_symbol',value:'A'}};
  const original=JSON.stringify(variables);
  assert.deepEqual(calcVariables(parse('C'),variables),['A','B']);
  const bindings=calcBindings(variables);
  assert.equal(bindings.C.args[0].kind,'symbol');assert.equal(bindings.C.args[1].kind,'symbol');
  assert.equal(bindings.Ans,variables.Ans,'Ans retains its snapshot semantics');
  assert.equal(JSON.stringify(variables),original,'saved definitions retain their snapshot semantics');
});
test('recalled formulas expose their input variables; numeric values remain editable',()=>{
  assert.deepEqual(calcVariables(parse('A+1'),{A:parse('x^2+y')}),['x','y']);
  assert.deepEqual(calcVariables(parse('x+1'),{x:{kind:'number',value:'2',args:[]}}),['x']);
  assert.deepEqual(calcVariables(parse('A'),{A:parse('B'),B:parse('A')}),['A']);
});
