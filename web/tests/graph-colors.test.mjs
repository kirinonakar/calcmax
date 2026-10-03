import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {JSDOM} from 'jsdom';
import {createAppState} from '../app-state.js';
import {defaultGraphColors,hexToHsl,hslToHex} from '../graph-colors.js';
import {graphColorSettings} from '../graph-color-settings.js';
import {createGraphWorkspace} from '../graph-workspace.js';
import {plotGraph} from '../graph-canvas.js';
import {plot} from '../plot.js';
import {installCanvas} from './canvas-context.mjs';
import {setLanguage} from '../i18n.js';

function page(t,html='<div id="plot"></div>'){
  const dom=new JSDOM(html);globalThis.document=dom.window.document;installCanvas(dom);
  t.after(()=>{dom.window.close();delete globalThis.document;setLanguage('en');});return dom;
}
test('saved palettes normalize six independent colors without mutating backups',()=>{
  const saved={graphColors:['#ABCDEF',null,'red','#ffffff','#fff','#000000','#ff0000']};
  assert.deepEqual(createAppState(saved).graphColors,['#abcdef',defaultGraphColors[1],defaultGraphColors[2],'#ffffff',defaultGraphColors[4],'#000000']);
  assert.equal(saved.graphColors[0],'#ABCDEF');
  for(const graphColors of [undefined,null,{},'invalid'])assert.deepEqual(createAppState({graphColors}).graphColors,defaultGraphColors);
});
test('HSL round trips preserve defaults and handle hue wrap, grayscale, black and white',()=>{
  for(const color of [...defaultGraphColors,'#000000','#ffffff','#808080','#ff0000','#00ff00','#0000ff'])assert.equal(hslToHex(hexToHsl(color)),color);
  assert.equal(hslToHex([360,100,50]),'#ff0000');assert.equal(hslToHex([120,100,50]),'#00ff00');assert.equal(hslToHex([240,100,50]),'#0000ff');
  assert.equal(hslToHex([120,0,50]),'#808080');assert.equal(hslToHex([120,100,0]),'#000000');assert.equal(hslToHex([120,100,100]),'#ffffff');
});
test('Setup edits all six colors independently, restores persisted colors and resets individually or together',t=>{
  const dom=page(t),state=createAppState();setLanguage('ko');let saves=0,redraws=0;
  const section=graphColorSettings({state,persist:()=>saves++,refreshDisplays:()=>redraws++});document.body.append(section);
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
  assert.deepEqual(state.graphColors,[...before.slice(0,5),defaultGraphColors[5]]);
  section.querySelector('.graph-color-resets button:last-child').click();assert.deepEqual(state.graphColors,defaultGraphColors);
  const restored=graphColorSettings({state:createAppState({graphColors:before}),persist:()=>{},refreshDisplays:()=>{}});
  restored.querySelector('[data-graph-color="4"]').click();assert.equal(restored.querySelector('code').textContent,before[4].toUpperCase());
});
test('Canvas and SVG use all six custom colors for curves, trace, points and integral shading',t=>{
  page(t);const container=document.getElementById('plot'),colors=['#ff0000','#00ff00','#0000ff','#00ffff','#ff00ff','#ffff00'],curves=colors.map((_,i)=>[[0,i],[1,i+1]]),result={curves,shadings:[{fill:[[[0,0],[1,1],[1,0]]]}]},bounds={xmin:-1,xmax:2,ymin:-1,ymax:8},options={colors,selected:5,dots:true,trace:[1,6],integral:[0,1]};
  const canvas=plotGraph(container,result,bounds,options),commands=canvas.getContext('2d').commands;
  for(const color of colors)assert.ok(commands.some(c=>c.op==='stroke'&&c.strokeStyle===color));
  assert.ok(commands.some(c=>c.op==='fill'&&c.fillStyle===colors[5]&&c.globalAlpha===.18));
  assert.ok(commands.some(c=>c.op==='fill'&&c.fillStyle===colors[5]&&c.path.some(p=>p.op==='arc'&&p.args[2]===6)));
  plot(container,result,bounds,options);
  for(let i=0;i<6;i++)assert.equal(container.querySelector(`[data-curve="${i}"]`).getAttribute('stroke'),colors[i]);
  assert.equal(container.querySelector('[data-trace]').getAttribute('fill'),colors[5]);assert.equal(container.querySelector('[data-integral]').getAttribute('fill'),colors[5]);
});
test('workspace refresh updates formula colors and animation redraws keep custom colors',async t=>{
  const dom=page(t,readFileSync(new URL('../index.html',import.meta.url),'utf8')),state=createAppState();let requests=0;
  const frames=new Map();let frameId=0;
  dom.window.requestAnimationFrame=callback=>{frames.set(++frameId,callback);return frameId;};dom.window.cancelAnimationFrame=id=>frames.delete(id);
  const workspace=createGraphWorkspace({execute:async()=>{requests++;return {ok:true,curves:Array.from({length:6},(_,i)=>[[0,i],[1,i+1]]),parameters:[]};},options:()=>({displayDigits:10}),onError:assert.fail,persist:()=>{},isBusy:()=>false,getColors:()=>state.graphColors});
  try {
  document.getElementById('graph-source').value='x\nx+1\nx+2\nx+3\nx+4\nx+5';await workspace.run();
  state.graphColors[5]='#ff0000';workspace.render();assert.equal(requests,1,'changing colors does not resample');
  assert.equal(document.getElementById('graph-formulas').children[5].style.getPropertyValue('--curve-color'),'#ff0000');
  document.getElementById('graph-animate').click();const batch=[...frames.values()];frames.clear();for(const frame of batch)frame(0);
  for(let i=0;i<5;i++)await Promise.resolve();
  assert.ok(document.querySelector('#graph-plot canvas').getContext('2d').commands.some(c=>c.op==='stroke'&&c.strokeStyle==='#ff0000'));
  } finally {workspace.dispose();}
});
