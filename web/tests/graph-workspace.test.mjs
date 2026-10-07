import test from 'node:test';
import assert from 'node:assert/strict';
import {graphShadings} from '../graph-workspace.js';

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
