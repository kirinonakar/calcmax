/* One dedicated WASM interpreter per worker. Termination is the hard cancellation
 * boundary and works without SharedArrayBuffer or COOP/COEP headers. */
import {loadPyodide} from './vendor/pyodide.mjs';

let pyodide;
const ready = (async () => {
  postMessage({type:'status',message:'WebAssembly 런타임 로딩…'});
  // The module loader resolves its adjacent runtime assets in browsers and Node.
  pyodide = await loadPyodide({stdout:message=>console.log(message),stderr:message=>console.error(message)});
  postMessage({type:'status',message:'SymPy 계산 엔진 로딩…'});
  await pyodide.loadPackage('sympy');
  const response = await fetch('./engine.zip');
  if (!response.ok) throw new Error('Engine archive is missing. Run python web/build.py.');
  pyodide.unpackArchive(await response.arrayBuffer(),'zip',{extractDir:'/calcmax'});
  pyodide.runPython("import sys\nsys.path.insert(0, '/calcmax')\nsys.set_int_max_str_digits(0)\nimport calc_engine, script_runner\n");
  postMessage({type:'ready',version:pyodide.version});
})();
ready.catch(error => postMessage({type:'fatal',error:String(error)}));
self.onmessage = async ({data:{id,request}}) => {
  try {
    await ready;
    pyodide.globals.set('_web_payload',JSON.stringify(request));
    if (request.action === 'python') {
      pyodide.globals.set('_web_inputs',JSON.stringify(request.inputs || []));
      pyodide.runPython(`
import json
class _WebInput:
    def __init__(self): self.values = iter(json.loads(_web_inputs))
    def request(self, prompt, output):
        try: return str(next(self.values))
        except StopIteration: raise EOFError('Not enough input values. Enter one value per line in Python inputs.')
_web_input = _WebInput()
`);
    }
    const raw = pyodide.runPython(request.action === 'python' ? 'script_runner.run(_web_payload, _web_input)' : 'calc_engine.dispatch(_web_payload)');
    postMessage({type:'result',id,result:JSON.parse(raw)});
  } catch (error) {
    postMessage({type:'result',id,result:{ok:false,error:String(error)}});
  } finally {
    if (pyodide) for(const key of ['_web_payload','_web_inputs','_web_input']) {
      if(pyodide.globals.has(key))pyodide.globals.delete(key);
    }
  }
};
