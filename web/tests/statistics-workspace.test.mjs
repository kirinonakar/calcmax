import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {JSDOM} from 'jsdom';
import {createAppUI} from '../app-ui.js';
import {setLanguage,translateDOM,t as translate} from '../i18n.js';
import {createStatisticsWorkspace} from '../statistics-workspace.js';
import {statisticsPlotSeries} from '../statistics-plot.js';
import {statisticsCommand,csvRows,statisticsDatasetSource} from '../workspace-commands.js';
import {regressionResidualCSV,renderRegressionReport} from '../regression-report.js';
import {restoreFields} from '../app-state.js';

function workspace(t,fields={}){
  const dom=new JSDOM(readFileSync(new URL('../index.html',import.meta.url),'utf8'));
  globalThis.document=dom.window.document;globalThis.NodeFilter=dom.window.NodeFilter;
  const $=id=>document.getElementById(id),ui=createAppUI(),requests=[],results=[],errors=[];
  let saves=0,cancels=0;
  const state={fields:{'statistics-kind':'xy',...fields},datasets:{saved:'1,2\n2,4'},datasetKinds:{saved:'xy'},digits:10};
  restoreFields(state);
  const engine={ready:true,execute:request=>new Promise(resolve=>requests.push({request,resolve})),cancel:()=>cancels++};
  const statistics=createStatisticsWorkspace({state,engine,ui,persist:()=>saves++,refreshWorkspaceMath:()=>{},
    storeExpression:()=>{},error:message=>errors.push(message),changeMode:()=>{},replaceInput:()=>{},graphs:{}});
  statistics.datasetsList();
  t.after(()=>{ui.dispose();setLanguage('en');dom.window.close();});
  const run=()=>statistics.runRegression({precision:60},(...args)=>results.push(args));
  return {$,state,requests,results,errors,run,statistics,get saves(){return saves;},get cancels(){return cancels;}};
}

test('regularization is selected within linear and logistic families and invalidates prior results',t=>{
  const context=workspace(t,{'regression-kind':'linear','regression-penalty':'elasticnet','regression-alpha':'0.2','regression-ratio':'0.7'});
  const {$,statistics}=context;
  $('statistics-data').value='0,1\n1,3\n2,5';
  assert.equal($('regression-penalty-label').hidden,false);
  assert.equal($('regression-lasso').hidden,false);
  assert.equal($('regression-ratio-label').hidden,false);
  assert.equal(statistics.expression('regression'),'regression([[0,1],[1,3],[2,5]],elasticnet,[0.2,0.7])');
  $('regression-kind').value='logistic';$('regression-kind').dispatchEvent(new document.defaultView.Event('change'));
  $('statistics-data').value='-1,0\n1,1';
  assert.equal(statistics.expression('regression'),'regression([[-1,0],[1,1]],logisticelasticnet,[0.2,0.7])');
  statistics.showRegression({exact:'1/(1+exp(-x))',regression:{model:'logisticelasticnet',n:2,df:null,alpha:.2,l1Ratio:.7,selectedPredictors:1,coefficients:[{name:'b1',estimate:'1'}],residuals:[]}});
  assert.doesNotMatch($('regression-inference').textContent,/OLS|Wald|95% CI/);
  $('regression-alpha').value='0.3';$('regression-alpha').dispatchEvent(new document.defaultView.Event('input'));
  assert.equal($('regression-inference').textContent,'');
});

test('forest reports both importance measures and suppresses algebraic graph transfer',t=>{
  const {$,statistics}=workspace(t,{'regression-kind':'randomforest','regression-trees':'20','regression-depth':'4','regression-seed':'7'});
  $('statistics-data').value='0,1\n1,3\n2,5';
  assert.equal($('regression-penalty-label').hidden,true);
  assert.equal($('regression-forest').hidden,false);
  assert.equal(statistics.expression('regression'),'regression([[0,1],[1,3],[2,5]],randomforest,[20,4,7])');
  statistics.showRegression({exact:'RandomForest',curve:[[0,1],[2,5]],regression:{model:'randomforest',n:3,df:null,trees:20,maxDepth:4,seed:7,oobN:3,permutationN:3,permutationRepeats:3,featureImportance:[{name:'b1',estimate:1}],permutationImportance:[{name:'b1',estimate:.8}],residuals:[]}});
  assert.equal($('regression-caption').textContent,'');
  assert.equal($('regression-transfer').hidden,true);
  assert.match($('regression-inference').textContent,/Feature importance/);
  assert.match($('regression-inference').textContent,/Permutation importance/);
  assert.doesNotMatch($('regression-inference').textContent,/df=null|OLS|95% CI/);
});

test('forest task choices survive restore and keep all response columns selectable',t=>{
  const {$,statistics}=workspace(t,{'statistics-kind':'xyz','regression-kind':'randomforest','regression-forest-task':'classification','regression-response-choice':'1'});
  $('statistics-data').value='10,0,20\n11,1,21';
  assert.equal($('regression-forest-task').value,'classification');
  assert.deepEqual([...$('regression-response').options].map(option=>option.value),['0','1','2']);
  assert.equal(statistics.expression('regression'),'regression([[10,20,0],[11,21,1]],randomforestclassifier,[100,10,0])');
  $('regression-forest-task').value='regression';$('regression-forest-task').dispatchEvent(new document.defaultView.Event('change'));
  assert.equal(statistics.expression('regression'),'regression([[10,20,0],[11,21,1]],randomforestregressor,[100,10,0])');
});

test('regularized commands allow more predictors than rows and keep only complete response rows',()=>{
  for(const mode of ['ridge','lasso','elasticnet','logisticridge','logisticlasso','logisticelasticnet','randomforest']){
    const command=statisticsCommand('1,10,20\n,11,21\n0,12,22',{kind:'xyz',op:'regression',regression:mode,responseColumn:0});
    assert.ok(command.startsWith('regression([[10,20,1],[12,22,0]],'+mode));
  }
});

test('rank commands preserve complete pairs, separate samples, and grouped observations',()=>{
  assert.equal(statisticsCommand('1,4\n2,\n,5\n3,6',{op:'wilcoxon',kind:'xy',tail:'right'}),'wilcoxon([1,3],[4,6],right)');
  assert.equal(statisticsCommand('1\n2\n3',{op:'wilcoxon',kind:'list'}),'wilcoxon([1,2,3])');
  assert.equal(statisticsCommand('1,4\n2,\n,5\n3,6',{op:'mannwhitney',kind:'xy'}),'mannwhitney([1,2,3],[4,5,6])');
  assert.equal(statisticsCommand('a,1\nb,4\na,2\nb,5',{op:'mannwhitney',kind:'xy',grouping:'groups',tail:'left'}),'mannwhitney([1,2],[4,5],left)');
  assert.equal(statisticsCommand('a,1\nb,4\nc,7\na,2\nb,5\nc,8',{op:'kruskal',kind:'xy',grouping:'groups'}),'kruskal([1,2],[4,5],[7,8])');
});

test('polynomial and multivariate commands use degree and complete response rows',()=>{
  assert.equal(statisticsCommand('0,1\n1,3\n2,9\n3,25\n4,57',{op:'regression',kind:'xy',regression:'polynomial',degree:'3'}),'regression([[0,1],[1,3],[2,9],[3,25],[4,57]],polynomial,3)');
  const rows='0,0,1\n1,0,3\n0,1,4\n1,1,7\n2,1,8\n3,2,';
  assert.equal(statisticsCommand(rows,{op:'regression',kind:'xyz',regression:'multiple'}),'regression([[0,0,1],[1,0,3],[0,1,4],[1,1,7],[2,1,8]],multiple)');
  assert.match(statisticsCommand(rows,{op:'regression',kind:'xyz',regression:'logistic'}),/,logistic\)$/);
  assert.equal(statisticsCommand('0,10,20\n1,11,21\n,12,22\n0,13,23\n1,14,24',{op:'regression',kind:'xyz',regression:'logistic',responseColumn:0}),'regression([[10,20,0],[11,21,1],[13,23,0],[14,24,1]],logistic)');
  assert.equal(statisticsCommand('0,10,20\n1,11,21\n,12,22\n0,13,23\n1,14,24',{op:'regression',kind:'xyz',regression:'multiple',responseColumn:0}),'regression([[10,20,0],[11,21,1],[13,23,0],[14,24,1]],multiple)');
  assert.equal(statisticsCommand('0,10\n1,11\n0,12\n1,13',{op:'regression',kind:'xy',regression:'logistic',responseColumn:0}),'regression([[10,0],[11,1],[12,0],[13,1]],logistic)');
  assert.throws(()=>statisticsCommand(rows,{op:'regression',kind:'xyz',regression:'logistic',responseColumn:3}),/dependent variable/);
});

test('logistic response choices follow the data type, orient the formula and axes, and cancel stale fits',async t=>{
  const context=workspace(t),{$,state,requests,results,statistics,run}=context;
  $('regression-kind').value='logistic';$('regression-kind').dispatchEvent(new document.defaultView.Event('change'));
  assert.equal($('regression-response').closest('label').hidden,false);
  assert.deepEqual([...$('regression-response').options].map(option=>option.value),['0','1']);
  assert.equal($('regression-response').value,'1');
  $('statistics-data').value='0,10\n1,11\n0,12\n1,13';
  const old=run();
  $('regression-response').value='0';$('regression-response').dispatchEvent(new document.defaultView.Event('change'));
  assert.equal(state.fields['regression-response-auto'],false);assert.equal(context.cancels,1);
  requests[0].resolve({ok:true,decimal:'x'});await old;assert.equal(results.length,0);
  statistics.showRegression({ok:true,decimal:'1/(1+exp(-x))',curve:[[10,.1],[13,.9]]});
  assert.match($('regression-caption').textContent,/P.*x.*1/);
  assert.equal($('statistics-plot').querySelector('[data-axis-label="x"]').textContent,'y');
  assert.equal($('statistics-plot').querySelector('[data-axis-label="y"]').textContent,'x');
  $('statistics-kind').value='xyz';$('statistics-kind').dispatchEvent(new document.defaultView.Event('change'));
  assert.deepEqual([...$('regression-response').options].map(option=>option.value),['0','2']);
  assert.equal($('regression-response').value,'0','an explicitly selected compatible response is retained');
  $('regression-kind').value='multiple';$('regression-kind').dispatchEvent(new document.defaultView.Event('change'));
  assert.equal($('regression-response').closest('label').hidden,false);
  $('statistics-kind').value='xy';$('statistics-kind').dispatchEvent(new document.defaultView.Event('change'));
  $('regression-kind').value='linear';$('regression-kind').dispatchEvent(new document.defaultView.Event('change'));
  assert.equal($('regression-response').closest('label').hidden,true);
});

test('restored dependent-variable choices survive localization and temporarily hidden regression controls',t=>{
  const {$,state,statistics}=workspace(t,{'statistics-kind':'xyz','regression-kind':'logistic','regression-response':'0','regression-response-choice':'0','regression-response-auto':false});
  setLanguage('ko');translateDOM();statistics.render();
  assert.equal($('regression-response').value,'0');
  $('statistics-kind').value='list';$('statistics-kind').dispatchEvent(new document.defaultView.Event('change'));
  assert.equal(state.fields['regression-response-choice'],'0');
  $('statistics-kind').value='xyz';$('statistics-kind').dispatchEvent(new document.defaultView.Event('change'));
  $('regression-kind').value='logistic';$('regression-kind').dispatchEvent(new document.defaultView.Event('change'));
  assert.equal($('regression-response').value,'0');
  assert.equal($('regression-response').closest('label').hidden,false);
});

test('regression renders inference and residual plot, exports every row, and clears stale data',t=>{
  const {$,statistics}=workspace(t);
  const report={n:120,df:118,fitScale:'y',rSquared:'.9',adjustedRSquared:'.89',rmse:'.2',warnings:[],coefficients:[{name:'b0',estimate:'1',se:'.1',low:'.8',high:'1.2',p:'.01'}],
    residuals:Array.from({length:120},(_,i)=>({row:i+1,observed:String(i),fitted:String(i+.1),residual:'-.1',standardized:'-1',leverage:'.1',cook:'.01'}))};
  statistics.showRegression({ok:true,decimal:'2*x',regression:report});
  assert.match($('regression-inference').textContent,/95% CI/);
  assert.match($('regression-inference').textContent,/R²=0.9/);
  assert.equal($('regression-inference').querySelectorAll('svg circle').length,120);
  assert.equal(regressionResidualCSV(report).split('\n').length,121);
  assert.equal($('regression-export').hidden,false);
  $('statistics-data').value='3,4';$('statistics-data').dispatchEvent(new document.defaultView.Event('input'));
  assert.equal($('regression-inference').textContent,'');
  assert.equal($('regression-export').hidden,true);
});

test('logistic C-statistic and ROC display the correct axes and chance reference in both languages',t=>{
  const {$}=workspace(t);
  const report={n:4,df:2,fitScale:'binomial',auc:'.75',roc:[['0','0'],['0','.5'],['.5','.5'],['.5','1'],['1','1']],coefficients:[],residuals:[],warnings:[]};
  for(const language of ['en','ko']){
    setLanguage(language);renderRegressionReport($('regression-inference'),report);
    assert.match($('regression-inference').textContent,/C-statistic \(AUC\)=0.75/);
    const svg=$('regression-inference').querySelector('svg[data-roc]');
    assert.equal(svg.getAttribute('role'),'img');
    assert.equal(svg.getAttribute('aria-label'),translate('ROC curve'));
    assert.ok(svg.textContent.includes(translate('False positive rate (FPR)')));
    assert.ok(svg.textContent.includes(translate('Sensitivity (TPR)')));
    assert.ok(svg.querySelector('[data-roc-chance]'));
    const positions=[...svg.querySelector('[data-roc-curve]').getAttribute('d').matchAll(/[ML]([\d.]+),([\d.]+)/g)].map(match=>[Number(match[1]),Number(match[2])]);
    assert.equal(positions.length,report.roc.length);
    assert.ok(positions[0][0]<positions.at(-1)[0]&&positions[0][1]>positions.at(-1)[1]);
    assert.ok(positions.slice(1).every((point,i)=>point[0]>=positions[i][0]&&point[1]<=positions[i][1]));
  }
  renderRegressionReport($('regression-inference'),{n:4,df:2,fitScale:'y',coefficients:[],residuals:[]});
  assert.equal($('regression-inference').querySelector('svg[data-roc]'),null);
});

test('xyz regression controls select multivariate models and keep graph transfer hidden',t=>{
  const {$,statistics}=workspace(t);
  $('statistics-kind').value='xyz';$('statistics-kind').dispatchEvent(new document.defaultView.Event('change'));
  assert.equal($('regression-section').hidden,false);
  assert.equal($('regression-kind').value,'multiple');
  assert.equal([...$('regression-kind').options].filter(o=>!o.disabled).map(o=>o.value).join(','),'multiple,logistic,randomforest');
  $('statistics-data').value='0,0,1\n1,0,3\n0,1,4\n1,1,7\n2,1,8';
  statistics.showRegression({ok:true,decimal:'1+2*x1+3*x2'});
  assert.equal($('regression-transfer').hidden,true);
  $('statistics-kind').value='xy';$('statistics-kind').dispatchEvent(new document.defaultView.Event('change'));
  $('regression-kind').value='polynomial';$('regression-kind').dispatchEvent(new document.defaultView.Event('change'));
  assert.equal($('regression-degree').closest('label').hidden,false);
});

test('translated regression labels retain model IDs and the selected model through language changes',t=>{
  const {$,statistics}=workspace(t);
  const models=[...$('regression-kind').options].map(option=>option.value);
  for(const language of ['ko','en','ko']){
    setLanguage(language);translateDOM();statistics.render();
    assert.deepEqual([...$('regression-kind').options].map(option=>option.value),models);
    for(const model of models.filter(model=>model!=='multiple')){
      $('regression-kind').value=model;$('regression-kind').dispatchEvent(new document.defaultView.Event('change'));
      statistics.render();
      assert.equal($('regression-kind').value,model);
      assert.equal($('regression-custom').hidden,model!=='custom');
      assert.equal($('regression-degree').closest('label').hidden,model!=='polynomial');
      const source=statistics.expression('regression');
      assert.match(source,new RegExp(`,${model}(?:,|\\))`));
    }
    $('regression-kind').value='logistic';
  }
  setLanguage('en');translateDOM();
  assert.equal($('regression-kind').value,'logistic');
});

test('Korean xyz data keeps multiple and logistic selectable and submits their model IDs',async t=>{
  const {$,statistics,requests,run}=workspace(t);
  setLanguage('ko');translateDOM();
  $('statistics-kind').value='xyz';$('statistics-kind').dispatchEvent(new document.defaultView.Event('change'));
  $('statistics-data').value='0,0,1\n1,0,0\n0,1,0\n1,1,1\n2,1,0';
  assert.equal($('regression-kind').value,'multiple');
  assert.deepEqual([...$('regression-kind').options].filter(option=>!option.disabled).map(option=>option.value),['multiple','logistic','randomforest']);
  for(const model of ['multiple','logistic','randomforest']){
    $('regression-kind').value=model;$('regression-kind').dispatchEvent(new document.defaultView.Event('change'));
    const pending=run();
    const request=requests.at(-1);
    assert.ok(request,`selected ${model} starts a fit`);
    assert.equal(request.request.tree.args[1].value,model);
    request.resolve({ok:true,decimal:model==='logistic'?'1/2':'x1+x2'});await pending;
    assert.equal($('regression-kind').value,model);
  }
});

for(const language of ['en','ko'])test(`data type changes remove incompatible menu entries and restore valid translated choices (${language})`,t=>{
  const {$,statistics}=workspace(t),options=id=>[...$(id).options].map(option=>option.value);
  const changeKind=kind=>{$('statistics-kind').value=kind;$('statistics-kind').dispatchEvent(new document.defaultView.Event('change'));};
  setLanguage(language);translateDOM();statistics.render();
  const xyModels=['linear','quadratic','polynomial','logistic','randomforest','logarithmic','exponential','power','custom'];
  assert.deepEqual(options('regression-kind'),xyModels);
  $('regression-kind').value='custom';$('regression-kind').dispatchEvent(new document.defaultView.Event('change'));
  changeKind('xyz');
  assert.deepEqual(options('regression-kind'),['multiple','logistic','randomforest']);
  assert.equal($('regression-kind').value,'multiple');
  assert.equal($('regression-custom').hidden,true);
  assert.deepEqual(options('statistics-plot-type'),['histogram','box']);
  assert.deepEqual(options('statistics-grouping'),['columns']);
  $('regression-kind').value='logistic';changeKind('xy');
  assert.equal($('regression-kind').value,'logistic','a model compatible with both shapes stays selected');
  assert.deepEqual(options('regression-kind'),xyModels);
  assert.deepEqual(options('statistics-plot-type'),['scatter','histogram','box']);
  $('statistics-op').value='anova';changeKind('list');
  assert.equal($('regression-section').hidden,true);
  assert.deepEqual(options('regression-kind'),[]);
  assert.equal($('statistics-op').value,'stats');
  for(const invalid of ['correlation','ttestpaired','ttest2','ztest2','anova','tukey','mannwhitney','kruskal','chi2independence','fisherexact'])assert.ok(!options('statistics-op').includes(invalid),`${invalid} is absent for a List`);
  assert.ok(options('statistics-op').includes('wilcoxon'),'one-sample signed ranks remain available');
  assert.deepEqual(options('statistics-plot-type'),['histogram','box']);
  changeKind('xyz');
  assert.deepEqual(options('regression-kind'),['multiple','logistic','randomforest']);
  assert.deepEqual([...$('regression-kind').options].map(option=>option.textContent),['multiple','logistic','Random Forest'].map(translate));
  assert.ok(options('statistics-op').includes('anova'),'valid group tests return after changing the data type');
  changeKind('xy');$('regression-kind').value='polynomial';$('regression-kind').dispatchEvent(new document.defaultView.Event('change'));
  assert.equal($('regression-degree').closest('label').hidden,false);
  assert.ok([...document.querySelectorAll('#regression-kind option,#statistics-op option,#statistics-plot-type option,#statistics-grouping option')].every(option=>!option.disabled),'menus contain no disabled choices');
});

test('independent comparisons omit the first group from second-group choices and hide unsupported grouping',t=>{
  const {$,statistics}=workspace(t),change=id=>$(id).dispatchEvent(new document.defaultView.Event('change'));
  $('statistics-data').value='a,1\nb,4\nc,7\na,2\nb,5\nc,8';
  $('statistics-op').value='mannwhitney';change('statistics-op');
  $('statistics-grouping').value='groups';change('statistics-grouping');
  assert.deepEqual([...$('statistics-second-group').options].map(option=>option.value),['b','c']);
  $('statistics-first-group').value='b';change('statistics-first-group');
  assert.deepEqual([...$('statistics-second-group').options].map(option=>option.value),['a','c']);
  assert.notEqual($('statistics-second-group').value,'b');
  assert.match(statistics.expression(),/^mannwhitney\(\[4,5\],\[(?:1,2|7,8)\]\)$/);
  $('statistics-op').value='wilcoxon';change('statistics-op');
  assert.deepEqual([...$('statistics-grouping').options].map(option=>option.value),['columns']);
  $('statistics-op').value='mannwhitney';change('statistics-op');
  assert.deepEqual([...$('statistics-grouping').options].map(option=>option.value),['columns','groups']);
});

test('regression exposes Cancel immediately and cancelled results cannot return; a new fit succeeds',async t=>{
  const context=workspace(t),{$,requests,results,errors,run}=context;
  $('regression-kind').value='power';
  const cancelled=run();
  assert.equal(requests.length,1);
  assert.equal(requests[0].request.precision,60);
  assert.equal($('regression-cancel').hidden,false);
  assert.equal($('regression-progress').hidden,false);
  assert.equal($('regression-section').getAttribute('aria-busy'),'true');
  await run();assert.equal(requests.length,1,'repeated Analyze does not overlap requests');
  $('regression-cancel').click();
  assert.equal(context.cancels,1);
  assert.equal($('regression-cancel').hidden,true);
  assert.equal($('regression-section').getAttribute('aria-busy'),'false');
  const next=run();
  requests[0].resolve({ok:true,exact:'999*x'});await cancelled;
  assert.equal(results.length,0);
  assert.equal($('regression-cancel').hidden,false,'old completion cannot clear the new busy state');
  requests[1].resolve({ok:true,decimal:'2*x',curve:[[1,2],[2,4]]});await next;
  assert.equal(results.length,1);assert.deepEqual(results[0][3],{decimalDisplay:true});
  assert.equal($('regression-transfer').hidden,false);
  assert.match($('regression-caption').textContent,/2/);
  assert.equal($('regression-cancel').hidden,true);
  assert.deepEqual(errors,[]);
});

test('new and added statistics rows stay blank across editing, saving, and data types',t=>{
  const context=workspace(t),{$,statistics,errors}=context;
  $('statistics-table-toggle').click();
  const cell=(row,col)=>$('statistics-grid').querySelector(`input[data-row="${row}"][data-column="${col}"]`);
  const change=id=>$(id).dispatchEvent(new document.defaultView.Event('change'));
  for(const [kind,columns] of [['list',1],['xy',2],['xyz',3]]){
    $('statistics-kind').value=kind;change('statistics-kind');$('statistics-new').click();
    assert.equal($('statistics-data').value,'');
    assert.equal($('statistics-grid').querySelectorAll('tbody tr').length,0);
    $('statistics-add-row').click();$('statistics-add-row').click();statistics.render();
    assert.equal($('statistics-grid').querySelectorAll('tbody tr').length,2);
    assert.deepEqual([...$('statistics-grid').querySelectorAll('input')].map(input=>input.value),Array(columns*2).fill(''));
    cell(1,0).value='5';cell(1,0).dispatchEvent(new document.defaultView.Event('input'));
    $('statistics-add-row').click();
    assert.equal($('statistics-grid').querySelectorAll('tbody tr').length,3);
    assert.equal(cell(0,0).value,'');assert.equal(cell(1,0).value,'5');assert.equal(cell(2,0).value,'');
    $('statistics-op').value='mean';$('statistics-column').value='0';$('statistics-grouping').value='columns';
    assert.equal(statistics.expression(),'mean([5])','blank rows must not become zero observations');
    $('dataset-name').value=`blank-${kind}`;$('dataset-save').click();$('statistics-new').click();
    $('dataset-list').value=`blank-${kind}`;change('dataset-list');
    assert.equal($('statistics-grid').querySelectorAll('tbody tr').length,3);
    assert.equal(cell(0,0).value,'');assert.equal(cell(1,0).value,'5');assert.equal(cell(2,0).value,'');
    $('statistics-table-toggle').click();$('statistics-table-toggle').click();
    assert.equal($('statistics-grid').querySelectorAll('tbody tr').length,3);
    $('statistics-grid').querySelectorAll('.table-row-action button')[1].click();
    assert.equal($('statistics-grid').querySelectorAll('tbody tr').length,2);
    assert.deepEqual([...$('statistics-grid').querySelectorAll('input')].map(input=>input.value),Array(columns*2).fill(''));
  }
  assert.deepEqual(errors,[]);
});

test('invalid input and failed fits release busy state; edited input discards old results',async t=>{
  const context=workspace(t),{$,requests,results,errors,run}=context;
  $('statistics-data').value='';await run();
  assert.equal(requests.length,0);assert.equal(errors.length,1);
  assert.equal($('regression-cancel').hidden,true);
  $('statistics-data').value='1,2\n2,4';
  const failed=run();requests[0].resolve({ok:false,error:'Singular matrix'});await failed;
  assert.equal(results.length,1);assert.equal(results[0][0].ok,false);
  assert.equal($('regression-cancel').hidden,true);
  const stale=run();$('statistics-data').value='1,3\n2,6';
  requests[1].resolve({ok:true,exact:'2*x'});await stale;
  assert.equal(results.length,1);assert.equal($('regression-cancel').hidden,true);
  const clearing=run();$('regression-clear').click();
  requests[2].resolve({ok:true,exact:'3*x'});await clearing;
  assert.equal(context.cancels,1);assert.equal(results.length,1);
});


test('n-column datasets keep every column in the editor, saved kind, analysis, and response choices',t=>{
  const {$,state,statistics}=workspace(t,{'statistics-kind':'columns','statistics-columns':'5','statistics-data':'1,2,3,4,5\n6,7,8,,10'});
  assert.equal($('statistics-columns-label').hidden,false);
  $('statistics-table-toggle').click();
  assert.equal($('statistics-grid').querySelectorAll('tbody input').length,10);
  assert.equal($('statistics-grid').querySelector('input[data-row="1"][data-column="4"]').value,'10');
  $('statistics-grid').querySelector('input[data-row="1"][data-column="3"]').value='9';
  $('statistics-grid').querySelector('input[data-row="1"][data-column="3"]').dispatchEvent(new document.defaultView.Event('input'));
  $('dataset-name').value='five';$('dataset-save').click();
  assert.equal(state.datasetKinds.five,'columns:5');
  assert.equal(state.datasets.five,'1,2,3,4,5\n6,7,8,9,10');
  assert.equal(statisticsCommand(state.datasets.five,{kind:'columns:5',op:'stats',column:4}),'stats([5,10])');
  assert.equal(statisticsCommand(state.datasets.five,{kind:'columns:5',op:'anova'}),'anova([1,6],[2,7],[3,8],[4,9],[5,10])');
  $('statistics-kind').value='list';$('statistics-kind').dispatchEvent(new document.defaultView.Event('change'));
  $('dataset-list').value='five';$('dataset-list').dispatchEvent(new document.defaultView.Event('change'));
  assert.equal($('statistics-kind').value,'columns');assert.equal($('statistics-columns').value,'5');
  $('regression-kind').value='logistic';$('regression-kind').dispatchEvent(new document.defaultView.Event('change'));
  assert.deepEqual([...$('regression-response').options].map(o=>[o.value,o.textContent]),[['0','first'],['4','last']]);
  $('regression-response').value='4';$('regression-response').dispatchEvent(new document.defaultView.Event('change'));
  $('statistics-columns').value='6';$('statistics-columns').dispatchEvent(new document.defaultView.Event('change'));
  assert.equal($('regression-response').value,'5');
  $('statistics-columns').value='1';$('statistics-columns').dispatchEvent(new document.defaultView.Event('change'));
  assert.equal($('regression-section').hidden,true);
  assert.equal(statisticsCommand('1\n2',{kind:'columns:1',op:'wilcoxon'}),'wilcoxon([1,2])');
});

test('polynomial first/last response controls reorder data and orient the displayed equation and axes',t=>{
  const {$,statistics}=workspace(t,{'statistics-data':'1,0\n4,1\n9,2'});
  $('regression-kind').value='polynomial';$('regression-kind').dispatchEvent(new document.defaultView.Event('change'));
  assert.equal($('regression-response').closest('label').hidden,false);
  assert.deepEqual([...$('regression-response').options].map(o=>o.textContent),['first','last']);
  assert.equal($('regression-response').value,'1');
  $('regression-response').value='0';$('regression-response').dispatchEvent(new document.defaultView.Event('change'));
  assert.equal(statistics.expression('regression'),'regression([[0,1],[1,4],[2,9]],polynomial,3)');
  statistics.showRegression({ok:true,decimal:'x^2+2*x+1',curve:[[0,1],[2,9]]});
  assert.match($('regression-caption').textContent,/x.*y/);
  assert.equal($('statistics-plot').querySelector('[data-axis-label="x"]').textContent,'y');
  assert.equal($('statistics-plot').querySelector('[data-axis-label="y"]').textContent,'x');
});


test('histogram and box plots group by first or last using categorical labels and independent numeric cells',t=>{
  const {$,statistics,state}=workspace(t,{'statistics-kind':'xyz','statistics-plot-type':'histogram','statistics-plot-grouping':'first','statistics-data':'A,1,10\nB,2,20\nA,,30\n,99,99\nB,NaN,40'});
  $('statistics-plot-run').click();
  const legend=[...$('statistics-plot').querySelectorAll('.statistics-plot-legend span')].map(el=>el.textContent);
  assert.deepEqual(legend,['A (n=1)','B (n=1)','A (n=2)','B (n=2)']);
  assert.deepEqual([...new Set([...$('statistics-plot').querySelectorAll('rect[data-series]')].map(el=>el.dataset.series))],['A','B']);
  assert.deepEqual([...$('statistics-plot').querySelectorAll('.statistics-plot-panel')].map(panel=>panel.dataset.column),['y','z']);
  $('statistics-plot-type').value='box';$('statistics-plot-type').dispatchEvent(new document.defaultView.Event('change'));
  assert.deepEqual([...$('statistics-plot').querySelectorAll('.statistics-plot-panel')].map(panel=>panel.querySelectorAll('rect').length),[2,2]);
  $('statistics-kind').value='xy';$('statistics-kind').dispatchEvent(new document.defaultView.Event('change'));
  $('statistics-data').value='1,A\n2,B\n3,A';$('statistics-plot-type').value='box';$('statistics-plot-grouping').value='last';$('statistics-plot-grouping').dispatchEvent(new document.defaultView.Event('change'));
  assert.equal($('statistics-plot').querySelectorAll('rect').length,2);
  assert.match($('statistics-plot').textContent,/A/);assert.match($('statistics-plot').textContent,/B/);
  assert.deepEqual(statisticsPlotSeries([['2024-01-01','1,234'],['2','5'],['','9']],{grouping:'first'}),[{label:'2024-01-01',values:[1234]},{label:'2',values:[5]}]);
  $('statistics-data').value=',7';$('statistics-plot-run').click();assert.equal($('statistics-plot').querySelectorAll('svg').length,0);
  $('statistics-plot-type').value='scatter';$('statistics-plot-type').dispatchEvent(new document.defaultView.Event('change'));
  assert.equal($('statistics-plot-grouping-label').hidden,true);
  assert.equal($('statistics-plot-grouping').value,'last','scatter temporarily hides the saved grouping choice');
});

test('VIF displays beside the matching predictor and omits the intercept value',t=>{
  const {$}=workspace(t),container=$('regression-inference');
  renderRegressionReport(container,{n:8,df:5,coefficients:[{name:'b0',estimate:'1',vif:null},{name:'b1',estimate:'2',vif:'2.0'},{name:'b2',estimate:'3',vif:'2.0'}]},10);
  const table=container.querySelector('table');
  assert.deepEqual([...table.querySelectorAll('thead th')].map(el=>el.textContent),['Parameter','Estimate','SE','95% CI','p','VIF']);
  assert.deepEqual([...table.querySelectorAll('tbody tr')].map(row=>row.lastElementChild.textContent),['—','2','2']);
});


test('arbitrary first-row headers are excluded consistently from analysis, plots, table editing and variable storage',t=>{
  const source='\uFEFFTreatment,Measurement\r\nA,1\r\nB,2\r\nA,3';
  const {$,statistics}=workspace(t,{'statistics-data':source,'statistics-plot-type':'histogram','statistics-plot-grouping':'first'});
  assert.equal(statisticsCommand(source,{kind:'xy',op:'mean',column:1}),'mean([1,2,3])');
  assert.deepEqual(csvRows(source),[['A','1'],['B','2'],['A','3']]);
  assert.equal(statisticsDatasetSource('Time,Outcome\n1,2\n2,4','xy'),'[[1,2],[2,4]]');
  assert.equal(statisticsCommand('Output,Input\n1,0\n4,1\n9,2',{kind:'xy',op:'regression',regression:'polynomial',degree:'2',responseColumn:0}),'regression([[0,1],[1,4],[2,9]],polynomial,2)');
  $('statistics-table-toggle').click();assert.equal($('statistics-grid').querySelectorAll('tbody tr').length,3);
  $('statistics-plot-run').click();assert.deepEqual([...$('statistics-plot').querySelectorAll('.statistics-plot-legend span')].map(el=>el.textContent),['A (n=2)','B (n=1)']);
  assert.deepEqual(csvRows('Value label\n1/2\n2'),[['1/2'],['2']]);
  assert.deepEqual(csvRows('1/2,3/4\n1,2'),[['1/2','3/4'],['1','2']]);
  assert.deepEqual(csvRows('sqrt(2)\n3'),[['sqrt(2)'],['3']]);
  assert.deepEqual(csvRows('A,\nB,2'),[['A',''],['B','2']]);
  assert.deepEqual(csvRows('\uFEFF1\n2'),[['1'],['2']]);
  assert.deepEqual(csvRows('pi\n2'),[['pi'],['2']]);
  assert.deepEqual(csvRows('A,1\nB,2'),[['A','1'],['B','2']]);
  assert.equal(csvRows('Label,Value\nA,1',{skipHeader:false})[0][0],'Label');
});


test('coefficient, odds ratio, VIF and fitted-parameter labels track header columns and response order without renaming engine IDs',t=>{
  const {$,statistics}=workspace(t,{'statistics-kind':'xyz','regression-kind':'logistic','statistics-data':'Outcome,Age,Weight\n0,20,50\n1,30,60'});
  const report={n:8,df:5,fitScale:'binomial',coefficients:[{name:'b0',estimate:'1',oddsRatio:'2',vif:null},{name:'b1',estimate:'3',oddsRatio:'4',vif:'1.2'},{name:'b2',estimate:'5',oddsRatio:'6',vif:'1.3'}]};
  $('regression-response').value='0';$('regression-response').dispatchEvent(new document.defaultView.Event('change'));
  statistics.showRegression({decimal:'1/(1+exp(-x1-x2))',regression:report,parameters:[['b0','1'],['b1','3'],['b2','5']]});
  const cells=()=>[...$('regression-inference').querySelectorAll('table[aria-label="Coefficient inference"] tbody tr')].map(row=>[...row.children].map(cell=>cell.textContent));
  assert.deepEqual(cells().map(row=>row[0]),['Intercept','Age (y)','Weight (z)']);
  assert.deepEqual(cells().map(row=>row[6]),['2','4','6']);
  assert.deepEqual(cells().map(row=>row[5]),['—','1.2','1.3']);
  assert.deepEqual([...$('regression-caption').querySelectorAll('.regression-parameter')].map(el=>el.textContent),['Intercept = 1','Age (y) = 3','Weight (z) = 5']);
  assert.deepEqual(report.coefficients.map(c=>c.name),['b0','b1','b2']);
  setLanguage('ko');translateDOM();statistics.render();
  assert.equal($('regression-inference').querySelector('tbody tr td').textContent,'절편');
  assert.match($('regression-inference').textContent,/Age \(y\)/);
  setLanguage('en');translateDOM();
  $('statistics-data').value='20,50,0\n30,60,1';$('regression-response').value='2';$('regression-response').dispatchEvent(new document.defaultView.Event('change'));
  statistics.showRegression({decimal:'x1+x2',regression:report});
  assert.deepEqual(cells().map(row=>row[0]),['Intercept','x','y']);
});
