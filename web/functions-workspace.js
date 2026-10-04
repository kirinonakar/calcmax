import {$,value,element,control} from './app-ui.js';
import {t} from './i18n.js';
import {latexInput} from './parser.js';
import {astSource} from './ast-source.js';
import {renderFormulas} from './formula-preview.js';
import {downloadFile} from './storage.js';
import {defineFunction,encodeFunctions,decodeFunctions} from './function-transfer.js';

export function createFunctionsWorkspace({state,ui,persist,refreshWorkspaceMath,changeMode,insert}) {
  const {toast,pickFile}=ui;
  function functionsList() {
    $('functions-list').replaceChildren();
    for(const [name,f] of Object.entries(state.functions)){const row=element('div','','list-row'),formula=element('div','','content formula-preview');renderFormulas(formula,[`${name}(${f.parameters.join(',')}) = ${f.source||astSource(f.body)}`],{digits:state.digits});row.append(formula,control('편집',()=>{$('function-name').value=name;$('function-parameters').value=f.parameters.join(',');$('function-body').value=f.source||astSource(f.body);refreshWorkspaceMath();}),control('Insert',()=>{changeMode('scientific');insert(`${name}(${','.repeat(Math.max(0,f.parameters.length-1))})`,name.length+1,{factor:true});}),control('삭제',()=>{delete state.functions[name];persist();functionsList();}));$('functions-list').append(row);}
  }
  $('function-clear').onclick=()=>{for(const id of ['function-name','function-parameters','function-body'])$(id).value='';refreshWorkspaceMath();persist();};
  $('function-save').onclick=()=>{try{const name=value('function-name').trim(),parameters=value('function-parameters').split(',').map(p=>p.trim()),source=latexInput(value('function-body'));state.functions[name]=defineFunction(name,parameters,source);persist();functionsList();toast('함수를 저장했습니다.');}catch(exc){toast(exc.message);}};
  $('function-export').onclick=()=>downloadFile('symvacas-functions.json',encodeFunctions(state.functions),'application/json');
  $('function-import').onclick=()=>pickFile('.json',async file=>{const {functions,skipped}=decodeFunctions(await file.text());Object.assign(state.functions,functions);persist();functionsList();toast(`${t('Functions imported.')} ${Object.keys(functions).length} · ${skipped} ${t('skipped')}`);});
  return {render:functionsList};
}
