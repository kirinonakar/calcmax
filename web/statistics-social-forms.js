import {statisticsComparisonData} from './statistics-comparison-data.js';

export const socialAnalysisIds=new Set(['cronbach','efa','cfa','sem','manova','mediation','moderation','cramerv','phi','cohenkappa','dunn','discriminantanalysis','quantreg','zeroinflated','tobit','hcluster']);

export function socialStatisticsPlan(id,rows,opts,columnLabels=[]){
  const n=Math.max(0,...rows.map(row=>row.length)),labels={};
  const col=key=>{let i=Number(opts[key]);if(i===-1)i=n-1;if(!Number.isInteger(i)||i<0||i>=n)throw new Error('Choose valid data columns');return i;};
  const columns=(key,excluded=[],optional=false)=>{
    const raw=opts[key],indices=raw==='auto'?Array.from({length:n},(_,i)=>i).filter(i=>!excluded.includes(i)):String(raw??'').split(',').filter(Boolean).map(Number);
    if((!optional&&!indices.length)||new Set(indices).size!==indices.length||indices.some(i=>!Number.isInteger(i)||i<0||i>=n||excluded.includes(i)))throw new Error('Choose distinct analysis columns');
    return indices;
  };
  const complete=indices=>{
    if(new Set(indices).size!==indices.length)throw new Error('Roles must use different columns');
    const selected=rows.map(row=>indices.map(i=>String(row[i]??'').trim()));
    if(selected.some(row=>row.some(cell=>!cell)))throw new Error('Complete selected rows required');return selected;
  };
  const list=values=>'['+values.join(',')+']',table=values=>list(values.map(list));
  const label=(at,fallback)=>columnLabels[at]||fallback;
  const featureLabels=indices=>indices.forEach((at,i)=>{labels[`feature:${i+1}`]=label(at,`Feature ${i+1}`);});
  let expression;
  if(id==='dunn'){
    const plan=statisticsComparisonData(rows,opts,{all:true});
    if(plan.samples.length<2)throw new Error('Choose at least two groups');
    plan.labels.forEach((at,i)=>{labels[`sample:${i+1}`]=opts.grouping==='groups'?at:label(Number(at),`Sample ${i+1}`);});
    expression=`dunn(${table(plan.samples)},${opts.adjustment})`;
  }else if(['cronbach','efa','cfa','sem','hcluster'].includes(id)){
    const multigroup=['cfa','sem'].includes(id)&&opts.groupMode==='multi',group=multigroup?col('group'):null;
    const indices=columns('columns',multigroup?[group]:[]),selected=['cfa','sem'].includes(id)&&opts.missing==='fiml'?rows.map(row=>indices.map(i=>String(row[i]??'').trim()||'NA')):complete(indices);featureLabels(indices);
    let efaSuffix=`${opts.factors},${opts.rotation}`;
    const samples=String(opts.factors)==='parallel'&&Number(opts.parallelSamples||0)===0?'100':opts.parallelSamples||'0';
    if(id==='efa'&&(opts.extraction==='pca'||Number(samples)>0||String(opts.factors)==='parallel'))efaSuffix+=`,${opts.extraction||'pa'},${samples},${opts.seed||'0'},${opts.percentile||'0.95'}`;
    const suffix=id==='cronbach'?opts.mode:id==='efa'?efaSuffix:id==='hcluster'?`${opts.clusters},${opts.linkage},${opts.standardize}`:null;
    if(suffix!==null)expression=`${id}(${table(selected)},${suffix})`;
    else{
      const factors=String(opts.factors).split(',').map(value=>value.trim());
      if(factors.length!==indices.length||factors.some(value=>!/^\d+$/.test(value)||Number(value)<1))throw new Error('Specify one positive factor ID per selected indicator');
      const paths=String(opts.paths??'').trim().split(';').filter(Boolean).map(value=>value.split(',').map(token=>token.trim()));
      if(paths.some(pair=>pair.length!==2||pair.some(value=>!/^\d+$/.test(value))))throw new Error('Use latent paths like 1,2;2,3');
      const cross=String(opts.cross??'').trim().split(';').filter(Boolean).map(value=>value.split(',').map(token=>token.trim()));
      if(cross.some(pair=>pair.length!==2||pair.some(value=>!/^\d+$/.test(value)||Number(value)<1)))throw new Error('Use cross-loadings like 2,2;5,1 in selected indicator order');
      let extra='';
      if(cross.length||opts.missing==='fiml'||multigroup){
        let ids=[];
        if(multigroup){const groupRows=complete([group]),groups=[...new Set(groupRows.map(row=>row[0]))];ids=groupRows.map(row=>groups.indexOf(row[0])+1);groups.forEach((name,i)=>{labels[`group:${i+1}`]=name;});}
        extra=`,${table(cross)},${opts.missing||'complete'},${list(ids)},${opts.invariance||'configural'}`;
      }
      expression=`${id}(${table(selected)},${list(factors)}${id==='sem'?','+table(paths):''}${extra})`;
    }
  }else if(id==='manova'){
    if(opts.design==='factorial'){
      const factors=columns('factorColumns'),responses=columns('responses',factors),selected=complete([...factors,...responses]);
      const categories=factors.map((_,i)=>[...new Set(selected.map(row=>row[i]))]);
      factors.forEach((at,i)=>{labels[`factor:${i+1}`]=label(at,`Factor ${i+1}`);});
      responses.forEach((at,i)=>{labels[`response:${i+1}`]=label(at,`Response ${i+1}`);});
      expression=`manova(${table(selected.map(row=>row.map((v,i)=>i<factors.length?categories[i].indexOf(v)+1:v)))},factorial,${factors.length},${opts.order||'2'})`;
    }else if(opts.design==='repeated'){
      const responses=columns('responses');expression=`manova(${table(complete(responses))},repeated,${opts.occasions||'3'})`;
    }else{
    const group=col('group'),responses=columns('responses',[group]),selected=complete([group,...responses]),groups=[...new Set(selected.map(row=>row[0]))];
    groups.forEach((name,i)=>{labels[`group:${i+1}`]=name;});responses.forEach((at,i)=>{labels[`response:${i+1}`]=label(at,`Response ${i+1}`);});
    expression=`manova(${table(selected.map(row=>[groups.indexOf(row[0])+1,...row.slice(1)]))})`;
    }
  }else if(['mediation','moderation'].includes(id)){
    const x=col('x'),middle=col('middle'),response=col('response'),covariates=columns('covariates',[x,middle,response],true),indices=[x,middle,...covariates];
    indices.forEach((at,i)=>{labels[`x${i+1}`]=label(at,`x${i+1}`);});labels['x1:x2']=`${labels.x1}:${labels.x2}`;
    expression=`${id}(${table(complete([...indices,response]))}${id==='mediation'?`,${opts.samples},${opts.seed}`:''})`;
  }else if(['cramerv','phi','cohenkappa'].includes(id)){
    let counts;
    if(opts.layout==='counts')counts=complete(columns('columns'));
    else{
      const first=col('first'),second=col('second'),pairs=complete([first,second]);
      let left=[...new Set(pairs.map(row=>row[0]))],right=[...new Set(pairs.map(row=>row[1]))];
      if(id==='cohenkappa'){
        const categories=String(opts.categories??'').trim();
        if(opts.weights!=='unweighted'&&!categories)throw new Error('Specify category order for weighted kappa');
        left=categories?categories.split(',').map(value=>value.trim()):[...new Set(pairs.flat())];right=left;
        if(new Set(left).size!==left.length||left.some(value=>!value)||pairs.flat().some(value=>!left.includes(value)))throw new Error('Category order must contain every observed category exactly once');
      }
      counts=left.map(a=>right.map(b=>pairs.filter(row=>row[0]===a&&row[1]===b).length));
      labels['table:row']=label(first,'First');labels['table:column']=label(second,'Second');
      left.forEach((name,i)=>{labels[`table:row:${i+1}`]=name;});right.forEach((name,i)=>{labels[`table:column:${i+1}`]=name;});
    }
    expression=`${id}(${table(counts)}${id==='cohenkappa'?','+opts.weights:''})`;
  }else{
    const response=col('response'),predictors=columns('predictors',[response]),selected=complete([...predictors,response]);
    predictors.forEach((at,i)=>{labels[`x${i+1}`]=label(at,`x${i+1}`);labels[`Count: x${i+1}`]=labels[`Inflation: x${i+1}`]=labels[`x${i+1}`];});
    if(id==='discriminantanalysis'){
      const classes=[...new Set(selected.map(row=>row.at(-1)))];classes.forEach((name,i)=>{labels[`class:${i+1}`]=name;});
      expression=`discriminantanalysis(${table(selected.map(row=>[...row.slice(0,-1),classes.indexOf(row.at(-1))+1]))},${opts.method},${opts.prior})`;
    }else expression=`${id}(${table(selected)},${id==='quantreg'?opts.quantile:id==='tobit'?`${opts.lower},${opts.upper}`:`${opts.family},${opts.inflation}`})`;
  }
  return {expression,labels};
}
