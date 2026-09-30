function cursorRect(target,source,start){
  const rect=target.getBoundingClientRect();
  if(['mi','mn','mtext'].includes(target.localName)&&target.firstChild?.nodeType===3){
    const a=Number(target.getAttribute('data-source-start')),b=Number(target.getAttribute('data-source-end')),text=target.textContent;
    const at=source.slice(a,b)===text?start-a:start===a?0:text.length;
    const range=target.ownerDocument.createRange();
    range.setStart(target.firstChild,at);range.collapse(true);
    const caret=range.getBoundingClientRect?.();
    // Range rectangles provide the character edge, but MathML text ranges
    // can report a different vertical origin than the rendered token.
    if(caret?.height)return {x:caret.left,top:rect.top,height:rect.height};
    // Measure an adjacent character when a collapsed MathML range is empty.
    if(text.length){
      range.setStart(target.firstChild,at?at-1:0);range.setEnd(target.firstChild,at||1);
      const character=range.getBoundingClientRect?.();
      if(character?.height)return {x:at?character.right:character.left,top:rect.top,height:rect.height};
    }
    return {x:at?rect.right:rect.left,top:rect.top,height:rect.height};
  }
  const next=[...target.children].find(node=>node.hasAttribute('data-source-start')&&Number(node.getAttribute('data-source-start'))>=start);
  if(start>Number(target.getAttribute('data-source-start'))&&next){
    const nextRect=next.getBoundingClientRect();return {x:nextRect.left,top:nextRect.top,height:nextRect.height};
  }
  return {x:start<=Number(target.getAttribute('data-source-start'))?rect.left:rect.right,top:rect.top,height:rect.height};
}
export function markInputCursor(math,source,start,end=start){
  let frame=math.parentElement;
  if(!frame?.classList.contains('input-math')){
    frame=math.ownerDocument.createElement('span');frame.className='input-math';
    math.replaceWith(frame);frame.append(math);
  }
  for(const node of frame.querySelectorAll('.input-caret'))node.remove();
  const nodes=[...math.querySelectorAll('[data-source-start]')];
  for(const node of nodes)node.classList.toggle('selected',end>start&&Number(node.getAttribute('data-source-start'))>=start&&Number(node.getAttribute('data-source-end'))<=end);
  if(end>start)return;
  const candidates=nodes.filter(node=>Number(node.getAttribute('data-source-start'))<=start&&Number(node.getAttribute('data-source-end'))>=start).sort((a,b)=>(Number(a.getAttribute('data-source-end'))-Number(a.getAttribute('data-source-start')))-(Number(b.getAttribute('data-source-end'))-Number(b.getAttribute('data-source-start'))));
  const target=candidates.find(node=>['mi','mn','mtext'].includes(node.localName))||candidates[0]||math.firstElementChild;
  if(!target)return;
  const rect=cursorRect(target,source,start),origin=frame.getBoundingClientRect();
  const font=target.style?math.ownerDocument.defaultView.getComputedStyle(target).fontSize:'';
  const size=font.endsWith('px')?parseFloat(font):rect.height||24;
  // Use an HTML containing block: positioned MathML children can be offset
  // by the math baseline even with explicit top/left coordinates.
  const marker=math.ownerDocument.createElement('span');marker.classList.add('input-caret');
  marker.setAttribute('aria-hidden','true');
  marker.setAttribute('data-source-start',String(start));marker.setAttribute('data-source-end',String(start));
  marker.setAttribute('style',`left:${rect.x-origin.left}px;top:${rect.top-origin.top+(rect.height-size)/2}px;height:${size}px`);
  frame.append(marker);
}
