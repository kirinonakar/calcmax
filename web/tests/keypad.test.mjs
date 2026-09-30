import test from 'node:test';
import assert from 'node:assert/strict';
import {JSDOM} from 'jsdom';
import {bindKeyPress,renderKeypad,updateKeypadState,scientificRows,secondRows,numericRows,topKeys,topFunctions} from '../keypad.js';
import {expressionDisplay} from '../expression-display.js';

test('both keypad pages retain Android row layout and shifted/alpha operations',()=>{
  assert.deepEqual(scientificRows.map(row=>row.map(k=>k.title)),[['a/b','√','x²','x□','log','ln'],['(−)','°′″','hyp','sin','cos','tan'],['RCL','ENG','(',')','S⇔D','M+']]);
  assert.deepEqual(secondRows.map(row=>row.map(k=>k.title)),[['simp','factor','expand','x','y','z'],['⌊x⌋','⌈x⌉','∞',',','{','}'],['MATRIX','det','inv','T','‖v‖','GRAPH']]);
  assert.deepEqual(numericRows.map(row=>row.map(k=>k.title)),[['7','8','9','DEL','AC'],['4','5','6','×','÷'],['1','2','3','+','−'],['0','.','×10ˣ','Ans','=']]);
  assert.equal(topKeys(false)[3].title,'2nd');assert.equal(topKeys(true)[3].title,'1st');
  assert.deepEqual(topFunctions(true).map(k=>k.title),['d/dx','lim','sinc','Π']);
  assert.equal(scientificRows[1][3].alternate,'asin()');assert.equal(scientificRows[1][3].alpha,'D');
});

test('long press invokes only the shifted action; release does not also type the base key',()=>{
  const dom=new JSDOM('<button></button>'),button=dom.window.document.querySelector('button');
  let pending,short=0,long=0;
  const schedule=callback=>{pending=callback;return 1;},cancel=()=>{pending=null;};
  bindKeyPress(button,()=>short++,()=>long++,{schedule,cancel});
  const pointer=(type,x=0)=>button.dispatchEvent(new dom.window.MouseEvent(type,{clientX:x,clientY:0,button:0,bubbles:true}));
  pointer('pointerdown');pointer('pointerup');button.click();assert.equal(short,1);assert.equal(long,0);
  pointer('pointerdown');pending();pointer('pointerup');button.click();assert.equal(short,1);assert.equal(long,1);
  // A normal keyboard/mouse click still works after the long click was consumed.
  button.click();assert.equal(short,2);
  pointer('pointerdown');pointer('pointercancel');assert.equal(pending,null);button.click();assert.equal(short,2);
  pointer('pointerdown');pointer('pointermove',20);assert.equal(pending,null);
  button.disabled=true;pointer('pointerdown');assert.equal(pending,null);
  dom.window.close();
});

test('both keypad pages show the backspace icon and preserve DEL and shifted INS actions',()=>{
  const dom=new JSDOM('<div id="keypad"></div>');globalThis.document=dom.window.document;
  for(const second of [false,true]){
    let action;const container=document.getElementById('keypad');renderKeypad(container,{second,press:key=>action=key.input,longPress:key=>action=key.alternate});
    const button=container.querySelector('[data-input="DEL"]');assert.equal(button.getAttribute('aria-label'),'DEL');assert.equal(button.querySelector('.key-face').textContent,'');assert.ok(button.querySelector('.key-face svg[aria-hidden="true"]'));
    button.click();assert.equal(action,'DEL');assert.equal(button.querySelector('.key-hints').textContent,'INS');
    updateKeypadState(container,{second,shift:true});assert.equal(button.getAttribute('aria-label'),'INS');assert.ok(button.querySelector('.key-backspace'));
  }
  dom.window.close();
});

test('natural source display keeps fractions, calculus bounds, and incomplete slots',()=>{
  const dom=new JSDOM();globalThis.document=dom.window.document;
  assert.equal(expressionDisplay('1/3+1/6').querySelectorAll('mfrac').length,2);
  const integral=expressionDisplay('integrate(exp(-x^2)*cos(2x),(x,0,oo))');
  assert.ok(integral.querySelector('msubsup'));assert.match(integral.textContent,/∫0∞/);
  const slots=expressionDisplay('integrate(,x,,)');assert.equal(slots.querySelectorAll('.input-slot').length,3);
  assert.ok(expressionDisplay('sqrt(').querySelector('.input-slot'));
  assert.equal(expressionDisplay('1/(2+x)').querySelector('mfrac').getAttribute('data-source-start'),'0');
  dom.window.close();
});
