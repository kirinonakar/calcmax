import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {JSDOM} from 'jsdom';
import {advancedStatisticsSchema as schema} from '../advanced-statistics-schema.js';
import {advancedStatisticsCommand,advancedStatisticsRows,createAdvancedStatistics,guidedStatisticsCommand,survivalAnalysisPlan} from '../advanced-statistics.js';
import {survivalStepPoints,survivalNumber} from '../survival-report.js';
import {parse,latexInput} from '../parser.js';
import {requiresExplicitEvaluation} from '../evaluation-policy.js';
import {setLanguage} from '../i18n.js';
import {restoreFields} from '../app-state.js';

test('advanced data shapes preserve subjects, censoring, categories and missing cells',()=>{
  const definition=id=>schema.find(item=>item.id===id);
  assert.equal(advancedStatisticsCommand(definition('padjust'),[['.01'],['.04']]),'padjust([.01,.04],holm,0.05)');
  assert.equal(advancedStatisticsCommand(definition('cohend'),[['1','2'],['3','']]),'cohend([1,3],[2],independent)');
  assert.equal(advancedStatisticsCommand(definition('impute'),[['1',''],['','2']]),'impute([[1,NA],[NA,2]],mean)');
  assert.equal(advancedStatisticsCommand(definition('logrank'),[['1','1','A'],['2','0','B'],['3','0','A'],['4','1','B']]),'logrank([[1,1],[3,0]],[[2,0],[4,1]])');
  assert.throws(()=>advancedStatisticsCommand(definition('cox'),[['1','','0'],['2','1','1']]),/Complete rows/);
  assert.throws(()=>advancedStatisticsCommand(definition('kstest'),[['1','2','3']]),/exactly two columns/);
  assert.throws(()=>advancedStatisticsCommand(definition('logrank'),[['1','1','A'],['2','0','A']]),/exactly two groups/);
  assert.deepEqual(advancedStatisticsRows('time,event,x\n1,0,2\n2,1,3\n'),[['1','0','2'],['2','1','3']]);
  assert.deepEqual(advancedStatisticsRows('NA,NA\n1,2'),[['NA','NA'],['1','2']]);
  assert.deepEqual(advancedStatisticsRows('1,2\n\n3,4'),[['1','2'],['',''],['3','4']]);
});

test('all advanced examples parse and require explicit evaluation',()=>{
  for(const item of schema){const tree=parse(latexInput(item.example));assert.equal(tree.value,item.id);assert.equal(requiresExplicitEvaluation(tree),true,item.id);}
});

test('survival plans validate distinct roles, preserve labels and omit unselected cells',()=>{
  const plan=survivalAnalysisPlan([['1','yes','A','30',''],['2','no','B','40','']],{eventValue:'yes',cox:'1',predictors:'3'},['time','status','arm','age','unused']);
  assert.deepEqual(plan,{expression:'survivalanalysis([[1,1,1,30],[2,0,2,40]],1,breslow,-1,1)',groups:['A','B'],predictors:['age']});
  assert.throws(()=>survivalAnalysisPlan([['1','1','A']],{group:'1'}),/different columns/);
  assert.throws(()=>survivalAnalysisPlan([['1','1','A']],{cox:'1',predictors:'2'}),/distinct analysis columns/);
  assert.throws(()=>survivalAnalysisPlan([['1','1','A'],['2','','B']]),/Complete selected rows/);
  assert.deepEqual(survivalStepPoints([[1,4,1,1,.75,.4,.9],[2,2,1,0,.375,.1,.7]],4),[[0,1],[1,1],[1,.75],[2,.75],[2,.375]]);
  assert.equal(survivalNumber(0.0000012345), '0.0000012345');
  assert.equal(survivalNumber(0.000000012345), '1.2345e-8');
  assert.equal(survivalNumber(1.310050946), '1.3101');
});

test('survival suite renders labeled steps, CI, censoring and HR and invalidates stale results',t=>{
  const dom=new JSDOM(readFileSync(new URL('../index.html',import.meta.url),'utf8'));
  const original=Object.getOwnPropertyDescriptor(globalThis,'document');
  Object.defineProperty(globalThis,'document',{value:dom.window.document,configurable:true});
  t.after(()=>{setLanguage('en');dom.window.close();if(original)Object.defineProperty(globalThis,'document',original);else delete globalThis.document;});
  setLanguage('en');
  const state={fields:{'statistics-advanced-kind':'survivalanalysis','statistics-advanced-input':'current'}};
  let data='time,status,arm\n1,1,<A>\n2,0,B';
  const api=createAdvancedStatistics({state,persist:()=>{},data:()=>data});
  const $=id=>document.getElementById(id),context=api.context();
  const result={ok:true,survival:{groups:[{id:1,n:1,events:1,median:1,curve:[[1,1,1,0,0,0,0]]},{id:2,n:1,events:0,median:null,curve:[[2,1,0,1,1,1,1]]}],logrank:{chi2:1,df:1,p:.3},cox:{coefficients:[{term:'group:1',HR:2,'HR CI95':[1,4],p:.1}]}}};
  api.showResult(result,context);
  const report=$('statistics-survival-result');
  assert.equal(report.hidden,false);
  assert.equal(report.querySelectorAll('[data-survival-curve]').length,2);
  assert.equal(report.querySelectorAll('[data-ci-band]').length,2);
  assert.equal(report.querySelectorAll('[data-censored]').length,1);
  assert.match(report.textContent,/B \/ <A>/);
  assert.equal(report.querySelector('a'),null);
  $('statistics-survival-band').checked=false;$('statistics-survival-band').onchange();
  assert.equal(report.querySelectorAll('[data-ci-band]').length,0);
  setLanguage('ko');api.render();assert.match(report.textContent,/미도달/);
  const event=$('statistics-form-survivalanalysis-eventValue');event.value='0';event.oninput();
  assert.equal(report.hidden,true);
  api.showResult(result,context);assert.equal(report.hidden,true,'late response for previous settings is ignored');
  data='time,status,arm\n3,1,<A>\n4,0,B';api.render();
  assert.equal(report.hidden,true);
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

test('guided UI selects correction, survival roles and GEE family, and restores options',t=>{
  const dom=new JSDOM(readFileSync(new URL('../index.html',import.meta.url),'utf8'));
  const original=Object.getOwnPropertyDescriptor(globalThis,'document');
  Object.defineProperty(globalThis,'document',{value:dom.window.document,configurable:true});
  t.after(()=>{setLanguage('en');dom.window.close();if(original)Object.defineProperty(globalThis,'document',original);else delete globalThis.document;});
  const state={fields:{'statistics-advanced-kind':'padjust','statistics-advanced-input':'current'}};
  let data='unused,p\n0,.01\n0,.04\n0,.2',saves=0;
  const api=createAdvancedStatistics({state,persist:()=>saves++,data:()=>data});
  const $=id=>document.getElementById(id),change=(id,value)=>{const field=$(id);field.value=value;field.dispatchEvent(new dom.window.Event('change'));};
  change('statistics-form-padjust-column','1');change('statistics-form-padjust-method','bonferroni');
  assert.equal(api.expression(),'padjust([.01,.04,.2],bonferroni,0.05)');
  assert.equal($('statistics-advanced-expression').hidden,true);
  change('statistics-form-padjust-method','fdr');
  setLanguage('ko');api.render();
  assert.match($('statistics-form-padjust-method').parentElement.textContent,/보정 방법/);
  assert.equal(api.expression(),'padjust([.01,.04,.2],fdr,0.05)');
  data='event,time,group\n1,1,A\n0,2,B\n0,3,A\n1,4,B';
  change('statistics-advanced-kind','logrank');change('statistics-form-logrank-time','1');change('statistics-form-logrank-event','0');
  assert.equal(api.expression(),'logrank([[1,1],[3,0]],[[2,0],[4,1]])');
  data=schema.find(d=>d.id==='gee').exampleRows.map(row=>row.join(',')).join('\n');
  change('statistics-advanced-kind','gee');change('statistics-form-gee-family','poisson');
  assert.match(api.expression(),/,poisson,independence\)$/);
  change('statistics-advanced-kind','padjust');
  assert.equal($('statistics-form-padjust-method').value,'fdr');
  assert.equal($('statistics-form-padjust-column').value,'1');
  assert.ok(saves>=7);
});

test('advanced controls restore selection and edited source, switch locale, and load raw data',t=>{
  const dom=new JSDOM(readFileSync(new URL('../index.html',import.meta.url),'utf8'));
  const original=Object.getOwnPropertyDescriptor(globalThis,'document');
  Object.defineProperty(globalThis,'document',{value:dom.window.document,configurable:true});
  t.after(()=>{setLanguage('en');dom.window.close();if(original)Object.defineProperty(globalThis,'document',original);else delete globalThis.document;});
  const state={fields:{'statistics-advanced-kind':'impute','statistics-advanced-source':'impute([[1,NA],[3,4]],median)'}};
  restoreFields(state);let saves=0;
  const api=createAdvancedStatistics({state,persist:()=>saves++,data:()=> '1,\n,2'});
  assert.equal(document.getElementById('statistics-advanced-kind').value,'impute');
  assert.equal(api.expression(),'impute([[1,NA],[3,4]],median)');
  document.getElementById('statistics-advanced-data').click();
  assert.equal(api.expression(),'impute([[1,NA],[NA,2]],mean)');
  setLanguage('ko');api.render();
  assert.match(document.getElementById('statistics-advanced-help').textContent,/결측값/);
  assert.equal(document.querySelector('#statistics-advanced-kind option[value="impute"]').textContent,'결측치 대체');
  assert.equal(saves,1);
});
