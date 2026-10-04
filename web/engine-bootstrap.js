// SymPy and mpmath are pure Python wheels. Install the bundled archives directly
// instead of waiting on Pyodide's package-manager dependency/installation locks.
export async function installEngine(pyodide,{runtimeURL,engineURL,fetcher=fetch}) {
  const lock=await (await fetcher(new URL('pyodide-lock.json',runtimeURL))).json();
  const packages=['mpmath','sympy'].map(name=>lock.packages[name]);
  const archives=await Promise.all([
    ...packages.map(async item=>{
      const integrity='sha256-'+btoa(String.fromCharCode(...item.sha256.match(/../g).map(byte=>parseInt(byte,16))));
      return (await fetcher(new URL(item.file_name,runtimeURL),{integrity})).arrayBuffer();
    }),
    (async()=> (await fetcher(engineURL)).arrayBuffer())()
  ]);
  const sitePackages=pyodide.runPython("import sysconfig\nsysconfig.get_path('purelib')");
  for(const archive of archives.slice(0,2))pyodide.unpackArchive(archive,'zip',{extractDir:sitePackages});
  pyodide.unpackArchive(archives[2],'zip',{extractDir:'/symvacas'});
  pyodide.runPython("import sys, importlib\nimportlib.invalidate_caches()\nsys.path.insert(0, '/symvacas')\nsys.set_int_max_str_digits(0)\nimport calc_engine, script_runner\n");
}
