// Browser Worker API adapter for Node integration tests. The production worker
// and real WASM interpreter run unchanged; only filesystem loading is adapted.
import {parentPort} from 'node:worker_threads';
import {readFileSync} from 'node:fs';
const channel=parentPort||process;
globalThis.self=globalThis;
globalThis.postMessage=message=>parentPort?parentPort.postMessage(message):process.send(message);
globalThis.onmessage=null;
globalThis.importScripts=()=>{throw new Error('Classic web workers are not supported');};
globalThis.fetch=async path=>{const url=new URL(path, new URL('../worker.js',import.meta.url));if(url.protocol!=='file:'||!url.href.startsWith(new URL('../',import.meta.url).href))throw new Error(`Unexpected request: ${path}`);return new Response(readFileSync(url));};
await import('../worker.js');
channel.on('message',data=>globalThis.onmessage({data}));
