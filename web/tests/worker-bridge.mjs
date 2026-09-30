// Browser Worker API adapter for Node integration tests. The production worker
// and real WASM interpreter run unchanged; only filesystem loading is adapted.
import {parentPort} from 'node:worker_threads';
import {readFileSync} from 'node:fs';
globalThis.self=globalThis;
globalThis.postMessage=message=>parentPort.postMessage(message);
globalThis.onmessage=null;
globalThis.importScripts=()=>{throw new Error('Classic web workers are not supported');};
globalThis.fetch=async path=>{if(path!=='./engine.zip')throw new Error(`Unexpected request: ${path}`);return new Response(readFileSync(new URL('../engine.zip',import.meta.url)));};
await import('../worker.js');
parentPort.on('message',data=>globalThis.onmessage({data}));
