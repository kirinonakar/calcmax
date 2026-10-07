import test from 'node:test';
import assert from 'node:assert/strict';
import {integralPolygons} from '../graph-integral.js';

test('integral fill interpolates boundaries of two-point lines and preserves discontinuities',()=>{
  assert.deepEqual(integralPolygons([[-10,-7],[10,13]],[0,1]),[[[0,0],[0,3],[1,4],[1,0]]]);
  assert.deepEqual(integralPolygons([[10,13],[-10,-7]],[1,0]),[[[1,0],[1,4],[0,3],[0,0]]]);
  const polygons=integralPolygons([[-2,-2],[-1,-1],null,[1,1],[2,2]],[-2,2]);
  assert.equal(polygons.length,2);assert.equal(polygons[0].at(-1)[0],-1);assert.equal(polygons[1][0][0],1);
  assert.deepEqual(integralPolygons([[-10,-7],[10,13]],[20,21]),[]);
});
