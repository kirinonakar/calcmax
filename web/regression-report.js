import {element} from './app-ui.js';
import {t} from './i18n.js';
import {roundNumber} from './display-format.js';
import {regressionROC} from './regression-roc.js';

export function regressionParameterLabels(mode,columns,responseColumn){
  const predictors=columns.filter((_,i)=>i!==responseColumn);if(!predictors.length)return {};
  if(['multiple','logistic'].includes(mode))return {b0:'Intercept',...Object.fromEntries(predictors.map((name,i)=>[`b${i+1}`,name]))};
  if(['linear','quadratic','polynomial'].includes(mode))return {b0:'Intercept',...Object.fromEntries(Array.from({length:10},(_,i)=>[`b${i+1}`,predictors[0]+(i?String(i+1).replace(/\d/g,digit=>'⁰¹²³⁴⁵⁶⁷⁸⁹'[Number(digit)]):'')]))};
  return {};
}
export function regressionParameterName(name,labels={}){return labels[name]==='Intercept'?t('Intercept'):labels[name]||name;}

export function renderRegressionReport(container,report,digits=10,parameterLabels={}) {
  container.replaceChildren();
  if(!report)return;
  const number=value=>value===null||value===undefined?'—':roundNumber(String(value),digits);
  const summary=element('p');
  const metrics=[['R²','rSquared'],['Adjusted R²','adjustedRSquared'],['RMSE','rmse'],['Residual SE','residualSE'],['C-statistic (AUC)','auc'],['McFadden R²','pseudoRSquared'],['Deviance','deviance'],['AIC','aic'],['LR p','likelihoodP'],['Durbin–Watson','durbinWatson'],['Residual Shapiro p','shapiroP']];
  summary.textContent=`n=${report.n} · df=${report.df} · `+metrics.filter(([,key])=>report[key]!=null||['rSquared','adjustedRSquared'].includes(key)&&key in report).map(([name,key])=>`${t(name)}=${number(report[key])}`).join(' · ');
  container.append(summary);
  const note=report.fitScale==='binomial'?'Binomial MLE; Wald intervals.':report.fitScale==='log(y)'?'Inference in log(y); R² and RMSE in original y units.':report.approximate?'Local Jacobian approximation; independent errors with constant variance.':'OLS inference; independent errors with constant variance.';
  container.append(element('p',t(note)));
  for(const warning of report.warnings||[])container.append(element('p',t(warning)));
  const wrapper=element('div');wrapper.style.overflowX='auto';
  const table=element('table');table.setAttribute('aria-label',t('Coefficient inference'));
  const hasVif=(report.coefficients||[]).some(c=>c.vif!=null);
  const head=element('tr');for(const label of ['Parameter','Estimate','SE','95% CI','p',...(hasVif?['VIF']:[]),...(report.fitScale==='binomial'?['Odds ratio','OR 95% CI']:[])])head.append(element('th',t(label)));
  const thead=element('thead');thead.append(head);table.append(thead);
  const body=element('tbody');
  for(const c of report.coefficients||[]){const row=element('tr');for(const label of [regressionParameterName(c.name,parameterLabels),number(c.estimate),number(c.se),`${number(c.low)} … ${number(c.high)}`,number(c.p),...(hasVif?[number(c.vif)]:[]),...(report.fitScale==='binomial'?[number(c.oddsRatio),`${number(c.oddsLow)} … ${number(c.oddsHigh)}`]:[])])row.append(element('td',label));body.append(row);}
  table.append(body);wrapper.append(table);container.append(wrapper);
  if(report.roc){const roc=regressionROC(report,digits);if(roc)container.append(roc);}
  const details=element('details'),label=element('summary',t('Residual diagnostics'));details.append(label);
  details.append(element('p',t(report.fitScale==='binomial'?'Deviance residual vs fitted probability.':'Residual vs fitted; Durbin–Watson uses input row order.')));
  const binomial=report.fitScale==='binomial';
  const rows=report.residuals||[],points=rows.map(r=>[Number(r.fitted),Number(binomial?r.deviance:r.residual),r]).filter(p=>p.slice(0,2).every(Number.isFinite));
  if(points.length){
    const ns='http://www.w3.org/2000/svg',svg=document.createElementNS(ns,'svg');svg.setAttribute('viewBox','0 0 600 200');svg.setAttribute('role','img');svg.setAttribute('aria-label',t('Residual vs fitted'));svg.style.width='100%';
    const min=Math.min(...points.map(p=>p[0])),max=Math.max(...points.map(p=>p[0])),range=Math.max(...points.map(p=>Math.abs(p[1])))||1;
    const zero=document.createElementNS(ns,'line');for(const [key,v] of Object.entries({x1:30,x2:580,y1:90,y2:90,stroke:'currentColor'}))zero.setAttribute(key,String(v));svg.append(zero);
    for(const [x,y,r] of points){const dot=document.createElementNS(ns,'circle');for(const [key,v] of Object.entries({cx:30+550*(x-min)/(max-min||1),cy:90-75*y/range,r:2.5,fill:Math.abs(Number(r.standardized))>3?'#da5545':'currentColor'}))dot.setAttribute(key,String(v));const title=document.createElementNS(ns,'title');title.textContent=`${r.row}: ${number(binomial?r.deviance:r.residual)}`;dot.append(title);svg.append(dot);}
    const axis=document.createElementNS(ns,'text');axis.setAttribute('x','300');axis.setAttribute('y','195');axis.setAttribute('text-anchor','middle');axis.setAttribute('fill','currentColor');axis.textContent=t('Fitted value');svg.append(axis);details.append(svg);
  }
  const residualWrapper=element('div');residualWrapper.style.overflowX='auto';
  const labels=binomial?['Observation','Observed','Fitted value','Residual','Pearson residual','Deviance residual']:['Observation','Observed','Fitted value','Residual','Standardized','Leverage',"Cook's D"];
  const residualTable=element('table'),residualHead=element('tr');for(const label of labels)residualHead.append(element('th',t(label)));residualTable.append(residualHead);
  for(const r of rows.slice(0,100)){const row=element('tr');for(const v of [r.row,r.observed,r.fitted,r.residual,r.standardized,...(binomial?[r.deviance]:[r.leverage,r.cook])])row.append(element('td',number(v)));residualTable.append(row);}
  residualWrapper.append(residualTable);details.append(residualWrapper);
  if(rows.length>100)details.append(element('p',t('Showing first 100 rows; export includes all rows.')));
  container.append(details);
}

export function regressionResidualCSV(report){
  const keys=['row','observed','fitted','residual','standardized','leverage','cook','deviance'];
  return [keys.join(','),...(report?.residuals||[]).map(row=>keys.map(key=>row[key]??'').join(','))].join('\n');
}
