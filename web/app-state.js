import {initialLanguage} from './i18n.js';
import {writeState} from './storage.js';
import {$} from './app-ui.js';
import {normalizeGraphColors} from './graph-colors.js';
import {removeExpiredAnswerFunctions} from './function-transfer.js';

export function createAppState(saved={},browserLanguage='en') {
  const objectOrEmpty=o=>o && typeof o==='object' && !Array.isArray(o) ? o : {};
  const state={
    variables:objectOrEmpty(saved.variables),
    functions:{...objectOrEmpty(saved.functions)},
    datasets:objectOrEmpty(saved.datasets),
    datasetKinds:objectOrEmpty(saved.datasetKinds),
    history:Array.isArray(saved.history)?saved.history.slice(0,500):[],
    favorites:Array.isArray(saved.favorites)?saved.favorites:[],
    recent:Array.isArray(saved.recent)?saved.recent:[],
    removeComputationLimit:saved.removeComputationLimit===true,
    precision:Math.max(3,Math.min(200,Math.trunc(Number(saved.precision))||30)),
    digits:Math.max(2,Math.min(200,Math.trunc(Number(saved.precision))||30,Math.trunc(Number(saved.digits))||10)),
    fields:{...objectOrEmpty(saved.fields)},
    graph:{...objectOrEmpty(saved.graph)},
    graphColors:normalizeGraphColors(saved.graphColors,saved.graphColorsVersion!==2),
    graphColorsVersion:2,
    matrixCells:objectOrEmpty(saved.matrixCells),
    rates:objectOrEmpty(saved.rates)
  };
  // Preserve shared sampling settings while replacing the old static HMC.
  if(state.fields['regression-bayesian-method']==='hmc')state.fields['regression-bayesian-method']='nuts';
  for(const suffix of ['samples','warmup','seed','chains']){
    const legacy=`regression-hmc-${suffix}`,current=`regression-nuts-${suffix}`;
    if(state.fields[current]===undefined&&state.fields[legacy]!==undefined)state.fields[current]=state.fields[legacy];
    delete state.fields[legacy];
  }
  delete state.fields['regression-hmc-leapfrog'];
  // Older regression options used their translated labels as option values.
  const regressionAliases={'다항':'polynomial','다중':'multiple','로지스틱':'logistic'};
  const regression=state.fields['regression-kind'];
  if(Object.hasOwn(regressionAliases,regression))state.fields['regression-kind']=regressionAliases[regression];
  if(state.fields['graph-kind']==='implicit'){
    const source=state.fields['graph-source']||state.graph.sources?.implicit||'x^2+y^2=1';
    state.fields['graph-kind']='cartesian';state.fields['graph-source']=source;
    state.graph.sources={...objectOrEmpty(state.graph.sources),cartesian:source};
  }
  state.language=initialLanguage(saved,browserLanguage);
  state.languageChosen=saved.languageChosen===true;
  state.theme=['system','light','dark'].includes(saved.theme)?saved.theme:'system';
  state.secondKeys=!!saved.secondKeys;
  state.tapeClearedAt=Number(saved.tapeClearedAt)||0;
  state.resultDisplayMode=['eng','sci'].includes(saved.resultDisplayMode)?saved.resultDisplayMode:'off';
  state.autoCloseBrackets=saved.autoCloseBrackets!==false;
  state.wordWrap=!!saved.wordWrap;
  state.calcModeStepByStep=saved.calcModeStepByStep===true;
  state.persistHistory=saved.persistHistory!==false;
  state.haptics=!!saved.haptics;state.sound=!!saved.sound;
  state.inputFont=Math.max(10,Math.min(42,Number(saved.inputFont)||24));
  state.outputFont=Math.max(10,Math.min(48,Number(saved.outputFont)||30));
  state.assumptions=objectOrEmpty(saved.assumptions);
  state.displayShortcuts=Array.isArray(saved.displayShortcuts)?saved.displayShortcuts.slice(0,6):[{label:'∫',input:'integrate(,x)'},{label:'∫ₐᵇ',input:'integrate(,x,0,1)'},{label:'d/dx',input:'diff(,x)'}];
  removeExpiredAnswerFunctions(state.functions,state.variables.Ans);
  return state;
}

export function restoreFields(state) {
  for(const [id,setting] of Object.entries(state.fields)) {
    const field=$(id);
    if(!field || field.closest('dialog')) continue;
    if(field.type==='checkbox') field.checked=!!setting;
    else if(field.tagName!=='SELECT') field.value=String(setting);
  }
  document.querySelectorAll('select').forEach(field=>restoreSelect(state,field.id));
}

export function restoreSelect(state,id) {
  if(state.fields[id]!==undefined&&Array.from($(id).options).some(o=>o.value===String(state.fields[id])))$(id).value=String(state.fields[id]);
}

export function createPersistence({state,snapshot,onPersist,toast}) {
  let saveTimer=null,storageWarning=false;
  function persist() {
    clearTimeout(saveTimer);saveTimer=null;
    const functionsChanged=removeExpiredAnswerFunctions(state.functions,state.variables.Ans);
    for(const field of document.querySelectorAll('main input[id],main textarea[id],main select[id],.mode-bar select[id]'))state.fields[field.id]=field.type==='checkbox'?field.checked:field.value;
    const draft=snapshot();
    state.fields.expression=draft.expression;
    state.graph=draft.graph;
    if(!writeState({...state,history:state.persistHistory?state.history:[]})&&!storageWarning){storageWarning=true;toast('브라우저 저장 공간을 사용할 수 없어 이번 세션에서만 보관합니다.');}
    onPersist({functionsChanged});
  }
  function schedulePersist(){clearTimeout(saveTimer);saveTimer=setTimeout(persist,150);}
  return {persist,schedulePersist,dispose:()=>clearTimeout(saveTimer)};
}
