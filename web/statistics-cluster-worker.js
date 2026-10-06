import {clusteredHeatMap} from './statistics-cluster.js';

globalThis.onmessage=({data})=>{
  try{globalThis.postMessage({result:clusteredHeatMap(data)});}
  catch(error){globalThis.postMessage({error:error.message});}
};
