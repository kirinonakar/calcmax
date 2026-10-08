import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {fileURLToPath} from 'node:url';
import {loadPyodide} from '../vendor/pyodide.mjs';
import {installEngine} from '../engine-bootstrap.js';
import {parse,latexInput} from '../parser.js';
import {tipCommand,moneyResult} from '../money.js';
import {statisticsCommand,statisticsAnalysisData,statisticsColumnLabels,statisticsCategoryLabels,distributionCommand,equationCommand} from '../workspace-commands.js';
import {solutionStepsCopyText} from '../equation-steps.js';
import {statisticsResultMarkdown} from '../statistics-markdown.js';
import {statisticsRequest} from '../statistics-request.js';
import {guidedStatisticsCommand} from '../advanced-statistics.js';
import {advancedStatisticsSchema} from '../advanced-statistics-schema.js';

// Reuse the interpreter for sequential integration scenarios. The cold solver
// scenario below explicitly loads its own interpreter to keep startup coverage.
let sharedRuntime;
test('statistics Markdown copy includes every real WASM table row and dedicated regression results',async()=>{
  const py=await runtime();
  const run=source=>{
    py.globals.set('payload',JSON.stringify({tree:parse(source)}));
    const result=JSON.parse(py.runPython('calc_engine.dispatch(payload)'));
    assert.equal(result.ok,true,result.error);return result;
  };
  const rows=Array.from({length:105},(_,i)=>[i,i+1]),imputed=run(`impute(${JSON.stringify(rows)},mean)`);
  const section=imputed.statisticsReport.sections.find(section=>section.title==='data');
  assert.equal(section.rows.length,100);assert.equal(section.copyRows.length,105);
  const markdown=statisticsResultMarkdown(imputed);
  assert.ok(markdown.includes('| 105 | 104 | 105 |'),markdown.slice(-500));
  assert.ok(markdown.includes('| --- | --- | --- |'));
  const regression=run('regression([[0,1],[1,3],[2,4],[3,7]],linear)');
  assert.equal(regression.statisticsReport,undefined);
  const copied=statisticsResultMarkdown(regression);
  assert.ok(copied.includes('### coefficients'));assert.ok(copied.includes('### residuals'));
  assert.ok(copied.includes('### Regression equation'));
  const logistic=run('regression([[0,0],[1,0],[2,1],[3,0],[4,1],[5,1]],logistic)');
  const logisticCopy=statisticsResultMarkdown(logistic);
  assert.ok(logisticCopy.includes('P(y = 1) = '));
  assert.ok(logisticCopy.includes('### coefficients'));
  assert.ok(logisticCopy.includes('oddsRatio'));
  assert.ok(logisticCopy.includes('### residuals'));
  const survival=run('survivalanalysis([[1,1,1],[2,0,1],[3,1,1]],0)');
  const survivalCopy=statisticsResultMarkdown(survival);
  assert.ok(survivalCopy.startsWith('## Survival analysis'));
  assert.ok(survivalCopy.includes('### survival table'));
  assert.ok(survivalCopy.includes('| Time | At risk | Events | Censored | Survival | Lower 95% CI | Upper 95% CI |'));
});
test('selected categorical columns and headers reach the real WASM contingency report',async()=>{
  const py=await runtime(),rows=[[6,2],[1,4]].flatMap((row,i)=>row.flatMap((count,j)=>Array.from({length:count},()=>`${i+j},junk,${['treated','control'][i]},${['yes','no'][j]}`)));
  const source='ID,Unused,Treatment,Outcome\n'+rows.concat('1,junk,,yes','2,,control,').join('\n'),options={op:'fisherexact',kind:'columns:4',firstGroup:'z',secondGroup:'x4'};
  const plan=statisticsAnalysisData(source,options),headers=statisticsColumnLabels(source,options.kind),labels=statisticsCategoryLabels(plan.pairs,headers[2],headers[3]);
  for(const op of ['fisherexact','chi2independence']){
    const request={tree:parse(statisticsCommand(source,{...options,op}))};
    py.globals.set('payload',JSON.stringify(request));const plain=JSON.parse(py.runPython('calc_engine.dispatch(payload)'));
    py.globals.set('payload',JSON.stringify({...request,statisticsTermLabels:labels}));const named=JSON.parse(py.runPython('calc_engine.dispatch(payload)'));
    assert.equal(named.ok,true,named.error);assert.deepEqual(named.resultAst,plain.resultAst);assert.equal(named.exact,plain.exact);
    assert.deepEqual(named.statisticsReport.sections[0].rows,[['Treatment (z)','Outcome (x4)']]);
    const observed=named.statisticsReport.sections.find(section=>section.title==='observed');
    assert.deepEqual(observed.columns,['Treatment (z)','Outcome (x4): yes','Outcome (x4): no']);
    assert.deepEqual(observed.rows.map(row=>row.slice(1).map(cell=>cell.exact)),[['6','2'],['1','4']]);
    if(op==='fisherexact'){assert.match(named.exact,/odds ratio: 12/);assert.match(named.exact,/p value: 4\/39/);}
  }
});
test('calculus explanations, special-function primitive and numerical guidance run in real WASM',async()=>{
  const py=await runtime();
  const evaluate=(source,options={})=>{
    py.globals.set('payload',JSON.stringify({tree:parse(latexInput(source)),angle:'RAD',solutionSteps:true,...options}));
    const result=JSON.parse(py.runPython('calc_engine.dispatch(payload)'));
    assert.equal(result.ok,true,`${source}: ${result.error}`);return result;
  };
  for(const [source,title] of [
    ['diff(ln(x),x)','Function and chain rules'],
    ['diff(sin(x^2),x)','Function and chain rules'],
    ['diff(x*exp(x),x)','Product rule'],
    ['diff(x^x,x)','General power rule'],
    ['diff(x^9,x,5)','Differentiate again'],
    ['integrate(x*exp(x),x)','Integration by parts'],
    ['integrate(2*x*sin(x^2),x)','Substitution rule'],
    ['integrate(x^2,x,0,2)','Evaluate at the bounds'],
    ['integrate(a*x^2,x)','Take out the constant'],
    ['integrate(exp(a*x),x)','Separate parameter cases'],
    ['integrate(x^a,x)','Integrate each parameter case'],
    ['limit(sin(x)/x,x,0)',"L'Hôpital's rule for 0/0"],
    ['limit((sqrt(1+x)-1)/x,x,0)','Rationalize with the conjugate'],
    ['limit(ln(x)/x,x,oo)',"L'Hôpital's rule for infinity/infinity"],
    ['limit((1+x)^(1/x),x,0)','Expand near the approach point'],
    ['limit(x*sin(1/x),x,0)','Squeeze theorem'],
    ['limit(abs(x)/x,x,0,left)','Evaluate the one-sided limit'],
    ['diff(integrate(x^2,x),x)','Differentiate the expression'],
  ]){
    const traced=evaluate(source),plain=evaluate(source,{solutionSteps:false});
    const {solutionSteps,...answer}=traced;assert.deepEqual(answer,plain,source);
    assert.ok(solutionSteps.steps.some(step=>step.title===title),source);
    const final=solutionSteps.steps.at(-1);
    assert.deepEqual(final.tree,traced.tree,source);
    assert.equal(final.equations,undefined,source);
    assert.equal(solutionSteps.steps.filter(step=>step.title==='Computed result'&&
      (step.exact===traced.exact||step.equations?.some(formula=>formula.exact===traced.exact))).length,1,source);
  }
  const logarithm=evaluate('diff(ln(x),x)');
  const reciprocal={kind:'fraction',value:'',args:[{kind:'text',value:'1',args:[]},{kind:'symbol',value:'x',args:[]}]};
  assert.equal(logarithm.exact,'1/x');
  assert.deepEqual(logarithm.tree,reciprocal);
  assert.deepEqual(logarithm.decimalTree,reciprocal);
  assert.deepEqual(logarithm.solutionSteps.steps.at(-1).tree,reciprocal);
  assert.deepEqual(logarithm.solutionSteps.steps.find(step=>step.title==='Combine the derivatives').equations[0].tree,reciprocal);
  assert.ok(solutionStepsCopyText(logarithm.solutionSteps).endsWith('1/x'));
  const primitive=evaluate('integrate(ln(x)/(1+x^2),x)');
  assert.ok(primitive.exact.includes('polylog'));assert.ok(!primitive.exact.includes('Integral'));
  assert.equal(primitive.guidance.status,'special_function');assert.ok(primitive.resultAst);
  assert.equal(evaluate('Ans(1)',{variables:{Ans:primitive.resultAst}}).ok,true);
  const equation=evaluate('solve(sin(x)=x/2,x)');
  assert.match(equation.guidance.knownRoots.exact,/1\.895494267/);
  assert.deepEqual(equation.guidance.searchRange,[-10,10]);
  assert.equal(equation.guidance.knownRoots.approximate,true);
  assert.match(evaluate('solve(cos(x)=x,x)').guidance.knownRoots.exact,/0\.739085133215/);
  for(const [source,answer] of [['limit(x^x,x,0,right)','1'],['limit(sin(x)^x,x,0,right)','1'],['limit(ceiling(x),x,0,left)','0']]){
    const result=evaluate(source);assert.equal(result.exact,answer);
    assert.ok(!result.solutionSteps.steps.some(step=>step.title==='Direct substitution'),source);
  }
  const positive=equation.guidance.suggestions.find(suggestion=>suggestion.detail==='1…2');
  assert.ok(positive);const root=evaluate(positive.command);
  assert.ok(Math.abs(Number(root.decimal)-1.895494267033981)<1e-12);
});
test('equation step explanations preserve real WASM answers across workspace methods',async()=>{
  const py=await runtime();
  const run=(source,trace=true,solutionSteps=false)=>{
    py.globals.set('payload',JSON.stringify({tree:parse(latexInput(source)),equationSteps:trace,solutionSteps,angle:'RAD'}));
    const result=JSON.parse(py.runPython('calc_engine.dispatch(payload)'));
    assert.equal(result.ok,true,`${source}: ${result.error}`);return result;
  };
  const cases=[
    [{kind:'solve',source:'nthroot(x,3)=16',variable:'x'},'Raise both sides to the root index'],
    [{kind:'solve',source:'2*nthroot(3*x+1,3)+4=12',variable:'x'},'Isolate the root'],
    [{kind:'solve',source:'sqrt(x)=-2',variable:'x'},'Check candidates in the original equation'],
    [{kind:'solve',source:'2x+3=0',variable:'x'},'Divide by the coefficient of the variable'],
    [{kind:'solve',source:'x^2-5x+6=0',variable:'x'},'Apply the quadratic formula'],
    [{kind:'solve',source:'x^3-6x^2+11x-6=0',variable:'x'},'Factor the polynomial'],
    [{kind:'solve',source:'x^5-6x^4+12x^3-12x^2+11x-6=0',variable:'x'},'Factor the polynomial'],
    [{kind:'solve',source:'x^4+x^2+1=0',variable:'x'},'Substitute a power of the variable'],
    [{kind:'solve',source:'x^6-5x^3+6=0',variable:'x'},'Recover roots of the original variable'],
    [{kind:'solve',source:'x+y=3x\nx-y=1',variable:'x,y'},'Substitute into the second equation'],
    [{kind:'solve',source:'x+a*y=1\ny=b',variable:'x,y'},'Substitute into the second equation'],
    [{kind:'solve',source:'y+z=3\nx+2y-z=4\n2x-y+z=1',variable:'x,y,z'},'Eliminate one variable'],
    [{kind:'solve',source:'x+y=3\nx^2+y^2=5',variable:'x,y'},'Back-substitute each candidate root'],
    [{kind:'solve',source:'x²+y²=5\nx*y=2',variable:'x,y'},'Combine the candidate solution pairs'],
    [{kind:'solve',source:'x·e^x=1',variable:'x'},'Use the Lambert W inverse'],
    [{kind:'solve',source:'sin(x)=1/2',variable:'x'},'Include periodic branches (n is an integer)'],
    [{kind:'nsolve',source:'x^2=2',variable:'x',extra:'1,2'},'Numerical root'],
    [{kind:'dsolve',source:'diff(y(t),t)=y(t)',variable:'y(t)',extra:'t',initial:'y(0)=1'},'Integrating factor'],
    [{kind:'dsolve',source:'diff(y(t),t)+y(t)/t=t',variable:'y(t)',extra:'t'},'Integrating factor'],
    [{kind:'dsolve',source:'diff(y(t),t,2)-2*diff(y(t),t)+y(t)=0',variable:'y(t)',extra:'t'},'Build the homogeneous solution'],
    [{kind:'pdsolve',source:'diff(u(x,y),x)+diff(u(x,y),y)=0',variable:'u(x,y)'},'Original equation'],
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
  const systemResult=run(equationCommand({kind:'solve',source:'x+y=3x\nx-y=1',variable:'x,y'}),true,true);
  const system=systemResult.equationSteps;
  assert.deepEqual(systemResult.solutionSteps.steps.at(-1).tree,system.steps.at(-1).tree);
  assert.equal(system.steps[1].explanationParts[0].text,'Subtract {term} from both sides.');
  assert.equal(system.steps[1].explanationParts[0].values.term,'x');
  const solved=system.steps.find(step=>step.title==='Solve the equation with one unknown');
  assert.deepEqual(solved.equations.map(formula=>formula.exact),['Eq(-x, 1)','Eq(x, -1)']);
  assert.equal(system.steps.at(-1).tree.kind,'tuple');
  assert.deepEqual(system.advancedSteps.at(-1).equations.map(formula=>formula.exact),['Eq(x, -1)','Eq(y, -2)']);
  const grouped=run('solve([x+y=1,x-y=2],[x,y])',true,true);
  const substitution=grouped.solutionSteps.steps.find(step=>step.title==='Substitute into the second equation');
  const minus=substitution.equations[0].tree.args[0].args.find(node=>node.kind==='unary');
  assert.equal(minus.args[0].kind,'parentheses');
  assert.equal(minus.args[0].args[0].kind,'sum');
  assert.equal(substitution.equations[0].exact,'Eq(2*x - 1, 2)');
  assert.deepEqual(grouped.solutionSteps,grouped.equationSteps);
  const copied=solutionStepsCopyText(grouped.solutionSteps);
  assert.ok(copied.includes('x-(1-x) = 2'),copied);
  assert.ok(copied.includes('x = 3/2'));
  assert.ok(copied.includes('y = -1/2'));
  assert.ok(copied.includes('Advanced solution · Gaussian elimination'));
  const quadratic=run('solve(x^2-5*x+6=0,x)').equationSteps;
  assert.ok(!quadratic.steps.some(step=>['Move all terms to the left','Expand and collect like terms'].includes(step.title)));
  const formula=quadratic.steps.find(step=>step.title==='Apply the quadratic formula');
  assert.equal(formula.exact.match(/sqrt\(1\)/g).length,2);
  const rootCount=tree=>Number(tree.kind==='root')+(tree.args||[]).reduce((total,arg)=>total+rootCount(arg),0);
  assert.equal(rootCount(formula.tree),2);
  assert.equal(quadratic.steps[quadratic.steps.indexOf(formula)+1].title,'Simplify the candidate roots');
  for(const command of ['solve([x^2-5*x+6=0],[x])','solve([x^2-5*x+6=0],[x,y])',
                       equationCommand({kind:'solve',source:'x^2-5*x+6=0',variable:'x,y'})]){
    const single=run(command,true,true),plain=run(command,false);
    const {equationSteps,solutionSteps,...answer}=single;
    assert.deepEqual(answer,plain);
    assert.deepEqual(equationSteps.steps.slice(0,-1),quadratic.steps.slice(0,-1));
    assert.equal(equationSteps.note,'');
    assert.deepEqual(solutionSteps,equationSteps);
  }
  const root=run('solve(nthroot(x,3)=16,x)',true,true);
  assert.equal(root.exact,'{4096}');assert.equal(root.equationSteps.note,'');
  assert.deepEqual(root.solutionSteps,root.equationSteps);
  const rootPower=root.solutionSteps.steps.find(step=>step.title==='Raise both sides to the root index');
  assert.equal(rootPower.tree.args[0].args[0].kind,'parentheses');
  assert.ok(solutionStepsCopyText(root.solutionSteps).includes('16^3'));
  assert.ok(!root.solutionSteps.steps.some(step=>step.title==='Move all terms to the left'));
  const rejected=run('solve(nthroot(x,3)=-2,x)',true,true);
  assert.equal(rejected.exact,'EmptySet');
  const rejection=rejected.solutionSteps.steps.find(step=>step.title==='Check candidates in the original equation');
  assert.equal(rejection.tree.args[0].args[1].value,'!=');
  const trig=run('solve(sin(x)=1/2,x)').equationSteps;
  assert.ok(!trig.steps.some(step=>['Exclude zero denominators','Multiply by the nonzero denominator','Isolate the variable in each branch'].includes(step.title)));
  const elimination=run('solve([y+z=3,x+2*y-z=4,2*x-y+z=1],[x,y,z])').equationSteps.steps.filter(step=>step.title==='Eliminate one variable');
  assert.deepEqual(elimination.map(step=>step.equations.at(-1).exact),['Eq(-5*y + 3*z, -7)','Eq(8*z, 8)']);
  assert.ok(elimination.every(step=>step.equations.length===3&&step.equations[1].tree.args[0].args.every(arg=>arg.kind!=='sum')));
  const nonlinear=run('solve([x+y=3,x^2+y^2=5],[x,y])',true,true);
  assert.equal(nonlinear.equationSteps.note,'');
  assert.deepEqual(nonlinear.solutionSteps,nonlinear.equationSteps);
  const back=nonlinear.equationSteps.steps.find(step=>step.title==='Back-substitute each candidate root');
  assert.equal(back.equations.length,3);
  assert.deepEqual(back.equations.slice(1).map(formula=>formula.exact),['(Eq(x, 2), Eq(y, 1))','(Eq(x, 1), Eq(y, 2))']);
  const symmetric=run(equationCommand({kind:'solve',source:'x²+y²=5\nx*y=2',variable:'x,y'}),true,true);
  assert.equal(symmetric.equationSteps.method,'Sum and difference method');
  assert.equal(symmetric.equationSteps.note,'');
  const pairs=symmetric.equationSteps.steps.find(step=>step.title==='Combine the candidate solution pairs');
  assert.deepEqual(pairs.equations.map(formula=>formula.exact),[
    '(Eq(x, 2), Eq(y, 1))','(Eq(x, 1), Eq(y, 2))','(Eq(x, -1), Eq(y, -2))','(Eq(x, -2), Eq(y, -1))']);
  const lambert=run('solve(x*exp(x)=1,x)',true,true);
  assert.match(lambert.exact,/LambertW\(1, k\)/);
  assert.match(lambert.note,/All complex solutions/);
  assert.ok(!lambert.equationSteps.note.includes('Detailed transformations are unavailable'));
  assert.ok(!lambert.equationSteps.note.includes('Partial solutions'));
  const principal=lambert.equationSteps.steps.find(step=>step.title==='Evaluate the principal real solution');
  assert.match(principal.exact,/0\.567143290409/);
  assert.equal(run('solve(x*exp(x)=1,x,real)').exact,'{LambertW(1)}');
  assert.equal(run('solve(x*exp(x)=-1,x,real)').exact,'EmptySet');
});
test('five thousand numeric rows pass the shared statistics dataset path in real WASM',async()=>{
  const py=await runtime();
  const rows=Array.from({length:5000},(_,i)=>{const x=(i%17)/17;return [i%20,x,2+0.3*(i%20)+1.5*x+0.1*Math.sin(i)];});
  const definition=advancedStatisticsSchema.find(item=>item.id==='mixedmodel');
  const source=guidedStatisticsCommand(definition,rows.map(row=>row.map(String)),{subject:'0',response:'2',predictors:'1',slope:'0'});
  py.globals.set('payload',JSON.stringify({...statisticsRequest(source),precision:15}));
  const result=JSON.parse(py.runPython('calc_engine.dispatch(payload)'));
  assert.equal(result.ok,true,result.error);
  const coefficients=result.statisticsReport.sections.find(section=>section.title==='coefficients');
  assert.ok(Math.abs(Number(coefficients.rows[1][1].exact)-1.5)<0.03);
});
test('Statistics logistic command with five predictors and 1500 rows passes real WASM',async()=>{
  const py=await runtime();let seed=17;
  const random=()=>{seed=(Math.imul(seed,1664525)+1013904223)>>>0;return seed/4294967296;};
  const rows=Array.from({length:1500},()=>{const xs=Array.from({length:5},()=>random()*4-2);return [...xs,Number(random()<1/(1+Math.exp(-0.4*xs[0]+0.3*xs[1])))];});
  const source=statisticsCommand(rows.map(row=>row.join(',')).join('\n'),{op:'regression',kind:'columns:6',regression:'logistic',responseColumn:5});
  py.globals.set('payload',JSON.stringify({...statisticsRequest(source),precision:15}));
  const result=JSON.parse(py.runPython('calc_engine.dispatch(payload)'));
  assert.equal(result.ok,true,result.error);
  assert.equal(result.regression.coefficients.length,6);
  assert.ok(Math.abs(Number(result.regression.coefficients[1].estimate)-0.4)<0.2);
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

test('Bayesian two-sample evidence and posterior references run through shared WASM',async()=>{
  const py=await runtime();
  py.globals.set('reference_json',readFileSync(new URL('../../tests/fixtures/bayesian_two_sample_reference.json',import.meta.url),'utf8'));
  py.runPython(`
import json
from calc_advanced_statistics import advanced
from calc_evaluator import Engine
import sympy as s
for case in json.loads(reference_json):
    actual = advanced(Engine({}),'bayescompare',[s.sympify(v) for v in case['arguments']])
    unequal = case['arguments'][2] == 'unequal'
    for key, expected in case['expected'].items():
        if key == 'difference credible interval':
            for i,target in enumerate(expected):
                tolerance = 6*float(actual['difference interval MCSE'][i])+1e-6 if unequal else 1e-8
                assert abs(float(actual[key][i])-target) <= tolerance, (case['name'],key,i)
        else:
            tolerance = 6*float(actual['probability MCSE'])+1e-6 if unequal and key == 'P(μB > μA)' else 1e-8*max(1,abs(expected))
            assert abs(float(actual[key])-expected) <= tolerance, (case['name'],key)
`);
  for(const mode of ['equal','unequal']){
    py.globals.set('payload',JSON.stringify({tree:parse(`bayescompare([10,11,9,10,12],[13,14,12,15,13],${mode})`),precision:20}));
    const result=JSON.parse(py.runPython('calc_engine.dispatch(payload)'));
    assert.equal(result.ok,true,result.error);
    assert.equal(result.statisticsReport.title,'Bayesian Two-Sample Comparison');
    assert.deepEqual(result.statisticsReport.sections.find(s=>s.title==='difference credible interval').columns,['Lower','Upper']);
    assert.match(result.note,/H0: muB-muA=0/);
  }
  assert.equal(py.runPython("callable(__import__('symvacas_catalog').bayescompare)"),true);
});

test('ANCOVA and GLM match independent references and selected forms in real WASM',async()=>{
  const py=await runtime();
  py.globals.set('reference_json',readFileSync(new URL('../../tests/fixtures/ancova_glm_reference.json',import.meta.url),'utf8'));
  py.runPython(`
import json
from calc_advanced_statistics import advanced
from calc_evaluator import Engine
import sympy as s
for case in json.loads(reference_json):
    actual = advanced(Engine({}),case['function'],[s.sympify(v) for v in case['arguments']])
    for path, expected in case['expected']:
        cell = actual
        for key in path: cell = cell[key]
        assert abs(float(cell)-expected) <= case['tolerance']*max(1,abs(expected)), (case['name'],path)
`);
  const definitions=JSON.parse(readFileSync(new URL('../../app/src/main/assets/advanced_statistics.json',import.meta.url),'utf8'));
  for(const name of ['ancova','glm']){
    const source=definitions.find(d=>d.id===name).example;
    py.globals.set('payload',JSON.stringify({tree:parse(source),precision:40,statisticsTermLabels:{x1:'baseline','group:1':'Control'}}));
    const result=JSON.parse(py.runPython('calc_engine.dispatch(payload)'));
    assert.equal(result.ok,true,result.error);
    assert.equal(result.statisticsReport.analysis,name);
    assert.match(JSON.stringify(result.statisticsReport),/baseline/);
  }
});

test('LMM defaults to REML and preserves explicit ML in real WASM',async()=>{
  const py=await runtime();
  const cases=JSON.parse(readFileSync(new URL('../../tests/fixtures/advanced_statistics_reference.json',import.meta.url),'utf8'))
    .filter(item=>item.name.startsWith('Mixed random intercept '));
  py.globals.set('lmm_reference_json',JSON.stringify(cases));
  py.runPython(`
import json
from calc_advanced_statistics import advanced
from calc_evaluator import Engine
import sympy as s
for case in json.loads(lmm_reference_json):
    actual = advanced(Engine({}), 'mixedmodel', [s.sympify(v) for v in case['arguments']])
    assert actual['estimation'] == ('ML' if case['name'].endswith(' ml') else 'REML')
    for path, expected in case['expected']:
        cell = actual
        for key in path: cell = cell[key]
        assert abs(float(cell)-expected) <= case['tolerance']*max(1,abs(expected)), (case['name'],path)
`);
  const rows=cases[0].arguments[0];
  for(const [suffix,label] of [['','restricted maximum likelihood (REML)'],[',0,ml','maximum likelihood (ML)']]){
    py.globals.set('payload',JSON.stringify(statisticsRequest(`mixedmodel(${JSON.stringify(rows)}${suffix})`)));
    const result=JSON.parse(py.runPython('calc_engine.dispatch(payload)'));
    assert.equal(result.ok,true,result.error);
    assert.ok(result.note.includes(label),result.note);
  }
});

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
