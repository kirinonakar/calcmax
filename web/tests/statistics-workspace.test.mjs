import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {JSDOM} from 'jsdom';
import {createAppUI} from '../app-ui.js';
import {setLanguage} from '../i18n.js';
import {createStatisticsWorkspace} from '../statistics-workspace.js';
import {statisticsCommand} from '../workspace-commands.js';
import {regressionResidualCSV} from '../regression-report.js';

function workspace(t){
  const dom=new JSDOM(readFileSync(new URL('../index.html',import.meta.url),'utf8'));
  globalThis.document=dom.window.document;globalThis.NodeFilter=dom.window.NodeFilter;
  const $=id=>document.getElementById(id),ui=createAppUI(),requests=[],results=[],errors=[];
  let saves=0,cancels=0;
  const state={fields:{'statistics-kind':'xy'},datasets:{saved:'1,2\n2,4'},datasetKinds:{saved:'xy'},digits:10};
  const engine={ready:true,execute:request=>new Promise(resolve=>requests.push({request,resolve})),cancel:()=>cancels++};
  const statistics=createStatisticsWorkspace({state,engine,ui,persist:()=>saves++,refreshWorkspaceMath:()=>{},
    storeExpression:()=>{},error:message=>errors.push(message),changeMode:()=>{},replaceInput:()=>{},graphs:{}});
  statistics.datasetsList();
  t.after(()=>{ui.dispose();setLanguage('en');dom.window.close();});
  const run=()=>statistics.runRegression({precision:60},(...args)=>results.push(args));
  return {$,state,requests,results,errors,run,statistics,get saves(){return saves;},get cancels(){return cancels;}};
}

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

test('xyz regression controls select multivariate models and keep graph transfer hidden',t=>{
  const {$,statistics}=workspace(t);
  $('statistics-kind').value='xyz';$('statistics-kind').dispatchEvent(new document.defaultView.Event('change'));
  assert.equal($('regression-section').hidden,false);
  assert.equal($('regression-kind').value,'multiple');
  assert.equal([...$('regression-kind').options].filter(o=>!o.disabled).map(o=>o.value).join(','),'multiple,logistic');
  $('statistics-data').value='0,0,1\n1,0,3\n0,1,4\n1,1,7\n2,1,8';
  statistics.showRegression({ok:true,decimal:'1+2*x1+3*x2'});
  assert.equal($('regression-transfer').hidden,true);
  $('statistics-kind').value='xy';$('statistics-kind').dispatchEvent(new document.defaultView.Event('change'));
  $('regression-kind').value='polynomial';$('regression-kind').dispatchEvent(new document.defaultView.Event('change'));
  assert.equal($('regression-degree').closest('label').hidden,false);
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
