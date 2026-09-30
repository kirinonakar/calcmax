# Android calculator alignment

Reference: repository `screenshot.png`, `CalculatorApp.kt`, `CalculatorKeypad.kt`, and `theme/Theme.kt`.

Implemented the native color tokens and header/mode/tape/keypad order. Both key pages mirror the native KeySpec rows: four utility keys and four function keys around a direction pad, three six-column scientific/CAS rows, and four five-column numeric rows. Key hold uses the native SHIFT alternate without a second normal click. SHIFT, ALPHA, HYP, memory, cursor selection, and mode actions are connected. Natural source display uses MathML and editable empty slots. Language/theme controls live in Setup.

The scientific workspace occupies one dynamic viewport with bounded grid tracks, zero minimum sizes, and internal expression/result overflow. The short wide layout places the keypad beside the calculation tape. Numeric-only `scr` keeps the number rows visible.

Source layout, module Worker boot, pointer timing, duplicate-click suppression, page switching, math rendering, themes/languages, calculations, and cancellation/recovery were checked by code/DOM/WASM tests.

Browser rendering and same-viewport screenshot comparison were not performed: the user's AGENTS instruction prohibits computer use unless explicitly requested. Pixel fidelity and actual browser geometry remain visually unverified.

final result: blocked (visual comparison only)
