import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {JSDOM} from 'jsdom';
import {createGraphWorkspace} from '../graph-workspace.js';
import {installCanvas} from './canvas-context.mjs';

test('Plot and Analyze stay disabled across animation frames and recover after Stop',async()=>{
  const dom=new JSDOM(readFileSync(new URL('../index.html',import.meta.url),'utf8'));
  globalThis.document=dom.window.document;installCanvas(dom);
  const byId=id=>document.getElementById(id),frames=new Map(),requests=[],resolvers=[];
  let busy=false,ready=true,frameId=0,workspace;
  dom.window.requestAnimationFrame=callback=>{frames.set(++frameId,callback);return frameId;};
  dom.window.cancelAnimationFrame=id=>frames.delete(id);
  workspace=createGraphWorkspace({
    execute:request=>{
      assert.equal(resolvers.length,0,'frame computations must remain serialized');
      requests.push(request);busy=true;workspace.updateButtons();
      return new Promise(resolve=>resolvers.push(resolve));
    },
    options:()=>({displayDigits:10}),onError:assert.fail,persist:()=>{},isBusy:()=>busy,isReady:()=>ready,
    saved:{parameters:{a:1}},
  });
  const complete=async()=>{
    const resolve=resolvers.shift();busy=false;workspace.updateButtons();
    resolve({ok:true,curves:[[[0,0],[1,1]]],parameters:['a']});
    for(let i=0;i<4;i++)await Promise.resolve();
  };
  try{
    byId('graph-source').value='a*x';const initial=workspace.run();await complete();await initial;
    const buttons=[document.querySelector('[data-run="graph"]'),byId('graph-analysis-run')],changes=buttons.map(()=>[]);
    const disabled=Object.getOwnPropertyDescriptor(dom.window.HTMLButtonElement.prototype,'disabled');
    buttons.forEach((button,index)=>Object.defineProperty(button,'disabled',{
      get(){return disabled.get.call(this);},
      set(next){changes[index].push(next);disabled.set.call(this,next);},
    }));
    byId('graph-animate').click();buttons.forEach(button=>assert.equal(button.disabled,true));
    for(let frame=0;frame<60;frame++){
      const batch=[...frames.values()];frames.clear();for(const callback of batch)callback(frame*1000/60);
      assert.equal(busy,true);workspace.updateButtons();buttons.forEach(button=>assert.equal(button.disabled,true));
      await complete();buttons.forEach(button=>assert.equal(button.disabled,true));
    }
    assert.equal(requests.filter(request=>request.samples===200).length,60,'animation still updates at 60 frames per second');
    changes.forEach(values=>assert.deepEqual(values,[true],'engine busy/idle cycles must not toggle or rewrite button state'));
    ready=false;workspace.updateButtons();ready=true;workspace.updateButtons();
    changes.forEach(values=>assert.deepEqual(values,[true]));
    assert.equal(byId('graph-animate').disabled,false,'Stop remains available');
    byId('graph-animate').click();assert.equal(requests.at(-1).samples,500);
    buttons.forEach(button=>assert.equal(button.disabled,true,'final refinement is still running'));
    await complete();buttons.forEach(button=>assert.equal(button.disabled,false));
    changes.forEach(values=>assert.deepEqual(values,[true,false]));
    const manual=workspace.run();buttons.forEach(button=>assert.equal(button.disabled,true));
    await complete();await manual;buttons.forEach(button=>assert.equal(button.disabled,false));
    ready=false;workspace.updateButtons();buttons.forEach(button=>assert.equal(button.disabled,true));
    ready=true;workspace.updateButtons();buttons.forEach(button=>assert.equal(button.disabled,false));
    byId('graph-animate').click();workspace.activate(false);buttons.forEach(button=>assert.equal(button.disabled,false,'leaving Graph releases the animation lock'));
  }finally{workspace.dispose();dom.window.close();}
});
