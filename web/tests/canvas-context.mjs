// Recording context for geometry, draw-call, and scheduling tests in jsdom.
export function installCanvas(dom){
  const contexts=new WeakMap();
  dom.window.HTMLCanvasElement.prototype.getContext=function(){
    if(contexts.has(this))return contexts.get(this);
    const context={commands:[],path:[],frames:0,globalAlpha:1,lineWidth:1,lineDash:[]},stack=[];
    for(const name of ['setTransform','clearRect','beginPath','moveTo','lineTo','rect','clip','arc','closePath','fill','stroke','fillText','setLineDash','save','restore'])context[name]=function(...args){
      if(name==='clearRect'){this.commands.length=0;this.frames++;}
      if(name==='beginPath')this.path=[];
      if(['moveTo','lineTo','rect','arc','closePath'].includes(name))this.path.push({op:name,args});
      if(name==='setLineDash')this.lineDash=args[0];
      if(name==='save')stack.push({fillStyle:this.fillStyle,strokeStyle:this.strokeStyle,lineWidth:this.lineWidth,globalAlpha:this.globalAlpha,lineDash:this.lineDash});
      if(name==='restore')Object.assign(this,stack.pop());
      this.commands.push({op:name,args,path:[...this.path],fillStyle:this.fillStyle,strokeStyle:this.strokeStyle,lineWidth:this.lineWidth,globalAlpha:this.globalAlpha,lineDash:[...this.lineDash]});
    };
    contexts.set(this,context);return context;
  };
}
export function surfaceFills(container){return container.querySelector('canvas').getContext('2d').commands.filter(c=>c.op==='fill'&&c.path.some(p=>p.op==='closePath'));}
export function curveStrokes(container){return container.querySelector('canvas')?.getContext('2d').commands.filter(c=>c.op==='stroke'&&[2,4].includes(c.lineWidth)&&!c.lineDash.length&&['#007b68','#a04c75','#3b70bd','#b17d00','#6b5ec2','#a34629'].includes(c.strokeStyle))||[];}
