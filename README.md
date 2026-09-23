# CalcMax

A native Android scientific, graphing and symbolic calculator. Kotlin and Jetpack Compose provide the instrument interface; an independent Kotlin AST and a bundled SymPy engine provide exact offline mathematics. No WebView, remote mathematical calculation service, or LLM is used. The currency converter alone downloads online reference rates and keeps an offline cache.

## Build

Requirements: JDK 21 (Android Studio's bundled runtime works), Android SDK 36.1, Python 3.14 on PATH, and an initial internet connection to download build dependencies. The installed application computes offline. Supported devices: Android 8/API 26 or later, arm64-v8a and x86_64.

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :app:assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`. `:app:assembleRelease` builds the unsigned release variant; use your own signing credentials for distribution. No release signing secret is checked in.

## Everyday use

* The first keypad page follows the [FX-991ES PLUS 2nd edition layout](https://www.casio.com/intl/scientific-calculators/product.FX-991ESPLUS-2/), with the ON position changed to a 2nd/1st page switch. The second page adds symbolic algebra, number theory, matrix and vector functions. The keypad stays anchored to the bottom while the expression/history region scrolls independently. ALPHA log enters `z`; ALPHA ln enters `t`.
* Complete expressions are evaluated as you type. `=` commits a calculation without removing the answer panel. The next calculation appears underneath; beginning it with an operator inserts a boxed, frozen copy of the previous answer. Swipe the display vertically to revisit calculations.
* Fractions, roots and calculus keys insert structural slots. Tap a slot or expression component to select it. Tap the space just after a fraction to leave its denominator. Left/right move the cursor; up selects its enclosing AST node; down selects a child.
* Selecting an exponent and pressing right once places the cursor after its value **inside** the exponent. Press right again to leave it. Parentheses follow the same inside/outside behavior. Empty power bases and fraction fields are shaded slots; filled slots have no visible scaffolding parentheses. Expressions share a mathematical alignment axis, so powers and fractions do not shift adjacent operands vertically.
* `Keyboard` enables Android text entry; hardware keyboards also work in the natural display. `Paste` inserts clipboard text.
* S⇔D switches exact and decimal results; SHIFT S⇔D switches improper/mixed fractions. The top `Catalog` button opens the searchable function catalog. Decimal output omits trailing zeros, and symbolic expressions remain typeset in decimal mode.
* MODE opens scientific, CAS, graphing, equations, matrix, vector, statistics, programmer, units, constants, tip and currency workspaces.
* SETUP chooses Light, Dark, or System (the default), angle unit, precision, haptics, sound and optional persistent history. Theme changes preserve the current calculation and editor state.
* RCL / SHIFT RCL open variable recall and storage. `radius=5` and `f(x)=x^2+1` are supported at the top level outside Equation mode. Use `solve(...)` to solve equations. Stored values are snapshots; user functions retain their expression bodies.
* The integral key inserts a definite integral; SHIFT integral inserts a derivative evaluated at a point. The display toolbar also provides indefinite integral, definite integral, symbolic derivative and point derivative templates.

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

Decimal literals are exact rationals. Decimal results use 3–200 configurable significant digits, with 3/10/15/30/50/100/200 presets and a custom entry. Numeric trig honors DEG/RAD/GRAD; explicit π or ° specifies a radian/degree expression. Symbolic calculus is in radians. Complex values use `i`; `polar(r,theta)`, `rectpolar(z)`, `re`, `im`, `arg` and `conj` are available. `log(x)` is base 10 and `ln(x)` is natural log. M+/M− update independent memory without replacing the displayed result with the memory total.

The graph workspace accepts one expression per line (six curves), including user functions. Cartesian variable: `x`. Parametric input: `[cos(t),sin(t)]`; polar input: `2*cos(3*t)`. Traces show sampled approximations. Cartesian analysis provides bracketed roots/intersections, interval extrema, derivatives and integrals. Graphing is a visual numerical tool, not a proof that all roots or singularities have been found.

The `x: decimal` / `x: π rad` control changes horizontal tick labels to radians expressed in multiples of π. It changes axis notation, not the underlying expression or samples.

The tip calculator supports a pre-tax bill, separate tip/tax percentages, currency precision and splitting between people. A remainder allocation ensures rounded shares sum exactly to the total.

The currency converter supports a manual rate or the latest [ExchangeRate-API daily reference rates](https://www.exchangerate-api.com/docs/free). A successful download is cached privately with both its provider reference time and local fetch time. Online-mode entry checks the cache; a download is performed only when at least 24 hours old. If the screen stays open, it checks again when the cache expires. When offline, the last saved snapshot remains usable with its original timestamp. Manual rates are explicitly labeled. These are indicative daily reference rates, not streaming market/trading quotes; there is no background fetch while the app is closed.

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

Scientific, CAS, equation, graphing, matrix/vector and statistics operations consume the same AST. Graphing compiles already validated symbolic expressions to local numeric functions; it never parses a separate expression language. Tip and currency forms use exact decimal value objects from the independent math module. SymPy's `parse_expr` and unrestricted string `eval` are not used for user input. Result ASTs preserve Ans/STO values without reparsing printed mathematics. The engine adapter isolates the UI from SymPy.

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

The desktop tests consume AST fixtures produced by the actual Kotlin parser. They cover the specification's exact examples, precedence, complex arithmetic, calculus, solving, domains, matrices, statistics, graph discontinuities, units, precision, safeguards, result serialization and 100 seeded rational arithmetic cases. Device tests cover the native keypad, actual service IPC, cancellation/recovery, CAS, graphing, saved variables, activity recreation, both themes, system theme changes and landscape. Regression tests also assert live results without `=`, unchanged keypad bounds, boxed-answer continuation, vertical history gestures, fraction exit hit targets, symbolic/decimal typesetting and calculus at bounds/points. Tests produce screenshots in the app's private `files/qa` directory.

## Explicit bounds

* Symbolic integration/solving is subject to SymPy's algorithmic coverage and the computation budget. Unsolved integrals and conditional solution sets are retained with an explanatory message. There is no claim of solving every possible symbolic problem.
* Matrix entry grid: up to 4×4; expression matrices: up to 32×32. Large exact factorizations/eigensystems may time out.
* Graphs use finite samples and break large discontinuities; narrow features can be missed. Analysis controls currently apply to Cartesian functions; all three graph types support pan, zoom, ranges and tracing.
* General output such as condition sets and series remainder terms can use textual mathematical notation where a dedicated native layout is not available. Such results may be copyable but not reusable through Ans; the app disables result insertion for them.
* The keypad retains its physical arrangement in landscape, using more compact keys beside the display. The application has been tested on an Android 16 emulator; physical-device and broader Android-version qualification is still needed before a store release.

## Third-party notices

SymPy 1.14.0 and mpmath 1.3.0 use BSD licenses. Chaquopy 17 uses the MIT license. Full licenses are included in `app/src/main/assets/licenses/`; Python and native runtime notices are also packaged by Chaquopy. AndroidX uses Apache 2.0. CalcMax is an independent application, not affiliated with or endorsed by Casio.
