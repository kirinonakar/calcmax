import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {JSDOM} from 'jsdom';
import {createAppState} from '../app-state.js';
import {defaultGraphColors,darkGraphColors,graphColorsForTheme,hexToHsl,hslToHex} from '../graph-colors.js';
import {graphColorSettings} from '../graph-color-settings.js';
import {createGraphWorkspace} from '../graph-workspace.js';
import {plotGraph} from '../graph-canvas.js';
import {plot} from '../plot.js';
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
test('light and dark default palettes match Android, with custom colors retained across themes',()=>{
  const native=readFileSync(new URL('../../app/src/main/java/com/kirinonakar/calcmax/ui/theme/Theme.kt',import.meta.url),'utf8');
  for(const [name,palette] of [['Light',defaultGraphColors],['Dark',darkGraphColors]]){
    const line=native.split('\n').find(line=>line.startsWith(`private val ${name} =`));
    const colors=[...line.slice(line.indexOf('listOf(')).matchAll(/Color\(0xFF([0-9A-F]{6})\)/g)].map(match=>'#'+match[1].toLowerCase());
    assert.deepEqual(palette,colors);
  }
  const state=createAppState();assert.deepEqual(graphColorsForTheme(state.graphColors,'light'),defaultGraphColors);assert.deepEqual(graphColorsForTheme(state.graphColors,'dark'),darkGraphColors);
  state.graphColors[2]='#123456';const restored=createAppState(JSON.parse(JSON.stringify(state)));
  for(const theme of ['dark','light'])assert.equal(graphColorsForTheme(restored.graphColors,theme)[2],'#123456');
});
test('legacy default colors migrate to theme defaults, while chosen custom colors survive upgrades and backups',()=>{
  const legacy=['#007b68','#a04c75','#3b70bd','#b17d00','#6b5ec2','#a34629'];
  const state=createAppState({graphColors:legacy});assert.deepEqual(state.graphColors,Array(6).fill(null));assert.equal(state.graphColorsVersion,2);
  legacy[1]='#123456';assert.equal(createAppState({graphColors:legacy}).graphColors[1],'#123456');
  const custom=createAppState({graphColors:legacy,graphColorsVersion:2});assert.equal(custom.graphColors[0],'#007b68');assert.deepEqual(createAppState(JSON.parse(JSON.stringify(custom))).graphColors,custom.graphColors);
});
test('HSL round trips preserve defaults and handle hue wrap, grayscale, black and white',()=>{
  for(const color of [...defaultGraphColors,'#000000','#ffffff','#808080','#ff0000','#00ff00','#0000ff'])assert.equal(hslToHex(hexToHsl(color)),color);
  assert.equal(hslToHex([360,100,50]),'#ff0000');assert.equal(hslToHex([120,100,50]),'#00ff00');assert.equal(hslToHex([240,100,50]),'#0000ff');
  assert.equal(hslToHex([120,0,50]),'#808080');assert.equal(hslToHex([120,100,0]),'#000000');assert.equal(hslToHex([120,100,100]),'#ffffff');
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
  const workspace=createGraphWorkspace({execute:async()=>{requests++;return {ok:true,curves:Array.from({length:6},(_,i)=>[[0,i],[1,i+1]]),parameters:[]};},options:()=>({displayDigits:10}),onError:assert.fail,persist:()=>{},isBusy:()=>false,getColors:()=>graphColorsForTheme(state.graphColors,document.documentElement.dataset.theme)});
  try {
  document.getElementById('graph-source').value='x\nx+1\nx+2\nx+3\nx+4\nx+5';await workspace.run();
  state.graphColors[5]='#ff0000';workspace.render();assert.equal(requests,1,'changing colors does not resample');
  assert.equal(document.getElementById('graph-formulas').children[5].style.getPropertyValue('--curve-color'),'#ff0000');
  for(const theme of ['dark','light']){
    document.documentElement.dataset.theme=theme;workspace.render();
    const palette=graphColorsForTheme(state.graphColors,theme),commands=document.querySelector('#graph-plot canvas').getContext('2d').commands;
    for(let i=0;i<6;i++){
      assert.equal(document.getElementById('graph-formulas').children[i].style.getPropertyValue('--curve-color'),palette[i]);
      assert.ok(commands.some(c=>c.op==='stroke'&&c.strokeStyle===palette[i]));
    }
    assert.equal(requests,1,'theme changes only redraw existing curves');
  }
  document.getElementById('graph-animate').click();const batch=[...frames.values()];frames.clear();for(const frame of batch)frame(0);
  for(let i=0;i<5;i++)await Promise.resolve();
  assert.ok(document.querySelector('#graph-plot canvas').getContext('2d').commands.some(c=>c.op==='stroke'&&c.strokeStyle==='#ff0000'));
  } finally {workspace.dispose();}
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
