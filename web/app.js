import {parse,latexInput,closeInputBrackets} from './parser.js';
import {EngineClient} from './engine-client.js';
import {mathDisplay} from './math-display.js';
import {plot,dataBounds} from './plot.js';
import {readState,writeState,downloadFile} from './storage.js';
import {t,setLanguage,getLanguage,initialLanguage,translateDOM,applyTheme,setText} from './i18n.js';
import {renderKeypad as buildKeypad,updateKeypadState,scientificRows,secondRows,numericRows,topKeys,topFunctions} from './keypad.js';
import {expressionDisplay} from './expression-display.js';
import {calcVariables,calcBindings} from './calc-session.js';
import {previousCalculations,renderPreviousCalculations,followTape} from './calculation-tape.js';
import {createGraphWorkspace} from './graph-workspace.js';
import {renderFormulas} from './formula-preview.js';
import {displayNumber} from './display-format.js';
import {statisticsCommand,distributionCommand,csvRows,numericStatisticsRows,equationCommand,polynomialEquation} from './workspace-commands.js';
import {statisticsPlot} from './statistics-plot.js';
import {astSource} from './ast-source.js';
import {tipCommand,moneyResult} from './money.js';
import {markInputCursor} from './input-cursor.js';
import {unitGroups} from './unit-groups.js';
import {bindPythonEditor} from './python-tools.js';
import {defineFunction,encodeFunctions,decodeFunctions} from './function-transfer.js';
import {moveMathCursor} from './input-navigation.js';
import {appVersion} from './app-version.js';
import {parseCatalogHelp,helpExampleInput} from './catalog-help.js';

const $=id=>document.getElementById(id);
const value=id=>$(id).value;
const saved=readState();
const objectOrEmpty=o=>o && typeof o==='object' && !Array.isArray(o) ? o : {};
const state={variables:objectOrEmpty(saved.variables),functions:objectOrEmpty(saved.functions),datasets:objectOrEmpty(saved.datasets),history:Array.isArray(saved.history)?saved.history.slice(0,500):[],favorites:Array.isArray(saved.favorites)?saved.favorites:[],recent:Array.isArray(saved.recent)?saved.recent:[],precision:Math.max(3,Math.min(200,Number(saved.precision)||30)),digits:Math.max(2,Math.min(200,Number(saved.precision)||30,Number(saved.digits)||10)),fields:objectOrEmpty(saved.fields),matrixCells:objectOrEmpty(saved.matrixCells),rates:objectOrEmpty(saved.rates)};
state.language=initialLanguage(saved,navigator.language);
state.languageChosen=saved.languageChosen===true;
state.theme=['system','light','dark'].includes(saved.theme)?saved.theme:'system';
const systemTheme=window.matchMedia('(prefers-color-scheme: dark)');
setLanguage(state.language);$('language').value=state.language;$('theme').value=state.theme;applyTheme(state.theme,systemTheme.matches);
translateDOM();
systemTheme.addEventListener('change',()=>applyTheme(state.theme,systemTheme.matches));
$('theme').onchange=()=>{state.theme=value('theme');applyTheme(state.theme,systemTheme.matches);refreshDisplays();persist();};
$('language').onchange=()=>{state.language=value('language');state.languageChosen=true;setLanguage(state.language);translateDOM();renderNotation();refreshDisplays();if(['matrix','vector'].includes(value('mode')))renderMatrix();persist();if($('dialog').open)$('dialog').close();};
state.secondKeys=!!saved.secondKeys;
state.tapeClearedAt=Number(saved.tapeClearedAt)||0;
state.resultDisplayMode=['eng','sci'].includes(saved.resultDisplayMode)?saved.resultDisplayMode:'off';
state.autoCloseBrackets=saved.autoCloseBrackets!==false;
state.persistHistory=saved.persistHistory!==false;state.haptics=!!saved.haptics;state.sound=!!saved.sound;
state.inputFont=Math.max(10,Math.min(42,Number(saved.inputFont)||24));state.outputFont=Math.max(10,Math.min(48,Number(saved.outputFont)||30));
state.assumptions=objectOrEmpty(saved.assumptions);
state.displayShortcuts=Array.isArray(saved.displayShortcuts)?saved.displayShortcuts.slice(0,12):[{label:'∫',input:'integrate(,x)'},{label:'∫ₐᵇ',input:'integrate(,x,0,1)'},{label:'d/dx',input:'diff(,x)'}];
let lastResult=null,decimal=false,shift=false,alpha=false,hyperbolic=false,typing=false,overwrite=false,committed=false,screenExpanded=false,grouping=false,mixed=false,busy=false,catalog={},lastResultSource='',statisticsGraph=null,storageWarning=false;
let calcSession=null;
let activeHistoryEntry=null,inputAnswer=null;
let tapeRows=null,tapeFormat='',keypadSignature='',saveTimer=null,keyAudioContext=null;
const tapeFollow=followTape($('calculation-tape'),$('tape-active'));
const expressionUndo=[];
const undoStack=()=>calcSession?.undo||expressionUndo;
for(const [id,setting] of Object.entries(state.fields)) {
  const field=$(id);
  if(!field || field.closest('dialog')) continue;
  if(field.type==='checkbox') field.checked=!!setting;
  else if(field.tagName!=='SELECT') field.value=String(setting);
}
function restoreSelect(id) { if(state.fields[id]!==undefined && Array.from($(id).options).some(o=>o.value===String(state.fields[id]))) $(id).value=String(state.fields[id]); }
document.querySelectorAll('select').forEach(field=>restoreSelect(field.id));
function persist() {
  clearTimeout(saveTimer);saveTimer=null;
  for(const field of document.querySelectorAll('main input[id],main textarea[id],main select[id],.mode-bar select[id]')) state.fields[field.id]=field.type==='checkbox'?field.checked:field.value;
  if(calcSession)state.fields.expression=calcSession.source;
  state.graph=graphs.snapshot();
  if(!writeState({...state,history:state.persistHistory?state.history:[]}) && !storageWarning) { storageWarning=true; toast('브라우저 저장 공간을 사용할 수 없어 이번 세션에서만 보관합니다.'); }
}
function schedulePersist(){clearTimeout(saveTimer);saveTimer=setTimeout(persist,150);}
function toast(message) { setText($('toast'),message); $('toast').hidden=false; clearTimeout(toast.timer); toast.timer=setTimeout(()=>$('toast').hidden=true,3500); }
function error(message) { $('answer').replaceChildren(); const el=document.createElement('span');el.className='error';setText(el,message);$('answer').append(el);$('note').textContent='';if(value('mode')==='equation'){try{renderFormulas($('result-source'),equationSource().split(/\r?\n/),{digits:state.digits});}catch{}}else if(value('mode')==='programmer')setText($('programmer-output'),message);else if(value('mode')==='constants')setText($('constants-list'),message); }
function control(label,action,className='') { const button=document.createElement('button');button.type='button';setText(button,label);button.className=className;button.addEventListener('click',action);return button; }
function element(tag,text='',className='') { const el=document.createElement(tag);setText(el,text);el.className=className;return el; }
function clearableCatalogSearch(input){
  const holder=element('div','','catalog-search'),clear=control('✕',()=>{
    input.value='';input.dispatchEvent(new input.ownerDocument.defaultView.Event('input',{bubbles:true}));input.focus();
  },'catalog-search-clear');
  clear.setAttribute('aria-label',t('Clear search'));clear.title=t('Clear search');
  const update=()=>{clear.hidden=!input.value;};input.addEventListener('input',update);update();
  holder.append(input,clear);return holder;
}
function openDialog(title,content) { $('dialog').classList.toggle('catalog-dialog',content.classList.contains('catalog-content'));setText($('dialog-title'),title);$('dialog-body').replaceChildren(content);translateDOM($('dialog'));if(!$('dialog').open)$('dialog').showModal(); }
$('about-button').onclick=()=>{
  const content=element('div','','about-content'),icon=element('img'),version=element('p',`v${appVersion}`,'hint'),link=element('a','https://github.com/kirinonakar/calcmax');
  icon.src='app-icon.webp';icon.alt='CalcMax';icon.width=64;icon.height=64;
  link.href='https://github.com/kirinonakar/calcmax';link.target='_blank';link.rel='noopener noreferrer';
  content.append(icon,version,link,control('Close',()=>$('dialog').close()));openDialog('CalcMax',content);
};
$('dialog-close').onclick=()=>$('dialog').close();
$('dialog').addEventListener('click',event=>{if(event.target===$('dialog')){const r=$('dialog').getBoundingClientRect();if(event.clientX<r.left||event.clientX>r.right||event.clientY<r.top||event.clientY>r.bottom)$('dialog').close();}});
function requestOptions() { return {angle:value('angle'),precision:state.precision,displayDigits:state.digits,variables:state.variables,functions:state.functions,assumptions:state.assumptions}; }
const engine=new EngineClient();
const graphs=createGraphWorkspace({execute:request=>engine.execute(request),options:requestOptions,onError:error,persist,isBusy:()=>busy,isReady:()=>engine.ready,saved:saved.graph});
function updateButtons() { document.querySelectorAll('[data-run],.key[data-evaluate]').forEach(button=>button.disabled=!engine.ready||busy);document.querySelectorAll('.tape-expression').forEach(button=>button.disabled=busy||!!calcSession);$('stop').disabled=!busy;$('stop').hidden=false;$('stop').style.visibility=busy?'':'hidden';$('expression').readOnly=!typing||busy;$('retry').hidden=engine.ready||busy;graphs.updateButtons();graphs.flush(); }
document.documentElement.dataset.busy='false';document.documentElement.dataset.engine='loading';document.documentElement.dataset.typing='false';
engine.addEventListener('status',event=>{setText($('status'),event.detail);document.documentElement.dataset.engine=engine.ready?'ready':'loading';updateButtons();});
engine.addEventListener('ready',()=>{document.documentElement.dataset.engine='ready';updateButtons();});
// Do not compete with the first WASM/SymPy download by precaching the same
// large runtime files. Offline installation starts only after the engine works.
engine.addEventListener('ready',registerOfflineCache,{once:true});
engine.addEventListener('busy',event=>{busy=event.detail;document.documentElement.dataset.busy=String(busy);updateButtons();});
$('stop').onclick=()=>engine.cancel();
$('retry').onclick=()=>engine.cancel('계산 엔진을 재시작합니다.');
function showResult(result,source='',displaySource=source) {
  if(!result.ok){error(result.error||'계산 오류');return;}
  const previousAnswer=value('mode')==='scientific'?(inputAnswer||state.variables.Ans):state.variables.Ans;
  lastResult=result;lastResultSource=displaySource;
  committed=true;$('commit-indicator').textContent='=';
  if(result.resultAst&&!result.assignment) state.variables.Ans=result.resultAst;
  renderResult();
  if(source){activeHistoryEntry={source,exact:result.exact||'',decimal:result.decimal||'',resultAst:result.resultAst,inputAns:previousAnswer,calcValues:result.calcValues,display:{tree:result.tree,decimalTree:result.decimalTree,approximate:result.approximate},time:Date.now(),star:false};state.history.unshift(activeHistoryEntry);state.history=state.history.slice(0,500);for(const entry of state.history.slice(11))delete entry.display;}
  renderTape();tapeFollow.latest();
  persist();
}
function renderTape(){const rows=previousCalculations(state.history,activeHistoryEntry,state.tapeClearedAt),format=`${decimal}:${state.digits}:${state.resultDisplayMode}:${grouping}`;if(tapeRows&&format===tapeFormat&&rows.length===tapeRows.length&&rows.every((entry,i)=>entry===tapeRows[i]))return;tapeRows=rows;tapeFormat=format;renderPreviousCalculations($('tape-history'),rows,{decimal,digits:state.digits,notation:state.resultDisplayMode,grouping,reuseDisabled:busy||!!calcSession,reuse:reuseCalculation});}
function reuseCalculation(entry){
  if(busy||calcSession)return;
  expressionUndo.push(value('expression'));committed=false;lastResult=null;activeHistoryEntry=null;inputAnswer=entry.inputAns||null;
  mode('scientific');$('expression').value=entry.source;$('expression').setSelectionRange(entry.source.length,entry.source.length);$('commit-indicator').textContent='';$('answer').replaceChildren();$('note').textContent='';preview();
}
function evaluationTree(source){
  const tree=parse(source);
  function freeze(node){if(inputAnswer&&node.kind==='symbol'&&node.value==='Ans')return inputAnswer;return {...node,args:(node.args||[]).map(freeze)};}
  return inputAnswer?freeze(tree):tree;
}
function renderResult() {
  if(!lastResult){renderTape();return;}
  let tree=decimal ? lastResult.decimalTree||lastResult.tree : lastResult.tree;
  if(mixed&&!decimal&&tree?.kind==='fraction'){
    try{const numerator=BigInt(tree.args[0].value),denominator=BigInt(tree.args[1].value),whole=numerator/denominator,remainder=(numerator<0n?-numerator:numerator)%denominator;if(whole)tree={kind:'mixed',args:[{kind:'number',value:whole.toString()},{kind:'fraction',args:[{kind:'number',value:remainder.toString()},{kind:'number',value:denominator.toString()}]}]};}catch{}
  }
  $('answer').replaceChildren();
  const text=(decimal ? lastResult.decimal : lastResult.exact)||'';
  if(tree && text.length<=40000) $('answer').append(mathDisplay(tree,state.digits,decimal||lastResult.approximate,{notation:state.resultDisplayMode,grouping}));
  else renderFormulas($('answer'),text.split(/\r?\n/),{digits:state.digits});
  $('result-source').hidden=['scientific','tip'].includes(value('mode'))||!lastResultSource;
  if(!$('result-source').hidden)renderFormulas($('result-source'),[lastResultSource],{digits:state.digits});
  const notes=[lastResult.note,...(lastResult.conditions||[]),lastResult.calcValues?Object.entries(lastResult.calcValues).map(([name,n])=>`${name} = ${n}`).join(', '):''].filter(Boolean).join('\n');
  if(notes)$('note').textContent=notes;else setText($('note'),'Next input starts a new calculation');
  $('answer-insert').disabled=!lastResult.resultAst;
  renderTape();
}
async function evaluate(source=value('expression')) {
  if(busy)return;
  if(!engine.ready){error('계산 엔진이 로딩 중입니다.');return;}
  if(calcSession){await submitCalcValue();return;}
  try {
    const converted=latexInput(state.autoCloseBrackets?closeInputBrackets(source):source);
    if(source===value('expression')&&converted!==source){$('expression').value=converted;preview();}
    const assignment=converted.match(/^\s*([A-Za-z][A-Za-z0-9_]*)\s*=(?!=)([\s\S]+)$/);
    if(assignment&&['pi','e','i','I','oo','Ans','c0','hP','hbar','G','qe','NA','kB0','me','mp0'].includes(assignment[1]))throw new Error('Reserved constant or answer name');
    const tree=evaluationTree(assignment?assignment[2]:converted);
    const result=await engine.execute({...requestOptions(),tree});
    if(assignment&&result.ok){
      if(!result.resultAst)throw new Error('No reusable result to store.');
      const inputs=calcVariables(tree),selfReference=inputs.includes(assignment[1])||calcVariables(tree,state.variables).includes(assignment[1]);
      state.variables[assignment[1]]=inputs.length&&!selfReference?tree:result.resultAst;result.assignment=true;result.note=`${t('Stored in')} ${assignment[1]}`;
    }
    showResult(result,converted);
  }
  catch(exc){error(exc.message);}
}
function preview() {
  const source=value('expression'),display=$('expression-preview');display.replaceChildren();
  let target=display;
  if(calcSession){display.append(element('div',calcSession.source,'calc-source'));target=element('div','','calc-value');target.append(element('span',`${calcSession.names[calcSession.index]} = `));display.append(target);}
  if(source)try{target.append(expressionDisplay(source));}catch{target.append(document.createTextNode(source));}
  renderInputCursor();
  renderTape();tapeFollow.latest();
  schedulePersist();
}
function renderInputCursor(){const field=$('expression'),display=$('expression-preview'),math=display.querySelector('math');if(math)markInputCursor(math,field.value,field.selectionStart,field.selectionEnd);else if(!field.value&&!display.querySelector('.text-caret')){const cursor=element('span','│','text-caret');display.append(cursor);}}
$('expression-preview').onclick=event=>{const target=event.target.closest('[data-source-start]');if(target){const field=$('expression');field.setSelectionRange(Number(target.getAttribute('data-source-start')),Number(target.getAttribute('data-source-end')));}else{$('expression').setSelectionRange(value('expression').length,value('expression').length);}preview();};
function insert(text,cursor=null,{factor=false}={}) {
  if(busy)return;
  const field=$('expression'),undo=undoStack();undo.push(field.value);if(undo.length>100)undo.shift();
  if(committed){inputAnswer=null;field.value=!lastResult?.assignment&&/^[+\-*/÷^%!∠]/.test(text)?'Ans':'';field.setSelectionRange(field.value.length,field.value.length);committed=false;$('commit-indicator').textContent='';}
  if(state.autoCloseBrackets&&text.length===1&&field.selectionStart===field.selectionEnd&&!overwrite){const pairs={'(' : ')','[':']','{':'}'};if(pairs[text]){text+=pairs[text];cursor=1;}else if(')]}'.includes(text)&&field.value[field.selectionStart]===text){field.setSelectionRange(field.selectionStart+1,field.selectionStart+1);preview();return;}}
  const start=field.selectionStart,end=overwrite&&field.selectionEnd===start?Math.min(field.value.length,start+text.length):field.selectionEnd;
  let prefix='',suffix='';
  // Keypad operands are separate factors; typed/pasted names remain intact.
  if(factor&&(start===end||/^[\p{L}_][\p{L}\p{N}_]*$/u.test(text))){
    const before=field.value[start-1]||'',after=field.value[end]||'';
    if(/[\p{L}\p{N}_.)\]}!%]/u.test(before)&&(/^[\p{L}_(]/u.test(text)||/^[0-9.]/.test(text)&&/[\p{L}_)\]}!%]/u.test(before)))prefix='*';
    if(/[\p{L}_]/u.test(after)&&/[\p{L}\p{N}_)\]}!%]$/u.test(text)||/[0-9]/.test(after)&&/[\p{L}_)\]}!%]$/u.test(text))suffix='*';
  }
  field.setRangeText(prefix+text+suffix,start,end,'end');
  const position=start+prefix.length+(cursor??text.length);field.setSelectionRange(position,position);
  if(typing)field.focus({preventScroll:true});preview();
}
function mode(mode) {
  $('mode').value=mode;
  document.documentElement.dataset.workspace=mode;
  const panel=$('answer').closest('.answer-panel'),actions=$('exact-toggle').parentElement;
  panel.hidden=['graph','python','programmer','constants'].includes(mode);actions.hidden=panel.hidden;
  if(mode==='scientific'){$('tape-active').append(panel);$('calculator-display').append(actions);}
  else{panel.append(actions);document.querySelector('main').insertBefore(panel,document.querySelector('.keypad-workspace'));}
  if(mode==='scientific'){const edits=$('calculator-display').querySelector('.edit-actions');if($('stop').parentElement!==edits)edits.insertBefore($('stop'),edits.firstChild);}
  else document.querySelector('.runtime-bar').append($('stop'));
  updateButtons();
  document.querySelectorAll('[data-mode]').forEach(section=>section.hidden=!section.dataset.mode.split(' ').includes(mode));
  if(['matrix','vector'].includes(mode))renderMatrix();
  if(mode==='scientific'){renderTape();tapeFollow.latest();}
  graphs.activate(mode==='graph');refreshWorkspaceMath();$('result-source').hidden=['scientific','tip'].includes(mode)||!lastResultSource;
  persist();
}
$('mode').onchange=()=>mode(value('mode'));
$('angle').onchange=persist;
$('expression').oninput=()=>{if(committed)inputAnswer=null;committed=false;if(!calcSession)$('commit-indicator').textContent='';preview();};
$('expression').addEventListener('beforeinput',event=>{if(typing&&state.autoCloseBrackets&&event.inputType==='insertText'&&event.data?.length===1&&'()[]{}'.includes(event.data)){event.preventDefault();insert(event.data);}});
$('expression').addEventListener('paste',event=>{const text=event.clipboardData?.getData('text');if(!text)return;try{const converted=latexInput(text);if(converted!==text){event.preventDefault();insert(converted);}}catch(exc){event.preventDefault();toast(exc.message);}});
$('expression').addEventListener('keydown',event=>{if(event.key==='Enter'&&!event.shiftKey&&!event.isComposing){event.preventDefault();event.stopPropagation();if(!event.repeat)evaluate();}if(event.key==='Escape'){event.preventDefault();if(calcSession)cancelCalc();else if(busy)engine.cancel();else{$('expression').value='';preview();}}});
$('clear').onclick=()=>{if(calcSession){cancelCalc();return;}expressionUndo.push(value('expression'));$('expression').value='';committed=false;lastResult=null;activeHistoryEntry=null;inputAnswer=null;$('answer').replaceChildren();$('note').textContent='';$('commit-indicator').textContent='';preview();};
$('undo').onclick=()=>{const undo=undoStack();if(undo.length){$('expression').value=undo.pop();preview();}};
async function clipboard(text) { try{await navigator.clipboard.writeText(text);toast('복사했습니다.');}catch{const field=element('textarea');field.value=text;openDialog('복사할 텍스트',field);field.select();} }
$('copy').onclick=()=>{const f=$('expression');clipboard(f.value.slice(f.selectionStart,f.selectionEnd)||f.value);};
$('cut').onclick=()=>{const field=$('expression'),start=field.selectionStart,end=field.selectionEnd;if(start!==end){clipboard(field.value.slice(start,end));undoStack().push(field.value);field.setRangeText('',start,end,'end');committed=false;preview();}};
$('typing-toggle').onclick=()=>{typing=!typing;document.documentElement.dataset.typing=String(typing);$('expression').readOnly=!typing;setText($('typing-toggle'),typing?'Math input':'Keyboard');if(typing)$('expression').focus({preventScroll:true});};
$('insert-mode').onclick=()=>{overwrite=!overwrite;$('insert-mode').textContent=overwrite?'OVR':'INS';};
$('paste').onclick=async()=>{try{insert(latexInput(await navigator.clipboard.readText()));}catch{const content=element('div'),field=element('textarea');field.rows=4;field.setAttribute('aria-label',t('Paste expression'));content.append(field,control('Insert',()=>{try{insert(latexInput(field.value));$('dialog').close();}catch(exc){toast(exc.message);}}));openDialog('Paste',content);field.focus({preventScroll:true});}};
document.addEventListener('paste',event=>{if(event.defaultPrevented||value('mode')!=='scientific'||typing||$('dialog').open||$('settings-dialog').open||event.target.closest?.('input,select,textarea')&&event.target!==$('expression'))return;const text=event.clipboardData?.getData('text/plain')||event.clipboardData?.getData('text');if(!text)return;event.preventDefault();try{insert(latexInput(text));}catch(exc){toast(exc.message);}});
$('answer-copy').onclick=()=>lastResult&&clipboard(decimal?lastResult.decimal:lastResult.exact);
$('answer-insert').onclick=()=>{if(state.variables.Ans){mode('scientific');insert('Ans',null,{factor:true});}else toast('먼저 재사용 가능한 결과를 계산해 주세요.');};
$('exact-toggle').onclick=()=>{decimal=!decimal;$('exact-toggle').textContent=decimal?'≈ Decimal':'Exact';renderResult();};
$('screen-toggle').onclick=()=>{screenExpanded=!screenExpanded;document.documentElement.dataset.screenExpanded=String(screenExpanded);$('screen-toggle').classList.toggle('active',screenExpanded);};
function renderNotation(){const mode=state.resultDisplayMode,button=$('engineering-toggle');button.textContent=mode==='sci'?'SCI':'ENG';button.classList.toggle('active',mode!=='off');button.dataset.notation=mode;button.setAttribute('aria-label',`${t('Result notation')}: ${mode.toUpperCase()}`);}
$('engineering-toggle').onclick=()=>{state.resultDisplayMode={off:'eng',eng:'sci',sci:'off'}[state.resultDisplayMode];renderNotation();renderResult();persist();};
renderNotation();
$('grouping-toggle').onclick=()=>{grouping=!grouping;$('grouping-toggle').classList.toggle('active',grouping);renderResult();};
function renderDisplayShortcuts(){const toolbar=$('exact-toggle').parentElement;toolbar.querySelectorAll('[data-shortcut]').forEach(button=>button.remove());for(const shortcut of state.displayShortcuts){const button=control(shortcut.label,()=>performKey(shortcut.input));button.dataset.shortcut=shortcut.input;toolbar.insertBefore(button,$('answer-copy'));}toolbar.append($('shortcut-settings'));}
$('shortcut-settings').onclick=()=>{const content=element('div'),current=element('div'),search=element('input'),choices=element('div');search.placeholder=t('Find button or function');
  const keypad=[...topKeys(),...topFunctions(),...topFunctions(true),...scientificRows.flat(),...secondRows.flat(),...numericRows.flat()],options=[...keypad.flatMap(k=>[{label:k.title,input:k.input},...(k.alternate?[{label:k.secondary||k.alternate,input:k.alternate}]:[]),...(k.alpha?[{label:k.alpha,input:k.alpha}]:[])]),...Object.values(catalog).flat().map(input=>({label:input,input}))];
  function render(){current.replaceChildren();for(const [index,shortcut] of state.displayShortcuts.entries()){const row=element('div','','list-row');row.append(element('span',shortcut.label,'content'),control('←',()=>{if(index){[state.displayShortcuts[index-1],state.displayShortcuts[index]]=[state.displayShortcuts[index],state.displayShortcuts[index-1]];renderDisplayShortcuts();render();persist();}}),control('Delete',()=>{state.displayShortcuts.splice(index,1);renderDisplayShortcuts();render();persist();}));current.append(row);}choices.replaceChildren();for(const choice of options.filter(c=>`${c.label} ${c.input}`.toLowerCase().includes(search.value.toLowerCase())).slice(0,80))choices.append(control(choice.label,()=>{if(state.displayShortcuts.length>=12){toast('Use up to twelve shortcuts');return;}state.displayShortcuts.push(choice);renderDisplayShortcuts();render();persist();}));}
  search.oninput=render;content.append(current,search,choices);render();openDialog('Customize display buttons',content);};
renderDisplayShortcuts();
function modeDialog(){const choices=element('div','','mode-choices');for(const option of $('mode').options)choices.append(control(option.textContent,()=>{mode(option.value);$('dialog').close();}));openDialog('Workspace',choices);}
function matrixInsertDialog(){const content=element('div'),rows=element('input'),cols=element('input');for(const [input,name] of [[rows,'Rows / components'],[cols,'Column']]){input.type='number';input.min='1';input.max='9';input.value='2';const label=element('label',name);label.append(input);content.append(label);}content.append(control('Insert',()=>{const r=Number(rows.value),c=Number(cols.value);if(!Number.isInteger(r)||!Number.isInteger(c)||r<1||c<1||r>9||c>9)return;mode('scientific');insert(`[${Array.from({length:r},()=>`[${Array(c).fill('0').join(',')}]`).join(',')}]`);$('dialog').close();}));openDialog('Matrix',content);}
function calcPrompt(){
  $('answer').replaceChildren();
  $('commit-indicator').textContent=`${calcSession.names[calcSession.index]}?`;
  setText($('note'),'CALC · enter a value, then press = · AC cancels');preview();
  updateButtons();
  if(typing)$('expression').focus({preventScroll:true});
}
async function startCalc(){
  if(busy)return;
  if(calcSession){await submitCalcValue();return;}
  try{
    const source=latexInput(state.autoCloseBrackets?closeInputBrackets(value('expression')):value('expression')),tree=evaluationTree(source),names=calcVariables(tree,state.variables);
    if(!names.length){await evaluate(source);return;}
    calcSession={source,tree,names,index:0,undo:[],values:{},previousResult:lastResult,previousCommitted:committed};
    document.documentElement.dataset.calcActive='true';lastResult=null;committed=false;$('answer').replaceChildren();$('expression').value='';calcPrompt();persist();
  }catch(exc){error(exc.message);}
}
function cancelCalc(){
  const session=calcSession;if(!session)return;calcSession=null;delete document.documentElement.dataset.calcActive;
  if(busy)engine.cancel();
  $('expression').value=session.source;lastResult=session.previousResult;committed=session.previousCommitted;
  $('commit-indicator').textContent=committed?'=':'';$('note').textContent='';if(lastResult)renderResult();else $('answer').replaceChildren();preview();
  updateButtons();
}
async function submitCalcValue(){
  const session=calcSession;if(!session||busy)return;
  const fail=message=>{error(message);setText($('note'),'CALC · enter a value, then press = · AC cancels');};
  try{
    const name=session.names[session.index],source=value('expression').trim();
    const tree=source?parse(latexInput(state.autoCloseBrackets?closeInputBrackets(source):source)):Object.hasOwn(state.variables,name)?{kind:'symbol',value:name}:parse('0');
    const numeric=await engine.execute({...requestOptions(),tree});if(calcSession!==session)return;
    if(!numeric.ok){fail(numeric.error);return;}
    if(numeric.symbolic||!numeric.resultAst){fail('Enter a numeric value');return;}
    state.variables[name]=numeric.resultAst;session.values[name]=numeric.exact;persist();
    if(session.index<session.names.length-1){session.index++;session.undo=[];$('expression').value='';calcPrompt();return;}
    const result=await engine.execute({...requestOptions(),variables:calcBindings(state.variables),tree:session.tree});if(calcSession!==session)return;
    if(!result.ok){fail(result.error);return;}
    calcSession=null;delete document.documentElement.dataset.calcActive;$('expression').value=session.source;preview();
    showResult({...result,calcValues:session.values},session.source);
  }catch(exc){if(calcSession===session)fail(exc.message);}
}
async function performKey(input){
  if(calcSession&&busy&&input!=='AC')return;
  if(state.haptics)navigator.vibrate?.(12);
  if(state.sound)try{const context=keyAudioContext||(keyAudioContext=new (window.AudioContext||window.webkitAudioContext)()),oscillator=context.createOscillator(),gain=context.createGain();oscillator.frequency.value=1200;gain.gain.value=.025;oscillator.connect(gain);gain.connect(context.destination);oscillator.start();oscillator.stop(context.currentTime+.025);oscillator.onended=()=>{oscillator.disconnect();gain.disconnect();};}catch{}
  const jumps={'Scientific/CAS':'scientific',Graph:'graph',Python:'python',Matrix:'matrix',Vector:'vector',Statistics:'statistics',Programmer:'programmer',Units:'units',Constants:'constants',Equations:'equation'};
  if(input==='SHIFT'){shift=!shift;alpha=false;renderKeypad();return;}
  if(input==='ALPHA'){alpha=!alpha;shift=false;renderKeypad();return;}
  if(input==='SECOND'){state.secondKeys=!state.secondKeys;shift=false;alpha=false;persist();renderKeypad();return;}
  if(calcSession){
    if(input==='AC'){cancelCalc();return;}
    if(input==='='||input==='CALC'){await submitCalcValue();return;}
    if(['MODE','STO','RCL','Clear','CLR ALL','SOLVE','RELATION','ENG','ENG−','S⇔D','MIXED','M+','M−','INS','MATRIX_INPUT','TO_GRAPH',...Object.keys(jumps)].includes(input)){shift=false;alpha=false;renderKeypad();return;}
  }
  if(input==='HYP'){hyperbolic=!hyperbolic;shift=false;alpha=false;renderKeypad();return;}
  if(hyperbolic&&/^(?:a?sin|a?cos|a?tan)\(\)$/.test(input))input=input.replace('()','h()');
  if(input==='CALC')startCalc();
  else if(input==='=')evaluate();
  else if(input==='SOLVE')evaluate(`solve(${value('expression')||'x'},x)`);
  else if(input==='MODE')modeDialog();
  else if(input==='RCL'||input==='STO'||input==='Clear')variablesDialog();
  else if(input==='AC')$('clear').click();
  else if(input==='CLR ALL'){state.variables={};state.tapeClearedAt=Date.now();$('clear').click();persist();}
  else if(input==='S⇔D')$('exact-toggle').click();
  else if(input==='MIXED'){mixed=!mixed;decimal=false;$('exact-toggle').textContent='Exact';renderResult();}
  else if(input==='INS')$('insert-mode').click();
  else if(input==='NEG'){if(committed){$('expression').value='';committed=false;}insert('-');}
  else if(input==='DEL'){const f=$('expression'),start=f.selectionStart,end=f.selectionEnd;undoStack().push(f.value);f.setRangeText('',start===end?Math.max(0,start-1):start,end,'end');committed=false;preview();}
  else if(['LEFT','RIGHT','UP','DOWN'].includes(input)){
    const f=$('expression');let start=f.selectionStart,end=f.selectionEnd;
    const position=typing?null:moveMathCursor(f.value,start,end,input);
    if(position!==null)start=end=position;
    else if(input==='LEFT'||input==='RIGHT')start=end=Math.max(0,Math.min(f.value.length,(input==='LEFT'?start:end)+(input==='LEFT'?-1:1)));
    else try{const nodes=[];const visit=n=>{if(n.start<=start&&n.end>=end)nodes.push(n);n.args?.forEach(visit);};visit(parse(f.value,{allowHoles:true}));nodes.sort((a,b)=>(a.end-a.start)-(b.end-b.start));const selected=input==='UP'?nodes.find(n=>n.start<start||n.end>end):nodes[0]?.args?.[0];if(selected){start=selected.start;end=selected.end;}}catch{}
    f.setSelectionRange(start,end);if(typing)f.focus({preventScroll:true});preview();
  }
  else if(input==='MATRIX_INPUT')matrixInsertDialog();
  else if(input==='TO_GRAPH'){$('graph-source').value=value('expression')||'x';mode('graph');}
  else if(jumps[input])mode(jumps[input]);
  else if(input==='M+'||input==='M−'){
    if(busy)return;const source=state.autoCloseBrackets?closeInputBrackets(value('expression')||'0'):value('expression')||'0';try{const operand=committed&&lastResult?.resultAst?lastResult:await engine.execute({...requestOptions(),tree:evaluationTree(source)});if(!operand.ok||!operand.resultAst){error(operand.error||'This result cannot be stored in memory');return;}if(!committed)showResult(operand,source);const result=await engine.execute({...requestOptions(),tree:{kind:'binary',value:input==='M+'?'+':'-',args:[state.variables.M||parse('0'),operand.resultAst]}});if(result.ok&&result.resultAst){state.variables.M=result.resultAst;persist();toast('M에 저장했습니다.');}else error(result.error);}catch(exc){error(exc.message);}
  }
  else if(input==='ENG'||input==='ENG−'){if(lastResult?.resultAst){const result=await engine.execute({...requestOptions(),tree:parse(`eng(Ans${input==='ENG−'?',3':''})`)});showResult(result);}}
  else if(input==='DMS'){if(lastResult?.resultAst){const result=await engine.execute({...requestOptions(),tree:parse('dms(Ans)')});showResult(result);}}
  else if(input==='DMS_INPUT'){const markers=value('expression').match(/[°′″]/g)||[];insert(['°','′','″'][markers.length%3]);}
  else if(input==='RANDOM')insert(String(Math.random()));
  else if(input==='RELATION')insert('=');
  else if(input==='*10^()'){const source=value('expression');insert(source&&!committed?input:'1'+input,(source&&!committed?input:'1'+input).indexOf('(')+1);}
  else {
    // Parenthesis templates place the cursor in their first empty argument.
    insert(input,input.includes('(')?input.indexOf('(')+1:null,{factor:true});
  }
  shift=false;alpha=false;hyperbolic=false;renderKeypad();
}
function renderKeypad() {
  const signature=`${state.secondKeys}:${shift}:${alpha}:${hyperbolic}`;if(keypadSignature===signature)return;keypadSignature=signature;
  const press=k=>performKey(k.input==='CALC'&&alpha?'RELATION':alpha&&k.alpha?k.alpha:shift&&k.alternate?k.alternate:k.input);
  if($('keypad').dataset.page!==(state.secondKeys?'2':'1'))buildKeypad($('keypad'),{second:state.secondKeys,shift,alpha,hyperbolic,press,longPress:k=>{if(['SHIFT','ALPHA','SECOND'].includes(k.input)||!k.alternate)press(k);else{shift=false;alpha=false;performKey(k.alternate);}}});
  else updateKeypadState($('keypad'),{second:state.secondKeys,shift,alpha,hyperbolic});
  $('key-modifier').textContent=shift?'SHIFT':alpha?'ALPHA':hyperbolic?'HYP':state.secondKeys?'2ND':'';
  $('key-modifier').classList.toggle('alpha',alpha);translateDOM($('keypad'));updateButtons();
}

function historyDialog() {
  const content=element('div'),search=element('input');search.placeholder='수식 또는 결과 검색';search.setAttribute('aria-label','기록 검색');const list=element('div');let favorites=false;
  const filter=control('즐겨찾기만',()=>{favorites=!favorites;setText(filter,favorites?'전체 기록':'즐겨찾기만');render();});
  function render(){list.replaceChildren();for(const item of state.history.filter(h=>(!favorites||h.star)&&`${h.source} ${h.exact}`.toLowerCase().includes(search.value.toLowerCase()))){const row=element('div','','list-row');const text=element('div','','content');text.append(element('code',item.source),element('div',`= ${item.exact}`));row.append(text,control(item.star?'★':'☆',()=>{item.star=!item.star;persist();render();}),control('사용',()=>{mode('scientific');$('expression').value=item.source;preview();$('dialog').close();}));list.append(row);}if(!list.childElementCount)list.append(element('p','기록이 없습니다.','hint'));}
  search.oninput=render;content.append(search,filter,control('기록 삭제',()=>{state.history=state.history.filter(item=>item.star);activeHistoryEntry=null;renderTape();persist();render();}),list);render();openDialog('History',content);
}
function catalogDialog() {
  const content=element('div','','catalog-content'),search=element('input'),tabs=element('div','','catalog-tabs'),list=element('div','','catalog-scroll');search.placeholder='함수 검색';search.setAttribute('aria-label','함수 검색');let category='Scientific';
  function entries(){if(search.value)return [...new Set([...Object.values(catalog).flat(),...custom()])].filter(s=>s.toLowerCase().includes(search.value.toLowerCase()));if(category==='Recent')return state.recent;if(category==='Favorites')return state.favorites;if(category==='Custom')return custom();return catalog[category]||[];}
  function custom(){return Object.entries(state.functions).map(([name,f])=>`${name}(${','.repeat(Math.max(0,f.parameters.length-1))})`);}
  function render(){list.replaceChildren();tabs.querySelectorAll('button').forEach(b=>b.classList.toggle('active',b.dataset.category===category));for(const source of entries()){
    const row=element('div','','catalog-entry');row.append(control(source,()=>{
      state.recent=[source,...state.recent.filter(s=>s!==source)].slice(0,50);persist();
      if(value('mode')==='python'){
        const field=$('python-source'),at=field.selectionStart;let draft=field.value;const inserted=`calc.${source}`;
        draft=draft.slice(0,at)+inserted+draft.slice(field.selectionEnd);
        if(!draft.includes('import calcmax_catalog as calc'))draft='import calcmax_catalog as calc\nfrom calcmax_catalog import x, y, z, t, pi\n'+draft;
        field.value=draft;field.focus();persist();
      }else{mode('scientific');insert(source,source==='rnd()'?source.length:source.includes('[]')?source.indexOf('[]')+1:source.indexOf('(')+1);}
      $('dialog').close();
    }),control(state.favorites.includes(source)?'★':'☆',()=>{state.favorites=state.favorites.includes(source)?state.favorites.filter(s=>s!==source):[...state.favorites,source];persist();render();}));list.append(row);
  }}
  for(const name of ['Recent','Favorites','Custom',...Object.keys(catalog)]){const tab=control(name,()=>{category=name;render();});tab.dataset.category=name;tabs.append(tab);}
  const tools=element('div','','catalog-tools');tools.append(clearableCatalogSearch(search),control('Help',catalogHelpDialog));
  search.oninput=()=>{list.scrollTop=0;render();};content.append(tools,tabs,list,element('p','빈 인수에 값을 입력하세요. 예: diff(sin(x^2),x), normcdf(-1.96,1.96)','hint'));
  render();openDialog('Catalog',content);
}
const helpDocuments={};
async function catalogHelpDialog(){
  const language=getLanguage(),content=element('div','','catalog-content'),search=element('input'),tools=element('div','','catalog-tools'),list=element('div','','catalog-scroll');
  search.placeholder=t('Search');search.setAttribute('aria-label',t('Search'));tools.append(clearableCatalogSearch(search),control('Catalog',catalogDialog));content.append(tools,list);list.append(element('p','Loading the catalog reference...','hint'));openDialog('Function catalog - help',content);
  try{
    if(!helpDocuments[language]){const response=await fetch(language==='ko'?'./catalog_help_ko.md':'./catalog_help.md');if(!response.ok)throw new Error('Could not load function help');helpDocuments[language]=await response.text();}
    if(!content.isConnected)return;
    function render(){
      list.replaceChildren();
      for(const block of parseCatalogHelp(helpDocuments[language],search.value)){
        const row=element('div','','catalog-help-block');
        if(block.kind==='entry'){
          row.append(element('code',block.signature),element('p',block.text,'hint'));
          if(block.example)row.append(control(`${t('Example:')} ${block.example}`,()=>{mode('scientific');$('clear').click();insert(helpExampleInput(block.example));$('dialog').close();},'catalog-example'));
        }else row.append(element(block.kind==='heading'?'h2':block.kind==='category'?'h3':'p',block.text));
        list.append(row);
      }
      if(!list.childElementCount)list.append(element('p','No matching entries','hint'));
    }
    search.oninput=()=>{list.scrollTop=0;render();};render();
  }catch(exc){if(content.isConnected){list.replaceChildren(element('p',exc.message,'error'),control('Retry',catalogHelpDialog));}}
}
function variablesDialog() {
  const content=element('div'),name=element('input');name.placeholder='변수 이름 · A, b, M';name.setAttribute('aria-label','변수 이름');const expression=element('input');expression.placeholder='값 또는 수식';expression.setAttribute('aria-label','변수 수식');const list=element('div');
  function store(ast){if(!/^[A-Za-z][A-Za-z0-9_]*$/.test(name.value)||name.value==='Ans')throw new Error('Ans 이외의 영문 변수 이름을 입력하세요.');state.variables[name.value]=ast;persist();render();}
  function render(){list.replaceChildren();for(const key of Object.keys(state.variables)){const row=element('div','','list-row'),shown=element('div','','content formula-preview');renderFormulas(shown,[`${key}=${astSource(state.variables[key])}`],{digits:state.digits});shown.onclick=()=>{name.value=key;expression.value=astSource(state.variables[key]);};row.append(shown,control('삽입',()=>{mode('scientific');insert(key,null,{factor:true});$('dialog').close();}),control('삭제',()=>{delete state.variables[key];persist();render();}));list.append(row);}for(const [key,data] of Object.entries(state.datasets)){list.append(control(`${key} · ${t('Stats data')}`,()=>{try{const rows=numericStatisticsRows(csvRows(data)),source=rows[0].length===1?'['+rows.map(row=>row[0]).filter(Boolean).join(',')+']':'['+rows.filter(row=>row.every(Boolean)).map(row=>'['+row.join(',')+']').join(',')+']';mode('scientific');insert(source,null,{factor:true});$('dialog').close();}catch(exc){error(exc.message);}}));}}
  const assumption=element('select');for(const key of ['none','real','positive','negative','integer','nonzero']){const option=element('option',key);option.value=key;assumption.append(option);}assumption.setAttribute('aria-label',t('Symbol assumption'));
  content.append(name,expression,control('수식 저장',()=>{try{store(parse(latexInput(expression.value)));}catch(exc){toast(exc.message);}}),control('현재 결과 STO',()=>{try{if(!lastResult?.resultAst)throw new Error('저장 가능한 결과가 없습니다.');store(lastResult.resultAst);}catch(exc){toast(exc.message);}}),assumption,control('Set assumption',()=>{if(!/^[A-Za-z][A-Za-z0-9_]*$/.test(name.value)){toast('Enter a valid variable name');return;}state.assumptions[name.value]=assumption.value==='none'?[]:[assumption.value];persist();}),list);render();openDialog('RCL / STO',content);
}
function settingsDialog() {
  const content=element('div');
  for(const [key,label,min,max] of [['precision','내부 유효 숫자',3,200],['digits','표시 소수 자릿수',2,200]]){const input=element('input');input.type='number';input.min=min;input.max=max;input.value=state[key];input.dataset.setting=key;input.onchange=()=>{state[key]=Math.max(min,Math.min(max,Number(input.value)||min));state.digits=Math.min(state.precision,state.digits);input.value=state[key];$('digits-indicator').textContent=`≤ ${state.digits} digits`;persist();refreshDisplays();};const holder=element('label',label);holder.append(input);content.append(holder);}
  for(const [key,label,min,max] of [['inputFont','Input font',10,42],['outputFont','Output font',10,48]]){const input=element('input');input.type='range';input.min=min;input.max=max;input.value=state[key];input.dataset.setting=key;input.oninput=()=>{state[key]=Number(input.value);applyFonts();persist();};const holder=element('label',label);holder.append(input);content.append(holder);}
  for(const [key,label] of [['autoCloseBrackets','Bracket auto-close'],['persistHistory','Save history locally'],['haptics','Key vibration'],['sound','Key sound']]){const input=element('input');input.type='checkbox';input.checked=state[key];input.dataset.setting=key;input.onchange=()=>{state[key]=input.checked;persist();};const holder=element('label',label,'check');holder.append(input);content.append(holder);}
  content.append(element('p','계산 기록, 변수, 함수, 작업 내용은 이 브라우저에 저장됩니다. 전체 백업에는 Python 코드도 포함됩니다.','hint'),control('전체 백업 내보내기',()=>{persist();downloadFile('calcmax-backup.json',JSON.stringify(state,null,2),'application/json');}),control('백업 가져오기',()=>pickFile('.json',async file=>{
    const backup=JSON.parse(await file.text());
    if(!backup||typeof backup!=='object'||!backup.fields||!backup.variables||!backup.functions)throw new Error('CalcMax 웹 백업 파일이 아닙니다.');
    // All restored names and math are still validated by the parser/engine.
    if(!writeState(backup))throw new Error('백업을 저장할 공간이 부족합니다.');location.reload();
  })),control('변수 초기화',()=>{state.variables={};persist();toast('변수를 초기화했습니다.');}));
  $('settings-body').replaceChildren(content);translateDOM($('settings-dialog'));$('settings-dialog').showModal();
}
$('settings-close').onclick=()=>$('settings-dialog').close();
$('history-button').onclick=historyDialog;$('catalog-button').onclick=catalogDialog;$('variables-button').onclick=variablesDialog;$('settings-button').onclick=settingsDialog;

async function pickFile(accept,handler) { const field=$('file-input');field.accept=accept;field.value='';field.onchange=async()=>{const file=field.files[0];if(!file)return;try{await handler(file);}catch(exc){toast(exc.message);}};field.click(); }
$('python-new').onclick=()=>{$('python-source').value='';$('python-output').textContent='';persist();};
$('python-open').onclick=()=>pickFile('.py,text/x-python',async file=>{$('python-source').value=await file.text();persist();});
$('python-save').onclick=()=>downloadFile('calcmax.py',value('python-source'),'text/x-python');
$('python-source').onkeydown=event=>{if(event.key==='Enter'&&(event.ctrlKey||event.metaKey)){event.preventDefault();run('python');}};
const pythonToolbar=element('div','','form-row'),pythonSuggestions=element('div','','form-row');$('python-source').closest('label').insertAdjacentElement('afterend',pythonSuggestions);$('python-source').closest('label').insertAdjacentElement('beforebegin',pythonToolbar);
const pythonEditor=bindPythonEditor($('python-source'),{toolbar:pythonToolbar,suggestions:pythonSuggestions,onEdit:persist,copy:clipboard,paste:()=>navigator.clipboard.readText()});

function renderMatrix() {
  const vector=value('mode')==='vector',rows=Number(value('matrix-rows')),cols=vector?1:Number(value('matrix-cols'));
  $('matrix-cols-label').hidden=vector;
  const previous=value('matrix-op'),options=vector?['norm','normalize','dot','cross','angle','projection']:['det','inverse','transpose','rank','trace','ref','rref','eigenvalues','eigenvectors','lu','qr','cholesky','nullspace','charpoly','add','subtract','multiply','linsolve'];
  $('matrix-op').replaceChildren(...options.map(name=>{const option=element('option',name);option.value=name;return option;}));
  $('matrix-op').value=options.includes(previous)?previous:options[0];
  $('vector-other-label').hidden=!['dot','cross','angle','projection','add','subtract','multiply','linsolve'].includes(value('matrix-op'));
  $('matrix-grid').style.gridTemplateColumns=`repeat(${cols}, minmax(60px, 1fr))`;
  $('matrix-grid').replaceChildren();
  for(let row=0;row<rows;row++)for(let col=0;col<cols;col++){
    const input=element('input'),key=`${vector?'v':'m'}-${row}-${col}`;input.value=state.matrixCells[key]??(vector?String(row+1):row===col?'1':'0');input.dataset.cell=key;input.setAttribute('aria-label',getLanguage()==='ko'?`${row+1}행 ${col+1}열`:`Row ${row+1}, column ${col+1}`);input.oninput=()=>{state.matrixCells[key]=input.value;persist();};$('matrix-grid').append(input);
  }
}
for(const id of ['matrix-rows','matrix-cols']) {for(let i=1;i<=9;i++)$(id).append(element('option',String(i)));$(id).value=id==='matrix-rows'?'3':'3';restoreSelect(id);$(id).onchange=()=>{renderMatrix();persist();};}
function matrixExpression(){const vector=value('mode')==='vector',columns=vector?1:Number(value('matrix-cols')),cells=Array.from($('matrix-grid').querySelectorAll('input')).map(input=>input.value||'0');if(vector)return `[${cells.join(',')}]`;const rows=[];for(let i=0;i<cells.length;i+=columns)rows.push(`[${cells.slice(i,i+columns).join(',')}]`);return `[${rows.join(',')}]`;}
$('matrix-op').onchange=()=>{$('vector-other-label').hidden=!['dot','cross','angle','projection','add','subtract','multiply','linsolve'].includes(value('matrix-op'));refreshWorkspaceMath();};
function datasetsList(){const previous=value('dataset-list');$('dataset-list').replaceChildren(element('option','새 데이터'));$('dataset-list').firstChild.value='';for(const name of Object.keys(state.datasets)) {const option=element('option',name);option.value=name;$('dataset-list').append(option);}if(state.datasets[previous])$('dataset-list').value=previous;}
$('dataset-list').onchange=()=>{const name=value('dataset-list');if(state.datasets[name]){$('statistics-data').value=state.datasets[name];$('dataset-name').value=name;refreshWorkspaceMath();persist();}};
$('dataset-save').onclick=()=>{const name=value('dataset-name').trim();if(!name){toast('데이터 이름을 입력하세요.');return;}state.datasets[name]=value('statistics-data');datasetsList();$('dataset-list').value=name;persist();toast('데이터를 저장했습니다.');};
$('dataset-delete').onclick=()=>{delete state.datasets[value('dataset-list')];datasetsList();persist();};
$('csv-open').onclick=()=>pickFile('.csv,.tsv,text/csv',async file=>{const rows=csvRows((await file.text()).replace(/^\uFEFF/,''),{maxColumns:100,skipHeader:false}),content=element('div'),columns=[],header=element('input');header.type='checkbox';header.checked=rows[0].every(cell=>cell!==''&&!Number.isFinite(Number(cell)))&&rows.slice(1).some(row=>row.some(cell=>cell!==''&&Number.isFinite(Number(cell))));const headerLabel=element('label','Skip header row','check');headerLabel.append(header);content.append(headerLabel);for(let index=0;index<rows[0].length;index++){const input=element('input');input.type='checkbox';input.checked=index<3;columns.push(input);const label=element('label',`${t('Column')} ${index+1}: ${rows[0][index]}`,'check');label.append(input);content.append(label);}content.append(control('Import CSV',()=>{const selected=columns.map((input,i)=>input.checked?i:null).filter(i=>i!==null);if(!selected.length||selected.length>3){toast('Select one to three columns');return;}$('statistics-data').value=rows.slice(header.checked?1:0).map(row=>selected.map(i=>row[i].includes(',')?'"'+row[i].replace(/"/g,'""')+'"':row[i]).join(',')).join('\n');$('dataset-name').value=file.name.replace(/\.(csv|tsv)$/i,'');refreshWorkspaceMath();persist();$('dialog').close();}));openDialog('Import CSV',content);});
$('csv-save').onclick=()=>downloadFile(`${value('dataset-name')||'calcmax-data'}.csv`,value('statistics-data'),'text/csv');
function dataRows(){return csvRows(value('statistics-data'));}
function statisticsExpression(op=value('statistics-op')){return statisticsCommand(value('statistics-data'),{op,column:Number(value('statistics-column')),extra:value('statistics-extra')||'0',tail:value('statistics-tail'),sigma:value('statistics-sigma'),sigmaY:value('statistics-sigma-y'),regression:value('regression-kind'),formula:value('regression-formula'),variable:value('regression-variable'),initials:value('regression-initials'),grouping:value('statistics-grouping'),firstGroup:value('statistics-first-group'),secondGroup:value('statistics-second-group')});}
function distributionExpression(){return distributionCommand(Object.fromEntries(['family','query','x','a','b','p','mean','sigma','df','df2','trials','success','lambda','k'].map(name=>[name,value('distribution-'+name)])));}
function equationSource(){return value('equation-form')==='general'?value('equation-source'):polynomialEquation(['equation-a','equation-b','equation-c','equation-d'].slice(0,Number(value('equation-form'))+1).map(value),value('equation-variable'));}
function tipExpression(){return tipCommand({bill:value('tip-amount'),percent:value('tip-percent'),fixed:value('tip-fixed'),tax:value('tip-tax'),people:value('tip-people'),method:value('tip-method'),whole:$('tip-whole').checked});}
function tipMethodControls(){const fixed=value('tip-method')==='amount';$('tip-percent').disabled=fixed;$('tip-fixed').disabled=!fixed;}
$('tip-method').onchange=tipMethodControls;tipMethodControls();

function refreshWorkspaceMath(){
  const targets=[['equation-source',()=>equationSource().split(/\r?\n/)],['function-body',()=>[`${value('function-name')}(${value('function-parameters')})=${value('function-body')}`]],['distribution-math',()=>[distributionExpression()]],['matrix-grid',()=>[matrixExpression()]],['vector-other',()=>[value('vector-other')]],['unit-value',()=>[`convert(${value('unit-value')},${value('unit-from')},${value('unit-to')})`]]];
  for(const [id,sources] of targets){const input=$(id);let preview=id==='distribution-math'?input:$(id+'-math');if(!preview){preview=element('div','','formula-preview');preview.id=id+'-math';input.closest('label')?.insertAdjacentElement('afterend',preview)||input.insertAdjacentElement('afterend',preview);}try{renderFormulas(preview,sources(),{digits:state.digits});}catch{preview.replaceChildren();preview.hidden=true;}}
}
function applyFonts(){document.documentElement.style.setProperty('--input-font',state.inputFont+'px');document.documentElement.style.setProperty('--output-font',state.outputFont+'px');renderInputCursor();}
function refreshDisplays(){renderResult();refreshWorkspaceMath();graphs.render();if(statisticsGraph)statisticsPlot($('statistics-plot'),statisticsGraph.rows,{type:value('statistics-plot-type'),digits:state.digits,curve:statisticsGraph.curve});}
applyFonts();
for(const field of document.querySelectorAll('main input,main textarea,main select'))if(field.id!=='expression'&&!field.id.startsWith('graph-')&&!field.id.startsWith('python-'))for(const name of ['input','change'])field.addEventListener(name,refreshWorkspaceMath);

function equationControls(){const type=value('equation-form'),method=value('equation-kind');$('equation-coefficients').hidden=type==='general';$('equation-source').closest('label').hidden=type!=='general';for(const [index,id] of ['equation-a','equation-b','equation-c','equation-d'].entries())$(id).closest('label').hidden=type!=='general'&&index>Number(type);$('equation-extra').closest('label').hidden=!['nsolve','dsolve'].includes(method);$('equation-initial').closest('label').hidden=method!=='dsolve';$('equation-hint').closest('label').hidden=method!=='pdsolve';refreshWorkspaceMath();}
$('equation-form').onchange=equationControls;
$('equation-kind').onchange=()=>{const kind=value('equation-kind');if(['dsolve','pdsolve'].includes(kind)){$('equation-form').value='general';$('equation-source').value=kind==='dsolve'?'diff(y(t),t)=y(t)':'diff(u(x,y),x)+diff(u(x,y),y)=0';$('equation-variable').value=kind==='dsolve'?'y(t)':'u(x,y)';$('equation-extra').value=kind==='dsolve'?'t':'';}else if(value('equation-variable').includes('(')){$('equation-source').value='x^2-5x+6=0';$('equation-variable').value='x';$('equation-extra').value='0,1';}equationControls();};
function distributionControls(){const family=value('distribution-family'),discrete=['binomial','poisson','geometric'].includes(family),queries=discrete?(family==='binomial'?['pdf','cdf','list-pdf','list-cdf']:['pdf','cdf']):['normal','t'].includes(family)?['pdf','cdf','interval','quantile']:['pdf','cdf','interval'];for(const option of $('distribution-query').options)option.disabled=!queries.includes(option.value);if(!queries.includes(value('distribution-query')))$('distribution-query').value='cdf';const query=value('distribution-query'),shown=new Set(discrete?(query.startsWith('list')?[]:['k']):query==='interval'?['a','b']:query==='quantile'?['p']:['x']);for(const name of family==='normal'?['mean','sigma']:family==='f'?['df','df2']:['t','chi2'].includes(family)?['df']:family==='binomial'?['trials','success']:family==='poisson'?['lambda']:['success'])shown.add(name);for(const name of ['x','a','b','p','mean','sigma','df','df2','trials','success','lambda','k'])$('distribution-'+name).closest('label').hidden=!shown.has(name);refreshWorkspaceMath();}
$('distribution-family').onchange=distributionControls;$('distribution-query').onchange=distributionControls;
$('distribution-insert').onclick=()=>{mode('scientific');$('expression').value=distributionExpression();committed=false;preview();};
$('regression-kind').onchange=()=>{$('regression-custom').hidden=value('regression-kind')!=='custom';refreshWorkspaceMath();};
$('regression-custom').hidden=value('regression-kind')!=='custom';
$('regression-clear').onclick=()=>{statisticsGraph=null;$('regression-caption').replaceChildren();$('regression-transfer').hidden=true;if(!$('statistics-plot').hidden)$('statistics-plot-run').click();};
$('statistics-op').onchange=()=>{if(['tinterval','zinterval'].includes(value('statistics-op'))&&value('statistics-extra')==='0')$('statistics-extra').value='95';refreshWorkspaceMath();};
function statisticsGrid(){const rows=value('statistics-data').trim()?dataRows():[],table=element('table');for(const [index,row] of rows.entries()){const tr=element('tr');for(const [column,cell] of row.entries()){const td=element('td'),input=element('input');input.value=cell;input.setAttribute('aria-label',`${index+1}, ${column+1}`);input.oninput=()=>{rows[index][column]=input.value;$('statistics-data').value=rows.map(r=>r.map(s=>s.includes(',')?'"'+s.replace(/"/g,'""')+'"':s).join(',')).join('\n');refreshWorkspaceMath();persist();};td.append(input);tr.append(td);}tr.append(control('−',()=>{rows.splice(index,1);$('statistics-data').value=rows.map(r=>r.map(s=>s.includes(',')?'"'+s.replace(/"/g,'""')+'"':s).join(',')).join('\n');statisticsGrid();refreshWorkspaceMath();persist();}));table.append(tr);}$('statistics-grid').replaceChildren(table);}
$('statistics-table-toggle').onclick=()=>{
  const grid=$('statistics-grid'),tableMode=grid.hidden;
  if(tableMode)try{statisticsGrid();}catch(exc){error(exc.message);return;}
  grid.hidden=!tableMode;$('statistics-data').closest('label').hidden=tableMode;
  setText($('statistics-table-toggle'),tableMode?'Direct input':'Table editor');
  $('statistics-table-toggle').setAttribute('aria-pressed',String(tableMode));
};
$('statistics-add-row').onclick=()=>{const columns=value('statistics-data').split(/\r?\n/).find(s=>s.trim())?.split(',').length||1;$('statistics-data').value+='\n'+Array(columns).fill('0').join(',');if(!$('statistics-grid').hidden)statisticsGrid();refreshWorkspaceMath();persist();};
$('statistics-store').onclick=async()=>{const name=value('dataset-name').trim();if(!/^[A-Za-z][A-Za-z0-9_]*$/.test(name)){error('Dataset name must be a valid variable name');return;}await storeWorkspaceExpression(name,`[${dataRows().map(row=>row.length===1?row[0]:'['+row.join(',')+']').join(',')}]`);};
$('statistics-plot-run').onclick=()=>{try{statisticsGraph={rows:numericStatisticsRows(dataRows()),curve:statisticsGraph?.curve||[]};$('statistics-plot').hidden=false;statisticsPlot($('statistics-plot'),statisticsGraph.rows,{type:value('statistics-plot-type'),digits:state.digits,curve:statisticsGraph.curve});}catch(exc){error(exc.message);}};
$('statistics-plot-type').onchange=()=>$('statistics-plot-run').click();
$('regression-transfer').onclick=()=>{if(!statisticsGraph?.fit)return;const variable=value('regression-kind')==='custom'?value('regression-variable'):'x';$('graph-kind').value='cartesian';$('graph-source').value=statisticsGraph.fit.replaceAll('**','^').replace(new RegExp(`\\b${variable}\\b`,'g'),'x');mode('graph');graphs.run();};
async function storeWorkspaceExpression(name,source){if(busy||!engine.ready)return;try{const result=await engine.execute({...requestOptions(),tree:parse(source)});if(!result.ok||!result.resultAst){error(result.error||'No reusable result to store.');return;}state.variables[name]=result.resultAst;persist();toast('저장했습니다.');}catch(exc){error(exc.message);}}
$('matrix-store').onclick=()=>{const name=value('matrix-name').trim();if(!/^[A-Za-z][A-Za-z0-9_]*$/.test(name)||name==='Ans'){error('Enter a valid variable name');return;}storeWorkspaceExpression(name,matrixExpression());};
$('matrix-load').onclick=()=>{let tree=state.variables[value('matrix-name')];if(tree?.kind==='restricted')tree=tree.args[0];if(tree?.kind!=='list'){error('Select a stored matrix or vector');return;}const vector=value('mode')==='vector',rows=tree.args,columns=vector?1:rows[0]?.args?.length;if(!columns||rows.length>9||columns>9||!vector&&rows.some(row=>row.kind!=='list'||row.args.length!==columns)){error('Select a stored matrix or vector');return;}$('matrix-rows').value=String(rows.length);$('matrix-cols').value=String(columns);rows.forEach((row,i)=>(vector?[row]:row.args).forEach((cell,j)=>state.matrixCells[`${vector?'v':'m'}-${i}-${j}`]=astSource(cell)));renderMatrix();refreshWorkspaceMath();persist();};
$('matrix-clear').onclick=()=>{$('matrix-grid').querySelectorAll('input').forEach(input=>{input.value='0';state.matrixCells[input.dataset.cell]='0';});refreshWorkspaceMath();persist();};
$('matrix-insert').onclick=()=>{const source=matrixExpression();mode('scientific');$('expression').value=source;committed=false;preview();};
$('units-swap').onclick=()=>{const from=value('unit-from');$('unit-from').value=value('unit-to');$('unit-to').value=from;refreshWorkspaceMath();persist();};
$('unit-category').onchange=()=>{const units=unitGroups[value('unit-category')]||null;for(const id of ['unit-from','unit-to']){for(const option of $(id).options)option.hidden=units?!units.includes(option.value):false;if(units&&!units.includes(value(id)))$(id).value=units[id==='unit-from'?0:1];}refreshWorkspaceMath();persist();};
$('currency-swap').onclick=()=>{const from=value('currency-from');$('currency-from').value=value('currency-to');$('currency-to').value=from;const rate=Number(value('currency-rate'));if(rate>0)$('currency-rate').value=String(1/rate);refreshWorkspaceMath();persist();};
$('constants-search').oninput=()=>{const query=value('constants-search').toLowerCase();for(const row of $('constants-list').children)row.hidden=!row.dataset.search?.includes(query);};
equationControls();distributionControls();

function functionsList() {
  $('functions-list').replaceChildren();
  for(const [name,f] of Object.entries(state.functions)){const row=element('div','','list-row'),formula=element('div','','content formula-preview');renderFormulas(formula,[`${name}(${f.parameters.join(',')}) = ${f.source||astSource(f.body)}`],{digits:state.digits});row.append(formula,control('편집',()=>{$('function-name').value=name;$('function-parameters').value=f.parameters.join(',');$('function-body').value=f.source||astSource(f.body);refreshWorkspaceMath();}),control('Insert',()=>{mode('scientific');insert(`${name}(${','.repeat(Math.max(0,f.parameters.length-1))})`,name.length+1,{factor:true});}),control('삭제',()=>{delete state.functions[name];persist();functionsList();}));$('functions-list').append(row);}
}
$('function-clear').onclick=()=>{for(const id of ['function-name','function-parameters','function-body'])$(id).value='';refreshWorkspaceMath();persist();};
$('function-save').onclick=()=>{try{const name=value('function-name').trim(),parameters=value('function-parameters').split(',').map(p=>p.trim()),source=latexInput(value('function-body'));state.functions[name]=defineFunction(name,parameters,source);persist();functionsList();toast('함수를 저장했습니다.');}catch(exc){toast(exc.message);}};
$('function-export').onclick=()=>downloadFile('calcmax-functions.json',encodeFunctions(state.functions),'application/json');
$('function-import').onclick=()=>pickFile('.json',async file=>{const {functions,skipped}=decodeFunctions(await file.text());Object.assign(state.functions,functions);persist();functionsList();toast(`${t('Functions imported.')} ${Object.keys(functions).length} · ${skipped} ${t('skipped')}`);});


$('currency-fetch').onclick=async()=>{
  const from=value('currency-from').trim().toUpperCase(),to=value('currency-to').trim().toUpperCase();
  if(!/^[A-Z]{3}$/.test(from)||!/^[A-Z]{3}$/.test(to)){toast('3자리 통화 코드를 입력하세요.');return;}
  const button=$('currency-fetch');button.disabled=true;
  try{
    let rates=state.rates[from];
    if(!rates||Date.now()-rates.fetched>=86400000){const response=await fetch(`https://open.er-api.com/v6/latest/${from}`,{signal:AbortSignal.timeout(10000)});if(!response.ok)throw new Error('환율 요청에 실패했습니다.');const data=await response.json();if(data.result!=='success')throw new Error(data['error-type']||'환율을 가져올 수 없습니다.');rates={values:data.rates,reference:data.time_last_update_utc,fetched:Date.now()};state.rates[from]=rates;}
    if(!Number.isFinite(rates.values[to])||rates.values[to]<=0)throw new Error('지원하지 않는 대상 통화입니다.');
    $('currency-rate').value=String(rates.values[to]);$('currency-status').textContent=`${from} → ${to} · ${getLanguage()==='ko'?'기준':'As of'} ${rates.reference} · ExchangeRate-API`;persist();
  }catch(exc){toast(`${t(exc.message)} ${getLanguage()==='ko'?'수동 환율을 사용할 수 있습니다.':'You can use a manual rate.'}`);}finally{button.disabled=false;}
};

async function run(workspace) {
  try{
    persist();refreshWorkspaceMath();
    if(workspace==='graph'){await graphs.run();return;}
    if(workspace==='python') {
      $('python-output').textContent=t('실행 중…');const result=await engine.execute({...requestOptions(),action:'python',source:value('python-source'),inputs:value('python-input')===''?[]:value('python-input').split(/\r?\n/),filename:'calcmax.py'});$('python-output').textContent=[result.output,result.error&&t(result.error)].filter(Boolean).join('\n')||t('실행 완료');if(!result.ok)error(result.error);return;
    }
    if(workspace==='programmer') {
      const result=await engine.execute({...requestOptions(),action:'programmer',base:Number(value('programmer-base')),width:Number(value('programmer-width')),signed:$('programmer-signed').checked,a:value('programmer-a'),b:value('programmer-b'),op:value('programmer-op')});if(result.ok)$('programmer-output').textContent=Object.entries(result.bases).map(([base,n])=>`${base.padEnd(4)} ${n}`).join('\n');showResult(result);return;
    }
    if(workspace==='constants') {
      const result=await engine.execute({...requestOptions(),action:'constants'});if(!result.ok){error(result.error);return;}$('constants-list').replaceChildren();for(const c of result.constants){const row=element('div','','list-row');row.dataset.search=`${c.symbol} ${c.name} ${c.unit}`.toLowerCase();const text=element('div','','content');text.append(element('strong',`${c.symbol} · ${c.name}`),element('p',`${displayNumber(c.value.replace(/…$/,''),state.digits)} ${c.unit}`,'hint'));row.append(text,control('사용',()=>{mode('scientific');insert(c.symbol);}));$('constants-list').append(row);}return;
    }
    let source;
    if(workspace==='scientific'){await evaluate();return;}
    if(workspace==='equation'){source=equationCommand({kind:value('equation-kind'),source:equationSource(),variable:value('equation-variable').trim(),extra:value('equation-extra'),initial:value('equation-initial'),hint:value('equation-hint')});
    }else if(workspace==='matrix'){
      const op=value('matrix-op');source=['add','subtract','multiply'].includes(op)?`(${matrixExpression()})${{add:'+',subtract:'-',multiply:'*'}[op]}(${value('vector-other')})`:`${op}(${matrixExpression()}${['dot','cross','angle','projection','linsolve'].includes(op)?','+value('vector-other'):op==='charpoly'?',x':''})`;
    }else if(workspace==='statistics'||workspace==='regression')source=statisticsExpression(workspace==='regression'?'regression':value('statistics-op'));
    else if(workspace==='distribution')source=distributionExpression();
    else if(workspace==='units')source=`convert(${value('unit-value')},${value('unit-from')},${value('unit-to')})`;
    else if(workspace==='tip')source=tipExpression();
    else if(workspace==='currency'){source=`(${value('currency-amount')})*(${value('currency-rate')})`;}
    let result=await engine.execute({...requestOptions(),tree:parse(latexInput(source))});if(workspace==='tip'&&result.ok)result=moneyResult(result,Number(value('tip-people')));showResult(result,source,workspace==='equation'?equationSource():source);
    if(workspace==='regression'&&result.ok){
      statisticsGraph={rows:numericStatisticsRows(dataRows()),curve:result.curve||[],fit:result.exact};$('statistics-plot').hidden=false;statisticsPlot($('statistics-plot'),statisticsGraph.rows,{type:value('statistics-plot-type'),digits:state.digits,curve:statisticsGraph.curve});
      renderFormulas($('regression-caption'),[`y=${result.exact}`,...(result.correlation!==null&&result.correlation!==undefined?[`r=${result.correlation}`]:[]),...(result.parameters||[]).map(([name,n])=>`${name}=${n}`)],{digits:state.digits});$('regression-transfer').hidden=false;
    }
  }catch(exc){error(exc.message);}
}
document.querySelectorAll('[data-run]').forEach(button=>button.onclick=()=>run(button.dataset.run));
document.addEventListener('keydown',event=>{
  if(value('mode')==='scientific'&&!$('dialog').open&&!$('settings-dialog').open&&event.key==='Enter'&&!event.shiftKey&&!event.ctrlKey&&!event.metaKey&&!event.altKey&&!event.isComposing&&event.target.closest('.keypad')){
    event.preventDefault();event.stopPropagation();if(!event.repeat)evaluate();
  }
},true);
document.addEventListener('keydown',event=>{
  if(event.defaultPrevented||event.isComposing)return;
  if(value('mode')!=='scientific'||typing||$('dialog').open||$('settings-dialog').open||event.ctrlKey||event.metaKey||event.altKey||event.target.closest('input,select,textarea'))return;
  if(event.target.closest('button')&&['Enter',' '].includes(event.key))return;
  const action={Enter:'=',Backspace:'DEL',Delete:'DEL',ArrowLeft:'LEFT',ArrowRight:'RIGHT',ArrowUp:'UP',ArrowDown:'DOWN',Escape:'AC'}[event.key];
  if(action){event.preventDefault();if(event.key==='Escape'&&busy)engine.cancel();else performKey(action);}
  else if(event.key.length===1&&/[0-9A-Za-z.,+\-*/÷×^%!()[\]{}=<>°∞π_]/.test(event.key)){event.preventDefault();insert(event.key);}
});
document.querySelectorAll('main input,main select,main textarea').forEach(field=>field.addEventListener('change',persist));
window.addEventListener('pagehide',()=>{persist();clearTimeout(toast.timer);tapeFollow.dispose();graphs.dispose();});
window.addEventListener('resize',()=>{graphs.render();renderInputCursor();});
document.fonts?.addEventListener('loadingdone',renderInputCursor);
async function initialize() {
  try{const response=await fetch('./catalog.json');if(!response.ok)throw new Error('Catalog load failed');catalog=await response.json();}catch(exc){toast('Catalog가 없습니다. 빌드 스크립트를 실행해 주세요.');}
  const units='m km cm mm in inch ft yd mi m2 cm2 km2 ha acre m3 L mL galUS kg g mg lb oz K degC degF s sec min h hr day ms mps kph mph knot mps2 g0 Pa kPa bar atm N kN lbf J kJ cal kWh eV W kW Hz kHz MHz A amp ampere mA uA C coulomb mC uC V volt mV kV ohm Ω kohm kΩ Mohm MΩ S siemens mS F farad uF nF pF H henry mH uH Wb weber Vs T tesla mT uT mol mole mmol umol bit byte kB KiB MB MiB GB rad deg grad'.split(' ');
  for(const id of ['unit-from','unit-to']){units.forEach(unit=>{const option=element('option',unit);option.value=unit;$(id).append(option);});$(id).value=id==='unit-from'?'degF':'degC';restoreSelect(id);}
  for(const group of Object.keys(unitGroups)){const option=element('option',group);option.value=group;$('unit-category').append(option);}restoreSelect('unit-category');$('unit-category').onchange();
  datasetsList();functionsList();refreshWorkspaceMath();mode(value('mode'));renderKeypad();$('expression').readOnly=true;preview();$('digits-indicator').textContent=`≤ ${state.digits} digits`;translateDOM();
  if(location.protocol==='file:')error('WebAssembly는 정적 HTTP 서버가 필요합니다. python web/serve.py를 실행하고 http://localhost:8080을 여세요.');
}
function registerOfflineCache() {
  if('serviceWorker' in navigator && location.protocol!=='file:'){
    navigator.serviceWorker.register('./sw.js',{updateViaCache:'none'}).then(registration=>{
      if(registration.active)setText($('offline-status'),'오프라인 사용 가능');
      registration.addEventListener('updatefound',()=>{const worker=registration.installing;worker?.addEventListener('statechange',()=>{if(worker.state==='activated')setText($('offline-status'),'오프라인 사용 가능');});});
    }).catch(()=>{setText($('offline-status'),'브라우저 캐시 사용');});
  }
}
initialize();
