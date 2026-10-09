import {setComputationLimitsRemoved} from './computation-limits.js';
import {$,value,element,control} from './app-ui.js';
import {t,setText,setLanguage,getLanguage,translateDOM,applyTheme} from './i18n.js';
import {parse,latexInput} from './parser.js';
import {renderFormulas} from './formula-preview.js';
import {astSource} from './ast-source.js';
import {csvRows,statisticsDatasetSource,statisticsColumnCount,statisticsKindForColumns} from './workspace-commands.js';
import {writeState,downloadFile} from './storage.js';
import {scientificRows,secondRows,numericRows,topKeys,topFunctions} from './keypad.js';
import {appVersion} from './app-version.js';
import {parseCatalogHelp,helpExampleInput} from './catalog-help.js';
import {graphColorSettings} from './graph-color-settings.js';

export function createAppDialogs({state,ui,persist,calculator,changeMode,pressKey,refreshDisplays,renderMatrix,error}) {
  const {toast,openDialog,pickFile}=ui;
  let catalog={},graphSettings=null;
  function clearableCatalogSearch(input){
    const holder=element('div','','catalog-search'),clear=control('✕',()=>{
      input.value='';input.dispatchEvent(new input.ownerDocument.defaultView.Event('input',{bubbles:true}));input.focus();
    },'catalog-search-clear');
    clear.setAttribute('aria-label',t('Clear search'));clear.title=t('Clear search');
    const update=()=>{clear.hidden=!input.value;};input.addEventListener('input',update);update();
    holder.append(input,clear);return holder;
  }
  $('about-button').onclick=()=>{
    const content=element('div','','about-content'),icon=element('img'),version=element('p',`v${appVersion}`,'hint'),link=element('a','https://github.com/kirinonakar/symvacas');
    icon.src='app-icon.png';icon.alt='SymvaCAS';icon.width=64;icon.height=64;
    link.href='https://github.com/kirinonakar/symvacas';link.target='_blank';link.rel='noopener noreferrer';
    content.append(icon,version,link,control('Close',()=>$('dialog').close()));openDialog('SymvaCAS',content);
  };
  function renderDisplayShortcuts(){const toolbar=$('exact-toggle').parentElement;toolbar.querySelectorAll('[data-shortcut]').forEach(button=>button.remove());for(const shortcut of state.displayShortcuts){const button=control(shortcut.label,()=>{if(shortcut.source==='catalog')calculator.insert(shortcut.input,shortcut.input.includes('[]')?shortcut.input.indexOf('[]')+1:shortcut.input.includes('(')?shortcut.input.indexOf('(')+1:shortcut.input.length);else pressKey(shortcut.input);});button.dataset.shortcut=shortcut.input;toolbar.insertBefore(button,$('answer-copy'));}toolbar.append($('shortcut-settings'));}
  $('shortcut-settings').onclick=()=>{
    const content=element('div'),current=element('div'),sources=element('div','','catalog-tabs'),tabs=element('div','','catalog-tabs'),search=element('input'),choices=element('div','','shortcut-choices');
    search.placeholder=t('Find button or function');search.setAttribute('aria-label',t('Find button or function'));
    let source='Keypad',category='Main keys';
    const keyChoices=keys=>keys.flatMap(k=>[{label:k.title,input:k.input},...(k.alternate?[{label:k.secondary||k.alternate,input:k.alternate}]:[]),...(k.alpha?[{label:k.alpha,input:k.input==='CALC'?'RELATION':k.alpha}]:[])]);
    const keypadGroups={
      'Main keys':keyChoices([...topKeys(),...topFunctions(),...['UP','LEFT','RIGHT','DOWN'].map((input,i)=>({title:['▲','◀','▶','▼'][i],input})),...scientificRows.flat()]),
      '2nd keys':keyChoices([...topFunctions(true),...secondRows.flat()]),
      'Number keys':keyChoices(numericRows.flat()),
      ALPHA:Array.from('ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz',label=>({label,input:label}))
    };
    function moveShortcut(index,offset){
      const target=index+offset;if(target<0||target>=state.displayShortcuts.length)return;
      [state.displayShortcuts[index],state.displayShortcuts[target]]=[state.displayShortcuts[target],state.displayShortcuts[index]];
      renderDisplayShortcuts();render();persist();
    }
    function render(){
      current.replaceChildren();for(const [index,shortcut] of state.displayShortcuts.entries()){
        const row=element('div','','list-row'),up=control('↑',()=>moveShortcut(index,-1)),down=control('↓',()=>moveShortcut(index,1));
        for(const [button,label] of [[up,'Move up'],[down,'Move down']]){button.setAttribute('aria-label',t(label));button.title=t(label);}
        up.disabled=index===0;down.disabled=index===state.displayShortcuts.length-1;
        row.append(element('span',shortcut.label,'content'),up,down,control('Delete',()=>{state.displayShortcuts.splice(index,1);renderDisplayShortcuts();render();persist();}));current.append(row);
      }
      const groups=source==='Keypad'?keypadGroups:Object.fromEntries(Object.entries(catalog).map(([name,items])=>[name,items.map(input=>({label:input.split('(')[0],input,source:'catalog'}))]));
      if(!groups[category])category=Object.keys(groups)[0];
      sources.querySelectorAll('button').forEach(button=>{const active=button.dataset.source===source;button.classList.toggle('active',active);button.setAttribute('aria-pressed',String(active));});
      tabs.replaceChildren();for(const name of Object.keys(groups)){const button=control(name,()=>{category=name;render();});button.dataset.category=name;button.classList.toggle('active',name===category);button.setAttribute('aria-pressed',String(name===category));tabs.append(button);}
      const query=search.value.trim().toLowerCase(),entries=query?Object.values(groups).flat().filter(choice=>`${choice.label} ${choice.input}`.toLowerCase().includes(query)):groups[category]||[];
      choices.replaceChildren();for(const choice of entries){const button=control(source==='Catalog'?choice.input:choice.label,()=>{if(state.displayShortcuts.length>=6){toast('Use up to six shortcuts');return;}state.displayShortcuts.push(choice);renderDisplayShortcuts();render();persist();});button.dataset.choice=choice.input;choices.append(button);}
      if(!entries.length)choices.append(element('p','No matching buttons','hint'));
    }
    for(const name of ['Keypad','Catalog']){const button=control(name,()=>{source=name;category=name==='Keypad'?'Main keys':'Scientific';render();});button.dataset.source=name;sources.append(button);}
    search.oninput=render;content.append(current,sources,clearableCatalogSearch(search),tabs,choices);render();openDialog('Customize display buttons',content);
  };
  renderDisplayShortcuts();
  function modeDialog(){const choices=element('div','','mode-choices');for(const option of $('mode').options)choices.append(control(option.textContent,()=>{changeMode(option.value);$('dialog').close();}));openDialog('Workspace',choices);}
  function matrixInsertDialog(){const content=element('div'),rows=element('input'),cols=element('input');for(const [input,name] of [[rows,'Rows / components'],[cols,'Column']]){input.type='number';input.min='1';input.max='9';input.value='2';const label=element('label',name);label.append(input);content.append(label);}content.append(control('Insert',()=>{const r=Number(rows.value),c=Number(cols.value);if(!Number.isInteger(r)||!Number.isInteger(c)||r<1||c<1||r>9||c>9)return;changeMode('scientific');calculator.insert(`[${Array.from({length:r},()=>`[${Array(c).fill('0').join(',')}]`).join(',')}]`);$('dialog').close();}));openDialog('Matrix',content);}
  function historyDialog() {
    const content=element('div','','history-content'),search=element('input'),actions=element('div','','history-actions');search.placeholder='수식 또는 결과 검색';search.setAttribute('aria-label','기록 검색');const list=element('div','','history-scroll');let favorites=false;
    const filter=control('즐겨찾기만',()=>{favorites=!favorites;setText(filter,favorites?'전체 기록':'즐겨찾기만');render();});
    function render(){list.replaceChildren();for(const item of state.history.filter(h=>(!favorites||h.star)&&`${h.source} ${h.exact}`.toLowerCase().includes(search.value.toLowerCase()))){const row=element('div','','list-row');const text=element('div','','content');text.append(element('code',item.source),element('div',`= ${item.exact}`));row.append(text,control(item.star?'★':'☆',()=>{item.star=!item.star;persist();render();}),control('사용',()=>{changeMode('scientific');$('expression').value=item.source;calculator.preview();$('dialog').close();}));list.append(row);}if(!list.childElementCount)list.append(element('p','기록이 없습니다.','hint'));}
    search.oninput=render;actions.append(filter,control('기록 삭제',()=>{state.history=state.history.filter(item=>item.star);calculator.clearHistorySelection();persist();render();}));content.append(search,actions,list);render();openDialog('History',content);
  }
  function catalogDialog() {
    const content=element('div','','catalog-content'),search=element('input'),tabs=element('div','','catalog-tabs'),list=element('div','','catalog-scroll');search.placeholder='함수 검색';search.setAttribute('aria-label','함수 검색');let category='Scientific';
    function entries(){if(search.value)return [...new Set([...Object.values(catalog).flat(),...custom()])].filter(s=>s.toLowerCase().includes(search.value.toLowerCase()));if(category==='Recent')return state.recent;if(category==='Favorites')return state.favorites;if(category==='Custom')return custom();return catalog[category]||[];}
    function custom(){return Object.entries(state.functions).map(([name,f])=>`${name}(${','.repeat(Math.max(0,f.parameters.length-1))})`);}
    function render(){list.replaceChildren();tabs.querySelectorAll('button').forEach(b=>b.classList.toggle('active',b.dataset.category===category));for(const source of entries()){
      const row=element('div','','catalog-entry');row.append(control(source,()=>{
        state.recent=[source,...state.recent.filter(s=>s!==source)].slice(0,50);persist();
        if(value('mode')==='python'){
          const field=$('python-source'),at=field.selectionStart;let draft=field.value;const inserted=`calc.${source.replace(/\b(real|complex|integer)\b/g,'calc.$1')}`;
          draft=draft.slice(0,at)+inserted+draft.slice(field.selectionEnd);
          if(!draft.includes('import symvacas_catalog as calc'))draft='import symvacas_catalog as calc\nfrom symvacas_catalog import x, y, z, t, pi\n'+draft;
          field.value=draft;field.focus();persist();
        }else{changeMode('scientific');calculator.insert(source,source==='rnd()'?source.length:source.includes('[]')?source.indexOf('[]')+1:source.indexOf('(')+1);}
        $('dialog').close();
      }),control(state.favorites.includes(source)?'★':'☆',()=>{state.favorites=state.favorites.includes(source)?state.favorites.filter(s=>s!==source):[...state.favorites,source];persist();render();}));list.append(row);
    }}
    for(const name of ['Recent','Favorites','Custom',...Object.keys(catalog)]){const tab=control(name,()=>{category=name;render();});tab.dataset.category=name;tabs.append(tab);}
    const tools=element('div','','catalog-tools');tools.append(clearableCatalogSearch(search),control('Help',catalogHelpDialog));
    search.oninput=()=>{list.scrollTop=0;render();};content.append(tools,tabs,list,element('p','빈 인수에 값을 입력하세요. 예: diff(sin(x^2),x), normcdf(-1.96,1.96)','hint'));
    render();openDialog('Catalog',content);
  }
  const helpDocuments={};
  async function catalogHelpDialog(){
    const language=getLanguage(),content=element('div','','catalog-content'),search=element('input'),tools=element('div','','catalog-tools'),list=element('div','','catalog-scroll');
    search.placeholder=t('Search');search.setAttribute('aria-label',t('Search'));tools.append(clearableCatalogSearch(search),control('Catalog',catalogDialog));content.append(tools,list);list.append(element('p','Loading the catalog reference...','hint'));openDialog('Function catalog - help',content);
    try{
      if(!helpDocuments[language]){const response=await fetch(language==='ko'?'./catalog_help_ko.md':'./catalog_help.md');if(!response.ok)throw new Error('Could not load function help');helpDocuments[language]=await response.text();}
      if(!content.isConnected)return;
      function render(){
        list.replaceChildren();
        for(const block of parseCatalogHelp(helpDocuments[language],search.value)){
          const row=element('div','','catalog-help-block');
          if(block.kind==='entry'){
            row.append(element('code',block.signature),element('p',block.text,'hint'));
            if(block.example)row.append(control(`${t('Example:')} ${block.example}`,()=>{changeMode('scientific');$('clear').click();calculator.insert(helpExampleInput(block.example));$('dialog').close();},'catalog-example'));
          }else row.append(element(block.kind==='heading'?'h2':block.kind==='category'?'h3':'p',block.text));
          list.append(row);
        }
        if(!list.childElementCount)list.append(element('p','No matching entries','hint'));
      }
      search.oninput=()=>{list.scrollTop=0;render();};render();
    }catch(exc){if(content.isConnected){list.replaceChildren(element('p',exc.message,'error'),control('Retry',catalogHelpDialog));}}
  }
  function variablesDialog() {
    const content=element('div'),name=element('input');name.placeholder='변수 이름 · A, b, M';name.setAttribute('aria-label','변수 이름');const expression=element('input');expression.placeholder='값 또는 수식';expression.setAttribute('aria-label','변수 수식');const list=element('div');
    const deleteAll=control(Object.keys(state.datasets).length?'Delete all variables':'Delete all',()=>{state.variables={};persist();render();});
    function store(ast){if(!/^[A-Za-z][A-Za-z0-9_]*$/.test(name.value)||name.value==='Ans')throw new Error('Ans 이외의 영문 변수 이름을 입력하세요.');state.variables[name.value]=ast;persist();render();}
    function render(){deleteAll.hidden=!Object.keys(state.variables).length;list.replaceChildren();for(const key of Object.keys(state.variables)){const row=element('div','','list-row'),shown=element('div','','content formula-preview');renderFormulas(shown,[`${key}=${astSource(state.variables[key])}`],{digits:state.digits});shown.onclick=()=>{name.value=key;expression.value=astSource(state.variables[key]);};row.append(shown,control('삽입',()=>{changeMode('scientific');calculator.insert(key,null,{factor:true});$('dialog').close();}),control('삭제',()=>{delete state.variables[key];persist();render();}));list.append(row);}{const datasets=Object.entries(state.datasets);if(datasets.length)list.append(element('p',t('Stats data'),'list-heading'));for(const [key,data] of datasets){list.append(control(key,()=>{try{const kind=statisticsColumnCount(state.datasetKinds[key])?state.datasetKinds[key]:statisticsKindForColumns(csvRows(data)[0].length),source=statisticsDatasetSource(data,kind);changeMode('scientific');calculator.insert(source,null,{factor:true});$('dialog').close();}catch(exc){error(exc.message);}}));}}}
    const assumption=element('select');for(const key of ['none','real','positive','negative','integer','nonzero']){const option=element('option',key);option.value=key;assumption.append(option);}assumption.setAttribute('aria-label',t('Symbol assumption'));
    content.append(name,expression,control('수식 저장',()=>{try{store(parse(latexInput(expression.value)));}catch(exc){toast(exc.message);}}),control('현재 결과 STO',()=>{try{if(!calculator.resultAst())throw new Error('저장 가능한 결과가 없습니다.');store(calculator.resultAst());}catch(exc){toast(exc.message);}}),assumption,control('Set assumption',()=>{if(!/^[A-Za-z][A-Za-z0-9_]*$/.test(name.value)){toast('Enter a valid variable name');return;}state.assumptions[name.value]=assumption.value==='none'?[]:[assumption.value];persist();}),deleteAll,list);render();openDialog('RCL / STO',content);
  }
  function settingsDialog() {
    const content=element('div');
    graphSettings=graphColorSettings({state,persist,refreshDisplays});
    content.append(graphSettings.element);
    for(const [key,label,min,max] of [['precision','내부 유효 숫자',3,200],['digits','표시 소수 자릿수',2,200]]){const input=element('input');input.type='number';input.min=min;input.max=max;input.value=state[key];input.dataset.setting=key;input.onchange=()=>{state[key]=Math.max(min,Math.min(max,Math.trunc(Number(input.value))||min));state.digits=Math.min(state.precision,state.digits);input.value=state[key];$('digits-indicator').textContent=`≤ ${state.digits} digits`;persist();refreshDisplays();};const holder=element('label',label);holder.append(input);content.append(holder);}
    for(const [key,label,min,max] of [['inputFont','Input font',10,42],['outputFont','Output font',10,48]]){const input=element('input');input.type='range';input.min=min;input.max=max;input.value=state[key];input.dataset.setting=key;input.oninput=()=>{state[key]=Number(input.value);calculator.applyFonts();persist();};const holder=element('label',label);holder.append(input);content.append(holder);}
    for(const [key,label] of [['removeComputationLimit','remove computation limit'],['autoCloseBrackets','Bracket auto-close'],['calcModeStepByStep','Calc mode step by step'],['wordWrap','Input word wrap'],['persistHistory','Save history locally'],['haptics','Key vibration'],['sound','Key sound']]){const input=element('input');input.type='checkbox';input.checked=state[key];input.dataset.setting=key;input.onchange=()=>{state[key]=input.checked;if(key==='removeComputationLimit'){setComputationLimitsRemoved(input.checked);calculator.preview();refreshDisplays();}if(key==='wordWrap'){calculator.applyWordWrap();calculator.preview();}if(key==='calcModeStepByStep')calculator.renderResult();persist();};const holder=element('label',label,'check');holder.append(input);content.append(holder);}
    content.append(element('p',t('Disables app time, workload, input size and exponent limits. Result size and precision settings stay unchanged; manual cancellation remains available.'),'hint'));
    content.append(element('p','계산 기록, 변수, 함수, 작업 내용은 이 브라우저에 저장됩니다. 전체 백업에는 Python 코드도 포함됩니다.','hint'),control('전체 백업 내보내기',()=>{persist();downloadFile('symvacas-backup.json',JSON.stringify(state,null,2),'application/json');}),control('백업 가져오기',()=>pickFile('.json',async file=>{
      const backup=JSON.parse(await file.text());
      if(!backup||typeof backup!=='object'||!backup.fields||!backup.variables||!backup.functions)throw new Error('SymvaCAS 웹 백업 파일이 아닙니다.');
      // All restored names and math are still validated by the parser/engine.
      if(!writeState(backup))throw new Error('백업을 저장할 공간이 부족합니다.');location.reload();
    })),control('변수 초기화',()=>{state.variables={};persist();toast('변수를 초기화했습니다.');}));
    $('settings-body').replaceChildren(content);translateDOM($('settings-dialog'));$('settings-dialog').showModal();
  }
  $('settings-close').onclick=()=>$('settings-dialog').close();
  $('history-button').onclick=historyDialog;$('catalog-button').onclick=catalogDialog;$('variables-button').onclick=variablesDialog;$('settings-button').onclick=settingsDialog;
  const systemTheme=window.matchMedia('(prefers-color-scheme: dark)');
  $('language').value=state.language;$('theme').value=state.theme;applyTheme(state.theme,systemTheme.matches);
  translateDOM();
  function refreshTheme(){applyTheme(state.theme,systemTheme.matches);graphSettings?.refresh();refreshDisplays();}
  systemTheme.addEventListener('change',()=>{if(state.theme==='system')refreshTheme();});
  $('theme').onchange=()=>{state.theme=value('theme');refreshTheme();persist();};
  $('language').onchange=()=>{state.language=value('language');state.languageChosen=true;setLanguage(state.language);translateDOM();calculator.renderNotation();refreshDisplays();if(['matrix','vector'].includes(value('mode')))renderMatrix();persist();if($('dialog').open)$('dialog').close();};

  async function initialize() {
    try{const response=await fetch('./catalog.json');if(!response.ok)throw new Error('Catalog load failed');catalog=await response.json();}catch(exc){toast('Catalog가 없습니다. 빌드 스크립트를 실행해 주세요.');}
  }
  return {initialize,mode:modeDialog,variables:variablesDialog,matrixInsert:matrixInsertDialog};
}
