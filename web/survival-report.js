import {element} from './app-ui.js';
import {getLanguage,t} from './i18n.js';

const colors=['var(--accent)','#cf7131','#8254b6','#397ec6','#bf547f','#86962e'];
export function survivalNumber(n,digits=5){return n===null||n===undefined||!Number.isFinite(Number(n))?'—':Number(Number(n).toPrecision(Math.min(5,Math.max(2,digits)))).toString();}
export function survivalStepPoints(curve,index){
  const points=[[0,1]];let previous=1;
  for(const row of curve){points.push([row[0],previous],[row[0],row[index]]);previous=row[index];}
  return points;
}

export function renderSurvivalReport(container,report,{groups=[],predictors=[],band=true,digits=5}={}){
  const ko=getLanguage()==='ko',text=(en,kr)=>ko?kr:en;
  const number=n=>survivalNumber(n,digits);
  const groupName=(group,i)=>groups[i]||(report.groups.length>1?text('Group ','그룹 ')+number(group.id):text('All subjects','전체'));
  const names=report.groups.map(groupName);
  container.hidden=false;container.replaceChildren(element('h3','Kaplan–Meier'));
  const legend=element('div','','survival-legend');
  names.forEach((name,i)=>{const item=element('span',`${name} · n=${report.groups[i].n}`);item.style.color=colors[i%colors.length];legend.append(item);});
  container.append(legend);
  const NS='http://www.w3.org/2000/svg',svg=document.createElementNS(NS,'svg');
  const width=Math.max(300,container.clientWidth?container.clientWidth-26:720);
  svg.setAttribute('viewBox',`0 0 ${width} 350`);svg.setAttribute('role','img');svg.setAttribute('aria-label',text('Kaplan–Meier survival curves with 95% confidence intervals','Kaplan–Meier 생존곡선과 95% 신뢰구간'));
  const add=(tag,attrs,label='')=>{const node=document.createElementNS(NS,tag);for(const [key,value] of Object.entries(attrs))node.setAttribute(key,String(value));node.textContent=label;svg.append(node);return node;};
  const maxTime=Math.max(1,...report.groups.map(g=>g.curve.at(-1)?.[0]||0)),x=t=>50+t/maxTime*(width-72),y=s=>285-s*255;
  for(let i=0;i<=4;i++){
    add('line',{x1:50,x2:width-22,y1:y(i/4),y2:y(i/4),stroke:'var(--line)'});
    add('text',{x:40,y:y(i/4)+4,'text-anchor':'end',fill:'var(--muted)','font-size':12},`${i*25}%`);
    add('text',{x:x(maxTime*i/4),y:306,'text-anchor':'middle',fill:'var(--muted)','font-size':12},number(maxTime*i/4));
  }
  add('text',{x:60,y:17,fill:'var(--muted)','font-size':12},text('Survival probability','생존확률'));
  add('text',{x:width/2,y:334,'text-anchor':'middle',fill:'var(--muted)','font-size':12},text('Time','시간'));
  const path=points=>points.map(([t,s],i)=>`${i?'L':'M'}${x(t)} ${y(s)}`).join(' ');
  report.groups.forEach((group,i)=>{
    const color=colors[i%colors.length];
    if(band)add('path',{d:path([...survivalStepPoints(group.curve,6),...survivalStepPoints(group.curve,5).reverse()])+' Z',fill:color,opacity:.13,'data-ci-band':i});
    add('path',{d:path(survivalStepPoints(group.curve,4)),fill:'none',stroke:color,'stroke-width':2.5,'data-survival-curve':i});
    for(const row of group.curve)if(row[3]){
      const marker=add('path',{d:`M${x(row[0])-4} ${y(row[4])}h8 M${x(row[0])} ${y(row[4])-4}v8`,stroke:color,'stroke-width':1.5,'data-censored':row[3]});
      const title=document.createElementNS(NS,'title');title.textContent=`${names[i]} · ${text('censored','중도절단')} ${row[3]} · t=${number(row[0])}`;marker.append(title);
    }
  });
  container.append(svg,element('p',text('Shading: pointwise 95% CI · + censored','음영: 시점별 95% 신뢰구간 · + 중도절단'),'hint'));
  const table=(headers,rows)=>{const scroll=element('div','','survival-table-scroll'),table=element('table'),head=element('thead'),header=element('tr'),body=element('tbody');headers.forEach(label=>{const cell=element('th',label);cell.scope='col';header.append(cell);});head.append(header);rows.forEach(values=>{const row=element('tr');values.forEach(value=>row.append(element('td',String(value))));body.append(row);});table.append(head,body);scroll.append(table);container.append(scroll);};
  table([text('Group','그룹'),'n',text('Events','사건'),text('Median survival','중앙 생존시간')],report.groups.map((g,i)=>[names[i],g.n,g.events,g.median===null?text('Not reached','미도달'):number(g.median)]));
  container.append(element('h3',text('Log-rank test','Log-rank 검정')));
  const lr=report.logrank;
  container.append(element('p',lr?.error?`${text('Unavailable','계산 불가')} · ${t(lr.error)}`:lr?`χ²=${number(lr.chi2)} · df=${lr.df} · p=${number(lr.p)}`:text('Choose at least two groups','비교할 그룹이 2개 이상 필요합니다.'),'hint'));
  if(report.cox){
    container.append(element('h3',text('Cox proportional hazards','Cox 비례위험')));
    if(report.cox.error)container.append(element('p',`${text('Unavailable','계산 불가')} · ${t(report.cox.error)}`,'hint'));
    else{
      const termName=term=>term.startsWith('group:')?`${names[Number(term.split(':')[1])]} / ${names[0]}`:predictors[Number(term.split(':')[1])]||term;
      table([text('Term','변수'),'HR','95% CI','p'],report.cox.coefficients.map(row=>[termName(row.term),number(row.HR),row['HR CI95']?row['HR CI95'].map(number).join(' – '):'—',number(row.p)]));
      const phP=report.cox['PH test p'];
      const summary=[text('Reference: first group','기준: 첫 그룹'),`${report.ties==='breslow'?'Breslow':'Efron'} ${text('ties','동률 처리')}`,...(report.truncation?[text('left truncation','좌측 절단')]:[]),...(report.ph?(phP===undefined?[text('PH test unavailable','PH 검정 계산 불가')]:[`PH χ²=${number(report.cox['PH test chi2'])} · p=${number(phP)}`]):[text('PH test off','PH 검정 생략')])];
      container.append(element('p',summary.join(' · '),'hint'));
    }
  }
}
