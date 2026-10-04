import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {JSDOM} from 'jsdom';
import {createAppState} from '../app-state.js';
import {defaultGraphColors,darkGraphColors} from '../graph-colors.js';
import {graphColorSettings} from '../graph-color-settings.js';
import {installCanvas} from './canvas-context.mjs';
import {setLanguage} from '../i18n.js';
import {createAppDialogs} from '../app-dialogs.js';

function page(t,html='<div id="plot"></div>'){
  const dom=new JSDOM(html);globalThis.document=dom.window.document;installCanvas(dom);
  t.after(()=>{dom.window.close();delete globalThis.document;setLanguage('en');});return dom;
}
test('saved palettes normalize six independent colors without mutating backups',()=>{
  const saved={graphColors:['#ABCDEF',null,'red','#ffffff','#fff','#000000','#ff0000']};
  assert.deepEqual(createAppState(saved).graphColors,['#abcdef',null,null,'#ffffff',null,'#000000']);
  assert.equal(saved.graphColors[0],'#ABCDEF');
  for(const graphColors of [undefined,null,{},'invalid'])assert.deepEqual(createAppState({graphColors}).graphColors,Array(6).fill(null));
});

test('Setup edits all six colors independently, restores persisted colors and resets individually or together',t=>{
  const dom=page(t),state=createAppState();setLanguage('ko');let saves=0,redraws=0;
  const {element:section}=graphColorSettings({state,persist:()=>saves++,refreshDisplays:()=>redraws++});document.body.append(section);
  assert.equal(section.querySelector('h3').textContent,'그래프 색');
  const input=section.querySelector('[data-hsl="2"]'),buttons=[...section.querySelectorAll('[data-graph-color]')];
  for(let i=0;i<6;i++){
    buttons[i].click();const before=[...state.graphColors];input.value=60+i;input.dispatchEvent(new dom.window.Event('input'));
    assert.equal(buttons[i].getAttribute('aria-pressed'),'true');assert.notEqual(state.graphColors[i],before[i]);
    assert.deepEqual(state.graphColors.filter((_,n)=>n!==i),before.filter((_,n)=>n!==i));
  }
  assert.equal(saves,6);assert.equal(redraws,6);
  assert.deepEqual(createAppState(JSON.parse(JSON.stringify(state))).graphColors,state.graphColors);
  const before=[...state.graphColors];section.querySelector('.graph-color-resets button').click();
  assert.deepEqual(state.graphColors,[...before.slice(0,5),null]);
  assert.equal(section.querySelector('code').textContent,defaultGraphColors[5].toUpperCase());
  section.querySelector('.graph-color-resets button:last-child').click();assert.deepEqual(state.graphColors,Array(6).fill(null));
  const {element:restored}=graphColorSettings({state:createAppState({graphColors:before}),persist:()=>{},refreshDisplays:()=>{}});
  restored.querySelector('[data-graph-color="4"]').click();assert.equal(restored.querySelector('code').textContent,before[4].toUpperCase());
});

test('open settings and graph redraws follow explicit and system themes; reset follows the active theme',t=>{
  const dom=page(t,readFileSync(new URL('../index.html',import.meta.url),'utf8')),state=createAppState(),listeners={};let redraws=0,saves=0;
  globalThis.window=dom.window;globalThis.NodeFilter=dom.window.NodeFilter;
  t.after(()=>{delete globalThis.window;delete globalThis.NodeFilter;});
  const system={matches:false,addEventListener:(name,listener)=>listeners[name]=listener};dom.window.matchMedia=()=>system;
  dom.window.HTMLDialogElement.prototype.showModal=function(){this.open=true;};
  const byId=id=>document.getElementById(id);
  createAppDialogs({state,ui:{},persist:()=>saves++,calculator:{},refreshDisplays:()=>redraws++});
  byId('settings-button').click();const editor=byId('settings-body').querySelector('.graph-color-settings');
  assert.equal(editor.querySelector('code').textContent,defaultGraphColors[0].toUpperCase());
  system.matches=true;listeners.change();assert.equal(document.documentElement.dataset.theme,'dark');assert.equal(editor.querySelector('code').textContent,darkGraphColors[0].toUpperCase());assert.equal(redraws,1);
  const input=editor.querySelector('[data-hsl="0"]');input.value=240;input.dispatchEvent(new dom.window.Event('input'));const custom=state.graphColors[0];
  byId('theme').value='light';byId('theme').onchange();assert.equal(editor.querySelector('code').textContent,custom.toUpperCase());
  assert.equal(editor.querySelector('[data-graph-color="1"]').style.getPropertyValue('--swatch'),defaultGraphColors[1]);
  const count=redraws;system.matches=false;listeners.change();assert.equal(redraws,count,'explicit theme ignores system changes');
  byId('theme').value='dark';byId('theme').onchange();editor.querySelector('.graph-color-resets button').click();assert.equal(state.graphColors[0],null);assert.equal(editor.querySelector('code').textContent,darkGraphColors[0].toUpperCase());
  assert.equal(createAppState(JSON.parse(JSON.stringify(state))).graphColors[0],null);
  byId('theme').value='system';byId('theme').onchange();assert.equal(editor.querySelector('code').textContent,defaultGraphColors[0].toUpperCase());system.matches=true;listeners.change();assert.equal(editor.querySelector('code').textContent,darkGraphColors[0].toUpperCase());assert.ok(saves>0);
});
