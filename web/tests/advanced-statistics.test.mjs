import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {advancedStatisticsSchema as schema} from '../advanced-statistics-schema.js';
import {guidedStatisticsCommand,survivalAnalysisPlan,advancedStatisticsTermLabels} from '../advanced-statistics.js';
import {survivalStepPoints,survivalNumber} from '../survival-report.js';
import {parse} from '../parser.js';

test('proportion forms reject incompatible binary categories and invalid count roles',()=>{
  const single=schema.find(item=>item.id==='propztest'),two=schema.find(item=>item.id==='propztest2');
  assert.throws(()=>guidedStatisticsCommand(single,[['a'],['b']],{layout:'binary',successValue:'yes'}),/Success value/);
  assert.throws(()=>guidedStatisticsCommand(two,[['a','b'],['c','b']],{layout:'binary'}),/same binary/);
  assert.throws(()=>guidedStatisticsCommand(two,[['1','2']]),/exactly two rows/);
  assert.throws(()=>guidedStatisticsCommand(single,[['1','2']],{trials:'0'}),/different columns/);
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

test('weighted kappa requires an explicit shared ordinal category order',()=>{
  const definition=schema.find(item=>item.id==='cohenkappa');
  const rows=[['low','mid'],['high','high'],['mid','low']];
  assert.throws(()=>guidedStatisticsCommand(definition,rows,{layout:'pairs',weights:'quadratic'}),/category order/i);
  assert.throws(()=>guidedStatisticsCommand(definition,rows,{layout:'pairs',weights:'linear',categories:'low,high'}),/every observed category/);
  assert.throws(()=>guidedStatisticsCommand(definition,rows,{layout:'pairs',weights:'quadratic',categories:'low,mid,mid,high'}),/exactly once/);
  const settings={layout:'pairs',weights:'quadratic',categories:'low,mid,high'};
  assert.equal(guidedStatisticsCommand(definition,rows,settings),'cohenkappa([[0,1,0],[1,0,0],[0,0,1]],quadratic)');
  assert.deepEqual(advancedStatisticsTermLabels(definition,rows,settings,['Reviewer A','Reviewer B']),{'table:row':'Reviewer A','table:column':'Reviewer B','table:row:1':'low','table:row:2':'mid','table:row:3':'high','table:column:1':'low','table:column:2':'mid','table:column:3':'high'});
  for(const id of ['mediation','moderation'])assert.throws(()=>guidedStatisticsCommand(schema.find(item=>item.id===id),[['1','2','3']],{response:'0'}),/different columns/);
  assert.throws(()=>guidedStatisticsCommand(schema.find(item=>item.id==='cfa'),[['1','2','3']],{}),/one positive factor ID/);
});
