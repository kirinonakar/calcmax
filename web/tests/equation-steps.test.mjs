import test from 'node:test';
import assert from 'node:assert/strict';
import {equationStepTrees,equationStepExplanation} from '../equation-steps.js';
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
