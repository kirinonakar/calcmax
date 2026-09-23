package com.example.calcmax.math

private val multiArgumentFunctions = setOf(
    "round", "nthroot", "log", "nCr", "nPr", "gcd", "lcm", "quotient", "remainder",
    "collect", "subs", "diff", "integrate", "limit", "series", "sum", "product", "solve",
    "nsolve", "nintegrate", "nderivative", "minimum", "maximum", "piecewise",
    "polar", "pol", "rec", "randInt", "eng", "dms", "mixed",
    "linsolve", "dot", "cross", "angle", "projection", "regression", "qty", "convert"
)

fun requiresExplicitEvaluation(tree: Expr, userFunctions: Set<String> = emptySet()): Boolean =
    tree.nodes().any { node ->
        node.kind == "call" && (node.args.size > 1 || node.value in multiArgumentFunctions || node.value in userFunctions)
    }
