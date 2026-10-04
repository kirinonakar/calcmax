import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {fileURLToPath} from 'node:url';
import {loadPyodide} from '../vendor/pyodide.mjs';
import {installEngine} from '../engine-bootstrap.js';
import {parse,latexInput} from '../parser.js';
import {equationCommand} from '../workspace-commands.js';
import {JSDOM} from 'jsdom';
import {resultMathDisplay} from '../result-display.js';

async function engine() {
  const py=await loadPyodide({indexURL:fileURLToPath(new URL('../vendor/',import.meta.url))});
  await installEngine(py,{runtimeURL:new URL('../vendor/',import.meta.url),engineURL:new URL('../engine.zip',import.meta.url),fetcher:async url=>new Response(readFileSync(url))});
  const run=request=>{
    py.globals.set('payload',JSON.stringify({angle:'RAD',...request}));
    return JSON.parse(py.runPython('calc_engine.dispatch(payload)'));
  };
  run.checkRoots=(result,degree)=>{
    py.globals.set('root_text',result.decimal);
    py.globals.set('root_degree',degree);
    assert.equal(py.runPython("all(abs(calc_engine.s.N(root**root_degree-root+1,30)) < calc_engine.s.Rational(1,10)**25 for root in calc_engine.s.sympify(root_text))"),true);
  };
  return run;
}

for(const degree of [4,5,6])test(`fresh WASM solves x^${degree}-x+1=0 and produces every decimal root`,async t=>{
  const dom=new JSDOM(''),previous=globalThis.document;
  globalThis.document=dom.window.document;
  t.after(()=>{globalThis.document=previous;dom.window.close();});
  const run=await engine(),tree=parse(equationCommand({source:`x^${degree}-x+1=0`,variable:'x'}));
  for(let attempt=0;attempt<2;attempt++) {
    const started=performance.now(),result=run({tree,precision:30,budget:8});
    assert.equal(result.ok,true,result.error);
    assert.equal(result.tree.kind,'set');
    assert.equal(result.tree.args.length,degree);
    assert.equal(result.decimalTree.args.length,degree);
    if(degree>4) {
      assert.equal(result.approximate,true);
      assert.ok(!result.exact.includes('CRootOf'));
      assert.ok(!JSON.stringify(result.tree).includes('CRootOf'));
      const display=resultMathDisplay(result.tree,10,result.approximate);
      assert.ok(!display.textContent.includes('CRootOf'));
      assert.ok(display.querySelectorAll('mn').length>=degree);
      assert.ok(display.classList.contains('result-flow'));
      assert.equal(display.querySelectorAll('.result-part').length,degree===5?9:12);
      if(degree===5)assert.ok(display.textContent.includes('1.1673039783'));
    }
    run.checkRoots(result,degree);
    assert.equal(result.note,'');
    console.log(`WASM degree ${degree} ${attempt?'warm':'cold'}: ${(performance.now()-started).toFixed(0)} ms`);
  }
});

test('actual WASM evaluates extended LaTeX with scoped index and integration variables',async()=>{
  const run=await engine();
  const cases=readFileSync(new URL('../../math/src/test/resources/latex-input.tsv',import.meta.url),'utf8').trim().split(/\r?\n/).map(line=>line.split('\t'));
  for(const [source,,exact] of cases) {
    const result=run({tree:parse(latexInput(source)),precision:30});
    assert.equal(result.ok,true,`${source}: ${result.error}`);
    if(exact.startsWith('Matrix('))assert.equal(result.exact.replace(/\s/g,''),exact.replace(/\s/g,''),source);
    else assert.equal(result.exact,exact,source);
  }
  for(const source of [String.raw`\sum_{k=1}^{5} k^2`,String.raw`\prod_{k=1}^{4} k`,String.raw`\int_0^1 x^2 dx`]) {
    const result=run({tree:parse(latexInput(source)),variables:{k:parse('99'),x:parse('99')}});
    assert.equal(result.ok,true,result.error);
    assert.equal(result.exact,source.includes('sum')?'55':source.includes('prod')?'24':'1/3');
  }
  for(const [source,angle,exact] of [[String.raw`\sec 60`,'DEG','2'],[String.raw`\csc 100`,'GRAD','1'],[String.raw`\cot 45`,'DEG','1'],[String.raw`\sec\frac{\pi}{3}`,'DEG','2']]) {
    const result=run({tree:parse(latexInput(source)),angle});
    assert.equal(result.ok,true,result.error);assert.equal(result.exact,exact);
  }
  const symbolic=run({tree:parse(latexInput(String.raw`\sec x`))});
  const reused=run({tree:parse('subs(Ans,x,pi/3)'),angle:'DEG',variables:{Ans:symbolic.resultAst}});
  assert.equal(reused.ok,true,reused.error);assert.equal(reused.exact,'2');
});
