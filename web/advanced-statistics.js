import {computationLimitsRemoved} from './computation-limits.js';
import {advancedStatisticsSchema} from './advanced-statistics-schema.js';
import {csvRows,statisticsCsvHasHeader,statisticsColumnLabels,statisticsCategoryLabels} from './workspace-commands.js';
import {$,element} from './app-ui.js';
import {getLanguage,t} from './i18n.js';
import {renderSurvivalReport} from './survival-report.js';
import {statisticsResultMarkdown} from './statistics-markdown.js';

export function interactionPairs(value,predictors=[],columnLabels=[]){
  const text=String(value??'').trim();
  if(!text)return [];
  const names=(columnLabels||[]).map(label=>String(label??'').trim().toLowerCase());
  const aliasColumn=token=>{
    if(['x','y','z'].includes(token))return ['x','y','z'].indexOf(token);
    const numbered=/^x(\d+)$/.exec(token);
    return numbered?Number(numbered[1])-1:null;
  };
  const splitLabel=label=>{
    const match=/^(.+?)\s*\(\s*([^()]*?)\s*\)$/.exec(label);
    return match?{name:match[1].trim(),alias:aliasColumn(match[2].trim())}:{name:label,alias:null};
  };
  const headers=names.map(label=>{const parts=splitLabel(label);return parts.alias===null?label:parts.name;});
  const positionOf=column=>{
    const position=predictors.indexOf(column);
    if(position<0)throw new Error('Interaction columns must be among the selected predictors');
    return position+1;
  };
  const resolve=token=>{
    const raw=token.trim(),lower=raw.toLowerCase();
    if(!raw)throw new Error('Use interaction pairs like age,weight;2,3');
    // Form labels such as "time (y)" resolve through the shown column letter.
    const labelled=splitLabel(lower);
    if(labelled.alias!==null){
      const column=labelled.alias;
      if(headers[column]===labelled.name||names[column]===labelled.name||names[column]===lower)return positionOf(column);
      const byHeader=headers.indexOf(labelled.name);
      if(byHeader>=0)return positionOf(byHeader);
      const byName=names.indexOf(lower);
      if(byName>=0)return positionOf(byName);
      throw new Error('Unknown interaction column');
    }
    // Shown column letters (x, y, z, x4, x5, ...) address the Nth data column.
    const alias=aliasColumn(lower);
    if(alias!==null&&alias>=0){
      if(predictors.includes(alias))return positionOf(alias);
      const byName=names.indexOf(lower);
      if(byName>=0)return positionOf(byName);
      const byHeader=headers.indexOf(lower);
      if(byHeader>=0)return positionOf(byHeader);
      throw new Error('Interaction columns must be among the selected predictors');
    }
    const name=names.indexOf(lower);
    if(name>=0)return positionOf(name);
    const header=headers.indexOf(lower);
    if(header>=0)return positionOf(header);
    const named=/^p(\d+)$/.exec(lower);
    if(named){
      const at=Number(named[1]);
      if(!(at>=1&&at<=predictors.length))throw new Error('Interaction positions must be within the selected predictors');
      return at;
    }
    const spelled=/^column\s*(\d+)$/.exec(lower),digits=/^#?(\d+)$/.exec(raw);
    const column=spelled?Number(spelled[1]):digits?Number(digits[1]):null;
    if(column===null)throw new Error('Unknown interaction column');
    return positionOf(column-1);
  };
  return text.split(';').map(group=>{
    const tokens=group.split(/[,+]+/).filter(Boolean);
    if(tokens.length!==2)throw new Error('Use interaction pairs like age,weight;2,3');
    const [first,second]=tokens.map(resolve);
    return first<=second?[first,second]:[second,first];
  });
}

export function survivalAnalysisPlan(rows,settings={},columnLabels=[]) {
  const opts={time:'0',event:'1',eventValue:'1',grouping:'groups',group:'2',cox:'0',predictors:'',ties:'efron',ph:'test',...settings};
  const n=Math.max(0,...rows.map(row=>row.length));
  const col=key=>{const i=Number(opts[key]);if(!Number.isInteger(i)||i<0||i>=n)throw new Error('Choose valid data columns');return i;};
  if(!rows.length)throw new Error('Enter data first');
  if(!['groups','all'].includes(opts.grouping)||!['0','1'].includes(String(opts.cox)))throw new Error('Invalid analysis option');
  const time=col('time'),event=col('event'),group=opts.grouping==='groups'?col('group'):null;
  const reserved=[time,event,...(group===null?[]:[group])];
  if(new Set(reserved).size!==reserved.length)throw new Error('Roles must use different columns');
  const predictors=String(opts.cox)==='1'?String(opts.predictors).split(',').filter(Boolean).map(Number):[];
  if(new Set(predictors).size!==predictors.length||predictors.some(i=>!Number.isInteger(i)||i<0||i>=n||reserved.includes(i)))throw new Error('Choose distinct analysis columns');
  const selected=rows.map(row=>[...reserved,...predictors].map(i=>(row[i]||'').trim()));
  if(selected.some(row=>row.some(cell=>!cell)))throw new Error('Complete selected rows required');
  const states=new Set(selected.map(row=>row[1])),eventValue=String(opts.eventValue).trim();
  if(!eventValue)throw new Error('Enter the event value');
  if(states.size>2)throw new Error('Event column must have at most two values');
  if(states.size>1&&!states.has(eventValue))throw new Error('Event value does not occur in the selected column');
  const groups=group===null?[]:[...new Set(selected.map(row=>row[2]))];
  const encoded=selected.map(row=>[row[0],row[1]===eventValue?'1':'0',group===null?'1':String(groups.indexOf(row[2])+1),...row.slice(reserved.length)]);
  return {expression:`survivalanalysis([${encoded.map(row=>`[${row.join(',')}]`).join(',')}],${opts.cox},${opts.ties},-1,${opts.ph==='test'?1:0})`,groups,predictors:predictors.map(i=>columnLabels[i]||['x','y','z'][i]||`x${i+1}`)};
}

export function guidedStatisticsCommand(definition,rows,settings={},columnLabels=[]) {
  if(definition.id==='survivalanalysis')return survivalAnalysisPlan(rows,settings).expression;
  if(!definition.controls)return advancedStatisticsCommand(definition,rows);
  if(!rows.some(row=>row.some(cell=>cell.trim())))throw new Error('Enter data first');
  const n=Math.max(...rows.map(row=>row.length)),id=definition.id;
  const opts=Object.fromEntries(definition.controls.map(field=>[field.key,settings[field.key]??field.default]));
  for(const field of definition.controls)if(field.type==='choice'&&!field.choices.some(choice=>choice.id===opts[field.key]&&(!choice.when||Object.entries(choice.when).every(([key,values])=>values.includes(opts[key])))))throw new Error('Invalid analysis option');
  const column=key=>{let i=Number(opts[key]);if(i===-1)i=n-1;if(!Number.isInteger(i)||i<0||i>=n)throw new Error('Choose valid data columns');return i;};
  const list=values=>`[${values.join(',')}]`,table=values=>list(values.map(list));
  const values=i=>rows.map(row=>(row[i]||'').trim()).filter(Boolean);
  const multiple=(key,excluded=[])=>{
    const raw=opts[key],cols=raw==='auto'?Array.from({length:n},(_,i)=>i).filter(i=>!excluded.includes(i)):Array.isArray(raw)?raw:String(raw).split(',').filter(Boolean).map(Number);
    if(!cols.length||new Set(cols).size!==cols.length||cols.some(i=>!Number.isInteger(i)||i<0||i>=n||excluded.includes(i)))throw new Error('Choose distinct analysis columns');
    return cols;
  };
  const distinct=indices=>{if(new Set(indices).size!==indices.length)throw new Error('Roles must use different columns');};
  const complete=indices=>{distinct(indices);const selected=rows.map(row=>indices.map(i=>(row[i]||'').trim()));if(selected.some(row=>row.some(cell=>!cell)))throw new Error('Complete selected rows required');return selected;};
  const eventRows=indices=>{
    const event=column('event'),time=column('time');const selected=complete([time,event,...indices]);
    if(!opts.eventValue.trim())throw new Error('Enter the event value');
    const states=new Set(selected.map(row=>row[1]));if(states.size>2)throw new Error('Event column must have at most two values');
    if(states.size>1&&!states.has(opts.eventValue))throw new Error('Event value does not occur in the selected column');
    return selected.map(row=>[row[0],row[1]===opts.eventValue?'1':'0',...row.slice(2)]);
  };
  if(id==='padjust')return `padjust(${list(values(column('column')))},${opts.method},${opts.alpha})`;
  if(id==='ancova'){
    const group=column('group'),response=column('response');distinct([group,response]);
    const selected=complete([group,...multiple('predictors',[group,response]),response]),labels=[...new Set(selected.map(row=>row[0]))];
    if(labels.length<2)throw new Error('Choose at least two groups');
    const mapped=selected.map(row=>[String(labels.indexOf(row[0])+1),...row.slice(1)]);
    return `ancova(${table(mapped)},${opts.level},${opts.slopes==='test'?1:0})`;
  }
  if(id==='glm'){
    const response=column('response'),offset=opts.adjustment!=='none'?column('offset'):null;
    const reserved=[response,...(offset===null?[]:[offset])];distinct(reserved);
    const mapped=complete([...multiple('predictors',reserved),response]);
    const suffix=offset===null?'':`,${list(complete([offset]).map(row=>row[0]))},${opts.adjustment}`;
    return `glm(${table(mapped)},${opts.family},${opts.link},${opts.family==='nbinom'?opts.alpha:1}${suffix})`;
  }
  if(id==='bayescompare'){
    const first=column('first'),second=column('second');distinct([first,second]);
    const samples=[values(first),values(second)];
    if(samples.some(sample=>sample.length<2))throw new Error('Enter at least two observations in each group');
    const keys=['variance','mu','kappa','alpha','beta','level','samples','seed'];
    if(keys.some(key=>!String(opts[key]).trim()))throw new Error('Enter all Bayesian prior and interval parameters');
    return `${id}(${samples.map(list).join(',')},${keys.map(key=>String(opts[key]).trim()).join(',')})`;
  }
  if(['bayesproportion','bayesmean','bayesrate'].includes(id)){
    let data;
    if(id==='bayesproportion'&&opts.layout==='counts')data=table(complete([column('successes'),column('trials')]));
    else if(id==='bayesrate'&&opts.layout==='exposure')data=table(complete([column('column'),column('exposure')]));
    else data=list(complete([column('column')]).map(row=>row[0]));
    const keys=id==='bayesmean'?['mu','kappa','alpha','beta','level','threshold']:['alpha','beta','level','threshold'];
    if(keys.some(key=>!String(opts[key]).trim()))throw new Error('Enter all Bayesian prior and interval parameters');
    return `${id}(${data},${keys.map(key=>String(opts[key]).trim()).join(',')})`;
  }
  if(['levene','bartlett'].includes(id)){
    let samples;
    if(opts.grouping==='groups'){
      const pairs=complete([column('group'),column('value')]),labels=[...new Set(pairs.map(row=>row[0]))];samples=labels.map(label=>pairs.filter(row=>row[0]===label).map(row=>row[1]));
    }else samples=multiple('columns').map(values);
    if(samples.length<2)throw new Error('Choose at least two groups');
    return `${id}(${samples.map(list).join(',')})`;
  }
  if(id==='mcnemar'){
    const first=column('first'),second=column('second');distinct([first,second]);
    const pairs=opts.layout==='pairs'?rows.map(row=>[String(row[first]??'').trim(),String(row[second]??'').trim()]).filter(row=>row.every(Boolean)):complete([first,second]);let counts=pairs;
    if(opts.layout==='pairs'){
      const labels=[...new Set(pairs.flat())];if(labels.length!==2)throw new Error('Paired observations require the same two categories');
      counts=[[0,0],[0,0]];for(const pair of pairs)counts[labels.indexOf(pair[0])][labels.indexOf(pair[1])]++;
    }else if(pairs.length!==2)throw new Error('The count table needs exactly two rows');
    return `mcnemar(${table(counts)},${opts.method})`;
  }
  if(id==='kaplanmeier')return `kaplanmeier(${table(eventRows([]))},${opts.level})`;
  if(id==='logrank'){
    const selected=eventRows([column('group')]),labels=[...new Set(selected.map(row=>row[2]))];
    if(labels.length!==2)throw new Error('Log-rank requires exactly two groups');
    return `logrank(${labels.map(label=>table(selected.filter(row=>row[2]===label).map(row=>row.slice(0,2)))).join(',')})`;
  }
  if(id==='cox'){
    const time=column('time'),event=column('event'),entry=opts.truncation==='entry'?column('entry'):null;
    const reserved=[time,event,...(entry===null?[]:[entry])];
    const selected=eventRows([...(entry===null?[]:[entry]),...multiple('predictors',reserved)]);
    return `cox(${table(selected)},${opts.ties},${entry===null?-1:2},${opts.ph==='test'?1:0})`;
  }
  if(id==='repeatedanova'){
    const cols=multiple('columns');if(cols.length<2)throw new Error('Choose at least two conditions');return `repeatedanova(${table(complete(cols))},${opts.factor2})`;
  }
  if(['poissonreg','nbreg'].includes(id)){
    const response=column('response'),offset=opts.adjustment!=='none'?column('offset'):null;
    const reserved=[response,...(offset===null?[]:[offset])];distinct(reserved);
    const predictors=multiple('predictors',reserved),mapped=complete([...predictors,response]);
    const suffix=offset===null?'':`,${list(complete([offset]).map(row=>row[0]))},${opts.adjustment}`;
    return `${id}(${table(mapped)}${suffix})`;
  }
  if(['mixedmodel','gee','glmm'].includes(id)){
    const subject=column('subject'),response=column('response');distinct([subject,response]);
    const offset=id==='glmm'&&opts.family!=='binomial'&&opts.adjustment!=='none'?column('offset'):null;
    const reserved=[subject,response,...(offset===null?[]:[offset])];distinct(reserved);
    const selectedColumns=multiple('predictors',reserved);
    const selected=complete([subject,...selectedColumns,response]);const labels=[...new Set(selected.map(row=>row[0]))];
    const mapped=selected.map(row=>[String(labels.indexOf(row[0])+1),...row.slice(1)]);
    if(id==='glmm'){
      const suffix=offset===null?'':`,${list(complete([offset]).map(row=>row[0]))},${opts.adjustment}`;
      return `glmm(${table(mapped)},${opts.family},${opts.points}${suffix})`;
    }
    if(id==='gee'){
      const pairs=interactionPairs(opts.interactions,selectedColumns,columnLabels);
      if(new Set(pairs.map(pair=>pair.join(','))).size!==pairs.length)throw new Error('Interaction pairs must be distinct');
      return `gee(${table(mapped)},${opts.family},${opts.corr}${pairs.length?','+JSON.stringify(pairs):''})`;
    }
    const positions=String(opts.slope).split(',').map(value=>value.trim()).filter(Boolean);
    if(!positions.length||positions.some(value=>!Number.isInteger(Number(value))))throw new Error('Enter random-slope positions like 0 or 1,2');
    const numbers=positions.map(Number).filter(value=>value!==0);
    if(numbers.some(value=>value<1||value>19)||new Set(numbers).size!==numbers.length)throw new Error('Random-slope positions must be distinct predictor numbers');
    const argument=numbers.length===0?'0':numbers.length===1?String(numbers[0]):'['+numbers.join(',')+']';
    return `mixedmodel(${table(mapped)},${argument},${opts.method})`;
  }
  if(id==='impute'){
    const width=Math.max(...rows.map(row=>row.length));
    const cells=rows.map(row=>Array.from({length:width},(_,index)=>String(row[index]??'').trim()||'NA'));
    const method=opts.method||'mean';
    const neighbors=method==='knn'?','+String(opts.k).trim():'';
    return `impute(${table(cells)},${method}${neighbors})`;
  }
  if(id==='crossvalidate'){
    const width=Math.max(...rows.map(row=>row.length));
    const cells=rows.map(row=>Array.from({length:width},(_,index)=>String(row[index]??'').trim()));
    if(cells.some(row=>row.some(cell=>!cell)))throw new Error('Complete rows required');
    const folds=Number(opts.folds),seed=Number(opts.seed);
    if(!Number.isInteger(folds)||folds<2||folds>cells.length)throw new Error('Folds must be between 2 and the row count');
    if(!Number.isInteger(seed)||seed<0)throw new Error('Seed must be a nonnegative integer');
    const penalty=opts.model==='elasticnet'?`,[${String(opts.alpha).trim()},${String(opts.ratio).trim()}]`:opts.model==='linear'?'':`,${String(opts.alpha).trim()}`;
    const suffix=opts.split==='random'&&opts.model==='linear'?'':`,${opts.split},${opts.model}${penalty}`;
    return `crossvalidate(${table(cells)},${folds},${seed}${suffix})`;
  }
  if(id==='kstest'){
    const first=column('first');
    if(opts.mode==='two'){const second=column('second');distinct([first,second]);return `kstest(${list(values(first))},${list(values(second))})`;}
    return `kstest(${list(values(first))},${opts.mode},${opts.location},${opts.scale})`;
  }
  throw new Error('Unknown analysis form');
}

// Predictor positions in the engine follow the selected order, not the table order.
export function advancedStatisticsTermLabels(definition,rows,settings={},columnLabels=[]){
  if(!columnLabels.length)return {};
  const n=Math.max(0,...rows.map(row=>row.length)),id=definition.id;
  const opts=Object.fromEntries((definition.controls||[]).map(field=>[field.key,settings[field.key]??field.default]));
  const column=key=>Number(opts[key])===-1?n-1:Number(opts[key]);
  const multiple=excluded=>opts.predictors==='auto'?Array.from({length:n},(_,i)=>i).filter(i=>!excluded.includes(i)):String(opts.predictors??'').split(',').filter(Boolean).map(Number);
  let predictors=[];
  if(id==='mcnemar'){
    const first=column('first'),second=column('second'),pairs=opts.layout==='pairs'?rows.filter(row=>row[first]&&row[second]).map(row=>[row[first],row[second]]):[];
    return statisticsCategoryLabels(pairs,columnLabels[first],columnLabels[second],true);
  }
  if(id==='ancova'){
    const groups=[...new Set(rows.map(row=>String(row[column('group')]??'').trim()))],labels={Group:columnLabels[column('group')]};
    groups.forEach((group,i)=>{labels[`group:${i+1}`]=group;});
    multiple([column('group'),column('response')]).forEach((at,i)=>{labels[`x${i+1}`]=columnLabels[at]||`x${i+1}`;});
    return labels;
  }
  if(id==='glm')predictors=multiple([column('response'),...(opts.adjustment!=='none'?[column('offset')]:[])]);
  if(['mixedmodel','gee','glmm'].includes(id))predictors=multiple([column('subject'),column('response'),...(id==='glmm'&&opts.family!=='binomial'&&opts.adjustment!=='none'?[column('offset')]:[])]);
  else if(id==='cox')predictors=multiple([column('time'),column('event'),...(opts.truncation==='entry'?[column('entry')]:[])]);
  else if(['poissonreg','nbreg'].includes(id))predictors=multiple([column('response'),...(opts.adjustment!=='none'?[column('offset')]:[])]);
  else if(['ordinal','multinomial'].includes(id))predictors=Array.from({length:Math.max(0,n-1)},(_,i)=>i);
  else if(id==='survivalanalysis'){
    const plan=survivalAnalysisPlan(rows,settings,columnLabels),labels={};
    plan.groups.slice(1).forEach((group,i)=>{labels[`group:${i+1}`]=`${columnLabels[column('group')]}: ${group} / ${plan.groups[0]}`;labels[`x${i+1}`]=labels[`group:${i+1}`];});
    plan.predictors.forEach((name,i)=>{labels[`predictor:${i}`]=name;labels[`x${Math.max(0,plan.groups.length-1)+i+1}`]=name;});
    return labels;
  }
  const labels=Object.fromEntries(predictors.map((column,i)=>[`x${i+1}`,columnLabels[column]||`x${i+1}`]));
  if(id==='gee')for(const [i,j] of interactionPairs(opts.interactions,predictors,columnLabels))labels[i===j?`x${i}^2`:`x${i}:x${j}`]=i===j?`${labels[`x${i}`]}^2`:`${labels[`x${i}`]}:${labels[`x${j}`]}`;
  return labels;
}

// Shared schema defines the function, shape and default options for both UIs.
export function advancedStatisticsCommand(definition,rows) {
  if(definition.input==='none')return definition.example;
  if(!rows.some(row=>row.some(value=>value.trim())))throw new Error('Enter data first');
  const cells=rows.map(row=>row.map(value=>value.trim()));
  const list=values=>`[${values.join(',')}]`;
  const table=values=>list(values.map(list));
  let data;
  if(definition.input==='list')data=list(cells.map(row=>row[0]).filter(Boolean));
  else if(definition.input==='groups'){
    if(cells[0].length<2)throw new Error('Enter at least two columns');
    const groups=cells[0].map((_,i)=>cells.map(row=>row[i]||'').filter(Boolean));
    if(['cohend','kstest'].includes(definition.id)&&groups.length!==2)throw new Error('Select exactly two columns');
    data=groups.map(list).join(',');
  }else{
    if(cells.some(row=>row.length!==cells[0].length))throw new Error('Rows must have equal column counts');
    if(definition.id==='impute')for(const row of cells)for(let i=0;i<row.length;i++)row[i]||= 'NA';
    else if(cells.some(row=>row.some(value=>!value)))throw new Error('Complete rows required; impute missing values first');
    if(definition.input==='survivalgroups'){
      if(cells[0].length!==3)throw new Error('Columns: time, event, group');
      const ids=[...new Set(cells.map(row=>row[2]))];
      if(ids.length!==2)throw new Error('Log-rank requires exactly two groups');
      data=ids.map(id=>table(cells.filter(row=>row[2]===id).map(row=>row.slice(0,2)))).join(',');
    }else data=table(cells);
  }
  return `${definition.id}(${data}${definition.suffix})`;
}

// Only the columns selected by the statistics data kind are used; extra cells are ignored.
export function advancedStatisticsRows(source,columnLimit){
  const limit=Number.isInteger(columnLimit)&&columnLimit>0?columnLimit:null;
  const rows=csvRows(source,{preserveEmptyRows:true,skipHeader:false,maxColumns:limit===null?20:100});
  const columns=Math.min(limit??Infinity,Math.max(...rows.map(row=>row.length)));
  if(!computationLimitsRemoved()&&columns>20)throw new Error('Use up to 20 data columns');
  const selected=rows.map(row=>Array.from({length:columns},(_,index)=>row[index]||''));
  if(statisticsCsvHasHeader(selected)&&!selected[0].some(cell=>cell==='NA'))return selected.slice(1);
  return selected;
}

export function createAdvancedStatistics({state,persist,data,columnLimit,copy}) {
  const select=$('statistics-advanced-kind'),source=$('statistics-advanced-source'),help=$('statistics-advanced-help'),input=$('statistics-advanced-input'),form=$('statistics-advanced-controls'),status=$('statistics-advanced-status');
  const selected=()=>advancedStatisticsSchema.find(item=>item.id===select.value)||advancedStatisticsSchema[0];
  const previous=state.fields['statistics-advanced-kind']||select.value;
  select.replaceChildren(...advancedStatisticsSchema.map(item=>{const option=element('option',item.label);option.value=item.id;return option;}));
  select.value=previous||advancedStatisticsSchema[0].id;
  if(!select.value)select.value=advancedStatisticsSchema[0].id;
  if(!source.value)source.value=selected().example;
  input.value=state.fields[input.id]||((state.fields[source.id]&&source.value!==selected().example)||!selected().controls?'expression':data().trim()?'current':'example');
  let signature='';
  const report=$('statistics-survival-result');
  let displayed=null,reportKey='';
  const fieldId=key=>`statistics-form-${selected().id}-${key}`;
  const settings=()=>Object.fromEntries((selected().controls||[]).map(field=>[field.key,state.fields[fieldId(field.key)]??field.default]));
  const limit=()=>{const value=Number(columnLimit?.());return Number.isInteger(value)&&value>0?value:null;};
  const currentRows=()=>input.value==='example'?selected().exampleRows:advancedStatisticsRows(data(),limit());
  const context=()=>{
    const plan=selected().id==='survivalanalysis'&&input.value!=='expression'?survivalAnalysisPlan(currentRows(),settings(),columnNames()):{expression:expression(),groups:[],predictors:[]};
    let usesCurrentData=input.value==='current'&&!!selected().controls;
    if(input.value==='current'&&!selected().controls)try{usesCurrentData=source.value.trim()===advancedStatisticsCommand(selected(),currentRows());}catch{}
    if(usesCurrentData)plan.termLabels=advancedStatisticsTermLabels(selected(),currentRows(),settings(),columnNames());
    return plan;
  };
  function columnNames(){if(input.value!=='current'||!data().trim())return [];const rows=csvRows(data(),{skipHeader:false});const count=Math.min(limit()??Infinity,Math.max(0,...rows.map(row=>row.length)));return statisticsCsvHasHeader(rows)&&!rows[0].some(cell=>cell==='NA')?statisticsColumnLabels(data(),`columns:${count}`):[];}
  const expression=()=>selected().controls&&input.value!=='expression'?guidedStatisticsCommand(selected(),currentRows(),settings(),columnNames()):source.value.trim();
  const preview=()=>{
    if(selected().controls&&input.value!=='expression'){
      try{source.value=expression();status.textContent=`${currentRows().length} ${getLanguage()==='ko'?'행':'rows'}`;}
      catch(exc){source.value='';status.textContent=t(exc.message);}
    }else status.textContent='';
  };
  const update=()=>{
    const definition=selected();
    const korean=getLanguage()==='ko';
    const suite=definition.id==='survivalanalysis';
    $('statistics-survival-tools').hidden=!suite;
    if(displayed){let key='';try{key=JSON.stringify(context());}catch{}if(key!==reportKey){displayed=null;report.replaceChildren();report.hidden=true;}else renderSurvivalResult();}
    help.textContent=definition.controls&&input.value!=='expression'?(korean?definition.formHelpKo:definition.formHelp):(korean?definition.helpKo:definition.help);
    for(const option of select.options){const item=advancedStatisticsSchema.find(item=>item.id===option.value);option.textContent=korean?item.ko:item.label;}
    $('statistics-advanced-data').disabled=definition.input==='none';
    input.closest('label').hidden=!definition.controls;
    const guided=definition.controls&&input.value!=='expression';
    source.readOnly=!!guided;
    $('statistics-advanced-expression').hidden=!!guided;
    form.hidden=!guided;
    $('statistics-advanced-example-data').hidden=!guided||input.value!=='example';
    if(guided){
      let rows=[];try{rows=currentRows();}catch{}
      const count=Math.max(1,...rows.map(row=>row.length)),opts=settings();
      let labels=Array.from({length:count},(_,i)=>['x','y','z'][i]||`x${i+1}`);
      if(input.value==='current')try{const raw=csvRows(data(),{skipHeader:false});if(statisticsCsvHasHeader(raw)&&!raw[0].some(cell=>cell==='NA'))labels=labels.map((name,i)=>raw[0][i]&&raw[0][i]!==name?`${raw[0][i]} (${name})`:name);}catch{}
      const next=JSON.stringify([definition.id,input.value,labels,korean,definition.controls.filter(f=>f.type==='choice'||f.type==='column').map(f=>opts[f.key])]);
      if(next!==signature){
        signature=next;form.replaceChildren();
        for(const field of definition.controls){
          if(field.when&&!Object.entries(field.when).every(([key,values])=>values.includes(opts[key])))continue;
          const caption=korean?field.ko:field.label,id=fieldId(field.key),value=opts[field.key];
          const changed=newValue=>{
            state.fields[id]=newValue;
            if(definition.id==='glm'&&field.key==='family')state.fields[fieldId('link')]='auto';
            if(['time','event','subject','response','group','grouping','offset','adjustment'].includes(field.key)||(definition.id==='glmm'&&field.key==='family'))state.fields[fieldId('predictors')]=definition.id==='survivalanalysis'?'':'auto';
            if(field.type!=='number')signature='';update();
            persist();
          };
          if(field.type==='columns'){
            const group=element('fieldset'),legend=element('legend',caption);group.append(legend);group.className='form-row statistics-form-columns';
            const roles=definition.id==='ancova'?['group','response']:definition.id==='survivalanalysis'?['time','event',...(opts.grouping==='groups'?['group']:[])]:definition.id==='cox'?['time','event']:['poissonreg','nbreg','glm'].includes(definition.id)?['response']:['subject','response'];
            if(['poissonreg','nbreg','glmm','glm'].includes(definition.id)&&opts.adjustment!=='none'&&(definition.id!=='glmm'||opts.family!=='binomial'))roles.push('offset');
            const reserved=field.key==='predictors'?roles.map(key=>Number(opts[key])===-1?count-1:Number(opts[key])):[];
            const columns=value==='auto'?Array.from({length:count},(_,i)=>i).filter(i=>!reserved.includes(i)):String(value).split(',').filter(Boolean).map(Number);
            const store=element('input');store.type='hidden';store.id=id;store.value=String(value);group.append(store);
            for(let i=0;i<count;i++)if(!reserved.includes(i)){
              const label=element('label',labels[i],'check'),check=element('input');check.type='checkbox';check.checked=columns.includes(i);check.dataset.column=String(i);
              check.onchange=()=>{const chosen=[...group.querySelectorAll('input[type="checkbox"]')].filter(item=>item.checked).map(item=>item.dataset.column).join(',');store.value=chosen;changed(chosen);};label.append(check);group.append(label);
            }
            form.append(group);
          }else{
            const label=element('label',caption),control=element(field.type==='number'?'input':'select');control.id=id;
            if(field.type==='number'){control.value=value;control.oninput=()=>changed(control.value);}
            else{
              const choices=field.type==='column'?labels.map((name,i)=>({id:String(i),label:name,ko:name})):field.choices.filter(choice=>!choice.when||Object.entries(choice.when).every(([key,values])=>values.includes(opts[key])));
              control.replaceChildren(...choices.map(choice=>{const item=element('option',korean?choice.ko:choice.label);item.value=String(choice.id);return item;}));
              const wanted=field.type==='column'&&Number(value)===-1?String(count-1):String(value);control.value=wanted;
              // Keep an unavailable role invalid until the user picks a column.
              if(!control.value){const missing=element('option',korean?'열 선택':'Choose column');missing.value=String(value);control.prepend(missing);control.value=String(value);}
              control.onchange=()=>changed(control.value);
            }
            label.append(control);form.append(label);
          }
        }
      }
      $('statistics-advanced-example-data').textContent=rows.map(row=>row.join(', ')).join('\n');
    }
    preview();
  };
  select.onchange=()=>{source.value=selected().example;input.value=selected().controls?(data().trim()?'current':'example'):'expression';signature='';update();persist();};
  input.onchange=()=>{if(input.value==='expression'&&!source.value)source.value=selected().example;signature='';update();persist();};
  $('statistics-advanced-example').onclick=()=>{source.value=selected().example;if(selected().controls)input.value='example';signature='';update();persist();};
  $('statistics-advanced-data').onclick=()=>{
    try{if(selected().controls){input.value='current';signature='';update();}else {source.value=advancedStatisticsCommand(selected(),advancedStatisticsRows(data(),limit()));input.value='current';}persist();}
    catch(exc){help.textContent=exc.message;}
  };
  $('statistics-data').addEventListener('input',update);
  globalThis.addEventListener?.('resize',()=>{if(displayed)update();});
  $('statistics-survival-band').checked=state.fields['statistics-survival-band']!==false;
  $('statistics-survival-band').onchange=()=>{state.fields['statistics-survival-band']=$('statistics-survival-band').checked;update();persist();};
  update();
  function renderSurvivalResult(){
    const snapshot=displayed.copyResult;
    renderSurvivalReport(report,displayed.result,{...displayed.context,band:$('statistics-survival-band').checked,digits:state.digits,
      onCopy:copy&&snapshot?()=>copy(statisticsResultMarkdown(snapshot,{digits:state.digits,notation:state.resultDisplayMode})):null});
  }
  return {expression,context,render:update,showResult:(result,runContext)=>{
    if(!result.ok||!result.survival)return;
    try{if(JSON.stringify(context())!==JSON.stringify(runContext))return;}catch{return;}
    displayed={result:result.survival,copyResult:result,context:runContext};reportKey=JSON.stringify(runContext);
    renderSurvivalResult();
  }};
}
