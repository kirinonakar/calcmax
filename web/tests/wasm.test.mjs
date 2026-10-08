import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {fileURLToPath} from 'node:url';
import {loadPyodide} from '../vendor/pyodide.mjs';
import {installEngine} from '../engine-bootstrap.js';
import {parse,latexInput} from '../parser.js';
import {tipCommand,moneyResult} from '../money.js';
import {statisticsCommand,distributionCommand,equationCommand} from '../workspace-commands.js';

// Reuse the interpreter for sequential integration scenarios. The cold solver
// scenario below explicitly loads its own interpreter to keep startup coverage.
let sharedRuntime;
test('calculus explanations, special-function primitive and numerical guidance run in real WASM',async()=>{
  const py=await runtime();
  const evaluate=(source,options={})=>{
    py.globals.set('payload',JSON.stringify({tree:parse(latexInput(source)),angle:'RAD',solutionSteps:true,...options}));
    const result=JSON.parse(py.runPython('calc_engine.dispatch(payload)'));
    assert.equal(result.ok,true,`${source}: ${result.error}`);return result;
  };
  for(const [source,title] of [
    ['diff(sin(x^2),x)','Function and chain rules'],
    ['diff(x*exp(x),x)','Product rule'],
    ['integrate(x*exp(x),x)','Integration by parts'],
    ['integrate(2*x*sin(x^2),x)','Substitution rule'],
    ['integrate(x^2,x,0,2)','Evaluate at the bounds'],
    ['limit(sin(x)/x,x,0)',"L'Hôpital's rule for 0/0"],
    ['diff(integrate(x^2,x),x)','Differentiate the expression'],
  ]){
    const traced=evaluate(source),plain=evaluate(source,{solutionSteps:false});
    const {solutionSteps,...answer}=traced;assert.deepEqual(answer,plain,source);
    assert.ok(solutionSteps.steps.some(step=>step.title===title),source);
  }
  const primitive=evaluate('integrate(ln(x)/(1+x^2),x)');
  assert.ok(primitive.exact.includes('polylog'));assert.ok(!primitive.exact.includes('Integral'));
  assert.equal(primitive.guidance.status,'special_function');assert.ok(primitive.resultAst);
  assert.equal(evaluate('Ans(1)',{variables:{Ans:primitive.resultAst}}).ok,true);
  const equation=evaluate('solve(sin(x)=x/2,x)');
  assert.equal(equation.guidance.knownRoots.exact,'{0}');
  const positive=equation.guidance.suggestions.find(suggestion=>suggestion.detail==='1…2');
  assert.ok(positive);const root=evaluate(positive.command);
  assert.ok(Math.abs(Number(root.decimal)-1.895494267033981)<1e-12);
});
test('equation step explanations preserve real WASM answers across workspace methods',async()=>{
  const py=await runtime();
  const run=(source,trace=true)=>{
    py.globals.set('payload',JSON.stringify({tree:parse(latexInput(source)),equationSteps:trace,angle:'RAD'}));
    const result=JSON.parse(py.runPython('calc_engine.dispatch(payload)'));
    assert.equal(result.ok,true,`${source}: ${result.error}`);return result;
  };
  const cases=[
    [{kind:'solve',source:'2x+3=0',variable:'x'},'Divide by the coefficient of the variable'],
    [{kind:'solve',source:'x^2-5x+6=0',variable:'x'},'Apply the quadratic formula'],
    [{kind:'solve',source:'x^3-6x^2+11x-6=0',variable:'x'},'Factor the polynomial'],
    [{kind:'solve',source:'x+y=3x\nx-y=1',variable:'x,y'},'Substitute into the second equation'],
    [{kind:'solve',source:'sin(x)=1/2',variable:'x'},'Include periodic branches (n is an integer)'],
    [{kind:'nsolve',source:'x^2=2',variable:'x',extra:'1,2'},'Numerical root'],
    [{kind:'dsolve',source:'diff(y(t),t)=y(t)',variable:'y(t)',extra:'t',initial:'y(0)=1'},'Integrating factor'],
    [{kind:'pdsolve',source:'diff(u(x,y),x)+diff(u(x,y),y)=0',variable:'u(x,y)'},'Move all terms to the left'],
  ];
  for(const [options,title] of cases){
    const source=equationCommand(options),plain=run(source,false),traced=run(source);
    const {equationSteps,...answer}=traced;
    assert.deepEqual(answer,plain,source);
    assert.ok(equationSteps.steps.some(step=>step.title===title),source);
    assert.equal(equationSteps.steps.at(-1).exact,traced.exact,source);
    assert.ok(equationSteps.steps.every(step=>!step.tree||typeof step.tree.kind==='string'));
  }
  const rational=run(equationCommand({kind:'solve',source:'(x^2-1)/(x-1)=2',variable:'x'}));
  assert.equal(rational.exact,'EmptySet');
  assert.ok(rational.equationSteps.steps.some(step=>step.title==='Check the original domain restrictions'));
});
test('Bayesian linear, logistic and HMC run through workspace commands in real WASM',async()=>{
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
    const hmcSource=statisticsCommand('-2,0\n-1,0\n1,1\n2,1',{op:'regression',kind:'xy',regression:mode,
      bayesianMethod:'hmc',hmcSamples:'200',hmcWarmup:'150',hmcSeed:'11'});
    const hmc=evaluate(hmcSource);
    assert.equal(hmc.regression.method,'hmc');
    assert.equal(hmc.regression.hmc.totalSamples,400);
    assert.ok(Number(hmc.regression.coefficients[1].ess)>0);
    assert.deepEqual(evaluate(hmcSource).regression,hmc.regression,'seeded chains reproduce in WASM');
    assert.ok(hmc.curve.length>100);
  }
  const multivariate=evaluate(statisticsCommand('10,0,20\n12,1,22',{op:'regression',kind:'xyz',responseColumn:1,regression:'bayeslogistic'}));
  assert.equal(multivariate.regression.coefficients.length,3);assert.equal(multivariate.curve.length,0);
  const catalogHmc=py.runPython("__import__('symvacas_catalog').regression_report([[-2,0],[-1,0],[1,1],[2,1]],'bayeslogistic',[2.5,.95,['hmc',100,50,10,0,2]])['method']");
  assert.equal(catalogHmc,'hmc');
});

async function loadRuntime(){
  const py=await loadPyodide({indexURL:fileURLToPath(new URL('../vendor/',import.meta.url))});
  await installEngine(py,{runtimeURL:new URL('../vendor/',import.meta.url),engineURL:new URL('../engine.zip',import.meta.url),fetcher:async url=>new Response(readFileSync(url))});
  return py;
}
function runtime(){return sharedRuntime??=loadRuntime();}

test('statistical conventions, solve domains and labeled eigenvalues in real WASM',async()=>{
  const py=await runtime();
  const run=(source,options={})=>{
    py.globals.set('payload',JSON.stringify({tree:parse(source),...options}));
    return JSON.parse(py.runPython('calc_engine.dispatch(payload)'));
  };
  const evaluate=(source,options={})=>{
    const result=run(source,options);assert.equal(result.ok,true,`${source}: ${result.error}`);return result;
  };
  assert.equal(evaluate('variance([1,2,3])').exact,'1');
  const descriptive=evaluate('stats([1,2,4])');
  const mean=descriptive.statisticsReport.sections.find(s=>s.title==='Summary').rows.find(r=>r[0]==='mean')[1];
  assert.equal(mean.exact,'7/3');assert.equal(mean.tree.kind,'fraction');
  const adjusted=evaluate('padjust([0.01,0.03,0.2],holm)').statisticsReport.sections.find(s=>s.title==='P-value adjustment');
  assert.equal(adjusted.rows.length,3);assert.deepEqual(adjusted.columns,['Observation','raw p','adjusted p','reject (1=yes)']);
  assert.equal(evaluate('variance([1,2,3],0)').exact,'2/3');
  assert.equal(evaluate('variance([1,2,3],1)').exact,'1');
  assert.equal(evaluate('stdev([2,4,4,4,5,5,7,9],0)').exact,'2');
  assert.equal(evaluate('stdev([1,2,3])').exact,'1');
  assert.equal(evaluate('covariance([1,2,3],[2,4,6])').exact,'2');
  assert.equal(evaluate('covariance([1,2,3],[2,4,6],0)').exact,'4/3');
  assert.equal(evaluate('covariance([1,2,3],[2,4,6],1)').exact,'2');
  assert.equal(run('stdev([7],1)').ok,false);
  assert.equal(run('stdev([7])').ok,false);
  const automatic=evaluate('solve(abs(x-1)=3,x)');
  assert.equal(automatic.exact,'{-2, 4}');
  assert.match(automatic.note,/real domain automatically/);
  assert.equal(evaluate('solve(abs(x-1)=3,x)',{variables:{x:parse('99')}}).exact,'{-2, 4}');
  assert.equal(evaluate('solve(sign(x)=1,x)').exact,'Interval.open(0, oo)');
  assert.equal(evaluate('solve(abs(x)=-1,x)').exact,'EmptySet');
  assert.equal(evaluate('solve(x^2+1=0,x)').exact,'{-I, I}');
  assert.equal(run('solve(abs(x-1)=3,x,complex)').ok,false);
  const system=evaluate('solve([abs(x-1)=3,y^2+1=0],[x,y])');
  assert.equal(system.resultAst.args.length,4);
  assert.match(system.note,/for x/);
  assert.equal(evaluate('solve(abs(x-1)=3,x,real)').exact,'{-2, 4}');
  assert.equal(evaluate('solve(abs(x-1)=3,x,integer)').exact,'{-2, 4}');
  assert.equal(evaluate('solve(x^2+1=0,x,real)').exact,'EmptySet');
  assert.equal(evaluate('solve(x^2+1=0,x,complex)').exact,'{-I, I}');
  const lambert=evaluate('solve(exp(x)=x,x)');
  assert.match(lambert.exact,/LambertW\(-1, k\)/);
  assert.match(lambert.note,/All complex solutions/);
  assert.equal(lambert.tree.kind,'rows');
  assert.equal(evaluate('solve(exp(x)=x,x,real)').exact,'EmptySet');
  assert.equal(evaluate('solve(exp(x)=x,x)',{assumptions:{x:['real']}}).exact,'EmptySet');
  assert.match(evaluate('solve(exp(x)=4*x,x,real)').exact,/LambertW\(-1\/4, -1\)/);
  assert.match(evaluate('solve(x*exp(x)=1,x)').note,/Partial solutions/);
  assert.match(evaluate('solve(sin(x)=x,x)').note,/not proof/);
  const integral=evaluate('integrate(sqrt(tan(x)),x)');
  assert.doesNotMatch(integral.exact,/Integral/);
  assert.match(integral.exact,/atan/);
  assert.match(integral.note,/rational function/);
  assert.ok(integral.conditions.includes('tan(x) > 0'));
  assert.equal(evaluate('simplify(diff(Ans,x)-sqrt(tan(x)))',{variables:{Ans:integral.resultAst}}).exact,'0');
  const applied=evaluate('Ans(0.5)',{variables:{Ans:integral.resultAst},angle:'DEG'});
  assert.match(applied.exact,/C/);
  assert.equal(run('Ans(-0.5)',{variables:{Ans:integral.resultAst}}).ok,false);
  assert.match(evaluate('integrate(sqrt(tan(x)+x),x)').note,/nintegrate/);
  assert.equal(evaluate('isprime(2^61-1)').exact,'True');
  assert.equal(run('isprime(2^64)').ok,false);
  assert.equal(evaluate('factorint(10^20)').exact,'2**20*5**20');
  assert.equal(evaluate('divisors(10^20)').resultAst.args.length,441);
  assert.match(run('divisors(2^2000)').error,/2000 results/);
  assert.match(run('divisors(2^1999)').error,/40000 characters/);
  const eigen=evaluate('eigenvalues([[2,1],[1,2]])');
  assert.equal(eigen.tree.kind,'rows');
  assert.match(eigen.exact,/multiplicity/);
  assert.deepEqual(evaluate('Ans',{variables:{Ans:eigen.resultAst}}).resultAst,eigen.resultAst);
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
