import {expressionDisplay} from './expression-display.js';
import {latexInput} from './parser.js';
export function renderFormulas(container,sources,{digits=10}={}){
  container.replaceChildren();
  for(const source of sources.filter(s=>s.trim()).slice(0,12)){
    const line=document.createElement('div');line.className='formula-line';
    try{line.append(expressionDisplay(latexInput(source),{digits,roundNumbers:true}));}
    catch{line.textContent=source;}
    container.append(line);
  }
  container.hidden=!container.childElementCount;
}
