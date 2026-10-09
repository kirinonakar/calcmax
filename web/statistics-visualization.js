import {element} from './app-ui.js';
import {t} from './i18n.js';

const ns='http://www.w3.org/2000/svg';
const number=value=>Number(value).toLocaleString(undefined,{maximumSignificantDigits:4});
const pc=(plot,index)=>`PC${index+1} (${number(100*plot.ratios[index])}%)`;

// Raw engine arrays retain every observation and all component coordinates.
export function statisticsPlotModel(plot,xAxis=0,yAxis=1){
  if(plot.kind==='scree'){
    let cumulative=0;
    return {xmin:.5,xmax:plot.ratios.length+.5,ymin:0,ymax:100,
      bars:plot.ratios.map((value,i)=>({x:i+1,y:100*value,label:`PC${i+1}: ${number(100*value)}% · λ=${number(plot.eigenvalues[i])}`})),
      line:plot.ratios.map((value,i)=>[i+1,100*(cumulative+=value)]),xlabel:t('Component'),ylabel:'%'};
  }
  if(plot.kind==='histogram'){
    const low=Math.min(plot.edges[0],plot.estimate,...plot.interval),high=Math.max(plot.edges.at(-1),plot.estimate,...plot.interval),padding=(high-low)*.025;
    return {xmin:low-padding,xmax:high+padding,ymin:0,ymax:Math.max(1,...plot.counts)*1.1,
      bars:plot.counts.map((value,i)=>({x:(plot.edges[i]+plot.edges[i+1])/2,y:value,width:plot.edges[i+1]-plot.edges[i],label:`${number(plot.edges[i])} – ${number(plot.edges[i+1])}: ${value}`})),
      markers:[...plot.interval,plot.estimate],zero:!!plot.groupLabels&&low-padding<=0&&high+padding>=0,xlabel:t(plot.statistic),ylabel:t('Draws')};
  }
  const width=plot.points[0]?.length||0;
  if(!width)return null;
  xAxis=Math.min(Math.max(0,xAxis),width-1);yAxis=width>1?Math.min(Math.max(0,yAxis),width-1):-1;
  const points=plot.points.map((row,i)=>({x:row[xAxis],y:yAxis<0?0:row[yAxis],label:plot.labels?.[i]||`${t('Observation')} ${i+1}`}));
  let xmin=Math.min(0,...points.map(p=>p.x)),xmax=Math.max(0,...points.map(p=>p.x));
  let ymin=Math.min(0,...points.map(p=>p.y)),ymax=Math.max(0,...points.map(p=>p.y));
  const dx=(xmax-xmin||1)*.14,dy=(ymax-ymin||1)*.14;
  if(plot.kind==='loadings'){xmin=ymin=-1.2;xmax=ymax=1.2;}
  else{xmin-=dx;xmax+=dx;ymin-=dy;ymax+=dy;}
  return {xmin,xmax,ymin,ymax,points,vectors:plot.kind==='loadings',xlabel:pc(plot,xAxis),ylabel:yAxis<0?'':pc(plot,yAxis)};
}

function chart(plot,xAxis,yAxis){
  const model=statisticsPlotModel(plot,xAxis,yAxis);
  const svg=document.createElementNS(ns,'svg');svg.setAttribute('viewBox','0 0 620 340');svg.setAttribute('role','img');
  svg.setAttribute('aria-label',t(plot.title));svg.style.cssText='width:100%;height:auto;min-width:260px;display:block;background:var(--number);border-radius:8px';
  if(!model)return svg;
  const {xmin,xmax,ymin,ymax}=model,left=64,top=22,w=526,h=258;
  const px=x=>left+w*(x-xmin)/(xmax-xmin),py=y=>top+h-h*(y-ymin)/(ymax-ymin);
  function shape(tag,attrs,text=''){
    const node=document.createElementNS(ns,tag);for(const [key,value]of Object.entries(attrs))node.setAttribute(key,String(value));node.textContent=text;svg.append(node);return node;
  }
  const text=(x,y,label,anchor='middle')=>shape('text',{x,y,fill:'var(--muted)','font-size':12,'text-anchor':anchor},label);
  for(let i=0;i<=4;i++){
    const y=ymin+(ymax-ymin)*i/4;
    shape('line',{x1:left,x2:left+w,y1:py(y),y2:py(y),stroke:'var(--line)'});text(left-8,py(y)+4,number(y),'end');
    if(plot.kind!=='scree'){const x=xmin+(xmax-xmin)*i/4;text(px(x),top+h+20,number(x));}
  }
  const barWidth=plot.kind==='scree' ? .65 : (model.bars?.[0]?.width||0);
  for(const bar of model.bars||[]){
    const node=shape('rect',{x:px(bar.x-(bar.width||barWidth)/2),y:py(bar.y),width:Math.max(.5,w*(bar.width||barWidth)/(xmax-xmin)-1),height:py(0)-py(bar.y),fill:'var(--accent)',opacity:.72});
    const title=document.createElementNS(ns,'title');title.textContent=bar.label;node.append(title);
    if(plot.kind==='scree')text(px(bar.x),top+h+20,`PC${bar.x}`);
  }
  if(model.line)shape('polyline',{points:model.line.map(([x,y])=>`${px(x)},${py(y)}`).join(' '),fill:'none',stroke:'var(--gold)','stroke-width':2.5});
  if(model.points){
    shape('line',{x1:px(0),x2:px(0),y1:top,y2:top+h,stroke:'var(--line)'});
    shape('line',{x1:left,x2:left+w,y1:py(0),y2:py(0),stroke:'var(--line)'});
    model.points.forEach((point,i)=>{
      const color=model.vectors?['var(--accent)','var(--gold)','#9466b8','#437db0'][i%4]:'var(--accent)';
      if(model.vectors){
        shape('line',{x1:px(0),y1:py(0),x2:px(point.x),y2:py(point.y),stroke:color,'stroke-width':2});
        const angle=Math.atan2(py(point.y)-py(0),px(point.x)-px(0));
        const tip=[px(point.x),py(point.y)],a=[tip[0]-8*Math.cos(angle-.45),tip[1]-8*Math.sin(angle-.45)],b=[tip[0]-8*Math.cos(angle+.45),tip[1]-8*Math.sin(angle+.45)];
        shape('polygon',{points:[tip,a,b].map(p=>p.join(',')).join(' '),fill:color});
        text(px(point.x)+5,py(point.y)-7,String(i+1),'start');
      }
      const node=shape('circle',{cx:px(point.x),cy:py(point.y),r:model.vectors?2:3,fill:color,opacity:.8});
      const title=document.createElementNS(ns,'title');title.textContent=`${point.label}: ${number(point.x)}, ${number(point.y)}`;node.append(title);
    });
  }
  for(const [i,value]of (model.markers||[]).entries())shape('line',{x1:px(value),x2:px(value),y1:top,y2:top+h,stroke:i===2?'var(--gold)':'var(--ink)','stroke-width':1.8,'stroke-dasharray':i===2?'3 3':'6 4'});
  if(model.zero)shape('line',{x1:px(0),x2:px(0),y1:top,y2:top+h,stroke:'var(--muted)','stroke-width':1.4});
  text(left+w/2,326,model.xlabel);text(left,14,model.ylabel,'start');
  return svg;
}

export function renderStatisticsVisualizations(container,plots=[]){
  for(const plot of plots){
    const block=element('section','','statistics-visualization');block.append(element('h4',t(plot.title)));
    const view=element('div');let x=0,y=(plot.points?.[0]?.length||0)>1?1:-1;
    const draw=()=>view.replaceChildren(chart(plot,x,y));
    const width=plot.points?.[0]?.length||0;
    if(width>1){
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
    container.append(block);
  }
}
