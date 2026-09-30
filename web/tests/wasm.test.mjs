import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {fileURLToPath} from 'node:url';
import {loadPyodide} from '../vendor/pyodide.mjs';
import {installEngine} from '../engine-bootstrap.js';
import {parse} from '../parser.js';
import {tipCommand,moneyResult} from '../money.js';
import {statisticsCommand,distributionCommand} from '../workspace-commands.js';

test('actual CPython WASM reuses the Android engine across workspaces',async()=>{
  const py=await loadPyodide({indexURL:fileURLToPath(new URL('../vendor/',import.meta.url))});
  await installEngine(py,{runtimeURL:new URL('../vendor/',import.meta.url),engineURL:new URL('../engine.zip',import.meta.url),fetcher:async url=>new Response(readFileSync(url))});
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
  assert.match(evaluate(statisticsCommand('A,1\nA,2\nA,3\nB,2\nB,4\nB,6',{op:'ztest2',grouping:'groups',sigma:'1',sigmaY:'2',tail:'left'})).exact,/p value/);
  assert.match(evaluate(statisticsCommand('A,yes\nA,no\nB,yes\nB,no',{op:'chi2independence'})).exact,/chi-square/);
  const fit=evaluate(statisticsCommand('0,1\n1,3\n2,5\n3,7',{op:'regression',regression:'custom',formula:'a*x+b',initials:'[[a,1],[b,0]]'}));assert.equal(fit.parameters.length,2);assert.ok(fit.curve.length>10);
  for(const family of ['normal','t','chi2','f','binomial','poisson','geometric'])evaluate(distributionCommand({family,query:'cdf'}));
  for(const [bill,people,whole,tip,total,share] of [['100','3',true,'17','117','39'],['100','2',true,'16','116','58'],['100','1',true,'15','115','115'],['100.01','3',false,'15','115.01',null],['1000000.01','3',true,'150001.99','1150002','383334']]){
    const result=moneyResult(evaluate(tipCommand({bill,people,whole}),{precision:3}),Number(people)),rows=result.tree.args;
    assert.equal(Number(rows[0].args[0].value),Number(tip));assert.equal(Number(rows[2].args[0].value),Number(total));
    if(share!==null)assert.equal(Number(rows[3].args[0].value),Number(share));
    else assert.equal(rows[3].args[0].args.reduce((sum,node)=>sum+Math.round(Number(node.value)*100),0),Math.round(Number(total)*100));
  }
  assert.equal(evaluate('f(3)',{functions:{f:{parameters:['x'],body:parse('x^2+1')}}}).exact,'10');
  const fixedTip=moneyResult(evaluate(tipCommand({bill:'100',fixed:'20',method:'amount',people:'3'})),3);
  assert.equal(Number(fixedTip.tree.args.at(-1).args[0].args[0].value),20,'fixed tip shows the implied percentage');
  const zeroBill=moneyResult(evaluate(tipCommand({bill:'0',fixed:'1',method:'amount',people:'1'})),1);
  assert.equal(Number(zeroBill.tree.args.at(-1).args[0].args[0].value),0,'zero bill does not divide by zero');
  for(const [percent,expected] of [['15','15.00'],['15.126','15.13'],['2.675','2.68'],['99.999','100.00']]){
    const result=moneyResult(evaluate(tipCommand({bill:'100',percent,people:'1',whole:false}),{precision:3}));
    assert.equal(result.tree.args.at(-1).args[0].args[0].value,expected,'percentage rounds exact values to two places');
  }
  for(const [options,expected] of [
    [{bill:'100',people:'2'},'16.00'],[{bill:'100',people:'3'},'17.00'],
    [{bill:'100',people:'3',whole:false},'15.00'],[{bill:'100',people:'1'},'15.00'],
    [{bill:'100',people:'2',tax:'10'},'16.00'],[{bill:'99',people:'3'},'15.15'],
    [{bill:'0',people:'3'},'0.00'],[{bill:'19.99',people:'1',tax:'8'},'17.06']
  ]){
    const result=moneyResult(evaluate(tipCommand(options)),Number(options.people));
    assert.equal(result.tree.args.at(-1).args[0].args[0].value,expected,'Tip % uses the adjusted tip, excluding tax');
  }
  for(const method of ['percent','amount'])for(const whole of [true,false]){
    const result=moneyResult(evaluate(tipCommand({bill:'19.99',percent:'15',fixed:'2.50',tax:'8',people:'1',method,whole})),1),rows=result.tree.args;
    const total=whole?25:method==='amount'?24.09:24.59,tip=whole?3.41:method==='amount'?2.50:3;
    assert.equal(Number(rows[0].args[0].value),tip,'single-person rounding adjusts the tip');
    assert.equal(Number(rows[1].args[0].value),1.60,'single-person rounding preserves tax');
    assert.equal(Number(rows[2].args[0].value),total);
    assert.equal(Number(rows[3].args[0].value),total,'one person pays the full total');
    if(whole)assert.equal(rows.at(-1).args[0].args[0].value,'17.06','one person sees the adjusted tip percentage');
  }
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
