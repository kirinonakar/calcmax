import test from 'node:test';
import assert from 'node:assert/strict';
import {semDiagramSvg,semDiagramSize} from '../statistics-sem-diagram.js';

// Exercise the SVG construction contract; this does not establish browser QA.
class SvgNode {
  constructor(tag){this.tag=tag;this.attrs={};this.children=[];this.style={};this.textContent='';}
  setAttribute(key,value){this.attrs[key]=value;}
  append(node){this.children.push(node);}
}
test('SEM SVG shows standardized intervals, latent R² and covariance direction',t=>{
  const previous=globalThis.document;t.after(()=>globalThis.document=previous);
  globalThis.document={createElementNS:(_,tag)=>new SvgNode(tag)};
  const plot={title:'Structural equation diagram',width:820,height:700,nodes:[
    {kind:'latent',label:'Factor 1',x:250,y:150,r2:null},
    {kind:'latent',label:'Factor 2',x:250,y:550,r2:.36},
    {kind:'observed',label:'Item <A>',x:680,y:150,r2:.64}
  ],edges:[
    {kind:'loading',source:'f1',target:'x1',start:[320,150],end:[610,150],controls:[[445,150],[485,150]],labelPosition:[465,150],estimate:.8,interval:[.6,1.01]},
    {kind:'covariance',source:'f1',target:'f2',start:[180,150],end:[180,550],controls:[[20,150],[20,550]],labelPosition:[60,350],estimate:-.3,interval:null}
  ]};
  const svg=semDiagramSvg(plot),text=svg.children.filter(n=>n.tag==='text').map(n=>n.textContent);
  assert.equal(svg.attrs.role,'img');assert.equal(svg.attrs.viewBox,'0 0 820 700');
  assert.ok(text.includes('[0.6, 1.01]')); // Wald bounds are not clipped to ±1.
  assert.ok(text.some(value=>value.includes('0.36')&&value.includes('R²')));
  assert.ok(text.some(value=>value.includes('0.64')&&value.includes('R²')));
  assert.ok(text.includes('Item <A>'));
  assert.equal(svg.children.filter(n=>n.tag==='ellipse').length,2);
  assert.equal(svg.children.filter(n=>n.tag==='polygon').length,3);
  assert.equal(svg.children.filter(n=>n.tag==='path'&&n.attrs['stroke-dasharray']).length,1);
  const path=svg.children.find(n=>n.tag==='path');
  assert.ok(path.children[0].textContent.includes('0.8 [0.6, 1.01]'));
});

test('SEM fit respects both viewport dimensions and original size restores its geometry',()=>{
  const plot={width:820,height:1200};
  for(const [viewportWidth,viewportHeight] of [[320,480],[1000,360],[1400,1800]]){
    const size=semDiagramSize(plot,{fitToScreen:true,viewportWidth,viewportHeight});
    assert.ok(size.width<=viewportWidth&&size.height<=viewportHeight);
    assert.equal(size.width/size.height,plot.width/plot.height);
    assert.ok(size.width<=plot.width);
  }
  assert.deepEqual(semDiagramSize(plot,{fitToScreen:false,viewportWidth:320,viewportHeight:480}),plot);
});
