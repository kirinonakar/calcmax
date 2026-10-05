import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {JSDOM} from 'jsdom';
import {advancedStatisticsSchema as schema} from '../advanced-statistics-schema.js';
import {advancedStatisticsCommand,advancedStatisticsRows,createAdvancedStatistics,guidedStatisticsCommand} from '../advanced-statistics.js';
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
  assert.match(api.expression(),/,poisson\)$/);
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
