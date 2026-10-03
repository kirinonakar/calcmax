import {parse,latexInput,closeInputBrackets} from './parser.js';
import {mathDisplay} from './math-display.js';
import {t,setText} from './i18n.js';
import {expressionInputDisplay} from './expression-display.js';
import {calcVariables,calcBindings} from './calc-session.js';
import {previousCalculations,renderPreviousCalculations,followTape} from './calculation-tape.js';
import {renderFormulas} from './formula-preview.js';
import {markInputCursor,followInputCursor,followTextCursor,inputPointPosition} from './input-cursor.js';
import {moveMathCursor,mathStructureExit,emptyCallDeletion,emptyPowerDeletion,emptyFractionDeletion,infinityDeletion,powerInput} from './input-navigation.js';
import {createDisplaySizing} from './display-sizing.js';
import {fractionInput} from './fraction-input.js';
import {requiresExplicitEvaluation} from './evaluation-policy.js';
import {graphExpressionTarget} from './graph-workspace.js';
import {defineFunction} from './function-transfer.js';
import {$,value,element,control} from './app-ui.js';

export function createCalculator({state,engine,isBusy,ui,persist,schedulePersist,requestOptions,error,changeMode,updateButtons,pressKey,modeDialog,variablesDialog,matrixInsertDialog,graphs,onFunctionsChanged=()=>{}}) {
  const {toast,openDialog,clipboard}=ui;
  let lastResult=null,decimal=false,typing=false,overwrite=false,committed=false,screenExpanded=false,grouping=false,mixed=false,lastResultSource='';
  let calcSession=null,activeHistoryEntry=null,inputAnswer=null,tapeRows=null,tapeFormat='';
  let engineeringConversion=false,engineeringShift=0;
  const tapeFollow=followTape($('calculation-tape'),$('tape-active'));
  const displaySizing=createDisplaySizing(document.querySelector('main'),$('expression-preview'),$('answer'));
  const expressionUndo=[];
  let previewSource=value('expression');
  let inputBoundary=null;
  let calculationPreviewTimer=null,calculationPreviewKey=null,calculationPreviewRevision=0;
  const undoStack=()=>calcSession?.undo||expressionUndo;
  function showResult(result,source='',displaySource=source,{decimalDisplay=false}={}) {
    if(!result.ok){error(result.error||'계산 오류');return;}
    if(decimalDisplay){decimal=true;$('exact-toggle').textContent='≈ Decimal';}
    const previousAnswer=value('mode')==='scientific'?(inputAnswer||state.variables.Ans):state.variables.Ans;
    engineeringConversion=false;engineeringShift=0;syncEngineering();
    lastResult=result;lastResultSource=displaySource;
    committed=true;$('commit-indicator').textContent='=';
    if(result.resultAst&&!result.assignment) state.variables.Ans=result.resultAst;
    renderResult();
    if(source){activeHistoryEntry={source,exact:result.exact||'',decimal:result.decimal||'',resultAst:result.resultAst,inputAns:previousAnswer,calcValues:result.calcValues,display:{tree:result.tree,decimalTree:result.decimalTree,approximate:result.approximate},time:Date.now(),star:false};state.history.unshift(activeHistoryEntry);state.history=state.history.slice(0,500);for(const entry of state.history.slice(11))delete entry.display;}
    renderTape();tapeFollow.latest();
    persist();
  }
  function renderTape(){const rows=previousCalculations(state.history,activeHistoryEntry,state.tapeClearedAt),format=`${decimal}:${state.digits}:${state.resultDisplayMode}:${grouping}`;if(tapeRows&&format===tapeFormat&&rows.length===tapeRows.length&&rows.every((entry,i)=>entry===tapeRows[i]))return;tapeRows=rows;tapeFormat=format;renderPreviousCalculations($('tape-history'),rows,{decimal,digits:state.digits,notation:state.resultDisplayMode,grouping,reuseDisabled:isBusy()||!!calcSession,reuse:reuseCalculation});}
  function reuseCalculation(entry){
    if(isBusy()||calcSession)return;
    expressionUndo.push(value('expression'));committed=false;lastResult=null;activeHistoryEntry=null;inputAnswer=entry.inputAns||null;
    changeMode('scientific');$('expression').value=entry.source;$('expression').setSelectionRange(entry.source.length,entry.source.length);$('commit-indicator').textContent='';$('answer').replaceChildren();$('note').textContent='';preview();
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
    const text=(decimal ? lastResult.decimal : lastResult.exact)||'';
    const output=element('div');
    if(tree && text.length<=40000) output.append(mathDisplay(tree,state.digits,decimal||lastResult.approximate,{notation:engineeringConversion?'eng':state.resultDisplayMode,grouping,engineeringShift,showZeroExponent:engineeringConversion}));
    else renderFormulas(output,text.split(/\r?\n/),{digits:state.digits});
    if(output.childNodes.length!==$('answer').childNodes.length||[...output.childNodes].some((node,i)=>!node.isEqualNode($('answer').childNodes[i])))$('answer').replaceChildren(...output.childNodes);
    updateResultSource();
    if(!$('result-source').hidden)renderFormulas($('result-source'),[lastResultSource],{digits:state.digits});
    const notes=[lastResult.note,...(lastResult.conditions||[]),lastResult.calcValues?Object.entries(lastResult.calcValues).map(([name,n])=>`${name} = ${n}`).join(', '):''].filter(Boolean).join('\n');
    if(engineeringConversion)setText($('note'),'ENG mode · ←/→ shifts mantissa');else if(notes)$('note').textContent=notes;else if(committed)setText($('note'),'Next input starts a new calculation');else $('note').textContent='';
    $('answer-insert').disabled=!lastResult.resultAst;
    displaySizing.refresh();
    renderTape();
  }
  async function evaluate(source=value('expression')) {
    if(engineeringConversion){exitEngineering();return;}
    if(isBusy())return;
    if(!engine.ready){error('계산 엔진이 로딩 중입니다.');return;}
    if(calcSession){await submitCalcValue();return;}
    cancelCalculationPreview();
    try {
      const converted=latexInput(state.autoCloseBrackets?closeInputBrackets(source):source);
      if(source===value('expression')&&converted!==source){$('expression').value=converted;preview();}
      const inputTree=parse(converted),[left,right]=inputTree.args;
      if(['=',':='].includes(inputTree.value)&&left?.kind==='call'&&left.args.every(arg=>arg.kind==='symbol')){
        const definition=defineFunction(left.value,left.args.map(arg=>arg.value),converted.slice(right.start,right.end));
        state.functions[left.value]=definition;
        const message=`${t('함수를 저장했습니다.')} ${left.value}(${definition.parameters.join(',')})`;
        showResult({ok:true,assignment:true,exact:message,decimal:message,tree:{kind:'text',value:message}},converted);
        onFunctionsChanged();
        return;
      }
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
    if(source)target.append(expressionInputDisplay(source,{wordWrap:state.wordWrap}));
    previewSource=source;
    renderInputCursor();
    renderTape();tapeFollow.latest();
    scheduleCalculationPreview();
    schedulePersist();
  }
  function calculationPreviewState() {
    return JSON.stringify([value('expression'),value('mode'),committed,!!calcSession,inputAnswer,requestOptions()]);
  }
  function cancelCalculationPreview() {
    clearTimeout(calculationPreviewTimer);calculationPreviewTimer=null;
    calculationPreviewKey=null;calculationPreviewRevision++;
  }
  function scheduleCalculationPreview() {
    const key=calculationPreviewState();
    if(key===calculationPreviewKey)return;
    cancelCalculationPreview();calculationPreviewKey=key;
    if(value('mode')!=='scientific'||committed||calcSession)return;
    const source=value('expression');
    if(!source.trim()){clearPreviewResult();return;}
    if(!engine.ready||isBusy())return;
    let tree;
    try {
      // Typing previews require a complete input, even when = can close brackets.
      tree=evaluationTree(latexInput(source));
      const userFunctions=new Set(Object.keys(state.functions).filter(name=>state.functions[name].parameters?.length>1));
      if(requiresExplicitEvaluation(tree,userFunctions)||
        ['=',':='].includes(tree.value)&&['symbol','call'].includes(tree.args?.[0]?.kind)){clearPreviewResult();return;}
    } catch {return;}
    const revision=calculationPreviewRevision;
    calculationPreviewTimer=setTimeout(async()=>{
      calculationPreviewTimer=null;
      try {
        if(engine.pending)await engine.pending.promise;
        if(revision!==calculationPreviewRevision||key!==calculationPreviewState()||isBusy()||!engine.ready)return;
        const result=await engine.execute({...requestOptions(),tree,budget:2},{background:true});
        if(revision!==calculationPreviewRevision||key!==calculationPreviewState())return;
        if(result.ok){lastResult=result;lastResultSource=source;renderResult();}else clearPreviewResult();
      } catch { /* Incomplete or failed previews leave the input editable. */ }
    },100);
  }
  function renderInputCursor(){displaySizing.refresh();const field=$('expression'),display=$('expression-preview'),math=display.querySelector('.input-flow,math');if(inputBoundary&&(inputBoundary.source!==field.value||inputBoundary.position!==field.selectionStart||field.selectionStart!==field.selectionEnd))inputBoundary=null;if(math){const marker=markInputCursor(math,field.value,field.selectionStart,field.selectionEnd,{boundary:inputBoundary?.edge,structure:inputBoundary});if(!typing)followInputCursor(display,marker||display.querySelector('.selected'),12,state.wordWrap);}else if(!field.value&&!display.querySelector('.text-caret')){const cursor=element('span','│','text-caret');display.append(cursor);}if(typing&&!state.wordWrap)followTextCursor(field);}
  $('expression-preview').onclick=event=>{
    inputBoundary=null;
    if(committed&&!isBusy()){
      // Explicit cursor placement edits the original formula with its original Ans.
      inputAnswer=activeHistoryEntry?.inputAns||inputAnswer;
      committed=false;$('commit-indicator').textContent='';
      if(engineeringConversion)exitEngineering();else renderResult();
    }
    const target=event.target.closest('[data-source-start]');
    if(target){const field=$('expression'),start=Number(target.getAttribute('data-source-start')),end=Number(target.getAttribute('data-source-end'));if(target.classList.contains('selected')||field.selectionStart===field.selectionEnd&&field.selectionStart>=start&&field.selectionStart<=end){const at=inputPointPosition(target,field.value,event.clientX,event.clientY);field.setSelectionRange(at,at);}else field.setSelectionRange(start,end);}else{$('expression').setSelectionRange(value('expression').length,value('expression').length);}
    preview();
  };
  function insert(text,cursor=null,{factor=false,fraction=false}={}) {
    if(isBusy())return;
    if(engineeringConversion)exitEngineering();
    const field=$('expression'),undo=undoStack();undo.push(field.value);if(undo.length>100)undo.shift();
    const outsideStructure=inputBoundary?.edge==='after'&&inputBoundary.source===field.value&&inputBoundary.position===field.selectionStart&&field.selectionStart===field.selectionEnd;
    if(committed){inputAnswer=null;field.value=!lastResult?.assignment&&(fraction||/^[+\-*/÷^%!∠]/.test(text))?'Ans':'';field.setSelectionRange(field.value.length,field.value.length);committed=false;$('commit-indicator').textContent='';}
    if(state.autoCloseBrackets&&text.length===1&&field.selectionStart===field.selectionEnd&&!overwrite){const pairs={'(' : ')','[':']','{':'}'};if(pairs[text]){text+=pairs[text];cursor=1;}else if(')]}'.includes(text)&&field.value[field.selectionStart]===text){field.setSelectionRange(field.selectionStart+1,field.selectionStart+1);preview();return;}}
    let start=field.selectionStart,end=!fraction&&overwrite&&field.selectionEnd===start?Math.min(field.value.length,start+text.length):field.selectionEnd;
    if(fraction){({start,end,text,cursor}=fractionInput(field.value,start,end));}
    let prefix='',suffix='';
    if(outsideStructure&&!fraction&&/^[\p{L}\p{N}_.(]/u.test(text))prefix='*';
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
  $('expression').oninput=()=>{if(engineeringConversion)exitEngineering();if(value('expression')!==previewSource){const undo=undoStack();undo.push(previewSource);if(undo.length>100)undo.shift();}if(committed)inputAnswer=null;committed=false;if(!calcSession)$('commit-indicator').textContent='';preview();};
  $('expression').addEventListener('select',renderInputCursor);
  $('expression').addEventListener('keyup',renderInputCursor);
  $('expression').addEventListener('beforeinput',event=>{
    if(typing&&['deleteContentBackward','deleteContentForward'].includes(event.inputType)){
      const field=$('expression'),backward=event.inputType==='deleteContentBackward';
      if(infinityDeletion(field.value,field.selectionStart,field.selectionEnd,backward)){event.preventDefault();handleKey(backward?'DEL':'DELETE_FORWARD');return;}
    }
    if(typing&&state.autoCloseBrackets&&event.inputType==='insertText'&&event.data?.length===1&&'()[]{}'.includes(event.data)){event.preventDefault();insert(event.data);}
  });
  $('expression').addEventListener('paste',event=>{const text=event.clipboardData?.getData('text');if(!text)return;try{const converted=latexInput(text);if(converted!==text){event.preventDefault();insert(converted);}}catch(exc){event.preventDefault();toast(exc.message);}});
  $('expression').addEventListener('keydown',event=>{if(event.key==='Enter'&&!event.shiftKey&&!event.isComposing){event.preventDefault();event.stopPropagation();if(!event.repeat)evaluate();}if(event.key==='Escape'){event.preventDefault();if(calcSession)cancelCalc();else if(isBusy())engine.cancel();else{$('expression').value='';preview();}}});
  $('clear').onclick=()=>{if(engineeringConversion)exitEngineering();if(calcSession){cancelCalc();return;}expressionUndo.push(value('expression'));$('expression').value='';committed=false;lastResult=null;activeHistoryEntry=null;inputAnswer=null;$('answer').replaceChildren();$('note').textContent='';$('commit-indicator').textContent='';preview();};
  $('undo').onclick=()=>{if(isBusy())return;const undo=undoStack();if(undo.length){const field=$('expression');field.value=undo.pop();field.setSelectionRange(field.value.length,field.value.length);committed=false;if(!calcSession)$('commit-indicator').textContent='';preview();}};
  $('copy').onclick=()=>{const f=$('expression');clipboard(f.value.slice(f.selectionStart,f.selectionEnd)||f.value);};
  $('cut').onclick=()=>{const field=$('expression'),start=field.selectionStart,end=field.selectionEnd;if(start!==end){clipboard(field.value.slice(start,end));undoStack().push(field.value);field.setRangeText('',start,end,'end');committed=false;preview();}};
  $('typing-toggle').onclick=()=>{typing=!typing;document.documentElement.dataset.typing=String(typing);$('expression').readOnly=!typing;setText($('typing-toggle'),typing?'Math input':'Keyboard');if(typing)$('expression').focus({preventScroll:true});};
  $('insert-mode').onclick=()=>{overwrite=!overwrite;$('insert-mode').textContent=overwrite?'OVR':'INS';};
  $('paste').onclick=async()=>{try{insert(latexInput(await navigator.clipboard.readText()));}catch{const content=element('div'),field=element('textarea');field.rows=4;field.setAttribute('aria-label',t('Paste expression'));content.append(field,control('Insert',()=>{try{insert(latexInput(field.value));$('dialog').close();}catch(exc){toast(exc.message);}}));openDialog('Paste',content);field.focus({preventScroll:true});}};
  document.addEventListener('paste',event=>{if(event.defaultPrevented||value('mode')!=='scientific'||typing||$('dialog').open||$('settings-dialog').open||event.target.closest?.('input,select,textarea')&&event.target!==$('expression'))return;const text=event.clipboardData?.getData('text/plain')||event.clipboardData?.getData('text');if(!text)return;event.preventDefault();try{insert(latexInput(text));}catch(exc){toast(exc.message);}});
  $('answer-copy').onclick=()=>lastResult&&clipboard(decimal?lastResult.decimal:lastResult.exact);
  $('answer-insert').onclick=()=>{if(state.variables.Ans){changeMode('scientific');insert('Ans',null,{factor:true});}else toast('먼저 재사용 가능한 결과를 계산해 주세요.');};
  $('exact-toggle').onclick=()=>{decimal=!decimal;$('exact-toggle').textContent=decimal?'≈ Decimal':'Exact';renderResult();};
  $('screen-toggle').onclick=()=>{screenExpanded=!screenExpanded;document.documentElement.dataset.screenExpanded=String(screenExpanded);$('screen-toggle').classList.toggle('active',screenExpanded);};
  function renderNotation(){const mode=state.resultDisplayMode,button=$('engineering-toggle');button.textContent=mode==='sci'?'SCI':'ENG';button.classList.toggle('active',mode!=='off');button.dataset.notation=mode;button.setAttribute('aria-label',`${t('Result notation')}: ${mode.toUpperCase()}`);}
  $('engineering-toggle').onclick=()=>{state.resultDisplayMode={off:'eng',eng:'sci',sci:'off'}[state.resultDisplayMode];renderNotation();renderResult();persist();};
  renderNotation();
  $('grouping-toggle').onclick=()=>{grouping=!grouping;$('grouping-toggle').classList.toggle('active',grouping);renderResult();};
  function calcPrompt(){
    $('answer').replaceChildren();
    $('commit-indicator').textContent=`${calcSession.names[calcSession.index]}?`;
    setText($('note'),'CALC · enter a value, then press = · AC cancels');preview();
    updateButtons();
    if(typing)$('expression').focus({preventScroll:true});
  }
  async function startCalc(){
    if(engineeringConversion){exitEngineering();return;}
    if(isBusy())return;
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
    if(isBusy())engine.cancel();
    $('expression').value=session.source;lastResult=session.previousResult;committed=session.previousCommitted;
    $('commit-indicator').textContent=committed?'=':'';$('note').textContent='';if(lastResult)renderResult();else $('answer').replaceChildren();preview();
    updateButtons();
  }
  async function submitCalcValue(){
    const session=calcSession;if(!session||isBusy())return;
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
  function handleKey(input){
    if(calcSession&&isBusy()&&input!=='AC')return;
    const jumps={'Scientific/CAS':'scientific',Graph:'graph',Python:'python',Matrix:'matrix',Vector:'vector',Statistics:'statistics',Programmer:'programmer',Units:'units',Constants:'constants',Equations:'equation'};
    if(calcSession){
      if(input==='AC'){cancelCalc();return;}
      if(input==='='||input==='CALC')return submitCalcValue();
    }
    if(engineeringConversion&&['LEFT','RIGHT'].includes(input)){engineeringShift=Math.max(-40000,Math.min(40000,engineeringShift+(input==='LEFT'?1:-1)));renderResult();return;}
    if(engineeringConversion&&!['ENG','ENG−','=','CALC','AC','CLR ALL'].includes(input))exitEngineering();
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
    else if(input==='DEL'||input==='DELETE_FORWARD'){
      const f=$('expression'),start=f.selectionStart,end=f.selectionEnd,call=emptyPowerDeletion(f.value,start,end)||emptyFractionDeletion(f.value,start,end)||emptyCallDeletion(f.value,start,end)||infinityDeletion(f.value,start,end,input==='DEL');
      undoStack().push(f.value);
      if(call){f.setRangeText(call.text,call.start,call.end,'end');const at=call.start+(call.cursor??(call.text?1:0));f.setSelectionRange(at,at);}
      else f.setRangeText('',start===end&&input==='DEL'?Math.max(0,start-1):start,start===end&&input==='DELETE_FORWARD'?Math.min(f.value.length,end+1):end,'end');
      committed=false;preview();
    }
    else if(['LEFT','RIGHT','UP','DOWN'].includes(input)){
      const f=$('expression');let start=f.selectionStart,end=f.selectionEnd;
      const outside=inputBoundary?.edge==='after'&&inputBoundary.source===f.value&&inputBoundary.position===start&&start===end?inputBoundary:null;
      const exit=!typing?mathStructureExit(f.value,start,end,input,outside):null;
      const position=typing?null:outside&&input==='LEFT'?(outside.exponentEnd??outside.denominatorEnd):exit?.position??(outside&&input==='RIGHT'?Math.min(f.value.length,start+1):moveMathCursor(f.value,start,end,input));
      if(position!==null)start=end=position;
      else if(input==='LEFT'||input==='RIGHT')start=end=Math.max(0,Math.min(f.value.length,(input==='LEFT'?start:end)+(input==='LEFT'?-1:1)));
      else try{const nodes=[];const visit=n=>{if(n.start<=start&&n.end>=end)nodes.push(n);n.args?.forEach(visit);};visit(parse(f.value,{allowHoles:true}));nodes.sort((a,b)=>(a.end-a.start)-(b.end-b.start));const selected=input==='UP'?nodes.find(n=>n.start<start||n.end>end):nodes[0]?.args?.[0];if(selected){start=selected.start;end=selected.end;}}catch{}
      f.setSelectionRange(start,end);inputBoundary=exit?{...exit,source:f.value,edge:'after'}:outside&&input==='RIGHT'&&start===outside.position?outside:null;if(typing)f.focus({preventScroll:true});preview();
    }
    else if(input==='MATRIX_INPUT')insert('[[,],[,]]',2);
    else if(input==='MATRIX_SIZE')matrixInsertDialog();
    else if(input==='TO_GRAPH'){
      const original=value('expression')||'x';let source=original,graphKind='cartesian';
      try{const target=graphExpressionTarget(original);source=target.source;graphKind=target.kind;graphs.addExpression(source,graphKind);}catch(exc){error(exc.message);return;}
      changeMode('graph');
    }
    else if(jumps[input])changeMode(jumps[input]);
    else if(input==='M+'||input==='M−'){
      return updateMemory(input);
    }
    else if(input==='ENG'||input==='ENG−'){if(lastResult){engineeringConversion=true;engineeringShift=input==='ENG−'?3:0;syncEngineering();renderResult();}}
    else if(input==='DMS')return transformAnswer('dms(Ans)');
    else if(input==='DMS_INPUT'){const markers=value('expression').match(/[°′″]/g)||[];insert(['°','′','″'][markers.length%3]);}
    else if(input==='RANDOM')insert(String(Math.random()));
    else if(input==='RELATION')insert('=');
    else if(input==='()/()')insert(input,null,{factor:true,fraction:true});
    else if(['^2','^3','^()','^(-1)'].includes(input)){
      const field=$('expression'),source=committed?(lastResult?.assignment?'':'Ans'):field.value;
      const start=committed?source.length:field.selectionStart,end=committed?source.length:field.selectionEnd;
      const template=powerInput(source,start,end,input);insert(template.text,template.cursor,{factor:true});
      if(['^2','^3'].includes(input)&&template.text===input){
        const exit=mathStructureExit(field.value,field.selectionStart,field.selectionEnd,'RIGHT');
        if(exit){inputBoundary={...exit,source:field.value,edge:'after'};renderInputCursor();}
      }
    }
    else if(input==='*10^()'){const field=$('expression'),before=field.value.slice(0,field.selectionStart).trimEnd(),text=!committed&&/[\p{L}\p{N}_.)\]}!%°′″]$/u.test(before)?input:'1'+input;insert(text,text.indexOf('(')+1,{factor:true});}
    else {
      // Parenthesis templates place the cursor in their first empty argument.
      insert(input,input.includes('(')?input.indexOf('(')+1:null,{factor:true});
    }
  }
  async function updateMemory(input) {
      if(isBusy())return;const source=state.autoCloseBrackets?closeInputBrackets(value('expression')||'0'):value('expression')||'0';try{const operand=committed&&lastResult?.resultAst?lastResult:await engine.execute({...requestOptions(),tree:evaluationTree(source)});if(!operand.ok||!operand.resultAst){error(operand.error||'This result cannot be stored in memory');return;}if(!committed)showResult(operand,source);const result=await engine.execute({...requestOptions(),tree:{kind:'binary',value:input==='M+'?'+':'-',args:[state.variables.M||parse('0'),operand.resultAst]}});if(result.ok&&result.resultAst){state.variables.M=result.resultAst;persist();toast('M에 저장했습니다.');}else error(result.error);}catch(exc){error(exc.message);}
  }
  async function transformAnswer(source) {
    if(lastResult?.resultAst){const result=await engine.execute({...requestOptions(),tree:parse(source)});showResult(result);}
  }
  function clearPreviewResult(){engineeringConversion=false;engineeringShift=0;syncEngineering();lastResult=null;lastResultSource='';$('answer').replaceChildren();$('note').textContent='';displaySizing.refresh();}
  function syncEngineering(){document.documentElement.dataset.engineeringConversion=String(engineeringConversion);$('keypad').querySelectorAll('[data-input="ENG"]').forEach(button=>button.classList.toggle('active',engineeringConversion));}
  function exitEngineering(){engineeringConversion=false;engineeringShift=0;syncEngineering();renderResult();}
  function applyFonts(){document.documentElement.style.setProperty('--input-font',state.inputFont+'px');document.documentElement.style.setProperty('--output-font',state.outputFont+'px');renderInputCursor();}
  function applyWordWrap(){document.documentElement.dataset.wordWrap=String(state.wordWrap);$('expression').wrap=state.wordWrap?'soft':'off';for(const id of ['expression-preview','expression']){$(id).scrollLeft=0;$(id).scrollTop=0;}}
  applyWordWrap();
  applyFonts();
  document.addEventListener('keydown',event=>{
    if(engineeringConversion&&!event.defaultPrevented&&!event.isComposing&&!event.altKey&&!event.ctrlKey&&!event.metaKey&&value('mode')==='scientific'&&!$('dialog').open&&!$('settings-dialog').open&&['ArrowLeft','ArrowRight'].includes(event.key)&&(!event.target.closest('input,select,textarea')||event.target===$('expression'))){event.preventDefault();event.stopPropagation();handleKey(event.key==='ArrowLeft'?'LEFT':'RIGHT');return;}
    if(!event.defaultPrevented&&!event.isComposing&&!event.altKey&&value('mode')==='scientific'&&!$('dialog').open&&!$('settings-dialog').open&&['Home','End'].includes(event.key)&&(!event.target.closest('input,select,textarea,[contenteditable="true"]')||event.target===$('expression'))){
      event.preventDefault();event.stopPropagation();
      const field=$('expression'),position=moveMathCursor(field.value,field.selectionStart,field.selectionEnd,event.key.toUpperCase()),anchor=event.shiftKey?(field.selectionDirection==='backward'?field.selectionEnd:field.selectionStart):position;
      field.setSelectionRange(Math.min(anchor,position),Math.max(anchor,position),position<anchor?'backward':'forward');inputBoundary=event.shiftKey?null:{source:field.value,position,edge:event.key.toLowerCase()};preview();if(typing)field.scrollTop=event.key==='End'?field.scrollHeight:0;return;
    }
    // Enter calculates while calculator controls retain focus after a click.
    // Cancel the native button activation before it can toggle display options.
    if(value('mode')==='scientific'&&!$('dialog').open&&!$('settings-dialog').open&&event.key==='Enter'&&!event.shiftKey&&!event.ctrlKey&&!event.metaKey&&!event.altKey&&!event.isComposing&&event.target.closest('.keypad, #calculator-display .answer-toolbar, #calculator-display .edit-actions')){
      event.preventDefault();event.stopPropagation();if(!event.repeat)evaluate();
    }
  },true);
  document.addEventListener('keydown',event=>{
    if(event.defaultPrevented||event.isComposing)return;
    if(value('mode')!=='scientific'||typing||$('dialog').open||$('settings-dialog').open||event.ctrlKey||event.metaKey||event.altKey||event.target.closest('input,select,textarea'))return;
    if(event.target.closest('button')&&['Enter',' '].includes(event.key))return;
    const action={Enter:'=',Backspace:'DEL',Delete:'DELETE_FORWARD',ArrowLeft:'LEFT',ArrowRight:'RIGHT',ArrowUp:'UP',ArrowDown:'DOWN',Escape:'AC'}[event.key];
    if(action){event.preventDefault();if(event.key==='Escape'&&isBusy())engine.cancel();else pressKey(action);}
    else if(event.key.length===1&&/[0-9A-Za-z.,+\-*/÷×^%!()[\]{}=<>°∞π_]/.test(event.key)){event.preventDefault();insert(event.key);}
  });

  document.documentElement.dataset.typing='false';
  document.fonts?.addEventListener('loadingdone',renderInputCursor);
  function replaceInput(source,{uncommit=false}={}) {
    $('expression').value=source;
    if(uncommit)committed=false;
    preview();
  }
  function updateInputButtons() {
    document.querySelectorAll('.tape-expression').forEach(button=>button.disabled=isBusy()||!!calcSession);
    $('expression').readOnly=!typing||isBusy();
  }
  function updateResultSource(){ $('result-source').hidden=['scientific','tip'].includes(value('mode'))||!lastResultSource; }
  function dispose() {
    cancelCalculationPreview();tapeFollow.dispose();displaySizing.dispose();
    document.fonts?.removeEventListener('loadingdone',renderInputCursor);
  }
  return {handleKey,insert,evaluate,showResult,renderResult,renderTape,renderNotation,preview,renderInputCursor,applyFonts,applyWordWrap,replaceInput,updateResultSource,
    cancelPreview:cancelCalculationPreview,schedulePreview:scheduleCalculationPreview,
    resetPreview:()=>{calculationPreviewKey=null;scheduleCalculationPreview();},
    resultAst:()=>lastResult?.resultAst,draftSource:()=>calcSession?.source||value('expression'),
    clearHistorySelection:()=>{activeHistoryEntry=null;renderTape();},
    get calcActive(){return !!calcSession;},updateButtons:updateInputButtons,refreshSizing:()=>displaySizing.refresh(),
    followTape:()=>tapeFollow.latest(),dispose};
}
