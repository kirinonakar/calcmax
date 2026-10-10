import {csvRows,statisticsCsvHasHeader} from './workspace-commands.js';

// Apply full-precision engine values to missing cells, retaining observed text.
export function statisticsImputationCSV(source,filled,columnLimit){
  const rows=csvRows(source,{preserveEmptyRows:true,skipHeader:false});
  const selected=rows.map(row=>row.slice(0,columnLimit));
  const header=statisticsCsvHasHeader(selected)&&!selected[0].includes('NA')?1:0;
  if(!Array.isArray(filled)||filled.length!==rows.length-header||!filled.length)throw new Error('Imputation result does not match the current data');
  for(let i=0;i<filled.length;i++){
    if(!Array.isArray(filled[i])||filled[i].length!==Math.min(columnLimit,Math.max(...rows.map(row=>row.length))))throw new Error('Imputation result does not match the current data');
    for(let j=0;j<filled[i].length;j++){
      const previous=String(rows[i+header][j]??'').trim(),value=String(filled[i][j]);
      if(!Number.isFinite(Number(value))||!value.trim())throw new Error('Imputed values must be finite numbers');
      if(!previous||['NA','nan'].includes(previous))rows[i+header][j]=value;
    }
  }
  const quote=value=>/[",\r\n]/.test(value)?'"'+value.replaceAll('"','""')+'"':value;
  return rows.map(row=>row.map(quote).join(',')).join('\n');
}
