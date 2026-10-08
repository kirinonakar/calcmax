import {element,control} from './app-ui.js';
import {t} from './i18n.js';
import {resultDisplayTree,resultMathDisplay} from './result-display.js';

const basicAnalyses=new Set('mean median variance stdev sumdata quartiles stats covariance correlation ttest ttest2 ttestpaired ztest ztest2 chi2test chi2independence fisherexact anova tukey shapiro wilcoxon mannwhitney kruskal tinterval zinterval'.split(' '));
export function statisticsReportTarget(result,source='',requested=''){
  if(!result?.statisticsReport)return '';
  if(['statistics-analysis-result','statistics-advanced-result'].includes(requested))return requested;
  const analysis=result.statisticsReport.analysis||source.split('(')[0].trim();
  return basicAnalyses.has(analysis)?'statistics-analysis-result':'statistics-advanced-result';
}

export function renderStatisticsReport(container,report,{digits=10,onCopy,...options}={}){
  container.replaceChildren();
  const panel=element('div','','statistics-result-report');
  const heading=element('div','','statistics-result-heading');heading.append(element('h3',t(report.title)));
  if(onCopy)heading.append(control('Copy result',onCopy));panel.append(heading);
  for(const section of report.sections){
    const block=element('section');
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
        else td.textContent=section.columns[index]==='Metric'?t(String(value)):String(value);
        row.append(td);
      }
      body.append(row);
    }
    table.append(body);scroll.append(table);block.append(scroll);
    if(section.totalRows>section.rows.length)block.append(element('p',`${section.rows.length} / ${section.totalRows} · ${t('Copy result includes all rows.')}`,'hint'));
    panel.append(block);
  }
  container.append(panel);
}
