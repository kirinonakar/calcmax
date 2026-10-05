import {plot,dataBounds} from './plot.js';
import {displayNumber} from './display-format.js';
export function statisticsPlot(container,rows,{type='scatter',digits=10,curve=[],xAxisLabel='x',yAxisLabel='y'}={}){
  const columns=Array.from({length:rows[0]?.length||1},(_,i)=>rows.map(r=>r[i]).filter(s=>s!==''&&s!==undefined).map(Number).filter(Number.isFinite));
  if(type==='scatter'){
    const points=rows.filter(r=>r[0]!==''&&r[1]!==''&&r.length>1).map(r=>r.slice(0,2).map(Number)).filter(p=>p.every(Number.isFinite));
    if(!points.length){container.replaceChildren();return;}
    plot(container,{curves:[points,...(curve.length?[curve]:[])]},dataBounds(points),{scatterCurves:[0],digits,xAxisLabel,yAxisLabel});return;
  }
  const NS='http://www.w3.org/2000/svg',svg=document.createElementNS(NS,'svg');svg.setAttribute('viewBox','0 0 800 400');svg.setAttribute('role','img');svg.setAttribute('aria-label',type==='box'?'Box plot':'Histogram');
  const colors=['var(--accent)','#a04c75','#3b70bd'],node=(tag,attrs,text='')=>{const el=document.createElementNS(NS,tag);for(const [name,value] of Object.entries(attrs))el.setAttribute(name,String(value));el.textContent=text;svg.append(el);return el;};
  const all=columns.flat();if(!all.length){container.replaceChildren();return;}
  const minimum=Math.min(...all),maximum=Math.max(...all),span=maximum-minimum||1,x=n=>60+(n-minimum)/span*680;
  node('line',{x1:60,x2:740,y1:340,y2:340,stroke:'var(--muted)'});
  for(let i=0;i<=4;i++)node('text',{x:60+i*170,y:370,fill:'var(--muted)','text-anchor':'middle','font-size':14},displayNumber(minimum+span*i/4,digits));
  if(type==='histogram'){
    const bins=Math.min(16,Math.max(1,Math.ceil(Math.sqrt(all.length)))),counts=columns.map(values=>{const result=Array(bins).fill(0);for(const n of values)result[Math.min(bins-1,Math.floor((n-minimum)/span*bins))]++;return result;}),peak=Math.max(...counts.flat(),1);
    for(let series=0;series<counts.length;series++)for(let i=0;i<bins;i++){const width=680/bins/counts.length,count=counts[series][i],height=280*count/peak;node('rect',{x:60+i*680/bins+series*width,y:340-height,width:Math.max(1,width-2),height,fill:colors[series],opacity:.7,'data-bin':i});node('text',{x:60+i*680/bins+(series+.5)*width,y:334-height,fill:'var(--ink)','text-anchor':'middle','font-size':11},String(count));}
  }else{
    columns.forEach((values,i)=>{if(!values.length)return;values=[...values].sort((a,b)=>a-b);const q=p=>{const index=(values.length-1)*p,lower=Math.floor(index);return values[lower]+(values[Math.ceil(index)]-values[lower])*(index-lower);},low=values[0],q1=q(.25),median=q(.5),q3=q(.75),high=values.at(-1),y=70+i*85;
      node('line',{x1:x(low),x2:x(high),y1:y,y2:y,stroke:colors[i],'stroke-width':2});node('rect',{x:x(q1),y:y-20,width:Math.max(1,x(q3)-x(q1)),height:40,fill:colors[i],opacity:.3});node('line',{x1:x(median),x2:x(median),y1:y-20,y2:y+20,stroke:colors[i],'stroke-width':3});for(const at of [low,high])node('line',{x1:x(at),x2:x(at),y1:y-12,y2:y+12,stroke:colors[i]});node('text',{x:20,y:y+5,fill:'var(--ink)'},['x','y','z'][i]);node('text',{x:x(median),y:y+42,fill:'var(--ink)','text-anchor':'middle','font-size':12},displayNumber(median,digits));
    });
  }
  container.replaceChildren(svg);
}
