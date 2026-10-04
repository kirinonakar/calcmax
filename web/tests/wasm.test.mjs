import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {fileURLToPath} from 'node:url';
import {loadPyodide} from '../vendor/pyodide.mjs';
import {installEngine} from '../engine-bootstrap.js';
import {parse,latexInput} from '../parser.js';
import {tipCommand,moneyResult} from '../money.js';
import {statisticsCommand,distributionCommand,equationCommand} from '../workspace-commands.js';
import {JSDOM} from 'jsdom';
import {resultMathDisplay} from '../result-display.js';

// Reuse the interpreter for sequential integration scenarios. The cold solver
// scenario below explicitly loads its own interpreter to keep startup coverage.
let sharedRuntime;
async function loadRuntime(){
  const py=await loadPyodide({indexURL:fileURLToPath(new URL('../vendor/',import.meta.url))});
  await installEngine(py,{runtimeURL:new URL('../vendor/',import.meta.url),engineURL:new URL('../engine.zip',import.meta.url),fetcher:async url=>new Response(readFileSync(url))});
  return py;
}
function runtime(){return sharedRuntime??=loadRuntime();}

test('actual WASM evaluates pasted LaTeX limits in radians with scoped variables',async()=>{
  const py=await runtime();
  for(const [source,exact] of [
    [String.raw`$$\int_{0}^{1} \left( \frac{x}{x} \right) dx$$`,'1'],
    [String.raw`$$\lim_{x \to 0} \frac{3x^2}{\sin^2 x}$$`,'3'],
    [String.raw`\lim_{x \to 0^+} 1/x`,'oo'],
    [String.raw`\lim_{x \to 0^{-}} 1/x`,'-oo'],
    [String.raw`\lim_{x \to \infty} \frac{1}{x}`,'0']
  ])for(const angle of ['RAD','DEG','GRAD']){
    py.globals.set('payload',JSON.stringify({tree:parse(latexInput(source)),angle,variables:{x:parse('99')}}));
    const result=JSON.parse(py.runPython('calc_engine.dispatch(payload)'));
    assert.equal(result.ok,true,result.error);
    assert.equal(result.exact,exact,`${source} in ${angle}`);
  }
});

test('actual WASM solves the pasted integral equation using a stored function',async()=>{
  const py=await runtime();
  const source=String.raw`$$\int _{-2}^{a} f(x) dx = \int _{-2}^{0} f(x) dx$$`;
  const tree=parse(latexInput(equationCommand({source,variable:'a'})));
  const functions={f:{parameters:['x'],body:parse('3x^2-16x-20')}};
  // Stored values must not replace the solver variable or the integration variable.
  for(const variables of [{},{x:parse('99'),a:parse('7')}]) {
    py.globals.set('payload',JSON.stringify({tree,functions,variables,angle:'RAD'}));
    const result=JSON.parse(py.runPython('calc_engine.dispatch(payload)'));
    assert.equal(result.ok,true,result.error);
    assert.equal(result.exact,'{-2, 0, 10}');
  }
});

test('actual CPython WASM reuses the Android engine across workspaces',async()=>{
  const py=await runtime();
  function run(request) {
    py.globals.set('payload',JSON.stringify({angle:'RAD',...request}));
    return JSON.parse(py.runPython('calc_engine.dispatch(payload)'));
  }
  function evaluate(source,options={}) { const result=run({tree:parse(source),...options}); assert.equal(result.ok,true,`${source}: ${result.error}`); return result; }
  for(const value of [1,2]){
    const result=run({action:'graph',trees:[parse('a*sin(x)')],min:-10,max:10,yMin:-5,yMax:5,parameters:{a:value}});
    assert.equal(result.ok,true,result.error);
    for(const point of result.curves[0])if(point)assert.ok(Math.abs(point[1]-value*Math.sin(point[0]))<1e-10);
  }
  assert.equal(evaluate('1/3+1/6').exact,'1/2');
  assert.equal(evaluate(latexInput(String.raw`$$\sqrt[3]{5} \times 25^{\frac{1}{3}}$$`)).exact,'5');
  assert.equal(evaluate(latexInput(String.raw`\frac{1}{2}^2`)).exact,'1/4');
  assert.equal(evaluate(latexInput(String.raw`\sqrt[3]{-8}`)).exact,'2*(-1)**(1/3)');
  const thetaEquation=latexInput(String.raw`$$\cos\left(\frac{\pi}{2} + \theta\right) = -\frac{1}{5}$$`);
  assert.match(evaluate(thetaEquation).exact,/theta/);
  assert.equal(evaluate(latexInput(String.raw`\theta`),{variables:{theta:parse('3')}}).exact,'3');
  const logEquation=latexInput(String.raw`$$a = 2 \log \frac{1}{\sqrt{10}} + \log_2 20 $$`);
  assert.match(evaluate(logEquation).exact,/a/);
  const logResult=run({tree:parse(logEquation).args[1]});
  assert.equal(logResult.ok,true,logResult.error);
  assert.ok(Math.abs(Number(logResult.decimal)-Math.log2(10))<1e-10);
  assert.equal(evaluate(latexInput(String.raw`\log_2 8`)).exact,'3');
  assert.equal(evaluate(latexInput(String.raw`\log 100`)).exact,'2');
  const shiftedLogEquation=latexInput(String.raw`$$\log_{2}(x-3) = \log_{4}(3x-5)$$`);
  for(const assumptions of [{},{x:['real']},{x:['positive']},{x:['integer']}]) {
    const result=evaluate(`solve(${shiftedLogEquation},x)`,{assumptions,variables:{x:parse('99')}});
    assert.equal(result.exact,'{7}');
    assert.equal(result.note,'');
    assert.equal(result.tree.kind,'set');
    assert.equal(evaluate('Ans',{variables:{Ans:result.resultAst}}).exact,'{7}');
  }
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
  assert.match(evaluate(statisticsCommand('A,1\nA,2\nA,3\nB,2\nB,4\nB,6',{op:'ztest2',grouping:'groups',sigma:'1',sigmaY:'2',tail:'left'})).exact,/p value/);
  assert.match(evaluate(statisticsCommand('A,yes\nA,no\nB,yes\nB,no',{op:'chi2independence'})).exact,/chi-square/);
  const chiData=[[20,10],[15,25]].flatMap((row,i)=>row.flatMap((count,j)=>Array(count).fill(`${i},${j}`))).join('\n');
  for(const [yatesCorrection,statistic] of [[true,'189/40'],[false,'35/6']]){
    const result=evaluate(statisticsCommand(chiData,{op:'chi2independence',yatesCorrection}));
    const fields=Object.fromEntries(result.exact.split('\n').map(line=>line.split(': ')));
    assert.equal(fields['chi-square'],statistic);
    assert.equal(fields['Yates correction'],yatesCorrection?'1':'0');
    const expectedP=yatesCorrection?0.0297271833060546:0.0157252997545054;
    assert.ok(Math.abs(Number(fields['p value'])-expectedP)<1e-10);
  }
  const fit=evaluate(statisticsCommand('0,1\n1,3\n2,5\n3,7',{op:'regression',regression:'custom',formula:'a*x+b',initials:'[[a,1],[b,0]]'}));assert.equal(fit.parameters.length,2);assert.ok(fit.curve.length>10);
  for(const family of ['normal','t','chi2','f','binomial','poisson','geometric'])evaluate(distributionCommand({family,query:'cdf'}));
  const tip=moneyResult(evaluate(tipCommand({bill:'100',people:'3',whole:true})),3);
  assert.equal(Number(tip.tree.args[2].args[0].value),117);
  const previous=evaluate('1/7');
  assert.equal(evaluate('Ans*7',{variables:{Ans:previous.resultAst}}).exact,'1');
  assert.equal(run({tree:parse('1/0')}).ok,false);
  assert.equal(run({action:'programmer',width:8,base:16,a:'FF',op:'>>',b:'1',signed:true}).bases.HEX,'FF');
  assert.ok(run({action:'constants'}).constants.length>10);
  for(const [graphKind,source,options] of [
    ['cartesian','1/x',{}],['implicit','x^2+y^2=1',{yMin:-3,yMax:3}],['parametric','(cos(t),sin(t))',{variable:'t'}],
    ['polar','1+cos(t)',{variable:'t'}],['sequence','u(n-1)+1',{min:0,max:10,initialTrees:[parse('1')]}],
    ['surface','sin(x)*cos(y)',{surfaceYMin:-3,surfaceYMax:3}],
    ['differential','y',{min:0,max:3,initialValues:[1]}]
  ]) {
    const result=run({action:'graph',graphKind,trees:[parse(source)],min:-3,max:3,...options});
    assert.equal(result.ok,true,`${graphKind}: ${result.error}`);
    assert.ok(result.curves?.[0].length>5 || result.surface?.length>10);
  }
  const discontinuity=run({action:'graph',trees:[parse('1/x')],min:-1,max:1});
  for(const surfaceSamples of [12,26,40,96]){
    const result=run({action:'graph',graphKind:'surface',trees:[parse('x+y')],min:-1,max:1,surfaceYMin:-1,surfaceYMax:1,surfaceSamples});
    assert.equal(result.ok,true,result.error);assert.equal(result.surfaceSamples,surfaceSamples);
    assert.equal(result.surface.length,surfaceSamples+1);assert.ok(result.surface.every(row=>row.length===surfaceSamples+1));
  }
  const intersectionStart=performance.now();
  for(const [parameters,expected] of [[{a:1,b:1,c:1},2],[{a:0,b:0,c:1},2],[{a:1,b:0,c:-3},4]]){
    const result=run({action:'graphAnalysis',graphKind:'cartesian',trees:[parse('a*x^2+b*x+c'),parse('x^2+y^2=5')],parameters,variables:{a:parse('999'),b:parse('999'),c:parse('999')},analysis:'intersection',selected:0,other:1,a:-3,b:3});
    assert.equal(result.ok,true,result.error);assert.equal(result.points.length,expected);
    for(const [x,y] of result.points){assert.ok(Math.abs(x*x+y*y-5)<1e-7);assert.ok(Math.abs(parameters.a*x*x+parameters.b*x+parameters.c-y)<1e-7);}
  }
  console.log(`WASM quadratic/circle intersections: ${Math.round(performance.now()-intersectionStart)} ms for 3 parameter sets`);
  const implicit=run({action:'graph',graphKind:'implicit',trees:[parse('x^2+y^2=a'),parse('x=.3')],parameters:{a:4},min:-3,max:3,yMin:-3,yMax:3});
  assert.equal(implicit.ok,true,implicit.error);assert.deepEqual(implicit.parameters,['a']);assert.equal(implicit.curves.length,2);
  const circle=implicit.curves[0].filter(Boolean);assert.ok(circle.some(([x,y])=>x<0&&y<0)&&circle.some(([x,y])=>x>0&&y>0));
  assert.ok(circle.every(([x,y])=>Math.abs(x*x+y*y-4)<1e-5));
  assert.ok(implicit.curves[1].filter(Boolean).every(([x])=>Math.abs(x-.3)<1e-6));
  assert.ok(discontinuity.curves[0].some(p=>p===null));
  py.globals.set('payload',JSON.stringify({source:'import calcmax_catalog as calc\nprint(calc.mean([1,2,3]))'}));
  assert.equal(JSON.parse(py.runPython('script_runner.run(payload)')).output,'2\n');
  // This loads the same source archive that the static browser Worker consumes.
  console.log(`WASM engine passed: Python ${py.runPython('sys.version.split()[0]')}, SymPy ${py.runPython('calc_engine.s.__version__')}`);
});

async function engine(fresh=false) {
  const py=await (fresh?loadRuntime():runtime());
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

for(const degree of [5])test(`fresh WASM solves x^${degree}-x+1=0 and produces every decimal root`,async t=>{
  const dom=new JSDOM(''),previous=globalThis.document;
  globalThis.document=dom.window.document;
  t.after(()=>{globalThis.document=previous;dom.window.close();});
  const run=await engine(true),tree=parse(equationCommand({source:`x^${degree}-x+1=0`,variable:'x'}));
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

test('WASM graph integrals return exact cancellation without losing small nonzero results',async()=>{
  const py=await runtime();
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
