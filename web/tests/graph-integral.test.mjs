import test from 'node:test';
import assert from 'node:assert/strict';
import {JSDOM} from 'jsdom';
import {integralPolygons} from '../graph-integral.js';
import {plotGraph} from '../graph-canvas.js';
import {plot} from '../plot.js';
import {installCanvas,surfaceFills} from './canvas-context.mjs';

test('integral fill interpolates boundaries of two-point lines and preserves discontinuities',()=>{
  assert.deepEqual(integralPolygons([[-10,-7],[10,13]],[0,1]),[[[0,0],[0,3],[1,4],[1,0]]]);
  assert.deepEqual(integralPolygons([[10,13],[-10,-7]],[1,0]),[[[1,0],[1,4],[0,3],[0,0]]]);
  const polygons=integralPolygons([[-2,-2],[-1,-1],null,[1,1],[2,2]],[-2,2]);
  assert.equal(polygons.length,2);assert.equal(polygons[0].at(-1)[0],-1);assert.equal(polygons[1][0][0],1);
  assert.deepEqual(integralPolygons([[-10,-7],[10,13]],[20,21]),[]);
});
test('Canvas and SVG shade a simplified x+3 line and prefer the independently sampled integral boundary',()=>{
  const dom=new JSDOM('<div id="plot"></div>');globalThis.document=dom.window.document;installCanvas(dom);
  const container=document.getElementById('plot'),bounds={xmin:-10,xmax:10,ymin:-5,ymax:5},result={curves:[[[-10,-7],[10,13]]]};
  try{
    plotGraph(container,result,bounds,{integral:[0,1]});assert.equal(surfaceFills(container).length,1);
    const analysis={integralFill:[[[.25,0],[.25,3.25],[.5,3.5],[.75,3.75],[.75,0]]]};
    plotGraph(container,result,bounds,{integral:[.25,.75],analysis});assert.equal(surfaceFills(container)[0].path.filter(p=>p.op==='lineTo').length,4);
    plot(container,result,bounds,{integral:[0,1]});assert.equal(container.querySelectorAll('[data-integral]').length,1);
    plot(container,result,bounds,{integral:[.25,.75],analysis});assert.equal(container.querySelector('[data-integral]').getAttribute('points').split(' ').length,5);
  }finally{dom.window.close();}
});
