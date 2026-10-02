import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {JSDOM} from 'jsdom';
import {createCalculator} from '../calculator.js';
import {createAppState} from '../app-state.js';
import {parse} from '../parser.js';

function calculatorPage(t,source,{answer='46',previousAnswer}={}) {
  const dom=new JSDOM(readFileSync(new URL('../index.html',import.meta.url),'utf8'));
  const original=Object.getOwnPropertyDescriptor(globalThis,'document');
  globalThis.document=dom.window.document;
  const $=id=>document.getElementById(id),state=createAppState();
  if(previousAnswer)state.variables.Ans=parse(previousAnswer);
  const result=number=>({ok:true,exact:number,decimal:number,tree:parse(number),resultAst:parse(number)});
  const engine={ready:false,execute:t.mock.fn(async()=>result('21'))};
  $('mode').value='scientific';
  $('expression').value=source;
  $('expression').setSelectionRange(0,0);
  const calculator=createCalculator({state,engine,isBusy:()=>false,
    ui:{toast:()=>{},openDialog:()=>{},clipboard:()=>{}},persist:()=>{},schedulePersist:()=>{},
    requestOptions:()=>({variables:state.variables}),error:message=>assert.fail(message),
    changeMode:mode=>{$('mode').value=mode;},updateButtons:()=>{},pressKey:key=>calculator.handleKey(key)});
  calculator.preview();
  calculator.showResult(result(answer),source);
  assert.equal($('commit-indicator').textContent,'=');
  t.after(()=>{
    $('expression').removeEventListener('select',calculator.renderInputCursor);
    calculator.dispose();dom.window.close();
    if(original)Object.defineProperty(globalThis,'document',original);else delete globalThis.document;
  });
  function clickNumber(number) {
    const target=[...$('expression-preview').querySelectorAll('mn')].find(node=>node.textContent===number);
    assert.ok(target,`rendered number ${number}`);
    target.dispatchEvent(new dom.window.MouseEvent('click',{bubbles:true}));
  }
  return {$,dom,calculator,engine,state,clickNumber};
}

test('clicking a number after = lets keypad input replace it and recalculates the edited expression',async t=>{
  const {$,calculator,engine,state,clickNumber}=calculatorPage(t,'12+34');
  clickNumber('34');
  assert.equal($('expression').value.slice($('expression').selectionStart,$('expression').selectionEnd),'34');
  assert.equal($('commit-indicator').textContent,'');
  assert.equal($('note').textContent,'');
  calculator.handleKey('9');
  assert.equal($('expression').value,'12+9');
  assert.equal($('commit-indicator').textContent,'');
  engine.ready=true;
  await calculator.evaluate();
  assert.deepEqual(engine.execute.mock.calls[0].arguments[0].tree,parse('12+9'));
  assert.equal($('answer').textContent,'21');
  assert.equal($('commit-indicator').textContent,'=');
  assert.deepEqual(state.history.map(entry=>entry.source),['12+9','12+34']);
  $('undo').click();
  assert.equal($('expression').value,'12+34');
});

test('keyboard input replaces selected numbers inside completed fractions and powers',t=>{
  const {$,dom,calculator,clickNumber}=calculatorPage(t,'12/34+5^6');
  clickNumber('34');
  document.body.dispatchEvent(new dom.window.KeyboardEvent('keydown',{key:'9',bubbles:true,cancelable:true}));
  assert.equal($('expression').value,'12/9+5^6');
  calculator.showResult({ok:true,exact:'1',decimal:'1',tree:parse('1'),resultAst:parse('1')},$('expression').value);
  clickNumber('6');
  document.body.dispatchEvent(new dom.window.KeyboardEvent('keydown',{key:'2',bubbles:true,cancelable:true}));
  assert.equal($('expression').value,'12/9+5^2');
});

test('pasted text replaces the selected number in a completed expression',t=>{
  const {$,dom,clickNumber}=calculatorPage(t,'12+34');
  clickNumber('34');
  const event=new dom.window.Event('paste',{bubbles:true,cancelable:true});
  Object.defineProperty(event,'clipboardData',{value:{getData:()=>'56'}});
  document.body.dispatchEvent(event);
  assert.equal(event.defaultPrevented,true);
  assert.equal($('expression').value,'12+56');
});

test('a second click places a caret inside a completed number for partial editing',t=>{
  const {$,calculator,clickNumber}=calculatorPage(t,'12+345');
  clickNumber('345');
  document.caretPositionFromPoint=()=>({offsetNode:[...$('expression-preview').querySelectorAll('mn')].find(node=>node.textContent==='345').firstChild,offset:1});
  clickNumber('345');
  assert.equal($('expression').selectionStart,4);
  assert.equal($('expression').selectionEnd,4);
  calculator.handleKey('9');
  assert.equal($('expression').value,'12+3945');
});

test('editing a completed expression retains its original Ans value',async t=>{
  const {$,calculator,engine,clickNumber}=calculatorPage(t,'Ans+2',{answer:'7',previousAnswer:'5'});
  clickNumber('2');
  calculator.handleKey('4');
  assert.equal($('expression').value,'Ans+4');
  engine.ready=true;
  await calculator.evaluate();
  assert.equal(engine.execute.mock.calls[0].arguments[0].tree.args[0].value,'5');
});

test('input after = without clicking still starts a new calculation or chains from Ans',t=>{
  const {$,calculator}=calculatorPage(t,'12+34');
  calculator.handleKey('9');
  assert.equal($('expression').value,'9');
  calculator.showResult({ok:true,exact:'9',decimal:'9',tree:parse('9'),resultAst:parse('9')},'9');
  calculator.handleKey('+');
  calculator.handleKey('2');
  assert.equal($('expression').value,'Ans+2');
});
