import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {JSDOM} from 'jsdom';
import {createGraphWorkspace} from '../graph-workspace.js';
import {installCanvas} from './canvas-context.mjs';

function setup(t,saved={}){
  const dom=new JSDOM(readFileSync(new URL('../index.html',import.meta.url),'utf8'));
  globalThis.document=dom.window.document;installCanvas(dom);
  const byId=id=>document.getElementById(id),requests=[];
  byId('graph-source').value='x';
  const workspace=createGraphWorkspace({
    execute:async request=>{requests.push(request);return {ok:true,curves:[[[-100,-100],[100,100]]],parameters:[]};},
    options:()=>({displayDigits:10}),onError:assert.fail,persist:()=>{},isBusy:()=>false,saved,
  });
  const edit=(id,number)=>{byId(id).value=String(number);byId(id).dispatchEvent(new dom.window.Event('input'));byId(id).dispatchEvent(new dom.window.Event('change'));};
  const expectDomain=(low,high)=>{
    for(const id of ['graph-analysis-a-slider','graph-analysis-b-slider','graph-tangent-slider']){
      assert.equal(Number(byId(id).min),low,id);assert.equal(Number(byId(id).max),high,id);
    }
  };
  t.after(()=>{workspace.dispose();dom.window.close();});
  return {dom,byId,workspace,requests,edit,expectDomain};
}

test('analysis and tangent domains follow pan, zoom, manual ranges, and Reset',async t=>{
  const {byId,workspace,edit,expectDomain}=setup(t);
  await workspace.run();expectDomain(-10,10);
  edit('graph-analysis-a',-2);edit('graph-analysis-b',3);
  byId('graph-right').click();expectDomain(-7,13);
  byId('graph-zoom-in').click();expectDomain(-2,8);
  assert.equal(workspace.snapshot().ranges['graph-analysis-a'],-2);
  assert.equal(workspace.snapshot().ranges['graph-analysis-b'],3);
  edit('graph-min',40);edit('graph-max',60);expectDomain(40,60);
  assert.equal(Number(byId('graph-analysis-a-slider').value),40,'offscreen values clamp visually without changing the field');
  assert.equal(workspace.snapshot().ranges['graph-analysis-a'],-2);
  byId('graph-analysis-visible-range').click();
  assert.equal(workspace.snapshot().ranges['graph-analysis-a'],40);
  assert.equal(workspace.snapshot().ranges['graph-analysis-b'],60);
  byId('graph-analysis-action').value='tangent';byId('graph-analysis-action').onchange();
  edit('graph-analysis-a',55);assert.equal(Number(byId('graph-tangent-slider').value),55);
  byId('graph-reset').click();expectDomain(-10,10);
  assert.equal(Number(byId('graph-tangent-slider').value),10);
});

test('drag updates analysis domains before pointer release and wheel keeps them linked',async t=>{
  const {dom,byId,workspace,expectDomain}=setup(t);
  await workspace.run();const plot=byId('graph-plot');
  plot.getBoundingClientRect=()=>({left:0,top:0,width:800,height:460});
  const pointer=(type,x)=>plot.dispatchEvent(new dom.window.MouseEvent(type,{clientX:x,clientY:200,button:0,bubbles:true}));
  pointer('pointerdown',400);pointer('pointermove',480);
  const ranges=workspace.snapshot().ranges;expectDomain(ranges['graph-min'],ranges['graph-max']);
  assert.ok(ranges['graph-min']<-10);
  pointer('pointerup',480);
  plot.dispatchEvent(new dom.window.WheelEvent('wheel',{clientX:400,clientY:200,deltaY:-100,bubbles:true}));
  const zoomed=workspace.snapshot().ranges;expectDomain(zoomed['graph-min'],zoomed['graph-max']);
  assert.ok(zoomed['graph-max']-zoomed['graph-min']<20);
});

for(const kind of ['parametric','polar'])test(`${kind} analysis uses t limits independently of viewport x`,async t=>{
  const {byId,workspace,edit,expectDomain}=setup(t);
  byId('graph-kind').value=kind;byId('graph-kind').onchange();await workspace.run();
  expectDomain(0,2*Math.PI);
  byId('graph-right').click();byId('graph-zoom-in').click();expectDomain(0,2*Math.PI);
  edit('graph-min',1);edit('graph-max',4);expectDomain(1,4);
  byId('graph-min-slider').value='2';byId('graph-min-slider').oninput();expectDomain(2,4);
});

test('restored offscreen analysis fields do not widen the visible domain',t=>{
  const {workspace,byId,expectDomain}=setup(t,{ranges:{'graph-min':20,'graph-max':30,'graph-analysis-a':-5,'graph-analysis-b':50}});
  expectDomain(20,30);
  assert.equal(workspace.snapshot().ranges['graph-analysis-a'],-5);
  assert.equal(Number(byId('graph-analysis-a-slider').value),20);
  assert.equal(Number(byId('graph-analysis-b-slider').value),30);
  byId('graph-analysis-a-slider').value='25';byId('graph-analysis-a-slider').oninput();
  expectDomain(20,30);assert.equal(workspace.snapshot().ranges['graph-analysis-a'],25);
});

test('analysis inputs precede the slider in a separate row with no duplicate numbers',t=>{
  const {byId}=setup(t),section=byId('graph-analysis'),row=byId('graph-analysis-sliders');
  assert.equal(section.querySelector('summary').nextElementSibling,row);
  assert.equal(row.parentElement,section);
  const track=byId('graph-analysis-a-slider').parentElement;
  assert.equal(track.closest('#graph-analysis-sliders'),row);
  assert.equal(byId('graph-analysis-b-slider').parentElement,track);
  assert.ok(row.contains(byId('graph-tangent-slider')));
  assert.equal(row.querySelector('select,button,output'),null);
  for(const id of ['graph-analysis-a','graph-analysis-b']){
    assert.ok(row.contains(byId(id)));
    assert.ok(byId(id).compareDocumentPosition(track)&4,`${id} precedes the slider`);
  }
  for(const id of ['graph-min','graph-max','graph-ymin','graph-ymax','graph-xmin','graph-xmax','graph-zmin','graph-zmax','graph-analysis-a','graph-analysis-b'])assert.equal(byId(id+'-value'),null);
  byId('graph-analysis-a-slider').value='-4';byId('graph-analysis-a-slider').oninput();
  byId('graph-analysis-b-slider').value='6';byId('graph-analysis-b-slider').oninput();
  assert.equal(byId('graph-analysis-a').value,'-4');assert.equal(byId('graph-analysis-b').value,'6');
  for(const id of ['graph-analysis-action','graph-other','graph-analysis-visible-range','graph-analysis-run','graph-analysis-clear']){
    assert.ok(row.compareDocumentPosition(byId(id))&4,`${id} follows the slider row`);
    assert.equal(row.contains(byId(id)),false);
  }
  for(const action of ['root','derivative','tangent']){
    byId('graph-analysis-action').value=action;byId('graph-analysis-action').onchange();
    assert.equal(byId('graph-analysis-a-slider').parentElement.hidden,action==='tangent');
    assert.equal(byId('graph-tangent-position').hidden,action!=='tangent');
  }
});

for(const width of [360,1064])test(`analysis tracks share the plotted x positions at ${width}px with changing y labels`,async t=>{
  const {dom,byId,workspace,edit}=setup(t),plot=byId('graph-plot');
  plot.style.border='1px solid black';
  dom.window.HTMLCanvasElement.prototype.getBoundingClientRect=()=>({width:width-2});
  await workspace.run();
  const css=readFileSync(new URL('../calculator.css',import.meta.url),'utf8');
  assert.match(css,/#graph-analysis-sliders\{width:100%;min-width:0;padding-left:var\(--graph-axis-left,5.25%\);padding-right:var\(--graph-axis-right,5.25%\)\}/);
  assert.match(css,/\.graph-axis-slider\{margin-left:-10px;margin-right:-10px\}/);
  for(const thumb of ['webkit-slider-thumb','moz-range-thumb'])assert.match(css,new RegExp(`::-${thumb}\\{[^}]*box-sizing:border-box;[^}]*width:20px`));
  assert.ok(byId('graph-analysis-a-slider').closest('.graph-axis-slider'));
  assert.ok(byId('graph-tangent-slider').closest('.graph-axis-slider'));
  const workspaceElement=plot.closest('[data-mode="graph"]');
  const inset=name=>{
    const match=workspaceElement.style.getPropertyValue(`--graph-axis-${name}`).match(/calc\(([-\d.]+)% \+ ([-\d.]+)px\)/);
    assert.ok(match);return width*Number(match[1])/100+Number(match[2]);
  };
  const verify=()=>{
    const canvas=plot.querySelector('canvas'),left=inset('left'),right=inset('right');
    for(const fraction of [0,.2,.5,.8,1]){
      const thumbX=left+fraction*(width-left-right);
      const graphX=1+(Number(canvas.dataset.plotLeft)+fraction*Number(canvas.dataset.plotWidth))*(width-2)/800;
      assert.ok(Math.abs(thumbX-graphX)<1e-9,`${thumbX} should equal ${graphX}`);
    }
    return left;
  };
  const original=verify();edit('graph-ymax',100000000);await workspace.run();
  assert.ok(verify()>original,'longer y labels increase the axis and slider left inset together');
});
