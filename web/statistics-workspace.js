import {$,value,element,control} from './app-ui.js';
import {t,setText} from './i18n.js';
import {downloadFile} from './storage.js';
import {statisticsCommand,statisticsAnalysisData,distributionCommand,csvRows,statisticsDataRows,statisticsDatasetSource,numericStatisticsRows,statisticsColumnCount,statisticsColumnNames,statisticsKindForColumns,statisticsCsvHasHeader,statisticsColumnLabels} from './workspace-commands.js';
import {statisticsPlot,statisticsPlotPanels} from './statistics-plot.js';
import {renderFormulas} from './formula-preview.js';
import {editableTable} from './editable-table.js';
import {parse,latexInput} from './parser.js';
import {astSource} from './ast-source.js';
import {roundNumber} from './display-format.js';
import {renderRegressionReport,regressionResidualCSV,regressionParameterLabels,regressionParameterName} from './regression-report.js';

export function regressionGraphSource(source,digits=10,variable='x') {
  function rounded(node){return {...node,value:node.kind==='number'?roundNumber(node.value,digits):node.kind==='symbol'&&node.value===variable?'x':node.value,args:node.args.map(rounded)};}
  return astSource(rounded(parse(latexInput(source))));
}

export function createStatisticsWorkspace({state,engine,ui,persist,refreshWorkspaceMath,storeExpression,error,changeMode,replaceInput,graphs}) {
  const {toast,pickFile,openDialog}=ui;
  let statisticsGraph=null;
  let regressionRun=null;
  const menus=Object.fromEntries(['statistics-op','statistics-grouping','regression-kind','regression-response','statistics-plot-type'].map(id=>
    [id,[...$(id).options].map(option=>({option,label:option.textContent}))]));
  function filterMenu(id,available,fallback){
    const select=$(id),previous=select.value,entries=menus[id].filter(({option})=>available(option.value));
    for(const {option,label} of entries){option.disabled=false;setText(option,label);}
    select.replaceChildren(...entries.map(({option})=>option));
    select.value=entries.some(({option})=>option.value===previous)?previous:
      entries.some(({option})=>option.value===fallback)?fallback:entries[0]?.option.value||'';
    return select.value!==previous;
  }
  function invalidateRegression(){cancelRegression();statisticsGraph=null;$('regression-caption').replaceChildren();$('regression-inference').replaceChildren();$('regression-export').hidden=true;$('regression-transfer').hidden=true;$('statistics-plot').replaceChildren();$('statistics-plot').hidden=true;}
  function regressionBusy(busy){$('regression-progress').hidden=!busy;$('regression-cancel').hidden=!busy;$('regression-section').setAttribute('aria-busy',String(busy));}
  function cancelRegression(){if(!regressionRun)return;regressionRun=null;regressionBusy(false);engine.cancel();}
  $('regression-cancel').onclick=cancelRegression;
  $('statistics-new').onclick=()=>{
    cancelRegression();$('statistics-data').value='';$('dataset-name').value='';$('dataset-list').value='';
    dataKindChange();$('statistics-plot').hidden=true;persist();
  };
  const dataKind=()=>value('statistics-kind')==='columns'?`columns:${Number(value('statistics-columns'))||4}`:value('statistics-kind');
  const dataColumns=()=>statisticsColumnCount(dataKind());
  function setDataKind(kind){
    if(kind?.startsWith('columns:')){$('statistics-columns').value=String(statisticsColumnCount(kind));$('statistics-kind').value='columns';}
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
  $('csv-open').onclick=()=>pickFile('.csv,.tsv,text/csv',async file=>{
    const rows=csvRows((await file.text()).replace(/^\uFEFF/,''),{maxColumns:100,skipHeader:false}),content=element('div'),columns=[],header=element('input'),preview=element('pre','','csv-preview');
    const selectedColumns=()=>columns.map((input,i)=>input.checked?i:null).filter(i=>i!==null);
    function updatePreview(){const selected=selectedColumns();preview.textContent=selected.length?rows.slice(header.checked?1:0).slice(0,3).map(row=>selected.map(i=>row[i]).join('  |  ')).join('\n'):'';}
    preview.setAttribute('aria-live','polite');
    header.type='checkbox';header.checked=statisticsCsvHasHeader(rows);
    header.onchange=updatePreview;
    const headerLabel=element('label','Skip header row','check');headerLabel.append(header);content.append(headerLabel);
    for(let index=0;index<rows[0].length;index++){
      const input=element('input');input.type='checkbox';input.checked=index<3;input.onchange=updatePreview;columns.push(input);
      const label=element('label',`${t('Column')} ${index+1}: ${rows[0][index]}`,'check');label.append(input);content.append(label);
    }
    content.append(element('h3','Preview'),preview);updatePreview();
    content.append(control('Import CSV',()=>{
      const selected=selectedColumns();if(!selected.length){toast(t('Select at least one column'));return;}
      $('statistics-data').value=rows.slice(header.checked?1:0).map(row=>selected.map(i=>/[",\r\n\t]/.test(row[i])?'"'+row[i].replace(/"/g,'""')+'"':row[i]).join(',')).join('\n');
      $('dataset-name').value=file.name.replace(/\.(csv|tsv)$/i,'');setDataKind(statisticsKindForColumns(selected.length));dataKindChange();persist();$('dialog').close();
    }));openDialog('Import CSV',content);
  });
  $('csv-save').onclick=()=>downloadFile(`${value('dataset-name')||'symvacas-data'}.csv`,value('statistics-data'),'text/csv');
  function dataRows(){return statisticsDataRows(value('statistics-data'),dataKind());}
  function statisticsExpression(op=value('statistics-op')){return statisticsCommand(value('statistics-data'),{op,kind:dataKind(),column:Number(value('statistics-column')),extra:value('statistics-extra')||'0',tail:value('statistics-tail'),sigma:value('statistics-sigma'),sigmaY:value('statistics-sigma-y'),yatesCorrection:$('statistics-yates').checked,regression:regressionMode(),degree:value('regression-degree'),alpha:value('regression-alpha'),l1Ratio:value('regression-ratio'),trees:value('regression-trees'),maxDepth:value('regression-depth'),seed:value('regression-seed'),responseColumn:Number(value('regression-response')),formula:value('regression-formula'),variable:value('regression-variable'),initials:value('regression-initials'),grouping:value('statistics-grouping'),firstGroup:value('statistics-first-group'),secondGroup:value('statistics-second-group')});}
  function analysisSummary(){
    const plan=statisticsAnalysisData(value('statistics-data'),{op:value('statistics-op'),kind:dataKind(),column:Number(value('statistics-column')),grouping:value('statistics-grouping'),firstGroup:value('statistics-first-group'),secondGroup:value('statistics-second-group')});
    if(plan.paired)return `${t('Compared columns')}: x ↔ y · ${t('Complete pairs')}: ${plan.pairs.length}`;
    return `${t('Analyzed groups')} (${plan.samples.length}): ${plan.samples.map(sample=>`${sample.label} (n=${sample.values?.length||0})`).join(' · ')}`;
  }
  function distributionExpression(){return distributionCommand(Object.fromEntries(['family','query','x','a','b','p','mean','sigma','df','df2','trials','success','lambda','k'].map(name=>[name,value('distribution-'+name)])));}
  function distributionControls(){const family=value('distribution-family'),discrete=['binomial','poisson','geometric'].includes(family),queries=discrete?(family==='binomial'?['pdf','cdf','list-pdf','list-cdf']:['pdf','cdf']):['normal','t'].includes(family)?['pdf','cdf','interval','quantile']:['pdf','cdf','interval'];for(const option of $('distribution-query').options)option.disabled=!queries.includes(option.value);if(!queries.includes(value('distribution-query')))$('distribution-query').value='cdf';const query=value('distribution-query'),shown=new Set(discrete?(query.startsWith('list')?[]:['k']):query==='interval'?['a','b']:query==='quantile'?['p']:['x']);for(const name of family==='normal'?['mean','sigma']:family==='f'?['df','df2']:['t','chi2'].includes(family)?['df']:family==='binomial'?['trials','success']:family==='poisson'?['lambda']:['success'])shown.add(name);for(const name of ['x','a','b','p','mean','sigma','df','df2','trials','success','lambda','k'])$('distribution-'+name).closest('label').hidden=!shown.has(name);refreshWorkspaceMath();}
  $('distribution-family').onchange=distributionControls;$('distribution-query').onchange=distributionControls;
  $('distribution-insert').onclick=()=>{changeMode('scientific');replaceInput(distributionExpression(),{uncommit:true});};
  $('regression-kind').onchange=()=>{invalidateRegression();statisticsControls();$('regression-custom').hidden=regressionMode()!=='custom';$('regression-penalty-label').hidden=!['linear','multiple','logistic'].includes(value('regression-kind'));$('regression-lasso').hidden=!['ridge','lasso','elasticnet','logisticridge','logisticlasso','logisticelasticnet'].includes(regressionMode());$('regression-ratio-label').hidden=!regressionMode().endsWith('elasticnet');$('regression-forest').hidden=!regressionMode().startsWith('randomforest');$('regression-degree').closest('label').hidden=regressionMode()!=='polynomial';$('regression-data-help').hidden=!['multiple','logistic','polynomial','ridge','lasso','elasticnet','logisticridge','logisticlasso','logisticelasticnet','randomforest','randomforestclassifier','randomforestregressor'].includes(regressionMode());$('regression-response').closest('label').hidden=!['multiple','logistic','polynomial','ridge','lasso','elasticnet','logisticridge','logisticlasso','logisticelasticnet','randomforest','randomforestclassifier','randomforestregressor'].includes(regressionMode());setText($('regression-data-help'),regressionMode().startsWith('logistic')?'Selected column is response; others are predictors. Logistic response: 0 or 1.':'Selected column is response; others are predictors.');refreshWorkspaceMath();};
  $('regression-kind').onchange();
  for(const id of ['regression-alpha','regression-ratio','regression-trees','regression-depth','regression-seed'])$(id).addEventListener('input',()=>{invalidateRegression();refreshWorkspaceMath();});
  $('regression-forest-task').onchange=()=>{$('regression-kind').onchange();persist();};
  $('regression-penalty').onchange=()=>{$('regression-kind').onchange();persist();};
  $('regression-response').onchange=()=>{state.fields['regression-response-auto']=false;state.fields['regression-response-choice']=value('regression-response');state.fields['regression-response-position']=Number(value('regression-response'))===0?'first':'last';invalidateRegression();refreshWorkspaceMath();persist();};
  $('regression-custom').hidden=regressionMode()!=='custom';
  $('regression-clear').onclick=()=>{cancelRegression();statisticsGraph=null;$('regression-caption').replaceChildren();$('regression-inference').replaceChildren();$('regression-export').hidden=true;$('regression-transfer').hidden=true;if(!$('statistics-plot').hidden)$('statistics-plot-run').click();};
  $('regression-export').onclick=()=>downloadFile('regression-residuals.csv',regressionResidualCSV(statisticsGraph?.report),'text/csv');
  function statisticsControls(){
    const columns=dataColumns(),kind=dataKind(),pairOps=['correlation','ttestpaired','wilcoxon','chi2independence','fisherexact'],multiOps=['ttest2','ztest2','mannwhitney','anova','tukey','kruskal'];
    filterMenu('statistics-op',op=>columns!==1||op==='wilcoxon'||![...pairOps,...multiOps].includes(op),'stats');
    const op=value('statistics-op'),paired=pairOps.includes(op)&&!(op==='wilcoxon'&&columns===1),multi=['ttest2','ztest2','mannwhitney'].includes(op),all=['anova','tukey','kruskal'].includes(op);
    filterMenu('statistics-grouping',grouping=>grouping==='columns'||!paired&&kind==='xy','columns');
    const grouped=!paired&&kind==='xy'&&value('statistics-grouping')==='groups';
    let rows=[];try{rows=dataRows();}catch{}
    const names=paired?['x','y']:grouped?[...new Set(rows.filter(row=>row[0]&&row[1]).map(row=>row[0]))]:statisticsColumnNames(columns);
    const selectedColumn=value('statistics-column');$('statistics-column').replaceChildren(...statisticsColumnNames(columns).map((name,i)=>{const option=element('option',name);option.value=String(i);return option;}));$('statistics-column').value=Number(selectedColumn)<columns?selectedColumn:'0';
    for(const [id,fallback] of [['statistics-first-group',0],['statistics-second-group',1]]){
      const select=$(id),previous=select.value,choices=multi&&id==='statistics-second-group'?names.filter(name=>name!==value('statistics-first-group')):names;
      select.replaceChildren(...choices.map(name=>{const option=element('option',name);option.value=name;return option;}));
      select.value=paired?names[fallback]:choices.includes(previous)?previous:choices.includes(names[fallback])?names[fallback]:choices[0]||'';
    }
    $('statistics-first-group').closest('label').hidden=all||!paired&&!multi&&!grouped;
    $('statistics-second-group').closest('label').hidden=all||!paired&&!multi;
    $('statistics-grouping').disabled=paired||kind!=='xy';
    $('statistics-column').disabled=paired||multi||all||grouped||columns===1;
    $('statistics-first-group').disabled=paired||all||!multi&&!grouped||columns===1;
    $('statistics-second-group').disabled=!multi||columns===1;
    $('statistics-extra').disabled=!['ttest','ttest2','ttestpaired','ztest','ztest2','tinterval','zinterval'].includes(op);
    $('statistics-tail').disabled=!['ttest','ttest2','ttestpaired','wilcoxon','mannwhitney','ztest','ztest2','fisherexact'].includes(op);
    $('statistics-sigma').disabled=!['ztest','ztest2','zinterval'].includes(op);
    $('statistics-sigma-y').disabled=op!=='ztest2';
    $('statistics-yates-options').hidden=op!=='chi2independence';
    $('statistics-columns-label').hidden=!kind.startsWith('columns:');
    $('regression-section').hidden=columns<2;
    $('regression-response').value=automaticResponse()?String(columns-1):String(state.fields['regression-response-choice']);
    const positionResponse=['polynomial','logistic','logisticridge','logisticlasso','logisticelasticnet'].includes(regressionMode());
    if(positionResponse&&state.fields['regression-response-position']==='last')$('regression-response').value=String(columns-1);
    const responseOptions=statisticsColumnNames(columns).flatMap((name,i)=>{
      if(positionResponse&&i!==0&&i!==columns-1)return [];
      const label=positionResponse?(i===0?'first':'last'):name,option=element('option',label);option.value=String(i);return [{option,label}];
    });
    menus['regression-response']=responseOptions;
    filterMenu('regression-response',column=>Number(column)<columns,String(columns-1));
    if(kind!=='list'&&!automaticResponse())state.fields['regression-response-choice']=value('regression-response');
    if(filterMenu('regression-kind',model=>kind==='xyz'||kind.startsWith('columns:')?columns>1&&['multiple','logistic','randomforest'].includes(model):kind==='xy'&&model!=='multiple',kind==='xyz'||kind.startsWith('columns:')?'multiple':'linear'))$('regression-kind').onchange();
    $('regression-data-help').hidden=!['multiple','logistic','polynomial','ridge','lasso','elasticnet','logisticridge','logisticlasso','logisticelasticnet','randomforest','randomforestclassifier','randomforestregressor'].includes(regressionMode());
    $('regression-response').closest('label').hidden=!['multiple','logistic','polynomial','ridge','lasso','elasticnet','logisticridge','logisticlasso','logisticelasticnet','randomforest','randomforestclassifier','randomforestregressor'].includes(regressionMode());
    filterMenu('statistics-plot-type',plot=>plot!=='scatter'||kind==='xy',kind==='xy'?'scatter':'histogram');
    $('statistics-plot-grouping-label').hidden=value('statistics-plot-type')==='scatter'||columns<2;
    setText($('statistics-data-label'),kind==='list'?'One value per line':kind==='xy'?'x, y values':kind==='xyz'?'x, y, z values':statisticsColumnNames(columns).join(', '));
    try{$('statistics-samples').textContent=analysisSummary();}catch{setText($('statistics-samples'),'Enter data to see analyzed groups');}
  }
  function dataKindChange(){cancelRegression();statisticsGraph=null;$('regression-caption').replaceChildren();$('regression-inference').replaceChildren();$('regression-export').hidden=true;$('regression-transfer').hidden=true;$('statistics-plot').replaceChildren();$('statistics-plot-type').value=dataKind()==='xy'?'scatter':'histogram';render();refreshWorkspaceMath();}
  $('statistics-kind').onchange=dataKindChange;
  $('statistics-columns').onchange=()=>{
    const count=Number(value('statistics-columns'));
    if(!Number.isInteger(count)||count<1||count>100){$('statistics-columns').value=String(state.fields['statistics-columns']||4);return;}
    dataKindChange();persist();
  };
  $('statistics-op').onchange=()=>{if(['tinterval','zinterval'].includes(value('statistics-op'))&&value('statistics-extra')==='0')$('statistics-extra').value='95';statisticsControls();refreshWorkspaceMath();};
  $('statistics-grouping').onchange=()=>{statisticsControls();refreshWorkspaceMath();};
  for(const id of ['statistics-column','statistics-first-group','statistics-second-group'])$(id).onchange=()=>{statisticsControls();refreshWorkspaceMath();};
  $('statistics-yates').onchange=()=>{refreshWorkspaceMath();persist();};
  function editorRows(){return value('statistics-data').trim()?csvRows(value('statistics-data'),{preserveEmptyRows:true}):[];}
  // Quote an empty List cell so a blank row survives serialization and reload.
  function writeRows(rows){invalidateRegression();$('statistics-data').value=rows.map(row=>row.length===1&&!row[0]?'""':row.map(cell=>/[",\r\n\t]/.test(cell)?'"'+cell.replace(/"/g,'""')+'"':cell).join(',')).join('\n');statisticsControls();refreshWorkspaceMath();persist();}
  function statisticsGrid(){
    const rows=editorRows(),columns=dataColumns();
    const table=editableTable({rows:rows.length,columns:statisticsColumnNames(columns),label:t('Stats data'),value:(row,col)=>rows[row][col]||'',
      onInput:(row,col,cell)=>{while(rows[row].length<columns)rows[row].push('');rows[row][col]=cell;writeRows(rows);},
      onDeleteRow:row=>{rows.splice(row,1);writeRows(rows);statisticsGrid();}});
    $('statistics-grid').replaceChildren(table);
  }
  $('statistics-table-toggle').onclick=()=>{
    const grid=$('statistics-grid'),tableMode=grid.hidden;
    if(tableMode)try{statisticsGrid();}catch(exc){error(exc.message);return;}
    grid.hidden=!tableMode;$('statistics-data').closest('label').hidden=tableMode;
    setText($('statistics-table-toggle'),tableMode?'Direct input':'Table editor');
    $('statistics-table-toggle').setAttribute('aria-pressed',String(tableMode));
  };
  $('statistics-add-row').onclick=()=>{try{const rows=editorRows(),columns=Math.max(dataColumns(),rows[0]?.length||0);rows.push(Array(columns).fill(''));writeRows(rows);if(!$('statistics-grid').hidden)statisticsGrid();}catch(exc){error(exc.message);}};
  $('statistics-data').addEventListener('input',()=>{invalidateRegression();statisticsControls();});
  $('statistics-data').addEventListener('change',()=>{invalidateRegression();statisticsControls();if(!$('statistics-grid').hidden)statisticsGrid();});
  $('statistics-store').onclick=async()=>{const name=value('dataset-name').trim();if(!/^[A-Za-z][A-Za-z0-9_]*$/.test(name)){error('Dataset name must be a valid variable name');return;}try{await storeExpression(name,statisticsDatasetSource(value('statistics-data'),dataKind()));}catch(exc){error(exc.message);}};
  $('statistics-plot-run').onclick=()=>{try{statisticsGraph={...statisticsGraph,rows:numericStatisticsRows(dataRows()),curve:statisticsGraph?.curve||[]};$('statistics-plot').hidden=false;drawStatisticsGraph();}catch(exc){error(exc.message);}};
  $('statistics-plot-type').onchange=()=>{statisticsControls();$('statistics-plot-run').click();};
  $('statistics-plot-grouping').onchange=()=>{$('statistics-plot-run').click();persist();};
  $('regression-transfer').onclick=()=>{if(!statisticsGraph?.fit)return;try{const source=regressionGraphSource(statisticsGraph.fit,state.digits,statisticsGraph.variable);$('graph-kind').value='cartesian';$('graph-source').value=source;changeMode('graph');graphs.run();}catch(exc){error(exc.message);}};

  function drawStatisticsGraph(){
    const container=$('statistics-plot'),type=value('statistics-plot-type'),options={type,digits:state.digits,curve:statisticsGraph.curve,xAxisLabel:statisticsGraph.xAxisLabel||'x',yAxisLabel:statisticsGraph.yAxisLabel||'y'};
    if(type==='scatter'){statisticsPlot(container,statisticsGraph.plotRows||statisticsGraph.rows,options);return;}
    const panels=statisticsPlotPanels(dataRows(),{grouping:value('statistics-plot-grouping'),columnCount:dataColumns()});
    if(panels.length===1&&!panels[0].label){statisticsPlot(container,statisticsGraph.rows,{...options,series:panels[0].series});return;}
    container.replaceChildren(...panels.map(panel=>{
      const section=element('section','','statistics-plot-panel'),chart=element('div');
      section.dataset.column=panel.label;section.append(element('h3',panel.label),chart);
      statisticsPlot(chart,statisticsGraph.rows,{...options,series:panel.series});return section;
    }));
  }
  function render(){statisticsControls();if(!$('statistics-grid').hidden)statisticsGrid();if(statisticsGraph){drawStatisticsGraph();if(statisticsGraph.captions)renderFormulas($('regression-caption'),statisticsGraph.captions,{digits:state.digits});for(const [name,n] of statisticsGraph.parameters||[])$('regression-caption').append(element('span',`${regressionParameterName(name,statisticsGraph.parameterLabels)} = ${roundNumber(String(n),state.digits)}`,'regression-parameter'));renderRegressionReport($('regression-inference'),statisticsGraph.report,state.digits,statisticsGraph.parameterLabels);}}
  function showRegression(result) {
    const rows=numericStatisticsRows(dataRows()),names=statisticsColumnNames(dataColumns()),logistic=regressionMode().startsWith('logistic');
    const response=['multiple','logistic','polynomial','ridge','lasso','elasticnet','logisticridge','logisticlasso','logisticelasticnet','randomforest','randomforestclassifier','randomforestregressor'].includes(regressionMode())?Number(value('regression-response')):names.length-1,predictors=names.filter((_,i)=>i!==response);
    const parameterLabels=regressionParameterLabels(regressionMode(),statisticsColumnLabels(value('statistics-data'),dataKind()),response);
    const variables=Object.fromEntries(predictors.map((name,i)=>[names.length===2?'x':`x${i+1}`,name]));
    let displayed=result.decimal||result.exact;
    if(logistic||['polynomial','ridge','lasso','elasticnet'].includes(regressionMode())||names.length>2){
      const renamed=node=>({...node,value:node.kind==='symbol'?(variables[node.value]||node.value):node.value,args:node.args.map(renamed)});
      try{displayed=astSource(renamed(parse(latexInput(displayed))));}catch{}
    }

    statisticsGraph={rows,plotRows:['polynomial','logistic','ridge','lasso','elasticnet','logisticridge','logisticlasso','logisticelasticnet','randomforest','randomforestclassifier','randomforestregressor'].includes(regressionMode())&&names.length===2?rows.map(row=>[row[1-response],row[response]]):rows,xAxisLabel:predictors[0],yAxisLabel:names[response],curve:result.curve||[],fit:result.decimal||result.exact,report:result.regression,parameterLabels,parameters:(result.parameters||[]).filter(([name])=>parameterLabels[name]),variable:regressionMode()==='custom'?value('regression-variable'):'x',
      captions:regressionMode().startsWith('randomforest')?[]:[`${logistic?`P(${names[response]}=1)`:names[response]}=${displayed}`,...(result.correlation!==null&&result.correlation!==undefined?[`r=${result.correlation}`]:[]),...(result.parameters||[]).filter(([name])=>!parameterLabels[name]).map(([name,n])=>`${name}=${n}`)]};
    $('statistics-plot').hidden=false;render();
    $('regression-transfer').hidden=dataColumns()!==2||regressionMode().startsWith('randomforest');
    $('regression-export').hidden=!result.regression;
  }
  async function runRegression(options,showResult){
    if(regressionRun||!engine.ready)return;
    const run={};
    regressionRun=run;
    try{
      const source=statisticsExpression('regression'),tree=parse(latexInput(source));
      regressionBusy(true);
      const result=await engine.execute({...options,tree});
      if(regressionRun!==run||source!==statisticsExpression('regression'))return;
      showResult(result,source,source,{decimalDisplay:true});
      if(result.ok)showRegression(result);
    }catch(exc){if(regressionRun===run)error(exc.message);}
    finally{if(regressionRun===run){regressionRun=null;regressionBusy(false);}}
  }
  statisticsControls();
  return {datasetsList,expression:statisticsExpression,analysisSummary,distributionExpression,distributionControls,render,showRegression,runRegression};
}
