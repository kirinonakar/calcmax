import {t} from './i18n.js';
import {resultText,resultDisplayTree} from './result-display.js';
import {equationFormulaText} from './equation-formula-text.js';
import {parse,latexInput} from './parser.js';
import {roundNumber} from './display-format.js';

export function regressionEquationCopyText(result,{digits=10,regressionVariables={},regressionPrefix}={}){
  const report=result.regression;if(!report||report.model==='randomforest')return null;
  const source=result.decimal||result.exact;if(!source)return null;
  try{
    const rename=node=>({...node,value:node.kind==='symbol'?(regressionVariables[node.value]||node.value):node.value,args:(node.args||[]).map(rename)});
    const tree=rename(parse(latexInput(source)));
    const logistic=report.fitScale==='binomial'||String(report.model||'').includes('logistic');
    return (regressionPrefix??(logistic?'P(y = 1) = ':'y = '))+equationFormulaText(tree,digits);
  }catch{return null;}
}

export function statisticsFormattedCopyCell(value,options={}){
  const settings={...options,decimal:true,mixed:false},digits=options.digits??10;
  const tree=resultDisplayTree(value,settings);
  const complex=node=>['sum','product','binary','function','call','root','power'].includes(node.kind)||(node.args||[]).some(complex);
  const notation=tree?.kind==='product'&&tree.args?.[1]?.kind==='power'&&tree.args[1].args?.[0]?.value==='10';
  if(tree&&!notation&&complex(tree))return equationFormulaText(tree,digits);
  const source=value.decimal||value.exact||'';
  if(tree?.kind==='text'&&/[+*/^()]/.test(source)){
    try{return equationFormulaText(parse(latexInput(source)),digits);}catch{}
  }
  return resultText(value,settings);
}

export function markdownCell(value){
  return String(value).replaceAll('\\','\\\\').replaceAll('&','&amp;').replaceAll('<','&lt;').replaceAll('>','&gt;')
    .replaceAll('|','\\|').replaceAll('*','\\*').replaceAll('_','\\_').replaceAll('`','\\`').replaceAll('[','\\[').replaceAll(']','\\]')
    .replace(/\r\n|\r/g,'\n').replaceAll('\n','<br>');
}

export function statisticsResultMarkdown(result,options={}){
  const report=result.statisticsReport||result.statisticsCopyReport;
  if(!report)return resultText(result,options);
  const blocks=['## '+markdownCell(t(report.title))],line=values=>'| '+values.join(' | ')+' |';
  const equation=regressionEquationCopyText(result,options);
  if(equation){
    const fence='`'.repeat(Math.max(3,...[...equation.matchAll(/`+/g)].map(match=>match[0].length+1)));
    blocks.push('### '+t('Regression equation')+'\n\n'+fence+'text\n'+equation+'\n'+fence);
  }
  const dedicated=report===result.statisticsCopyReport,settings=dedicated?{...options,notation:'off',grouping:false}:options;
  for(const section of report.sections){
    const table=[line(section.columns.map(label=>markdownCell(t(label)))),line(section.columns.map(()=> '---'))];
    for(const row of section.copyRows||section.rows){
      if(equation&&section.columns[0]==='Metric'&&row[0]==='Fitted expression')continue;
      table.push(line(section.columns.map((column,index)=>{
      const value=row[index];
      const text=value&&typeof value==='object'?statisticsFormattedCopyCell(value,settings):column==='Metric'?t(String(value??'')):
        dedicated&&/^[+-]?(?:\d+(?:\.\d*)?|\.\d+)(?:e[+-]?\d+)?$/i.test(String(value))?roundNumber(String(value),options.digits??10):String(value??'');
      return markdownCell(text);
    })));}
    if(table.length>2)blocks.push('### '+markdownCell(t(section.title))+'\n\n'+table.join('\n'));
  }
  if(result.note)blocks.push(markdownCell(result.note));
  if(result.conditions?.length)blocks.push('### '+t('Conditions')+'\n\n'+result.conditions.map(condition=>'- '+markdownCell(condition)).join('\n'));
  return blocks.join('\n\n');
}
