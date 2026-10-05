import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {JSDOM} from 'jsdom';
import {createCalculator} from '../calculator.js';
import {createAppState} from '../app-state.js';
import {parse,latexSymbolLabels} from '../parser.js';

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

test('keyboard characters complete Greek symbol names without inserting constant multiplication',t=>{
  const {$,dom,calculator}=calculatorPage(t,'1+1');
  const type=letter=>document.body.dispatchEvent(new dom.window.KeyboardEvent('keydown',{key:letter,bubbles:true,cancelable:true}));
  for(const [name,glyph] of Object.entries(latexSymbolLabels)){
    $('clear').click();calculator.insert('sin()',4);
    for(let index=0;index<name.length;index++){
      type(name[index]);
      assert.equal($('expression').value,`sin(${name.slice(0,index+1)})`,name);
    }
    assert.equal(parse($('expression').value).args[0].value,name);
    assert.ok([...$('expression-preview').querySelectorAll('mi')].some(node=>node.textContent===glyph),name);
    calculator.handleKey('DEL');assert.equal($('expression').value,'sin()',name);
  }
  $('clear').click();calculator.insert('thta');$('expression').setSelectionRange(2,2);type('e');
  assert.equal($('expression').value,'theta');
  $('clear').click();calculator.insert('th');calculator.handleKey('e');assert.equal($('expression').value,'th*e');
});

test('typing sin and other function names keeps one name and opens its argument',t=>{
  const {$,dom}=calculatorPage(t,'1+1');
  const type=letter=>document.body.dispatchEvent(new dom.window.KeyboardEvent('keydown',{key:letter,bubbles:true,cancelable:true}));
  for(const name of ['sin','cos','tan','asin','sinh','integrate','piecewise','limit']){
    $('clear').click();
    for(let index=0;index<name.length;index++){
      type(name[index]);assert.equal($('expression').value,name.slice(0,index+1),name);
    }
    for(const letter of '(60)')type(letter);
    assert.equal($('expression').value,`${name}(60)`);
    assert.equal(parse($('expression').value).kind,'call');
    assert.equal(parse($('expression').value).value,name);
  }
});

for(const typing of [false,true])for(const backward of [false,true])test(`Greek symbol deletion is atomic in ${typing?'typing':'math'} input (${backward?'Backspace':'Delete'})`,t=>{
  const source='sin(theta)+pi',{$,dom,calculator}=calculatorPage(t,source);
  assert.ok([...$('expression-preview').querySelectorAll('mi')].some(node=>node.textContent==='θ'));
  if(typing)$('typing-toggle').click();
  const at=backward?9:4;$('expression').setSelectionRange(at,at);
  if(typing){
    const event=new dom.window.InputEvent('beforeinput',{inputType:backward?'deleteContentBackward':'deleteContentForward',bubbles:true,cancelable:true});
    $('expression').dispatchEvent(event);assert.equal(event.defaultPrevented,true);
  }else document.body.dispatchEvent(new dom.window.KeyboardEvent('keydown',{key:backward?'Backspace':'Delete',bubbles:true,cancelable:true}));
  assert.equal($('expression').value,'sin()+pi');assert.equal($('expression').selectionStart,4);
  $('undo').click();assert.equal($('expression').value,source);
  $('expression').setSelectionRange(source.length,source.length);calculator.handleKey('DEL');
  assert.equal($('expression').value,'sin(theta)+');
});

for(const keyboard of [false,true])test(`arrows cross function heads through ${keyboard?'keyboard':'keypad'} controls`,t=>{
  const source='sin(60)+cos(60)',{$,dom,calculator}=calculatorPage(t,source);
  $('expression').setSelectionRange(source.length,source.length);
  const move=direction=>{
    if(keyboard)document.body.dispatchEvent(new dom.window.KeyboardEvent('keydown',{key:direction==='LEFT'?'ArrowLeft':'ArrowRight',bubbles:true,cancelable:true}));
    else calculator.handleKey(direction);
  };
  for(const expected of [14,13,12,8,7,6,5,4,0]){
    move('LEFT');assert.equal($('expression').selectionStart,expected);
    assert.equal($('expression').selectionEnd,expected);
    assert.equal($('expression').value,source);
    assert.equal($('expression-preview').querySelector('.input-caret').dataset.sourceStart,String(expected));
  }
  move('RIGHT');assert.equal($('expression').selectionStart,4);
  $('expression').setSelectionRange(8,8);move('RIGHT');assert.equal($('expression').selectionStart,12);
});

for(const keyboard of [false,true])test(`equality exits functions in ${keyboard?'typing':'math'} input after the right arrow`,t=>{
  const {$,dom,calculator}=calculatorPage(t,'1+1');
  $('clear').click();
  calculator.insert('diff(,x)',5);calculator.insert('x');calculator.handleKey('RIGHT');
  if(keyboard){
    $('typing-toggle').click();
    const event=new dom.window.InputEvent('beforeinput',{inputType:'insertText',data:'=',bubbles:true,cancelable:true});
    $('expression').dispatchEvent(event);assert.equal(event.defaultPrevented,true);
  }else calculator.handleKey('RELATION');
  calculator.insert('a');
  assert.equal($('expression').value,'diff(x,x)=a');
  assert.equal(parse($('expression').value).kind,'relation');
  $('undo').click();assert.equal($('expression').value,'diff(x,x)=');
  $('undo').click();assert.equal($('expression').value,'diff(x,x)');
  $('clear').click();calculator.insert('integrate(,x)',10);calculator.insert('x');calculator.handleKey('RIGHT');calculator.insert('=f(x)');
  assert.equal($('expression').value,'integrate(x,x)=f(x)');
});

test('returning from keyboard input measures the visible formula and keeps its insertion point',t=>{
  const {$,calculator}=calculatorPage(t,'sin(9)');
  $('typing-toggle').click();
  calculator.replaceInput('sin(9)',{uncommit:true});
  $('expression').setSelectionRange(5,5);
  const flow=$('expression-preview').querySelector('.input-flow'),number=flow.querySelector('mn');
  const box=(left,top,width,height)=>({left,top,right:left+width,bottom:top+height,width,height});
  flow.getBoundingClientRect=()=>document.documentElement.dataset.typing==='true'?box(0,0,0,0):box(100,50,90,24);
  number.getBoundingClientRect=()=>document.documentElement.dataset.typing==='true'?box(0,0,0,0):box(147,54,12,16);
  calculator.renderInputCursor();
  assert.equal(flow.querySelector('.input-caret'),null,'hidden MathML cannot supply caret coordinates');
  $('typing-toggle').click();
  const caret=flow.querySelector('.input-caret');
  assert.ok(caret,'switching back redraws the caret without an extra key or click');
  assert.equal(caret.style.left,'59px');assert.equal(caret.style.top,'4px');
  assert.equal(caret.dataset.sourceStart,'5');assert.equal($('expression').selectionStart,5);
  calculator.insert('0');assert.equal($('expression').value,'sin(90)');
});

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

for(const keyboard of [false,true])test(`cosine equation paste avoids redundant fences in ${keyboard?'keyboard':'math'} input`,t=>{
  const {$,dom}=calculatorPage(t,'12+34');
  if(keyboard)$('typing-toggle').click();
  const event=new dom.window.Event('paste',{bubbles:true,cancelable:true});
  Object.defineProperty(event,'clipboardData',{value:{getData:()=>String.raw`$$\cos\left(\frac{\pi}{2} + \theta\right) = -\frac{1}{5}$$`}});
  (keyboard?$('expression'):document.body).dispatchEvent(event);
  assert.equal(event.defaultPrevented,true);
  assert.equal($('expression').value,'cos(pi/2+theta)=-1/5');
  assert.equal($('expression').selectionStart,$('expression').value.length);
  if(keyboard)$('typing-toggle').click();
  assert.equal($('expression-preview').querySelectorAll('mfrac').length,2);
  const negative=$('expression-preview').querySelectorAll('mfrac')[1];
  assert.equal(negative.children[0].textContent,'1');
  assert.equal(negative.previousElementSibling.textContent,'−');
  assert.deepEqual([...$('expression-preview').querySelectorAll('mo')].map(n=>n.textContent).filter(s=>s==='('||s===')'),['(',')']);
  $('undo').click();assert.equal($('expression').value,'12+34');
});

test('the minus before a fraction selects its sign and the numerator selects only its digits',t=>{
  const {$,dom,clickNumber}=calculatorPage(t,'-1/5');
  $('expression').setSelectionRange(4,4);
  const fraction=$('expression-preview').querySelector('mfrac'),sign=fraction.previousElementSibling;
  sign.dispatchEvent(new dom.window.MouseEvent('click',{bubbles:true}));
  assert.equal($('expression').selectionStart,0);assert.equal($('expression').selectionEnd,1);
  clickNumber('1');
  assert.equal($('expression').selectionStart,1);assert.equal($('expression').selectionEnd,2);
});

for(const overwrite of [false,true])test(`pasting a fraction into a denominator keeps its scope and caret (${overwrite?'overwrite':'selection'})`,t=>{
  const source=overwrite?'1/234+7':'1/2';
  const {$,dom}=calculatorPage(t,source);
  $('clear').click();
  $('expression').value=source;$('expression').setSelectionRange(2,overwrite?2:3);
  if(overwrite)$('insert-mode').click();
  $('typing-toggle').click();
  const event=new dom.window.Event('paste',{bubbles:true,cancelable:true});
  Object.defineProperty(event,'clipboardData',{value:{getData:()=>String.raw`$$\frac{1}{5}$$`}});
  $('expression').dispatchEvent(event);
  assert.equal($('expression').value,overwrite?'1/(1/5)+7':'1/(1/5)');
  assert.equal($('expression').selectionStart,7);
  $('undo').click();assert.equal($('expression').value,source);
});

for(const keyboard of [false,true])test(`LaTeX paste renders indexed roots and fractional powers in ${keyboard?'keyboard':'math'} input`,async t=>{
  const {$,dom,calculator,engine}=calculatorPage(t,'12+34');
  if(keyboard)$('typing-toggle').click();
  const event=new dom.window.Event('paste',{bubbles:true,cancelable:true});
  Object.defineProperty(event,'clipboardData',{value:{getData:()=>String.raw`$$\sqrt[3]{5} \times 25^{\frac{1}{3}}$$`}});
  (keyboard?$('expression'):document.body).dispatchEvent(event);
  const converted='nthroot(5,3)*25^(1/3)';
  assert.equal(event.defaultPrevented,true);
  assert.equal($('expression').value,converted);
  assert.equal($('expression').selectionStart,converted.length);
  if(keyboard)$('typing-toggle').click();
  assert.equal($('expression-preview').querySelectorAll('mroot').length,1);
  assert.equal($('expression-preview').querySelectorAll('msup').length,1);
  assert.equal($('expression-preview').querySelectorAll('mfrac').length,1);
  engine.ready=true;
  await calculator.evaluate();
  assert.deepEqual(engine.execute.mock.calls[0].arguments[0].tree,parse(converted));
  $('undo').click();
  assert.equal($('expression').value,'12+34');
});

test('wrapped LaTeX paste replaces a selected operand and keeps surrounding terms',t=>{
  const {$,dom,clickNumber}=calculatorPage(t,'1+2+3');
  clickNumber('2');
  $('typing-toggle').click();
  const event=new dom.window.Event('paste',{bubbles:true,cancelable:true});
  Object.defineProperty(event,'clipboardData',{value:{getData:()=>String.raw`$$\sqrt[3]{5} \times 25^{\frac{1}{3}}$$`}});
  $('expression').dispatchEvent(event);
  assert.equal(event.defaultPrevented,true);
  assert.equal($('expression').value,'1+nthroot(5,3)*25^(1/3)+3');
  assert.equal($('expression').selectionStart,$('expression').value.length-2);
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
