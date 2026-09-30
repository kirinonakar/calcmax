import test from 'node:test';
import assert from 'node:assert/strict';
import {JSDOM} from 'jsdom';
import {previousCalculations,renderPreviousCalculations,followTape} from '../calculation-tape.js';

test('tape keeps ten previous calculations in chronological order and excludes the active result',()=>{
  const history=Array.from({length:15},(_,i)=>({source:String(15-i),exact:String(15-i),time:15-i}));
  assert.deepEqual(previousCalculations(history,history[0]).map(e=>e.source),['5','6','7','8','9','10','11','12','13','14']);
  assert.deepEqual(previousCalculations(history,null,12).map(e=>e.source),['13','14','15']);
  assert.equal(history.length,15,'the full History library remains intact');
});
test('history displays exact math and reusable original formulas; old saved rows remain readable',()=>{
  const dom=new JSDOM('<div id="history"></div>');globalThis.document=dom.window.document;
  const container=document.getElementById('history'),entries=[{source:'1/3+1/6',exact:'1/2',decimal:'0.5',display:{tree:{kind:'fraction',args:[{kind:'number',value:'1'},{kind:'number',value:'2'}]},decimalTree:{kind:'number',value:'0.5'}}},{source:'2+3',exact:'5',decimal:'5'}];let selected;
  renderPreviousCalculations(container,entries,{reuse:entry=>selected=entry});
  assert.equal(container.querySelectorAll('.tape-entry').length,2);assert.ok(container.querySelector('.tape-result mfrac'));
  container.querySelector('.tape-expression').click();assert.equal(selected,entries[0]);
  renderPreviousCalculations(container,entries,{decimal:true,reuse:()=>{}});assert.equal(container.querySelector('.tape-result').textContent,'0.5');
  dom.window.close();
});
test('newest-line scrolling waits for a touch gesture to finish and leaves normal browsing alone',()=>{
  const dom=new JSDOM('<div></div>'),scroll=dom.window.document.querySelector('div');Object.defineProperty(scroll,'scrollHeight',{value:500});
  let time=0,callback=null;const follow=followTape(scroll,{now:()=>time,schedule:fn=>{callback=fn;return 1;},cancel:()=>{callback=null;}});
  follow.latest();assert.equal(scroll.scrollTop,500);
  scroll.scrollTop=100;scroll.dispatchEvent(new dom.window.Event('pointerdown'));follow.latest();assert.equal(scroll.scrollTop,100);
  scroll.dispatchEvent(new dom.window.Event('pointerup'));time=160;callback();assert.equal(scroll.scrollTop,500);
  scroll.scrollTop=80;scroll.dispatchEvent(new dom.window.Event('scroll'));assert.equal(scroll.scrollTop,80);
  follow.dispose();dom.window.close();
});
