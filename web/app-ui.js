import {setText,translateDOM} from './i18n.js';

export const $=id=>document.getElementById(id);
export const value=id=>$(id).value;
export function control(label,action,className='') {
  const button=element('button',label,className);
  button.type='button';button.addEventListener('click',action);
  return button;
}
export function element(tag,text='',className='') {
  const node=document.createElement(tag);
  setText(node,text);node.className=className;
  return node;
}

export function createAppUI() {
  let toastTimer=null;
  function toast(message) {
    setText($('toast'),message);$('toast').hidden=false;
    clearTimeout(toastTimer);toastTimer=setTimeout(()=>$('toast').hidden=true,3500);
  }
  function openDialog(title,content) {
    $('dialog').classList.toggle('catalog-dialog',content.classList.contains('catalog-content'));
    setText($('dialog-title'),title);$('dialog-body').replaceChildren(content);
    translateDOM($('dialog'));if(!$('dialog').open)$('dialog').showModal();
  }
  async function clipboard(text) {
    try {await navigator.clipboard.writeText(text);toast('복사했습니다.');}
    catch {const field=element('textarea');field.value=text;openDialog('복사할 텍스트',field);field.select();}
  }
  async function pickFile(accept,handler) {
    const field=$('file-input');field.accept=accept;field.value='';
    field.onchange=async()=>{const file=field.files[0];if(!file)return;try{await handler(file);}catch(exc){toast(exc.message);}};
    field.click();
  }
  $('dialog-close').onclick=()=>$('dialog').close();
  $('dialog').addEventListener('click',event=>{
    if(event.target!==$('dialog'))return;
    const r=$('dialog').getBoundingClientRect();
    if(event.clientX<r.left||event.clientX>r.right||event.clientY<r.top||event.clientY>r.bottom)$('dialog').close();
  });
  return {toast,openDialog,clipboard,pickFile,dispose:()=>clearTimeout(toastTimer)};
}
