import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {advancedStatisticsSchema as schema} from '../advanced-statistics-schema.js';
import {guidedStatisticsCommand,survivalAnalysisPlan} from '../advanced-statistics.js';
import {survivalStepPoints,survivalNumber} from '../survival-report.js';
import {parse} from '../parser.js';
import {statisticsReportTarget} from '../statistics-report.js';

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
