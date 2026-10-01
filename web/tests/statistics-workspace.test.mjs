import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {JSDOM} from 'jsdom';
import {createAppUI} from '../app-ui.js';
import {setLanguage} from '../i18n.js';
import {createStatisticsWorkspace} from '../statistics-workspace.js';

test('CSV import previews the first three selected rows and follows import options',async t=>{
  const dom=new JSDOM(readFileSync(new URL('../index.html',import.meta.url),'utf8'));
  const {window}=dom;
  globalThis.document=window.document;globalThis.NodeFilter=window.NodeFilter;
  window.HTMLDialogElement.prototype.showModal=function(){this.open=true;};
  window.HTMLDialogElement.prototype.close=function(){this.open=false;};
  const $=id=>document.getElementById(id),ui=createAppUI();
  let saves=0;
  createStatisticsWorkspace({state:{fields:{'statistics-kind':'list'},datasets:{},datasetKinds:{},digits:10},ui,
    persist:()=>saves++,refreshWorkspaceMath:()=>{},storeExpression:()=>{},error:assert.fail,changeMode:()=>{},replaceInput:()=>{},graphs:{}});
  t.after(()=>{ui.dispose();setLanguage('en');dom.window.close();});
  const load=async(source,name='sample.csv')=>{
    $('csv-open').click();
    Object.defineProperty($('file-input'),'files',{value:[{name,text:async()=>source}],configurable:true});
    await $('file-input').onchange();
    assert.equal($('dialog').open,true);
    return [...$('dialog-body').querySelectorAll('input[type="checkbox"]')];
  };
  const toggle=input=>{input.checked=!input.checked;input.dispatchEvent(new window.Event('change'));};
  const preview=()=>$('dialog-body').querySelector('.csv-preview');

  await t.test('header detection, column selection, and import agree while preview leaves the dataset intact',async()=>{
    setLanguage('ko');
    const original=$('statistics-data').value,checkboxes=await load('\uFEFFtime,a,b,c\r\n1,10,100,1000\r\n2,20,200,2000\r\n3,30,300,3000\r\n4,40,400,4000');
    assert.equal($('dialog-body').querySelector('h3').textContent,'미리보기');
    assert.equal(checkboxes[0].checked,true);
    assert.equal(preview().textContent,'1  |  10  |  100\n2  |  20  |  200\n3  |  30  |  300');
    assert.equal(preview().getAttribute('aria-live'),'polite');
    toggle(checkboxes[2]);toggle(checkboxes[4]);
    assert.equal(preview().textContent,'1  |  100  |  1000\n2  |  200  |  2000\n3  |  300  |  3000');
    toggle(checkboxes[0]);
    assert.equal(preview().textContent,'time  |  b  |  c\n1  |  100  |  1000\n2  |  200  |  2000');
    toggle(checkboxes[0]);
    assert.equal($('statistics-data').value,original);assert.equal(saves,0);
    $('dialog-body').querySelector('button').click();
    assert.equal($('statistics-data').value,'1,100,1000\n2,200,2000\n3,300,3000\n4,400,4000');
    assert.equal($('statistics-kind').value,'xyz');assert.equal($('dataset-name').value,'sample');
    assert.equal($('dialog').open,false);assert.equal(saves,1);
  });

  await t.test('headerless TSV and short files preview all available rows',async()=>{
    setLanguage('en');
    const checkboxes=await load('1\t2\n3\t4','short.tsv');
    assert.equal($('dialog-body').querySelector('h3').textContent,'Preview');
    assert.equal(checkboxes[0].checked,false);
    assert.equal(preview().textContent,'1  |  2\n3  |  4');
    toggle(checkboxes[2]);assert.equal(preview().textContent,'1\n3');
    $('dialog-body').querySelector('button').click();
    assert.equal($('statistics-data').value,'1\n3');assert.equal($('statistics-kind').value,'list');
  });

  await t.test('quoted values and missing cells stay literal; invalid selection keeps the dialog open',async()=>{
    const checkboxes=await load('group,value\n"<b>alpha,beta</b>",1\ngamma,2\ndelta\nepsilon,4');
    assert.equal(preview().textContent,'<b>alpha,beta</b>  |  1\ngamma  |  2\ndelta  |  ');
    assert.equal(preview().querySelector('b'),null);
    toggle(checkboxes[1]);toggle(checkboxes[2]);assert.equal(preview().textContent,'');
    const data=$('statistics-data').value,before=saves;
    $('dialog-body').querySelector('button').click();
    assert.equal($('dialog').open,true);assert.equal($('statistics-data').value,data);assert.equal(saves,before);
    assert.equal($('toast').textContent,'Select one to three columns');
    toggle(checkboxes[1]);assert.equal(preview().textContent,'<b>alpha,beta</b>\ngamma\ndelta');
  });
});
