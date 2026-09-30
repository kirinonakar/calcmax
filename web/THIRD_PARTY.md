# Third-party notices

CalcMax is provided under the [MIT license](LICENSE), copyright (c) 2026 kirinonakar.

The static web build includes:

- [Pyodide 314.0.7](https://github.com/pyodide/pyodide), Mozilla Public License 2.0. It includes CPython compiled for WebAssembly and runtime components with their own notices. The build bundles the full [Pyodide license](vendor/licenses/pyodide-LICENSE.txt) and [CPython license](vendor/licenses/python-LICENSE.txt). CalcMax does not modify these runtimes.
- [SymPy 1.14.0](https://www.sympy.org/), BSD license. The build extracts the full license to [vendor/licenses/sympy-LICENSE.txt](vendor/licenses/sympy-LICENSE.txt); the original wheel also contains all notices.
- [mpmath 1.4.1](https://mpmath.org/), BSD license. The build extracts the full license to [vendor/licenses/mpmath-LICENSE.txt](vendor/licenses/mpmath-LICENSE.txt); the original wheel also contains its notices.
- [jsdom](https://github.com/jsdom/jsdom), MIT license, is used only for development tests and is not shipped to the browser.

The optional online currency workspace uses [ExchangeRate-API's open daily reference rates](https://www.exchangerate-api.com/docs/free), only when the user requests them. Rates and their reference timestamps are cached locally.
