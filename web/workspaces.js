import {$,value,element,control} from './app-ui.js';
import {t,getLanguage} from './i18n.js';
import {parse,latexInput} from './parser.js';
import {renderFormulas} from './formula-preview.js';
import {displayNumber} from './display-format.js';
import {equationCommand,polynomialEquation} from './workspace-commands.js';
import {tipCommand,moneyResult} from './money.js';
import {unitGroups} from './unit-groups.js';
import {createMatrixWorkspace} from './matrix-workspace.js';
import {createStatisticsWorkspace} from './statistics-workspace.js';
import {createFunctionsWorkspace} from './functions-workspace.js';
import {createPythonWorkspace} from './python-workspace.js';

export function createWorkspaces({state,engine,ui,persist,restoreSelect,requestOptions,isBusy,error,changeMode,replaceInput,insert,evaluate,showResult,graphs}) {
  const {toast}=ui;
  const matrix=createMatrixWorkspace({state,persist,restoreSelect,refreshWorkspaceMath,storeExpression:storeWorkspaceExpression,error,changeMode,replaceInput});
  const statistics=createStatisticsWorkspace({state,engine,ui,persist,refreshWorkspaceMath,storeExpression:storeWorkspaceExpression,error,changeMode,replaceInput,graphs});
  const functions=createFunctionsWorkspace({state,ui,persist,refreshWorkspaceMath,changeMode,insert});
  const python=createPythonWorkspace({engine,ui,persist,requestOptions,error,run});
  function equationSource(){return value('equation-form')==='general'?value('equation-source'):polynomialEquation(['equation-a','equation-b','equation-c','equation-d'].slice(0,Number(value('equation-form'))+1).map(value),value('equation-variable'));}
  function tipExpression(){return tipCommand({bill:value('tip-amount'),percent:value('tip-percent'),fixed:value('tip-fixed'),tax:value('tip-tax'),people:value('tip-people'),method:value('tip-method'),whole:$('tip-whole').checked});}
  function tipMethodControls(){const fixed=value('tip-method')==='amount';$('tip-percent').disabled=fixed;$('tip-fixed').disabled=!fixed;}
  $('tip-method').onchange=tipMethodControls;tipMethodControls();

  function refreshWorkspaceMath(){
    const targets=[['equation-source',()=>equationSource().split(/\r?\n/)],['function-body',()=>[`${value('function-name')}(${value('function-parameters')})=${value('function-body')}`]],['distribution-math',()=>[statistics.distributionExpression()]],['matrix-grid',()=>[matrix.expression()]],['vector-other',()=>[matrix.operandExpression()]]];
    for(const [id,sources] of targets){const input=$(id);let preview=id==='distribution-math'?input:$(id+'-math');if(!preview){preview=element('div','','formula-preview');preview.id=id+'-math';input.closest('label')?.insertAdjacentElement('afterend',preview)||input.insertAdjacentElement('afterend',preview);}try{renderFormulas(preview,sources(),{digits:state.digits});}catch{preview.replaceChildren();preview.hidden=true;}}
  }
  for(const field of document.querySelectorAll('main input,main textarea,main select'))if(field.id!=='expression'&&!field.id.startsWith('graph-')&&!field.id.startsWith('python-'))for(const name of ['input','change'])field.addEventListener(name,refreshWorkspaceMath);

  function equationControls(){const type=value('equation-form'),method=value('equation-kind');$('equation-coefficients').hidden=type==='general';$('equation-source').closest('label').hidden=type!=='general';for(const [index,id] of ['equation-a','equation-b','equation-c','equation-d'].entries())$(id).closest('label').hidden=type!=='general'&&index>Number(type);$('equation-extra').closest('label').hidden=!['nsolve','dsolve'].includes(method);$('equation-initial').closest('label').hidden=method!=='dsolve';$('equation-hint').closest('label').hidden=method!=='pdsolve';refreshWorkspaceMath();}
  $('equation-form').onchange=equationControls;
  $('equation-kind').onchange=()=>{const kind=value('equation-kind');if(['dsolve','pdsolve'].includes(kind)){$('equation-form').value='general';$('equation-source').value=kind==='dsolve'?'diff(y(t),t)=y(t)':'diff(u(x,y),x)+diff(u(x,y),y)=0';$('equation-variable').value=kind==='dsolve'?'y(t)':'u(x,y)';$('equation-extra').value=kind==='dsolve'?'t':'';}else if(value('equation-variable').includes('(')){$('equation-source').value='x^2-5x+6=0';$('equation-variable').value='x';$('equation-extra').value='0,1';}equationControls();};
  async function storeWorkspaceExpression(name,source){if(isBusy()||!engine.ready)return;try{const result=await engine.execute({...requestOptions(),tree:parse(source)});if(!result.ok||!result.resultAst){error(result.error||'No reusable result to store.');return;}state.variables[name]=result.resultAst;persist();toast('저장했습니다.');}catch(exc){error(exc.message);}}
  $('units-swap').onclick=()=>{const from=value('unit-from');$('unit-from').value=value('unit-to');$('unit-to').value=from;refreshWorkspaceMath();persist();};
  $('unit-category').onchange=()=>{const units=unitGroups[value('unit-category')]||null;for(const id of ['unit-from','unit-to']){for(const option of $(id).options)option.hidden=units?!units.includes(option.value):false;if(units&&!units.includes(value(id)))$(id).value=units[id==='unit-from'?0:1];}refreshWorkspaceMath();persist();};
  $('currency-swap').onclick=()=>{const from=value('currency-from');$('currency-from').value=value('currency-to');$('currency-to').value=from;const rate=Number(value('currency-rate'));if(rate>0)$('currency-rate').value=String(1/rate);refreshWorkspaceMath();persist();};
  $('constants-search').oninput=()=>{const query=value('constants-search').toLowerCase();for(const row of $('constants-list').children)row.hidden=!row.dataset.search?.includes(query);};
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
      if(workspace==='python'){await python.run();return;}
      if(workspace==='regression'){await statistics.runRegression(requestOptions(),showResult);return;}
      if(workspace==='programmer') {
        const result=await engine.execute({...requestOptions(),action:'programmer',base:Number(value('programmer-base')),width:Number(value('programmer-width')),signed:$('programmer-signed').checked,a:value('programmer-a'),b:value('programmer-b'),op:value('programmer-op')});if(result.ok)$('programmer-output').textContent=Object.entries(result.bases).map(([base,n])=>`${base.padEnd(4)} ${n}`).join('\n');showResult(result);return;
      }
      if(workspace==='constants') {
        const result=await engine.execute({...requestOptions(),action:'constants'});if(!result.ok){error(result.error);return;}$('constants-list').replaceChildren();for(const c of result.constants){const row=element('div','','list-row');row.dataset.search=`${c.symbol} ${c.name} ${c.unit}`.toLowerCase();const text=element('div','','content');text.append(element('strong',`${c.symbol} · ${c.name}`),element('p',`${displayNumber(c.value.replace(/…$/,''),state.digits)} ${c.unit}`,'hint'));row.append(text,control('사용',()=>{changeMode('scientific');insert(c.symbol);}));$('constants-list').append(row);}return;
      }
      let source,statisticsContext='';
      if(workspace==='scientific'){await evaluate();return;}
      if(workspace==='equation'){source=equationCommand({kind:value('equation-kind'),source:equationSource(),variable:value('equation-variable').trim(),extra:value('equation-extra'),initial:value('equation-initial'),hint:value('equation-hint')});
      }else if(workspace==='matrix'){source=matrix.command();
      }else if(workspace==='statistics'){source=statistics.expression();statisticsContext=statistics.analysisSummary();}
      else if(workspace==='distribution')source=statistics.distributionExpression();
      else if(workspace==='units')source=`convert(${value('unit-value')},${value('unit-from')},${value('unit-to')})`;
      else if(workspace==='tip')source=tipExpression();
      else if(workspace==='currency'){source=`(${value('currency-amount')})*(${value('currency-rate')})`;}
      let result=await engine.execute({...requestOptions(),tree:parse(latexInput(source))});if(workspace==='tip'&&result.ok)result=moneyResult(result,Number(value('tip-people')));if(statisticsContext&&result.ok)result={...result,note:[statisticsContext,result.note].filter(Boolean).join('\n')};showResult(result,source,workspace==='equation'?equationSource():source,{decimalDisplay:workspace==='regression'});
    }catch(exc){error(exc.message);}
  }
  document.querySelectorAll('[data-run]').forEach(button=>button.onclick=()=>run(button.dataset.run));

  function initialize() {
    const units='m km cm mm in inch ft yd mi m2 cm2 km2 ha acre m3 L mL galUS kg g mg lb oz K degC degF s sec min h hr day ms mps kph mph knot mps2 g0 Pa kPa bar atm N kN lbf J kJ cal kWh eV W kW Hz kHz MHz A amp ampere mA uA C coulomb mC uC V volt mV kV ohm Ω kohm kΩ Mohm MΩ S siemens mS F farad uF nF pF H henry mH uH Wb weber Vs T tesla mT uT mol mole mmol umol bit byte kB KiB MB MiB GB rad deg grad'.split(' ');
    for(const id of ['unit-from','unit-to']){units.forEach(unit=>{const option=element('option',unit);option.value=unit;$(id).append(option);});$(id).value=id==='unit-from'?'degF':'degC';restoreSelect(id);}
    for(const group of Object.keys(unitGroups)){const option=element('option',group==='Amount'?'amount':group);option.value=group;$('unit-category').append(option);}restoreSelect('unit-category');$('unit-category').onchange();
    statistics.datasetsList();functions.render();equationControls();statistics.distributionControls();refreshWorkspaceMath();
  }
  function render(){refreshWorkspaceMath();statistics.render();}
  return {initialize,render,refreshMath:refreshWorkspaceMath,renderMatrix:matrix.render,renderFunctions:functions.render,equationSource};
}
