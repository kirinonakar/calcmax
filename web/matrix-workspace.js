import {$,value,element,control} from './app-ui.js';
import {getLanguage,setText,t} from './i18n.js';
import {astSource} from './ast-source.js';
import {editableTable} from './editable-table.js';
import {renderFormulas} from './formula-preview.js';

export function createMatrixWorkspace({state,persist,restoreSelect,refreshWorkspaceMath,storeExpression,error,changeMode,replaceInput}) {
  const arithmetic={add:'+',subtract:'-',multiply:'*',divide:'/'};
  const binaryFunctions=['dot','cross','angle','projection','linsolve'];
  let namesInitialized=false;
  function storedShape(tree) {
    if(tree?.kind==='restricted')tree=tree.args[0];
    if(tree?.kind!=='list'||!tree.args.length)return null;
    const nested=tree.args.every(row=>row.kind==='list');
    const rows=nested?tree.args.map(row=>row.args):tree.args.map(cell=>[cell]);
    const columns=rows[0].length;
    if(!columns||rows.some(row=>row.length!==columns||row.some(cell=>cell.kind==='list')))return null;
    return {tree,rows,columns,matrix:nested,vector:!nested||rows.length===1||columns===1};
  }
  function renderSavedValues() {
    renderVariableNames();
    renderOperandNames();
    const list=$('matrix-saved-list');list.replaceChildren();
    for(const name of Object.keys(state.variables).sort()) {
      const shape=storedShape(state.variables[name]);if(!shape)continue;
      const row=element('div','','list-row'),content=element('div','','content');
      const size=shape.matrix?`${shape.rows.length} × ${shape.columns}`:String(shape.rows.length);
      const title=element('strong');title.textContent=`${name} · ${t(shape.matrix?'Matrix':'Vector')} · ${size}`;
      const preview=element('div','','formula-preview');renderFormulas(preview,[astSource(shape.tree)],{digits:state.digits});
      const load=control('Load variable',()=>loadStored(name,true));load.dataset.variable=name;
      load.disabled=shape.rows.length>9||shape.columns>9;
      if(load.disabled)load.title=t('The grid supports up to 9 × 9 matrices or 9 vector components.');
      content.append(title,preview);row.append(content,load);list.append(row);
    }
    if(!list.childElementCount)list.append(element('p','No saved matrices or vectors.','hint'));
  }
  function variableNameControls() {$('matrix-new-name-label').hidden=value('matrix-name')!=='';}
  function renderOperandNames() {
    const select=$('matrix-other-name'),direct=element('option','Direct input');direct.value='';
    const names=Object.keys(state.variables).filter(name=>name!=='Ans'&&state.variables[name]?.kind).sort();
    select.replaceChildren(direct,...names.map(name=>{const option=element('option');option.textContent=name;option.value=name;return option;}));
    const source=value('vector-other').trim();select.value=names.includes(source)?source:'';
    $('vector-other').hidden=select.value!=='';
  }
  function operandExpression() {
    const source=value('vector-other').trim(),tree=state.variables[source];
    return tree?`${source}=${astSource(tree)}`:source;
  }
  function renderVariableNames() {
    const select=$('matrix-name');
    const previous=namesInitialized?select.value:state.fields?.['matrix-name']??'A';
    const names=new Set(['A','B','C',...Object.keys(state.variables).filter(name=>storedShape(state.variables[name]))]);
    if(/^[A-Za-z][A-Za-z0-9_]*$/.test(previous)&&previous!=='Ans')names.add(previous);
    select.replaceChildren(...[...names].sort().map(name=>{
      const option=element('option');option.textContent=name;option.value=name;
      const shape=storedShape(state.variables[name]);option.disabled=!!shape&&(shape.rows.length>9||shape.columns>9);
      if(option.disabled)option.title=t('The grid supports up to 9 × 9 matrices or 9 vector components.');
      return option;
    }));
    const custom=element('option','New variable…');custom.value='';select.append(custom);
    select.value=previous;namesInitialized=true;variableNameControls();
  }
  function loadStored(name,switchMode=false) {
    const shape=storedShape(state.variables[name]);
    if(!shape){error('Select a stored matrix or vector');return;}
    const currentVector=value('mode')==='vector';
    const vector=switchMode?(currentVector?shape.vector:!shape.matrix):currentVector;
    if(vector&&!shape.vector||!vector&&!shape.matrix){error('Select a stored matrix or vector');return;}
    const rows=vector&&shape.rows.length===1?[...shape.rows[0]].map(cell=>[cell]):shape.rows;
    const columns=vector?1:shape.columns;
    if(rows.length>9||columns>9){error('The grid supports up to 9 × 9 matrices or 9 vector components.');return;}
    $('matrix-name').value=name;$('matrix-rows').value=String(rows.length);
    if(!vector)$('matrix-cols').value=String(columns);
    rows.forEach((row,i)=>row.forEach((cell,j)=>state.matrixCells[`${vector?'v':'m'}-${i}-${j}`]=astSource(cell)));
    if(vector!==currentVector)changeMode(vector?'vector':'matrix');
    renderMatrix();refreshWorkspaceMath();persist();
  }
  function operandControls() {
    const vector=value('mode')==='vector',op=value('matrix-op'),scalar=op==='divide'||vector&&op==='multiply';
    $('vector-other-label').hidden=!(op in arithmetic||binaryFunctions.includes(op));
    setText($('matrix-other-title'),scalar?'Scalar (k)':vector?'Second vector (B)':op==='multiply'?'Second matrix / scalar (B)':'Second matrix / vector (B)');
    const placeholder=scalar?'2 or a stored scalar':vector?'B or [4,5,6]':'B or [[1,2],[3,4]]';
    $('vector-other').dataset.i18nplaceholder=placeholder;
    $('vector-other').placeholder=t(placeholder);
    setText($('matrix-operation-hint'),scalar?'k must be a scalar; division requires a nonzero value.':vector?'Addition, subtraction, dot, angle and projection need matching vector lengths. Cross needs 3 components.':'Addition and subtraction need matching sizes. For matrix multiplication, the columns of A must match the rows of B. Multiplication by a scalar is also supported.');
    renderOperandNames();
  }
  function renderMatrix() {
    const vector=value('mode')==='vector',rows=Number(value('matrix-rows')),cols=vector?1:Number(value('matrix-cols'));
    $('matrix-cols-label').hidden=vector;
    const previous=value('matrix-op')||state.fields?.['matrix-op'],options=vector?['norm','normalize','add','subtract','multiply','divide','dot','cross','angle','projection']:['det','inverse','transpose','rank','trace','ref','rref','eigenvalues','eigenvectors','lu','qr','cholesky','nullspace','charpoly','add','subtract','multiply','divide','linsolve'];
    $('matrix-op').replaceChildren(...options.map(name=>{const option=element('option',name);option.value=name;return option;}));
    $('matrix-op').value=options.includes(previous)?previous:options[0];
    operandControls();
    const key=(row,col)=>`${vector?'v':'m'}-${row}-${col}`;
    const table=editableTable({rows,columns:Array.from({length:cols},(_,i)=>vector?'x':String(i+1)),label:vector?'Vector':'Matrix',
      value:(row,col)=>state.matrixCells[key(row,col)]??(vector?String(row+1):row===col?'1':'0'),
      onInput:(row,col,value)=>{state.matrixCells[key(row,col)]=value;refreshWorkspaceMath();persist();}});
    for(const input of table.querySelectorAll('input')){input.dataset.cell=key(input.dataset.row,input.dataset.column);input.setAttribute('aria-label',getLanguage()==='ko'?`${Number(input.dataset.row)+1}행 ${Number(input.dataset.column)+1}열`:`Row ${Number(input.dataset.row)+1}, column ${Number(input.dataset.column)+1}`);}
    $('matrix-grid').replaceChildren(table);
    renderSavedValues();
  }
  for(const id of ['matrix-rows','matrix-cols']) {for(let i=1;i<=9;i++)$(id).append(element('option',String(i)));$(id).value=id==='matrix-rows'?'3':'3';restoreSelect(id);$(id).onchange=()=>{renderMatrix();persist();};}
  function matrixExpression(){const vector=value('mode')==='vector',columns=vector?1:Number(value('matrix-cols')),cells=Array.from($('matrix-grid').querySelectorAll('input')).map(input=>input.value||'0');if(vector)return `[${cells.join(',')}]`;const rows=[];for(let i=0;i<cells.length;i+=columns)rows.push(`[${cells.slice(i,i+columns).join(',')}]`);return `[${rows.join(',')}]`;}
  $('matrix-op').onchange=()=>{operandControls();refreshWorkspaceMath();persist();};
  $('matrix-name').onchange=()=>{variableNameControls();const name=value('matrix-name');if(storedShape(state.variables[name]))loadStored(name,true);else persist();};
  $('matrix-other-name').onchange=()=>{
    const name=value('matrix-other-name');if(name)$('vector-other').value=name;
    $('vector-other').hidden=name!=='';refreshWorkspaceMath();persist();
  };
  $('vector-other').oninput=()=>{$('matrix-other-name').value='';refreshWorkspaceMath();persist();};
  $('matrix-store').onclick=async()=>{
    const name=(value('matrix-name')||value('matrix-new-name')).trim();
    if(!/^[A-Za-z][A-Za-z0-9_]*$/.test(name)||name==='Ans'){error('Enter a valid variable name');return;}
    await storeExpression(name,matrixExpression());renderSavedValues();refreshWorkspaceMath();
    if(storedShape(state.variables[name])){$('matrix-name').value=name;variableNameControls();persist();}
  };
  $('matrix-load').onclick=()=>loadStored(value('matrix-name').trim(),true);
  $('dialog').addEventListener('close',()=>{renderSavedValues();refreshWorkspaceMath();});
  $('matrix-clear').onclick=()=>{$('matrix-grid').querySelectorAll('input').forEach(input=>{input.value='0';state.matrixCells[input.dataset.cell]='0';});refreshWorkspaceMath();persist();};
  $('matrix-insert').onclick=()=>{const source=matrixExpression();changeMode('scientific');replaceInput(source,{uncommit:true});};

  function command() {
    const op=value('matrix-op');
    const other=value('vector-other').trim();
    if((op in arithmetic||binaryFunctions.includes(op))&&!other)throw new Error(t('Enter the second operand.'));
    return op in arithmetic?`(${matrixExpression()})${arithmetic[op]}(${other})`:`${op}(${matrixExpression()}${binaryFunctions.includes(op)?','+other:op==='charpoly'?',x':''})`;
  }
  return {render:renderMatrix,expression:matrixExpression,operandExpression,command};
}
