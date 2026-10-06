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

export function statisticsHeatMapData(rows,{grouping='columns',columnCount=rows[0]?.length||1,mode='raw',columnNames=statisticsColumnNames(columnCount)}={}){
  const groupColumn=columnCount>1&&['first','last'].includes(grouping)?(grouping==='first'?0:columnCount-1):-1;
  const indices=Array.from({length:columnCount},(_,i)=>i).filter(i=>i!==groupColumn),columns=indices.map(i=>columnNames[i]||statisticsColumnNames(columnCount)[i]);
  const values=rows.map(row=>indices.map(column=>statisticsPlotNumber(row[column])));
  if(mode==='zrow')for(const row of values)standardizeHeatMapValues(row);
  else if(mode==='zcolumn')for(let column=0;column<indices.length;column++){
    const standardized=values.map(row=>row[column]);standardizeHeatMapValues(standardized);standardized.forEach((value,row)=>{values[row][column]=value;});
  }
  const data={columns,rows:rows.map((row,i)=>({
    label:groupColumn<0?String(i+1):String(row[groupColumn]??'').trim()||String(i+1),
    values:values[i]
  }))};
  if(mode!=='raw')data.mode=mode;
  return data;
}

function standardizeHeatMapValues(values){
  const finite=values.map((value,index)=>value===null?null:{value,index}).filter(item=>item!==null);
  if(!finite.length)return;
  const scale=finite.reduce((peak,item)=>Math.max(peak,Math.abs(item.value)),0)||1;
  const mean=finite.reduce((sum,item)=>sum+item.value/scale/finite.length,0);
  const deviation=Math.sqrt(finite.reduce((sum,item)=>sum+((item.value/scale-mean)**2)/finite.length,0));
  finite.forEach(({value,index})=>{values[index]=deviation===0?0:(value/scale-mean)/deviation;});
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

export function statisticsCorrelationHeatMap(rows,{columnCount=rows[0]?.length||1,columnNames=statisticsColumnNames(columnCount),xColumns,yColumns,method='pearson'}={}){
  const indices=Array.from({length:columnCount},(_,i)=>i),usable=indices.filter(i=>rows.some(row=>statisticsPlotNumber(row[i])!==null));
  const x=(xColumns||usable).filter(i=>usable.includes(i)),y=(yColumns||usable).filter(i=>usable.includes(i));
  const cells=y.map(yi=>x.map(xi=>{
    const pairs=rows.flatMap(row=>{const a=statisticsPlotNumber(row[xi]),b=statisticsPlotNumber(row[yi]);return a===null||b===null?[]:[[a,b]];});
    return {value:correlationCoefficient(pairs,method),n:pairs.length};
  }));
  return {columns:x.map(i=>columnNames[i]||statisticsColumnNames(columnCount)[i]),rows:y.map((index,i)=>({label:columnNames[index]||statisticsColumnNames(columnCount)[index],values:cells[i].map(cell=>cell.value),counts:cells[i].map(cell=>cell.n)})),range:[-1,1],correlation:true,method};
}

function correlationCoefficient(pairs,method){
  const n=pairs.length;if(n<2)return null;
  if(method==='spearman')pairs=rankPairs(pairs);
  if(method==='kendall')return kendallTauB(pairs);
  const [x,y]=[0,1].map(axis=>pairs.map(pair=>pair[axis]));
  if(x.every(value=>value===x[0])||y.every(value=>value===y[0]))return null;
  const sx=Math.max(...x.map(Math.abs))||1,sy=Math.max(...y.map(Math.abs))||1;
  const mx=x.reduce((sum,value)=>sum+value/sx/n,0),my=y.reduce((sum,value)=>sum+value/sy/n,0);
  let xx=0,yy=0,xy=0;
  for(let i=0;i<n;i++){const dx=x[i]/sx-mx,dy=y[i]/sy-my;xx+=dx*dx;yy+=dy*dy;xy+=dx*dy;}
  return xx>0&&yy>0?Math.max(-1,Math.min(1,xy/Math.sqrt(xx*yy))):null;
}

function rankPairs(pairs){
  const ranks=pairs.map((pair,index)=>({x:pair[0],y:pair[1],index,rx:0,ry:0}));
  for(const key of ['x','y']){
    const ordered=[...ranks].sort((a,b)=>a[key]-b[key]||a.index-b.index);
    for(let start=0;start<ordered.length;){let end=start+1;while(end<ordered.length&&ordered[end][key]===ordered[start][key])end++;const rank=(start+1+end)/2;for(let i=start;i<end;i++)ordered[i][`r${key}`]=rank;start=end;}
  }
  return ranks.map(row=>[row.rx,row.ry]);
}

function kendallTauB(pairs){
  const n=pairs.length,total=n*(n-1)/2;
  const tiedPairs=axis=>{const counts=new Map();for(const pair of pairs)counts.set(pair[axis],(counts.get(pair[axis])||0)+1);return [...counts.values()].reduce((sum,count)=>sum+count*(count-1)/2,0);};
  const tiesX=tiedPairs(0),tiesY=tiedPairs(1);
  const ys=[...new Set(pairs.map(pair=>pair[1]))].sort((a,b)=>a-b),rank=new Map(ys.map((value,index)=>[value,index+1])),tree=new Float64Array(ys.length+1);
  const query=index=>{let sum=0;for(let i=index;i>0;i-=i&-i)sum+=tree[i];return sum;};
  const add=index=>{for(let i=index;i<tree.length;i+=i&-i)tree[i]++;};
  const sorted=[...pairs].sort((a,b)=>a[0]-b[0]||a[1]-b[1]);let previous=0,concordantMinusDiscordant=0;
  for(let start=0;start<sorted.length;){let end=start+1;while(end<sorted.length&&sorted[end][0]===sorted[start][0])end++;
    for(let i=start;i<end;i++){const yRank=rank.get(sorted[i][1]);concordantMinusDiscordant+=query(yRank-1)-(previous-query(yRank));}
    for(let i=start;i<end;i++){add(rank.get(sorted[i][1]));previous++;}start=end;
  }
  const denominator=Math.sqrt((total-tiesX)*(total-tiesY));
  return denominator>0?Math.max(-1,Math.min(1,concordantMinusDiscordant/denominator)):null;
}
