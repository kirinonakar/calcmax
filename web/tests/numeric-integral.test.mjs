import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {fileURLToPath} from 'node:url';
import {loadPyodide} from '../vendor/pyodide.mjs';
import {installEngine} from '../engine-bootstrap.js';
import {parse} from '../parser.js';

test('WASM graph integrals return exact cancellation without losing small nonzero results',async()=>{
  const py=await loadPyodide({indexURL:fileURLToPath(new URL('../vendor/',import.meta.url))});
  await installEngine(py,{runtimeURL:new URL('../vendor/',import.meta.url),engineURL:new URL('../engine.zip',import.meta.url),fetcher:async url=>new Response(readFileSync(url))});
  function run(request){
    py.globals.set('payload',JSON.stringify({angle:'RAD',...request}));
    const result=JSON.parse(py.runPython('calc_engine.dispatch(payload)'));
    assert.equal(result.ok,true,result.error);
    return result;
  }
  const formula='3*x^2-16*x-20';
  for(const precision of [15,50,100]){
    const result=run({action:'graphAnalysis',trees:[parse(formula)],analysis:'integral',a:-2,b:0,precision});
    assert.equal(result.value,0);
    assert.ok(result.integralFill.flat().some(([,y])=>y>0));
    assert.ok(result.integralFill.flat().some(([,y])=>y<0));
    assert.equal(Number(run({tree:parse(`nintegrate(${formula},x,-2,0)`),precision}).decimal),0);
  }
  assert.equal(run({action:'graphAnalysis',trees:[parse(`${formula}+1e-80`)],analysis:'integral',a:-2,b:0,precision:100}).value,2e-80);
  assert.equal(run({action:'graphAnalysis',trees:[parse(`[x,${formula}]`)],graphKind:'parametric',variable:'x',analysis:'integral',a:-2,b:0}).value,0);
  assert.equal(run({action:'graphAnalysis',trees:[parse('0')],graphKind:'polar',analysis:'integral',a:-2,b:0}).value,0);
  assert.equal(run({action:'graphAnalysis',trees:[parse('[1,2]')],graphKind:'parametric',analysis:'arclength',a:-2,b:0}).value,0);
  assert.ok(Math.abs(Number(run({tree:parse('nintegrate(sin(x),x,0,pi)')}).decimal)-2)<1e-12);
});
