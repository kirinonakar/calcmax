// One sample plan for independent groups, paired groups and repeated conditions.
export function statisticsComparisonData(rows,settings={}, {all=false,paired=false,single=false,strict=false}={}){
  const width=Math.max(0,...rows.map(row=>row.length));
  const cell=(row,i)=>String(row[i]??'').trim();
  const column=(key,fallback)=>{const i=Number(settings[key]??fallback);if(!Number.isInteger(i)||i<0||i>=width)throw new Error('Choose valid data columns');return i;};
  if(settings.grouping!=='groups'){
    const columns=all?(settings.columns===undefined||settings.columns==='auto'?Array.from({length:width},(_,i)=>i):String(settings.columns).split(',').filter(Boolean).map(Number)):
      single?[column('first',0)]:[column('first',0),column('second',1)];
    if(new Set(columns).size!==columns.length||columns.some(i=>!Number.isInteger(i)||i<0||i>=width))throw new Error('Choose distinct analysis columns');
    let matrix=rows.map(row=>columns.map(i=>cell(row,i)));
    if(paired){if(strict&&matrix.some(row=>row.some(value=>!value)))throw new Error('Complete selected rows required');matrix=matrix.filter(row=>row.every(Boolean));}
    return {labels:columns.map(String),samples:columns.map((at,i)=>paired?matrix.map(row=>row[i]):rows.map(row=>cell(row,at)).filter(Boolean)),matrix,omitted:paired?rows.length-matrix.length:0};
  }
  const group=column('group',0),value=column('value',1);
  if(group===value)throw new Error('Roles must use different columns');
  const labels=[...new Set(rows.map(row=>cell(row,group)).filter(Boolean))];
  const first=labels.includes(settings.firstGroup)?settings.firstGroup:labels[0];
  const second=labels.includes(settings.secondGroup)&&settings.secondGroup!==first?settings.secondGroup:labels.find(label=>label!==first);
  const selected=all?labels:single?[first]:[first,second];
  if(selected.some(label=>!label)||new Set(selected).size!==selected.length)throw new Error('Choose distinct groups');
  const members=selected.map(label=>rows.filter(row=>cell(row,group)===label));
  let samples=members.map(groupRows=>groupRows.map(row=>cell(row,value)).filter(Boolean)),matrix=[],omitted=0;
  if(paired){
    if(settings.matching==='subject'){
      const subject=column('subject',0);if([group,value].includes(subject))throw new Error('Roles must use different columns');
      const maps=members.map(groupRows=>{const map=new Map();for(const row of groupRows){const id=cell(row,subject);if(!id)throw new Error('Subject IDs are required for paired groups');if(map.has(id))throw new Error('Each subject must occur once per group');map.set(id,cell(row,value));}return map;});
      const ids=[...new Set(maps.flatMap(map=>[...map.keys()]))];
      matrix=ids.filter(id=>maps.every(map=>map.get(id))).map(id=>maps.map(map=>map.get(id)));omitted=ids.length-matrix.length;
    }else{
      if(members.some(groupRows=>groupRows.some(row=>!cell(row,value))))throw new Error('Missing paired values require subject-ID matching');
      if(new Set(samples.map(sample=>sample.length)).size!==1)throw new Error('Paired groups must have equal lengths');
      matrix=samples[0].map((_,i)=>samples.map(sample=>sample[i]));
    }
    samples=selected.map((_,i)=>matrix.map(row=>row[i]));
  }
  return {labels:selected,samples,matrix,omitted};
}
