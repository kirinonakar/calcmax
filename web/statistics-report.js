import {advancedStatisticsSchema} from './advanced-statistics-schema.js';
import {element,control} from './app-ui.js';
import {t} from './i18n.js';
import {resultDisplayTree,resultMathDisplay} from './result-display.js';
import {renderStatisticsVisualizations} from './statistics-visualization.js';

const basicAnalyses=new Set('mean median variance stdev sumdata quartiles stats covariance correlation ttest ttest2 ttestpaired ztest ztest2 chi2test chi2independence fisherexact anova welchanova tukey gameshowell shapiro wilcoxon mannwhitney kruskal tinterval zinterval'.split(' '));
export function statisticsReportTarget(result,source='',requested=''){
  if(!result?.statisticsReport)return '';
  if(['statistics-summary-result','statistics-analysis-result','statistics-advanced-result','statistics-tests-result','statistics-general-result','statistics-models-result','statistics-preparation-result'].includes(requested))return requested;
  const analysis=result.statisticsReport.analysis||source.split('(')[0].trim();
  const definition=advancedStatisticsSchema.find(item=>item.id===analysis);
  return !definition&&basicAnalyses.has(analysis)?'statistics-analysis-result':`statistics-${definition?.section||'advanced'}-result`;
}

export function renderStatisticsReport(container,report,{digits=10,onCopy,onClear,...options}={}){
  container.replaceChildren();
  const panel=element('div','','statistics-result-report');
  const heading=element('div','','statistics-result-heading');heading.append(element('h3',t(report.title)));
  if(onCopy)heading.append(control('Copy',onCopy));
  if(onClear)heading.append(control('Clear',onClear));panel.append(heading);
  if(report.highlights?.length){
    const cards=element('div','','statistics-highlights');
    for(const item of report.highlights){const card=element('div','','statistics-highlight');card.append(element('span',t(item.label)),resultMathDisplay(resultDisplayTree(item.value,{decimal:true,mixed:false}),digits,true,options));cards.append(card);}
    panel.append(cards);
  }
  if(report.notes?.length){const notes=element('details','','statistics-result-details');notes.append(element('summary',t('Interpretation & assumptions')));for(const note of report.notes)notes.append(element('p',t(note),'statistics-interpretation'));panel.append(notes);}
  const primary=['Summary','ANOVA','Overall model','Overall ANOVA','Overall Welch ANOVA',report.title];
  const prominent=section=>primary.includes(section.title)||['Summary','Sample summaries','Assumption checks','Effect size','Mean confidence interval (95%, two-sided)','Overall ANOVA','Expected-count diagnostics'].includes(section.title);
  const sections=[...report.sections.filter(prominent).sort((a,b)=>Number(!primary.includes(a.title))-Number(!primary.includes(b.title))),...report.sections.filter(section=>!prominent(section))];
  let plotsShown=false;
  for(const section of sections){
    if(!prominent(section)&&!plotsShown){renderStatisticsVisualizations(panel,report.plots);plotsShown=true;}
    const collapsible=section.totalRows>12&&!prominent(section),block=element(collapsible?'details':'section');
    if(collapsible){block.className='statistics-result-details';block.append(element('summary',`${t(section.title)} (${section.totalRows})`));}
    const scroll=element('div','','statistics-result-scroll');
    scroll.tabIndex=0;scroll.setAttribute('role','region');scroll.setAttribute('aria-label',t(section.title));
    const table=element('table');
    table.append(element('caption',t(section.title)));
    const head=element('thead'),header=element('tr');
    for(const label of section.columns){const th=element('th',t(label));th.scope='col';header.append(th);}
    head.append(header);table.append(head);
    const body=element('tbody');
    for(const values of section.rows){
      const row=element('tr');
      for(const [index,value] of values.entries()){
        const td=element('td');
        if(value&&typeof value==='object')td.append(resultMathDisplay(resultDisplayTree(value,{decimal:true,mixed:false}),digits,true,options));
        else td.textContent=['Metric','Check','Interpretation','Sample'].includes(section.columns[index])?t(String(value)):String(value);
        row.append(td);
      }
      body.append(row);
    }
    table.append(body);scroll.append(table);block.append(scroll);
    if(section.totalRows>section.rows.length)block.append(element('p',`${section.rows.length} / ${section.totalRows} · ${t('Copy result includes all rows.')}`,'hint'));
    panel.append(block);
  }
  if(!plotsShown)renderStatisticsVisualizations(panel,report.plots);
  container.append(panel);
}
