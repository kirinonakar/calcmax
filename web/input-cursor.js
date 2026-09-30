const NS='http://www.w3.org/1998/Math/MathML';
export function markInputCursor(math,source,start,end=start){
  for(const node of math.querySelectorAll('.input-caret'))node.remove();
  const nodes=[...math.querySelectorAll('[data-source-start]')];
  for(const node of nodes)node.classList.toggle('selected',end>start&&Number(node.getAttribute('data-source-start'))>=start&&Number(node.getAttribute('data-source-end'))<=end);
  if(end>start)return;
  const marker=document.createElementNS(NS,'mspace');marker.classList.add('input-caret');marker.setAttribute('width','2px');marker.setAttribute('height','.85em');marker.setAttribute('depth','.15em');marker.setAttribute('data-source-start',String(start));marker.setAttribute('data-source-end',String(start));
  const candidates=nodes.filter(node=>Number(node.getAttribute('data-source-start'))<=start&&Number(node.getAttribute('data-source-end'))>=start).sort((a,b)=>(Number(a.getAttribute('data-source-end'))-Number(a.getAttribute('data-source-start')))-(Number(b.getAttribute('data-source-end'))-Number(b.getAttribute('data-source-start'))));
  const leaf=candidates.find(node=>['mi','mn','mtext'].includes(node.localName));
  if(leaf){
    const a=Number(leaf.getAttribute('data-source-start')),b=Number(leaf.getAttribute('data-source-end')),text=leaf.textContent,at=source.slice(a,b)===text?start-a:start===a?0:text.length;
    const group=document.createElementNS(NS,'mrow');group.setAttribute('data-source-start',String(a));group.setAttribute('data-source-end',String(b));
    for(const [part,place] of [[text.slice(0,at),'before'],[text.slice(at),'after']]){if(place==='after')group.append(marker);if(part){const token=document.createElementNS(NS,leaf.localName);if(leaf.hasAttribute('class'))token.setAttribute('class',leaf.getAttribute('class'));token.textContent=part;group.append(token);}}
    leaf.replaceWith(group);return;
  }
  const target=candidates[0]||math.firstElementChild;
  if(!target){math.append(marker);return;}
  function adjacent(after){if(['mfrac','msup','mroot','munder','mover','munderover','msubsup'].includes(target.parentElement.localName)){const group=document.createElementNS(NS,'mrow');target.replaceWith(group);group.append(...(after?[target,marker]:[marker,target]));}else if(after)target.after(marker);else target.before(marker);}
  const children=[...target.children];
  const next=children.find(node=>Number(node.getAttribute('data-source-start'))>=start&&node.hasAttribute('data-source-start'));
  if(start<=Number(target.getAttribute('data-source-start'))){
    if(['mfrac','msup','msqrt','mroot'].includes(target.localName))adjacent(false);else target.prepend(marker);
  }else if(next)target.insertBefore(marker,next);
  else if(['mfrac','msup','msqrt','mroot'].includes(target.localName))adjacent(true);else target.append(marker);
}
