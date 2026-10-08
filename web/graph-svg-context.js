// Canvas-compatible vector recording for the operations used by graph-canvas.
// The same plot function supplies geometry, clipping, labels and surface faces.
export function createGraphSvgContext(measure,width,height){
  const escape=value=>String(value).replaceAll('&','&amp;').replaceAll('<','&lt;').replaceAll('>','&gt;').replaceAll('"','&quot;');
  const elements=[],definitions=[],stack=[];let path=[],clip=null;
  const fields=['fillStyle','strokeStyle','lineWidth','lineCap','lineJoin','globalAlpha','font','textAlign'];
  const ctx={fillStyle:'#000',strokeStyle:'#000',lineWidth:1,lineCap:'butt',lineJoin:'miter',globalAlpha:1,font:'10px sans-serif',textAlign:'left',dash:[],
    setTransform(){},clearRect(){},
    beginPath(){path=[];},
    moveTo(x,y){if(Number.isFinite(x)&&Number.isFinite(y))path.push(`M${x} ${y}`);},
    lineTo(x,y){if(Number.isFinite(x)&&Number.isFinite(y))path.push(`L${x} ${y}`);},
    closePath(){path.push('Z');},
    rect(x,y,w,h){path.push(`M${x} ${y}h${w}v${h}h${-w}Z`);},
    arc(x,y,r){if([x,y,r].every(Number.isFinite))path.push(`M${x+r} ${y}A${r} ${r} 0 1 0 ${x-r} ${y}A${r} ${r} 0 1 0 ${x+r} ${y}Z`);},
    save(){stack.push({values:fields.map(field=>ctx[field]),dash:[...ctx.dash],clip});},
    restore(){const state=stack.pop();if(!state)return;fields.forEach((field,i)=>{ctx[field]=state.values[i];});ctx.dash=state.dash;clip=state.clip;},
    setLineDash(dash){ctx.dash=[...dash];},
    clip(){const id=`plot-clip-${definitions.length}`;definitions.push(`<clipPath id="${id}"><path d="${escape(path.join(' '))}"/></clipPath>`);clip=id;},
    measureText(text){measure.font=ctx.font;return measure.measureText(text);},
    fill(){emit(`<path d="${escape(path.join(' '))}" fill="${escape(ctx.fillStyle)}"/>`);},
    stroke(){emit(`<path d="${escape(path.join(' '))}" fill="none" stroke="${escape(ctx.strokeStyle)}" stroke-width="${ctx.lineWidth}" stroke-linecap="${escape(ctx.lineCap)}" stroke-linejoin="${escape(ctx.lineJoin)}"${ctx.dash.length?` stroke-dasharray="${ctx.dash.join(' ')}"`:''}/>`);},
    fillRect(x,y,w,h){emit(`<rect x="${x}" y="${y}" width="${w}" height="${h}" fill="${escape(ctx.fillStyle)}"/>`);},
    fillText(text,x,y){
      const [,size='10',family='sans-serif']=ctx.font.match(/([\d.]+)px\s+(.+)/)||[];
      const anchor=ctx.textAlign==='center'?'middle':['right','end'].includes(ctx.textAlign)?'end':'start';
      emit(`<text x="${x}" y="${y}" fill="${escape(ctx.fillStyle)}" font-size="${size}" font-family="${escape(family)}" text-anchor="${anchor}">${escape(text)}</text>`);
    },
    document(){return `<svg xmlns="http://www.w3.org/2000/svg" width="${width}" height="${height}" viewBox="0 0 ${width} ${height}"><defs><clipPath id="viewport"><rect width="${width}" height="${height}"/></clipPath>${definitions.join('')}</defs><g clip-path="url(#viewport)">${elements.join('')}</g></svg>`;}
  };
  function emit(element){elements.push(`<g opacity="${ctx.globalAlpha}"${clip?` clip-path="url(#${clip})"`:''}>${element}</g>`);}
  return ctx;
}
