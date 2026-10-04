import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {JSDOM} from 'jsdom';
import {createAppUI} from '../app-ui.js';
import {setLanguage} from '../i18n.js';
import {createStatisticsWorkspace} from '../statistics-workspace.js';

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
