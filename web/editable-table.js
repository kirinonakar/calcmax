import {element,control} from './app-ui.js';
import {t} from './i18n.js';

// One spreadsheet surface for statistics, matrices, and vectors.
export function editableTable({rows,columns,value,onInput,onDeleteRow,onMoveColumn,onDeleteColumn,label}) {
  const table=element('table','','editable-table'),colgroup=element('colgroup'),head=element('thead'),heading=element('tr'),body=element('tbody');
  // Fixed table layout needs explicit column sizes; cell min-width is unreliable.
  table.style.minWidth=`${columns.length*80+36+(onDeleteRow?36:0)}px`;
  const numberColumn=element('col');numberColumn.style.width='36px';colgroup.append(numberColumn);
  colgroup.append(...columns.map(()=>element('col')));
  if(onDeleteRow){const actionColumn=element('col');actionColumn.style.width='36px';colgroup.append(actionColumn);}
  table.setAttribute('aria-label',label);
  heading.append(element('th','#'));
  for(const name of columns){const th=element('th',name);th.scope='col';heading.append(th);}
  if(onDeleteRow)heading.append(element('th',''));
  head.append(heading);
  // Column actions mirror the Android statistics table editor: move left, move right, delete.
  if((onMoveColumn||onDeleteColumn)&&columns.length>1){
    const actions=element('tr','','table-column-actions');
    actions.append(element('th',''));
    columns.forEach((_,col)=>{
      const cell=element('th');
      if(onMoveColumn){
        const left=control('◀',()=>onMoveColumn(col,-1));
        left.disabled=col===0;left.dataset.column=String(col);left.dataset.columnAction='left';left.setAttribute('aria-label',t('Move column left'));
        const right=control('▶',()=>onMoveColumn(col,1));
        right.disabled=col===columns.length-1;right.dataset.column=String(col);right.dataset.columnAction='right';right.setAttribute('aria-label',t('Move column right'));
        cell.append(left,right);
      }
      if(onDeleteColumn){
        const remove=control('−',()=>onDeleteColumn(col));
        remove.dataset.column=String(col);remove.dataset.columnAction='delete';remove.setAttribute('aria-label',t('Delete column'));
        cell.append(remove);
      }
      actions.append(cell);
    });
    if(onDeleteRow)actions.append(element('th',''));
    head.append(actions);
  }
  for(let row=0;row<rows;row++){
    const tr=element('tr'),number=element('th',String(row+1));number.scope='row';tr.append(number);
    for(let col=0;col<columns.length;col++){
      const td=element('td'),input=element('input');
      input.value=value(row,col);input.dataset.row=String(row);input.dataset.column=String(col);
      input.setAttribute('aria-label',`${columns[col]}, ${row+1}`);
      input.autocomplete='off';input.spellcheck=false;input.size=1;
      input.oninput=()=>onInput(row,col,input.value,input);
      input.onkeydown=event=>{
        const delta={ArrowLeft:[0,-1],ArrowRight:[0,1],ArrowUp:[-1,0],ArrowDown:[1,0]}[event.key];
        if(!delta||event.isComposing||event.altKey||event.ctrlKey||event.metaKey||event.shiftKey)return;
        event.preventDefault();event.stopPropagation();
        const next=table.querySelector(`input[data-row="${row+delta[0]}"][data-column="${col+delta[1]}"]`);
        if(next){next.focus({preventScroll:true});next.select();next.scrollIntoView?.({block:'nearest',inline:'nearest'});}
      };
      td.append(input);tr.append(td);
    }
    if(onDeleteRow){const td=element('td'),remove=control('−',()=>onDeleteRow(row));remove.setAttribute('aria-label',`${t('Delete')} ${row+1}`);td.className='table-row-action';td.append(remove);tr.append(td);}
    body.append(tr);
  }
  table.append(colgroup,head,body);return table;
}
