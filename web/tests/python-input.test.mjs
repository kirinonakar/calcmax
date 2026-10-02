import test from 'node:test';
import assert from 'node:assert/strict';
import {fork} from 'node:child_process';
import {readFileSync} from 'node:fs';
import {JSDOM} from 'jsdom';
import {createPythonWorkspace} from '../python-workspace.js';
import {setLanguage,translateDOM} from '../i18n.js';

function nextMessage(worker,predicate) {
  return new Promise((resolve,reject)=>{
    const timer=setTimeout(()=>done(new Error('Worker message timed out')),30000);
    const onMessage=data=>{if(data.type==='fatal')done(new Error(data.error));else if(predicate(data))done(null,data);};
    function done(error,data){clearTimeout(timer);worker.off('message',onMessage);worker.off('error',done);error?reject(error):resolve(data);}
    worker.on('message',onMessage);worker.on('error',done);
  });
}

for(const interactive of [true,false])test(`production WASM Python input (JSPI ${interactive?'enabled':'disabled'})`,{timeout:60000},async t=>{
  // V8 flags cannot be supplied through Worker.execArgv. Use an isolated Node
  // process so enabled/disabled runtimes cannot change each other's V8 flags.
  const worker=fork(new URL('./worker-bridge.mjs',import.meta.url),[],{execArgv:[interactive?'--experimental-wasm-jspi':'--no-experimental-wasm-jspi']});
  worker.postMessage=data=>worker.send(data);
  t.after(()=>worker.kill());
  await nextMessage(worker,data=>data.type==='ready');
  const requests=[];
  worker.on('message',data=>{
    if(data.type!=='input')return;
    requests.push(data);
    // Wrong identifiers must not resolve the suspended input call.
    worker.postMessage({type:'input',id:data.id,inputId:-1,value:'wrong'});
    worker.postMessage({type:'input',id:data.id,inputId:data.inputId,value:['  한글  ',''][requests.length-1]});
  });
  const resultMessage=nextMessage(worker,data=>data.type==='result'&&data.id===1);
  const source="from pathlib import Path\np=Path('/input-counter')\np.write_text(str(int(p.read_text())+1) if p.exists() else '1')\nprint('before')\na=input('a=')\nb=input('b=')\nc=input()\nprint(repr(a), repr(b), repr(c), p.read_text())";
  worker.postMessage({id:1,request:{action:'python',source,inputs:interactive?['first']:['first','  한글  ','']}});
  const {result}=await resultMessage;
  assert.equal(result.ok,true,result.error);
  assert.equal(result.output,"before\na=first\nb=  한글  \n\n'first' '  한글  ' '' 1\n");
  assert.equal(requests.length,interactive?2:0);
  if(interactive){assert.equal(requests[0].prompt,'b=');assert.equal(requests[0].output,'before\na=first\n');}
  const second=nextMessage(worker,data=>data.type==='result'&&data.id===2);
  worker.postMessage({id:2,request:{action:'python',source:interactive?"print('next')":"input('missing=')",inputs:[]}});
  const next=(await second).result;
  if(interactive){
    assert.equal(next.ok,true,next.error);assert.equal(next.output,'next\n');
    requests.length=0;
    const emptyInputs=nextMessage(worker,data=>data.type==='result'&&data.id===3);
    worker.postMessage({id:3,request:{action:'python',source:"def ask():\n    return input('value=')\nprint([ask() for _ in range(2)])",inputs:[]}});
    const resumed=(await emptyInputs).result;
    assert.equal(resumed.ok,true,resumed.error);assert.equal(requests.length,2);
    assert.equal(resumed.output,"value=  한글  \nvalue=\n['  한글  ', '']\n");
  }
  else {assert.equal(next.ok,false);assert.match(next.error,/Interactive input is unavailable/);}
});

test('Python workspace prompts, empty input, cancellation, and import label work in both languages',async t=>{
  const dom=new JSDOM(readFileSync(new URL('../index.html',import.meta.url),'utf8'));
  const previous=globalThis.document,previousFilter=globalThis.NodeFilter;globalThis.document=dom.window.document;globalThis.NodeFilter=dom.window.NodeFilter;
  t.after(()=>{globalThis.document=previous;globalThis.NodeFilter=previousFilter;setLanguage('en');dom.window.close();});
  dom.window.HTMLDialogElement.prototype.showModal=function(){this.open=true;};
  const $=id=>document.getElementById(id);
  let options;
  const engine={execute:async(request,settings)=>{options=settings;return {ok:true,output:'done'};}};
  const workspace=createPythonWorkspace({engine,ui:{pickFile:()=>{},clipboard:()=>{}},persist:()=>{},requestOptions:()=>({}),error:()=>{},run:()=>{}});
  await workspace.run();
  for(const language of ['ko','en']){
    setLanguage(language);translateDOM();
    assert.ok([...document.querySelectorAll('option')].some(option=>option.textContent==='import'));
    const controller=new AbortController();
    const input=options.onInput({prompt:'이름: ',output:'before\n',signal:controller.signal});
    const dialog=document.querySelector('dialog[aria-label]'),field=dialog.querySelector('input');
    assert.equal(document.activeElement,field);assert.equal($('python-output').textContent,'before\n이름: ');
    assert.equal(dialog.querySelector('label').firstChild.textContent,'이름: ');
    field.value='';dialog.querySelector('form').dispatchEvent(new dom.window.Event('submit',{cancelable:true}));
    assert.equal(await input,'');assert.equal(dialog.isConnected,false);
    const cancel=options.onInput({prompt:'',output:'',signal:controller.signal});
    document.querySelector('dialog[aria-label]').dispatchEvent(new dom.window.Event('cancel',{cancelable:true}));
    assert.equal(await cancel,null);
    const stop=options.onInput({prompt:'x=',output:'',signal:controller.signal});
    controller.abort();assert.equal(await stop,null);assert.equal(document.querySelector('dialog[aria-label]'),null);
  }
});
