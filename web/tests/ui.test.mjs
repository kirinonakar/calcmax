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
  let offlineRegistrations=0;
  Object.defineProperty(window.navigator,'serviceWorker',{value:{
    register:async(url,options)=>{
      assert.equal(window.document.documentElement.dataset.engine,'ready','offline precaching must wait for WASM/SymPy readiness');
      assert.equal(url,'./sw.js');assert.equal(options.updateViaCache,'none');
      offlineRegistrations++;
      return {active:true,addEventListener(){}};
    }
  }});
  for(const name of ['styles.css','calculator.css']){const style=window.document.createElement('style');style.textContent=readFileSync(new URL('../'+name,import.meta.url),'utf8');window.document.head.append(style);}
  window.matchMedia=()=>({matches:false,addEventListener(){}});
  window.HTMLDialogElement.prototype.showModal=function(){this.open=true;};
  window.HTMLDialogElement.prototype.close=function(){this.open=false;};
  for(const key of ['window','document','localStorage','location','NodeFilter','CustomEvent','EventTarget'])Object.defineProperty(globalThis,key,{value:key==='window'?window:window[key],configurable:true,writable:true});
  Object.defineProperty(globalThis,'navigator',{value:window.navigator,configurable:true});
  localStorage.setItem('calcmax-web-v1',JSON.stringify({language:'ko',fields:{}}));
  globalThis.Worker=BrowserWorker;
  globalThis.fetch=async path=>new Response(readFileSync(new URL(path.replace('./','../'),import.meta.url),'utf8'));
  const $=id=>window.document.getElementById(id);
  const change=(id,v)=>{$(id).value=v;$(id).dispatchEvent(new window.Event('change'));};
  t.after(async()=>{window.dispatchEvent(new window.Event('pagehide'));await Promise.all(workers.map(w=>w.terminate()));window.close();});
  await import('../app.js');
  await waitFor(()=>$('keypad').childElementCount>0,'app initialization');
  assert.equal(offlineRegistrations,0,'cold startup does not download the entire offline runtime concurrently');
  const area=selector=>window.getComputedStyle(document.querySelector(selector)).gridArea;
  assert.equal(area('main'),'main','calculation area is assigned to the flexible main track');
  assert.equal(area('header'),'header');assert.equal(area('.mode-bar'),'mode');assert.equal(area('.runtime-bar'),'runtime');
  await waitFor(()=>!document.querySelector('.key[data-evaluate]').disabled,'WASM readiness');
  assert.equal(offlineRegistrations,1);
  await waitFor(()=>$('offline-status').textContent==='오프라인 사용 가능','offline installation after readiness');
  assert.equal(area('main'),'main','hiding the loading row cannot auto-place main in the runtime track');
  assert.equal(window.getComputedStyle(document.querySelector('.runtime-bar')).display,'none');
  assert.equal(document.documentElement.lang,'ko');
  assert.equal($('stop').textContent,'중지');
  assert.equal($('language').value,'ko');
  $('about-button').click();assert.ok($('dialog').open);assert.equal($('dialog-title').textContent,'CalcMax');assert.equal($('dialog-body').querySelector('button').textContent,'닫기');assert.match($('dialog-body').textContent,/v1\.1\.9/);assert.equal($('dialog-body').querySelector('a').href,'https://github.com/kirinonakar/calcmax');assert.equal($('dialog-body').querySelector('img').width,64);$('dialog-body').querySelector('button').click();assert.equal($('dialog').open,false);
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
  assert.equal(window.getComputedStyle($('tape-active')).justifyContent,'flex-start','current input is aligned to the top');
  const checkArrowFaces=()=>{
    for(const button of $('keypad').querySelectorAll('.direction-key')){
      const face=window.getComputedStyle(button.querySelector('.key-face'));
      assert.equal(face.backgroundColor,'rgba(0, 0, 0, 0)','arrows have no inner horizontal background strip');
      assert.ok(['','none'].includes(face.borderTopStyle),'arrows have no nested key-face border');
      assert.equal(face.borderRadius,'','arrows do not inherit the rectangular key face');
      assert.equal(window.getComputedStyle(button).justifyContent,'center');
    }
    assert.equal(window.getComputedStyle($('keypad').querySelector('.numeric .key-face')).borderRadius,'7px 7px 4px 4px','normal key faces retain their shape');
  };
  checkArrowFaces();
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
  checkArrowFaces();
  change('theme','light');assert.equal(document.documentElement.dataset.theme,'light');
  checkArrowFaces();
  const toolbar=$('exact-toggle').parentElement,toolbarMarkup=toolbar.innerHTML,toolbarChildren=Array.from(toolbar.children);
  toolbar.scrollLeft=25;
  $('expression').value='1/3+1/6';document.querySelector('.key[data-evaluate]').click();
  assert.equal(document.documentElement.dataset.busy,'true');
  assert.equal(window.getComputedStyle(document.querySelector('.runtime-bar')).display,'none','ready status must stay hidden while calculating');
  assert.equal($('stop').closest('.edit-actions')!==null,true,'stop stays in a reserved editing-row slot');
  assert.equal($('stop').hidden,false);assert.equal($('stop').style.visibility,'');
  assert.equal(toolbar.innerHTML,toolbarMarkup,'busy state leaves the Exact toolbar unchanged');
  await waitFor(()=>$('answer').textContent==='12','exact fraction');
  assert.equal(area('main'),'main','calculation busy/idle transitions keep the same main track');
  assert.equal($('answer').querySelector('mfrac')?.children.length,2);
  assert.equal(toolbar.innerHTML,toolbarMarkup,'completion leaves the Exact toolbar unchanged');
  assert.deepEqual(Array.from(toolbar.children),toolbarChildren,'buttons retain their DOM identity');
  assert.equal(toolbar.scrollLeft,25,'toolbar scrolling is preserved');
  assert.equal($('stop').hidden,false);assert.equal($('stop').style.visibility,'hidden','idle stop keeps its space without shifting controls');
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
  change('mode','tip');document.querySelector('[data-run="tip"]').click();await waitFor(()=>$('answer').textContent.includes('Per person: 58'),'whole amount tip split');assert.match($('answer').textContent,/Total: 116/);assert.match($('answer').textContent,/Tip: 16/);
  assert.match($('answer').textContent,/Tip %: 16\.00%/);
  assert.equal($('tip-percent').disabled,false);assert.equal($('tip-fixed').disabled,true);
  change('tip-method','amount');assert.equal($('tip-percent').disabled,true);assert.equal($('tip-fixed').disabled,false);
  $('tip-percent').value='invalid inactive value';$('tip-fixed').value='20';document.querySelector('[data-run="tip"]').click();await waitFor(()=>$('answer').textContent.includes('Tip %: 20.00%'),'fixed tip ignores disabled percent input');
  $('tip-percent').value='15';change('tip-method','percent');assert.equal($('tip-percent').disabled,false);assert.equal($('tip-fixed').disabled,true);
  $('tip-fixed').value='';document.querySelector('[data-run="tip"]').click();await waitFor(()=>$('answer').textContent.includes('Tip %: 16.00%'),'percent tip ignores disabled amount input and reports adjusted rate');$('tip-fixed').value='15';
  change('mode','functions');$('function-save').click();change('mode','scientific');$('expression').value='f(3)';document.querySelector('.key[data-evaluate]').click();await waitFor(()=>$('answer').textContent==='10','saved function');
  const edit=source=>{$('expression').value=source;$('expression').dispatchEvent(new window.Event('input'));};
  key('AC').click();key('8').click();key('*').click();key('sqrt()').click();key('2').click();key('^2').click();
  assert.equal($('expression').value,'8*sqrt(2^2)','power key stays inside the radical at the current cursor');
  assert.ok($('expression-preview').querySelector('msqrt msup'),'root roof contains the complete power');key('=').click();await waitFor(()=>$('answer').textContent==='16','root with an internal exponent computes correctly');
  edit('sqrt(2)');$('expression').setSelectionRange(7,7);key('^2').click();assert.equal($('expression').value,'sqrt(2)^2','placing the cursor after the root still squares the whole root');
  edit('sqrt(2)');$('expression').setSelectionRange(6,6);key('^()').click();assert.equal($('expression').value,'sqrt(2^())');assert.ok($('expression-preview').querySelector('msqrt msup .input-slot'),'editable exponent stays under the root roof');
  assert.ok($('mode').closest('.mode-picker'),'mode dropdown has an explicit arrow container');
  edit('7+8');key('8').focus();
  const enter=new window.KeyboardEvent('keydown',{key:'Enter',bubbles:true,cancelable:true});key('8').dispatchEvent(enter);
  assert.equal(enter.defaultPrevented,true);await waitFor(()=>$('answer').textContent==='15','Enter evaluates with focus on a numeric key');assert.equal($('expression').value,'7+8');
  edit('B=5');$('expression').dispatchEvent(new window.KeyboardEvent('keydown',{key:'Enter',bubbles:true,cancelable:true}));await waitFor(()=>$('note').textContent.includes('Stored in B'),'inline STO');
  assert.equal(JSON.parse(localStorage.getItem('calcmax-web-v1')).variables.B.value,'5');
  edit('B+2');key('=').click();await waitFor(()=>$('answer').textContent==='7','recall inline STO');
  edit('B=B+1');key('=').click();await waitFor(()=>$('note').textContent.includes('Stored in B'),'self-referential STO');
  edit('B');key('=').click();await waitFor(()=>$('answer').textContent==='6','STO freezes its previous value');
  change('mode','graph');await waitFor(()=>!document.documentElement.dataset.busy.includes('true'),'graph idle');
  change('graph-selected','1');assert.equal($('graph-plot').querySelector('path[data-curve="1"]').getAttribute('stroke-width'),'4');assert.equal($('graph-plot').querySelector('path[data-curve="0"]').getAttribute('stroke-width'),'2');
  $('graph-formulas').children[0].click();assert.equal($('graph-selected').value,'0');assert.equal($('graph-plot').querySelector('path[data-curve="0"]').getAttribute('stroke-width'),'4');
  change('mode','statistics');assert.equal($('statistics-op').querySelector('[value="regression"]'),null);assert.ok($('regression-kind').closest('#regression-section'));
  document.querySelector('[data-run="regression"]').click();await waitFor(()=>$('regression-caption').textContent.includes('y='),'independent regression action');assert.equal($('statistics-op').value,'mean');assert.equal($('regression-transfer').hidden,false);
  $('regression-clear').click();assert.equal($('regression-caption').textContent,'');assert.equal($('regression-transfer').hidden,true);
  change('mode','scientific');
  const shifted=input=>{key('SHIFT').click();key(input).click();};
  key('AC').click();shifted('+');shifted('-');assert.equal($('expression').value,'pi*e');
  key('=').click();await waitFor(()=>/(?:π|pi)/.test($('answer').textContent)&&!$('answer').querySelector('.error'),'pi times Euler constant');
  edit('2');key('=').click();await waitFor(()=>$('answer').textContent==='2','Ans multiplication seed');
  key('AC').click();key('Ans').click();shifted('+');assert.equal($('expression').value,'Ans*pi');
  key('=').click();await waitFor(()=>$('answer').textContent.includes('2')&&/(?:π|pi)/.test($('answer').textContent),'Ans times pi uses the saved answer');
  key('AC').click();shifted('-');shifted('+');assert.equal($('expression').value,'e*pi','reverse constant order also stays separate');
  key('AC').click();shifted('+');key('2').click();assert.equal($('expression').value,'pi*2','digits cannot become part of a constant name');
  key('AC').click();key('1').click();key('2').click();assert.equal($('expression').value,'12','numeric entry remains contiguous');
  edit('pie');assert.equal($('expression-preview').querySelectorAll('mi').length,1,'typed variable names are preserved');
  edit('pi');$('expression').setSelectionRange(0,0);key('Ans').click();assert.equal($('expression').value,'Ans*pi','insertion before a constant separates both operands');
  assert.equal($('expression').selectionStart,3,'cursor stays after the inserted operand');
  edit('pi');$('expression').setSelectionRange(0,2);shifted('-');assert.equal($('expression').value,'e','selected constant is replaced without an extra operator');
  edit('12345');key('=').click();await waitFor(()=>$('answer').textContent==='12345','notation seed');
  const notation=$('engineering-toggle');assert.equal(notation.dataset.notation,'off');
  notation.click();assert.equal(notation.dataset.notation,'eng');assert.equal(notation.textContent,'ENG');assert.ok(notation.classList.contains('active'));assert.equal($('answer').textContent,'12.345×103');
  notation.click();assert.equal(notation.dataset.notation,'sci');assert.equal(notation.textContent,'SCI');assert.equal($('answer').textContent,'1.2345×104');
  assert.equal(JSON.parse(localStorage.getItem('calcmax-web-v1')).resultDisplayMode,'sci','notation preference is saved');
  assert.match($('tape-history').lastElementChild.querySelector('.tape-result').textContent,/2.*(?:π|pi)/,'notation must not alter older exact symbolic expressions');
  edit('1/3');key('=').click();await waitFor(()=>$('answer').querySelector('mfrac'),'SCI preserves the Exact fraction');
  assert.equal($('tape-history').lastElementChild.querySelector('.tape-result').textContent,'1.2345×104','history uses the same notation');
  notation.click();assert.equal(notation.dataset.notation,'off');assert.equal(notation.textContent,'ENG');assert.equal(notation.classList.contains('active'),false);assert.ok($('answer').querySelector('mfrac'));assert.equal($('tape-history').lastElementChild.querySelector('.tape-result').textContent,'12345');
  edit('x^2+y');key('CALC').click();assert.equal(document.documentElement.dataset.calcActive,'true');assert.equal($('commit-indicator').textContent,'x?');
  assert.equal(JSON.parse(localStorage.getItem('calcmax-web-v1')).fields.expression,'x^2+y','backup preserves the formula while entering CALC values');
  edit('z');key('CALC').click();await waitFor(()=>$('answer').textContent==='Enter a numeric value','CALC rejects symbolic values');assert.equal($('commit-indicator').textContent,'x?');
  edit('3');key('=').click();assert.equal(window.getComputedStyle(document.querySelector('.runtime-bar')).display,'none');await waitFor(()=>$('commit-indicator').textContent==='y?','CALC advances to the next variable');assert.equal($('answer').querySelector('.error'),null,'accepted CALC values clear the preceding validation error');
  edit('1/2');key('CALC').click();await waitFor(()=>!document.documentElement.dataset.calcActive&&$('answer').textContent==='192','CALC substitutes variables with exact values');assert.equal($('expression').value,'x^2+y');assert.match($('note').textContent,/x = 3, y = 1\/2/);
  key('CALC').click();key('=').click();await waitFor(()=>$('commit-indicator').textContent==='y?','blank CALC entry recalls stored x');key('=').click();await waitFor(()=>!document.documentElement.dataset.calcActive&&$('answer').textContent==='192','blank CALC entry recalls stored y');
  edit('x+1');key('CALC').click();key('4').click();key('AC').click();assert.equal($('expression').value,'x+1');assert.equal(document.documentElement.dataset.calcActive,undefined,'AC cancels CALC without losing the formula');
  edit('integrate(x^2,x,0,1)');key('CALC').click();assert.equal(document.documentElement.dataset.calcActive,undefined,'integration variable is not a CALC input');await waitFor(()=>$('answer').textContent==='13','CALC evaluates expressions with no free inputs');
  await t.test('stored C=A+B prompts A and B even when they already have numeric values',async()=>{
    for(const assignment of ['A=2','B=3','C=A+B']){
      edit(assignment);key('=').click();await waitFor(()=>document.documentElement.dataset.busy==='false'&&$('note').textContent.includes('Stored in'),'assignment stored');
    }
    edit('C');key('CALC').click();assert.equal($('commit-indicator').textContent,'A?');
    edit('4');key('=').click();await waitFor(()=>$('commit-indicator').textContent==='B?','stored formula second input');
    edit('6');key('CALC').click();await waitFor(()=>!document.documentElement.dataset.calcActive&&$('answer').textContent==='10','stored formula substitution');
    assert.equal($('expression').value,'C');assert.match($('note').textContent,/A = 4, B = 6/);
    key('CALC').click();assert.equal($('commit-indicator').textContent,'A?','the formula survives CALC');key('AC').click();
    key('RCL').click();const fields=$('dialog-body').querySelectorAll('input');fields[0].value='D';fields[1].value='A+B';
    Array.from($('dialog-body').querySelectorAll('button')).find(button=>button.textContent==='Store expression').click();$('dialog').close();
    edit('D');key('CALC').click();assert.equal($('commit-indicator').textContent,'A?');key('AC').click();
    edit('P+Q');key('=').click();await waitFor(()=>document.documentElement.dataset.busy==='false'&&$('answer').textContent==='P+Q','symbolic result for STO');
    key('RCL').click();$('dialog-body').querySelector('input').value='E';
    Array.from($('dialog-body').querySelectorAll('button')).find(button=>button.textContent==='STO current result').click();$('dialog').close();
    edit('E');key('CALC').click();assert.equal($('commit-indicator').textContent,'P?');edit('4');key('=').click();await waitFor(()=>$('commit-indicator').textContent==='Q?','result AST input');
    edit('5');key('=').click();await waitFor(()=>!document.documentElement.dataset.calcActive&&$('answer').textContent==='9','result AST CALC');
    assert.equal(JSON.parse(localStorage.getItem('calcmax-web-v1')).variables.E.args[0].kind,'snapshot_symbol','CALC leaves the saved STO result intact');
  });
  await t.test('math input pastes, closes missing parentheses, and shows root cursor movement',async()=>{
    key('AC').click();const event=new window.Event('paste',{bubbles:true,cancelable:true});Object.defineProperty(event,'clipboardData',{value:{getData:()=> '4+sqrt(5'}});$('expression-preview').dispatchEvent(event);
    assert.equal(event.defaultPrevented,true);assert.equal($('expression').value,'4+sqrt(5');assert.ok($('expression-preview').querySelector('.input-caret'));
    key('=').click();await waitFor(()=>$('answer').querySelector('msqrt'),'auto-closed radical answer');assert.equal($('expression').value,'4+sqrt(5)');assert.ok($('answer').textContent.includes('4')&&$('answer').textContent.includes('5'));
    edit('4+sqrt(5)');$('expression').setSelectionRange(7,7);key('LEFT').click();assert.equal($('expression').selectionStart,2);assert.equal($('expression-preview').querySelector('.input-caret').getAttribute('data-source-start'),'2');
    key('RIGHT').click();assert.equal($('expression').selectionStart,7);$('expression').setSelectionRange(8,8);key('RIGHT').click();assert.equal($('expression').selectionStart,9);
  });
  await t.test('equations show the original formula above answers and Stop preserves the header space',async()=>{
    change('mode','equation');change('equation-kind','solve');change('equation-form','2');$('equation-a').value='1';$('equation-b').value='-5';$('equation-c').value='6';$('equation-variable').value='x';
    assert.equal($('stop').hidden,false);assert.equal($('stop').style.visibility,'hidden');const stopParent=$('stop').parentElement;
    document.querySelector('[data-run="equation"]').click();assert.equal($('stop').hidden,false);assert.equal($('stop').parentElement,stopParent);
    await waitFor(()=>$('answer').textContent.includes('2,3'),'coefficient quadratic');assert.equal($('stop').style.visibility,'hidden');
    assert.ok($('result-source').querySelector('msup'));assert.ok($('result-source').compareDocumentPosition($('answer'))&window.Node.DOCUMENT_POSITION_FOLLOWING);assert.doesNotMatch($('result-source').textContent,/solve/);
    change('equation-kind','dsolve');$('equation-initial').value='y(0)=1';document.querySelector('[data-run="equation"]').click();await waitFor(()=>$('answer').querySelector('msup')&&$('answer').textContent.includes('e'),'ODE initial condition');
    change('equation-kind','solve');change('equation-form','general');
  });
  await t.test('display digits apply to statistical rows, distributions, matrices, and graph coordinates without losing exact values',async()=>{
    $('settings-button').click();const digits=document.querySelector('[data-setting="digits"]');digits.value='3';digits.dispatchEvent(new window.Event('change'));$('settings-close').click();
    change('mode','statistics');assert.equal($('statistics-op-math'),null,'no expression sits beside the Analyze dropdown');$('statistics-data').value='1\n2\n4';change('statistics-op','stats');document.querySelector('[data-run="statistics"]').click();await waitFor(()=>$('answer').querySelector('mtable'),'MathML statistics rows');
    $('exact-toggle').click();for(const number of $('answer').querySelectorAll('mn')){const match=/\.([0-9]+)(?:e|$)/i.exec(number.textContent);if(match)assert.ok(match[1].length<=3,number.textContent);}
    $('exact-toggle').click();change('distribution-family','normal');change('distribution-query','pdf');$('distribution-x').value='1';document.querySelector('[data-run="distribution"]').click();await waitFor(()=>$('answer').querySelector('mfrac')&&!document.querySelector('[data-run="distribution"]').disabled,'exact normal density');$('exact-toggle').click();assert.equal($('answer').textContent,'0.242');$('exact-toggle').click();assert.ok($('distribution-math').querySelector('math'));
    change('mode','matrix');$('matrix-name').value='B';$('matrix-store').click();await waitFor(()=>JSON.parse(localStorage.getItem('calcmax-web-v1')).variables.B?.kind==='list','matrix storage replaces the numeric variable');$('matrix-clear').click();$('matrix-load').click();assert.equal($('matrix-grid').querySelector('input').value,'1');
  });
  await t.test('graph controls connect all Android analysis operations and ranges/sliders stay below the plot',async()=>{
    change('mode','graph');$('graph-source').value='x^2-1\nx';$('graph-source').dispatchEvent(new window.Event('input'));for(const [id,number] of [['graph-min','-2'],['graph-max','2'],['graph-ymin','-2'],['graph-ymax','4']]){$(id).value=number;$(id).dispatchEvent(new window.Event('change'));}
    document.querySelector('[data-run="graph"]').click();await waitFor(()=>$('graph-plot').querySelectorAll('path').length===2&&!$('graph-analysis-run').disabled,'two Cartesian curves');
    assert.ok($('graph-formulas').querySelector('msup'));assert.ok($('graph-plot').compareDocumentPosition($('graph-ranges'))&window.Node.DOCUMENT_POSITION_FOLLOWING);
    $('graph-min-slider').value='-1.234567';$('graph-min-slider').dispatchEvent(new window.Event('input'));assert.equal($('graph-min').value,'-1.235');$('graph-min-slider').dispatchEvent(new window.Event('change'));await waitFor(()=>!$('graph-analysis-run').disabled,'range slider sampling');
    assert.equal(JSON.parse(localStorage.getItem('calcmax-web-v1')).graph.ranges['graph-min'],-1.234567,'rounded range keeps its original value');
    $('graph-analysis-a').value='-1';$('graph-analysis-b').value='1';change('graph-analysis-action','root');$('graph-analysis-run').click();await waitFor(()=>$('graph-analysis-result').querySelectorAll('.analysis-point').length===2,'two roots');
    change('graph-analysis-action','intersection');$('graph-other').value='1';$('graph-analysis-a').value='-2';$('graph-analysis-b').value='2';$('graph-analysis-run').click();await waitFor(()=>$('graph-analysis-result').querySelectorAll('.analysis-point').length===2&&!$('graph-analysis-run').disabled,'intersections');
    for(const action of ['minimum','maximum']){change('graph-analysis-action',action);$('graph-analysis-run').click();await waitFor(()=>$('graph-analysis-result').firstChild?.textContent===action[0].toUpperCase()+action.slice(1)&&!$('graph-analysis-run').disabled,action);assert.ok($('graph-analysis-result').querySelector('.analysis-point'));}
    change('graph-analysis-action','tangent');$('graph-analysis-a').value='1';$('graph-analysis-run').click();await waitFor(()=>$('graph-plot').querySelector('[data-tangent]'),'tangent overlay');assert.equal(Array.from($('graph-analysis').querySelectorAll('input[type="range"]')).filter(input=>!input.closest('[hidden]')).length,1,'only one tangent slider is visible');
    $('graph-tangent-slider').value='.5';$('graph-tangent-slider').dispatchEvent(new window.Event('input'));$('graph-tangent-slider').dispatchEvent(new window.Event('change'));await waitFor(()=>$('graph-analysis-result').textContent.includes('0.5')&&!$('graph-analysis-run').disabled,'moving tangent');
    for(const action of ['derivative','integral','arclength']){change('graph-analysis-action',action);$('graph-analysis-a').value='0';$('graph-analysis-b').value='1';$('graph-analysis-run').click();await waitFor(()=>$('graph-analysis-result').firstChild?.textContent==={derivative:'Derivative',integral:'Integral',arclength:'Arc length'}[action]&&!$('graph-analysis-run').disabled,action);assert.ok($('graph-analysis-result').querySelector('math'));}
    $('graph-derivative').checked=true;$('graph-derivative').dispatchEvent(new window.Event('change'));await waitFor(()=>$('graph-plot').querySelectorAll('path').length===3,'derivative curve');
    const before=Number($('graph-max').value)-Number($('graph-min').value);$('graph-zoom-in').click();assert.ok(Number($('graph-max').value)-Number($('graph-min').value)<before);await waitFor(()=>!$('graph-analysis-run').disabled,'zoom sampling');
    for(const cell of $('graph-table').querySelectorAll('td')){const match=/\.([0-9]+)(?:e|$)/i.exec(cell.textContent);if(match)assert.ok(match[1].length<=3,cell.textContent);}
    $('graph-source').value='x^3';$('graph-source').dispatchEvent(new window.Event('input'));document.querySelector('[data-run="graph"]').click();await waitFor(()=>!$('graph-analysis-run').disabled,'inflection source');change('graph-analysis-action','inflection');$('graph-analysis-a').value='-1';$('graph-analysis-b').value='1';$('graph-analysis-run').click();await waitFor(()=>$('graph-analysis-result').firstChild?.textContent==='Inflection'&&$('graph-analysis-result').querySelector('.analysis-point'),'inflection point');
    change('graph-kind','parametric');assert.equal($('graph-source').value,'[cos(t),sin(t)]');assert.equal($('graph-min').dataset.fullValue,'0');assert.equal(Number($('graph-max').dataset.fullValue),2*Math.PI);await waitFor(()=>!$('graph-analysis-run').disabled,'parametric sampling');
    $('graph-source').value='[2*cos(t),sin(t)]';$('graph-source').dispatchEvent(new window.Event('input'));change('graph-kind','cartesian');assert.equal($('graph-source').value,'x^3');change('graph-kind','parametric');assert.equal($('graph-source').value,'[2*cos(t),sin(t)]');change('graph-kind','cartesian');await waitFor(()=>!$('graph-analysis-run').disabled,'restored Cartesian sampling');
    change('mode','scientific');
  });
  await t.test('tip has answers only, no extra rows, and the allocated amounts add up to Total',async()=>{
    change('mode','tip');$('tip-people').value='3';document.querySelector('[data-run="tip"]').click();await waitFor(()=>$('answer').textContent.includes('Per person: 39'),'whole amounts for three people');assert.match($('answer').textContent,/Tip: 17/);assert.match($('answer').textContent,/Total: 117/);assert.equal($('tip-amount-math'),null);assert.equal($('result-source').hidden,true);assert.doesNotMatch($('answer').textContent,/Extra/);
    assert.match($('answer').textContent,/Tip %: 17\.00%/);
    $('tip-whole').checked=false;document.querySelector('[data-run="tip"]').click();await waitFor(()=>$('answer').textContent.includes('38.34,38.33,38.33'),'cent remainder allocation');assert.match($('answer').textContent,/Tip %: 15\.00%/);$('tip-whole').checked=true;
    assert.equal(3834+3833+3833,11500);change('mode','currency');assert.equal($('currency-amount-math'),null,'no expression sits next to the currency amount');
    $('settings-button').click();const digits=document.querySelector('[data-setting="digits"]');digits.value='10';digits.dispatchEvent(new window.Event('change'));$('settings-close').click();change('mode','scientific');
  });
  $('history-button').click();assert.ok($('dialog').open);assert.ok($('dialog-body').textContent.includes('f(3)'));$('dialog-close').click();
  $('catalog-button').click();assert.ok($('dialog-body').textContent.includes('sin()'));assert.ok($('dialog').classList.contains('catalog-dialog'));assert.ok($('dialog-body').querySelector('.catalog-scroll'));
  const catalogSearch=$('dialog-body').querySelector('input'),catalogClear=$('dialog-body').querySelector('.catalog-search-clear');
  assert.equal(catalogClear.hidden,true);catalogSearch.value='cos';catalogSearch.dispatchEvent(new window.Event('input'));assert.equal(catalogClear.hidden,false);assert.equal($('dialog-body').querySelector('.catalog-scroll').textContent.includes('sin()'),false);
  catalogClear.click();assert.equal(catalogSearch.value,'');assert.equal(catalogClear.hidden,true);assert.ok($('dialog-body').querySelector('.catalog-scroll').textContent.includes('sin()'));assert.equal(document.activeElement,catalogSearch);
  [...$('dialog-body').querySelectorAll('button')].find(button=>button.textContent==='Help').click();await waitFor(()=>$('dialog-body').querySelector('.catalog-example'),'inline catalog help');
  const helpSearch=$('dialog-body').querySelector('input');helpSearch.value='banker';helpSearch.dispatchEvent(new window.Event('input'));assert.ok($('dialog-body').textContent.includes('round(x,n)'));assert.equal($('dialog-body').textContent.includes('Sine of x'),false);
  const helpClear=$('dialog-body').querySelector('.catalog-search-clear');assert.equal(helpClear.hidden,false);assert.equal(helpClear.getAttribute('aria-label'),'Clear search');helpClear.click();assert.equal(helpSearch.value,'');assert.equal(helpClear.hidden,true);assert.ok($('dialog-body').textContent.includes('Sine of x'));assert.equal(document.activeElement,helpSearch);
  helpSearch.value='banker';helpSearch.dispatchEvent(new window.Event('input'));
  $('dialog-body').querySelector('.catalog-example').click();assert.equal($('expression').value,'round(2.5)');assert.equal($('dialog').open,false);
  $('about-button').click();assert.equal($('dialog-body').querySelector('button').textContent,'Close');$('dialog-close').click();
  for(let i=1;i<=12;i++){edit(`${i}+100`);key('=').click();await waitFor(()=>$('answer').textContent===String(i+100),`tape calculation ${i}`);}
  const recent=Array.from($('tape-history').querySelectorAll('.tape-expression'));
  assert.equal(recent.length,10,'only ten earlier calculations are on the inline tape');
  assert.deepEqual(recent.map(button=>button.dataset.source),Array.from({length:10},(_,i)=>`${i+2}+100`));
  assert.equal($('answer').closest('#tape-active')!==null,true);assert.equal($('keypad').closest('.keypad-workspace').parentElement.tagName,'MAIN','keypad is outside the scrolling display');
  assert.equal(window.getComputedStyle($('calculation-tape')).overflow,'auto');
  await t.test('ordinary typing reuses the keypad and history DOM and batches local storage writes',async()=>{
    const button=key('1'),rows=Array.from($('tape-history').children),original=window.Storage.prototype.setItem;let writes=0;
    window.Storage.prototype.setItem=function(...args){writes++;return original.apply(this,args);};
    try{edit('');for(let i=0;i<20;i++)key('1').click();assert.equal(key('1'),button);assert.deepEqual(Array.from($('tape-history').children),rows);assert.equal(writes,0,'keypresses do not synchronously serialize all saved data');await waitFor(()=>writes>0,'debounced draft backup');assert.equal(writes,1);assert.equal(JSON.parse(localStorage.getItem('calcmax-web-v1')).fields.expression,'1'.repeat(20));}
    finally{window.Storage.prototype.setItem=original;}
  });
  recent[0].click();assert.equal($('expression').value,'2+100');key('=').click();await waitFor(()=>$('answer').textContent==='102','reuse from the scrolling tape');
  edit('10');key('=').click();await waitFor(()=>$('answer').textContent==='10','historical Ans seed');
  edit('Ans+1');key('=').click();await waitFor(()=>$('answer').textContent==='11','historical Ans expression');
  edit('1000');key('=').click();await waitFor(()=>$('answer').textContent==='1000','later Ans change');
  Array.from($('tape-history').querySelectorAll('.tape-expression')).find(button=>button.dataset.source==='Ans+1').click();key('=').click();await waitFor(()=>$('answer').textContent==='11','reused Ans uses the original full-precision snapshot');
  key('+').click();key('1').click();key('=').click();await waitFor(()=>$('answer').textContent==='12','new calculation resumes the current Ans');
  edit('1/3');key('=').click();await waitFor(()=>$('answer').textContent==='13','fraction on tape');key('AC').click();$('exact-toggle').click();assert.equal($('tape-history').lastElementChild.querySelector('.tape-result').textContent,'0.3333333333','decimal toggle formats earlier entries even with an empty current answer');$('exact-toggle').click();
  // Non-cooperative Python cannot freeze the page; hard cancellation restores WASM.
  change('mode','python');assert.ok($('stop').closest('.runtime-bar'),'Python stop stays at the top of the workspace');$('python-source').value='while True: pass';document.querySelector('[data-run="python"]').click();await waitFor(()=>!$('stop').disabled,'script running');$('stop').click();await waitFor(()=>$('python-output').textContent.includes('cancelled'),'hard cancellation');await waitFor(()=>!document.querySelector('[data-run="python"]').disabled,'engine recovery');
  $('python-source').value='print(42)';document.querySelector('[data-run="python"]').click();await waitFor(()=>$('python-output').textContent==='42\n','post-cancellation script');
  assert.equal(offlineRegistrations,1,'engine recovery does not register the service worker again');
  await t.test('clearing history preserves starred entries and persisted favorites',async()=>{
    change('mode','scientific');$('history-button').click();
    const rows=$('dialog-body').querySelectorAll('.list-row');assert.ok(rows.length>1);
    const favoriteSource=rows[0].querySelector('code').textContent;
    Array.from(rows[0].querySelectorAll('button')).find(button=>button.textContent==='☆').click();
    Array.from($('dialog-body').querySelectorAll('button')).find(button=>button.textContent==='Clear history').click();
    assert.equal($('dialog-body').querySelectorAll('.list-row').length,1);
    assert.equal($('dialog-body').querySelector('code').textContent,favoriteSource);
    const retained=JSON.parse(localStorage.getItem('calcmax-web-v1')).history;assert.equal(retained.length,1);assert.equal(retained[0].star,true);
    $('dialog').close();$('history-button').click();assert.equal($('dialog-body').querySelectorAll('.list-row').length,1);$('dialog').close();
  });
  const persisted=JSON.parse(localStorage.getItem('calcmax-web-v1'));assert.equal(persisted.language,'en');assert.equal(persisted.theme,'light');assert.equal(persisted.history.length,1);assert.ok(persisted.functions.f);
});
