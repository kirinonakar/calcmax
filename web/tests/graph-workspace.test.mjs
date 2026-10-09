import test from 'node:test';
import assert from 'node:assert/strict';
import {graphShadings,createGraphInputHistory,removeGraphSource} from '../graph-workspace.js';

test('graph undo restores deleted curves, shading, derivatives and parameter settings',()=>{
  const history=createGraphInputHistory();
  let current={source:'a*x\ncos(x)\n[s] x, 2, -1..1',derivative:0,secondDerivative:1,parameters:{a:2.5},parameterRanges:{a:[-5,5]}};
  const original=structuredClone(current);
  history.remember('cartesian',current);
  current.source=removeGraphSource(current.source,0,'cartesian',true);current.derivative=null;current.secondDerivative=null;
  history.remember('cartesian',current);
  current.source=removeGraphSource(current.source,0);
  history.remember('cartesian',current);
  current.source=removeGraphSource(current.source,0);current.parameters.a=99;
  assert.equal(current.source,'');
  assert.equal(history.canUndo('polar'),false);
  assert.equal(history.undo('cartesian').source,'cos(x)');
  assert.equal(history.undo('cartesian').source,'a*x\ncos(x)');
  assert.deepEqual(history.undo('cartesian'),original);
  assert.equal(history.canUndo('cartesian'),false);
  assert.equal(history.undo('cartesian'),undefined);
});

test('second derivative toggles can be undone independently of the first derivative',()=>{
  const history=createGraphInputHistory(),before={source:'x^3',derivative:0,secondDerivative:null};
  history.remember('cartesian',before);
  history.remember('cartesian',{...before,secondDerivative:0});
  assert.equal(history.undo('cartesian').secondDerivative,0);
  assert.deepEqual(history.undo('cartesian'),before);
});

test('shading bands combine x and y restrictions regardless of input order',()=>{
  for(const source of ['sin(x), cos(x), -1<x<2, y<0','y<0, -1<x<2, sin(x), cos(x)']){
    const [item]=graphShadings(`[s] ${source}`,'cartesian');
    assert.equal(item.mode,'band');assert.deepEqual(item.trees.map(tree=>tree.value),['sin','cos']);
    assert.deepEqual(item.xBounds.map(bound=>bound.side),['lower','upper']);
    assert.equal(item.yBounds.length,1);assert.equal(item.yBounds[0].side,'upper');assert.equal(item.yBounds[0].tree.value,'0');
  }
  const [item]=graphShadings('[shade] x, 2, -1<y<0','cartesian');
  assert.deepEqual(item.yBounds.map(bound=>bound.side),['lower','upper']);
});
