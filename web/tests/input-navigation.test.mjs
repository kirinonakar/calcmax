import test from 'node:test';
import {functionRelationExit,emptyPowerDeletion,emptyFractionDeletion,moveMathCursor,mathStructureExit} from '../input-navigation.js';
import assert from 'node:assert/strict';
import {bindKeyPress} from '../keypad.js';

import {parse} from '../parser.js';
import {requiresExplicitEvaluation} from '../evaluation-policy.js';

test('structured navigation preserves equation scope and empty-template editing',()=>{
  { // equality moves

  for(const source of ['diff(x,x)','integrate(x,x)','sin(x)','sin(diff(x,x))','f(x)']){
    const at=source.indexOf('x')+1;
    assert.equal(functionRelationExit(source,at,at),source.length,source);
  }
  assert.equal(functionRelationExit('diff(x,x)',7,7),9,'the right arrow may move past the comma');
  assert.equal(functionRelationExit('solve(x,x)',7,7),null);
  assert.equal(functionRelationExit('solve(sin(x),x)',11,11),12);
  const nested='dsolve(diff(y(t),t),y(t),t)',at=nested.indexOf('y(t)')+3;
  assert.equal(functionRelationExit(nested,at,at),nested.indexOf(',y(t)'));
  assert.equal(functionRelationExit('piecewise((x,x>0),(0,true))',12,12),null);
  for(const [source,start,end] of [['diff(x,x)',5,6],['diff(x,x)',0,0],['sin(x)',6,6],['sin(x>',6,6]])assert.equal(functionRelationExit(source,start,end),null,source);

  }
  { // Right exits completed

  for(const source of ['5^2','5^3','5^(-1)','(9)^(9)','5^(23)+1']){
    const treeEnd=source.endsWith('+1')?source.length-2:source.length;
    const at=source[treeEnd-1]===')'?treeEnd-1:treeEnd;
    assert.deepEqual(mathStructureExit(source,at,at,'RIGHT'),{position:treeEnd,start:0,end:treeEnd,exponentEnd:at},source);
    assert.equal(moveMathCursor(source,at,at,'RIGHT'),treeEnd,source);
  }
  for(const source of ['2^(3^4)','1/(2^3)','2^(1/3)','2^3^4']){
    const at=source.endsWith(')')?source.length-1:source.length;
    const inner=mathStructureExit(source,at,at,'RIGHT');assert.ok(inner.start>0,source);
    const outer=mathStructureExit(source,inner.position,inner.position,'RIGHT',inner);
    assert.equal(outer.start,0,source);assert.equal(outer.position,source.length,source);
  }
  for(const [source,at] of [['5^()',3],['5^(2+)',5],['5^(234)',5]])
    assert.equal(mathStructureExit(source,at,at,'RIGHT'),null,source);
  assert.equal(mathStructureExit('5^2',2,3,'RIGHT'),null,'selection collapse is not an exponent exit');
  assert.equal(mathStructureExit('5^2',3,3,'LEFT'),null);

  }
  { // empty powers and fractions

  for(const [source,position,remove] of [
    ['()^2',1,emptyPowerDeletion],['()^3',1,emptyPowerDeletion],['()^(-1)',1,emptyPowerDeletion],
    ['()^()',1,emptyPowerDeletion],['()^()',4,emptyPowerDeletion],
    ['()/()',1,emptyFractionDeletion],['()/()',4,emptyFractionDeletion],
  ]){
    assert.deepEqual(remove(source,position,position),{start:0,end:source.length,text:''},source);
    assert.deepEqual(remove(`sin(${source})`,position+4,position+4),{start:4,end:source.length+4,text:''},source);
  }
  assert.deepEqual(emptyPowerDeletion('2*()^2*3',3,3),{start:2,end:6,text:'()'});
  for(const [source,at,remove,expected] of [
    ['3^()',3,emptyPowerDeletion,'3'],['(3)^()',5,emptyPowerDeletion,'3'],
    ['(3)/()',5,emptyFractionDeletion,'3'],['(3+4)/()',7,emptyFractionDeletion,'(3+4)'],
  ])assert.deepEqual(remove(source,at,at),{start:0,end:source.length,text:expected,cursor:expected.length});
  for(const source of ['(2)^2','(2)^(-1)','(1)/(2)']){
    assert.equal(emptyPowerDeletion(source,source.length,source.length),null);
    assert.equal(emptyFractionDeletion(source,source.length,source.length),null);
  }
  assert.equal(emptyFractionDeletion('1÷',2,2),null,'plain division keeps its text deletion behavior');
  assert.equal(emptyPowerDeletion('()^2',0,4),null,'ordinary selections keep their behavior');

  }
});


test('preview policy evaluates arithmetic but waits for expensive or stateful calls',()=>{
  for(const source of ['2+3*4','cos(2*x)','f(1)','log(100,10)','nthroot(8,3)'])
    assert.equal(requiresExplicitEvaluation(parse(source)),false,source);
  for(const source of ['rnd()','integrate(exp(-x^2),(x,0,oo))','1+dot([1,2],[3,4])','f(1,2)'])
    assert.equal(requiresExplicitEvaluation(parse(source)),true,source);
  assert.equal(requiresExplicitEvaluation(parse('1+f(1)'),new Set(['f'])),true);
});
