import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {Worker as NodeWorker} from 'node:worker_threads';
import {JSDOM} from 'jsdom';
import {initialLanguage} from '../i18n.js';

async function waitFor(check,message,timeout=15000) {
  const deadline=Date.now()+timeout;
  while(!check()){if(Date.now()>deadline)throw new Error(`Timeout: ${message}; answer=${globalThis.document?.getElementById('answer')?.textContent}; status=${globalThis.document?.getElementById('status')?.textContent}`);await new Promise(resolve=>setTimeout(resolve,25));}
}
test('DOM workflows use the production Worker, real WASM, both languages, and themes',async t=>{
  const dom=new JSDOM(readFileSync(new URL('../index.html',import.meta.url),'utf8'),{url:'http://localhost/',pretendToBeVisual:true});
  const workers=[];
  class BrowserWorker {
    constructor(url,options){assert.equal(options?.type,'module','Pyodide requires a module Worker');assert.equal(url.pathname.endsWith('/worker.js'),true);this.node=new NodeWorker(new URL('./worker-bridge.mjs',import.meta.url));workers.push(this.node);this.node.on('message',data=>this.onmessage?.({data}));this.node.on('error',error=>this.onerror?.({message:error.message}));}
    postMessage(data){this.node.postMessage(data);}
    terminate(){this.node.terminate();}
  }
  const {window}=dom;
  assert.equal(window.document.documentElement.lang,'en','initial HTML is English before JavaScript initializes');
  assert.equal(window.document.getElementById('status').textContent,'Loading WebAssembly runtime…');
  Object.defineProperty(window.navigator,'language',{value:'ko-KR',configurable:true});
  for(const name of ['styles.css','calculator.css']){const style=window.document.createElement('style');style.textContent=readFileSync(new URL('../'+name,import.meta.url),'utf8');window.document.head.append(style);}
  window.matchMedia=()=>({matches:false,addEventListener(){}});
  window.HTMLDialogElement.prototype.showModal=function(){this.open=true;};
  window.HTMLDialogElement.prototype.close=function(){this.open=false;};
  for(const key of ['window','document','localStorage','location','NodeFilter','CustomEvent','EventTarget'])Object.defineProperty(globalThis,key,{value:key==='window'?window:window[key],configurable:true,writable:true});
  Object.defineProperty(globalThis,'navigator',{value:window.navigator,configurable:true});
  localStorage.setItem('calcmax-web-v1',JSON.stringify({language:'ko',fields:{}}));
  globalThis.Worker=BrowserWorker;
  globalThis.fetch=async path=>({ok:true,json:async()=>JSON.parse(readFileSync(new URL(path.replace('./','../'),import.meta.url),'utf8'))});
  const $=id=>window.document.getElementById(id);
  const change=(id,v)=>{$(id).value=v;$(id).dispatchEvent(new window.Event('change'));};
  t.after(async()=>{window.dispatchEvent(new window.Event('pagehide'));await Promise.all(workers.map(w=>w.terminate()));window.close();});
  await import('../app.js');
  await waitFor(()=>$('keypad').childElementCount>0,'app initialization');
  const area=selector=>window.getComputedStyle(document.querySelector(selector)).gridArea;
  assert.equal(area('main'),'main','calculation area is assigned to the flexible main track');
  assert.equal(area('header'),'header');assert.equal(area('.mode-bar'),'mode');assert.equal(area('.runtime-bar'),'runtime');
  await waitFor(()=>!document.querySelector('.key[data-evaluate]').disabled,'WASM readiness');
  assert.equal(area('main'),'main','hiding the loading row cannot auto-place main in the runtime track');
  assert.equal(window.getComputedStyle(document.querySelector('.runtime-bar')).display,'none');
  assert.equal(document.documentElement.lang,'ko');
  assert.equal($('stop').textContent,'중지');
  assert.equal($('language').value,'ko');
  $('settings-button').click();$('language').value='en';$('language').dispatchEvent(new window.Event('change'));$('settings-close').click();
  assert.equal(document.documentElement.lang,'en');
  assert.equal($('stop').textContent,'Stop');
  assert.equal(initialLanguage(JSON.parse(localStorage.getItem('calcmax-web-v1')),'ko-KR'),'en','explicit English choice overrides the Korean browser');
  assert.equal($('graph-kind').value,'cartesian');
  assert.equal(document.documentElement.dataset.workspace,'scientific');
  assert.equal($('keypad').querySelectorAll('.numeric-row').length,4);
  assert.ok(Array.from($('keypad').querySelectorAll('.numeric-row')).every(row=>row.children.length===5));
  assert.ok(Array.from($('keypad').querySelectorAll('.scientific-row')).every(row=>row.children.length===6));
  assert.equal($('keypad').querySelectorAll('button').length,50);
  assert.equal(window.getComputedStyle(document.body).overflow,'hidden');
  const key=input=>$('keypad').querySelector(`[data-input="${input}"]`);
  key('SECOND').click();assert.equal($('keypad').dataset.page,'2');assert.ok(key('factor()'));assert.equal(key('SECOND').textContent,'1st');
  key('factor()').click();assert.equal($('expression').value,'factor()');assert.ok($('expression-preview').querySelector('.input-slot'));
  key('AC').click();key('SECOND').click();assert.equal($('keypad').dataset.page,'1');
  const longClick=async input=>{const button=key(input);button.dispatchEvent(new window.MouseEvent('pointerdown',{bubbles:true,button:0}));await new Promise(resolve=>setTimeout(resolve,550));button.dispatchEvent(new window.MouseEvent('pointerup',{bubbles:true,button:0}));button.click();};
  await longClick('sin()');assert.equal($('expression').value,'asin()');
  key('AC').click();await longClick('SHIFT');assert.equal(key('SHIFT').getAttribute('aria-pressed'),'true');key('cos()').click();assert.equal($('expression').value,'acos()');assert.equal(key('SHIFT').getAttribute('aria-pressed'),'false');
  key('AC').click();key('ALPHA').click();key('log()').click();assert.equal($('expression').value,'n');key('AC').click();
  $('screen-toggle').click();assert.equal(window.getComputedStyle($('keypad').querySelector('.scientific-row')).display,'none');$('screen-toggle').click();assert.notEqual(window.getComputedStyle($('keypad').querySelector('.scientific-row')).display,'none');
  $('settings-button').click();assert.ok($('settings-dialog').open);assert.equal($('language').closest('dialog').id,'settings-dialog');$('settings-close').click();
  const code=$('python-source').value;
  change('language','ko');assert.equal(document.documentElement.lang,'ko');assert.equal($('history-button').textContent,'기록');assert.equal($('stop').textContent,'중지');assert.equal(initialLanguage(JSON.parse(localStorage.getItem('calcmax-web-v1'))),'ko');
  change('language','en');assert.equal($('stop').textContent,'Stop');assert.equal($('python-source').value,code);assert.equal($('graph-kind').value,'cartesian');
  const walker=document.createTreeWalker(document.body,window.NodeFilter.SHOW_TEXT),untranslated=[];
  while(walker.nextNode()){const node=walker.currentNode;if(!node.parentElement.closest('textarea,code,pre,#language')&&/[가-힣]/.test(node.textContent))untranslated.push(node.textContent);}
  assert.deepEqual(untranslated,[],'all static UI text is translated to English');
  change('theme','dark');assert.equal(document.documentElement.dataset.theme,'dark');
  change('theme','light');assert.equal(document.documentElement.dataset.theme,'light');
  $('expression').value='1/3+1/6';document.querySelector('.key[data-evaluate]').click();
  assert.equal(document.documentElement.dataset.busy,'true');
  assert.equal(window.getComputedStyle(document.querySelector('.runtime-bar')).display,'none','ready status must stay hidden while calculating');
  assert.equal($('stop').closest('.answer-toolbar')!==null,true,'stop remains available without reopening the top status row');
  await waitFor(()=>$('answer').textContent==='12','exact fraction');
  assert.equal(area('main'),'main','calculation busy/idle transitions keep the same main track');
  assert.equal($('answer').querySelector('mfrac')?.children.length,2);
  $('exact-toggle').click();assert.match($('answer').textContent,/0.5/);$('exact-toggle').click();
  key('-').click();key('1').click();assert.equal($('expression').value,'Ans-1');key('=').click();await waitFor(()=>$('answer').textContent==='-12','subtraction continues the previous answer');
  change('mode','equation');document.querySelector('[data-run="equation"]').click();await waitFor(()=>$('answer').textContent.includes('2,3'),'equation solutions');
  change('mode','graph');document.querySelector('[data-run="graph"]').click();await waitFor(()=>$('graph-plot').querySelectorAll('path').length===2,'graph SVG');assert.ok($('graph-table').querySelectorAll('tbody tr').length>10);
  change('mode','python');$('python-source').value='name=input("Name: ")\nprint("Hello",name)';$('python-input').value='CalcMax';document.querySelector('[data-run="python"]').click();await waitFor(()=>$('python-output').textContent.includes('Hello CalcMax'),'Python input bridge');
  change('mode','matrix');document.querySelector('[data-run="matrix"]').click();await waitFor(()=>$('answer').textContent==='1','identity determinant');
  change('mode','vector');change('matrix-op','dot');document.querySelector('[data-run="matrix"]').click();await waitFor(()=>$('answer').textContent==='32','dot product');
  change('mode','statistics');change('statistics-op','mean');document.querySelector('[data-run="statistics"]').click();await waitFor(()=>$('answer').querySelector('mfrac'),'mean fraction');assert.equal($('answer').textContent,'52');
  change('mode','programmer');document.querySelector('[data-run="programmer"]').click();await waitFor(()=>$('programmer-output').textContent.includes('HEX  000000FF'),'programmer bases');
  change('mode','units');document.querySelector('[data-run="units"]').click();await waitFor(()=>$('answer').textContent==='0','temperature conversion');
  change('mode','tip');document.querySelector('[data-run="tip"]').click();await waitFor(()=>$('answer').querySelector('mfrac'),'tip exact result');assert.equal($('answer').textContent,'1152');
  change('mode','functions');$('function-save').click();change('mode','scientific');$('expression').value='f(3)';document.querySelector('.key[data-evaluate]').click();await waitFor(()=>$('answer').textContent==='10','saved function');
  const edit=source=>{$('expression').value=source;$('expression').dispatchEvent(new window.Event('input'));};
  edit('x^2+y');key('CALC').click();assert.equal(document.documentElement.dataset.calcActive,'true');assert.equal($('commit-indicator').textContent,'x?');
  assert.equal(JSON.parse(localStorage.getItem('calcmax-web-v1')).fields.expression,'x^2+y','backup preserves the formula while entering CALC values');
  edit('z');key('CALC').click();await waitFor(()=>$('answer').textContent==='Enter a numeric value','CALC rejects symbolic values');assert.equal($('commit-indicator').textContent,'x?');
  edit('3');key('=').click();assert.equal(window.getComputedStyle(document.querySelector('.runtime-bar')).display,'none');await waitFor(()=>$('commit-indicator').textContent==='y?','CALC advances to the next variable');assert.equal($('answer').querySelector('.error'),null,'accepted CALC values clear the preceding validation error');
  edit('1/2');key('CALC').click();await waitFor(()=>!document.documentElement.dataset.calcActive&&$('answer').textContent==='192','CALC substitutes variables with exact values');assert.equal($('expression').value,'x^2+y');assert.match($('note').textContent,/x = 3, y = 1\/2/);
  key('CALC').click();key('=').click();await waitFor(()=>$('commit-indicator').textContent==='y?','blank CALC entry recalls stored x');key('=').click();await waitFor(()=>!document.documentElement.dataset.calcActive&&$('answer').textContent==='192','blank CALC entry recalls stored y');
  edit('x+1');key('CALC').click();key('4').click();key('AC').click();assert.equal($('expression').value,'x+1');assert.equal(document.documentElement.dataset.calcActive,undefined,'AC cancels CALC without losing the formula');
  edit('integrate(x^2,x,0,1)');key('CALC').click();assert.equal(document.documentElement.dataset.calcActive,undefined,'integration variable is not a CALC input');await waitFor(()=>$('answer').textContent==='13','CALC evaluates expressions with no free inputs');
  $('history-button').click();assert.ok($('dialog').open);assert.ok($('dialog-body').textContent.includes('f(3)'));$('dialog-close').click();
  $('catalog-button').click();assert.ok($('dialog-body').textContent.includes('sin()'));$('dialog-close').click();
  // Non-cooperative Python cannot freeze the page; hard cancellation restores WASM.
  change('mode','python');assert.ok($('stop').closest('.runtime-bar'),'Python stop stays at the top of the workspace');$('python-source').value='while True: pass';document.querySelector('[data-run="python"]').click();await waitFor(()=>!$('stop').disabled,'script running');$('stop').click();await waitFor(()=>$('python-output').textContent.includes('cancelled'),'hard cancellation');await waitFor(()=>!document.querySelector('[data-run="python"]').disabled,'engine recovery');
  $('python-source').value='print(42)';document.querySelector('[data-run="python"]').click();await waitFor(()=>$('python-output').textContent==='42\n','post-cancellation script');
  const persisted=JSON.parse(localStorage.getItem('calcmax-web-v1'));assert.equal(persisted.language,'en');assert.equal(persisted.theme,'light');assert.ok(persisted.history.length>=8);assert.ok(persisted.functions.f);
});
