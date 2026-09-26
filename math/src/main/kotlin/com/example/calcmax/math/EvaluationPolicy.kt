package com.example.calcmax.math

private val multiArgumentFunctions = setOf(
    "round", "nCr", "nPr", "gcd", "lcm", "quotient", "remainder",
    "collect", "subs", "diff", "integrate", "limit", "series", "sum", "product", "solve",
    "nsolve", "nintegrate", "nderivative", "minimum", "maximum", "piecewise",
    "polar", "pol", "rec", "randInt", "eng", "dms",
    "linsolve", "dot", "cross", "angle", "projection", "regression", "qty", "convert",
    "tpdf", "tcdf", "invt", "chi2pdf", "chi2cdf", "fpdf", "fcdf",
    "binompdf", "binomcdf", "poissonpdf", "poissoncdf", "geometpdf", "geometcdf",
    "ttest", "ztest", "chi2test", "anova", "tinterval", "zinterval",
    "tvmfv", "tvmpv", "tvmpmt", "tvmn", "tvmrate", "npv", "irr", "amort"
)

/** Entry helpers that keep previewing while typing even though they take more than one argument. */
private val previewFunctions = setOf("log", "nthroot", "mixed")

fun requiresExplicitEvaluation(tree: Expr, userFunctions: Set<String> = emptySet()): Boolean =
    tree.nodes().any { node ->
        node.kind == "call" && node.value !in previewFunctions &&
            (node.args.size > 1 || node.value in multiArgumentFunctions || node.value in userFunctions)
    }
