import test from 'node:test';
import assert from 'node:assert/strict';
import {standalonePlotSvg,pngPlotSize,plotSvgPng} from '../svg-export.js';

// A small SVG DOM for testing serialization contracts without a browser.
class SvgNode {
  constructor(tag,document,attributes={},paint={}){this.tag=tag;this.ownerDocument=document;this.attributes={...attributes};this.paint=paint;this.children=[];this.textContent='';}
  setAttribute(name,value){this.attributes[name]=String(value);}
  getAttribute(name){return this.attributes[name]||null;}
  removeAttribute(name){delete this.attributes[name];}
  append(...children){this.children.push(...children);}
  prepend(child){this.children.unshift(child);}
  querySelectorAll(){return this.children.flatMap(child=>[child,...child.querySelectorAll()]);}
  cloneNode(){const copy=new SvgNode(this.tag,this.ownerDocument,this.attributes,this.paint);copy.textContent=this.textContent;copy.children=this.children.map(child=>child.cloneNode());return copy;}
}
function svgDocument(){
  const escape=value=>String(value).replaceAll('&','&amp;').replaceAll('<','&lt;').replaceAll('"','&quot;');
  const serialize=node=>`<${node.tag}${Object.entries(node.attributes).map(([key,value])=>` ${key}="${escape(value)}"`).join('')}>${escape(node.textContent)}${node.children.map(serialize).join('')}</${node.tag}>`;
  const document={defaultView:{getComputedStyle:node=>({getPropertyValue:property=>node.paint[property]||''}),XMLSerializer:class{serializeToString(node){return serialize(node);}}}};
  document.createElementNS=(_,tag)=>new SvgNode(tag,document);
  return document;
}

test('exported statistical SVG resolves theme paints and retains clipping, rotation and color scale',()=>{
  const document=svgDocument(),svg=new SvgNode('svg',document,{viewBox:'0 0 800 400',style:'width:320px'},{'--number':'#fefefe','--muted':'#555'});
  const group=new SvgNode('g',document,{'clip-path':'url(#cells)'});
  group.append(new SvgNode('rect',document,{fill:'var(--accent)',width:30,height:20},{fill:'rgb(0, 110, 80)'}));
  const text=new SvgNode('text',document,{fill:'currentColor',transform:'translate(14 162) rotate(-90)'},{fill:'rgb(1, 2, 3)','font-size':'12px'});text.textContent='Sensitivity';
  svg.append(group,text);
  const snapshot=standalonePlotSvg(svg,{captions:[{text:'Group A (n=10)',color:'#b12345'}],scale:{low:'-1',high:'1'}});
  assert.equal(snapshot.width,800);assert.equal(snapshot.height,472);
  assert.match(snapshot.text,/viewBox="0 0 800 472"/);
  assert.match(snapshot.text,/fill="rgb\(0, 110, 80\)"/);
  assert.match(snapshot.text,/clip-path="url\(#cells\)"/);
  assert.match(snapshot.text,/transform="translate\(14 162\) rotate\(-90\)"/);
  assert.match(snapshot.text,/Group A \(n=10\)/);
  assert.match(snapshot.text,/linearGradient/);
  assert.doesNotMatch(snapshot.text,/var\(|currentColor|width:320px/);
  assert.equal(svg.getAttribute('style'),'width:320px');
});

test('full heat map PNG size retains aspect ratio within bounded bitmap dimensions and area',()=>{
  assert.deepEqual(pngPlotSize(800,460),[1600,920]);
  const [width,height]=pngPlotSize(8000,150000);
  assert.ok(width<=16384&&height<=16384);
  assert.ok(width*height<=16_020_000);
  assert.ok(Math.abs(width/height-8000/150000)<.001);
});

test('SVG-to-PNG encodes its snapshot and releases the temporary URL on success and decode failure',async()=>{
  const revoked=[],drawn=[],blob=new Blob(['PNG'],{type:'image/png'});
  const canvas={getContext:()=>({drawImage:(_,x,y,w,h)=>drawn.push([x,y,w,h])}),toBlob:callback=>callback(blob)};
  const document={createElement:()=>canvas,defaultView:{URL:{createObjectURL:()=> 'blob:plot',revokeObjectURL:url=>revoked.push(url)},Image:class{set src(_){this.onload();}}}};
  assert.equal(await plotSvgPng({text:'<svg/>',width:800,height:400},document),blob);
  assert.deepEqual(drawn,[[0,0,1600,800]]);assert.deepEqual(revoked,['blob:plot']);
  document.defaultView.Image=class{set src(_){this.onerror();}};
  await assert.rejects(plotSvgPng({text:'bad',width:800,height:400},document),/Could not export/);
  assert.deepEqual(revoked,['blob:plot','blob:plot']);
});
