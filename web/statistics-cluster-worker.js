import {clusteredHeatMap} from './statistics-cluster.js';

globalThis.onmessage=({data})=>{
  try{globalThis.postMessage({result:clusteredHeatMap(data.data,data.options||{})});}
  catch(error){globalThis.postMessage({error:error.message});}
};
