import test from 'node:test';
import assert from 'node:assert/strict';
import {graphSvg,graphPng} from '../graph-export.js';

function plotContainer(){
  const canvas={width:800,height:460,style:{},dataset:{},setAttribute(){},getBoundingClientRect:()=>({width:800}),getContext:()=>({measureText:text=>({width:String(text).length*7})})};
  return {querySelector:()=>canvas,closest:()=>null,ownerDocument:{defaultView:{devicePixelRatio:1,getComputedStyle:()=>({getPropertyValue:key=>key==='--number'?'#123456':'',borderLeftWidth:'0',borderRightWidth:'0'})}}};
}

test('SVG records the actual 2D plot with gaps, clipping, shading and dashed analysis',()=>{
  const result={curves:[[[-1,-1],[0,0],null,[.5,.25],[1,1]]],shadings:[{fill:[[[-1,-1],[0,0],[0,-1]]]}]};
  const svg=graphSvg(plotContainer(),result,{xmin:-1,xmax:1,ymin:-1,ymax:1},{analysis:{line:[[-1,0],[1,0]]},trace:[0,0]});
  assert.match(svg,/<svg xmlns="http:\/\/www.w3.org\/2000\/svg" width="800" height="460"/);
  assert.match(svg,/fill="#123456"/);
  assert.match(svg,/d="M44 418 L401 230 M579.5 183 L758 42"/);
  assert.match(svg,/clip-path="url\(#plot-clip-0\)"/);
  assert.match(svg,/opacity="0.16"/);
  assert.match(svg,/stroke-dasharray="6 4"/);
  assert.match(svg,/stroke-dasharray="3 3"/);
  assert.match(svg,/<text /);
  assert.doesNotMatch(svg,/<image|NaN|Infinity/);
});

test('SVG keeps the rotated 3D surface as vector faces and axis labels',()=>{
  const result={surface:[[[-1,-1,-1],[1,-1,0]],[[-1,1,0],[1,1,1]]],zMin:-1,zMax:1};
  const svg=graphSvg(plotContainer(),result,{xmin:-1,xmax:1,ymin:-1,ymax:1},{heightScale:.5,surfaceView:{rotation:65,elevation:20,zoom:1.5,renderMode:'surface-wireframe'}});
  assert.match(svg,/height="230" viewBox="0 0 800 230"/);
  assert.match(svg,/fill="rgb\(/);
  for(const axis of ['x','y','z'])assert.match(svg,new RegExp(`>${axis}</text>`));
  assert.doesNotMatch(svg,/<image|NaN|Infinity/);
});

test('PNG encoding returns the image blob and reports encoding failure',async()=>{
  const blob=new Blob(['png'],{type:'image/png'});
  assert.equal(await graphPng({toBlob(callback,type){assert.equal(type,'image/png');callback(blob);}}),blob);
  await assert.rejects(graphPng({toBlob(callback){callback(null);}}),/Could not export/);
});
