import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {fileURLToPath} from 'node:url';
import {JSDOM} from 'jsdom';
import {loadPyodide} from '../vendor/pyodide.mjs';
import {installEngine} from '../engine-bootstrap.js';
import {parse} from '../parser.js';
import {createGraphWorkspace,graphInputTree,cartesianFormula} from '../graph-workspace.js';
import {defineFunction} from '../function-transfer.js';
import {installCanvas} from './canvas-context.mjs';

test('named Cartesian graph inputs keep their label and normalize only function definitions',()=>{
  for(const source of ['f(x)=x+1','g(x)=sin(x)','f2(x)=2*x^2']){
    assert.deepEqual(graphInputTree(source),parse(source).args[1]);
    assert.equal(cartesianFormula(source),source);
  }
  for(const source of ['y=x+1','sin(x)=0','x^2+y^2=1'])assert.deepEqual(graphInputTree(source),parse(source));
  assert.deepEqual(graphInputTree('f(x)=x+1','implicit'),parse('f(x)=x+1'));
});

test('named functions plot and analyze from the entered bodies in the actual WASM engine',async t=>{
  const py=await loadPyodide({indexURL:fileURLToPath(new URL('../vendor/',import.meta.url))});
  await installEngine(py,{runtimeURL:new URL('../vendor/',import.meta.url),engineURL:new URL('../engine.zip',import.meta.url),fetcher:async url=>new Response(readFileSync(url))});
  const dom=new JSDOM(readFileSync(new URL('../index.html',import.meta.url),'utf8'));
  const previous=globalThis.document;globalThis.document=dom.window.document;installCanvas(dom);
  const $=id=>document.getElementById(id),requests=[],responses=[],errors=[];
  $('graph-kind').value='cartesian';$('graph-source').value='f(x)=x+1\ng(x)=x^2';
  $('graph-min').value='-2';$('graph-max').value='2';$('graph-ymin').value='-2';$('graph-ymax').value='4';
  const workspace=createGraphWorkspace({execute:async request=>{
    requests.push(request);py.globals.set('payload',JSON.stringify(request));
    const result=JSON.parse(py.runPython('calc_engine.dispatch(payload)'));responses.push(result);return result;
  },options:()=>({displayDigits:10,functions:{f:defineFunction('f',['x'],'99')},variables:{x:parse('999')}}),onError:message=>errors.push(message),persist:()=>{},isBusy:()=>false});
  t.after(()=>{workspace.dispose();dom.window.close();globalThis.document=previous;});
  await workspace.run();
  assert.deepEqual(errors,[]);
  const result=responses.at(-1);
  assert.equal(result.ok,true,result.error);
  assert.deepEqual(result.implicitCurves,[false,false]);
  assert.deepEqual(result.parameters,[]);
  for(const [i,curve] of result.curves.entries())for(const point of curve.filter(Boolean))assert.ok(Math.abs(point[1]-(i?point[0]**2:point[0]+1))<1e-10);
  assert.match($('graph-formulas').textContent,/f\(x\)/);
  $('graph-analysis-a').value='-2';$('graph-analysis-b').value='2';
  $('graph-analysis-action').value='root';await $('graph-analysis-run').onclick();
  assert.deepEqual(responses.at(-1).points,[[-1,0]]);
  $('graph-analysis-action').value='integral';await $('graph-analysis-run').onclick();
  assert.ok(Math.abs(responses.at(-1).value-4)<1e-9);
  assert.deepEqual(requests.at(-1).trees,requests[0].trees,'analysis uses the same function bodies');
  assert.deepEqual(errors,[]);
});
