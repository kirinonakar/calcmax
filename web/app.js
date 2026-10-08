import {EngineClient} from './engine-client.js';
import {readState} from './storage.js';
import {translateDOM,setText,setLanguage} from './i18n.js';
import {renderFormulas} from './formula-preview.js';
import {createGraphWorkspace} from './graph-workspace.js';
import {$,value,element,createAppUI} from './app-ui.js';
import {createAppState,restoreFields,restoreSelect,createPersistence} from './app-state.js';
import {createEngineUI} from './engine-ui.js';
import {createCalculator} from './calculator.js';
import {createCalculatorKeypad} from './calculator-keypad.js';
import {createWorkspaces} from './workspaces.js';
import {createAppDialogs} from './app-dialogs.js';
import {bindKeyPress} from './keypad.js';
import {graphColorsForTheme} from './graph-colors.js';

const saved=readState(),state=createAppState(saved,navigator.language),ui=createAppUI();
setLanguage(state.language);
restoreFields(state);
let calculator,graphs,workspaces,keypad,dialogs;
const persistence=createPersistence({state,toast:ui.toast,
  snapshot:()=>({expression:calculator.draftSource(),graph:graphs.snapshot()}),
  onPersist:({functionsChanged})=>{if(functionsChanged)workspaces.renderFunctions();calculator.schedulePreview();}});
const {persist,schedulePersist}=persistence;
const requestOptions=()=>({angle:value('angle'),precision:state.precision,displayDigits:state.digits,variables:state.variables,functions:state.functions,assumptions:state.assumptions});
const engine=new EngineClient();
const runtime=createEngineUI({engine,onChange:updateButtons,onReady:()=>calculator.resetPreview(),cancelPreview:()=>calculator.cancelPreview()});
graphs=createGraphWorkspace({execute:(request,settings)=>engine.execute(request,settings),options:requestOptions,onError:error,onClearError:()=>{$('answer').querySelector('.error')?.remove();},persist,isBusy:()=>runtime.busy,isReady:()=>engine.ready,saved:state.graph,getColors:()=>graphColorsForTheme(state.graphColors,document.documentElement.dataset.theme)});
calculator=createCalculator({state,engine,isBusy:()=>runtime.busy,ui,persist,schedulePersist,requestOptions,error,changeMode,updateButtons,graphs,
  onFunctionsChanged:()=>workspaces.renderFunctions(),
  pressKey:input=>keypad.press(input),modeDialog:()=>dialogs.mode(),variablesDialog:()=>dialogs.variables(),matrixInsertDialog:()=>dialogs.matrixInsert()});
keypad=createCalculatorKeypad({state,persist,isCalcActive:()=>calculator.calcActive,isBusy:()=>runtime.busy,handleKey:calculator.handleKey,updateButtons});
workspaces=createWorkspaces({state,engine,ui,persist,restoreSelect:id=>restoreSelect(state,id),requestOptions,isBusy:()=>runtime.busy,error,changeMode,
  replaceInput:calculator.replaceInput,insert:calculator.insert,evaluate:calculator.evaluate,showResult:calculator.showResult,graphs});
dialogs=createAppDialogs({state,ui,persist,calculator,changeMode,pressKey:keypad.press,refreshDisplays,renderMatrix:workspaces.renderMatrix,error});

function updateButtons() {
  runtime.updateStopButton();
  document.querySelectorAll('[data-run]:not([data-run="graph"]),.key[data-evaluate]').forEach(button=>button.disabled=!engine.ready||runtime.busy);
  calculator.updateButtons();$('retry').hidden=engine.ready||runtime.busy;
  graphs.updateButtons();graphs.flush();
}
function error(message) {
  $('answer').replaceChildren(element('span',message,'error'));$('note').textContent='';
  if(value('mode')==='equation'){try{renderFormulas($('result-source'),workspaces.equationSource().split(/\r?\n/),{digits:state.digits});}catch{}}
  else if(value('mode')==='programmer')setText($('programmer-output'),message);
  else if(value('mode')==='constants')setText($('constants-list'),message);
}
function changeMode(mode) {
  $('mode').value=mode;document.documentElement.dataset.workspace=mode;
  setText($('mode-status'),$('mode').selectedOptions[0].textContent);
  const panel=$('answer').closest('.answer-panel'),actions=$('exact-toggle').parentElement;
  panel.hidden=['graph','python','programmer','constants','probability'].includes(mode);actions.hidden=panel.hidden;
  if(mode==='scientific'){$('tape-active').append(panel);$('calculator-display').append(actions);}
  else{panel.append(actions);document.querySelector('main').insertBefore(panel,document.querySelector('.keypad-workspace'));}
  if(mode==='scientific'){const edits=$('calculator-display').querySelector('.edit-actions');if($('stop').parentElement!==edits)edits.insertBefore($('stop'),edits.firstChild);}
  else document.querySelector('.runtime-bar').append($('stop'));
  updateButtons();
  document.querySelectorAll('[data-mode]').forEach(section=>section.hidden=!section.dataset.mode.split(' ').includes(mode));
  if(['matrix','vector'].includes(mode))workspaces.renderMatrix();
  if(mode==='scientific'){calculator.renderTape();calculator.followTape();}
  graphs.activate(mode==='graph');workspaces.refreshMath();calculator.updateResultSource();
  calculator.refreshSizing();
  if(mode==='scientific')calculator.renderInputCursor();
  persist();
}
function refreshDisplays(){calculator.renderResult();workspaces.render();graphs.render();}
$('mode').onchange=()=>changeMode(value('mode'));$('angle').onchange=persist;
$('digits-indicator').onclick=()=>{
  state.digits=({2:3,3:5,5:10})[state.digits]||2;
  state.precision=Math.max(state.precision,state.digits);
  $('digits-indicator').textContent=`≤ ${state.digits} digits`;persist();refreshDisplays();calculator.resetPreview();
};
const clearModePress=bindKeyPress($('mode-status'),()=>dialogs.mode(),()=>changeMode('scientific'));
document.querySelectorAll('main input,main select,main textarea').forEach(field=>field.addEventListener('change',persist));
window.addEventListener('pagehide',()=>{clearModePress();persist();persistence.dispose();calculator.dispose();runtime.dispose();ui.dispose();graphs.dispose();});
window.addEventListener('resize',()=>{graphs.render();calculator.renderInputCursor();});

async function initialize() {
  await dialogs.initialize();workspaces.initialize();changeMode(value('mode'));keypad.render();
  $('expression').readOnly=true;calculator.preview();$('digits-indicator').textContent=`≤ ${state.digits} digits`;translateDOM();
  if(location.protocol==='file:')error('WebAssembly는 정적 HTTP 서버가 필요합니다. python web/serve.py를 실행하고 http://localhost:8080을 여세요.');
}
await initialize();
