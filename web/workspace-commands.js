import {parse,latexInput} from './parser.js';
function csvRecordDelimiter(source,start){
  // Excel separates columns with tabs; commas inside those cells are literal.
  let quoted=false;
  for(let i=start;i<source.length;i++){const ch=source[i];if(ch==='"')quoted=!quoted;else if(!quoted){if(ch==='\t')return '\t';if(ch==='\n'||ch==='\r')break;}}
  return ',';
}
export function statisticsCsvHasHeader(rows){
  const first=rows[0];if(!first?.some(Boolean))return false;
  const labels=first.map(cell=>cell.toLowerCase());
  if(['x','n','value','y','x,y','group,value','date,value','date,y','x,y,z'].includes(labels.join(','))||labels.length>3&&labels.every((cell,i)=>cell===statisticsColumnNames(labels.length)[i]))return true;
  function dataCell(cell){
    if(!cell)return false;
    const normalized=cell.replace(/,/g,'');
    if(['pi','π','e','E','tau','τ','∞'].includes(cell)||Number.isFinite(Number(normalized))||/^(?:NaN|[+-]?Infinity)$/i.test(cell)||/^\d{4}([-/.])\d{1,2}\1\d{1,2}$/.test(cell))return true;
    if(/^[\p{L}_][\p{L}\p{N}_]*(?:\s+[\p{L}_][\p{L}\p{N}_]*)*$/u.test(cell))return false;
    try{return parse(latexInput(cell)).kind!=='symbol';}catch{return false;}
  }
  return first.every(Boolean)&&first.every(cell=>!dataCell(cell))&&rows.slice(1).some(row=>row.length===first.length&&row.some(dataCell));
}

export function csvRows(source,{maxColumns=100,skipHeader=true,preserveEmptyRows=false}={}){
  source=source.replace(/^\uFEFF/,'');
  const rows=[];let row=[],cell='',quoted=false,delimiter=csvRecordDelimiter(source,0);
  for(let i=0;i<source.length;i++){const ch=source[i];if(ch==='"'){if(quoted&&source[i+1]==='"'){cell+='"';i++;}else quoted=!quoted;}else if(!quoted&&ch===delimiter){row.push(cell.trim());cell='';}else if(!quoted&&(ch==='\n'||ch==='\r')){if(ch==='\r'&&source[i+1]==='\n')i++;row.push(cell.trim());if(row.some(Boolean)||preserveEmptyRows)rows.push(row);row=[];cell='';delimiter=csvRecordDelimiter(source,i+1);}else cell+=ch;}
  if(quoted)throw new Error('Unclosed CSV quote');row.push(cell.trim());if(row.some(Boolean)||preserveEmptyRows&&source.length&&!/[\r\n]$/.test(source))rows.push(row);
  if(!rows.length||rows.length>5000)throw new Error('Enter 1–5000 data rows');
  if(skipHeader&&statisticsCsvHasHeader(rows))rows.shift();
  if(!rows.length)throw new Error('Enter data below the header');
  const columns=Math.max(...rows.map(r=>r.length));if(columns>maxColumns)throw new Error(`Use up to ${maxColumns} data columns`);return rows.map(r=>Array.from({length:columns},(_,i)=>r[i]||''));
}
function markdownCells(line){
  line=line.trim();if(line.startsWith('|'))line=line.slice(1);if(line.endsWith('|')&&!line.endsWith('\\|'))line=line.slice(0,-1);
  const cells=[];let cell='';
  for(let index=0;index<line.length;index++){const ch=line[index];if(ch==='\\'&&line[index+1]==='|'){cell+='|';index++;}else if(ch==='|'){cells.push(cell.trim());cell='';}else cell+=ch;}
  cells.push(cell.trim());return cells;
}
function markdownCsvCell(cell){return /[",\r\n\t]/.test(cell)?`"${cell.replace(/"/g,'""')}"`:cell;}
export function markdownTableCsv(source){
  const lines=source.replace(/^\uFEFF/,'').trim().split(/\r\n|\n|\r/);
  if(lines[0]?.startsWith('```')&&lines.at(-1)?.startsWith('```')){lines.shift();lines.pop();}
  if(lines.length<3||lines.some(line=>!line.includes('|')))return null;
  const header=markdownCells(lines[0]),separator=markdownCells(lines[1]);
  if(!header.length||separator.length!==header.length||!separator.every(cell=>/^:?-{3,}:?$/.test(cell)))return null;
  const rows=[header,...lines.slice(2).map(markdownCells)];
  if(rows.slice(1).some(row=>row.length>header.length))return null;
  return rows.map(row=>Array.from({length:header.length},(_,index)=>markdownCsvCell(row[index]||'')).join(',')).join('\n');
}
export function statisticsColumnCount(kind){
  if(Object.hasOwn({list:1,xy:2,xyz:3},kind))return {list:1,xy:2,xyz:3}[kind];
  const match=/^columns:(\d+)$/.exec(kind||'');
  return match&&Number(match[1])>=1&&Number(match[1])<=100?Number(match[1]):0;
}
export function statisticsDetectedColumns(source){
  if(!source.trim())return 1;
  return Math.max(1,...csvRows(source,{skipHeader:false,preserveEmptyRows:true}).map(row=>row.length));
}
export function statisticsColumnNames(count){return Array.from({length:count},(_,i)=>['x','y','z'][i]||`x${i+1}`);}
export function statisticsColumnLabels(source,kind){
  const names=statisticsColumnNames(statisticsColumnCount(kind));
  try{const raw=csvRows(source,{skipHeader:false});if(statisticsCsvHasHeader(raw))return names.map((name,i)=>raw[0][i]&&raw[0][i]!==name?`${raw[0][i]} (${name})`:name);}catch{}
  return names;
}
export function statisticsHeatMapColumnNames(source,kind){
  const names=statisticsColumnNames(statisticsColumnCount(kind));
  try{const rows=csvRows(source,{skipHeader:false});if(statisticsCsvHasHeader(rows))return names.map((name,index)=>rows[0][index]||name);}catch{}
  return names;
}
export function statisticsKindForColumns(count){return ['list','xy','xyz'][count-1]||`columns:${count}`;}
export function statisticsDataRows(source,kind){
  const columns=statisticsColumnCount(kind);
  if(!columns)throw new Error('Select List, x,y data, or x,y,z data');
  return csvRows(source).map(row=>Array.from({length:columns},(_,i)=>row[i]||''));
}
export function numericStatisticsRows(rows){
  const dates=rows.map(row=>{const match=/^(\d{4})([-/.])(\d{1,2})\2(\d{1,2})$/.exec(row[0]);if(!match)return null;const at=Date.UTC(Number(match[1]),Number(match[3])-1,Number(match[4])),date=new Date(at);return date.getUTCFullYear()===Number(match[1])&&date.getUTCMonth()===Number(match[3])-1&&date.getUTCDate()===Number(match[4])?at:null;}),valid=dates.filter(n=>n!==null),origin=valid.length?Math.min(...valid)-86400000:null;
  return rows.map((row,i)=>row.map((cell,j)=>j===0&&origin!==null?dates[i]===null?'':String((dates[i]-origin)/86400000):/^[+-]?\d{1,3}(?:,\d{3})+(?:\.\d+)?(?:[eE][+-]?\d+)?$/.test(cell)?cell.replace(/,/g,''):cell));
}
const vector=values=>'['+values.join(',')+']';
export function statisticsDatasetSource(source,kind){
  const rows=numericStatisticsRows(statisticsDataRows(source,kind));
  return vector(statisticsColumnCount(kind)===1?rows.map(row=>row[0]).filter(Boolean):rows.filter(row=>row.every(Boolean)).map(vector));
}
export function statisticsAnalysisData(source,{op='stats',column=0,grouping='columns',firstGroup='',secondGroup='',kind}={}){
  const paired=['regression','correlation','ttestpaired','wilcoxon','chi2independence','fisherexact'].includes(op)&&!(op==='wilcoxon'&&statisticsColumnCount(kind)===1);
  const categorical=['chi2independence','fisherexact'].includes(op);
  const grouped=grouping==='groups'&&!paired&&(!kind||kind==='xy'),raw=kind?statisticsDataRows(source,kind):csvRows(source),rows=grouped||categorical?raw:numericStatisticsRows(raw),columns=Array.from({length:rows[0].length},(_,i)=>rows.map(r=>r[i]).filter(Boolean)),pairs=rows.filter(r=>r[0]&&r[1]),groups=new Map();
  if(grouped)for(const [name,number] of pairs){if(!groups.has(name))groups.set(name,[]);groups.get(name).push(number);}
  const names=groups.size?[...groups.keys()]:statisticsColumnNames(columns.length),values=groups.size?[...groups.values()]:columns;
  const first=groups.size?(names.includes(firstGroup)?names.indexOf(firstGroup):0):firstGroup?names.indexOf(firstGroup):0;
  const second=groups.size?(names.includes(secondGroup)?names.indexOf(secondGroup):names.findIndex((_,i)=>i!==first)):secondGroup?names.indexOf(secondGroup):1;
  const selected=groups.size?first:column;
  const samples=paired?['x','y'].map((label,i)=>({label,values:pairs.map(row=>row[i])})):['anova','tukey','kruskal'].includes(op)?names.map((label,i)=>({label,values:values[i]})):['ttest2','ztest2','mannwhitney'].includes(op)?[first,second].map(i=>({label:names[i],values:values[i]})):[{label:names[selected],values:values[selected]}];
  return {rows,groups,pairs,paired,categorical,first,second,samples};
}
export function statisticsCommand(source,{op='stats',column=0,extra='0',tail='two',sigma='1',sigmaY='1',yatesCorrection=true,regression='linear',degree='3',alpha='0.1',l1Ratio='0.5',trees='100',maxDepth='10',seed='0',responseColumn,formula='A*exp(-k*x)+C',variable='x',initials='',grouping='columns',firstGroup='',secondGroup='',kind}={}){
  if(op==='regression'&&kind&&kind!=='xy'&&!(statisticsColumnCount(kind)>1&&['multiple','logistic','ridge','lasso','elasticnet','logisticridge','logisticlasso','logisticelasticnet','randomforest','randomforestclassifier','randomforestregressor'].includes(regression)))throw new Error('Regression needs x,y data');
  const {rows,groups,pairs,categorical,first,second,samples:activeSamples}=statisticsAnalysisData(source,{op,column,grouping,firstGroup,secondGroup,kind});
  const samples=activeSamples.map(sample=>sample.values),data=samples[0];
  if(!categorical){for(const number of samples.flat().filter(Boolean))parse(number);}
  const tailArgument=tail==='two'?'':','+tail;
  if(op==='regression'){
    if(['multiple','logistic','ridge','lasso','elasticnet','logisticridge','logisticlasso','logisticelasticnet','randomforest','randomforestclassifier','randomforestregressor'].includes(regression)){
      const complete=rows.filter(row=>row.every(Boolean));
      const machineLearning=['ridge','lasso','elasticnet','logisticridge','logisticlasso','logisticelasticnet','randomforest','randomforestclassifier','randomforestregressor'].includes(regression);
      if(machineLearning?complete.length<2:complete.length<=rows[0].length)throw new Error(machineLearning?'Regression needs at least two complete rows':'Add more data points than fit parameters');
      const response=responseColumn===undefined?rows[0].length-1:Number(responseColumn);
      if(!Number.isInteger(response)||response<0||response>=rows[0].length)throw new Error('Select a dependent variable column');
      const order=rows[0].map((_,i)=>i).filter(i=>i!==response).concat(response);
      return `regression(${vector(complete.map(row=>vector(order.map(i=>row[i]))))},${regression}${['elasticnet','logisticelasticnet'].includes(regression)?`,[${alpha},${l1Ratio}]`:['ridge','lasso','logisticridge','logisticlasso'].includes(regression)?','+alpha:regression.startsWith('randomforest')?`,[${trees},${maxDepth},${seed}]`:''})`;
    }
    if(pairs.length<2)throw new Error('Regression needs at least two complete x,y rows');
    return `regression(${vector(pairs.map(p=>vector(regression==='polynomial'&&Number(responseColumn)===0?[p[1],p[0]]:p.slice(0,2))))},${regression}${regression==='polynomial'?','+degree:regression==='custom'?`,${formula},${variable}${initials.trim()?','+initials:''}`:''})`;
  }
  if(categorical){if(pairs.length<2)throw new Error('Enter complete categorical pairs');const left=[...new Set(pairs.map(r=>r[0]))],right=[...new Set(pairs.map(r=>r[1]))];return `${op}(${vector(pairs.map(r=>left.indexOf(r[0])+1))},${vector(pairs.map(r=>right.indexOf(r[1])+1))}${op==='fisherexact'?tailArgument:','+(yatesCorrection?1:0)})`;}
  if(op==='wilcoxon'&&statisticsColumnCount(kind)===1){if(!data?.length)throw new Error('Select a nonempty data column');return `wilcoxon(${vector(data)}${tailArgument})`;}
  if(['correlation','ttestpaired','wilcoxon'].includes(op)){if(pairs.length<2)throw new Error('Enter at least two complete paired rows');return `${op}(${op==='ttestpaired'?extra+',':''}${vector(pairs.map(r=>r[0]))},${vector(pairs.map(r=>r[1]))}${op!=='correlation'?tailArgument:''})`;}
  if(op==='mannwhitney'){const [a,b]=samples;if(!a?.length||!b?.length||first===second)throw new Error('Select two different nonempty samples');return `mannwhitney(${vector(a)},${vector(b)}${tailArgument})`;}
  if(['ttest2','ztest2'].includes(op)){const [a,b]=samples;if(!a?.length||!b?.length||first===second)throw new Error('Select two different nonempty samples');return `${op}(${extra},${op==='ztest2'?sigma+','+sigmaY+',':''}${vector(a)},${vector(b)}${tailArgument})`;}
  if(['anova','tukey','kruskal'].includes(op)){if(samples.length<2||samples.some(s=>s.length<(op==='kruskal'?1:2)))throw new Error('Enter at least two observations in each group');return `${op}(${samples.map(vector).join(',')})`;}
  if(!data?.length)throw new Error('Select a nonempty data column');
  if(op==='ttest')return `ttest(${extra},${vector(data)}${tailArgument})`;
  if(op==='ztest')return `ztest(${extra},${sigma},${vector(data)}${tailArgument})`;
  if(op==='tinterval')return `tinterval(${extra},${vector(data)})`;
  if(op==='zinterval')return `zinterval(${extra},${sigma},${vector(data)})`;
  return `${op}(${vector(data)})`;
}
export function distributionCommand({family='normal',query='cdf',x='1',a='-1.96',b='1.96',p='.975',mean='0',sigma='1',df='10',df2='10',trials='10',success='.5',lambda='2',k='3'}={}){
  const names={normal:['normpdf','normcdf','invnorm'],t:['tpdf','tcdf','invt'],chi2:['chi2pdf','chi2cdf'],f:['fpdf','fcdf'],binomial:['binompdf','binomcdf'],poisson:['poissonpdf','poissoncdf'],geometric:['geometpdf','geometcdf']},functions=names[family];
  const discrete=['binomial','poisson','geometric'].includes(family),index=query==='pdf'||query==='list-pdf'?0:query==='quantile'?2:1,name=functions?.[index];if(!name)throw new Error('This distribution does not support that query');
  if(discrete){const parameters=family==='binomial'?[trials,success]:[family==='poisson'?lambda:success];if(!query.startsWith('list'))parameters.push(k);return `${name}(${parameters.join(',')})`;}
  const parameters=family==='normal'?[mean,sigma]:family==='f'?[df,df2]:[df];
  const inputs=query==='interval'?[a,b]:[query==='quantile'?p:x];if(family==='normal'&&query==='cdf')inputs.unshift('-oo');return `${name}(${[...inputs,...parameters].join(',')})`;
}
export function polynomialEquation(coefficients,variable='x'){
  if(!/^[A-Za-z][A-Za-z0-9_]*$/.test(variable))throw new Error('Enter a valid variable name');
  let source='';for(const [index,coefficient] of coefficients.entries()){const raw=coefficient.trim()||'0';if(raw==='0')continue;const numeric=/^[+-]?(?:\d+(?:\.\d*)?|\.\d+)(?:[eE][+-]?\d+)?$/.test(raw),negative=numeric&&raw.startsWith('-'),magnitude=negative?raw.slice(1):raw,power=coefficients.length-index-1;source+=source?(negative?'-':'+'):negative?'-':'';if(!power)source+=numeric?magnitude:`(${magnitude})`;else{if(magnitude!=='1')source+=(numeric?magnitude:`(${magnitude})` )+'*';source+=variable+(power>1?'^'+power:'');}}
  return (source||'0')+'=0';
}
export function equationCommand({kind='solve',source,variable='x',extra='0,1',initial='',hint=''}){
  const equations=source.split(/\r?\n/).map(s=>s.trim()).filter(Boolean).map(latexInput);if(!equations.length)throw new Error('Enter an equation');
  const expression=equations.length===1?equations[0]:vector(equations);
  if(kind==='dsolve')return `dsolve(${expression},${variable},${extra}${initial.trim()?','+initial.trim():''})`;
  if(kind==='pdsolve')return `pdsolve(${expression},${variable}${hint.trim()?','+hint.trim():''})`;
  return `${kind}(${expression},${variable.includes(',')?vector(variable.split(',').map(s=>s.trim())):variable}${kind==='nsolve'?','+extra:''})`;
}
