import {statisticsColumnNames} from './workspace-commands.js';

export function statisticsPlotNumber(cell){
  const text=String(cell??'').trim();if(!text)return null;
  const normalized=/^[+-]?\d{1,3}(?:,\d{3})+(?:\.\d+)?(?:[eE][+-]?\d+)?$/.test(text)?text.replace(/,/g,''):text;
  const value=Number(normalized);return Number.isFinite(value)?value:null;
}

// Gaussian KDE with Scott's bandwidth; widths are normalized per violin.
// Normalize before computing variance so large finite observations stay finite.
export function violinDensity(values){
  const finite=values.filter(Number.isFinite);if(finite.length<2)return [];
  const scale=finite.reduce((peak,v)=>Math.max(peak,Math.abs(v)),0)||1;
  const data=finite.map(v=>v/scale),lo=data.reduce((a,b)=>Math.min(a,b)),hi=data.reduce((a,b)=>Math.max(a,b));
  if(lo===hi)return [];
  const mean=data.reduce((a,b)=>a+b/data.length,0);
  const sd=Math.sqrt(data.reduce((sum,v)=>sum+(v-mean)**2/(data.length-1),0));
  const bandwidth=Math.max(sd*data.length**(-.2),(hi-lo)/1000);
  const samples=Array.from({length:97},(_,i)=>{
    const at=lo*(1-i/96)+hi*(i/96);
    return [at*scale,data.reduce((sum,v)=>sum+Math.exp(-.5*((at-v)/bandwidth)**2),0)];
  });
  const peak=Math.max(...samples.map(([,density])=>density));
  return samples.map(([at,density])=>[at,density/peak]);
}

// Keep the value-axis coordinates fixed and pack circles nearest the centerline.
// A dense swarm uses smaller circles rather than losing points or crossing lanes.
export function beeswarmLayout(axisPositions,preferredRadius,halfWidth){
  if(!axisPositions.length)return {radius:preferredRadius,offsets:[]};
  const order=axisPositions.map((axis,index)=>({axis,index})).sort((a,b)=>a.axis-b.axis||a.index-b.index);
  let duplicates=1,run=1;
  for(let i=1;i<order.length;i++){run=order[i].axis===order[i-1].axis?run+1:1;duplicates=Math.max(duplicates,run);}
  let radius=Math.min(preferredRadius,halfWidth/(1+Math.ceil((duplicates-1)/2)*2.15));
  function pack(radius){
    const spacing=radius*2.15,offsets=Array(axisPositions.length).fill(0),placed=[];
    let first=0;
    for(let i=0;i<order.length;){
      const point=order[i];
      while(first<placed.length&&point.axis-placed[first].axis>=spacing)first++;
      // Isolated equal values can be laid out directly, including large constants.
      if(first===placed.length){
        let end=i+1;while(end<order.length&&order[end].axis===point.axis)end++;
        for(let j=i;j<end;j++){
          const rank=j-i,offset=rank===0?0:Math.ceil(rank/2)*spacing*(rank%2?1:-1);
          if(Math.abs(offset)+radius>halfWidth+1e-9)return null;
          offsets[order[j].index]=offset;placed.push({axis:point.axis,offset});
        }
        i=end;continue;
      }
      const intervals=placed.slice(first).map(other=>{
        const distance=point.axis-other.axis,reach=Math.sqrt(Math.max(0,spacing*spacing-distance*distance));
        return [other.offset-reach,other.offset+reach];
      }).sort((a,b)=>a[0]-b[0]);
      const merged=[];
      for(const interval of intervals){
        const last=merged.at(-1);
        if(last&&interval[0]<last[1])last[1]=Math.max(last[1],interval[1]);else merged.push([...interval]);
      }
      const blocked=merged.find(([lo,hi])=>lo<0&&hi>0);
      const offset=!blocked?0:Math.abs(blocked[0])<blocked[1]?blocked[0]:Math.abs(blocked[0])>blocked[1]?blocked[1]:i%2?blocked[1]:blocked[0];
      if(Math.abs(offset)+radius>halfWidth+1e-9)return null;
      offsets[point.index]=offset;placed.push({axis:point.axis,offset});i++;
    }
    return offsets;
  }
  let offsets=pack(radius);
  while(offsets===null){radius*=.8;offsets=pack(radius);}
  return {radius,offsets};
}

export function statisticsHeatMapData(rows,{grouping='columns',columnCount=rows[0]?.length||1}={}){
  const groupColumn=columnCount>1&&['first','last'].includes(grouping)?(grouping==='first'?0:columnCount-1):-1;
  const indices=Array.from({length:columnCount},(_,i)=>i).filter(i=>i!==groupColumn),names=statisticsColumnNames(columnCount);
  return {columns:indices.map(i=>names[i]),rows:rows.map((row,i)=>({
    label:groupColumn<0?String(i+1):String(row[groupColumn]??'').trim()||String(i+1),
    values:indices.map(column=>statisticsPlotNumber(row[column]))
  }))};
}

// Shared blue -> neutral -> red scale, including a stable midpoint for constants.
export function heatMapColor(value,minimum,maximum){
  if(value===null)return null;
  const scale=Math.max(Math.abs(minimum),Math.abs(maximum))||1;
  const t=minimum===maximum ? .5 : Math.max(0,Math.min(1,(value/scale-minimum/scale)/(maximum/scale-minimum/scale)));
  const low=[59,112,189],middle=[241,241,235],high=[180,55,72];
  const a=t<=.5?low:middle,b=t<=.5?middle:high,f=t<=.5?t*2:(t-.5)*2;
  return `rgb(${a.map((v,i)=>Math.round(v+(b[i]-v)*f)).join(',')})`;
}

export function statisticsCorrelationHeatMap(rows,{columnCount=rows[0]?.length||1}={}){
  const names=statisticsColumnNames(columnCount),columns=names.map((label,i)=>({label,values:rows.map(row=>statisticsPlotNumber(row[i]))})).filter(column=>column.values.some(value=>value!==null));
  const cells=columns.map(a=>columns.map(b=>{
    const pairs=a.values.flatMap((value,i)=>value!==null&&b.values[i]!==null?[[value,b.values[i]]]:[]),n=pairs.length;
    if(n<2||pairs.every(p=>p[0]===pairs[0][0])||pairs.every(p=>p[1]===pairs[0][1]))return {value:null,n};
    const sx=pairs.reduce((peak,p)=>Math.max(peak,Math.abs(p[0])),0)||1,sy=pairs.reduce((peak,p)=>Math.max(peak,Math.abs(p[1])),0)||1;
    const mx=pairs.reduce((sum,p)=>sum+p[0]/sx/n,0),my=pairs.reduce((sum,p)=>sum+p[1]/sy/n,0);
    let xx=0,yy=0,xy=0;
    for(const [x,y] of pairs){const dx=x/sx-mx,dy=y/sy-my;xx+=dx*dx;yy+=dy*dy;xy+=dx*dy;}
    return {value:xx>0&&yy>0?(a===b?1:Math.max(-1,Math.min(1,xy/(Math.sqrt(xx)*Math.sqrt(yy))))):null,n};
  }));
  return {columns:columns.map(column=>column.label),rows:columns.map((column,i)=>({label:column.label,values:cells[i].map(cell=>cell.value),counts:cells[i].map(cell=>cell.n)})),range:[-1,1],correlation:true};
}
