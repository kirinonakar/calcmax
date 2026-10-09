import test from 'node:test';
import assert from 'node:assert/strict';
import {integralPolygons} from '../graph-integral.js';
import {clipGraphSegment} from '../graph-geometry.js';

test('clip before projecting huge exponential coordinates and retain viewport crossings',()=>{
  const bounds={xmin:-10,xmax:10,ymin:-5,ymax:5};
  const segment=clipGraphSegment([0,0],[1,1e99],bounds);
  assert.deepEqual(segment[0],[0,0]);assert.equal(segment[1][1],5);assert.ok(Math.abs(segment[1][0]-5e-99)<1e-110);
  assert.deepEqual(clipGraphSegment([1,1e99],[0,0],bounds),[segment[1],segment[0]]);
  assert.equal(clipGraphSegment([1,1e50],[2,1e99],bounds),null);
  assert.equal(clipGraphSegment(null,[0,0],bounds),null);
  assert.deepEqual(clipGraphSegment([-100,-100],[100,100],bounds),[[-5,-5],[5,5]]);
  assert.deepEqual(clipGraphSegment([0,-1e99],[0,1e99],bounds),[[0,-5],[0,5]]);
});

test('integral fill interpolates boundaries of two-point lines and preserves discontinuities',()=>{
  assert.deepEqual(integralPolygons([[-10,-7],[10,13]],[0,1]),[[[0,0],[0,3],[1,4],[1,0]]]);
  assert.deepEqual(integralPolygons([[10,13],[-10,-7]],[1,0]),[[[1,0],[1,4],[0,3],[0,0]]]);
  const polygons=integralPolygons([[-2,-2],[-1,-1],null,[1,1],[2,2]],[-2,2]);
  assert.equal(polygons.length,2);assert.equal(polygons[0].at(-1)[0],-1);assert.equal(polygons[1][0][0],1);
  assert.deepEqual(integralPolygons([[-10,-7],[10,13]],[20,21]),[]);
});
