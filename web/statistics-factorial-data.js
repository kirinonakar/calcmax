export function statisticsFactorialData(rows,opts,columns=[]){
  const width=Math.max(0,...rows.map(row=>row.length));
  const col=key=>{const raw=Number(opts[key]),i=raw===-1?width-1:raw;if(!Number.isInteger(i)||i<0||i>=width)throw new Error('Choose valid data columns');return i;};
  const multiple=key=>opts[key]==='auto'?Array.from({length:width},(_,i)=>i).filter(i=>i!==col('response')):String(opts[key]??'').split(',').filter(Boolean).map(Number);
  const labels={'response':columns[col('response')]||'Response'},levels={};let selected,categorical;
  if(opts.factorA!==undefined){
    if(opts.layout==='columns'){
      const indices=opts.columns==='auto'?Array.from({length:width},(_,i)=>i):String(opts.columns).split(',').filter(Boolean).map(Number),b=Number(opts.levelsB);
      if(!Number.isInteger(b)||b<2||indices.length%b||indices.length/b<2)throw new Error('Cell columns must contain every factor combination');
      if(new Set(indices).size!==indices.length||indices.some(i=>!Number.isInteger(i)||i<0||i>=width))throw new Error('Choose distinct analysis columns');
      const encoded=indices.flatMap((at,i)=>rows.map(row=>String(row[at]??'').trim()).filter(Boolean).map(value=>[String(Math.floor(i/b)+1),String(i%b+1),value]));
      indices.forEach((at,i)=>labels[`cell:${Math.floor(i/b)+1}:${i%b+1}`]=columns[at]||`A${Math.floor(i/b)+1} × B${i%b+1}`);
      for(let i=0;i<indices.length/b;i++)labels[`factor:A:${i+1}`]=`A${i+1}`;
      for(let i=0;i<b;i++)labels[`factor:B:${i+1}`]=`B${i+1}`;
      return {encoded,labels};
    }
    selected=[col('factorA'),col('factorB'),col('response')];categorical=selected.slice(0,2);
    labels['factor:A']=columns[selected[0]]||'Factor A';labels['factor:B']=columns[selected[1]]||'Factor B';
  }else{
    const response=col('response'),predictors=multiple('predictors');categorical=opts.categorical==='auto'?predictors:multiple('categorical');selected=[...predictors,response];
    if(!predictors.length||categorical.some(i=>!predictors.includes(i)))throw new Error('Categorical columns must be selected predictors');
    predictors.forEach((at,i)=>labels[`x${i+1}`]=columns[at]||`x${i+1}`);
  }
  if(new Set(selected).size!==selected.length||selected.some(i=>!Number.isInteger(i)||i<0||i>=width))throw new Error('Choose distinct analysis columns');
  if(rows.some(row=>selected.some(i=>!String(row[i]??'').trim())))throw new Error('Complete selected rows required');
  categorical.forEach(at=>levels[at]=[...new Set(rows.map(row=>String(row[at]).trim()))]);
  const encoded=rows.map(row=>selected.map(at=>categorical.includes(at)?String(levels[at].indexOf(String(row[at]).trim())+1):String(row[at]).trim()));
  categorical.forEach(at=>levels[at].forEach((value,i)=>labels[opts.factorA!==undefined?`factor:${at===selected[0]?'A':'B'}:${i+1}`:`level:${selected.indexOf(at)+1}:${i+1}`]=value));
  return {encoded,labels,categorical:categorical.map(at=>String(selected.indexOf(at)+1))};
}
