import {plotGraph} from './graph-canvas.js';
import {createGraphSvgContext} from './graph-svg-context.js';

export function graphSvg(container,result,bounds,options){
  let recording;
  plotGraph(container,result,bounds,{...options,contextFactory:(context,{width,height})=>{
    recording=createGraphSvgContext(context,width,height);return recording;
  }});
  if(!recording)throw new Error('Could not export the graph');
  return recording.document();
}

export function graphPng(canvas){
  if(!canvas)throw new Error('Plot a graph before saving');
  return new Promise((resolve,reject)=>canvas.toBlob(blob=>{
    if(blob)resolve(blob);else reject(new Error('Could not export the graph'));
  },'image/png'));
}
