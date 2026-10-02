import {$,value,element,control} from './app-ui.js';
import {t} from './i18n.js';
import {downloadFile} from './storage.js';
import {bindPythonEditor} from './python-tools.js';

export function createPythonWorkspace({engine,ui,persist,requestOptions,error,run}) {
  const {pickFile,clipboard}=ui;
  $('python-new').onclick=()=>{$('python-source').value='';$('python-output').textContent='';persist();};
  $('python-open').onclick=()=>pickFile('.py,text/x-python',async file=>{$('python-source').value=await file.text();persist();});
  $('python-save').onclick=()=>downloadFile('calcmax.py',value('python-source'),'text/x-python');
  $('python-source').onkeydown=event=>{if(event.key==='Enter'&&(event.ctrlKey||event.metaKey)){event.preventDefault();run('python');}};
  const pythonToolbar=element('div','','form-row'),pythonSuggestions=element('div','','form-row');$('python-source').closest('label').insertAdjacentElement('afterend',pythonSuggestions);$('python-source').closest('label').insertAdjacentElement('beforebegin',pythonToolbar);
  bindPythonEditor($('python-source'),{toolbar:pythonToolbar,suggestions:pythonSuggestions,onEdit:persist,copy:clipboard,paste:()=>navigator.clipboard.readText()});
  function requestInput({prompt,output,signal}) {
    $('python-output').textContent=output+prompt;
    return new Promise(resolve=>{
      const dialog=element('dialog'),form=element('form'),label=element('label',prompt||'input()'),field=element('input'),actions=element('div','','form-row');
      dialog.setAttribute('aria-label',t('Python input'));
      field.type='text';field.autocomplete='off';label.append(field);
      const submit=element('button','Continue');submit.type='submit';
      actions.append(submit,control('Cancel',()=>finish(null)));form.append(label,actions);dialog.append(form);
      function finish(value){signal.removeEventListener('abort',abort);dialog.remove();resolve(value);}
      function abort(){finish(null);}
      form.onsubmit=event=>{event.preventDefault();finish(field.value);};
      dialog.addEventListener('cancel',event=>{event.preventDefault();finish(null);});
      dialog.addEventListener('close',()=>finish(null));
      signal.addEventListener('abort',abort,{once:true});
      if(signal.aborted){finish(null);return;}
      document.body.append(dialog);dialog.showModal();field.focus();
    });
  }
  async function execute() {
    $('python-output').textContent=t('실행 중…');
    const result=await engine.execute({...requestOptions(),action:'python',source:value('python-source'),inputs:value('python-input')===''?[]:value('python-input').split(/\r?\n/),filename:'calcmax.py'},{onInput:requestInput});
    $('python-output').textContent=[result.output,result.error&&t(result.error)].filter(Boolean).join('\n')||t('실행 완료');
    if(!result.ok)error(result.error);
  }
  return {run:execute};
}
