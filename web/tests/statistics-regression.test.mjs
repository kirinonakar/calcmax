import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {JSDOM} from 'jsdom';
import {createAppUI} from '../app-ui.js';
import {createStatisticsWorkspace} from '../statistics-workspace.js';
import {setLanguage,t as translate} from '../i18n.js';

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

test('New empties text and table, cancels regression, clears plots and selection, and retains saved datasets',async t=>{
  const context=workspace(t),{$,state,requests,results,statistics,run}=context;
  $('dataset-list').value='saved';$('dataset-list').dispatchEvent(new document.defaultView.Event('change'));
  statistics.showRegression({ok:true,decimal:'2*x',curve:[[1,2],[2,4]]});
  $('statistics-table-toggle').click();
  assert.ok($('statistics-grid').querySelectorAll('input').length>0);
  const before=context.saves,pending=run();
  $('statistics-new').click();
  assert.equal(context.cancels,1);assert.equal(context.saves,before+1);
  assert.equal($('statistics-data').value,'');assert.equal($('dataset-name').value,'');assert.equal($('dataset-list').value,'');
  assert.equal($('statistics-kind').value,'xy');
  assert.equal($('statistics-grid').querySelectorAll('input').length,0);
  assert.equal($('statistics-grid').hidden,false);
  assert.equal($('statistics-plot').hidden,true);assert.equal($('statistics-plot').childElementCount,0);
  assert.equal($('regression-caption').childElementCount,0);assert.equal($('regression-transfer').hidden,true);
  assert.deepEqual(state.datasets,{saved:'1,2\n2,4'});assert.deepEqual(state.datasetKinds,{saved:'xy'});
  requests[0].resolve({ok:true,exact:'2*x',curve:[[1,2],[2,4]]});await pending;
  assert.equal(results.length,0);assert.equal($('statistics-plot').hidden,true);
  $('statistics-add-row').click();assert.equal($('statistics-data').value,'0,0');
  $('dataset-list').value='saved';$('dataset-list').dispatchEvent(new document.defaultView.Event('change'));
  assert.equal($('statistics-data').value,'1,2\n2,4');
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

test('regression actions and progress translate into Korean',()=>{
  setLanguage('ko');
  assert.equal(translate('New'),'새로 만들기');assert.equal(translate('Cancel'),'취소');
  assert.equal(translate('Fitting regression…'),'회귀 적합 중…');setLanguage('en');
});
