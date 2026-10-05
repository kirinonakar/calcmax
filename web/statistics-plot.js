import {plot,dataBounds} from './plot.js';
import {numericStatisticsRows,statisticsColumnNames} from './workspace-commands.js';
import {displayNumber} from './display-format.js';
export function statisticsPlotSeries(rows,{grouping='columns',columnCount=rows[0]?.length||1}={}){
  const names=statisticsColumnNames(columnCount);
  const number=cell=>{const text=String(cell??'').trim();if(!text)return null;const normalized=/^[+-]?\d{1,3}(?:,\d{3})+(?:\.\d+)?(?:[eE][+-]?\d+)?$/.test(text)?text.replace(/,/g,''):text;const n=Number(normalized);return Number.isFinite(n)?n:null;};
  if(!['first','last'].includes(grouping)||columnCount<2){
    const numeric=numericStatisticsRows(rows);
    return names.map((label,i)=>({label,values:numeric.map(row=>number(row[i])).filter(n=>n!==null)}));
  }
  const groupColumn=grouping==='first'?0:columnCount-1,groups=new Map();
  for(const row of rows){
    const group=String(row[groupColumn]??'').trim();if(!group)continue;
    if(!groups.has(group))groups.set(group,names.map(()=>[]));
    row.slice(0,columnCount).forEach((cell,i)=>{const n=number(cell);if(i!==groupColumn&&n!==null)groups.get(group)[i].push(n);});
  }
  return [...groups].flatMap(([group,samples])=>names.flatMap((name,i)=>i===groupColumn?[]:[{label:columnCount===2?group:`${group} · ${name}`,values:samples[i]}]));
}

export function statisticsPlotPanels(rows,{grouping='columns',columnCount=rows[0]?.length||1}={}){
  if(!['first','last'].includes(grouping)||columnCount<2)return [{label:'',series:statisticsPlotSeries(rows,{columnCount})}];
  const groupColumn=grouping==='first'?0:columnCount-1;
  return statisticsColumnNames(columnCount).flatMap((label,column)=>column===groupColumn?[]:[{
    label,series:statisticsPlotSeries(rows.map(row=>[row[groupColumn]||'',row[column]||'']),{grouping:'first',columnCount:2})
  }]);
}

export function statisticsPlot(container,rows,{type='scatter',digits=10,curve=[],xAxisLabel='x',yAxisLabel='y',series=statisticsPlotSeries(rows)}={}){
  const columns=series.map(entry=>entry.values);
  if(type==='scatter'){
    const points=rows.filter(r=>r[0]!==''&&r[1]!==''&&r.length>1).map(r=>r.slice(0,2).map(Number)).filter(p=>p.every(Number.isFinite));
    if(!points.length){container.replaceChildren();return;}
    plot(container,{curves:[points,...(curve.length?[curve]:[])]},dataBounds(points),{scatterCurves:[0],digits,xAxisLabel,yAxisLabel});return;
  }
  const chartHeight=type==='box'?Math.max(400,columns.length*85+110):400;
  const NS='http://www.w3.org/2000/svg',svg=document.createElementNS(NS,'svg');svg.setAttribute('viewBox',`0 0 800 ${chartHeight}`);svg.setAttribute('role','img');svg.setAttribute('aria-label',type==='box'?'Box plot':'Histogram');
  const colors=['var(--accent)','#a04c75','#3b70bd'],node=(tag,attrs,text='')=>{const el=document.createElementNS(NS,tag);for(const [name,value] of Object.entries(attrs))el.setAttribute(name,String(value));el.textContent=text;svg.append(el);return el;};
  const all=columns.flat();if(!all.length){container.replaceChildren();return;}
  const minimum=Math.min(...all),maximum=Math.max(...all),span=maximum-minimum||1,x=n=>60+(n-minimum)/span*680;
  node('line',{x1:60,x2:740,y1:chartHeight-60,y2:chartHeight-60,stroke:'var(--muted)'});
  for(let i=0;i<=4;i++)node('text',{x:60+i*170,y:chartHeight-30,fill:'var(--muted)','text-anchor':'middle','font-size':14},displayNumber(minimum+span*i/4,digits));
  if(type==='histogram'){
    const bins=Math.min(16,Math.max(1,Math.ceil(Math.sqrt(all.length)))),counts=columns.map(values=>{const result=Array(bins).fill(0);for(const n of values)result[Math.min(bins-1,Math.floor((n-minimum)/span*bins))]++;return result;}),peak=Math.max(...counts.flat(),1);
    for(let index=0;index<counts.length;index++)for(let i=0;i<bins;i++){const width=680/bins/counts.length,count=counts[index][i],height=280*count/peak;node('rect',{x:60+i*680/bins+index*width,y:340-height,width:Math.max(1,width-2),height,fill:colors[index%colors.length],opacity:.7,'data-bin':i,'data-series':series[index].label});node('text',{x:60+i*680/bins+(index+.5)*width,y:334-height,fill:'var(--ink)','text-anchor':'middle','font-size':11},String(count));}
  }else{
    columns.forEach((values,i)=>{if(!values.length)return;values=[...values].sort((a,b)=>a-b);const q=p=>{const index=(values.length-1)*p,lower=Math.floor(index);return values[lower]+(values[Math.ceil(index)]-values[lower])*(index-lower);},low=values[0],q1=q(.25),median=q(.5),q3=q(.75),high=values.at(-1),y=70+i*85;
      node('line',{x1:x(low),x2:x(high),y1:y,y2:y,stroke:colors[i%colors.length],'stroke-width':2});node('rect',{x:x(q1),y:y-20,width:Math.max(1,x(q3)-x(q1)),height:40,fill:colors[i%colors.length],opacity:.3});node('line',{x1:x(median),x2:x(median),y1:y-20,y2:y+20,stroke:colors[i%colors.length],'stroke-width':3});for(const at of [low,high])node('line',{x1:x(at),x2:x(at),y1:y-12,y2:y+12,stroke:colors[i%colors.length]});node('text',{x:20,y:y+5,fill:'var(--ink)'},series[i].label);node('text',{x:x(median),y:y+42,fill:'var(--ink)','text-anchor':'middle','font-size':12},displayNumber(median,digits));
    });
  }
  container.replaceChildren(svg);
  if(type==='histogram'){const legend=document.createElement('div');legend.className='statistics-plot-legend';series.forEach((entry,i)=>{const label=document.createElement('span');label.textContent=`${entry.label} (n=${entry.values.length})`;label.style.color=colors[i%colors.length];legend.append(label);});container.prepend(legend);}
}
