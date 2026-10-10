import test from 'node:test';
import assert from 'node:assert/strict';
import {integralPolygons} from '../graph-integral.js';
import {clipGraphSegment} from '../graph-geometry.js';
import {surfaceLightingGradient,prepareSurfaceFaces,projectSurfaceFaces,surfaceProjection} from '../surface-geometry.js';
import {surfaceCameraMatrix} from '../surface-gpu.js';

test('convex shells draw front faces last at every angle and camera depth agrees with projection',()=>{
  const vertices=[[2,0,0],[-2,0,0],[0,1,0],[0,-1,0],[0,0,.5],[0,0,-.5]],ids=[[0,2,4],[2,1,4],[1,3,4],[3,0,4],[2,0,5],[1,2,5],[3,1,5],[0,3,5]];
  const bounds={xmin:-3,xmax:3,ymin:-2,ymax:2,zmin:-1,zmax:1},triangles=ids.map(face=>face.map(i=>vertices[i]));
  const normals=ids.map(face=>face.map(i=>vertices[i].map((v,k)=>v/[4,1,.25][k])));
  const prepared=prepareSurfaceFaces([],bounds,triangles,normals,true);
  assert.equal(prepared.closed,true);
  const cut=prepareSurfaceFaces([], {...bounds,xmax:.5},triangles,normals,true);
  assert.equal(cut.closed,false);
  for(const rotation of [0,35,90,135,180,225,270,315,359])for(const elevation of [-90,-32,0,32,90]){
    const projection=surfaceProjection(bounds,rotation,elevation),faces=projectSurfaceFaces(prepared,projection);
    assert.ok(faces.length>0&&faces.every(face=>face.front));
    const projected=projectSurfaceFaces(cut,surfaceProjection({...bounds,xmax:.5},rotation,elevation));
    let front=false;for(const face of projected){if(face.front)front=true;else assert.equal(front,false,'back face painted over front');}
    const matrix=surfaceCameraMatrix(rotation,elevation,100,800,460);
    for(const point of vertices){
      const normalized=projection.normalize(point),clip=Array.from({length:3},(_,r)=>normalized.reduce((sum,v,c)=>sum+matrix[c*4+r]*v,0));
      const p=projection.project(point);
      assert.ok(Math.abs(clip[0]-p[0]*.25)<1e-6);assert.ok(Math.abs(clip[1]+p[1]*200/460)<1e-6);assert.ok(Math.abs(clip[2]+p[2]/4)<1e-6);
    }
  }
});

test('smooth lighting reproduces every vertex and both triangles agree on their shared edge',()=>{
  const first=[[0,0],[2,0],[0,2]],second=[[2,0],[2,2],[0,2]];
  const a=surfaceLightingGradient(first,[.3,.8,.5]),b=surfaceLightingGradient(second,[.8,.9,.5]);
  const brightness=(gradient,point)=>{
    const d=gradient.end.map((v,i)=>v-gradient.start[i]);
    const fraction=point.reduce((sum,v,i)=>sum+(v-gradient.start[i])*d[i],0)/d.reduce((sum,v)=>sum+v*v,0);
    return gradient.min+(gradient.max-gradient.min)*fraction;
  };
  for(const [i,p] of first.entries())assert.ok(Math.abs(brightness(a,p)-[.3,.8,.5][i])<1e-12);
  for(const t of [0,.25,.5,.75,1])assert.ok(Math.abs(brightness(a,[2*(1-t),2*t])-brightness(b,[2*(1-t),2*t]))<1e-12);
  assert.equal(surfaceLightingGradient(first,[.5,.5,.5]),null);
});

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
