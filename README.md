# CalcMax

A native, offline Android scientific, graphing and symbolic calculator. Kotlin and Jetpack Compose provide the instrument interface; an independent Kotlin AST and a bundled SymPy engine provide exact mathematics. No WebView, remote calculation service, LLM, or network permission is used.

## Build

Requirements: JDK 21 (Android Studio's bundled runtime works), Android SDK 36.1, Python 3.14 on PATH, and an initial internet connection to download build dependencies. The installed application computes offline. Supported devices: Android 8/API 26 or later, arm64-v8a and x86_64.

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :app:assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`. `:app:assembleRelease` builds the unsigned release variant; use your own signing credentials for distribution. No release signing secret is checked in.

## Everyday use

* Use the keypad for ordinary calculations. SHIFT and ALPHA are one-shot modifiers; their labels are visible above each key.
* Fractions, roots and calculus keys insert structural slots. Tap a slot or expression component to select it. Left/right move the cursor; up selects its enclosing AST node; SHIFT up selects a child. The small source line makes cursor position explicit.
* `Type / paste` enables Android text entry; hardware keyboards also work in the natural display. `Paste` inserts clipboard text.
* S⇔D switches exact and decimal results. SHIFT S⇔D opens the searchable function catalog.
* MODE opens scientific, CAS, graphing, equations, matrix, vector, statistics, programmer, units and constants workspaces.
* SETUP chooses Light, Dark, or System (the default), angle unit, precision, haptics, sound and optional persistent history. Theme changes preserve the current calculation and editor state.
* STO / SHIFT STO open variable storage and recall. `radius=5` and `f(x)=x^2+1` are supported at the top level outside Equation mode. Use `solve(...)` to solve equations. Stored values are snapshots; user functions retain their expression bodies.

## Examples

```text
2+3*4
1/3+1/6
sqrt(8)
sin(30°)
sin(pi/6)
factor(x^4-1)
diff(sin(x^2),x)
integrate(x^2*exp(x),x)
integrate(x^2,x,0,1)
limit(sin(x)/x,x,0)
limit(1/x,x,0,left)
series(exp(x),x,0,6)
solve(x^2-5x+6=0,x)
solve([x+y=3,x-y=1],[x,y])
solve(x^2<4,x)
nsolve(cos(x)-x,x,0,1)
nintegrate(sin(x),x,0,pi)
det([[1,2],[3,4]])
eigenvalues([[1,0],[0,2]])
dot([1,2,3],[4,5,6])
stats([1,2,3,4])
regression([[1,2],[2,4],[3,6]],linear)
convert(32,degF,degC)
qty(2,m)+qty(30,cm)
convert(qty(1,kg)*qty(2,mps2),N)
```

Decimal literals are exact rationals. Decimal results use 15–200 configurable significant digits. Numeric trig honors DEG/RAD/GRAD; explicit π or ° specifies a radian/degree expression. Symbolic calculus is in radians. Complex values use `i`; `polar(r,theta)`, `rectpolar(z)`, `re`, `im`, `arg` and `conj` are available. `log(x)` is base 10 and `ln(x)` is natural log.

The graph workspace accepts one expression per line (six curves), including user functions. Cartesian variable: `x`. Parametric input: `[cos(t),sin(t)]`; polar input: `2*cos(3*t)`. Traces show sampled approximations. Cartesian analysis provides bracketed roots/intersections, interval extrema, derivatives and integrals. Graphing is a visual numerical tool, not a proof that all roots or singularities have been found.

Statistics distinguish population and sample variance/SD; quartiles use the inclusive interpolation convention. Regression supports linear, quadratic, logarithmic, exponential and power models. Programmer inputs use the selected base, mask to 8/16/32/64 bits, and expose all four bases; right shifts are arithmetic in signed mode and logical in unsigned mode. Shift counts are entered in the selected base.

Units use exact conversion factors, explicit dimensions and affine temperature conversion. Use `qty(value,unit)` inside expressions. Absolute temperatures support conversion, not compound algebra; temperature differences should be represented separately. Constants use SI definitions and [NIST CODATA 2022](https://physics.nist.gov/cuu/Constants/). Measured constants are marked as approximate and retain their published precision.

## Architecture

```text
math/           Pure Kotlin lexer, Pratt parser, immutable source-spanned AST, editor
app/.../ui/     Native Compose mathematical layouts, keypad and mode workspaces
app/.../calculator/
                ViewModel, local persistence, typed AST JSON process protocol
app/src/main/python/calc_engine.py
                Validated AST -> SymPy -> result tree + exact/decimal forms
app/src/main/python/quantities.py
                Dimension algebra independent of Android
tests/          Desktop integration and randomized exact arithmetic tests
```

Every mathematical workspace consumes the same AST. Graphing compiles already validated symbolic expressions to local numeric functions; it never parses a separate expression language. SymPy's `parse_expr` and unrestricted string `eval` are not used for user input. Result ASTs preserve Ans/STO values without reparsing printed mathematics. The engine adapter isolates the UI from SymPy.

Computation runs in a bound service in a separate `:math` process. Python execution has recursion, AST size, numeric size, step and time limits. A 20-second IPC deadline can terminate and restart the worker process; cancellation therefore also works for operations that do not cooperate with coroutine cancellation. The engine's default Python budget is eight seconds. Errors and unevaluated symbolic results are displayed rather than replaced by fabricated answers.

History is capped at 500 entries and stored privately on the device. Turning persistence off deletes its saved copy. Variables/functions/preferences remain local. Android backup behavior follows the manifest backup configuration.

## Validation

```powershell
.\gradlew.bat :math:test :math:exportCases :app:lintDebug
python -m venv .venv
.\.venv\Scripts\python.exe -m pip install sympy==1.14.0
.\.venv\Scripts\python.exe -m unittest discover -s tests -v
.\gradlew.bat :app:connectedDebugAndroidTest
```

The desktop tests consume AST fixtures produced by the actual Kotlin parser. They cover the specification's exact examples, precedence, complex arithmetic, calculus, solving, domains, matrices, statistics, graph discontinuities, units, precision, safeguards, result serialization and 100 seeded rational arithmetic cases. Device tests cover the native keypad, actual service IPC, cancellation/recovery, CAS, graphing, saved variables, activity recreation, both themes, system theme changes and landscape. Tests produce screenshots in the app's private `files/qa` directory.

## Explicit bounds

* Symbolic integration/solving is subject to SymPy's algorithmic coverage and the computation budget. Unsolved integrals and conditional solution sets are retained with an explanatory message. There is no claim of solving every possible symbolic problem.
* Matrix entry grid: up to 4×4; expression matrices: up to 32×32. Large exact factorizations/eigensystems may time out.
* Graphs use finite samples and break large discontinuities; narrow features can be missed. Analysis controls currently apply to Cartesian functions; all three graph types support pan, zoom, ranges and tracing.
* General output such as condition sets and series remainder terms can use textual mathematical notation where a dedicated native layout is not available. Such results may be copyable but not reusable through Ans; the app disables result insertion for them.
* Large font sizes and small/landscape screens may scroll the keypad. The application has been tested on an Android 16 emulator; physical-device and broader Android-version qualification is still needed before a store release.

## Third-party notices

SymPy 1.14.0 and mpmath 1.3.0 use BSD licenses. Chaquopy 17 uses the MIT license. Full licenses are included in `app/src/main/assets/licenses/`; Python and native runtime notices are also packaged by Chaquopy. AndroidX uses Apache 2.0. CalcMax is an independent application, not affiliated with or endorsed by Casio.
