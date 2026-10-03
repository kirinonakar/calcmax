import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {JSDOM} from 'jsdom';
import {createGraphWorkspace,removeGraphSource} from '../graph-workspace.js';
import {installCanvas} from './canvas-context.mjs';

function setup(t,source,kind='cartesian',execute){
  const dom=new JSDOM(readFileSync(new URL('../index.html',import.meta.url),'utf8'));
  globalThis.document=dom.window.document;installCanvas(dom);
  const byId=id=>document.getElementById(id),requests=[],errors=[];let saves=0;
  byId('graph-kind').value=kind;byId('graph-source').value=source;
  const workspace=createGraphWorkspace({
    execute:async request=>{requests.push(request);return execute?execute(request):{ok:true,curves:request.trees.map((_,i)=>[[0,i],[1,i+1]]),parameters:[]};},
    options:()=>({displayDigits:10}),onError:error=>errors.push(error),persist:()=>saves++,isBusy:()=>false,
  });
  t.after(()=>{workspace.dispose();dom.window.close();});
  const remove=index=>byId('graph-formulas').children[index].querySelector('.graph-formula-remove').click();
  return {byId,workspace,requests,errors,remove,saves:()=>saves};
}

test('removal targets the displayed occurrence, preserving blank lines and shading',()=>{
  const source='\n x \n[shade] x, 0\n\nx\nx+1';
  assert.equal(removeGraphSource(source,1),'\n x \n[shade] x, 0\n\nx+1');
  assert.equal(removeGraphSource(source,0,'cartesian',true),'\n x \n\nx\nx+1');
  assert.equal(removeGraphSource(source,8),source);
});

test('delete buttons remove only their curve, preserve the selected curve, and persist',async t=>{
  const {byId,workspace,remove,saves}=setup(t,'x\n[shade] x, 0\nx+1\nx+2');
  await workspace.run();
  byId('graph-formulas').children[2].querySelector('.graph-formula-select').click();
  assert.equal(byId('graph-selected').value,'2');
  const before=saves();remove(0);
  assert.equal(byId('graph-source').value,'[shade] x, 0\nx+1\nx+2');
  assert.equal(byId('graph-selected').value,'1','the same remaining curve stays selected');
  assert.notEqual(byId('graph-other').value,byId('graph-selected').value);
  assert.equal(byId('graph-plot').children.length,0,'old curves disappear immediately');
  assert.ok(saves()>before);
  assert.equal(workspace.snapshot().sources.cartesian,byId('graph-source').value);
  await workspace.run();
  remove(2);
  assert.equal(byId('graph-source').value,'x+1\nx+2','shading has its own delete button');
});

test('deleting a derivative disables it without deleting the source',async t=>{
  const {byId,workspace,remove}=setup(t,'x^2');
  await workspace.run();
  byId('graph-derivative').checked=true;byId('graph-derivative').onchange();
  assert.equal(byId('graph-formulas').children.length,2);
  remove(1);
  assert.equal(byId('graph-derivative').checked,false);
  assert.equal(byId('graph-source').value,'x^2');
  assert.equal(byId('graph-formulas').children.length,1);
});

for(const [kind,source] of [['cartesian','x'],['parametric','[cos(t),sin(t)]'],['polar','cos(t)'],['sequence','n'],['surface','x+y'],['differential','y-t']]){
  test(`deleting the last ${kind} graph clears the plot without an empty-input error`,async t=>{
    const {byId,workspace,remove,requests,errors}=setup(t,source,kind);
    await workspace.run();remove(0);await workspace.run();
    assert.equal(byId('graph-source').value,'');
    for(const id of ['graph-plot','graph-formulas','graph-table','graph-trace','graph-analysis-result'])assert.equal(byId(id).children.length,0,id);
    assert.equal(byId('graph-status').textContent,'');
    assert.equal(requests.length,1,'empty lists do not request computation');
    assert.deepEqual(errors,[]);
  });
}

test('an in-flight plot cannot restore a deleted graph',async t=>{
  let resolve;
  const {workspace,byId,remove}=setup(t,'x','cartesian',()=>new Promise(done=>resolve=done));
  const pending=workspace.run();remove(0);
  resolve({ok:true,curves:[[[0,0],[1,1]]],parameters:[]});
  await pending;
  assert.equal(byId('graph-plot').children.length,0);
  assert.equal(byId('graph-formulas').children.length,0);
});
