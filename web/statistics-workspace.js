import {$,value,element,control} from './app-ui.js';
import {t,setText} from './i18n.js';
import {downloadFile} from './storage.js';
import {statisticsCommand,distributionCommand,csvRows,numericStatisticsRows} from './workspace-commands.js';
import {statisticsPlot} from './statistics-plot.js';
import {renderFormulas} from './formula-preview.js';
import {editableTable} from './editable-table.js';

export function createStatisticsWorkspace({state,ui,persist,refreshWorkspaceMath,storeExpression,error,changeMode,replaceInput,graphs}) {
  const {toast,pickFile,openDialog}=ui;
  let statisticsGraph=null;
  function datasetsList(){const previous=value('dataset-list');$('dataset-list').replaceChildren(element('option','새 데이터'));$('dataset-list').firstChild.value='';for(const name of Object.keys(state.datasets)) {const option=element('option',name);option.value=name;$('dataset-list').append(option);}if(state.datasets[previous])$('dataset-list').value=previous;}
  $('dataset-list').onchange=()=>{const name=value('dataset-list');if(state.datasets[name]){$('statistics-data').value=state.datasets[name];$('dataset-name').value=name;render();refreshWorkspaceMath();persist();}};
  $('dataset-save').onclick=()=>{const name=value('dataset-name').trim();if(!name){toast('데이터 이름을 입력하세요.');return;}state.datasets[name]=value('statistics-data');datasetsList();$('dataset-list').value=name;persist();toast('데이터를 저장했습니다.');};
  $('dataset-delete').onclick=()=>{delete state.datasets[value('dataset-list')];datasetsList();persist();};
  $('csv-open').onclick=()=>pickFile('.csv,.tsv,text/csv',async file=>{const rows=csvRows((await file.text()).replace(/^\uFEFF/,''),{maxColumns:100,skipHeader:false}),content=element('div'),columns=[],header=element('input');header.type='checkbox';header.checked=rows[0].every(cell=>cell!==''&&!Number.isFinite(Number(cell)))&&rows.slice(1).some(row=>row.some(cell=>cell!==''&&Number.isFinite(Number(cell))));const headerLabel=element('label','Skip header row','check');headerLabel.append(header);content.append(headerLabel);for(let index=0;index<rows[0].length;index++){const input=element('input');input.type='checkbox';input.checked=index<3;columns.push(input);const label=element('label',`${t('Column')} ${index+1}: ${rows[0][index]}`,'check');label.append(input);content.append(label);}content.append(control('Import CSV',()=>{const selected=columns.map((input,i)=>input.checked?i:null).filter(i=>i!==null);if(!selected.length||selected.length>3){toast('Select one to three columns');return;}$('statistics-data').value=rows.slice(header.checked?1:0).map(row=>selected.map(i=>row[i].includes(',')?'"'+row[i].replace(/"/g,'""')+'"':row[i]).join(',')).join('\n');$('dataset-name').value=file.name.replace(/\.(csv|tsv)$/i,'');render();refreshWorkspaceMath();persist();$('dialog').close();}));openDialog('Import CSV',content);});
  $('csv-save').onclick=()=>downloadFile(`${value('dataset-name')||'calcmax-data'}.csv`,value('statistics-data'),'text/csv');
  function dataRows(){return csvRows(value('statistics-data'));}
  function statisticsExpression(op=value('statistics-op')){return statisticsCommand(value('statistics-data'),{op,column:Number(value('statistics-column')),extra:value('statistics-extra')||'0',tail:value('statistics-tail'),sigma:value('statistics-sigma'),sigmaY:value('statistics-sigma-y'),regression:value('regression-kind'),formula:value('regression-formula'),variable:value('regression-variable'),initials:value('regression-initials'),grouping:value('statistics-grouping'),firstGroup:value('statistics-first-group'),secondGroup:value('statistics-second-group')});}
  function distributionExpression(){return distributionCommand(Object.fromEntries(['family','query','x','a','b','p','mean','sigma','df','df2','trials','success','lambda','k'].map(name=>[name,value('distribution-'+name)])));}
  function distributionControls(){const family=value('distribution-family'),discrete=['binomial','poisson','geometric'].includes(family),queries=discrete?(family==='binomial'?['pdf','cdf','list-pdf','list-cdf']:['pdf','cdf']):['normal','t'].includes(family)?['pdf','cdf','interval','quantile']:['pdf','cdf','interval'];for(const option of $('distribution-query').options)option.disabled=!queries.includes(option.value);if(!queries.includes(value('distribution-query')))$('distribution-query').value='cdf';const query=value('distribution-query'),shown=new Set(discrete?(query.startsWith('list')?[]:['k']):query==='interval'?['a','b']:query==='quantile'?['p']:['x']);for(const name of family==='normal'?['mean','sigma']:family==='f'?['df','df2']:['t','chi2'].includes(family)?['df']:family==='binomial'?['trials','success']:family==='poisson'?['lambda']:['success'])shown.add(name);for(const name of ['x','a','b','p','mean','sigma','df','df2','trials','success','lambda','k'])$('distribution-'+name).closest('label').hidden=!shown.has(name);refreshWorkspaceMath();}
  $('distribution-family').onchange=distributionControls;$('distribution-query').onchange=distributionControls;
  $('distribution-insert').onclick=()=>{changeMode('scientific');replaceInput(distributionExpression(),{uncommit:true});};
  $('regression-kind').onchange=()=>{$('regression-custom').hidden=value('regression-kind')!=='custom';refreshWorkspaceMath();};
  $('regression-custom').hidden=value('regression-kind')!=='custom';
  $('regression-clear').onclick=()=>{statisticsGraph=null;$('regression-caption').replaceChildren();$('regression-transfer').hidden=true;if(!$('statistics-plot').hidden)$('statistics-plot-run').click();};
  function statisticsControls(){
    const op=value('statistics-op'),paired=['correlation','ttestpaired','chi2independence','fisherexact'].includes(op),multi=['ttest2','ztest2'].includes(op),all=['anova','tukey'].includes(op),grouped=value('statistics-grouping')==='groups';
    let rows=[];try{rows=dataRows();}catch{}
    const names=grouped?[...new Set(rows.filter(row=>row[0]&&row[1]).map(row=>row[0]))]:['x','y','z'];
    for(const [id,fallback] of [['statistics-first-group',0],['statistics-second-group',1]]){
      const select=$(id),previous=select.value;select.replaceChildren(...names.map(name=>{const option=element('option',name);option.value=name;return option;}));
      select.value=names.includes(previous)?previous:names[fallback]||names[0]||'';
    }
    $('statistics-grouping').disabled=paired;
    $('statistics-column').disabled=paired||multi||all||grouped;
    $('statistics-first-group').disabled=paired||all||!multi&&!grouped;
    $('statistics-second-group').disabled=!multi;
    $('statistics-extra').disabled=!['ttest','ttest2','ttestpaired','ztest','ztest2','tinterval','zinterval'].includes(op);
    $('statistics-tail').disabled=!['ttest','ttest2','ttestpaired','ztest','ztest2','fisherexact'].includes(op);
    $('statistics-sigma').disabled=!['ztest','ztest2','zinterval'].includes(op);
    $('statistics-sigma-y').disabled=op!=='ztest2';
  }
  $('statistics-op').onchange=()=>{if(['tinterval','zinterval'].includes(value('statistics-op'))&&value('statistics-extra')==='0')$('statistics-extra').value='95';statisticsControls();refreshWorkspaceMath();};
  $('statistics-grouping').onchange=()=>{statisticsControls();refreshWorkspaceMath();};
  function writeRows(rows){$('statistics-data').value=rows.map(row=>row.map(cell=>/[",\r\n]/.test(cell)?'"'+cell.replace(/"/g,'""')+'"':cell).join(',')).join('\n');statisticsControls();refreshWorkspaceMath();persist();}
  function statisticsGrid(){
    const rows=value('statistics-data').trim()?dataRows():[],columns=rows[0]?.length||3;
    const table=editableTable({rows:rows.length,columns:['x','y','z'].slice(0,columns),label:t('Stats data'),value:(row,col)=>rows[row][col],
      onInput:(row,col,cell)=>{rows[row][col]=cell;writeRows(rows);},
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
  $('statistics-add-row').onclick=()=>{try{const rows=value('statistics-data').trim()?dataRows():[],columns=rows[0]?.length||3;rows.push(Array(columns).fill('0'));writeRows(rows);if(!$('statistics-grid').hidden)statisticsGrid();}catch(exc){error(exc.message);}};
  $('statistics-data').addEventListener('input',statisticsControls);
  $('statistics-data').addEventListener('change',()=>{statisticsControls();if(!$('statistics-grid').hidden)statisticsGrid();});
  $('statistics-store').onclick=async()=>{const name=value('dataset-name').trim();if(!/^[A-Za-z][A-Za-z0-9_]*$/.test(name)){error('Dataset name must be a valid variable name');return;}await storeExpression(name,`[${dataRows().map(row=>row.length===1?row[0]:'['+row.join(',')+']').join(',')}]`);};
  $('statistics-plot-run').onclick=()=>{try{statisticsGraph={rows:numericStatisticsRows(dataRows()),curve:statisticsGraph?.curve||[]};$('statistics-plot').hidden=false;statisticsPlot($('statistics-plot'),statisticsGraph.rows,{type:value('statistics-plot-type'),digits:state.digits,curve:statisticsGraph.curve});}catch(exc){error(exc.message);}};
  $('statistics-plot-type').onchange=()=>$('statistics-plot-run').click();
  $('regression-transfer').onclick=()=>{if(!statisticsGraph?.fit)return;const variable=value('regression-kind')==='custom'?value('regression-variable'):'x';$('graph-kind').value='cartesian';$('graph-source').value=statisticsGraph.fit.replaceAll('**','^').replace(new RegExp(`\\b${variable}\\b`,'g'),'x');changeMode('graph');graphs.run();};

  function render(){statisticsControls();if(!$('statistics-grid').hidden)statisticsGrid();if(statisticsGraph)statisticsPlot($('statistics-plot'),statisticsGraph.rows,{type:value('statistics-plot-type'),digits:state.digits,curve:statisticsGraph.curve});}
  function showRegression(result) {
    statisticsGraph={rows:numericStatisticsRows(dataRows()),curve:result.curve||[],fit:result.exact};
    $('statistics-plot').hidden=false;render();
    renderFormulas($('regression-caption'),[`y=${result.exact}`,...(result.correlation!==null&&result.correlation!==undefined?[`r=${result.correlation}`]:[]),...(result.parameters||[]).map(([name,n])=>`${name}=${n}`)],{digits:state.digits});
    $('regression-transfer').hidden=false;
  }
  statisticsControls();
  return {datasetsList,expression:statisticsExpression,distributionExpression,distributionControls,render,showRegression};
}
