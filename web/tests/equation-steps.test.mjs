import test from 'node:test';
import assert from 'node:assert/strict';
import {equationStepTrees,equationStepExplanation,solutionStepsCopyText} from '../equation-steps.js';
import {setLanguage} from '../i18n.js';

test('a row operation and its augmented matrix stay in one mathematical expression',()=>{
  const operation={kind:'relation',value:'←',args:[{kind:'symbol',value:'R2'},{kind:'symbol',value:'R1'}]};
  const matrix={kind:'matrix',augmentedColumn:2,args:[{kind:'list',args:[{kind:'number',value:'1'},{kind:'number',value:'0'},{kind:'number',value:'-1'}]}]};
  const trees=equationStepTrees({operation,equations:[{tree:matrix}]});
  assert.equal(trees.length,1);
  assert.equal(trees[0].kind,'row-operation');
  assert.deepEqual(trees[0].args,[operation,matrix]);
  assert.deepEqual(equationStepTrees({equations:[{tree:matrix}]}),[matrix]);
});

test('full solution copy retains displayed substitution, localized explanations and collapsed advanced formulas',()=>{
  const node=(kind,value='',args=[])=>({kind,value,args}),x=node('symbol','x');
  const replacement=node('parentheses','',[node('sum','',[node('number','1'),node('unary','-',[x])])]);
  const tree=node('relation','=',[node('sum','',[x,node('unary','-',[replacement])]),node('number','2')]);
  const report={method:'Substitution method',steps:[{
    title:'Substitute into the second equation',explanationParts:[{text:'Subtract {term} from both sides.',values:{term:'x'}}],equations:[{exact:'Eq(2*x - 1, 2)',tree}]
  },{title:'Computed result',tree:node('relation','=',[x,node('number','1.23456')])}],advancedSteps:[{
    title:'Augmented matrix',variableOrder:{tree:node('list','',[x,node('symbol','y')])},
    operation:node('relation','←',[node('symbol','R2'),node('symbol','R1')]),
    equations:[{tree:node('matrix','',[node('list','',[node('number','1'),node('number','0'),node('number','2')])])}]
  }]};
  try{
    setLanguage('ko');const text=solutionStepsCopyText(report,3);
    assert.ok(text.startsWith('단계별 풀이'));
    assert.ok(text.includes('양변에서 x를 뺍니다.'));
    assert.ok(text.includes('x-(1-x) = 2'));
    assert.ok(text.includes('x = 1.235'));
    assert.ok(text.includes('R2 ← R1\n[[1,0,2]]'));
    assert.ok(text.includes('[x,y]'));
    assert.ok(!text.includes('Eq(2*x'));
    setLanguage('en');assert.ok(solutionStepsCopyText(report).includes('Advanced solution · Gaussian elimination'));
  }finally{setLanguage('en');}
});

test('actual algebra operations translate before mathematical parameters are inserted',()=>{
  const step={explanationParts:[{text:'Subtract {term} from both sides.',values:{term:'x'}},{text:'Combine like terms.'}]};
  setLanguage('en');
  assert.equal(equationStepExplanation(step),'Subtract x from both sides. Combine like terms.');
  try{
    setLanguage('ko');
    assert.equal(equationStepExplanation(step),'양변에서 x를 뺍니다. 동류항을 정리합니다.');
    assert.equal(equationStepExplanation({explanationParts:[{text:'Divide both sides by {coefficient}.',values:{coefficient:'3'}}]}),'양변을 3로 나눕니다.');
  }finally{setLanguage('en');}
});
