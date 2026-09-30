import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {fileURLToPath} from 'node:url';
import {loadPyodide} from '../vendor/pyodide.mjs';
import {parse} from '../parser.js';

test('actual CPython WASM reuses the Android engine across workspaces',async()=>{
  const py=await loadPyodide({indexURL:fileURLToPath(new URL('../vendor/',import.meta.url))});
  await py.loadPackage('sympy');
  py.unpackArchive(new Uint8Array(readFileSync(new URL('../engine.zip',import.meta.url))),'zip',{extractDir:'/calcmax'});
  py.runPython("import sys\nsys.path.insert(0,'/calcmax')\nimport calc_engine, script_runner");
  function run(request) {
    py.globals.set('payload',JSON.stringify({angle:'RAD',...request}));
    return JSON.parse(py.runPython('calc_engine.dispatch(payload)'));
  }
  function evaluate(source,options={}) { const result=run({tree:parse(source),...options}); assert.equal(result.ok,true,`${source}: ${result.error}`); return result; }
  assert.equal(evaluate('1/3+1/6').exact,'1/2');
  assert.equal(evaluate('0.1+0.2').exact,'3/10');
  assert.equal(evaluate('-2^2').exact,'-4');
  assert.equal(evaluate('sin(30)',{angle:'DEG'}).exact,'1/2');
  assert.equal(evaluate('sin(pi/6)',{angle:'DEG'}).exact,'1/2');
  assert.equal(evaluate('det([[1,2],[3,4]])').exact,'-2');
  assert.equal(evaluate('dot([1,2,3],[4,5,6])').exact,'32');
  assert.equal(evaluate('convert(32,degF,degC)').exact,'0');
  assert.equal(evaluate('integrate(x^2,x,0,1)').exact,'1/3');
  assert.match(evaluate('factor(x^4-1)').exact,/x/);
  assert.match(evaluate('solve(x^2-5x+6=0,x)').exact,/2.*3/);
  assert.match(evaluate('stats([1,2,3,4])').exact,/mean/i);
  assert.equal(evaluate('f(3)',{functions:{f:{parameters:['x'],body:parse('x^2+1')}}}).exact,'10');
  const previous=evaluate('1/7');
  assert.equal(evaluate('Ans*7',{variables:{Ans:previous.resultAst}}).exact,'1');
  assert.equal(run({tree:parse('1/0')}).ok,false);
  assert.equal(run({action:'programmer',width:8,base:16,a:'FF',op:'>>',b:'1',signed:true}).bases.HEX,'FF');
  assert.ok(run({action:'constants'}).constants.length>10);
  for(const [graphKind,source,options] of [
    ['cartesian','1/x',{}],['parametric','(cos(t),sin(t))',{variable:'t'}],
    ['polar','1+cos(t)',{variable:'t'}],['sequence','u(n-1)+1',{min:0,max:10,initialTrees:[parse('1')]}],
    ['surface','sin(x)*cos(y)',{surfaceYMin:-3,surfaceYMax:3}],
    ['differential','y',{min:0,max:3,initialValues:[1]}]
  ]) {
    const result=run({action:'graph',graphKind,trees:[parse(source)],min:-3,max:3,...options});
    assert.equal(result.ok,true,`${graphKind}: ${result.error}`);
    assert.ok(result.curves?.[0].length>5 || result.surface?.length>10);
  }
  const discontinuity=run({action:'graph',trees:[parse('1/x')],min:-1,max:1});
  assert.ok(discontinuity.curves[0].some(p=>p===null));
  py.globals.set('payload',JSON.stringify({source:'import calcmax_catalog as calc\nprint(calc.mean([1,2,3]))'}));
  assert.equal(JSON.parse(py.runPython('script_runner.run(payload)')).output,'2\n');
  // This loads the same source archive that the static browser Worker consumes.
  console.log(`WASM engine passed: Python ${py.runPython('sys.version.split()[0]')}, SymPy ${py.runPython('calc_engine.s.__version__')}`);
});
