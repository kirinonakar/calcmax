import test from 'node:test';
import assert from 'node:assert/strict';
import {JSDOM} from 'jsdom';
import {mathDisplay} from '../math-display.js';

test('ENG and SCI normalize numeric results without losing high precision or changing exact structures',()=>{
  const dom=new JSDOM();globalThis.document=dom.window.document;
  const display=(value,notation,digits=10)=>mathDisplay({kind:'number',value},digits,true,{notation});
  assert.equal(display('12345','eng').textContent,'12.345×103');
  assert.equal(display('12345','sci').textContent,'1.2345×104');
  assert.equal(display('12345','off').textContent,'12345');
  assert.equal(display('-0.000123456','eng',3).textContent,'-123.456×10-6');
  assert.equal(display('-0.000123456','sci',3).textContent,'-1.235×10-4');
  assert.equal(display('12345678901234567890','sci',19).textContent,'1.234567890123456789×1019');
  assert.equal(display('1.25e-100','eng').textContent,'125×10-102');
  assert.equal(display('1.25e-100','sci').textContent,'1.25×10-100');
  assert.equal(display('0','sci').textContent,'0');
  const fraction={kind:'fraction',args:[{kind:'number',value:'12345'},{kind:'number',value:'7'}]},snapshot=JSON.stringify(fraction);
  assert.equal(mathDisplay(fraction,10,false,{notation:'sci'}).querySelectorAll('msup').length,0,'exact fraction leaves are not converted to powers');
  assert.equal(JSON.stringify(fraction),snapshot,'display formatting leaves the engine value intact');
  dom.window.close();
});
