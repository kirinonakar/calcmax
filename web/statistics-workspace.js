import {$,value,element,control} from './app-ui.js';
import {t,setText} from './i18n.js';
import {downloadFile} from './storage.js';
import {statisticsCommand,statisticsAnalysisData,distributionCommand,csvRows,statisticsDataRows,statisticsDatasetSource,numericStatisticsRows} from './workspace-commands.js';
import {statisticsPlot} from './statistics-plot.js';
import {renderFormulas} from './formula-preview.js';
import {editableTable} from './editable-table.js';
import {parse,latexInput} from './parser.js';
import {astSource} from './ast-source.js';
import {roundNumber} from './display-format.js';

export function regressionGraphSource(source,digits=10,variable='x') {
  function rounded(node){return {...node,value:node.kind==='number'?roundNumber(node.value,digits):node.kind==='symbol'&&node.value===variable?'x':node.value,args:node.args.map(rounded)};}
  return astSource(rounded(parse(latexInput(source))));
}

export function createStatisticsWorkspace({state,engine,ui,persist,refreshWorkspaceMath,storeExpression,error,changeMode,replaceInput,graphs}) {
  const {toast,pickFile,openDialog}=ui;
  let statisticsGraph=null;
  let regressionRun=null;
  function regressionBusy(busy){$('regression-progress').hidden=!busy;$('regression-cancel').hidden=!busy;$('regression-section').setAttribute('aria-busy',String(busy));}
  function cancelRegression(){if(!regressionRun)return;regressionRun=null;regressionBusy(false);engine.cancel();}
  $('regression-cancel').onclick=cancelRegression;
  $('statistics-new').onclick=()=>{
    cancelRegression();$('statistics-data').value='';$('dataset-name').value='';$('dataset-list').value='';
    dataKindChange();$('statistics-plot').hidden=true;persist();
  };
  const dataColumns=()=>({list:1,xy:2,xyz:3}[value('statistics-kind')]);
  function inferDataKind(){try{return ['list','xy','xyz'][csvRows(value('statistics-data'))[0].length-1];}catch{return 'list';}}
  if(!['list','xy','xyz'].includes(state.fields['statistics-kind']))$('statistics-kind').value=inferDataKind();
  function datasetsList(){const previous=value('dataset-list');$('dataset-list').replaceChildren(element('option','새 데이터'));$('dataset-list').firstChild.value='';for(const name of Object.keys(state.datasets)) {const option=element('option',name);option.value=name;$('dataset-list').append(option);}if(Object.hasOwn(state.datasets,previous))$('dataset-list').value=previous;}
  $('dataset-list').onchange=()=>{const name=value('dataset-list');if(Object.hasOwn(state.datasets,name)){$('statistics-data').value=state.datasets[name];$('dataset-name').value=name;const savedKind=state.datasetKinds[name];$('statistics-kind').value=['list','xy','xyz'].includes(savedKind)?savedKind:inferDataKind();dataKindChange();persist();}};
  $('dataset-save').onclick=()=>{const name=value('dataset-name').trim();if(!name){toast('데이터 이름을 입력하세요.');return;}state.datasets[name]=value('statistics-data');state.datasetKinds[name]=value('statistics-kind');datasetsList();$('dataset-list').value=name;persist();toast('데이터를 저장했습니다.');};
  $('dataset-delete').onclick=()=>{delete state.datasetKinds[value('dataset-list')];delete state.datasets[value('dataset-list')];datasetsList();persist();};
  $('csv-open').onclick=()=>pickFile('.csv,.tsv,text/csv',async file=>{
    const rows=csvRows((await file.text()).replace(/^\uFEFF/,''),{maxColumns:100,skipHeader:false}),content=element('div'),columns=[],header=element('input'),preview=element('pre','','csv-preview');
    const selectedColumns=()=>columns.map((input,i)=>input.checked?i:null).filter(i=>i!==null);
    function updatePreview(){const selected=selectedColumns();preview.textContent=selected.length?rows.slice(header.checked?1:0).slice(0,3).map(row=>selected.map(i=>row[i]).join('  |  ')).join('\n'):'';}
    preview.setAttribute('aria-live','polite');
    header.type='checkbox';header.checked=rows[0].every(cell=>cell!==''&&!Number.isFinite(Number(cell)))&&rows.slice(1).some(row=>row.some(cell=>cell!==''&&Number.isFinite(Number(cell))));
    header.onchange=updatePreview;
    const headerLabel=element('label','Skip header row','check');headerLabel.append(header);content.append(headerLabel);
    for(let index=0;index<rows[0].length;index++){
      const input=element('input');input.type='checkbox';input.checked=index<3;input.onchange=updatePreview;columns.push(input);
      const label=element('label',`${t('Column')} ${index+1}: ${rows[0][index]}`,'check');label.append(input);content.append(label);
    }
    content.append(element('h3','Preview'),preview);updatePreview();
    content.append(control('Import CSV',()=>{
      const selected=selectedColumns();if(!selected.length||selected.length>3){toast('Select one to three columns');return;}
      $('statistics-data').value=rows.slice(header.checked?1:0).map(row=>selected.map(i=>row[i].includes(',')?'"'+row[i].replace(/"/g,'""')+'"':row[i]).join(',')).join('\n');
      $('dataset-name').value=file.name.replace(/\.(csv|tsv)$/i,'');$('statistics-kind').value=['list','xy','xyz'][selected.length-1];dataKindChange();persist();$('dialog').close();
    }));openDialog('Import CSV',content);
  });
  $('csv-save').onclick=()=>downloadFile(`${value('dataset-name')||'calcmax-data'}.csv`,value('statistics-data'),'text/csv');
  function dataRows(){return statisticsDataRows(value('statistics-data'),value('statistics-kind'));}
  function statisticsExpression(op=value('statistics-op')){return statisticsCommand(value('statistics-data'),{op,kind:value('statistics-kind'),column:Number(value('statistics-column')),extra:value('statistics-extra')||'0',tail:value('statistics-tail'),sigma:value('statistics-sigma'),sigmaY:value('statistics-sigma-y'),regression:value('regression-kind'),formula:value('regression-formula'),variable:value('regression-variable'),initials:value('regression-initials'),grouping:value('statistics-grouping'),firstGroup:value('statistics-first-group'),secondGroup:value('statistics-second-group')});}
  function analysisSummary(){
    const plan=statisticsAnalysisData(value('statistics-data'),{op:value('statistics-op'),kind:value('statistics-kind'),column:Number(value('statistics-column')),grouping:value('statistics-grouping'),firstGroup:value('statistics-first-group'),secondGroup:value('statistics-second-group')});
    if(plan.paired)return `${t('Compared columns')}: x ↔ y · ${t('Complete pairs')}: ${plan.pairs.length}`;
    return `${t('Analyzed groups')} (${plan.samples.length}): ${plan.samples.map(sample=>`${sample.label} (n=${sample.values?.length||0})`).join(' · ')}`;
  }
  function distributionExpression(){return distributionCommand(Object.fromEntries(['family','query','x','a','b','p','mean','sigma','df','df2','trials','success','lambda','k'].map(name=>[name,value('distribution-'+name)])));}
  function distributionControls(){const family=value('distribution-family'),discrete=['binomial','poisson','geometric'].includes(family),queries=discrete?(family==='binomial'?['pdf','cdf','list-pdf','list-cdf']:['pdf','cdf']):['normal','t'].includes(family)?['pdf','cdf','interval','quantile']:['pdf','cdf','interval'];for(const option of $('distribution-query').options)option.disabled=!queries.includes(option.value);if(!queries.includes(value('distribution-query')))$('distribution-query').value='cdf';const query=value('distribution-query'),shown=new Set(discrete?(query.startsWith('list')?[]:['k']):query==='interval'?['a','b']:query==='quantile'?['p']:['x']);for(const name of family==='normal'?['mean','sigma']:family==='f'?['df','df2']:['t','chi2'].includes(family)?['df']:family==='binomial'?['trials','success']:family==='poisson'?['lambda']:['success'])shown.add(name);for(const name of ['x','a','b','p','mean','sigma','df','df2','trials','success','lambda','k'])$('distribution-'+name).closest('label').hidden=!shown.has(name);refreshWorkspaceMath();}
  $('distribution-family').onchange=distributionControls;$('distribution-query').onchange=distributionControls;
  $('distribution-insert').onclick=()=>{changeMode('scientific');replaceInput(distributionExpression(),{uncommit:true});};
  $('regression-kind').onchange=()=>{$('regression-custom').hidden=value('regression-kind')!=='custom';refreshWorkspaceMath();};
  $('regression-custom').hidden=value('regression-kind')!=='custom';
  $('regression-clear').onclick=()=>{cancelRegression();statisticsGraph=null;$('regression-caption').replaceChildren();$('regression-transfer').hidden=true;if(!$('statistics-plot').hidden)$('statistics-plot-run').click();};
  function statisticsControls(){
    const columns=dataColumns(),kind=value('statistics-kind'),pairOps=['correlation','ttestpaired','chi2independence','fisherexact'],multiOps=['ttest2','ztest2','anova','tukey'];
    for(const option of $('statistics-op').options)option.disabled=columns===1&&[...pairOps,...multiOps].includes(option.value);
    if($('statistics-op').selectedOptions[0]?.disabled)$('statistics-op').value='stats';
    const op=value('statistics-op'),paired=pairOps.includes(op),multi=['ttest2','ztest2'].includes(op),all=['anova','tukey'].includes(op),grouped=!paired&&kind==='xy'&&value('statistics-grouping')==='groups';
    let rows=[];try{rows=dataRows();}catch{}
    const names=paired?['x','y']:grouped?[...new Set(rows.filter(row=>row[0]&&row[1]).map(row=>row[0]))]:['x','y','z'].slice(0,columns);
    const selectedColumn=value('statistics-column');$('statistics-column').replaceChildren(...['x','y','z'].slice(0,columns).map((name,i)=>{const option=element('option',name);option.value=String(i);return option;}));$('statistics-column').value=Number(selectedColumn)<columns?selectedColumn:'0';
    for(const [id,fallback] of [['statistics-first-group',0],['statistics-second-group',1]]){
      const select=$(id),previous=select.value;select.replaceChildren(...names.map(name=>{const option=element('option',name);option.value=name;return option;}));
      select.value=paired?names[fallback]:names.includes(previous)?previous:names[fallback]||names[0]||'';
    }
    if(multi){const second=$('statistics-second-group'),first=value('statistics-first-group');if(second.value===first)second.value=names.find(name=>name!==first)||'';for(const option of second.options)option.disabled=option.value===first;}
    $('statistics-first-group').closest('label').hidden=all||!paired&&!multi&&!grouped;
    $('statistics-second-group').closest('label').hidden=all||!paired&&!multi;
    $('statistics-grouping').disabled=paired||kind!=='xy';
    $('statistics-column').disabled=paired||multi||all||grouped||columns===1;
    $('statistics-first-group').disabled=paired||all||!multi&&!grouped||columns===1;
    $('statistics-second-group').disabled=!multi||columns===1;
    $('statistics-extra').disabled=!['ttest','ttest2','ttestpaired','ztest','ztest2','tinterval','zinterval'].includes(op);
    $('statistics-tail').disabled=!['ttest','ttest2','ttestpaired','ztest','ztest2','fisherexact'].includes(op);
    $('statistics-sigma').disabled=!['ztest','ztest2','zinterval'].includes(op);
    $('statistics-sigma-y').disabled=op!=='ztest2';
    $('regression-section').hidden=kind!=='xy';
    $('statistics-plot-type').querySelector('[value="scatter"]').disabled=kind!=='xy';
    if(kind!=='xy'&&value('statistics-plot-type')==='scatter')$('statistics-plot-type').value='histogram';
    setText($('statistics-data-label'),kind==='list'?'One value per line':kind==='xy'?'x, y values':'x, y, z values');
    try{$('statistics-samples').textContent=analysisSummary();}catch{setText($('statistics-samples'),'Enter data to see analyzed groups');}
  }
  function dataKindChange(){cancelRegression();statisticsGraph=null;$('regression-caption').replaceChildren();$('regression-transfer').hidden=true;$('statistics-plot').replaceChildren();$('statistics-plot-type').value=value('statistics-kind')==='xy'?'scatter':'histogram';render();refreshWorkspaceMath();}
  $('statistics-kind').onchange=dataKindChange;
  $('statistics-op').onchange=()=>{if(['tinterval','zinterval'].includes(value('statistics-op'))&&value('statistics-extra')==='0')$('statistics-extra').value='95';statisticsControls();refreshWorkspaceMath();};
  $('statistics-grouping').onchange=()=>{statisticsControls();refreshWorkspaceMath();};
  for(const id of ['statistics-column','statistics-first-group','statistics-second-group'])$(id).onchange=()=>{statisticsControls();refreshWorkspaceMath();};
  function writeRows(rows){$('statistics-data').value=rows.map(row=>row.map(cell=>/[",\r\n]/.test(cell)?'"'+cell.replace(/"/g,'""')+'"':cell).join(',')).join('\n');statisticsControls();refreshWorkspaceMath();persist();}
  function statisticsGrid(){
    const rows=value('statistics-data').trim()?csvRows(value('statistics-data')):[],columns=dataColumns();
    const table=editableTable({rows:rows.length,columns:['x','y','z'].slice(0,columns),label:t('Stats data'),value:(row,col)=>rows[row][col]||'',
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
  $('statistics-add-row').onclick=()=>{try{const rows=value('statistics-data').trim()?csvRows(value('statistics-data')):[],columns=Math.max(dataColumns(),rows[0]?.length||0);rows.push(Array.from({length:columns},(_,i)=>i<dataColumns()?'0':''));writeRows(rows);if(!$('statistics-grid').hidden)statisticsGrid();}catch(exc){error(exc.message);}};
  $('statistics-data').addEventListener('input',statisticsControls);
  $('statistics-data').addEventListener('change',()=>{statisticsControls();if(!$('statistics-grid').hidden)statisticsGrid();});
  $('statistics-store').onclick=async()=>{const name=value('dataset-name').trim();if(!/^[A-Za-z][A-Za-z0-9_]*$/.test(name)){error('Dataset name must be a valid variable name');return;}try{await storeExpression(name,statisticsDatasetSource(value('statistics-data'),value('statistics-kind')));}catch(exc){error(exc.message);}};
  $('statistics-plot-run').onclick=()=>{try{statisticsGraph={...statisticsGraph,rows:numericStatisticsRows(dataRows()),curve:statisticsGraph?.curve||[]};$('statistics-plot').hidden=false;statisticsPlot($('statistics-plot'),statisticsGraph.rows,{type:value('statistics-plot-type'),digits:state.digits,curve:statisticsGraph.curve});}catch(exc){error(exc.message);}};
  $('statistics-plot-type').onchange=()=>$('statistics-plot-run').click();
  $('regression-transfer').onclick=()=>{if(!statisticsGraph?.fit)return;try{const source=regressionGraphSource(statisticsGraph.fit,state.digits,statisticsGraph.variable);$('graph-kind').value='cartesian';$('graph-source').value=source;changeMode('graph');graphs.run();}catch(exc){error(exc.message);}};

  function render(){statisticsControls();if(!$('statistics-grid').hidden)statisticsGrid();if(statisticsGraph){statisticsPlot($('statistics-plot'),statisticsGraph.rows,{type:value('statistics-plot-type'),digits:state.digits,curve:statisticsGraph.curve});if(statisticsGraph.captions)renderFormulas($('regression-caption'),statisticsGraph.captions,{digits:state.digits});}}
  function showRegression(result) {
    statisticsGraph={rows:numericStatisticsRows(dataRows()),curve:result.curve||[],fit:result.decimal||result.exact,variable:value('regression-kind')==='custom'?value('regression-variable'):'x',
      captions:[`y=${result.decimal||result.exact}`,...(result.correlation!==null&&result.correlation!==undefined?[`r=${result.correlation}`]:[]),...(result.parameters||[]).map(([name,n])=>`${name}=${n}`)]};
    $('statistics-plot').hidden=false;render();
    $('regression-transfer').hidden=false;
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
