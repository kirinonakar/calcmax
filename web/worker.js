/* One dedicated WASM interpreter per worker. Termination is the hard cancellation
 * boundary and works without SharedArrayBuffer or COOP/COEP headers. */
import {loadPyodide} from './vendor/pyodide.mjs';
import {fetchEngineAsset} from './engine-fetch.js';
import {installEngine} from './engine-bootstrap.js';

let pyodide;
let waitingInput=null,inputCounter=0;
function requestInput(id,prompt,output) {
  return new Promise(resolve=>{
    const inputId=++inputCounter;
    waitingInput={id,inputId,resolve};
    postMessage({type:'input',id,inputId,prompt,output});
  });
}
const ready = (async () => {
  const originalFetch=self.fetch;
  self.fetch=(input,options)=>fetchEngineAsset(originalFetch.bind(self),input,options);
  try {
  postMessage({type:'status',message:'WebAssembly 런타임 로딩…'});
  // The module loader resolves its adjacent runtime assets in browsers and Node.
  const runtimeURL=new URL('./vendor/',import.meta.url);
  pyodide = await loadPyodide({...(runtimeURL.protocol!=='file:'?{indexURL:runtimeURL.href}:{}),stdout:message=>console.log(message),stderr:message=>console.error(message)});
  postMessage({type:'status',message:'SymPy 계산 엔진 로딩…'});
  await installEngine(pyodide,{runtimeURL,engineURL:new URL('./engine.zip',import.meta.url)});
  postMessage({type:'ready',version:pyodide.version});
  } finally {self.fetch=originalFetch;}
})();
ready.catch(error => postMessage({type:'fatal',error:String(error)}));
self.onmessage = async ({data}) => {
  if(data.type==='input') {
    if(waitingInput?.id===data.id&&waitingInput.inputId===data.inputId) {
      const {resolve}=waitingInput;waitingInput=null;resolve(data.value);
    }
    return;
  }
  const {id,request}=data;
  try {
    await ready;
    pyodide.globals.set('_web_payload',JSON.stringify(request));
    if (request.action === 'python') {
      pyodide.globals.set('_web_inputs',JSON.stringify(request.inputs || []));
      pyodide.globals.set('_web_request_input',(prompt,output)=>requestInput(id,prompt,output));
      pyodide.globals.set('_web_interactive',typeof WebAssembly.Suspending==='function'&&typeof WebAssembly.promising==='function');
      pyodide.runPython(`
import json
from pyodide.ffi import run_sync
class _WebInput:
    def __init__(self): self.values = iter(json.loads(_web_inputs))
    def request(self, prompt, output):
        value = next(self.values, None)
        if value is not None: return str(value)
        if not _web_interactive:
            raise EOFError('Interactive input is unavailable in this browser. Use a browser with WebAssembly Promise Integration or enter one value per line in Python inputs.')
        value = run_sync(_web_request_input(prompt, output))
        if value is None: raise EOFError('Input cancelled')
        return str(value)
_web_input = _WebInput()
`);
    }
    const raw = request.action==='python'
      ? await pyodide.runPythonAsync('script_runner.run(_web_payload, _web_input)')
      : pyodide.runPython('calc_engine.dispatch(_web_payload)');
    postMessage({type:'result',id,result:JSON.parse(raw)});
  } catch (error) {
    postMessage({type:'result',id,result:{ok:false,error:String(error)}});
  } finally {
    waitingInput=null;
    if (pyodide) for(const key of ['_web_payload','_web_inputs','_web_input','_web_request_input','_web_interactive']) {
      if(pyodide.globals.has(key))pyodide.globals.delete(key);
    }
  }
};
