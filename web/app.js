import {parse,latexInput} from './parser.js';
import {EngineClient} from './engine-client.js';
import {mathDisplay} from './math-display.js';
import {plot,dataBounds} from './plot.js';
import {readState,writeState,downloadFile} from './storage.js';
import {t,setLanguage,getLanguage,initialLanguage,translateDOM,applyTheme,setText} from './i18n.js';
import {renderKeypad as buildKeypad} from './keypad.js';
import {expressionDisplay} from './expression-display.js';
import {calcVariables} from './calc-session.js';

const $=id=>document.getElementById(id);
const value=id=>$(id).value;
const saved=readState();
const objectOrEmpty=o=>o && typeof o==='object' && !Array.isArray(o) ? o : {};
const state={variables:objectOrEmpty(saved.variables),functions:objectOrEmpty(saved.functions),datasets:objectOrEmpty(saved.datasets),history:Array.isArray(saved.history)?saved.history.slice(0,500):[],favorites:Array.isArray(saved.favorites)?saved.favorites:[],recent:Array.isArray(saved.recent)?saved.recent:[],precision:Math.max(3,Math.min(200,Number(saved.precision)||30)),digits:Math.max(1,Math.min(100,Number(saved.digits)||10)),fields:objectOrEmpty(saved.fields),matrixCells:objectOrEmpty(saved.matrixCells),rates:objectOrEmpty(saved.rates)};
state.language=initialLanguage(saved,navigator.language);
state.languageChosen=saved.languageChosen===true;
state.theme=['system','light','dark'].includes(saved.theme)?saved.theme:'system';
const systemTheme=window.matchMedia('(prefers-color-scheme: dark)');
setLanguage(state.language);$('language').value=state.language;$('theme').value=state.theme;applyTheme(state.theme,systemTheme.matches);
translateDOM();
systemTheme.addEventListener('change',()=>applyTheme(state.theme,systemTheme.matches));
$('theme').onchange=()=>{state.theme=value('theme');applyTheme(state.theme,systemTheme.matches);persist();};
$('language').onchange=()=>{state.language=value('language');state.languageChosen=true;setLanguage(state.language);translateDOM();if(['matrix','vector'].includes(value('mode')))renderMatrix();persist();if($('dialog').open)$('dialog').close();};
state.secondKeys=!!saved.secondKeys;
let lastResult=null,decimal=false,shift=false,alpha=false,hyperbolic=false,typing=false,overwrite=false,committed=false,screenExpanded=false,engineering=false,grouping=false,mixed=false,busy=false,catalog={},graphParameters={},lastGraph=null,lastGraphBounds=null,storageWarning=false;
let calcSession=null;
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
  for(const field of document.querySelectorAll('main input[id],main textarea[id],main select[id],.mode-bar select[id]')) state.fields[field.id]=field.type==='checkbox'?field.checked:field.value;
  if(calcSession)state.fields.expression=calcSession.source;
  if(!writeState(state) && !storageWarning) { storageWarning=true; toast('브라우저 저장 공간을 사용할 수 없어 이번 세션에서만 보관합니다.'); }
}
function toast(message) { setText($('toast'),message); $('toast').hidden=false; clearTimeout(toast.timer); toast.timer=setTimeout(()=>$('toast').hidden=true,3500); }
function error(message) { $('answer').replaceChildren(); const el=document.createElement('span');el.className='error';setText(el,message);$('answer').append(el);$('note').textContent=''; }
function control(label,action,className='') { const button=document.createElement('button');button.type='button';setText(button,label);button.className=className;button.addEventListener('click',action);return button; }
function element(tag,text='',className='') { const el=document.createElement(tag);setText(el,text);el.className=className;return el; }
function openDialog(title,content) { setText($('dialog-title'),title);$('dialog-body').replaceChildren(content);translateDOM($('dialog'));if(!$('dialog').open)$('dialog').showModal(); }
$('dialog-close').onclick=()=>$('dialog').close();
$('dialog').addEventListener('click',event=>{if(event.target===$('dialog')){const r=$('dialog').getBoundingClientRect();if(event.clientX<r.left||event.clientX>r.right||event.clientY<r.top||event.clientY>r.bottom)$('dialog').close();}});
function requestOptions() { return {angle:value('angle'),precision:state.precision,displayDigits:state.digits,variables:state.variables,functions:state.functions}; }
const engine=new EngineClient();
function updateButtons() { document.querySelectorAll('[data-run],.key[data-evaluate]').forEach(button=>button.disabled=!engine.ready||busy);$('stop').disabled=!busy;$('stop').hidden=!busy;$('expression').readOnly=!typing||!!calcSession&&busy;$('retry').hidden=engine.ready||busy; }
document.documentElement.dataset.busy='false';document.documentElement.dataset.engine='loading';document.documentElement.dataset.typing='false';
engine.addEventListener('status',event=>{setText($('status'),event.detail);document.documentElement.dataset.engine=engine.ready?'ready':'loading';updateButtons();});
engine.addEventListener('ready',()=>{document.documentElement.dataset.engine='ready';updateButtons();});
engine.addEventListener('busy',event=>{busy=event.detail;document.documentElement.dataset.busy=String(busy);updateButtons();});
$('stop').onclick=()=>engine.cancel();
$('retry').onclick=()=>engine.cancel('계산 엔진을 재시작합니다.');
function showResult(result,source='') {
  if(!result.ok){error(result.error||'계산 오류');return;}
  lastResult=result;
  committed=true;$('commit-indicator').textContent='=';
  if(result.resultAst) state.variables.Ans=result.resultAst;
  renderResult();
  if(source){state.history.unshift({source,exact:result.exact||'',decimal:result.decimal||'',resultAst:result.resultAst,time:Date.now(),star:false});state.history=state.history.slice(0,500);}
  persist();
}
function renderResult() {
  if(!lastResult) return;
  let tree=decimal||engineering ? lastResult.decimalTree||lastResult.tree : lastResult.tree;
  if(mixed&&!decimal&&tree?.kind==='fraction'){
    try{const numerator=BigInt(tree.args[0].value),denominator=BigInt(tree.args[1].value),whole=numerator/denominator,remainder=(numerator<0n?-numerator:numerator)%denominator;if(whole)tree={kind:'mixed',args:[{kind:'number',value:whole.toString()},{kind:'fraction',args:[{kind:'number',value:remainder.toString()},{kind:'number',value:denominator.toString()}]}]};}catch{}
  }
  $('answer').replaceChildren();
  const text=(decimal ? lastResult.decimal : lastResult.exact)||'';
  if(tree && text.length<10000 && !text.includes('\n')) $('answer').append(mathDisplay(tree,state.digits,decimal||lastResult.approximate||engineering,{engineering,grouping}));
  else $('answer').textContent=text;
  const notes=[lastResult.note,...(lastResult.conditions||[]),lastResult.calcValues?Object.entries(lastResult.calcValues).map(([name,n])=>`${name} = ${n}`).join(', '):''].filter(Boolean).join('\n');
  if(notes)$('note').textContent=notes;else setText($('note'),'Next input starts a new calculation');
  $('answer-insert').disabled=!lastResult.resultAst;
}
async function evaluate(source=value('expression')) {
  if(calcSession){await submitCalcValue();return;}
  try {const converted=latexInput(source);if(source===value('expression')&&converted!==source){$('expression').value=converted;preview();}const result=await engine.execute({...requestOptions(),tree:parse(converted)});showResult(result,converted);}
  catch(exc){error(exc.message);}
}
function preview() {
  const source=value('expression'),display=$('expression-preview');display.replaceChildren();
  let target=display;
  if(calcSession){display.append(element('div',calcSession.source,'calc-source'));target=element('div','','calc-value');target.append(element('span',`${calcSession.names[calcSession.index]} = `));display.append(target);}
  if(source)try{target.append(expressionDisplay(source));}catch{target.append(document.createTextNode(source));}
  persist();
}
$('expression-preview').onclick=event=>{const target=event.target.closest('[data-source-start]');if(target){const field=$('expression');field.setSelectionRange(Number(target.getAttribute('data-source-start')),Number(target.getAttribute('data-source-end')));$('expression-preview').querySelectorAll('.selected').forEach(el=>el.classList.remove('selected'));target.classList.add('selected');}else{$('expression').setSelectionRange(value('expression').length,value('expression').length);}};
function insert(text,cursor=null) {
  if(calcSession&&busy)return;
  const field=$('expression'),undo=undoStack();undo.push(field.value);if(undo.length>100)undo.shift();
  if(committed){field.value=/^[+\-*/÷^%!∠]/.test(text)?'Ans':'';field.setSelectionRange(field.value.length,field.value.length);committed=false;$('commit-indicator').textContent='';}
  const start=field.selectionStart,end=overwrite&&field.selectionEnd===start?Math.min(field.value.length,start+text.length):field.selectionEnd;
  field.setRangeText(text,start,end,'end');
  if(cursor!==null)field.setSelectionRange(start+cursor,start+cursor);
  if(typing)field.focus({preventScroll:true});preview();
}
function mode(mode) {
  $('mode').value=mode;
  document.documentElement.dataset.workspace=mode;
  if(mode==='scientific')$('exact-toggle').parentElement.insertBefore($('stop'),$('engineering-toggle'));
  else document.querySelector('.runtime-bar').append($('stop'));
  document.querySelectorAll('[data-mode]').forEach(section=>section.hidden=!section.dataset.mode.split(' ').includes(mode));
  if(['matrix','vector'].includes(mode))renderMatrix();
  persist();
}
$('mode').onchange=()=>mode(value('mode'));
$('angle').onchange=persist;
$('expression').oninput=()=>{committed=false;if(!calcSession)$('commit-indicator').textContent='';preview();};
$('expression').addEventListener('paste',event=>{const text=event.clipboardData?.getData('text');if(!text)return;try{const converted=latexInput(text);if(converted!==text){event.preventDefault();insert(converted);}}catch(exc){event.preventDefault();toast(exc.message);}});
$('expression').addEventListener('keydown',event=>{if(event.key==='Enter'&&!event.shiftKey){event.preventDefault();evaluate();}if(event.key==='Escape'){event.preventDefault();if(calcSession)cancelCalc();else if(busy)engine.cancel();else{$('expression').value='';preview();}}});
$('clear').onclick=()=>{if(calcSession){cancelCalc();return;}expressionUndo.push(value('expression'));$('expression').value='';committed=false;lastResult=null;$('answer').replaceChildren();$('note').textContent='';$('commit-indicator').textContent='';preview();};
$('undo').onclick=()=>{const undo=undoStack();if(undo.length){$('expression').value=undo.pop();preview();}};
async function clipboard(text) { try{await navigator.clipboard.writeText(text);toast('복사했습니다.');}catch{const field=element('textarea');field.value=text;openDialog('복사할 텍스트',field);field.select();} }
$('copy').onclick=()=>{const f=$('expression');clipboard(f.value.slice(f.selectionStart,f.selectionEnd)||f.value);};
$('cut').onclick=()=>{const field=$('expression'),start=field.selectionStart,end=field.selectionEnd;if(start!==end){clipboard(field.value.slice(start,end));undoStack().push(field.value);field.setRangeText('',start,end,'end');committed=false;preview();}};
$('typing-toggle').onclick=()=>{typing=!typing;document.documentElement.dataset.typing=String(typing);$('expression').readOnly=!typing;setText($('typing-toggle'),typing?'Math input':'Keyboard');if(typing)$('expression').focus({preventScroll:true});};
$('insert-mode').onclick=()=>{overwrite=!overwrite;$('insert-mode').textContent=overwrite?'OVR':'INS';};
$('paste').onclick=async()=>{try{insert(latexInput(await navigator.clipboard.readText()));}catch{toast('붙여넣기를 허용하거나 Ctrl+V를 사용해 주세요.');}};
$('answer-copy').onclick=()=>lastResult&&clipboard(decimal?lastResult.decimal:lastResult.exact);
$('answer-insert').onclick=()=>{if(state.variables.Ans){mode('scientific');insert('Ans');}else toast('먼저 재사용 가능한 결과를 계산해 주세요.');};
$('exact-toggle').onclick=()=>{decimal=!decimal;$('exact-toggle').textContent=decimal?'≈ Decimal':'Exact';renderResult();};
$('screen-toggle').onclick=()=>{screenExpanded=!screenExpanded;document.documentElement.dataset.screenExpanded=String(screenExpanded);$('screen-toggle').classList.toggle('active',screenExpanded);};
$('engineering-toggle').onclick=()=>{engineering=!engineering;$('engineering-toggle').classList.toggle('active',engineering);renderResult();};
$('grouping-toggle').onclick=()=>{grouping=!grouping;$('grouping-toggle').classList.toggle('active',grouping);renderResult();};
document.querySelectorAll('[data-shortcut]').forEach(button=>button.onclick=()=>{mode('scientific');insert(button.dataset.shortcut,button.dataset.shortcut.indexOf('(')+1);});
function modeDialog(){const choices=element('div','','mode-choices');for(const option of $('mode').options)choices.append(control(option.textContent,()=>{mode(option.value);$('dialog').close();}));openDialog('Workspace',choices);}
function matrixInsertDialog(){const content=element('div'),rows=element('input'),cols=element('input');for(const [input,name] of [[rows,'Rows / components'],[cols,'Column']]){input.type='number';input.min='1';input.max='9';input.value='2';const label=element('label',name);label.append(input);content.append(label);}content.append(control('Insert',()=>{const r=Number(rows.value),c=Number(cols.value);if(!Number.isInteger(r)||!Number.isInteger(c)||r<1||c<1||r>9||c>9)return;mode('scientific');insert(`[${Array.from({length:r},()=>`[${Array(c).fill('0').join(',')}]`).join(',')}]`);$('dialog').close();}));openDialog('Matrix',content);}
function calcPrompt(){
  $('answer').replaceChildren();
  $('commit-indicator').textContent=`${calcSession.names[calcSession.index]}?`;
  setText($('note'),'CALC · enter a value, then press = · AC cancels');preview();
  if(typing)$('expression').focus({preventScroll:true});
}
async function startCalc(){
  if(busy)return;
  if(calcSession){await submitCalcValue();return;}
  try{
    const source=latexInput(value('expression')),tree=parse(source),names=calcVariables(tree,state.variables);
    if(!names.length){await evaluate(source);return;}
    calcSession={source,tree,names,index:0,undo:[],values:{},previousResult:lastResult,previousCommitted:committed};
    document.documentElement.dataset.calcActive='true';lastResult=null;committed=false;$('answer').replaceChildren();$('expression').value='';calcPrompt();
  }catch(exc){error(exc.message);}
}
function cancelCalc(){
  const session=calcSession;if(!session)return;calcSession=null;delete document.documentElement.dataset.calcActive;
  if(busy)engine.cancel();
  $('expression').value=session.source;lastResult=session.previousResult;committed=session.previousCommitted;
  $('commit-indicator').textContent=committed?'=':'';$('note').textContent='';if(lastResult)renderResult();else $('answer').replaceChildren();preview();
}
async function submitCalcValue(){
  const session=calcSession;if(!session||busy)return;
  const fail=message=>{error(message);setText($('note'),'CALC · enter a value, then press = · AC cancels');};
  try{
    const name=session.names[session.index],source=value('expression').trim();
    const tree=source?parse(latexInput(source)):Object.hasOwn(state.variables,name)?{kind:'symbol',value:name}:parse('0');
    const numeric=await engine.execute({...requestOptions(),tree});if(calcSession!==session)return;
    if(!numeric.ok){fail(numeric.error);return;}
    if(numeric.symbolic||!numeric.resultAst){fail('Enter a numeric value');return;}
    state.variables[name]=numeric.resultAst;session.values[name]=numeric.exact;persist();
    if(session.index<session.names.length-1){session.index++;session.undo=[];$('expression').value='';calcPrompt();return;}
    const result=await engine.execute({...requestOptions(),tree:session.tree});if(calcSession!==session)return;
    if(!result.ok){fail(result.error);return;}
    calcSession=null;delete document.documentElement.dataset.calcActive;$('expression').value=session.source;preview();
    showResult({...result,calcValues:session.values},session.source);
  }catch(exc){if(calcSession===session)fail(exc.message);}
}
async function performKey(input){
  if(calcSession&&busy&&input!=='AC')return;
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
  else if(input==='CLR ALL'){state.variables={};$('clear').click();persist();}
  else if(input==='S⇔D')$('exact-toggle').click();
  else if(input==='MIXED'){mixed=!mixed;decimal=false;$('exact-toggle').textContent='Exact';renderResult();}
  else if(input==='INS')$('insert-mode').click();
  else if(input==='NEG'){if(committed){$('expression').value='';committed=false;}insert('-');}
  else if(input==='DEL'){const f=$('expression'),start=f.selectionStart,end=f.selectionEnd;undoStack().push(f.value);f.setRangeText('',start===end?Math.max(0,start-1):start,end,'end');committed=false;preview();}
  else if(['LEFT','RIGHT','UP','DOWN'].includes(input)){
    const f=$('expression');let start=f.selectionStart,end=f.selectionEnd;
    if(input==='LEFT'||input==='RIGHT')start=end=Math.max(0,Math.min(f.value.length,(input==='LEFT'?start:end)+(input==='LEFT'?-1:1)));
    else try{const nodes=[];const visit=n=>{if(n.start<=start&&n.end>=end)nodes.push(n);n.args?.forEach(visit);};visit(parse(f.value));nodes.sort((a,b)=>(a.end-a.start)-(b.end-b.start));const selected=input==='UP'?nodes.find(n=>n.start<start||n.end>end):nodes[0]?.args?.[0];if(selected){start=selected.start;end=selected.end;}}catch{}
    f.setSelectionRange(start,end);if(typing)f.focus({preventScroll:true});
  }
  else if(input==='MATRIX_INPUT')matrixInsertDialog();
  else if(input==='TO_GRAPH'){$('graph-source').value=value('expression')||'x';mode('graph');}
  else if(jumps[input])mode(jumps[input]);
  else if(input==='M+'||input==='M−'){
    if(lastResult?.resultAst){const result=await engine.execute({...requestOptions(),tree:{kind:'binary',value:input==='M+'?'+':'-',args:[state.variables.M||parse('0'),lastResult.resultAst]}});if(result.ok&&result.resultAst){state.variables.M=result.resultAst;persist();toast('M에 저장했습니다.');}else error(result.error);}
  }
  else if(input==='ENG'||input==='ENG−'){if(lastResult?.resultAst){const result=await engine.execute({...requestOptions(),tree:parse(`eng(Ans${input==='ENG−'?',3':''})`)});showResult(result);}}
  else if(input==='DMS'){if(lastResult?.resultAst){const result=await engine.execute({...requestOptions(),tree:parse('dms(Ans)')});showResult(result);}}
  else if(input==='DMS_INPUT'){const markers=value('expression').match(/[°′″]/g)||[];insert(['°','′','″'][markers.length%3]);}
  else if(input==='RANDOM')insert(String(Math.random()));
  else if(input==='RELATION')insert('=');
  else if(input==='*10^()'){const source=value('expression');insert(source&&!committed?input:'1'+input,(source&&!committed?input:'1'+input).indexOf('(')+1);}
  else {
    // Parenthesis templates place the cursor in their first empty argument.
    if(/^\^/.test(input)&&value('expression')&&!committed){$('expression').setSelectionRange(value('expression').length,value('expression').length);}
    insert(input,input.includes('(')?input.indexOf('(')+1:null);
  }
  shift=false;alpha=false;hyperbolic=false;renderKeypad();
}
function renderKeypad() {
  const press=k=>performKey(k.input==='CALC'&&alpha?'RELATION':alpha&&k.alpha?k.alpha:shift&&k.alternate?k.alternate:k.input);
  buildKeypad($('keypad'),{second:state.secondKeys,shift,alpha,hyperbolic,press,longPress:k=>{if(['SHIFT','ALPHA','SECOND'].includes(k.input)||!k.alternate)press(k);else{shift=false;alpha=false;performKey(k.alternate);}}});
  $('key-modifier').textContent=shift?'SHIFT':alpha?'ALPHA':hyperbolic?'HYP':state.secondKeys?'2ND':'';
  $('key-modifier').classList.toggle('alpha',alpha);translateDOM($('keypad'));updateButtons();
}

function historyDialog() {
  const content=element('div'),search=element('input');search.placeholder='수식 또는 결과 검색';search.setAttribute('aria-label','기록 검색');const list=element('div');let favorites=false;
  const filter=control('즐겨찾기만',()=>{favorites=!favorites;setText(filter,favorites?'전체 기록':'즐겨찾기만');render();});
  function render(){list.replaceChildren();for(const item of state.history.filter(h=>(!favorites||h.star)&&`${h.source} ${h.exact}`.toLowerCase().includes(search.value.toLowerCase()))){const row=element('div','','list-row');const text=element('div','','content');text.append(element('code',item.source),element('div',`= ${item.exact}`));row.append(text,control(item.star?'★':'☆',()=>{item.star=!item.star;persist();render();}),control('사용',()=>{mode('scientific');$('expression').value=item.source;preview();$('dialog').close();}));list.append(row);}if(!list.childElementCount)list.append(element('p','기록이 없습니다.','hint'));}
  search.oninput=render;content.append(search,filter,control('기록 삭제',()=>{state.history=[];persist();render();}),list);render();openDialog('History',content);
}
function catalogDialog() {
  const content=element('div'),search=element('input'),tabs=element('div','','catalog-tabs'),list=element('div');search.placeholder='함수 검색';search.setAttribute('aria-label','함수 검색');let category='Scientific';
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
  search.oninput=render;content.append(search,tabs,list,element('p','빈 인수에 값을 입력하세요. 예: diff(sin(x^2),x), normcdf(-1.96,1.96)','hint'),element('a','함수 도움말'));
  content.lastChild.href=getLanguage()==='ko'?'catalog_help_ko.md':'catalog_help.md';content.lastChild.target='_blank';render();openDialog('Catalog',content);
}
function variablesDialog() {
  const content=element('div'),name=element('input');name.placeholder='변수 이름 · A, b, M';name.setAttribute('aria-label','변수 이름');const expression=element('input');expression.placeholder='값 또는 수식';expression.setAttribute('aria-label','변수 수식');const list=element('div');
  function store(ast){if(!/^[A-Za-z][A-Za-z0-9_]*$/.test(name.value)||name.value==='Ans')throw new Error('Ans 이외의 영문 변수 이름을 입력하세요.');state.variables[name.value]=ast;persist();render();}
  function render(){list.replaceChildren();for(const key of Object.keys(state.variables)){const row=element('div','','list-row');row.append(element('code',key,'content'),control('삽입',()=>{mode('scientific');insert(key);$('dialog').close();}),control('삭제',()=>{delete state.variables[key];persist();render();}));list.append(row);}}
  content.append(name,expression,control('수식 저장',()=>{try{store(parse(latexInput(expression.value)));}catch(exc){toast(exc.message);}}),control('현재 결과 STO',()=>{try{if(!lastResult?.resultAst)throw new Error('저장 가능한 결과가 없습니다.');store(lastResult.resultAst);}catch(exc){toast(exc.message);}}),list);render();openDialog('RCL / STO',content);
}
function settingsDialog() {
  const content=element('div');
  for(const [key,label,min,max] of [['precision','내부 유효 숫자',3,200],['digits','표시 소수 자릿수',1,100]]){const input=element('input');input.type='number';input.min=min;input.max=max;input.value=state[key];input.onchange=()=>{state[key]=Math.max(min,Math.min(max,Number(input.value)||min));input.value=state[key];$('digits-indicator').textContent=`≤ ${state.digits} digits`;persist();renderResult();};const holder=element('label',label);holder.append(input);content.append(holder);}
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
$('python-source').onkeydown=event=>{if(event.key==='Tab'){event.preventDefault();const f=event.target;f.setRangeText('    ',f.selectionStart,f.selectionEnd,'end');persist();}else if(event.key==='Enter'&&(event.ctrlKey||event.metaKey)){event.preventDefault();run('python');}};

function renderMatrix() {
  const vector=value('mode')==='vector',rows=Number(value('matrix-rows')),cols=vector?1:Number(value('matrix-cols'));
  $('matrix-cols-label').hidden=vector;$('vector-other-label').hidden=!vector;
  const previous=value('matrix-op'),options=vector?['norm','normalize','dot','cross','angle','projection']:['det','inverse','transpose','rank','trace','rref','eigenvalues','eigenvectors','lu','qr','cholesky','nullspace','charpoly'];
  $('matrix-op').replaceChildren(...options.map(name=>{const option=element('option',name);option.value=name;return option;}));
  $('matrix-op').value=options.includes(previous)?previous:options[0];
  $('matrix-grid').style.gridTemplateColumns=`repeat(${cols}, minmax(60px, 1fr))`;
  $('matrix-grid').replaceChildren();
  for(let row=0;row<rows;row++)for(let col=0;col<cols;col++){
    const input=element('input'),key=`${vector?'v':'m'}-${row}-${col}`;input.value=state.matrixCells[key]??(vector?String(row+1):row===col?'1':'0');input.dataset.cell=key;input.setAttribute('aria-label',getLanguage()==='ko'?`${row+1}행 ${col+1}열`:`Row ${row+1}, column ${col+1}`);input.oninput=()=>{state.matrixCells[key]=input.value;persist();};$('matrix-grid').append(input);
  }
}
for(const id of ['matrix-rows','matrix-cols']) {for(let i=1;i<=9;i++)$(id).append(element('option',String(i)));$(id).value=id==='matrix-rows'?'3':'3';restoreSelect(id);$(id).onchange=()=>{renderMatrix();persist();};}
function matrixExpression(){const vector=value('mode')==='vector',columns=vector?1:Number(value('matrix-cols')),cells=Array.from($('matrix-grid').querySelectorAll('input')).map(input=>input.value||'0');if(vector)return `[${cells.join(',')}]`;const rows=[];for(let i=0;i<cells.length;i+=columns)rows.push(`[${cells.slice(i,i+columns).join(',')}]`);return `[${rows.join(',')}]`;}
function datasetsList(){const previous=value('dataset-list');$('dataset-list').replaceChildren(element('option','새 데이터'));$('dataset-list').firstChild.value='';for(const name of Object.keys(state.datasets)) {const option=element('option',name);option.value=name;$('dataset-list').append(option);}if(state.datasets[previous])$('dataset-list').value=previous;}
$('dataset-list').onchange=()=>{const name=value('dataset-list');if(state.datasets[name]){$('statistics-data').value=state.datasets[name];$('dataset-name').value=name;persist();}};
$('dataset-save').onclick=()=>{const name=value('dataset-name').trim();if(!name){toast('데이터 이름을 입력하세요.');return;}state.datasets[name]=value('statistics-data');datasetsList();$('dataset-list').value=name;persist();toast('데이터를 저장했습니다.');};
$('dataset-delete').onclick=()=>{delete state.datasets[value('dataset-list')];datasetsList();persist();};
$('csv-open').onclick=()=>pickFile('.csv,.tsv,text/csv',async file=>{$('statistics-data').value=(await file.text()).replace(/^\uFEFF/,'').replace(/\t/g,',');$('dataset-name').value=file.name.replace(/\.(csv|tsv)$/i,'');persist();});
$('csv-save').onclick=()=>downloadFile(`${value('dataset-name')||'calcmax-data'}.csv`,value('statistics-data'),'text/csv');
function dataRows(){const rows=value('statistics-data').trim().split(/\r?\n/).filter(line=>line.trim()).map(line=>line.split(',').map(cell=>cell.trim()));if(!rows.length||rows.length>5000)throw new Error('1~5000행의 데이터를 입력하세요.');if(rows.some(row=>row.length!==rows[0].length||row.length>3||row.some(cell=>!cell)))throw new Error('각 행의 열 수를 맞추고 빈 값을 채워 주세요. 최대 3열을 사용할 수 있습니다.');rows.flat().forEach(cell=>parse(cell));return rows;}
function statisticsExpression(){const rows=dataRows(),column=Number(value('statistics-column')),op=value('statistics-op'),extra=value('statistics-extra')||'0';if(column>=rows[0].length)throw new Error('선택한 열이 데이터에 없습니다.');const columns=Array.from({length:rows[0].length},(_,i)=>`[${rows.map(row=>row[i]).join(',')}]`),data=columns[column];
  if(op==='regression'){if(rows[0].length<2)throw new Error('회귀에는 x,y 두 열이 필요합니다.');return `regression([${rows.map(r=>`[${r[0]},${r[1]}]`).join(',')}],${value('regression-kind')})`;}
  if(['ttest2','ttestpaired','correlation'].includes(op)){if(columns.length<2)throw new Error('이 분석에는 두 열이 필요합니다.');return `${op}(${op==='correlation'?'':extra+','}${columns[0]},${columns[1]})`;}
  if(['anova','tukey'].includes(op)){if(columns.length<2)throw new Error('이 분석에는 두 열 이상이 필요합니다.');return `${op}(${columns.join(',')})`;}
  if(['ttest','tinterval'].includes(op))return `${op}(${extra},${data})`;
  return `${op}(${data})`;
}

function functionsList() {
  $('functions-list').replaceChildren();
  for(const [name,f] of Object.entries(state.functions)){const row=element('div','','list-row');row.append(element('code',`${name}(${f.parameters.join(',')}) = ${f.source||'저장된 수식'}`,'content'),control('편집',()=>{$('function-name').value=name;$('function-parameters').value=f.parameters.join(',');$('function-body').value=f.source||'';}),control('삭제',()=>{delete state.functions[name];persist();functionsList();}));$('functions-list').append(row);}
}
function validFunction(name,parameters,body,source='') { if(!/^[A-Za-z][A-Za-z0-9_]*$/.test(name)||!parameters.every(p=>/^[A-Za-z][A-Za-z0-9_]*$/.test(p))||!parameters.length||new Set(parameters).size!==parameters.length)throw new Error('함수와 매개변수에 서로 다른 영문 이름을 사용하세요.');if(!body||typeof body!=='object'||!body.kind)throw new Error('함수 수식이 올바르지 않습니다.');return {parameters,body,source}; }
$('function-save').onclick=()=>{try{const name=value('function-name').trim(),parameters=value('function-parameters').split(',').map(p=>p.trim()),source=latexInput(value('function-body'));state.functions[name]=validFunction(name,parameters,parse(source),source);persist();functionsList();toast('함수를 저장했습니다.');}catch(exc){toast(exc.message);}};
$('function-export').onclick=()=>downloadFile('calcmax-functions.json',JSON.stringify(state.functions,null,2),'application/json');
$('function-import').onclick=()=>pickFile('.json',async file=>{const input=JSON.parse(await file.text()),functions={};if(!input||typeof input!=='object'||Array.isArray(input))throw new Error('함수 JSON 객체가 필요합니다.');for(const [name,f] of Object.entries(input)){functions[name]=validFunction(name,f.parameters,f.body,f.source||'');}Object.assign(state.functions,functions);persist();functionsList();toast('함수를 가져왔습니다.');});

function graphRequest() {
  const kind=value('graph-kind'),trees=[],shadings=[];
  for(let line of value('graph-source').split(/\r?\n/).map(s=>s.trim()).filter(Boolean)){
    if(line.startsWith('[shade]')){
      if(kind!=='cartesian')throw new Error('영역 표시는 y=f(x) 그래프에서 사용할 수 있습니다.');
      const [body,interval]=line.slice(7).trim().split(';'),match=/^y\s*(<=|>=|<|>)\s*(.+)$/.exec(body.trim());
      const shade=match?{mode:'halfplane',side:match[1].startsWith('<')?'below':'above',trees:[parse(match[2])]}:{mode:'band',trees:body.split(',').map(s=>parse(s.trim()))};
      if(interval){const [a,b]=interval.trim().split('..');if(!a||!b)throw new Error('음영 구간은 a..b 형식으로 입력하세요.');shade.a=parse(a);shade.b=parse(b);}
      shadings.push(shade);continue;
    }
    line=line.replace(kind==='surface'?/^z\s*=\s*/:kind==='differential'?/^dy\/dt\s*=\s*/:kind==='cartesian'?/^y\s*=\s*/:/^r\s*=\s*/,'');trees.push(parse(latexInput(line)));
  }
  if(!trees.length&&!shadings.length)throw new Error('그래프 수식을 입력하세요.');
  const min=Number(value('graph-min')),max=Number(value('graph-max')),yMin=Number(value('graph-ymin')),yMax=Number(value('graph-ymax'));
  if(![min,max,yMin,yMax].every(Number.isFinite)||max<=min||yMax<=yMin)throw new Error('최대값은 최소값보다 커야 합니다.');
  const request={...requestOptions(),action:'graph',graphKind:kind,trees,shadings,min,max,yMin,yMax,parameters:graphParameters,variable:['parametric','polar','differential'].includes(kind)?'t':kind==='sequence'?'n':'x',surfaceYMin:yMin,surfaceYMax:yMax};
  if(kind==='sequence')request.initialTrees=value('graph-initial').split(',').filter(s=>s.trim()).map(s=>parse(s.trim()));
  if(kind==='differential'){request.initialValues=value('graph-initial').split(',').map(Number);request.t0=Number(value('graph-t0'));}
  return request;
}
function graphControls(names=[]) {
  $('graph-parameters').replaceChildren();
  for(const name of names){const holder=element('label'),input=element('input'),label=element('span');input.type='range';input.min='-10';input.max='10';input.step='.1';input.value=graphParameters[name]??1;label.textContent=`${name} = ${input.value}`;input.oninput=()=>{graphParameters[name]=Number(input.value);label.textContent=`${name} = ${input.value}`;};input.onchange=()=>run('graph');holder.append(label,input);$('graph-parameters').append(holder);}
}
function graphTable(result) {
  $('graph-table').replaceChildren();const table=element('table'),head=element('thead'),tr=element('tr');['곡선','x / t / n','y / z'].forEach(name=>tr.append(element('th',name)));head.append(tr);table.append(head);const body=element('tbody');
  (result.curves||result.surface||[]).forEach((curve,i)=>curve.filter(Boolean).filter((_,index)=>index%Math.max(1,Math.floor(curve.length/60))===0).slice(0,80).forEach(point=>{const row=element('tr');[i+1,...point].forEach(n=>row.append(element('td',Number(n).toPrecision(7))));body.append(row);}));table.append(body);$('graph-table').append(table);
}
$('graph-kind').onchange=()=>{const kind=value('graph-kind');const examples={cartesian:'sin(x)\ncos(x)',parametric:'(cos(t),sin(t))',polar:'1+cos(t)',sequence:'u(n-1)+1',surface:'sin(x)*cos(y)',differential:'y'};$('graph-source').value=examples[kind];$('graph-min').value=['sequence','differential'].includes(kind)?'0':'-10';$('graph-max').value=kind==='sequence'?'20':kind==='differential'?'3':'10';graphParameters={};graphControls();persist();};

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
    persist();
    if(workspace==='graph') {
      const request=graphRequest(),result=await engine.execute(request);
      if(!result.ok){error(result.error);return;}
      lastGraph=result;lastGraphBounds={xmin:request.min,xmax:request.max,ymin:request.yMin,ymax:request.yMax};
      plot($('graph-plot'),result,lastGraphBounds,{dots:request.graphKind==='sequence'});graphControls(result.parameters);graphTable(result);setText($('note'),'그래프 계산 완료');return;
    }
    if(workspace==='python') {
      $('python-output').textContent=t('실행 중…');const result=await engine.execute({...requestOptions(),action:'python',source:value('python-source'),inputs:value('python-input')===''?[]:value('python-input').split(/\r?\n/),filename:'calcmax.py'});$('python-output').textContent=[result.output,result.error&&t(result.error)].filter(Boolean).join('\n')||t('실행 완료');if(!result.ok)error(result.error);return;
    }
    if(workspace==='programmer') {
      const result=await engine.execute({...requestOptions(),action:'programmer',base:Number(value('programmer-base')),width:Number(value('programmer-width')),signed:$('programmer-signed').checked,a:value('programmer-a'),b:value('programmer-b'),op:value('programmer-op')});if(result.ok)$('programmer-output').textContent=Object.entries(result.bases).map(([base,n])=>`${base.padEnd(4)} ${n}`).join('\n');showResult(result);return;
    }
    if(workspace==='constants') {
      const result=await engine.execute({...requestOptions(),action:'constants'});if(!result.ok){error(result.error);return;}$('constants-list').replaceChildren();for(const c of result.constants){const row=element('div','','list-row');const text=element('div','','content');text.append(element('strong',`${c.symbol} · ${c.name}`),element('p',`${c.value} ${c.unit}`,'hint'));row.append(text,control('사용',()=>{mode('scientific');insert(c.symbol);}));$('constants-list').append(row);}return;
    }
    let source;
    if(workspace==='scientific'){await evaluate();return;}
    if(workspace==='equation'){
      const equations=value('equation-source').split(/\r?\n/).map(s=>s.trim()).filter(Boolean),variable=value('equation-variable').trim(),kind=value('equation-kind');if(!equations.length)throw new Error('방정식을 입력하세요.');source=`${kind}(${equations.length===1?equations[0]:'['+equations.join(',')+']'},${variable.includes(',')?'['+variable+']':variable}${['nsolve','dsolve'].includes(kind)?','+value('equation-extra'):''})`;
    }else if(workspace==='matrix'){
      const op=value('matrix-op');source=`${op}(${matrixExpression()}${value('mode')==='vector'&&['dot','cross','angle','projection'].includes(op)?','+value('vector-other'):op==='charpoly'?',x':''})`;
    }else if(workspace==='statistics')source=statisticsExpression();
    else if(workspace==='units')source=`convert(${value('unit-value')},${value('unit-from')},${value('unit-to')})`;
    else if(workspace==='tip'){const people=Number(value('tip-people'));if(!Number.isInteger(people)||people<1)throw new Error('인원은 1 이상의 정수여야 합니다.');source=`(${value('tip-amount')})*(1+(${value('tip-percent')})/100)/${people}`;}
    else if(workspace==='currency'){source=`(${value('currency-amount')})*(${value('currency-rate')})`;}
    const result=await engine.execute({...requestOptions(),tree:parse(latexInput(source))});showResult(result,source);
    if(workspace==='statistics'&&result.ok&&value('statistics-op')==='regression'){
      const points=dataRows().map(row=>row.slice(0,2).map(Number)).filter(p=>p.every(Number.isFinite));
      if(points.length>1){$('statistics-plot').hidden=false;plot($('statistics-plot'),{curves:[points,result.curve||[]]},dataBounds(points),{dots:true});}
    }
  }catch(exc){error(exc.message);}
}
document.querySelectorAll('[data-run]').forEach(button=>button.onclick=()=>run(button.dataset.run));
document.addEventListener('keydown',event=>{
  if(value('mode')!=='scientific'||typing||$('dialog').open||$('settings-dialog').open||event.ctrlKey||event.metaKey||event.altKey||event.target.closest('input,select,textarea'))return;
  if(event.target.closest('button')&&['Enter',' '].includes(event.key))return;
  const action={Enter:'=',Backspace:'DEL',Delete:'DEL',ArrowLeft:'LEFT',ArrowRight:'RIGHT',ArrowUp:'UP',ArrowDown:'DOWN',Escape:'AC'}[event.key];
  if(action){event.preventDefault();if(event.key==='Escape'&&busy)engine.cancel();else performKey(action);}
  else if(event.key.length===1&&/[0-9A-Za-z.,+\-*/÷×^%!()[\]{}=<>°∞π_]/.test(event.key)){event.preventDefault();insert(event.key);}
});
document.querySelectorAll('main input,main select,main textarea').forEach(field=>field.addEventListener('change',persist));
window.addEventListener('pagehide',()=>{persist();clearTimeout(toast.timer);});
window.addEventListener('resize',()=>{if(lastGraph&&value('mode')==='graph')plot($('graph-plot'),lastGraph,lastGraphBounds);});
async function initialize() {
  try{const response=await fetch('./catalog.json');if(!response.ok)throw new Error('Catalog load failed');catalog=await response.json();}catch(exc){toast('Catalog가 없습니다. 빌드 스크립트를 실행해 주세요.');}
  const units='m km cm mm in inch ft yd mi m2 cm2 km2 ha acre m3 L mL galUS kg g mg lb oz K degC degF s sec min h hr day ms mps kph mph knot mps2 g0 Pa kPa bar atm N kN lbf J kJ cal kWh eV W kW Hz kHz MHz A amp ampere mA uA C coulomb mC uC V volt mV kV ohm Ω kohm kΩ Mohm MΩ S siemens mS F farad uF nF pF H henry mH uH Wb weber Vs T tesla mT uT mol mole mmol umol bit byte kB KiB MB MiB GB rad deg grad'.split(' ');
  for(const id of ['unit-from','unit-to']){units.forEach(unit=>{const option=element('option',unit);option.value=unit;$(id).append(option);});$(id).value=id==='unit-from'?'degF':'degC';restoreSelect(id);}
  datasetsList();functionsList();mode(value('mode'));renderKeypad();$('expression').readOnly=true;preview();$('digits-indicator').textContent=`≤ ${state.digits} digits`;translateDOM();
  if(location.protocol==='file:')error('WebAssembly는 정적 HTTP 서버가 필요합니다. python web/serve.py를 실행하고 http://localhost:8080을 여세요.');
  if('serviceWorker' in navigator && location.protocol!=='file:'){
    navigator.serviceWorker.register('./sw.js',{updateViaCache:'none'}).then(registration=>{
      if(registration.active)setText($('offline-status'),'오프라인 사용 가능');
      registration.addEventListener('updatefound',()=>{const worker=registration.installing;worker?.addEventListener('statechange',()=>{if(worker.state==='activated')setText($('offline-status'),'오프라인 사용 가능');});});
    }).catch(()=>{setText($('offline-status'),'브라우저 캐시 사용');});
  }
}
initialize();
