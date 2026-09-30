import test from 'node:test';
import assert from 'node:assert/strict';
import {installEngine} from '../engine-bootstrap.js';
import {fetchEngineAsset} from '../engine-fetch.js';

function fixture(){
  const requests=[],unpacked=[],python=[];
  const lock={packages:Object.fromEntries(['mpmath','sympy'].map(name=>[name,{file_name:name+'.whl',sha256:'00'.repeat(32)}]))};
  const pyodide={runPython:source=>{python.push(source);return '/lib/python3.14/site-packages';},unpackArchive:(body,format,options)=>unpacked.push({body:new TextDecoder().decode(body),format,...options}),loadPackage:()=>{throw new Error('Package manager must not run during startup');}};
  const fetcher=async(url,options)=>{requests.push({url:String(url),options});return new Response(String(url).endsWith('pyodide-lock.json')?JSON.stringify(lock):String(url).split('/').at(-1));};
  const options={runtimeURL:new URL('https://example.test/calcmax/vendor/'),engineURL:new URL('https://example.test/calcmax/engine.zip'),fetcher};
  return {requests,unpacked,python,pyodide,options};
}

test('cold startup installs bundled wheels in dependency order with integrity and nested paths',async()=>{
  const f=fixture();await installEngine(f.pyodide,f.options);
  assert.deepEqual(f.unpacked.map(item=>item.body),['mpmath.whl','sympy.whl','engine.zip']);
  assert.deepEqual(f.unpacked.map(item=>item.extractDir),['/lib/python3.14/site-packages','/lib/python3.14/site-packages','/calcmax']);
  for(const request of f.requests.filter(item=>item.url.endsWith('.whl'))){assert.match(request.url,/\/calcmax\/vendor\//);assert.match(request.options.integrity,/^sha256-/);}
  assert.match(f.python.at(-1),/import calc_engine, script_runner/);
});

test('a stalled first SymPy download recovers before importing the engine',async()=>{
  const f=fixture(),fetcher=f.options.fetcher;let attempts=0;
  f.options.fetcher=(url,options)=>fetchEngineAsset(async(input,init)=>{
    if(String(input).endsWith('sympy.whl')&&++attempts===1)return {ok:true,status:200,arrayBuffer:()=>new Promise(()=>{})};
    return fetcher(input,init);
  },url,options,15);
  await installEngine(f.pyodide,f.options);
  assert.equal(attempts,2);assert.equal(f.requests.find(item=>item.url.endsWith('sympy.whl')).options.cache,'reload');
  assert.match(f.python.at(-1),/import calc_engine/);
});

test('missing wheels stop startup before Python imports or partial installation',async()=>{
  const f=fixture(),fetcher=f.options.fetcher;
  f.options.fetcher=(url,options)=>fetchEngineAsset((input,init)=>String(input).endsWith('sympy.whl')?Promise.resolve(new Response('missing',{status:404})):fetcher(input,init),url,options);
  await assert.rejects(installEngine(f.pyodide,f.options),/404/);
  assert.equal(f.unpacked.length,0);assert.equal(f.python.length,0);
});
