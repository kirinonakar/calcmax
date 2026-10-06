// Hierarchical clustering for heat maps. Missing cells only use shared
// dimensions. Single linkage keeps the linear-memory minimum spanning forest;
// average, complete and Ward use cached nearest-neighbour Lance–Williams merges.
// Ward is only defined for squared Euclidean distances, so it pins the metric.
const LINKAGES=['single','average','complete','ward'];
const METRICS=['euclidean','manhattan','correlation'];
const MATRIX_LIMIT=800;

export function clusteringOptions(options={}){
  const linkage=LINKAGES.includes(options.linkage)?options.linkage:'single';
  return {linkage,metric:linkage==='ward'||!METRICS.includes(options.metric)?'euclidean':options.metric};
}

function distanceFunction(data,dimensions,metric){
  return (a,b)=>{
    let squares=0,absolute=0,count=0,sumX=0,sumY=0,sumXX=0,sumYY=0,sumXY=0;
    for(let i=0;i<dimensions;i++){
      const x=data[a][i],y=data[b][i];
      if(!Number.isFinite(x)||!Number.isFinite(y))continue;
      const delta=x-y;squares+=delta*delta;absolute+=Math.abs(delta);count++;
      if(metric==='correlation'){sumX+=x;sumY+=y;sumXX+=x*x;sumYY+=y*y;sumXY+=x*y;}
    }
    if(!count)return Infinity;
    if(metric==='manhattan')return absolute*dimensions/count;
    if(metric==='correlation'){
      if(count<2)return Infinity;
      const covariance=sumXY-sumX*sumY/count,varianceX=sumXX-sumX*sumX/count,varianceY=sumYY-sumY*sumY/count;
      if(!(varianceX>0)||!(varianceY>0))return 1;
      return 1-covariance/Math.sqrt(varianceX*varianceY);
    }
    return Math.sqrt(squares*dimensions/count);
  };
}

function singleLinkage(n,distance){
  const visited=new Uint8Array(n),nearest=new Float64Array(n).fill(Infinity),from=new Int32Array(n).fill(-1),edges=[];
  for(let step=0;step<n;step++){
    let next=-1;for(let i=0;i<n;i++)if(!visited[i]&&(next<0||nearest[i]<nearest[next]))next=i;
    visited[next]=1;if(from[next]>=0)edges.push({a:from[next],b:next,height:nearest[next]});
    for(let i=0;i<n;i++)if(!visited[i]){const d=distance(next,i);if(d<nearest[i]){nearest[i]=d;from[i]=next;}}
  }
  edges.sort((a,b)=>a.height-b.height||a.a-b.a||a.b-b.b);
  const parent=Int32Array.from({length:n},(_,i)=>i),roots=Array.from({length:n},(_,i)=>i),nodes=Array.from({length:n},(_,i)=>({minimum:i,height:0}));
  const find=i=>{while(parent[i]!==i){parent[i]=parent[parent[i]];i=parent[i];}return i;};
  for(const edge of edges){
    let a=find(edge.a),b=find(edge.b);if(a===b)continue;
    if(nodes[roots[a]].minimum>nodes[roots[b]].minimum)[a,b]=[b,a];
    nodes.push({left:roots[a],right:roots[b],minimum:nodes[roots[a]].minimum,height:edge.height});
    roots[a]=nodes.length-1;parent[b]=a;
  }
  const forest=Array.from({length:n},(_,i)=>i).filter(i=>find(i)===i).map(i=>roots[i]).sort((a,b)=>nodes[a].minimum-nodes[b].minimum);
  const order=[],stack=[...forest].reverse();
  while(stack.length){const id=stack.pop(),node=nodes[id];if(id<n)order.push(id);else stack.push(node.right,node.left);}
  const centers=Array(nodes.length).fill(0);order.forEach((id,index)=>centers[id]=index);
  const links=[];
  for(let id=n;id<nodes.length;id++){
    const node=nodes[id];centers[id]=(centers[node.left]+centers[node.right])/2;
    links.push({left:centers[node.left],right:centers[node.right],leftHeight:nodes[node.left].height,rightHeight:nodes[node.right].height,height:node.height});
  }
  return {order,links};
}

function matrixLinkage(n,distance,linkage){
  const matrix=new Float64Array(n*n),sizes=new Int32Array(n).fill(1),active=new Uint8Array(n).fill(1),least=Int32Array.from({length:n},(_,i)=>i),root=Int32Array.from({length:n},(_,i)=>i);
  for(let i=0;i<n;i++)for(let j=i+1;j<n;j++){
    const value=distance(i,j),stored=linkage==='ward'?value*value:value,safe=Number.isFinite(stored)?stored:Infinity;
    matrix[i*n+j]=safe;matrix[j*n+i]=safe;
  }
  const left=new Int32Array(2*n).fill(-1),right=new Int32Array(2*n).fill(-1),heights=new Float64Array(2*n);
  const nearest=new Int32Array(n).fill(-1),best=new Float64Array(n).fill(Infinity);
  const refresh=i=>{let target=-1,value=Infinity;for(let j=0;j<n;j++)if(active[j]&&j!==i){const candidate=matrix[i*n+j];if(candidate<value){value=candidate;target=j;}}nearest[i]=target;best[i]=value;};
  for(let i=0;i<n;i++)refresh(i);
  let count=n,next=n;
  while(count>1){
    let a=-1,value=Infinity;
    for(let i=0;i<n;i++)if(active[i]&&(best[i]<value||(best[i]===value&&a>=0&&least[i]<least[a]))){value=best[i];a=i;}
    if(a<0||!Number.isFinite(value))break;
    const b=nearest[a];if(b<0)break;
    const first=least[a]<=least[b]?a:b,second=first===a?b:a,sizeA=sizes[first],sizeB=sizes[second];
    heights[next]=linkage==='ward'?Math.sqrt(Math.max(0,matrix[first*n+second])):matrix[first*n+second];
    left[next]=root[first];right[next]=root[second];root[first]=next;
    least[first]=Math.min(least[first],least[second]);
    const squared=matrix[first*n+second];
    for(let c=0;c<n;c++)if(active[c]&&c!==first){
      const otherA=matrix[first*n+c],otherB=matrix[second*n+c];
      const merged=linkage==='average'?(sizeA*otherA+sizeB*otherB)/(sizeA+sizeB):
        linkage==='complete'?Math.max(otherA,otherB):
        ((sizeA+sizes[c])*otherA+(sizeB+sizes[c])*otherB-sizes[c]*squared)/(sizeA+sizeB+sizes[c]);
      const safe=Number.isFinite(merged)?merged:Infinity;
      matrix[first*n+c]=safe;matrix[c*n+first]=safe;
    }
    sizes[first]=sizeA+sizeB;active[second]=0;
    refresh(first);
    for(let c=0;c<n;c++)if(active[c]&&c!==first&&(nearest[c]===first||nearest[c]===second))refresh(c);
    count--;next++;
  }
  const forest=Array.from({length:n},(_,i)=>i).filter(i=>active[i]).sort((a,b)=>least[a]-least[b]).map(i=>root[i]);
  const order=[],stack=[...forest].reverse();
  while(stack.length){const id=stack.pop();if(id<n)order.push(id);else stack.push(right[id],left[id]);}
  const centers=Array(next).fill(0);order.forEach((id,index)=>centers[id]=index);
  const links=[];
  for(let id=n;id<next;id++){
    centers[id]=(centers[left[id]]+centers[right[id]])/2;
    links.push({left:centers[left[id]],right:centers[right[id]],leftHeight:heights[left[id]],rightHeight:heights[right[id]],height:heights[id]});
  }
  return {order,links};
}

export function statisticsHierarchy(vectors,options={}){
  const {linkage,metric}=clusteringOptions(options);
  const n=vectors.length,dimensions=vectors[0]?.length||0;
  if(!n)return {order:[],links:[]};
  let scale=0;for(const row of vectors)for(const value of row)if(Number.isFinite(value))scale=Math.max(scale,Math.abs(value));scale||=1;
  const data=vectors.map(row=>Float64Array.from({length:dimensions},(_,i)=>Number.isFinite(row[i])?row[i]/scale:NaN));
  const distance=distanceFunction(data,dimensions,metric);
  if(linkage==='single')return singleLinkage(n,distance);
  if(n>MATRIX_LIMIT)throw new Error(`Average, complete and Ward linkage support at most ${MATRIX_LIMIT} labels; use single linkage for larger matrices`);
  return matrixLinkage(n,distance,linkage);
}

export function clusteredHeatMap(data,options={}){
  const rows=statisticsHierarchy(data.rows.map(row=>row.values),options);
  const columns=statisticsHierarchy(data.columns.map((_,i)=>data.rows.map(row=>row.values[i])),options);
  return {...data,clustered:true,rowLinks:rows.links,columnLinks:columns.links,
    columns:columns.order.map(i=>data.columns[i]),rows:rows.order.map(i=>({...data.rows[i],values:columns.order.map(j=>data.rows[i].values[j]),...(data.rows[i].counts?{counts:columns.order.map(j=>data.rows[i].counts[j])}:{})}))};
}
