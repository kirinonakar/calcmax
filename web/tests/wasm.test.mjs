import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {fileURLToPath} from 'node:url';
import {loadPyodide} from '../vendor/pyodide.mjs';
import {installEngine} from '../engine-bootstrap.js';
import {parse,latexInput} from '../parser.js';
import {tipCommand,moneyResult} from '../money.js';
import {statisticsCommand,distributionCommand,equationCommand} from '../workspace-commands.js';
import {guidedStatisticsCommand} from '../advanced-statistics.js';
import {advancedStatisticsSchema} from '../advanced-statistics-schema.js';
import {graphInputTree} from '../graph-workspace.js';

// Reuse the interpreter for sequential integration scenarios. The cold solver
// scenario below explicitly loads its own interpreter to keep startup coverage.
let sharedRuntime;

test('implicit 3D surfaces and named scaled space curves run through real WASM and LaTeX input',async()=>{
  const py=await runtime();
  const run=(source,graphKind,options={})=>{
    py.globals.set('payload',JSON.stringify({action:'graph',graphKind,trees:[parse(latexInput(source))],min:-2,max:2,surfaceYMin:-2,surfaceYMax:2,surfaceSamples:20,...options}));
    const started=performance.now(),result=JSON.parse(py.runPython('calc_engine.dispatch(payload)'));
    console.log('WASM 3D:',graphKind,Math.round(performance.now()-started),'ms');
    assert.equal(result.ok,true,result.error);return result;
  };
  const surface=run(String.raw`x^{2}+y^{2}+z^{2}+\sin4x+\sin4y+\sin4z=a`,'surface',{parameters:{a:1}});
  assert.equal(surface.implicitSurface,true);assert.deepEqual(surface.parameters,['a']);assert.ok(surface.surfaceTriangles.length>100);
  assert.equal(surface.surfaceNormals.length,surface.surfaceVertices.length);
  for(const [i,point] of surface.surfaceVertices.entries()){
    assert.ok(Math.abs(point.reduce((sum,v)=>sum+v*v+Math.sin(4*v),-1))<1e-3);
    assert.ok(Math.abs(Math.hypot(...surface.surfaceNormals[i])-1)<1e-10);
  }
  assert.ok(surface.surfaceVertices.some(p=>p[2]<-.5)&&surface.surfaceVertices.some(p=>p[2]>.5));
  const curve=run(String.raw`C(t)=4(\sin t,\cos t,0.6\sin(2t))`,'space',{min:0,max:2*Math.PI});
  assert.deepEqual(curve.parameters,[]);assert.deepEqual(curve.spaceCurves[0][0],[0,4,0]);
  for(const [i,point] of curve.spaceCurves[0].entries()){
    const at=curve.curveParameters[0][i];
    for(const [value,expected] of point.map((v,k)=>[v,[4*Math.sin(at),4*Math.cos(at),2.4*Math.sin(2*at)][k]]))assert.ok(Math.abs(value-expected)<1e-11);
  }
});

test('Desmos restrictions, branch joins, Bayesian bootstrap and PCA forms run through real WASM',async()=>{
  const py=await runtime();
  const run=request=>{
    py.globals.set('payload',JSON.stringify(request));
    const result=JSON.parse(py.runPython('calc_engine.dispatch(payload)'));assert.equal(result.ok,true,result.error);return result;
  };
  const graph=source=>run({action:'graph',graphKind:'cartesian',trees:[graphInputTree(source)],min:-1,max:3,yMin:-1,yMax:6}).curves[0];
  const domain=graph('y=x^2 {0<=x<=2}').filter(Boolean);
  assert.equal(domain[0][0],0);assert.equal(domain.at(-1)[0],2);
  assert.ok(domain.every(([x,y])=>x>=0&&x<=2&&Math.abs(y-x*x)<1e-12));
  const piece=graph('f(x)={x<0:x^2,x>=0:2*x}');
  assert.ok(piece.every(Boolean));assert.ok(piece.some(([x,y])=>x===0&&y===0));
  const jump=graph('y={x<0:1,2}');assert.ok(jump.includes(null));
  for(let i=1;i<jump.length;i++)if(jump[i-1]&&jump[i])assert.equal(jump[i-1][1],jump[i][1]);
  const bootstrap=advancedStatisticsSchema.find(d=>d.id==='bayesbootstrap');
  const command=guidedStatisticsCommand(bootstrap,[['0'],['1']],{samples:'2000',seed:'7'});
  const posterior=run({tree:parse(command)}).statisticsReport;
  assert.equal(posterior.analysis,'bayesbootstrap');assert.equal(posterior.plots[0].counts.reduce((a,b)=>a+b,0),2000);
  assert.ok(Math.abs(posterior.plots[0].interval[0]-.025)<.02);assert.ok(Math.abs(posterior.plots[0].interval[1]-.975)<.02);
  const pca=advancedStatisticsSchema.find(d=>d.id==='pca');
  const source=guidedStatisticsCommand(pca,[['A','1','2'],['B','2','1'],['C','3','4'],['D','4','3']],{columns:'2,1',components:'1'});
  const report=run({tree:parse(source),statisticsTermLabels:{'feature:1':'weight','feature:2':'height'}}).statisticsReport;
  assert.deepEqual(report.plots[2].labels,['weight','height']);assert.equal(report.plots[0].ratios.length,2);
  assert.equal(report.plots[1].points[0].length,1);assert.equal(report.plots[1].points.length,4);
  const scaling=report.sections.find(section=>section.title==='Feature scaling');assert.equal(scaling.rows[0][0],'weight');
});
test('model diagnostic options and guarded inference run through real WASM dispatch',async()=>{
  const py=await runtime();
  const fixture=JSON.parse(readFileSync(new URL('../../tests/fixtures/model_diagnostics_reference.json',import.meta.url),'utf8'));
  const evaluate=source=>{
    py.globals.set('payload',JSON.stringify({tree:parse(source),precision:20,budget:60}));
    const result=JSON.parse(py.runPython('calc_engine.dispatch(payload)'));
    assert.equal(result.ok,true,result.error);return result;
  };
  for(const option of ['profile','[bootstrap,100,7]']){
    const result=evaluate(`mixedmodel(${JSON.stringify(fixture['mixed rows'])},0,ml,${option})`);
    assert.equal(result.statisticsReport.sections[0].title,'Model diagnostics');
    assert.ok(result.statisticsReport.sections.some(section=>section.title==='coefficients'));
    assert.match(result.exact,option==='profile'?/ML profile likelihood/:/Parametric bootstrap/);
    assert.match(result.exact,/p: unavailable/);
  }
  const gee=fixture.gee.find(c=>c.family==='gaussian'&&c.correlation==='exchangeable');
  const result=evaluate(`gee(${JSON.stringify(gee.rows)},gaussian,exchangeable,[],small)`);
  assert.match(result.exact,/Mancl-DeRouen/);
  assert.match(result.exact,/inference df: 6/);
});
test('Bayesian linear, logistic and NUTS run through workspace commands in real WASM',async()=>{
  const py=await runtime();
  const reference=JSON.parse(readFileSync(new URL('../../tests/fixtures/bayesian_regression_reference.json',import.meta.url),'utf8'));
  const evaluate=source=>{
    py.globals.set('payload',JSON.stringify({tree:parse(source),precision:30,budget:30}));
    const result=JSON.parse(py.runPython('calc_engine.dispatch(payload)'));
    assert.equal(result.ok,true,result.error);return result;
  };
  for(const [mode,key] of [['bayeslinear','linear'],['bayeslogistic','laplace']]){
    const source=statisticsCommand('0,-2\n0,-1\n1,1\n1,2',{op:'regression',kind:'xy',responseColumn:0,regression:mode});
    const result=evaluate(source);
    for(const [i,c] of result.regression.coefficients.entries())for(const field of ['estimate','posteriorSD','low','high','probabilityPositive']){
      assert.ok(Math.abs(Number(c[field])-reference[key][i][field])<1e-8,`${mode} ${field}`);
    }
    assert.ok(result.curve.length>100);
    if(mode==='bayeslogistic')assert.ok(result.curve.every(([,y])=>y>=0&&y<=1));
    const nutsSource=statisticsCommand('-2,0\n-1,0\n1,1\n2,1',{op:'regression',kind:'xy',regression:mode,
      bayesianMethod:'nuts',nutsSamples:'200',nutsWarmup:'150',nutsSeed:'11'});
    const nuts=evaluate(nutsSource);
    assert.equal(nuts.regression.method,'nuts');
    assert.equal(nuts.regression.nuts.totalSamples,400);
    assert.ok(Number(nuts.regression.coefficients[1].ess)>0);
    assert.ok(Number(nuts.regression.coefficients[1].bulkEss)>0);
    assert.ok(Number(nuts.regression.coefficients[1].tailEss)>0);
    assert.match(nuts.regression.nuts.diagnosticMethod,/rank-normalized/);
    assert.deepEqual(evaluate(nutsSource).regression,nuts.regression,'seeded chains reproduce in WASM');
    assert.ok(nuts.curve.length>100);
  }
  const multivariate=evaluate(statisticsCommand('10,0,20\n12,1,22',{op:'regression',kind:'xyz',responseColumn:1,regression:'bayeslogistic'}));
  assert.equal(multivariate.regression.coefficients.length,3);assert.equal(multivariate.curve.length,0);
  const catalogNuts=py.runPython("__import__('symvacas_catalog').regression_report([[-2,0],[-1,0],[1,1],[2,1]],'bayeslogistic',[2.5,.95,['nuts',100,50,8,0,2]])['method']");
  assert.equal(catalogNuts,'nuts');
});

async function loadRuntime(){
  const py=await loadPyodide({indexURL:fileURLToPath(new URL('../vendor/',import.meta.url))});
  await installEngine(py,{runtimeURL:new URL('../vendor/',import.meta.url),engineURL:new URL('../engine.zip',import.meta.url),fetcher:async url=>new Response(readFileSync(url))});
  return py;
}
function runtime(){return sharedRuntime??=loadRuntime();}

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
  py.globals.set('payload',JSON.stringify({source:'import symvacas_catalog as calc\nprint(calc.mean([1,2,3]))'}));
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
    }
    run.checkRoots(result,degree);
    assert.equal(result.note,'');
    console.log(`WASM degree ${degree} ${attempt?'warm':'cold'}: ${(performance.now()-started).toFixed(0)} ms`);
  }
});

test('real WASM removes computation capacity while preserving result size and precision',async()=>{
  const py=await runtime();
  const run=request=>{py.globals.set('payload',JSON.stringify(request));return JSON.parse(py.runPython('calc_engine.dispatch(payload)'));};
  const number={kind:'number',value:'1e100001'};
  assert.equal(run({tree:number}).ok,false);
  const result=run({tree:number,removeComputationLimit:true,budget:-1,precision:1000});
  assert.equal(result.ok,true,result.error);
  assert.match(result.exact,/full value in Ans/);
  assert.ok(result.exact.length<10000);
  assert.equal(run({tree:number}).ok,false,'request-scoped policy restores the default');
  const mean={kind:'call',value:'mean',args:[{kind:'symbol',value:'data'}]};
  const request={tree:mean,statisticsDatasets:{data:Array(5001).fill('2')}};
  assert.equal(run(request).ok,false);
  assert.equal(run({...request,removeComputationLimit:true}).exact,'2');
  const huge=run({tree:{kind:'symbol',value:'x'.repeat(40001)},removeComputationLimit:true});
  assert.equal(huge.ok,false);assert.match(huge.error,/display size limit/);
});
