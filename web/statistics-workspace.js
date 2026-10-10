import {$,value,element,control} from './app-ui.js';
import {t,setText} from './i18n.js';
import {downloadFile} from './storage.js';
import {statisticsCommand,statisticsAnalysisData,statisticsCategoryLabels,csvRows,statisticsDataRows,statisticsDatasetSource,numericStatisticsRows,statisticsColumnCount,statisticsColumnNames,statisticsKindForColumns,statisticsCsvHasHeader,statisticsColumnLabels,statisticsHeatMapColumnNames,statisticsDetectedColumns,markdownTableCsv} from './workspace-commands.js';
import {readXlsxWorkbook} from './xlsx-reader.js';
import {statisticsPlot,statisticsPlotPanels} from './statistics-plot.js';
import {statisticsHeatMapData,statisticsCorrelationHeatMap,statisticsPlotNumber} from './statistics-plot-data.js';
import {clusteredHeatMap} from './statistics-cluster.js';
import {createAdvancedStatistics} from './advanced-statistics.js';
import {statisticsRequest} from './statistics-request.js';
import {renderFormulas} from './formula-preview.js';
import {editableTable} from './editable-table.js';
import {parse,latexInput} from './parser.js';
import {astSource} from './ast-source.js';
import {roundNumber} from './display-format.js';
import {renderRegressionReport,regressionResidualCSV,regressionParameterLabels,regressionParameterName} from './regression-report.js';
import {statisticsResultMarkdown} from './statistics-markdown.js';

export function regressionGraphSource(source,digits=10,variable='x') {
  function rounded(node){return {...node,value:node.kind==='number'?roundNumber(node.value,digits):node.kind==='symbol'&&node.value===variable?'x':node.value,args:node.args.map(rounded)};}
  return astSource(rounded(parse(latexInput(source))));
}

export function createStatisticsWorkspace({state,engine,ui,persist,refreshWorkspaceMath,storeExpression,error,changeMode,replaceInput,graphs,onSummary}) {
  const {toast,pickFile,openDialog}=ui;
  let statisticsGraph=null;
  let advanced=null,summaryOp="stats";
  document.querySelector('[data-run="statistics-summary"]').addEventListener("click",()=>{summaryOp="stats";},true);
  $("statistics-summary-correlation").onclick=()=>{summaryOp="correlation";onSummary?.();};
  const summaryExpression=()=>statisticsCommand(value("statistics-data"),{op:summaryOp,column:Number(value("statistics-summary-column")),kind:dataKind()});
  const summaryTermLabels=()=>{const labels=statisticsColumnLabels(value("statistics-data"),dataKind());return summaryOp==="correlation"?{'sample:1':labels[0],'sample:2':labels[1]}:{'sample:1':labels[Number(value("statistics-summary-column"))]};};
  const analysisPanels={};
  let regressionRun=null;
  let detectedColumnSource=null,detectedColumnCount=null;
  let clusterRun=null;
  let heatMapNumericColumnCount=null;
  function cancelClustering(){const run=clusterRun;clusterRun=null;run?.worker.terminate();}
  const oldPlotType=state.fields['statistics-plot-type'];
  if(oldPlotType==='clusteredheatmap'||oldPlotType==='correlationheatmap'){
    $('statistics-plot-type').value='heatmap';state.fields['statistics-plot-type']='heatmap';
    if(oldPlotType==='correlationheatmap')$('statistics-heatmap-mode').value='correlation';
    if(oldPlotType==='clusteredheatmap')$('statistics-heatmap-clustering').checked=true;
  }
  const menus=Object.fromEntries(['statistics-op','statistics-grouping','regression-kind','regression-response','statistics-plot-type'].map(id=>
    [id,[...$(id).options].map(option=>({option,label:option.textContent,group:option.parentElement.tagName==='OPTGROUP'?option.parentElement.label:''}))]));
  function filterMenu(id,available,fallback){
    const select=$(id),previous=select.value,entries=menus[id].filter(({option})=>available(option.value));
    for(const {option,label} of entries){option.disabled=false;setText(option,label);}
    if(id==='statistics-op'){
      select.replaceChildren(...[...new Set(entries.map(entry=>entry.group))].map(group=>{const block=element('optgroup');block.label=t(group);block.append(...entries.filter(entry=>entry.group===group).map(entry=>entry.option));return block;}));
    }else select.replaceChildren(...entries.map(({option})=>option));
    select.value=entries.some(({option})=>option.value===previous)?previous:
      entries.some(({option})=>option.value===fallback)?fallback:entries[0]?.option.value||'';
    return select.value!==previous;
  }
  function invalidateRegression(){cancelClustering();cancelRegression();statisticsGraph=null;$('regression-caption').replaceChildren();$('regression-inference').replaceChildren();$('regression-export').hidden=true;$('regression-transfer').hidden=true;$('statistics-plot').replaceChildren();$('statistics-plot').hidden=true;}
  function regressionBusy(busy){$('regression-section').setAttribute('aria-busy',String(busy));}
  function cancelRegression(){if(!regressionRun)return;regressionRun=null;regressionBusy(false);engine.cancel();}
  $('statistics-collapse-all').onclick=()=>{for(const details of document.querySelectorAll('.workspace[data-mode="statistics"] details[open]'))details.open=false;};
  $('statistics-new').onclick=()=>{
    cancelRegression();$('statistics-data').value='';$('dataset-name').value='';$('dataset-list').value='';
    dataKindChange();$('statistics-plot').hidden=true;persist();
  };
  const dataKind=()=>{
    if(value('statistics-kind')!=='columns')return value('statistics-kind');
    if($('statistics-columns-auto').checked){
      const source=value('statistics-data');
      if(source!==detectedColumnSource){detectedColumnSource=source;try{detectedColumnCount=statisticsDetectedColumns(source);}catch{detectedColumnCount=null;}}
      if(detectedColumnCount!==null)$('statistics-columns').value=String(detectedColumnCount);
    }
    return statisticsKindForColumns(Number(value('statistics-columns'))||4);
  };
  const dataColumns=()=>statisticsColumnCount(dataKind());
  function setDataKind(kind){
    if(kind?.startsWith('columns:')){
      const count=statisticsColumnCount(kind);
      $('statistics-columns').value=String(count);
      $('statistics-kind').value=count>=1&&count<=3?statisticsKindForColumns(count):'columns';
    }
    else $('statistics-kind').value=kind;
  }
  function inferDataKind(){try{return statisticsKindForColumns(csvRows(value('statistics-data'))[0].length);}catch{return 'list';}}
  if(!['list','xy','xyz','columns'].includes(state.fields['statistics-kind']))setDataKind(inferDataKind());
  if(state.fields['regression-response-auto']===false&&!Object.hasOwn(state.fields,'regression-response-choice'))state.fields['regression-response-choice']=state.fields['regression-response'];
  const automaticResponse=()=>state.fields['regression-response-choice']===undefined;
  if(automaticResponse())$('regression-response').value=String(dataColumns()-1);

  function regressionMode(){const base=value('regression-kind'),penalty=value('regression-penalty');if(base==='randomforest')return {auto:'randomforest',regression:'randomforestregressor',classification:'randomforestclassifier'}[value('regression-forest-task')]||'randomforest';return ['linear','multiple','logistic'].includes(base)&&penalty!=='none'?(base==='logistic'?'logistic':'')+penalty:base;}
  function datasetsList(){const previous=value('dataset-list');$('dataset-list').replaceChildren(element('option','새 데이터'));$('dataset-list').firstChild.value='';for(const name of Object.keys(state.datasets)) {const option=element('option',name);option.value=name;$('dataset-list').append(option);}if(Object.hasOwn(state.datasets,previous))$('dataset-list').value=previous;}
  $('dataset-list').onchange=()=>{const name=value('dataset-list');if(Object.hasOwn(state.datasets,name)){$('statistics-data').value=state.datasets[name];$('dataset-name').value=name;const savedKind=state.datasetKinds[name];setDataKind(statisticsColumnCount(savedKind)?savedKind:inferDataKind());dataKindChange();persist();}};
  $('dataset-save').onclick=()=>{const name=value('dataset-name').trim();if(!name){toast('데이터 이름을 입력하세요.');return;}state.datasets[name]=value('statistics-data');state.datasetKinds[name]=dataKind();datasetsList();$('dataset-list').value=name;persist();toast('데이터를 저장했습니다.');};
  $('dataset-delete').onclick=()=>{delete state.datasetKinds[value('dataset-list')];delete state.datasets[value('dataset-list')];datasetsList();persist();};
  $('csv-open').onclick=()=>pickFile('.csv,.tsv,.xlsx,text/csv,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',async file=>{
    const isXlsx=/\.xlsx$/i.test(file.name)||file.type==='application/vnd.openxmlformats-officedocument.spreadsheetml.sheet';
    if(isXlsx&&file.size>64*1024*1024)throw new Error('XLSX file is too large');
    function showPreview(rows){
      if(!rows.length)throw new Error('The selected sheet is empty');
      const content=element('div'),columns=[],header=element('input'),preview=element('pre','','csv-preview');
      const selectedColumns=()=>columns.map((input,i)=>input.checked?i:null).filter(i=>i!==null);
      function updatePreview(){const selected=selectedColumns();preview.textContent=selected.length?rows.slice(header.checked?1:0).slice(0,3).map(row=>selected.map(i=>row[i]).join('  |  ')).join('\n'):'';}
      preview.setAttribute('aria-live','polite');
      header.type='checkbox';header.checked=statisticsCsvHasHeader(rows);
      header.onchange=updatePreview;
      const headerLabel=element('label','Skip header row','check');headerLabel.append(header);content.append(headerLabel);
      for(let index=0;index<rows[0].length;index++){
        const input=element('input');input.type='checkbox';input.checked=index<3;input.onchange=updatePreview;columns.push(input);
        const label=element('label',statisticsCsvHasHeader(rows)?`${rows[0][index]} (${statisticsColumnNames(rows[0].length)[index]})`:statisticsColumnNames(rows[0].length)[index],'check');label.append(input);content.append(label);
      }
      content.append(element('h3','Preview'),preview);updatePreview();
      content.append(control('Import CSV/XLSX',()=>{
        const selected=selectedColumns();if(!selected.length){toast(t('Select at least one column'));return;}
        const body=rows.slice(header.checked?1:0),imported=header.checked?[rows[0],...body]:body;
        $('statistics-data').value=imported.map(row=>selected.map(i=>/[",\r\n\t]/.test(row[i])?'"'+row[i].replace(/"/g,'""')+'"':row[i]).join(',')).join('\n');
        $('dataset-name').value=file.name.replace(/\.(csv|tsv|xlsx)$/i,'');setDataKind(statisticsKindForColumns(selected.length));dataKindChange();persist();$('dialog').close();
      }));openDialog('Import CSV/XLSX',content);
    }
    if(!isXlsx){showPreview(csvRows((await file.text()).replace(/^\uFEFF/,''),{maxColumns:100,skipHeader:false}));return;}
    const workbook=await readXlsxWorkbook(await file.arrayBuffer()),sheets=workbook.filter(sheet=>sheet.rows.length);
    if(!sheets.length)throw new Error('The XLSX workbook has no data sheets');
    if(sheets.length===1){showPreview(sheets[0].rows);return;}
    const content=element('div'),selector=element('select'),label=element('label','Sheet');
    sheets.forEach((sheet,index)=>{const option=element('option',sheet.name);option.value=String(index);selector.append(option);});
    label.append(selector);content.append(element('p','Choose a sheet to import'),label);
    content.append(control('Continue',()=>showPreview(sheets[Number(selector.value)].rows)));openDialog('Select sheet',content);
  });
  $('csv-save').onclick=()=>downloadFile(`${value('dataset-name')||'symvacas-data'}.csv`,value('statistics-data'),'text/csv');
  function dataRows(){return statisticsDataRows(value('statistics-data'),dataKind());}
  function statisticsExpression(op=value('statistics-op')){return statisticsCommand(value('statistics-data'),{op,kind:dataKind(),column:Number(value('statistics-column')),extra:value('statistics-extra')||'0',tail:value('statistics-tail'),sigma:value('statistics-sigma'),sigmaY:value('statistics-sigma-y'),yatesCorrection:$('statistics-yates').checked,regression:regressionMode(),degree:value('regression-degree'),alpha:$('regression-alpha-cv').checked?'cv':value('regression-alpha'),l1Ratio:value('regression-ratio'),trees:value('regression-trees'),maxDepth:value('regression-depth'),seed:value('regression-seed'),responseColumn:Number(value('regression-response')),formula:value('regression-formula'),variable:value('regression-variable'),initials:value('regression-initials'),grouping:value('statistics-grouping'),groupColumns:value('statistics-group-columns-choice'),firstGroup:value('statistics-first-group'),secondGroup:value('statistics-second-group'),groupColumn:Number(value('statistics-group-column')),valueColumn:Number(value('statistics-value-column')),matching:value('statistics-pair-matching'),subjectColumn:Number(value('statistics-subject-column')),anovaMethod:value('statistics-anova-method'),independentMethod:value('statistics-t-method'),firth:value('regression-firth'),priorSD:value('regression-prior-sd'),credibleLevel:value('regression-credible-level'),varianceShape:value('regression-variance-shape'),varianceScale:value('regression-variance-scale'),bayesianMethod:value('regression-bayesian-method'),nutsSamples:value('regression-nuts-samples'),nutsWarmup:value('regression-nuts-warmup'),nutsMaxDepth:value('regression-nuts-max-depth'),nutsSeed:value('regression-nuts-seed'),nutsChains:value('regression-nuts-chains')});}
  function analysisSummary(){
    const plan=statisticsAnalysisData(value('statistics-data'),{op:value('statistics-op'),kind:dataKind(),column:Number(value('statistics-column')),grouping:value('statistics-grouping'),groupColumns:value('statistics-group-columns-choice'),firstGroup:value('statistics-first-group'),secondGroup:value('statistics-second-group'),groupColumn:Number(value('statistics-group-column')),valueColumn:Number(value('statistics-value-column')),matching:value('statistics-pair-matching'),subjectColumn:Number(value('statistics-subject-column')),anovaMethod:value('statistics-anova-method'),independentMethod:value('statistics-t-method')});
    if(plan.paired){const names=statisticsColumnNames(dataColumns()),labels=statisticsColumnLabels(value('statistics-data'),dataKind());return `${t('Compared columns')}: ${plan.samples.map(sample=>value('statistics-grouping')==='groups'&&!plan.categorical?sample.label:labels[names.indexOf(sample.label)]||sample.label).join(' ↔ ')} · ${t('Complete pairs')}: ${plan.pairs.length}${plan.omitted?` · ${t('Incomplete pairs omitted')}: ${plan.omitted}`:''}`;}
    return `${t('Analyzed groups')} (${plan.samples.length}): ${plan.samples.map(sample=>`${value('statistics-grouping')==='groups'?sample.label:statisticsColumnLabels(value('statistics-data'),dataKind())[statisticsColumnNames(dataColumns()).indexOf(sample.label)]||sample.label} (n=${sample.values?.length||0})`).join(' · ')}`;
  }
  function analysisTermLabels(){
    const plan=statisticsAnalysisData(value('statistics-data'),{op:value('statistics-op'),kind:dataKind(),column:Number(value('statistics-column')),grouping:value('statistics-grouping'),groupColumns:value('statistics-group-columns-choice'),firstGroup:value('statistics-first-group'),secondGroup:value('statistics-second-group'),groupColumn:Number(value('statistics-group-column')),valueColumn:Number(value('statistics-value-column')),matching:value('statistics-pair-matching'),subjectColumn:Number(value('statistics-subject-column')),anovaMethod:value('statistics-anova-method'),independentMethod:value('statistics-t-method')});
    const names=statisticsColumnNames(dataColumns()),labels=statisticsColumnLabels(value('statistics-data'),dataKind());
    const selected=plan.samples.map(sample=>value('statistics-grouping')==='groups'&&!plan.categorical?sample.label:labels[names.indexOf(sample.label)]||sample.label);
    return plan.categorical?statisticsCategoryLabels(plan.pairs,...selected):Object.fromEntries(selected.map((label,i)=>[`sample:${i+1}`,label]));
  }
  $('regression-kind').onchange=()=>{invalidateRegression();statisticsControls();$('regression-custom').hidden=regressionMode()!=='custom';$('regression-penalty-label').hidden=!['linear','multiple','logistic'].includes(value('regression-kind'));$('regression-lasso').hidden=!['ridge','lasso','elasticnet','logisticridge','logisticlasso','logisticelasticnet'].includes(regressionMode());$('regression-ratio-label').hidden=!regressionMode().endsWith('elasticnet');$('regression-nuts').hidden=!regressionMode().startsWith('bayes')||value('regression-bayesian-method')!=='nuts';setText($('regression-bayesian-method').options[0],regressionMode()==='bayeslinear'?'Conjugate (exact)':'Laplace approximation');$('regression-bayesian').hidden=!regressionMode().startsWith('bayes');$('regression-variance-shape').closest('label').hidden=regressionMode()!=='bayeslinear';$('regression-variance-scale').closest('label').hidden=regressionMode()!=='bayeslinear';$('regression-forest').hidden=!regressionMode().startsWith('randomforest');$('regression-degree').closest('label').hidden=regressionMode()!=='polynomial';$('regression-data-help').hidden=!['multiple','logistic','polynomial','ridge','lasso','elasticnet','logisticridge','logisticlasso','logisticelasticnet','randomforest','randomforestclassifier','randomforestregressor','bayeslinear','bayeslogistic'].includes(regressionMode());$('regression-response').closest('label').hidden=!['multiple','logistic','polynomial','ridge','lasso','elasticnet','logisticridge','logisticlasso','logisticelasticnet','randomforest','randomforestclassifier','randomforestregressor','bayeslinear','bayeslogistic'].includes(regressionMode());setText($('regression-data-help'),(regressionMode().startsWith('logistic')||regressionMode()==='bayeslogistic')?'Selected column is response; others are predictors. Logistic response: 0 or 1.':'Selected column is response; others are predictors.');refreshWorkspaceMath();};
  $('regression-kind').onchange();
  for(const id of ['regression-nuts-samples','regression-nuts-warmup','regression-nuts-max-depth','regression-nuts-seed','regression-nuts-chains','regression-prior-sd','regression-credible-level','regression-variance-shape','regression-variance-scale','regression-alpha','regression-ratio','regression-trees','regression-depth','regression-seed'])$(id).addEventListener('input',()=>{invalidateRegression();refreshWorkspaceMath();});
  $('regression-bayesian-method').onchange=()=>{$('regression-kind').onchange();persist();};
  $('regression-forest-task').onchange=()=>{$('regression-kind').onchange();persist();};
  $('regression-penalty').onchange=()=>{$('regression-kind').onchange();persist();};
  $('regression-response').onchange=()=>{state.fields['regression-response-auto']=false;state.fields['regression-response-choice']=value('regression-response');state.fields['regression-response-position']=Number(value('regression-response'))===0?'first':'last';invalidateRegression();refreshWorkspaceMath();persist();};
  $('regression-custom').hidden=regressionMode()!=='custom';
  $('regression-clear').onclick=()=>{cancelRegression();statisticsGraph=null;$('regression-caption').replaceChildren();$('regression-inference').replaceChildren();$('regression-export').hidden=true;$('regression-transfer').hidden=true;if(!$('statistics-plot').hidden)$('statistics-plot-run').click();};
  $('regression-export').onclick=()=>downloadFile('regression-residuals.csv',regressionResidualCSV(statisticsGraph?.report),'text/csv');
  function statisticsControls(){
    const columns=dataColumns(),kind=dataKind(),columnsMode=value('statistics-kind')==='columns',pairOps=['correlation','ttestpaired','wilcoxon','chi2independence','fisherexact'],multiOps=['ttest2','ztest2','mannwhitney','anova','welchanova','tukey','gameshowell','kruskal'];
    filterMenu('statistics-op',op=>columns!==1||op==='wilcoxon'||![...pairOps,...multiOps].includes(op),'ttest');
    const op=value('statistics-op'),paired=pairOps.includes(op)&&!(op==='wilcoxon'&&columns===1),categorical=['chi2independence','fisherexact'].includes(op),selectablePairs=categorical||['wilcoxon','ttestpaired'].includes(op),multi=['ttest2','ztest2','mannwhitney'].includes(op),all=['anova','welchanova','tukey','gameshowell','kruskal'].includes(op);
    filterMenu('statistics-grouping',grouping=>grouping==='columns'||columns>1,'columns');
    const grouped=columns>1&&value('statistics-grouping')==='groups';
    let rows=[];try{rows=dataRows();}catch{}
    const columnLabels=statisticsColumnLabels(value('statistics-data'),kind);
    for(const [id,fallback]of [['statistics-group-column',0],['statistics-value-column',1],['statistics-subject-column',0]]){const select=$(id),previous=select.value;select.replaceChildren(...columnLabels.map((label,i)=>{const option=element('option',label);option.value=String(i);return option;}));select.value=previous&&Number(previous)<columns?previous:String(Math.min(fallback,columns-1));}
    const summaryAt=Number(value('statistics-summary-column'));$('statistics-summary-column').replaceChildren(...columnLabels.map((label,i)=>{const option=element('option',label);option.value=String(i);return option;}));$('statistics-summary-column').value=String(Math.min(summaryAt,columns-1));$('statistics-summary-correlation').hidden=columns!==2;
    const groupColumns=$('statistics-group-columns'),selection=value('statistics-group-columns-choice');groupColumns.hidden=!all||grouped;
    const chosen=selection==='auto'?columnLabels.map((_,i)=>i):selection.split(',').filter(Boolean).map(Number);groupColumns.replaceChildren();
    for(let i=0;i<columns;i++){const label=element('label',columnLabels[i],'check'),check=element('input');check.type='checkbox';check.checked=chosen.includes(i);check.value=String(i);check.onchange=()=>{$('statistics-group-columns-choice').value=[...groupColumns.querySelectorAll('input:checked')].map(input=>input.value).join(',');statisticsControls();persist();};label.append(check);groupColumns.append(label);}
    const plotGroup=$('statistics-plot-grouping'),previousPlotGroup=plotGroup.value;
    const plotGroupIndex=previousPlotGroup==='first'?0:previousPlotGroup==='last'?columns-1:Number(previousPlotGroup.replace('column:',''));
    const plotGroupChoices=[{id:'columns',label:t('Columns')},...columnLabels.map((label,i)=>({id:`column:${i}`,label}))];
    plotGroup.replaceChildren(...plotGroupChoices.map(choice=>{const option=element('option',choice.label);option.value=choice.id;return option;}));plotGroup.value=previousPlotGroup!=='columns'&&plotGroupIndex>=0&&plotGroupIndex<columns?`column:${plotGroupIndex}`:'columns';
    const groupAt=Number(value('statistics-group-column'));
    const names=grouped&&!categorical?[...new Set(rows.map(row=>row[groupAt]).filter(Boolean))]:paired&&!selectablePairs?['x','y']:statisticsColumnNames(columns);
    const selectedColumn=value('statistics-column');$('statistics-column').replaceChildren(...columnLabels.map((name,i)=>{const option=element('option',name);option.value=String(i);return option;}));$('statistics-column').value=Number(selectedColumn)<columns?selectedColumn:'0';
    for(const [id,fallback] of [['statistics-first-group',0],['statistics-second-group',1]]){
      const select=$(id),previous=select.value,choices=(multi||selectablePairs)&&id==='statistics-second-group'?names.filter(name=>name!==value('statistics-first-group')):names;
      select.replaceChildren(...choices.map(name=>{const option=element('option',grouped&&!categorical?name:columnLabels[statisticsColumnNames(columns).indexOf(name)]||name);option.value=name;return option;}));
      select.value=paired&&!selectablePairs&&!grouped?names[fallback]:choices.includes(previous)?previous:choices.includes(names[fallback])?names[fallback]:choices[0]||'';
      const label=select.closest('label');if(label.firstChild.nodeType===3)label.replaceChild(element('span'),label.firstChild);
      setText(label.firstChild,categorical?(fallback===0?'First column':'Second column'):(fallback===0?'First group':'Second group'));
    }
    $('statistics-first-group').closest('label').hidden=all||grouped&&categorical||!paired&&!multi&&!grouped;
    $('statistics-second-group').closest('label').hidden=all||grouped&&categorical||!paired&&!multi;
    $('statistics-grouping').disabled=columns<2;
    $('statistics-column').disabled=paired||multi||all||grouped||columns===1;
    $('statistics-first-group').disabled=paired&&!selectablePairs&&!grouped||all||!multi&&!grouped&&!selectablePairs||columns===1;
    $('statistics-second-group').disabled=!multi&&!selectablePairs&&!grouped||all||columns===1;
    $('statistics-group-role-options').hidden=!grouped;
    $('statistics-pair-matching-label').hidden=!grouped||!paired||categorical;
    $('statistics-subject-column-label').hidden=!grouped||!paired||categorical||value('statistics-pair-matching')!=='subject';
    $('statistics-pair-order-help').hidden=!grouped||!paired||categorical||value('statistics-pair-matching')==='subject';
    $('statistics-t-method-label').hidden=op!=='ttest2';$('statistics-anova-method-label').hidden=op!=='anova';$('statistics-posthoc-help').hidden=op!=='anova';
    setText($('statistics-posthoc-help'),value('statistics-anova-method')==='classic'?'Set analysis: ANOVA + Tukey–Kramer + assumptions':'Set analysis: Welch ANOVA + Games–Howell + assumptions');
    $('statistics-extra').disabled=!['ttest','ttest2','ttestpaired','ztest','ztest2','tinterval','zinterval'].includes(op);
    $('statistics-tail').disabled=!['ttest','ttest2','ttestpaired','wilcoxon','mannwhitney','ztest','ztest2','fisherexact'].includes(op);
    $('statistics-sigma').disabled=!['ztest','ztest2','zinterval'].includes(op);
    $('statistics-sigma-y').disabled=op!=='ztest2';
    $('statistics-yates-options').hidden=op!=='chi2independence';
    $('statistics-columns-label').hidden=!columnsMode;
    $('statistics-columns-auto-label').hidden=!columnsMode;
    $('statistics-columns').disabled=$('statistics-columns-auto').checked;
    $('regression-section').hidden=columns<2;
    $('regression-response').value=automaticResponse()?String(columns-1):String(state.fields['regression-response-choice']);
    const positionResponse=false;
    if(positionResponse&&state.fields['regression-response-position']==='last')$('regression-response').value=String(columns-1);
    const responseOptions=statisticsColumnNames(columns).flatMap((name,i)=>{
      if(positionResponse&&i!==0&&i!==columns-1)return [];
      const label=columnLabels[i]||name,option=element('option',label);option.value=String(i);return [{option,label}];
    });
    menus['regression-response']=responseOptions;
    filterMenu('regression-response',column=>Number(column)<columns,String(columns-1));
    if(kind!=='list'&&!automaticResponse())state.fields['regression-response-choice']=value('regression-response');
    if(filterMenu('regression-kind',model=>kind==='xyz'||kind.startsWith('columns:')?columns>1&&['multiple','logistic','randomforest','bayeslinear','bayeslogistic'].includes(model):kind==='xy'&&model!=='multiple',kind==='xyz'||kind.startsWith('columns:')?'multiple':'linear'))$('regression-kind').onchange();
    $('regression-data-help').hidden=!['multiple','logistic','polynomial','ridge','lasso','elasticnet','logisticridge','logisticlasso','logisticelasticnet','randomforest','randomforestclassifier','randomforestregressor','bayeslinear','bayeslogistic'].includes(regressionMode());
    $('regression-response').closest('label').hidden=!['multiple','logistic','polynomial','ridge','lasso','elasticnet','logisticridge','logisticlasso','logisticelasticnet','randomforest','randomforestclassifier','randomforestregressor','bayeslinear','bayeslogistic'].includes(regressionMode());
    $('regression-firth-label').hidden=regressionMode()!=='logistic';
    $('regression-alpha').disabled=$('regression-alpha-cv').checked;
    if(value('statistics-plot-type')==='clusteredheatmap'||value('statistics-plot-type')==='correlationheatmap'){
      if(value('statistics-plot-type')==='correlationheatmap')$('statistics-heatmap-mode').value='correlation';
      if(value('statistics-plot-type')==='clusteredheatmap')$('statistics-heatmap-clustering').checked=true;
      $('statistics-plot-type').value='heatmap';
    }
    filterMenu('statistics-plot-type',plot=>plot!=='scatter'||kind==='xy',kind==='xy'?'scatter':'histogram');
    const heatMap=value('statistics-plot-type')==='heatmap',correlation=heatMap&&value('statistics-heatmap-mode')==='correlation';
    $('statistics-heatmap-options').hidden=!heatMap;
    $('statistics-heatmap-correlation-label').hidden=!correlation;
    $('statistics-heatmap-axes').hidden=!correlation;
    const clusteringOn=$('statistics-heatmap-clustering').checked;
    $('statistics-heatmap-linkage-label').hidden=!clusteringOn;
    $('statistics-heatmap-metric-label').hidden=!clusteringOn;
    const wardLinkage=clusteringOn&&value('statistics-heatmap-linkage')==='ward';
    if(wardLinkage)$('statistics-heatmap-metric').value='euclidean';
    $('statistics-heatmap-metric').disabled=wardLinkage;
    $('statistics-plot-grouping-label').hidden=value('statistics-plot-type')==='scatter'||correlation||columns<2;
    $('statistics-plot-orientation-label').hidden=!['box','violin'].includes(value('statistics-plot-type'));
    if(correlation){
      let heatMapRows=[];try{heatMapRows=dataRows();}catch{}
      const names=statisticsColumnLabels(value('statistics-data'),kind);
      const numericCount=names.filter((_,index)=>heatMapRows.some(row=>statisticsPlotNumber(row[index])!==null)).length;
      if(heatMapNumericColumnCount!==null&&heatMapNumericColumnCount<2&&numericCount>=2){$('statistics-heatmap-x-axis').replaceChildren();$('statistics-heatmap-y-axis').replaceChildren();}
      updateHeatMapAxis('statistics-heatmap-x-axis',names,heatMapRows);
      const selectedX=new Set([...$('statistics-heatmap-x-axis').querySelectorAll('input:checked')].map(input=>Number(input.value)));
      updateHeatMapAxis('statistics-heatmap-y-axis',names,heatMapRows,selectedX);
      heatMapNumericColumnCount=numericCount;
      if(numericCount>=2)saveHeatMapAxisSelections();
      else{delete state.fields['statistics-heatmap-x-columns'];delete state.fields['statistics-heatmap-y-columns'];}
    }
    // Detected header names replace the generic x/y names in the localized value hint.
    const dataLabels=statisticsColumnLabels(value('statistics-data'),kind).join(', ');
    setText($('statistics-data-label'),kind==='list'?'One value per line':kind==='xy'?t('x, y values').replace('x, y',dataLabels):kind==='xyz'?t('x, y, z values').replace('x, y, z',dataLabels):dataLabels);
    try{$('statistics-samples').textContent=analysisSummary();}catch{setText($('statistics-samples'),'Enter data to see analyzed groups');}
    for(const panel of Object.values(analysisPanels))panel.render();
    $('statistics-basic-controls').hidden=op==='mcnemar';
    $('statistics-general').hidden=op!=='mcnemar';
    updateLineNumbers();
  }
  function updateHeatMapAxis(id,names,rows,excluded=new Set()){
    const container=$(id),axis=id.endsWith('x-axis')?'x':'y',stateField=`statistics-heatmap-${axis}-columns`,existing=[...container.querySelectorAll('input:checked')].map(input=>Number(input.value)),hadChoices=container.querySelectorAll('input').length>0;
    const savedText=String(state.fields[stateField]||''),hasSaved=Object.hasOwn(state.fields,stateField)&&savedText!=='',saved=new Set(savedText==='-'?[]:savedText.split(',').map(Number).filter(Number.isInteger));
    const available=names.map((name,index)=>({name,index})).filter(({index})=>rows.some(row=>statisticsPlotNumber(row[index])!==null));
    const split=Math.ceil(available.length/2),defaults=new Set((axis==='x'?available.slice(0,split):available.slice(split)).map(({index})=>index));
    let selected=hadChoices?new Set(existing):hasSaved?saved:defaults;
    if(hasSaved&&savedText!=='-'&&!available.some(({index})=>selected.has(index)))selected=defaults;
    selected=new Set([...selected].filter(index=>available.some(option=>option.index===index)&&!excluded.has(index)));
    const choices=available.map(({name,index})=>{
      const input=element('input');input.type='checkbox';input.value=String(index);input.checked=selected.has(index);
      const label=element('label',name,'check');label.append(input);input.onchange=()=>{
        if(input.checked){
          const otherId=axis==='x'?'statistics-heatmap-y-axis':'statistics-heatmap-x-axis';
          const other=[...$(otherId).querySelectorAll('input')].find(item=>item.value===input.value);if(other)other.checked=false;
        }
        saveHeatMapAxisSelections();
        $('statistics-plot-run').click();persist();
      };
      return label;
    });
    if(choices.length)container.replaceChildren(...choices);else container.replaceChildren(element('span',t('No numeric columns')));
  }
  function saveHeatMapAxisSelections(){
    for(const [axis,id] of [['x','statistics-heatmap-x-axis'],['y','statistics-heatmap-y-axis']]){
      const selected=[...$(id).querySelectorAll('input:checked')].map(input=>input.value);
      state.fields[`statistics-heatmap-${axis}-columns`]=selected.join(',')||'-';
    }
  }
  function dataKindChange(){cancelClustering();cancelRegression();statisticsGraph=null;$('regression-caption').replaceChildren();$('regression-inference').replaceChildren();$('regression-export').hidden=true;$('regression-transfer').hidden=true;$('statistics-plot').replaceChildren();$('statistics-plot-type').value=dataKind()==='xy'?'scatter':'histogram';render();refreshWorkspaceMath();}
  $('statistics-kind').onchange=dataKindChange;
  $('statistics-columns').onchange=()=>{
    const count=Number(value('statistics-columns'));
    if(!Number.isInteger(count)||count<1||count>100){$('statistics-columns').value=String(state.fields['statistics-columns']||4);return;}
    dataKindChange();persist();
  };
  $('statistics-columns-auto').onchange=()=>{invalidateRegression();render();refreshWorkspaceMath();persist();};
  $('statistics-op').onchange=()=>{if(['tinterval','zinterval'].includes(value('statistics-op'))&&value('statistics-extra')==='0')$('statistics-extra').value='95';statisticsControls();refreshWorkspaceMath();};
  $('statistics-grouping').onchange=()=>{statisticsControls();refreshWorkspaceMath();};
  for(const id of ['statistics-column','statistics-first-group','statistics-second-group','statistics-group-column','statistics-value-column','statistics-subject-column','statistics-pair-matching','statistics-anova-method','statistics-t-method'])$(id).onchange=()=>{statisticsControls();refreshWorkspaceMath();persist();};
  $('statistics-yates').onchange=()=>{refreshWorkspaceMath();persist();};
  function editorRows(){return value('statistics-data').trim()?csvRows(value('statistics-data'),{preserveEmptyRows:true}):[];}
  // Number every logical line; the gutter shares the textarea line height and mirrors its vertical scroll.
  function updateLineNumbers(){
    const field=$('statistics-data'),gutter=$('statistics-line-numbers'),count=field.value.split('\n').length,numbers=[];
    for(let line=1;line<=count;line++)numbers.push(line);
    gutter.textContent=numbers.join('\n');syncLineNumberScroll();
  }
  function syncLineNumberScroll(){$('statistics-line-numbers').style.transform=`translateY(${-$('statistics-data').scrollTop}px)`;}
  // The grid edits only the rows below a detected header, so the header row has to survive every write-back.
  function editorHeader(){
    try{
      const stored=csvRows(value('statistics-data'),{preserveEmptyRows:true,skipHeader:false}),data=editorRows();
      return data.length<stored.length?stored.slice(0,stored.length-data.length):[];
    }catch{return [];}
  }
  // Quote an empty List cell so a blank row survives serialization and reload.
  function writeStoredRows(rows){invalidateRegression();$('statistics-data').value=rows.map(row=>row.length===1&&!row[0]?'""':row.map(cell=>/[",\r\n\t]/.test(cell)?'"'+cell.replace(/"/g,'""')+'"':cell).join(',')).join('\n');statisticsControls();refreshWorkspaceMath();persist();}
  function statisticsGrid(){
    const rows=editorRows(),columns=dataColumns();
    const table=editableTable({rows:rows.length,columns:statisticsColumnLabels(value('statistics-data'),dataKind()),label:t('Stats data'),value:(row,col)=>rows[row][col]||'',
      onInput:(row,col,cell)=>{while(rows[row].length<columns)rows[row].push('');rows[row][col]=cell;writeRows(rows);},
      onMoveColumn:moveColumn,onDeleteColumn:removeColumn,
      onDeleteRow:row=>{rows.splice(row,1);writeRows(rows);statisticsGrid();}});
    $('statistics-grid').replaceChildren(table);
  }
  function writeRows(rows){writeStoredRows(editorHeader().concat(rows));}
  function storedRows(){return editorHeader().concat(editorRows());}
  // Column edits rewrite the header and every data row, matching the Android statistics table editor.
  function moveColumn(column,delta){
    try{
      const target=column+delta;
      writeStoredRows(storedRows().map(row=>{
        if(column<0||target<0||column>=row.length||target>=row.length)return row;
        const swapped=row.slice();swapped[column]=row[target];swapped[target]=row[column];return swapped;
      }));
      statisticsGrid();
    }catch(exc){error(exc.message);}
  }
  function removeColumn(column){
    try{
      const remaining=Math.max(dataColumns()-1,1);
      if(!($('statistics-columns-auto').checked&&value('statistics-kind')==='columns'))setDataKind(statisticsKindForColumns(remaining));
      if(statisticsKindForColumns(remaining)!=='xy'&&value('statistics-plot-type')==='scatter')$('statistics-plot-type').value='histogram';
      writeStoredRows(storedRows().map(row=>column<row.length?row.filter((_,index)=>index!==column):row));
      statisticsGrid();
    }catch(exc){error(exc.message);}
  }
  $('statistics-table-toggle').onclick=()=>{
    const grid=$('statistics-grid'),tableMode=grid.hidden;
    if(tableMode)try{statisticsGrid();}catch(exc){error(exc.message);return;}
    grid.hidden=!tableMode;$('statistics-data').closest('label').hidden=tableMode;
    setText($('statistics-table-toggle'),tableMode?'Direct input':'Table editor');
    $('statistics-table-toggle').setAttribute('aria-pressed',String(tableMode));
  };
  $('statistics-add-row').onclick=()=>{try{const rows=editorRows(),columns=Math.max(dataColumns(),rows[0]?.length||0);rows.push(Array(columns).fill(''));writeRows(rows);if(!$('statistics-grid').hidden)statisticsGrid();}catch(exc){error(exc.message);}};
  $('statistics-editor-expand').onclick=()=>{
    const expanded=$('statistics-editor').classList.toggle('expanded');
    setText($('statistics-editor-expand'),expanded?'Collapse':'Expand');
    $('statistics-editor-expand').setAttribute('aria-pressed',String(expanded));
  };
  $('statistics-data').addEventListener('input',()=>{invalidateRegression();statisticsControls();});
  $('statistics-data').addEventListener('scroll',syncLineNumberScroll);
  $('statistics-data').addEventListener('paste',event=>{
    const text=event.clipboardData?.getData('text/plain')||event.clipboardData?.getData('text');if(!text)return;
    const converted=markdownTableCsv(text);if(converted===null)return;
    const field=$('statistics-data'),start=field.selectionStart,end=field.selectionEnd;
    event.preventDefault();field.setRangeText(converted,start,end,'end');field.dispatchEvent(new field.ownerDocument.defaultView.Event('input',{bubbles:true}));
  });
  $('statistics-data').addEventListener('change',()=>{invalidateRegression();statisticsControls();if(!$('statistics-grid').hidden)statisticsGrid();});
  $('statistics-store').onclick=async()=>{const name=value('dataset-name').trim();if(!/^[A-Za-z][A-Za-z0-9_]*$/.test(name)){error('Dataset name must be a valid variable name');return;}try{await storeExpression(name,statisticsDatasetSource(value('statistics-data'),dataKind()));}catch(exc){error(exc.message);}};
  $('statistics-plot-run').onclick=()=>{try{statisticsGraph={...statisticsGraph,rows:numericStatisticsRows(dataRows()),curve:statisticsGraph?.curve||[]};$('statistics-plot').hidden=false;drawStatisticsGraph();}catch(exc){error(exc.message);}};
  $('statistics-visualize').addEventListener('toggle',()=>{if($('statistics-visualize').open&&statisticsGraph&&!$('statistics-plot').hidden)drawStatisticsGraph();});
  $('statistics-plot-type').onchange=()=>{statisticsControls();$('statistics-plot-run').click();persist();};
  $('statistics-plot-grouping').onchange=()=>{$('statistics-plot-run').click();persist();};
  $('statistics-plot-orientation').onchange=()=>{$('statistics-plot-run').click();persist();};
  for(const id of ['statistics-heatmap-mode','statistics-heatmap-correlation','statistics-heatmap-clustering','statistics-heatmap-fit','statistics-heatmap-linkage','statistics-heatmap-metric'])$(id).onchange=()=>{statisticsControls();$('statistics-plot-run').click();persist();};
  $('regression-transfer').onclick=()=>{if(!statisticsGraph?.fit)return;try{const source=regressionGraphSource(statisticsGraph.fit,state.digits,statisticsGraph.variable);$('graph-kind').value='cartesian';$('graph-source').value=source;changeMode('graph');graphs.run();}catch(exc){error(exc.message);}};

  function drawStatisticsGraph(){
    const container=$('statistics-plot'),type=value('statistics-plot-type'),options={type,orientation:value('statistics-plot-orientation'),digits:state.digits,heatMapFit:$('statistics-heatmap-fit').checked,curve:statisticsGraph.curve,xAxisLabel:statisticsGraph.xAxisLabel||'x',yAxisLabel:statisticsGraph.yAxisLabel||'y'};
    const cluster=$('statistics-heatmap-clustering').checked;
    if(type!=='heatmap'||!cluster)cancelClustering();
    if(type==='scatter'){statisticsPlot(container,statisticsGraph.plotRows||statisticsGraph.rows,options);return;}
    if(type==='heatmap'){
      const rows=dataRows(),columnNames=statisticsHeatMapColumnNames(value('statistics-data'),dataKind()),mode=value('statistics-heatmap-mode'),method=value('statistics-heatmap-correlation'),clusterOptions={linkage:value('statistics-heatmap-linkage'),metric:value('statistics-heatmap-metric')};
      const axisSelection=id=>[...$(id).querySelectorAll('input:checked')].map(input=>Number(input.value));
      const xColumns=axisSelection('statistics-heatmap-x-axis'),yColumns=axisSelection('statistics-heatmap-y-axis');
      const data=mode==='correlation'?statisticsCorrelationHeatMap(rows,{columnCount:dataColumns(),columnNames,xColumns,yColumns,method}):statisticsHeatMapData(rows,{grouping:value('statistics-plot-grouping'),columnCount:dataColumns(),columnNames,mode});
      if(!cluster){statisticsPlot(container,statisticsGraph.rows,{...options,heatMap:data});return;}
      const key=JSON.stringify([value('statistics-data'),dataKind(),value('statistics-plot-grouping'),mode,method,xColumns,yColumns,clusterOptions]);
      if(statisticsGraph.clusterKey===key&&statisticsGraph.clusterData){cancelClustering();statisticsPlot(container,statisticsGraph.rows,{...options,heatMap:statisticsGraph.clusterData});return;}
      setText(container,'Clustering…');if(clusterRun?.key===key){clusterRun.graph=statisticsGraph;return;}
      cancelClustering();
      if(typeof Worker==='undefined'){statisticsGraph.clusterKey=key;statisticsGraph.clusterData=clusteredHeatMap(data,clusterOptions);statisticsPlot(container,statisticsGraph.rows,{...options,heatMap:statisticsGraph.clusterData});return;}
      const worker=new Worker(new URL('./statistics-cluster-worker.js',import.meta.url),{type:'module'}),run={worker,key,graph:statisticsGraph};clusterRun=run;
      worker.onmessage=({data:message})=>{
        if(clusterRun!==run||statisticsGraph!==run.graph||value('statistics-plot-type')!=='heatmap'||!$('statistics-heatmap-clustering').checked)return;
        cancelClustering();if(message.error){container.replaceChildren();error(message.error);return;}
        statisticsGraph.clusterKey=key;statisticsGraph.clusterData=message.result;
        statisticsPlot(container,statisticsGraph.rows,{...options,digits:state.digits,heatMap:message.result});
      };
      worker.onerror=event=>{if(clusterRun!==run)return;cancelClustering();container.replaceChildren();error(event.message||'Clustering failed');};
      worker.postMessage({data,options:clusterOptions});return;
    }
    const panels=statisticsPlotPanels(dataRows(),{grouping:value('statistics-plot-grouping'),columnCount:dataColumns(),columnNames:statisticsColumnLabels(value('statistics-data'),dataKind())});
    if(panels.length===1&&!panels[0].label){statisticsPlot(container,statisticsGraph.rows,{...options,series:panels[0].series});return;}
    container.replaceChildren(...panels.map(panel=>{
      const section=element('section','','statistics-plot-panel'),chart=element('div');
      section.dataset.column=panel.label;section.append(element('h3',panel.label),chart);
      statisticsPlot(chart,statisticsGraph.rows,{...options,series:panel.series});return section;
    }));
  }
  function render(){statisticsControls();if(!$('statistics-grid').hidden)statisticsGrid();if(statisticsGraph){drawStatisticsGraph();if(statisticsGraph.captions)renderFormulas($('regression-caption'),statisticsGraph.captions,{digits:state.digits});for(const [name,n] of statisticsGraph.parameters||[])$('regression-caption').append(element('span',`${regressionParameterName(name,statisticsGraph.parameterLabels)} = ${roundNumber(String(n),state.digits)}`,'regression-parameter'));
    const snapshot=statisticsGraph.result;
    const copyOptions={digits:state.digits,regressionVariables:statisticsGraph.regressionVariables,regressionPrefix:statisticsGraph.regressionPrefix};
    renderRegressionReport($('regression-inference'),statisticsGraph.report,state.digits,statisticsGraph.parameterLabels,{onCopy:snapshot?()=>ui.clipboard(statisticsResultMarkdown(snapshot,copyOptions)):null});}}
  function showRegression(result) {
    const rows=numericStatisticsRows(dataRows()),names=statisticsColumnNames(dataColumns()),logistic=(regressionMode().startsWith('logistic')||regressionMode()==='bayeslogistic');
    const response=['multiple','logistic','polynomial','ridge','lasso','elasticnet','logisticridge','logisticlasso','logisticelasticnet','randomforest','randomforestclassifier','randomforestregressor','bayeslinear','bayeslogistic'].includes(regressionMode())?Number(value('regression-response')):names.length-1,predictors=names.filter((_,i)=>i!==response);
    const parameterLabels=regressionParameterLabels(regressionMode(),statisticsColumnLabels(value('statistics-data'),dataKind()),response);
    const variables=Object.fromEntries(predictors.map((name,i)=>[names.length===2?'x':`x${i+1}`,name]));
    let displayed=result.decimal||result.exact;
    if(logistic||['polynomial','ridge','lasso','elasticnet','bayeslinear'].includes(regressionMode())||names.length>2){
      const renamed=node=>({...node,value:node.kind==='symbol'?(variables[node.value]||node.value):node.value,args:node.args.map(renamed)});
      try{displayed=astSource(renamed(parse(latexInput(displayed))));}catch{}
    }

    statisticsGraph={rows,plotRows:['polynomial','logistic','ridge','lasso','elasticnet','logisticridge','logisticlasso','logisticelasticnet','randomforest','randomforestclassifier','randomforestregressor','bayeslinear','bayeslogistic'].includes(regressionMode())&&names.length===2?rows.map(row=>[row[1-response],row[response]]):rows,xAxisLabel:predictors[0],yAxisLabel:names[response],curve:result.curve||[],fit:result.decimal||result.exact,report:result.regression,parameterLabels,parameters:(result.parameters||[]).filter(([name])=>parameterLabels[name]),variable:regressionMode()==='custom'?value('regression-variable'):'x',
      captions:regressionMode().startsWith('randomforest')?[]:[`${logistic?`P(${names[response]}=1)`:names[response]}=${displayed}`,...(result.correlation!==null&&result.correlation!==undefined?[`r=${result.correlation}`]:[]),...(result.parameters||[]).filter(([name])=>!parameterLabels[name]).map(([name,n])=>`${name}=${n}`)]};
    statisticsGraph.result=result;
    statisticsGraph.regressionVariables=variables;
    statisticsGraph.regressionPrefix=logistic?`P(${names[response]} = 1) = `:`${names[response]} = `;
    $('statistics-plot').hidden=false;render();
    $('regression-transfer').hidden=dataColumns()!==2||regressionMode().startsWith('randomforest');
    $('regression-export').hidden=!result.regression;
  }
  async function runRegression(options,showResult){
    if(regressionRun||!engine.ready)return;
    const run={};
    regressionRun=run;
    try{
      const source=statisticsExpression('regression'),expressionRequest=statisticsRequest(latexInput(source));
      regressionBusy(true);
      const result=await engine.execute({...options,...expressionRequest},{context:'regression'});
      if(regressionRun!==run||source!==statisticsExpression('regression'))return;
      showResult(result,source,source,{decimalDisplay:true});
      if(result.ok)showRegression(result);
    }catch(exc){if(regressionRun===run)error(exc.message);}
    finally{if(regressionRun===run){regressionRun=null;regressionBusy(false);}}
  }
  for(const section of ['preparation','general','tests','models','advanced'])analysisPanels[`statistics-${section}`]=createAdvancedStatistics({state,persist,data:()=>value('statistics-data'),columnLimit:()=>dataColumns(),copy:ui.clipboard,section,applyData:updated=>{
    $('statistics-data').value=updated;const selected=value('dataset-list');
    if(selected&&selected===value('dataset-name')&&Object.hasOwn(state.datasets,selected))state.datasets[selected]=updated;
    dataKindChange();persist();toast(t('Missing values applied to current data'));
  }});
  advanced=analysisPanels['statistics-advanced'];
  statisticsControls();
  return {datasetsList,summaryExpression,summaryTermLabels,expression:statisticsExpression,analysisTermLabels,advancedExpression:(panel='statistics-advanced')=>analysisPanels[panel].expression(),advancedContext:(panel='statistics-advanced')=>analysisPanels[panel].context(),showAdvancedResult:(result,context,panel='statistics-advanced')=>analysisPanels[panel]?.showResult(result,context),analysisSummary,render,showRegression,runRegression};
}
