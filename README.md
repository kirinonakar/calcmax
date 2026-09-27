# CalcMax
<p align="center">
  <img src="app/src/main/ic_launcher-playstore.png" alt="CalcMax" width="100" height="100" />
</p>

A native Android scientific, graphing, programming and Computer Algebra System (CAS) calculator. A bundled SymPy engine provide exact offline mathematics.

<img src="screenshot.png" alt="screenshot" width="50%">

## 📥 Download
You can download the latest release from the [Releases Page](https://github.com/kirinonakar/calcmax/releases).

## Everyday use

* The first keypad page groups scientific and numeric operations. The second page groups symbolic tools.
* Long-press a key to apply its SHIFT function directly; long-press the mode indicator below the title bar to jump straight to the Scientific/CAS workspace.
* `Keyboard` enables Android text entry; software keyboard input overlays the lower keys without resizing the instrument, and hardware keyboards also work in the natural display. `Paste` inserts clipboard text. `Copy` copies a selected expression when one is selected; otherwise it alternates between the answer and expression. `Cut` copies and removes a selected expression.
* S⇔D switches exact and decimal results; SHIFT S⇔D switches improper/mixed fractions. The top `Catalog` button opens the searchable function catalog.
* RCL lists stored values. Tap a stored variable to select it, then choose Store, Recall or Delete. The variable shortcuts stay visible while scrolling. SHIFT AC is CLR ALL and clears the visible tape and variables while preserving History, settings, assumptions and custom functions.

### Numbers and precision

Decimal literals are exact rationals. Internal precision (3–200 significant digits, default 30) drives numeric algorithms, while display digits (default 10) limit how many digits a numeric or decimal result shows; exact integers, fractions and symbolic forms are never rounded, display digits never exceed internal precision, and Ans/STO keep the full-precision value. Numeric trig honors DEG/RAD/GRAD; explicit π or ° specifies a radian/degree expression. Symbolic calculus is in radians. Complex values use `i`; `polar(r,theta)`, `rectpolar(z)`, `re`, `im`, `arg` and `conj` are available. `prime(n)` returns the nth prime and `isprime(n)` returns true or false for an integer.

### Workspaces

MODE opens scientific/CAS, graphing, Python, equations, matrix, vector, statistics, programmer, units, constants, tip, currency and custom functions workspaces.

* **Graphing** — supports Cartesian, parametric, polar, sequence, 3D surface and first-order differential-equation graphs. Cartesian/parametric/polar/sequence modes accept up to six expressions per line; surfaces and differential equations accept one. Sequence rules use `n` and can refer to earlier values with `u(n-1)`; set the seed values in the graph panel. Surface input is `z=f(x,y)`. Differential input is `dy/dt=f(t,y)` with one or more initial y values at `t₀`; solutions use a fixed-step RK4 integrator and include a direction field. Graph tables show sampled values and tapping a row traces the corresponding point. A line that starts with `[shade]` shades an inequality region such as `y < f(x)` or the area between one or two functions, optionally limited to an `a..b` interval. Expressions may use free parameters such as a, b and c; the graph panel adds an adjustable range slider for each parameter and Animate sweeps their values to explore a family of curves. Analysis covers Cartesian, parametric and polar curves: roots, intersections, extrema, inflection points, derivative and tangent slopes with a drawn tangent line, areas and arc lengths are marked in a selected interval, and a polar integral is the enclosed sector area. Curve sampling starts with a regular grid and adds points around detected curvature and breaks. Directional pinches lock to x for horizontal finger placement, y for vertical placement, and equal x/y scaling for diagonal placement. Tap a number once to select it and again to place its blinking internal cursor. Setup offers separate input/output font sizes.
  The Range dialog offers numeric inputs and sliders for each visible axis. For 3D surfaces, the z range can follow sampled values automatically or be set manually.
* **Python** — edits and runs scripts using the bundled interpreter. New/Open/Save/Save as use Android's document picker for `.py` files. Import and Function menus insert common statements and templates. Choosing a function from the shared Catalog inserts `calc.function(...)` at the Python cursor, or `print(calc.function(...))` in an empty file, and places the catalog and symbol imports once at the top. The bundled adapter supports calculator catalog functions, symbols and saved custom functions; use Python's `**` for exponentiation. Completion suggestions include Python names, names in the script and common module members. Choosing a module completion also inserts its import if needed. Execution can be stopped and has a 20-second service deadline. Imports are limited to Python's bundled and installed packages; this mode does not install packages at runtime.
* **Equations** — provides coefficient forms for linear/quadratic/cubic equations, multi-line systems and general exact/numeric solving.
* **Data & Statistics** — saves named one-column lists and paired x,y datasets locally, imports and exports CSV, and can store a dataset as a calculator variable. It provides summaries, scatterplots, histograms, box plots, and linear, quadratic, logarithmic, exponential and power regression. Statistics distinguish population and sample variance/SD; quartiles use the inclusive interpolation convention. The workspace also hosts a Distributions panel (normal, t, χ², F, binomial, Poisson and geometric densities, cumulative probabilities, intervals and quantiles) and a Tests & intervals panel (t and z tests, χ² goodness-of-fit, one-way ANOVA, and confidence intervals, from raw data or summary statistics).
* **Programmer** — inputs use the selected base, mask to 8/16/32/64 bits, and expose all four bases. Shift counts are entered in the selected base; right shifts are arithmetic in signed mode and logical in unsigned mode.
* **Currency** — supports a manual rate or the latest [ExchangeRate-API daily reference rates](https://www.exchangerate-api.com/docs/free). A successful download is cached privately with both its provider reference time and local fetch time. Online-mode entry checks the cache; a download is performed only when the cache is at least 24 hours old.
* **Functions** — provides creation, editing, insertion and deletion of reusable formulas, plus JSON export and import of the custom library through Android's document picker; importing validates each definition and reports added, replaced and skipped entries.

### Distributions, tests and finance

The catalog adds probability distributions (`normpdf`, `normcdf`, `invnorm`, `tpdf`, `tcdf`, `invt`, `chi2pdf`, `chi2cdf`, `fpdf`, `fcdf`, `binompdf`, `binomcdf`, `poissonpdf`, `poissoncdf`, `geometpdf`, `geometcdf`), one-sample tests with optional one-sided p values (`ttest`, `ztest`, `chi2test`, `anova`) and confidence intervals (`tinterval`, `zinterval`). Results stay exact where SymPy supplies a closed form (`normcdf(0)` is 1/2, `fcdf(3,2,4)` is 0.84) and the remaining cumulative probabilities and quantiles use mpmath at the internal precision.

Finance functions follow the TVM cash-flow convention with the rate per payment period: `tvmfv`, `tvmpv`, `tvmpmt`, `tvmn`, `tvmrate`, `npv`, `irr` and `amort` return numeric results, and an optional final `begin` selects payments at the start of each period.

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
integrate(exp(-x^2)*cos(2x),(x,0,oo))
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
prime(1000)
isprime(123457)
stats([1,2,3,4])
regression([[1,2],[2,4],[3,6]],linear)
convert(32,degF,degC)
qty(2,m)+qty(30,cm)
convert(qty(1,kg)*qty(2,mps2),N)
apart(1/(x*(x+1)),x)
gradient(x^2+y^2,[x,y])
dsolve(diff(y(t),t)=y(t),y(t),t)
laplace(sin(t),t,s)
charpoly([[1,2],[3,4]],x)
convert(qty(1,V)/qty(1,ohm),A)
normcdf(-1.96,1.96)
invnorm(0.975)
ttest(0,[1,2,3,4])
tinterval(0.95,[1,2,3,4])
npv(0.1,-1000,[300,400,500])
tvmpmt(360,0.05/12,250000)
```

## Build

Requirements: JDK 21 (Android Studio's bundled runtime works), Android SDK 36.1, Python 3.14 on PATH, and an initial internet connection to download build dependencies. The installed application computes offline. Supported devices: Android 8/API 26 or later, arm64-v8a and x86_64.

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :app:assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`. `:app:assembleRelease` builds the unsigned release variant; use your own signing credentials for distribution. No release signing secret is checked in.

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

Scientific, CAS, equation, graphing, matrix/vector and statistics operations consume the same AST. Graphing compiles already validated symbolic expressions to local numeric functions; it never parses a separate expression language. Tip and currency forms use exact decimal value objects from the independent math module. SymPy's `parse_expr` and unrestricted string `eval` are not used for user input. Result ASTs preserve Ans/STO values without reparsing printed mathematics. The engine adapter isolates the UI from SymPy.

Computation runs in a bound service in a separate `:math` process. The calculator expression engine has recursion, AST size, numeric size, step and time limits. A 20-second IPC deadline can terminate and restart the worker process; cancellation therefore also works for operations that do not cooperate with coroutine cancellation. The expression engine's default budget is eight seconds; heavy symbolic calls such as integration and transforms keep a larger step allowance and may use up to sixteen seconds inside the IPC window. Python mode runs the user's script directly in the service process and is subject to the IPC deadline. Errors and unevaluated symbolic results are displayed rather than replaced by fabricated answers.

History is capped at 500 entries and stored privately on the device. Turning persistence off deletes its saved copy. Variables/functions/preferences remain local. Android backup behavior follows the manifest backup configuration.

## Validation

```powershell
.\gradlew.bat :math:test :math:exportCases :app:lintDebug
python -m venv .venv
.\.venv\Scripts\python.exe -m pip install sympy==1.14.0
.\.venv\Scripts\python.exe -m unittest discover -s tests -v
.\gradlew.bat :app:connectedDebugAndroidTest
```

The desktop tests consume AST fixtures produced by the actual Kotlin parser. They cover the specification's exact examples, precedence, complex arithmetic, calculus, solving, domains, matrices, statistics, graph discontinuities, units, precision, safeguards, result serialization and 100 seeded rational arithmetic cases. Device tests cover the native keypad, actual service IPC, cancellation/recovery, CAS, graphing, saved variables, activity recreation, both themes, system theme changes and landscape. Regression tests also assert live results without `=`, unchanged keypad bounds, boxed-answer continuation, vertical history gestures, fraction exit hit targets, symbolic/decimal typesetting and calculus at bounds/points. Tests produce screenshots in the app's private `files/qa` directory.

## Explicit bounds

* Symbolic integration/solving is subject to SymPy's algorithmic coverage and the computation budget. Unsolved integrals and conditional solution sets are retained with an explanatory message. There is no claim of solving every possible symbolic problem.
* Matrix entry grid: up to 9×9 and vector entry: up to 9 components; expression matrices: up to 32×32. Large exact factorizations/eigensystems may time out.
* Graphs use bounded adaptive samples; very narrow features can still be missed. Cartesian analysis controls apply only to Cartesian functions. The RK4 differential-equation plots are numerical approximations, and 3D surfaces use a finite wireframe grid.
* General output such as condition sets and series remainder terms can use textual mathematical notation where a dedicated native layout is not available. Such results may be copyable but not reusable through Ans; the app disables result insertion for them.
* Statistical tests cover one-sample z and t tests, goodness-of-fit χ² and one-way ANOVA; two-sample and proportion procedures are not included. Exact binomial sums cap the list form of `binom*` at n ≤ 100 and single probabilities at n ≤ 1000.

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

Copyright (c) 2026 **kirinonakar**. All rights reserved.

## Third-party notices

SymPy 1.14.0 and mpmath 1.3.0 use BSD licenses. Chaquopy 17 uses the MIT license. Full licenses are included in `app/src/main/assets/licenses/`; Python and native runtime notices are also packaged by Chaquopy. AndroidX uses Apache 2.0.
