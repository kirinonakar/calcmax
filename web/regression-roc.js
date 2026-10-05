import {element} from './app-ui.js';
import {t} from './i18n.js';
import {roundNumber} from './display-format.js';

export function regressionROC(report,digits=10,{oob=false}={}){
  const points=(report.roc||[]).map(point=>point.map(Number)).filter(point=>point.length===2&&point.every(n=>Number.isFinite(n)&&n>=0&&n<=1));
  if(points.length<2)return null;
  const figure=element('figure');figure.style.margin='12px 0';
  const title=oob?'OOB ROC curve':'ROC curve';
  figure.append(element('figcaption',`${t(title)} · AUC=${roundNumber(String(report.auc),digits)}`),element('p',t(oob?'ROC/AUC uses OOB predictions; positive class = 1.':'ROC/AUC uses fitted data; positive class = 1.')));
  const ns='http://www.w3.org/2000/svg',svg=document.createElementNS(ns,'svg');
  svg.setAttribute('viewBox','0 0 360 360');svg.setAttribute('role','img');svg.setAttribute('aria-label',t(title));svg.dataset.roc=oob?'oob':'true';svg.style.width='100%';svg.style.maxWidth='360px';svg.style.height='auto';
  const draw=(tag,attributes,label='')=>{const node=document.createElementNS(ns,tag);for(const [key,value] of Object.entries(attributes))node.setAttribute(key,String(value));node.textContent=label;svg.append(node);return node;};
  const x=value=>52+288*value,y=value=>306-288*value;
  for(const value of [0,.25,.5,.75,1]){
    draw('line',{x1:x(value),x2:x(value),y1:y(0),y2:y(1),stroke:'var(--line)'});
    draw('line',{x1:x(0),x2:x(1),y1:y(value),y2:y(value),stroke:'var(--line)'});
    draw('text',{x:x(value),y:323,'text-anchor':'middle','font-size':11,fill:'var(--muted)'},String(value));
    draw('text',{x:45,y:y(value)+4,'text-anchor':'end','font-size':11,fill:'var(--muted)'},String(value));
  }
  draw('line',{x1:x(0),y1:y(0),x2:x(1),y2:y(1),stroke:'var(--muted)','stroke-dasharray':'5 5','data-roc-chance':'true'});
  draw('path',{d:points.map(([fpr,tpr],i)=>`${i?'L':'M'}${x(fpr)},${y(tpr)}`).join(' '),stroke:'var(--accent)','stroke-width':2.5,fill:'none','data-roc-curve':'true'});
  draw('text',{x:196,y:351,'text-anchor':'middle','font-size':12,fill:'var(--ink)'},t('False positive rate (FPR)'));
  draw('text',{x:0,y:0,transform:'translate(14 162) rotate(-90)','text-anchor':'middle','font-size':12,fill:'var(--ink)'},t('Sensitivity (TPR)'));
  figure.append(svg);return figure;
}
