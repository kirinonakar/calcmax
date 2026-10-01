import {$,value,element} from './app-ui.js';
import {getLanguage} from './i18n.js';
import {astSource} from './ast-source.js';
import {editableTable} from './editable-table.js';

export function createMatrixWorkspace({state,persist,restoreSelect,refreshWorkspaceMath,storeExpression,error,changeMode,replaceInput}) {
  function renderMatrix() {
    const vector=value('mode')==='vector',rows=Number(value('matrix-rows')),cols=vector?1:Number(value('matrix-cols'));
    $('matrix-cols-label').hidden=vector;
    const previous=value('matrix-op'),options=vector?['norm','normalize','dot','cross','angle','projection']:['det','inverse','transpose','rank','trace','ref','rref','eigenvalues','eigenvectors','lu','qr','cholesky','nullspace','charpoly','add','subtract','multiply','linsolve'];
    $('matrix-op').replaceChildren(...options.map(name=>{const option=element('option',name);option.value=name;return option;}));
    $('matrix-op').value=options.includes(previous)?previous:options[0];
    $('vector-other-label').hidden=!['dot','cross','angle','projection','add','subtract','multiply','linsolve'].includes(value('matrix-op'));
    const key=(row,col)=>`${vector?'v':'m'}-${row}-${col}`;
    const table=editableTable({rows,columns:Array.from({length:cols},(_,i)=>vector?'x':String(i+1)),label:vector?'Vector':'Matrix',
      value:(row,col)=>state.matrixCells[key(row,col)]??(vector?String(row+1):row===col?'1':'0'),
      onInput:(row,col,value)=>{state.matrixCells[key(row,col)]=value;refreshWorkspaceMath();persist();}});
    for(const input of table.querySelectorAll('input')){input.dataset.cell=key(input.dataset.row,input.dataset.column);input.setAttribute('aria-label',getLanguage()==='ko'?`${Number(input.dataset.row)+1}행 ${Number(input.dataset.column)+1}열`:`Row ${Number(input.dataset.row)+1}, column ${Number(input.dataset.column)+1}`);}
    $('matrix-grid').replaceChildren(table);
  }
  for(const id of ['matrix-rows','matrix-cols']) {for(let i=1;i<=9;i++)$(id).append(element('option',String(i)));$(id).value=id==='matrix-rows'?'3':'3';restoreSelect(id);$(id).onchange=()=>{renderMatrix();persist();};}
  function matrixExpression(){const vector=value('mode')==='vector',columns=vector?1:Number(value('matrix-cols')),cells=Array.from($('matrix-grid').querySelectorAll('input')).map(input=>input.value||'0');if(vector)return `[${cells.join(',')}]`;const rows=[];for(let i=0;i<cells.length;i+=columns)rows.push(`[${cells.slice(i,i+columns).join(',')}]`);return `[${rows.join(',')}]`;}
  $('matrix-op').onchange=()=>{$('vector-other-label').hidden=!['dot','cross','angle','projection','add','subtract','multiply','linsolve'].includes(value('matrix-op'));refreshWorkspaceMath();};
  $('matrix-store').onclick=()=>{const name=value('matrix-name').trim();if(!/^[A-Za-z][A-Za-z0-9_]*$/.test(name)||name==='Ans'){error('Enter a valid variable name');return;}storeExpression(name,matrixExpression());};
  $('matrix-load').onclick=()=>{let tree=state.variables[value('matrix-name')];if(tree?.kind==='restricted')tree=tree.args[0];if(tree?.kind!=='list'){error('Select a stored matrix or vector');return;}const vector=value('mode')==='vector',rows=tree.args,columns=vector?1:rows[0]?.args?.length;if(!columns||rows.length>9||columns>9||!vector&&rows.some(row=>row.kind!=='list'||row.args.length!==columns)){error('Select a stored matrix or vector');return;}$('matrix-rows').value=String(rows.length);$('matrix-cols').value=String(columns);rows.forEach((row,i)=>(vector?[row]:row.args).forEach((cell,j)=>state.matrixCells[`${vector?'v':'m'}-${i}-${j}`]=astSource(cell)));renderMatrix();refreshWorkspaceMath();persist();};
  $('matrix-clear').onclick=()=>{$('matrix-grid').querySelectorAll('input').forEach(input=>{input.value='0';state.matrixCells[input.dataset.cell]='0';});refreshWorkspaceMath();persist();};
  $('matrix-insert').onclick=()=>{const source=matrixExpression();changeMode('scientific');replaceInput(source,{uncommit:true});};

  function command() {
    const op=value('matrix-op');
    return ['add','subtract','multiply'].includes(op)?`(${matrixExpression()})${{add:'+',subtract:'-',multiply:'*'}[op]}(${value('vector-other')})`:`${op}(${matrixExpression()}${['dot','cross','angle','projection','linsolve'].includes(op)?','+value('vector-other'):op==='charpoly'?',x':''})`;
  }
  return {render:renderMatrix,expression:matrixExpression,command};
}
