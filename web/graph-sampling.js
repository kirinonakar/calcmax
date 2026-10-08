// Throttle rather than debounce: continuous pointer moves must not postpone
// sampling forever. The workspace conflates requests while the worker is busy.
export function graphSamplingInterval(callback,{setTimer=setTimeout,clearTimer=clearTimeout,interval=80}={}){
  let timer=null;
  return {
    schedule(){if(timer===null)timer=setTimer(()=>{timer=null;callback();},interval);},
    cancel(){if(timer!==null)clearTimer(timer);timer=null;}
  };
}

export function graphPreviewRequest(request){
  if(!['cartesian','implicit'].includes(request.graphKind))return {...request,samples:200};
  const dx=(request.xMax-request.xMin)*.15,dy=(request.yMax-request.yMin)*.15;
  return {...request,min:request.min-dx,max:request.max+dx,xMin:request.xMin-dx,xMax:request.xMax+dx,yMin:request.yMin-dy,yMax:request.yMax+dy,samples:200};
}

// A completed preview is still useful after the viewport has moved, provided
// the expression, parameters and calculation options still match.
export function graphSamplingIdentity(request){
  const {xMin,xMax,yMin,yMax,surfaceYMin,surfaceYMax,samples,...identity}=request;
  if(['cartesian','implicit','surface'].includes(request.graphKind)){delete identity.min;delete identity.max;}
  return JSON.stringify(identity);
}
