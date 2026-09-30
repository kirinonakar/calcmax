function cursorRect(target,source,start){
  const rect=target.getBoundingClientRect();
  if(['mi','mn','mtext','span'].includes(target.localName)&&target.firstChild?.nodeType===3){
    const a=Number(target.getAttribute('data-source-start')),b=Number(target.getAttribute('data-source-end')),text=target.textContent;
    const at=source.slice(a,b)===text?start-a:start===a?0:text.length;
    const range=target.ownerDocument.createRange();
    range.setStart(target.firstChild,at);range.collapse(true);
    const caret=range.getBoundingClientRect?.();
    // Range rectangles provide the character edge, but MathML text ranges
    // can report a different vertical origin than the rendered token.
    if(caret?.height)return {x:caret.left,top:target.localName==='span'?caret.top:rect.top,height:target.localName==='span'?caret.height:rect.height};
    // Measure an adjacent character when a collapsed MathML range is empty.
    if(text.length){
      range.setStart(target.firstChild,at?at-1:0);range.setEnd(target.firstChild,at||1);
      const character=range.getBoundingClientRect?.();
      if(character?.height)return {x:at?character.right:character.left,top:target.localName==='span'?character.top:rect.top,height:target.localName==='span'?character.height:rect.height};
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
  if(math.classList.contains('input-wrapped')&&math.clientWidth){
    for(const part of math.querySelectorAll('.input-part'))part.style.overflowX=part.firstElementChild.getBoundingClientRect().width>math.clientWidth?'auto':'';
  }
  let frame=math.classList.contains('input-flow')?math:math.parentElement;
  if(!frame?.classList.contains('input-math')&&!frame?.classList.contains('math-frame')){
    frame=math.ownerDocument.createElement('span');frame.className='input-math';
    math.replaceWith(frame);frame.append(math);
  }
  frame.classList.add('input-math');
  for(const node of frame.querySelectorAll('.input-caret'))node.remove();
  const nodes=[...math.querySelectorAll('[data-source-start]')];
  for(const node of nodes)node.classList.toggle('selected',end>start&&Number(node.getAttribute('data-source-start'))>=start&&Number(node.getAttribute('data-source-end'))<=end);
  if(end>start)return;
  const candidates=nodes.filter(node=>Number(node.getAttribute('data-source-start'))<=start&&Number(node.getAttribute('data-source-end'))>=start).sort((a,b)=>(Number(a.getAttribute('data-source-end'))-Number(a.getAttribute('data-source-start')))-(Number(b.getAttribute('data-source-end'))-Number(b.getAttribute('data-source-start'))));
  const target=candidates.find(node=>['mi','mn','mtext','span'].includes(node.localName))||candidates[0]||math.firstElementChild;
  if(!target)return;
  let rect=cursorRect(target,source,start);
  const part=target.closest('.input-part');
  if(part?.style.overflowX==='auto'&&part.clientWidth){
    const bounds=part.getBoundingClientRect();
    if(rect.x<bounds.left+4)part.scrollLeft+=rect.x-bounds.left-4;
    else if(rect.x>bounds.right-4)part.scrollLeft+=rect.x-bounds.right+4;
    rect=cursorRect(target,source,start);
  }
  const origin=frame.getBoundingClientRect();
  const font=target.style?math.ownerDocument.defaultView.getComputedStyle(target).fontSize:'';
  const size=font.endsWith('px')?parseFloat(font):rect.height||24;
  // Use an HTML containing block: positioned MathML children can be offset
  // by the math baseline even with explicit top/left coordinates.
  const marker=math.ownerDocument.createElement('span');marker.classList.add('input-caret');
  marker.setAttribute('aria-hidden','true');
  marker.setAttribute('data-source-start',String(start));marker.setAttribute('data-source-end',String(start));
  marker.setAttribute('style',`left:${rect.x-origin.left}px;top:${rect.top-origin.top+(rect.height-size)/2}px;height:${size}px`);
  frame.append(marker);
  return marker;
}

// Scroll only the input viewport; scrolling the page would move the keypad.
export function followInputCursor(viewport,marker,margin=12,wordWrap=false){
  if(!marker||!viewport.clientWidth)return;
  const bounds=viewport.getBoundingClientRect(),caret=marker.getBoundingClientRect(),left=bounds.left+viewport.clientLeft;
  const inset=Math.min(margin,viewport.clientWidth/2);
  if(!wordWrap){
    if(caret.left<left+inset)viewport.scrollLeft+=caret.left-left-inset;
    else if(caret.right>left+viewport.clientWidth-inset)viewport.scrollLeft+=caret.right-left-viewport.clientWidth+inset;
  }else if(viewport.clientHeight){
    const top=bounds.top+viewport.clientTop;
    if(caret.top<top)viewport.scrollTop+=caret.top-top;
    else if(caret.bottom>top+viewport.clientHeight)viewport.scrollTop+=caret.bottom-top-viewport.clientHeight;
  }
}

export function followTextCursor(field){
  if(!field.clientWidth)return;
  const style=field.ownerDocument.defaultView.getComputedStyle(field),mirror=field.ownerDocument.createElement('span');
  mirror.style.cssText='position:fixed;visibility:hidden;white-space:pre;pointer-events:none';
  for(const property of ['font','letterSpacing','tabSize'])mirror.style[property]=style[property];
  mirror.textContent=field.value.slice(0,field.selectionEnd).split('\n').at(-1);
  field.ownerDocument.body.append(mirror);
  const x=mirror.getBoundingClientRect().width,padding=(parseFloat(style.paddingLeft)||0)+(parseFloat(style.paddingRight)||0),width=field.clientWidth-padding,margin=Math.min(12,width/2);
  mirror.remove();
  if(x<field.scrollLeft+margin)field.scrollLeft=Math.max(0,x-margin);
  else if(x>field.scrollLeft+width-margin)field.scrollLeft=x-width+margin;
}

export function inputPointPosition(target,source,x,y){
  const start=Number(target.getAttribute('data-source-start')),end=Number(target.getAttribute('data-source-end'));
  if(target.firstChild?.nodeType!==3||source.slice(start,end)!==target.textContent)return start;
  const doc=target.ownerDocument,point=doc.caretPositionFromPoint?.(x,y),range=point?null:doc.caretRangeFromPoint?.(x,y);
  const node=point?.offsetNode||range?.startContainer,offset=point?.offset??range?.startOffset;
  if(node===target.firstChild)return start+Math.max(0,Math.min(end-start,offset));
  // A second tap places the caret at the nearest character boundary. This
  // also supports MathML implementations without caretPositionFromPoint.
  let best=start,distance=Infinity;
  for(let at=start;at<=end;at++){
    const rect=cursorRect(target,source,at),dy=y<rect.top?rect.top-y:y>rect.top+rect.height?y-rect.top-rect.height:0,d=Math.abs(x-rect.x)+dy*2;
    if(d<distance){distance=d;best=at;}
  }
  return best;
}
