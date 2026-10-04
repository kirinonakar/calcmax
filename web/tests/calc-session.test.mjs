import test from 'node:test';
import assert from 'node:assert/strict';
import {parse} from '../parser.js';
import {calcVariables} from '../calc-session.js';

test('CALC prompts input variables once, excluding constants and calculus binders',()=>{
  assert.deepEqual(calcVariables(parse('x^2+y+x')),['x','y']);
  assert.deepEqual(calcVariables(parse('2+sin(pi/6)+c0')),[]);
  assert.deepEqual(calcVariables(parse('integrate(a*x,x,0,b)')),['a','b']);
  assert.deepEqual(calcVariables(parse('integrate(a*x,(x,0,b))')),['a','b']);
  assert.deepEqual(calcVariables(parse('solve(a*x=2,x)')),['a']);
  assert.deepEqual(calcVariables(parse('diff(a*x^2,x)+x')),['a','x']);
});
