import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {advancedStatisticsSchema as schema} from '../advanced-statistics-schema.js';
import {guidedStatisticsCommand,survivalAnalysisPlan,advancedStatisticsTermLabels} from '../advanced-statistics.js';
import {survivalStepPoints,survivalNumber} from '../survival-report.js';
import {parse} from '../parser.js';
import {statisticsReportTarget} from '../statistics-report.js';
import {requiresExplicitEvaluation} from '../evaluation-policy.js';
import {statisticsPlotModel} from '../statistics-visualization.js';

test('Bayesian bootstrap selects two columns or two groups while preserving pairing and names',()=>{
  const definition=schema.find(d=>d.id==='bayesbootstrap');
  assert.equal(definition.controls.find(field=>field.key==='samples').default,'10000');
  assert.match(definition.controls.find(field=>field.key==='statistic').choices.find(choice=>choice.id==='median').label,/sample estimate \/ weighted posterior/);
  const rows=[['control','1'],['treatment','4'],['control','2'],['treatment','5']];
  assert.equal(guidedStatisticsCommand(definition,rows,{layout:'groups'}),'bayesbootstrap([1,2],[4,5],mean,0.95,10000,0,independent)');
  assert.equal(guidedStatisticsCommand(definition,rows,{layout:'groups',order:'reverse'}),'bayesbootstrap([4,5],[1,2],mean,0.95,10000,0,independent)');
  assert.deepEqual(advancedStatisticsTermLabels(definition,rows,{layout:'groups'},[]),{'sample:A':'control','sample:B':'treatment'});
  assert.deepEqual(advancedStatisticsTermLabels(definition,rows,{layout:'groups',order:'reverse'},[]),{'sample:A':'treatment','sample:B':'control'});
  assert.throws(()=>guidedStatisticsCommand(definition,[...rows,['other','9']],{layout:'groups'}),/exactly two groups/);
  assert.throws(()=>guidedStatisticsCommand(definition,[...rows,['control','']],{layout:'groups'}),/Complete selected/);
  const columns=[['1','5'],['2','6'],['','7']];
  assert.equal(guidedStatisticsCommand(definition,columns,{layout:'columns'}),'bayesbootstrap([1,2],[5,6,7],mean,0.95,10000,0,independent)');
  assert.throws(()=>guidedStatisticsCommand(definition,columns,{layout:'columns',comparison:'paired'}),/Complete selected/);
  assert.throws(()=>guidedStatisticsCommand(definition,columns,{layout:'columns',second:'0'}),/different columns/);
});

test('PCA forms preserve feature order and reject incomplete or invalid selections',()=>{
  const definition=schema.find(d=>d.id==='pca'),rows=[['A','1','9'],['B','2','8']];
  assert.equal(guidedStatisticsCommand(definition,rows,{columns:'2,1',components:'1',standardize:'0'}),'pca([[9,1],[8,2]],1,0)');
  assert.deepEqual(advancedStatisticsTermLabels(definition,rows,{columns:'2,1'},['id','height','weight']),{'feature:1':'weight','feature:2':'height'});
  assert.throws(()=>guidedStatisticsCommand(definition,rows,{columns:'1',components:'2'}),/Components/);
  assert.throws(()=>guidedStatisticsCommand(definition,[['1',''],['2','3']]),/Complete selected/);
  assert.throws(()=>guidedStatisticsCommand(definition,rows,{columns:'1,1'}),/distinct/);
});

test('visualizations retain scree cumulative variance, alternative axes and one-component results',()=>{
  const scree=statisticsPlotModel({kind:'scree',ratios:[.6,.3,.1],eigenvalues:[6,3,1]});
  assert.deepEqual(scree.bars.map(b=>b.y),[60,30,10]);assert.ok(Math.abs(scree.line.at(-1)[1]-100)<1e-10);
  const scores=statisticsPlotModel({kind:'scores',points:[[1,2,3],[-1,-2,-3]],ratios:[.6,.3,.1]},2,0);
  assert.deepEqual(scores.points.map(p=>[p.x,p.y]),[[3,1],[-3,-1]]);
  assert.match(scores.xlabel,/PC3/);assert.match(scores.ylabel,/PC1/);
  const single=statisticsPlotModel({kind:'scores',points:[[1],[-1]],ratios:[.8]});
  assert.deepEqual(single.points.map(p=>p.y),[0,0]);assert.ok(single.ymax>single.ymin);
  const histogram=statisticsPlotModel({kind:'histogram',edges:[2.5,3.5],counts:[100],interval:[3,3],estimate:3,statistic:'mean'});
  assert.deepEqual(histogram.markers,[3,3,3]);assert.ok(histogram.xmax>histogram.xmin);
  const outside=statisticsPlotModel({kind:'histogram',edges:[0,1],counts:[100],interval:[.1,.9],estimate:1.5,statistic:'Difference (B − A)',groupLabels:['A','B']});
  assert.ok(outside.xmax>1.5);assert.ok(outside.xmin<0);assert.equal(outside.zero,true);
  const positive=statisticsPlotModel({kind:'histogram',edges:[2,4],counts:[100],interval:[2.1,3.9],estimate:3,statistic:'Difference (B − A)',groupLabels:['A','B']});
  assert.equal(positive.zero,false);
});

test('Bayesian two-sample forms keep independent samples and enforce distinct roles and settings',()=>{
  const definition=schema.find(d=>d.id==='bayescompare'),rows=[['10','13'],['11','14'],['','15']];
  assert.equal(guidedStatisticsCommand(definition,rows),'bayescompare([10,11],[13,14,15],equal,0,0.01,2,1,0.95,20000,0)');
  assert.equal(requiresExplicitEvaluation(parse(definition.example)),true);
  assert.throws(()=>guidedStatisticsCommand(definition,rows,{second:'0'}),/different columns/);
  assert.throws(()=>guidedStatisticsCommand(definition,[['10','13'],['','14']]),/at least two/);
  assert.throws(()=>guidedStatisticsCommand(definition,rows,{kappa:''}),/Enter all Bayesian/);
  assert.throws(()=>guidedStatisticsCommand(definition,rows,{variance:'paired'}),/Invalid analysis option/);
});

test('structured reports route without an explicit target and preserve the invoking menu',()=>{
  assert.equal(statisticsReportTarget({statisticsReport:{analysis:'gee'}},''),'statistics-advanced-result');
  assert.equal(statisticsReportTarget({statisticsReport:{analysis:'stats'}},'stats([1,2])'),'statistics-analysis-result');
  assert.equal(statisticsReportTarget({statisticsReport:{}},'gee([[1,2,3]])'),'statistics-advanced-result');
  assert.equal(statisticsReportTarget({statisticsReport:{analysis:'stats'}},'stats([1,2])','statistics-advanced-result'),'statistics-advanced-result');
  assert.equal(statisticsReportTarget({exact:'2'},'1+1','statistics-advanced-result'),'');
});

test('survival plans validate distinct roles, preserve labels and omit unselected cells',()=>{
  const plan=survivalAnalysisPlan([['1','yes','A','30',''],['2','no','B','40','']],{eventValue:'yes',cox:'1',predictors:'3'},['time','status','arm','age','unused']);
  assert.deepEqual(plan,{expression:'survivalanalysis([[1,1,1,30],[2,0,2,40]],1,efron,-1,1)',groups:['A','B'],predictors:['age']});
  assert.throws(()=>survivalAnalysisPlan([['1','1','A']],{group:'1'}),/different columns/);
  assert.throws(()=>survivalAnalysisPlan([['1','1','A']],{cox:'1',predictors:'2'}),/distinct analysis columns/);
  assert.throws(()=>survivalAnalysisPlan([['1','1','A'],['2','','B']]),/Complete selected rows/);
  assert.deepEqual(survivalStepPoints([[1,4,1,1,.75,.4,.9],[2,2,1,0,.375,.1,.7]],4),[[0,1],[1,1],[1,.75],[2,.75],[2,.375]]);
  assert.equal(survivalNumber(0.0000012345), '0.0000012345');
  assert.equal(survivalNumber(0.000000012345), '1.2345e-8');
  assert.equal(survivalNumber(1.310050946), '1.3101');
});

test('Android and Web shared form cases select roles, groups, methods and independent samples',()=>{
  const cases=JSON.parse(readFileSync(new URL('../../tests/fixtures/statistics_forms.json',import.meta.url),'utf8'));
  for(const item of cases)assert.equal(guidedStatisticsCommand(schema.find(d=>d.id===item.id),item.rows,item.settings),item.expected,item.id);
  for(const definition of schema.filter(item=>item.controls))assert.deepEqual(parse(guidedStatisticsCommand(definition,definition.exampleRows)),parse(definition.example),definition.id);
  const km=schema.find(d=>d.id==='kaplanmeier');
  assert.equal(guidedStatisticsCommand(km,[['1','0'],['2','0']]),'kaplanmeier([[1,0],[2,0]],0.95)');
  assert.throws(()=>guidedStatisticsCommand(km,[['1','1'],['2','0']],{time:'0',event:'0'}),/different columns/);
  assert.throws(()=>guidedStatisticsCommand(schema.find(d=>d.id==='cox'),[['1','1','3'],['2','0','4']],{predictors:'0'}),/distinct analysis columns/);
});

test('ANCOVA and GLM reject invalid roles and links and preserve source labels',()=>{
  const ancova=schema.find(d=>d.id==='ancova'),glm=schema.find(d=>d.id==='glm');
  const rows=[['Control','1','3'],['Treatment','2','5']];
  assert.deepEqual(advancedStatisticsTermLabels(ancova,rows,{},['arm','baseline','response']),{Group:'arm','group:1':'Control','group:2':'Treatment',x1:'baseline'});
  assert.deepEqual(advancedStatisticsTermLabels(glm,rows,{predictors:'1'},['arm','baseline','response']),{x1:'baseline'});
  assert.throws(()=>guidedStatisticsCommand(ancova,rows,{response:'0'}),/different columns/);
  assert.throws(()=>guidedStatisticsCommand(ancova,rows,{predictors:'0,1'}),/distinct analysis columns/);
  assert.throws(()=>guidedStatisticsCommand(ancova,[['A','','3'],['B','2','5']]),/Complete selected rows/);
  assert.throws(()=>guidedStatisticsCommand(glm,rows,{predictors:'1',family:'poisson',link:'logit'}),/Invalid analysis option/);
  assert.throws(()=>guidedStatisticsCommand(glm,rows,{predictors:'1',adjustment:'offset',offset:'2'}),/different columns/);
});
