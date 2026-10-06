import {plot,dataBounds} from './plot.js';
import {numericStatisticsRows,statisticsColumnNames} from './workspace-commands.js';
import {displayNumber} from './display-format.js';
import {t} from './i18n.js';
import {statisticsPlotNumber,violinDensity,beeswarmLayout,statisticsHeatMapData,statisticsCorrelationHeatMap,heatMapColor} from './statistics-plot-data.js';
import {clusteredHeatMap} from './statistics-cluster.js';
export function statisticsPlotSeries(rows,{grouping='columns',columnCount=rows[0]?.length||1}={}){
  const names=statisticsColumnNames(columnCount);
  const number=statisticsPlotNumber;
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

export function statisticsPlot(container,rows,{type='scatter',digits=10,curve=[],xAxisLabel='x',yAxisLabel='y',series=statisticsPlotSeries(rows),heatMap,orientation='horizontal'}={}){
  if(['heatmap','correlationheatmap','clusteredheatmap'].includes(type)){drawHeatMap(container,heatMap||(type==='correlationheatmap'?statisticsCorrelationHeatMap(rows):type==='clusteredheatmap'?clusteredHeatMap(statisticsHeatMapData(rows)):statisticsHeatMapData(rows)),digits);return;}
  if(type==='violin'||type==='box'){drawDistribution(container,series,digits,type,orientation);return;}
  const columns=series.map(entry=>entry.values);
  if(type==='scatter'){
    const points=rows.filter(r=>r[0]!==''&&r[1]!==''&&r.length>1).map(r=>r.slice(0,2).map(Number)).filter(p=>p.every(Number.isFinite));
    if(!points.length){container.replaceChildren();return;}
    plot(container,{curves:[points,...(curve.length?[curve]:[])]},dataBounds(points),{scatterCurves:[0],digits,xAxisLabel,yAxisLabel});return;
  }
  const chartHeight=400;
  const NS='http://www.w3.org/2000/svg',svg=document.createElementNS(NS,'svg');svg.setAttribute('viewBox',`0 0 800 ${chartHeight}`);svg.setAttribute('role','img');svg.setAttribute('aria-label',t('Histogram'));
  const colors=['var(--accent)','#a04c75','#3b70bd'],node=(tag,attrs,text='')=>{const el=document.createElementNS(NS,tag);for(const [name,value] of Object.entries(attrs))el.setAttribute(name,String(value));el.textContent=text;svg.append(el);return el;};
  const all=columns.flat();if(!all.length){container.replaceChildren();return;}
  const minimum=Math.min(...all),maximum=Math.max(...all),span=maximum-minimum||1;
  node('line',{x1:60,x2:740,y1:chartHeight-60,y2:chartHeight-60,stroke:'var(--muted)'});
  for(let i=0;i<=4;i++)node('text',{x:60+i*170,y:chartHeight-30,fill:'var(--muted)','text-anchor':'middle','font-size':14},displayNumber(minimum+span*i/4,digits));
  if(type==='histogram'){
    const bins=Math.min(16,Math.max(1,Math.ceil(Math.sqrt(all.length)))),counts=columns.map(values=>{const result=Array(bins).fill(0);for(const n of values)result[Math.min(bins-1,Math.floor((n-minimum)/span*bins))]++;return result;}),peak=Math.max(...counts.flat(),1);
    for(let index=0;index<counts.length;index++)for(let i=0;i<bins;i++){const width=680/bins/counts.length,count=counts[index][i],height=280*count/peak;node('rect',{x:60+i*680/bins+index*width,y:340-height,width:Math.max(1,width-2),height,fill:colors[index%colors.length],opacity:.7,'data-bin':i,'data-series':series[index].label});node('text',{x:60+i*680/bins+(index+.5)*width,y:334-height,fill:'var(--ink)','text-anchor':'middle','font-size':11},String(count));}
  }
  container.replaceChildren(svg);
  if(type==='histogram'){const legend=document.createElement('div');legend.className='statistics-plot-legend';series.forEach((entry,i)=>{const label=document.createElement('span');label.textContent=`${entry.label} (n=${entry.values.length})`;label.style.color=colors[i%colors.length];legend.append(label);});container.prepend(legend);}
}

function svgChart(label,width,height){
  const NS='http://www.w3.org/2000/svg',svg=document.createElementNS(NS,'svg');
  svg.setAttribute('viewBox',`0 0 ${width} ${height}`);svg.setAttribute('role','img');svg.setAttribute('aria-label',t(label));
  const node=(tag,attrs,text='',parent=svg)=>{const el=document.createElementNS(NS,tag);for(const [key,value] of Object.entries(attrs))el.setAttribute(key,String(value));el.textContent=text;parent.append(el);return el;};
  return {svg,node};
}

function drawDistribution(container,series,digits,type,orientation){
  if(!series.some(entry=>entry.values.length)){container.replaceChildren();return;}
  const vertical=orientation==='vertical',width=vertical?Math.max(800,series.length*90+130):800,height=vertical?440:Math.max(300,series.length*90+70);
  const {svg,node}=svgChart(type==='box'?'Box plot':'Violin + points',width,height);
  svg.dataset.orientation=vertical?'vertical':'horizontal';
  if(type==='violin')svg.dataset.rawLayout='beeswarm';
  const all=series.flatMap(entry=>entry.values),scale=all.reduce((a,b)=>Math.max(a,Math.abs(b)),0)||1;
  const minimum=all.reduce((a,b)=>Math.min(a,b)),maximum=all.reduce((a,b)=>Math.max(a,b)),span=maximum/scale-minimum/scale;
  const fraction=value=>span?(value/scale-minimum/scale)/span:.5;
  const axis=value=>vertical?height-80-fraction(value)*(height-120):110+fraction(value)*630;
  const lane=index=>vertical?110+(index+.5)*(width-150)/series.length:45+index*90;
  const position=(value,index,offset=0)=>vertical?[lane(index)+offset,axis(value)]:[axis(value),lane(index)+offset];
  const colors=['var(--accent)','#a04c75','#3b70bd'];
  node('line',vertical?{x1:110,x2:110,y1:40,y2:height-80,stroke:'var(--muted)'}:{x1:110,x2:740,y1:height-45,y2:height-45,stroke:'var(--muted)'});
  for(let i=0;i<=(span?4:0);i++){
    const fraction=span?i/4:.5,value=(minimum/scale*(1-fraction)+maximum/scale*fraction)*scale;
    node('text',{x:vertical?100:110+fraction*630,y:vertical?axis(value)+5:height-20,fill:'var(--muted)','text-anchor':vertical?'end':'middle','font-size':14,'data-value-tick':value},displayNumber(value,digits));
  }
  series.forEach((entry,index)=>{
    const center=lane(index),color=colors[index%colors.length],density=type==='violin'?violinDensity(entry.values):[];
    const label=node('text',{x:vertical?center:20,y:vertical?height-52:center+5,fill:'var(--ink)','font-size':14,'text-anchor':vertical?'middle':'start'},entry.label.length>12?`${entry.label.slice(0,11)}…`:entry.label);
    node('title',{},entry.label,label);
    node('text',{x:vertical?center:20,y:vertical?height-32:center+23,fill:'var(--muted)','font-size':12,'text-anchor':vertical?'middle':'start'},`n=${entry.values.length}`);
    if(type==='box'&&entry.values.length){
      const values=[...entry.values].sort((a,b)=>a-b),q=p=>{const at=(values.length-1)*p,lo=Math.floor(at),hi=Math.ceil(at),f=at-lo;return values[lo]*(1-f)+values[hi]*f;};
      const low=values[0],q1=q(.25),median=q(.5),q3=q(.75),high=values.at(-1);
      const line=(value1,offset1,value2,offset2,attrs={})=>{const [x1,y1]=position(value1,index,offset1),[x2,y2]=position(value2,index,offset2);return node('line',{x1,y1,x2,y2,stroke:color,'stroke-width':2,...attrs});};
      line(low,0,high,0);
      const a=position(q1,index,-20),b=position(q3,index,20);
      const box=node('rect',{x:Math.min(a[0],b[0]),y:Math.min(a[1],b[1]),width:Math.max(1,Math.abs(b[0]-a[0])),height:Math.max(1,Math.abs(b[1]-a[1])),fill:color,'fill-opacity':.3,stroke:color,'data-box':entry.label});
      node('title',{},`${entry.label}: min=${displayNumber(low,digits)}, Q1=${displayNumber(q1,digits)}, median=${displayNumber(median,digits)}, Q3=${displayNumber(q3,digits)}, max=${displayNumber(high,digits)}`,box);
      line(median,-20,median,20,{'stroke-width':3,'data-median':median});for(const at of [low,high])line(at,-12,at,12);
      const [mx,my]=position(median,index,42);node('text',{x:mx,y:my+4,fill:'var(--ink)','text-anchor':'middle','font-size':12},displayNumber(median,digits));
    }
    if(density.length){
      const upper=density.map(([value,width])=>position(value,index,-width*28).join(',')),lower=[...density].reverse().map(([value,width])=>position(value,index,width*28).join(','));
      node('path',{d:`M${upper.join(' L')} L${lower.join(' L')} Z`,fill:color,'fill-opacity':.24,stroke:color,'stroke-width':1.5,'data-violin':entry.label});
    }
    if(type==='violin'){
      const swarm=beeswarmLayout(entry.values.map(axis),3,28);
      entry.values.forEach((value,i)=>{
        const [cx,cy]=position(value,index,swarm.offsets[i]),point=node('circle',{cx,cy,r:swarm.radius,fill:color,'fill-opacity':.75,'data-raw-point':entry.label,'data-value':value});
        node('title',{},`${entry.label}: ${displayNumber(value,digits)}`,point);
      });
    }
  });
  svg.classList.add('statistics-distribution');
  if(vertical&&width>800){const scroll=document.createElement('div');scroll.className='statistics-distribution-scroll';svg.style.width=`${width}px`;scroll.append(svg);container.replaceChildren(scroll);}
  else container.replaceChildren(svg);
}

function drawHeatMap(container,data,digits){
  const finite=data.rows.flatMap(row=>row.values).filter(value=>value!==null);
  if(!data.rows.length||!data.columns.length||!finite.length&&!data.correlation){container.replaceChildren();return;}
  const minimum=data.range?.[0]??finite.reduce((a,b)=>Math.min(a,b)),maximum=data.range?.[1]??finite.reduce((a,b)=>Math.max(a,b));
  const left=data.clustered?180:100,top=data.clustered?120:45,width=Math.max(600,left+20+data.columns.length*80),cellWidth=(width-left-20)/data.columns.length;
  const {svg,node}=svgChart('Heat map',width,top+data.rows.length*30);
  svg.style.width=`${width}px`;svg.classList.add('statistics-heatmap');
  if(data.clustered){
    const rowPeak=data.rowLinks.reduce((peak,link)=>Math.max(peak,link.height),0)||1,columnPeak=data.columnLinks.reduce((peak,link)=>Math.max(peak,link.height),0)||1;
    for(const link of data.rowLinks){const a=top+(link.left+.5)*30,b=top+(link.right+.5)*30,x=78-link.height/rowPeak*70;
      node('path',{d:`M${78-link.leftHeight/rowPeak*70},${a} H${x} V${b} H${78-link.rightHeight/rowPeak*70}`,fill:'none',stroke:'var(--muted)','stroke-width':1.5,'data-dendrogram':'row'});}
    for(const link of data.columnLinks){const a=left+(link.left+.5)*cellWidth,b=left+(link.right+.5)*cellWidth,y=85-link.height/columnPeak*75;
      node('path',{d:`M${a},${85-link.leftHeight/columnPeak*75} V${y} H${b} V${85-link.rightHeight/columnPeak*75}`,fill:'none',stroke:'var(--muted)','stroke-width':1.5,'data-dendrogram':'column'});}
  }
  data.columns.forEach((name,i)=>node('text',{x:left+(i+.5)*cellWidth,y:top-21,fill:'var(--ink)','text-anchor':'middle','font-size':14},name));
  data.rows.forEach((row,i)=>{
    const label=node('text',{x:left-10,y:top+i*30+19,fill:'var(--ink)','text-anchor':'end','font-size':13},row.label.length>12?`${row.label.slice(0,11)}…`:row.label);
    node('title',{},row.label,label);
    row.values.forEach((value,column)=>{
      const cell=node('rect',{x:left+column*cellWidth,y:top+i*30,width:cellWidth,height:30,fill:heatMapColor(value,minimum,maximum)||'var(--number)',stroke:'var(--line)','data-row':i,'data-column':column,'data-value':value??''});
      const displayed=value===null?'—':displayNumber(value,digits);
      node('title',{},`${row.label} · ${data.columns[column]}: ${displayed}${row.counts?` (n=${row.counts[column]})`:''}`,cell);
      node('text',{x:left+(column+.5)*cellWidth,y:top+i*30+20,fill:value===null?'var(--muted)':heatMapTextColor(value,minimum,maximum),'text-anchor':'middle','font-size':12},displayed.length>10?`${displayed.slice(0,9)}…`:displayed);
    });
  });
  const scroll=document.createElement('div');scroll.className='statistics-heatmap-scroll';scroll.tabIndex=0;scroll.setAttribute('aria-label',t('Heat map'));scroll.append(svg);
  const legend=document.createElement('div');legend.className='statistics-heatmap-legend';
  const low=document.createElement('span'),bar=document.createElement('span'),high=document.createElement('span');
  low.textContent=displayNumber(minimum,digits);high.textContent=displayNumber(maximum,digits);bar.className='statistics-heatmap-scale';
  if(minimum===maximum)bar.style.background=heatMapColor(minimum,minimum,maximum);
  legend.append(low,bar,high);
  const caption=document.createElement('div');caption.className='statistics-plot-legend';
  const methodName={pearson:'Pearson',spearman:'Spearman',kendall:'Kendall'}[data.method]||'Pearson';
  const captionKey=data.correlation?`${methodName} correlation · pairwise complete observations`:data.mode==='zrow'?'Row z-scores · color = z-score':data.mode==='zcolumn'?'Column z-scores · color = z-score':'Raw values · rows × columns';
  caption.textContent=t(captionKey)+(data.clustered?` · ${t('Hierarchical clustering')}`:'');
  container.replaceChildren(caption,scroll,legend);
}

function heatMapTextColor(value,minimum,maximum){
  const rgb=heatMapColor(value,minimum,maximum).match(/\d+/g).map(Number);
  const linear=rgb.map(v=>{const n=v/255;return n<=.04045?n/12.92:((n+.055)/1.055)**2.4;});
  return .2126*linear[0]+.7152*linear[1]+.0722*linear[2]<.18?'#fff':'#17232c';
}
