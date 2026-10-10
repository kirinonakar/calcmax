import {element} from './app-ui.js';
import {t} from './i18n.js';
import {appendPlotExportButtons} from './svg-export.js';

const ns='http://www.w3.org/2000/svg';
const activeCharts=new Map();
globalThis.addEventListener?.('resize',()=>{for(const [view,draw] of activeCharts){if(view.isConnected)draw();else activeCharts.delete(view);}});
const number=value=>Number(value).toLocaleString(undefined,{maximumSignificantDigits:4});
const pc=(plot,index)=>['clusters','interaction'].includes(plot.kind)?(plot.features?.[index]||`Feature ${index+1}`).replace(/^Feature (\d+)$/,`${t('Feature')} $1`):`PC${index+1} (${number(100*plot.ratios[index])}%)`;

// Raw engine arrays retain every observation and all component coordinates.
export function statisticsPlotModel(plot,xAxis=0,yAxis=1){
  if(plot.kind==='intervals'){
    const rows=plot.rows.filter(row=>row.slice(1).every(Number.isFinite));
    if(!rows.length)return null;
    const bounds=rows.flatMap(row=>row.slice(1));if(Number.isFinite(plot.reference))bounds.push(plot.reference);
    const lo=Math.min(...bounds),hi=Math.max(...bounds),pad=(hi-lo||1)*.1;
    return {xmin:lo-pad,xmax:hi+pad,ymin:0,ymax:rows.length+1,intervals:rows.map(([label,estimate,lower,upper],i)=>({label,estimate,lower,upper,y:rows.length-i})),reference:plot.reference,xlabel:t('Estimate & interval'),ylabel:''};
  }
  if(plot.kind==='bars')return {xmin:.5,xmax:plot.values.length+.5,ymin:0,ymax:plot.maximum||Math.max(1e-12,...plot.values,...(plot.secondary||[]))*1.15,
    bars:plot.values.map((value,i)=>({x:i+1,y:value,label:`${plot.labels[i]}: ${number(value)}`})),secondary:plot.secondary,reference:plot.reference,xlabel:t('Observation / fold'),ylabel:t(plot.ylabel)};
  if(plot.kind==='scree'){
    let cumulative=0;
    return {xmin:.5,xmax:plot.ratios.length+.5,ymin:0,ymax:100,
      bars:plot.ratios.map((value,i)=>({x:i+1,y:100*value,label:`PC${i+1}: ${number(100*value)}% · λ=${number(plot.eigenvalues[i])}`})),
      line:plot.ratios.map((value,i)=>[i+1,100*(cumulative+=value)]),xlabel:t('Component'),ylabel:'%'};
  }
  if(['histogram','distribution'].includes(plot.kind)){
    const markers=plot.kind==='histogram'?[...plot.interval,plot.estimate]:[];
    const low=Math.min(plot.edges[0],...markers),high=Math.max(plot.edges.at(-1),...markers),padding=(high-low||1)*.025;
    return {xmin:low-padding,xmax:high+padding,ymin:0,ymax:Math.max(1,...plot.counts)*1.1,
      bars:plot.counts.map((value,i)=>({x:(plot.edges[i]+plot.edges[i+1])/2,y:value,width:plot.edges[i+1]-plot.edges[i],label:`${number(plot.edges[i])} – ${number(plot.edges[i+1])}: ${value}`})),
      markers,zero:!!plot.groupLabels&&low-padding<=0&&high+padding>=0,xlabel:t(plot.statistic||'Value'),ylabel:t(plot.kind==='distribution'?'Count':'Draws')};
  }
  const width=plot.points[0]?.length||0;
  if(!width)return null;
  xAxis=Math.min(Math.max(0,xAxis),width-1);yAxis=width>1?Math.min(Math.max(0,yAxis),width-1):-1;
  const points=plot.points.map((row,i)=>({x:row[xAxis],y:yAxis<0?0:row[yAxis],group:plot.assignments?.[i],label:plot.labels?.[i]||`${t('Observation')} ${i+1}`}));
  let xmin=Math.min(0,...points.map(p=>p.x)),xmax=Math.max(0,...points.map(p=>p.x));
  let ymin=Math.min(0,...points.map(p=>p.y)),ymax=Math.max(0,...points.map(p=>p.y));
  const dx=(xmax-xmin||1)*.14,dy=(ymax-ymin||1)*.14;
  if(plot.kind==='loadings'){xmin=ymin=-1.2;xmax=ymax=1.2;}
  else{xmin-=dx;xmax+=dx;ymin-=dy;ymax+=dy;}
  const centroids=plot.centroids?.map((row,i)=>({x:row[xAxis],y:yAxis<0?0:row[yAxis],group:i+1}));
  return {xmin,xmax,ymin,ymax,points,centroids,referenceLine:plot.referenceLine,vectors:plot.kind==='loadings',xlabel:plot.kind==='qq'?t('Theoretical normal quantiles'):pc(plot,xAxis),ylabel:plot.kind==='qq'?t('Ordered sample values'):yAxis<0?'':pc(plot,yAxis)};
}

function chart(plot,xAxis,yAxis,displayWidth){
  const model=statisticsPlotModel(plot,xAxis,yAxis);
  const height=model?.intervals?Math.max(240,model.intervals.length*32+90):340,width=Math.max(320,Math.min(620,displayWidth||(globalThis.innerWidth||720)-56));
  const svg=document.createElementNS(ns,'svg');svg.setAttribute('viewBox',`0 0 ${width} ${height}`);svg.setAttribute('role','img');
  svg.setAttribute('aria-label',t(plot.title));svg.style.cssText='width:100%;height:auto;min-width:260px;display:block;background:var(--number);border-radius:8px';
  if(!model)return svg;
  const {xmin,xmax,ymin,ymax}=model,left=model.intervals?(width<400?128:170):64,top=22,w=width-left-30,h=height-82;
  const px=x=>left+w*(x-xmin)/(xmax-xmin),py=y=>top+h-h*(y-ymin)/(ymax-ymin);
  function shape(tag,attrs,text=''){
    const node=document.createElementNS(ns,tag);for(const [key,value]of Object.entries(attrs))node.setAttribute(key,String(value));node.textContent=text;svg.append(node);return node;
  }
  const text=(x,y,label,anchor='middle')=>shape('text',{x,y,fill:'var(--muted)','font-size':12,'text-anchor':anchor},label);
  for(let i=0;i<=4;i++){
    const y=ymin+(ymax-ymin)*i/4;
    shape('line',{x1:left,x2:left+w,y1:py(y),y2:py(y),stroke:'var(--line)'});if(!model.intervals)text(left-8,py(y)+4,number(y),'end');
    if(!['scree','bars','interaction'].includes(plot.kind)){const x=xmin+(xmax-xmin)*i/4;text(px(x),top+h+20,number(x));}
  }
  const barWidth=['scree','bars'].includes(plot.kind) ? .65 : (model.bars?.[0]?.width||0);
  for(const bar of model.bars||[]){
    const node=shape('rect',{x:px(bar.x-(bar.width||barWidth)/2),y:py(bar.y),width:Math.max(.5,w*(bar.width||barWidth)/(xmax-xmin)-1),height:py(0)-py(bar.y),fill:'var(--accent)',opacity:.72});
    const title=document.createElementNS(ns,'title');title.textContent=bar.label;node.append(title);
    if(plot.kind==='scree')text(px(bar.x),top+h+20,`PC${bar.x}`);
    if(plot.kind==='bars'){
      text(px(bar.x),top+h+20,plot.labels[bar.x-1]);
      if(model.secondary){const node=shape('circle',{cx:px(bar.x),cy:py(model.secondary[bar.x-1]),r:4,fill:'var(--gold)'});const title=document.createElementNS(ns,'title');title.textContent=`${t('Raw p')}: ${number(model.secondary[bar.x-1])}`;node.append(title);}
    }
  }
  if(Number.isFinite(model.reference)){
    const vertical=!!model.intervals;shape('line',vertical?{x1:px(model.reference),x2:px(model.reference),y1:top,y2:top+h,stroke:'var(--muted)','stroke-dasharray':'5 4'}:{x1:left,x2:left+w,y1:py(model.reference),y2:py(model.reference),stroke:'var(--muted)','stroke-dasharray':'5 4'});
  }
  for(const row of model.intervals||[]){
    text(left-10,py(row.y)+4,row.label.length>21?row.label.slice(0,20)+'…':row.label,'end');
    shape('line',{x1:px(row.lower),x2:px(row.upper),y1:py(row.y),y2:py(row.y),stroke:'var(--accent)','stroke-width':3});
    for(const end of [row.lower,row.upper])shape('line',{x1:px(end),x2:px(end),y1:py(row.y)-5,y2:py(row.y)+5,stroke:'var(--accent)','stroke-width':2});
    const node=shape('circle',{cx:px(row.estimate),cy:py(row.y),r:4,fill:'var(--gold)'}),title=document.createElementNS(ns,'title');title.textContent=`${row.label}: ${number(row.estimate)} [${number(row.lower)}, ${number(row.upper)}]`;node.append(title);
  }
  if(plot.kind==='interaction'){
    plot.xLabels.forEach((label,i)=>text(px(i+1),top+h+20,label));
    plot.lineGroups.forEach((line,i)=>shape('polyline',{points:line.map(([x,y])=>`${px(x)},${py(y)}`).join(' '),fill:'none',stroke:['var(--accent)','var(--gold)','#9466b8','#437db0','#bd5c67','#598e85'][i%6],'stroke-width':2.5}));
  }
  if(model.line)shape('polyline',{points:model.line.map(([x,y])=>`${px(x)},${py(y)}`).join(' '),fill:'none',stroke:'var(--gold)','stroke-width':2.5});
  if(model.points){
    shape('line',{x1:px(0),x2:px(0),y1:top,y2:top+h,stroke:'var(--line)'});
    shape('line',{x1:left,x2:left+w,y1:py(0),y2:py(0),stroke:'var(--line)'});
    model.points.forEach((point,i)=>{
      const palette=['var(--accent)','var(--gold)','#9466b8','#437db0','#bd5c67','#598e85'];
      const color=model.vectors?palette[i%palette.length]:point.group?palette[(point.group-1)%palette.length]:'var(--accent)';
      if(model.vectors){
        shape('line',{x1:px(0),y1:py(0),x2:px(point.x),y2:py(point.y),stroke:color,'stroke-width':2});
        const angle=Math.atan2(py(point.y)-py(0),px(point.x)-px(0));
        const tip=[px(point.x),py(point.y)],a=[tip[0]-8*Math.cos(angle-.45),tip[1]-8*Math.sin(angle-.45)],b=[tip[0]-8*Math.cos(angle+.45),tip[1]-8*Math.sin(angle+.45)];
        shape('polygon',{points:[tip,a,b].map(p=>p.join(',')).join(' '),fill:color});
        text(px(point.x)+5,py(point.y)-7,String(i+1),'start');
      }
      const node=shape('circle',{cx:px(point.x),cy:py(point.y),r:model.vectors?2:3,fill:color,opacity:.8});
      const title=document.createElementNS(ns,'title');title.textContent=`${point.label}${point.group?` · ${t('Cluster')} ${point.group}`:''}: ${number(point.x)}, ${number(point.y)}`;node.append(title);
    });
  }
  for(const point of model.centroids||[])shape('path',{d:`M${px(point.x)-6},${py(point.y)} L${px(point.x)+6},${py(point.y)} M${px(point.x)},${py(point.y)-6} L${px(point.x)},${py(point.y)+6}`,stroke:'var(--ink)','stroke-width':3});
  if(model.referenceLine)shape('polyline',{points:model.referenceLine.map(([x,y])=>`${px(x)},${py(y)}`).join(' '),fill:'none',stroke:'var(--gold)','stroke-width':2});
  for(const [i,value]of (model.markers||[]).entries())shape('line',{x1:px(value),x2:px(value),y1:top,y2:top+h,stroke:i===2?'var(--gold)':'var(--ink)','stroke-width':1.8,'stroke-dasharray':i===2?'3 3':'6 4'});
  if(model.zero)shape('line',{x1:px(0),x2:px(0),y1:top,y2:top+h,stroke:'var(--muted)','stroke-width':1.4});
  text(left+w/2,height-14,model.xlabel);text(left,14,model.ylabel,'start');
  return svg;
}

export function renderStatisticsVisualizations(container,plots=[]){
  for(const view of activeCharts.keys())if(!view.isConnected)activeCharts.delete(view);
  for(const plot of plots){
    const block=element('section','','statistics-visualization');block.append(element('h4',t(plot.title)));
    const view=element('div');let x=0,y=(plot.points?.[0]?.length||0)>1?1:-1,sample=0;
    const selected=()=>plot.series?{...plot,...plot.series[sample]}:plot;
    const draw=()=>{const svg=chart(selected(),x,plot.kind==='qq'?1:y,view.clientWidth);view.replaceChildren(svg);appendPlotExportButtons(svg,`symvacas-${plot.kind}`,{captions:[{text:t(plot.title)},...(plot.series?[{text:`${plot.series[sample].label} · n=${plot.series[sample].n}`}]:[])]});};
    activeCharts.set(view,draw);
    const width=plot.points?.[0]?.length||0;
    if(plot.series){
      const label=element('label',t('Sample')),select=element('select');
      plot.series.forEach((item,i)=>{const option=element('option',`${t(item.label)} (n=${item.n})`);option.value=String(i);select.append(option);});
      select.onchange=()=>{sample=Number(select.value);draw();};label.append(select);block.append(label);
    }
    if(width>1&&!['qq','interaction'].includes(plot.kind)){
      const controls=element('div','','form-row');
      for(const axis of ['x','y']){
        const label=element('label',t(axis==='x'?'X axis':'Y axis')),select=element('select');
        for(let i=0;i<width;i++){const option=element('option',pc(plot,i));option.value=String(i);select.append(option);}
        select.value=String(axis==='x'?x:y);
        select.onchange=()=>{const value=Number(select.value);if(axis==='x'){if(value===y)y=x;x=value;}else{if(value===x)x=y;y=value;}controls.querySelectorAll('select')[0].value=String(x);controls.querySelectorAll('select')[1].value=String(y);draw();};
        label.append(select);controls.append(label);
      }
      block.append(controls);
    }
    draw();block.append(view);
    if(plot.kind==='scree')block.append(element('p',t('Bars: explained variance · line: cumulative variance'),'hint'));
    if(plot.kind==='histogram')block.append(element('p',`${number(plot.level*100)}% ${t('credible interval')}: ${plot.interval.map(number).join(' – ')} · ${t('estimate')}: ${number(plot.estimate)}`,'hint'));
    if(plot.kind==='histogram')block.append(element('p',t('Dashed: credible bounds · dotted: estimate')+(statisticsPlotModel(plot).zero?' · '+t('Solid: zero difference'):''),'hint'));
    if(plot.groupLabels)block.append(element('p',`A: ${plot.groupLabels[0]} · B: ${plot.groupLabels[1]} · ${t('Difference (B − A)')}`,'hint'));
    if(plot.kind==='loadings')block.append(element('p',plot.labels.map((label,i)=>`${i+1}: ${label}`).join(' · '),'hint'));
    if(plot.kind==='qq')block.append(element('p',t('Reference line passes through the first and third quartiles. Curvature or tail departures suggest non-normality; up to 200 ordered points are shown.'),'hint'));
    if(plot.kind==='clusters')block.append(element('p',t('Colors: cluster membership · crosses: centroids. Axes show original feature values.'),'hint'));
    if(plot.kind==='interaction'){
      const legend=element('div','','form-row');plot.lineLabels.forEach((label,i)=>{const item=element('span',`● ${label}`);item.style.color=['var(--accent)','var(--gold)','#9466b8','#437db0','#bd5c67','#598e85'][i%6];legend.append(item);});block.append(legend);
      block.append(element('p',t('Lines connect fitted cell means; nonparallel lines suggest interaction. Intervals are shown separately. Interpret with the interaction F test.'),'hint'));
    }
    if(plot.kind==='clusters'){
      const legend=element('div','','form-row');
      for(let i=0;i<plot.centroids.length;i++){const item=element('span',`● ${t('Cluster')} ${i+1}`);item.style.color=['var(--accent)','var(--gold)','#9466b8','#437db0','#bd5c67','#598e85'][i%6];legend.append(item);}block.append(legend);
    }
    if(plot.secondary)block.append(element('p',t('Bars: adjusted p · dots: raw p · dashed line: α'),'hint'));
    container.append(block);
  }
}
