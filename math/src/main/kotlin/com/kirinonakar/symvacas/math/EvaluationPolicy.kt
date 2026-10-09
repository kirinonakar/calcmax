package com.kirinonakar.symvacas.math

private val multiArgumentFunctions = setOf(
    "round", "roundh", "nCr", "nPr", "gcd", "lcm", "quotient", "remainder", "mod", "divmod",
    "collect", "subs", "diff", "integrate", "limit", "series", "sum", "product", "solve",
    "nsolve", "nintegrate", "nderivative", "minimum", "maximum", "piecewise",
    "polar", "pol", "rec", "rnd", "randInt", "eng", "dms",
    "linsolve", "dot", "cross", "angle", "projection", "regression", "qty", "convert",
    "tpdf", "tcdf", "invt", "chi2pdf", "chi2cdf", "fpdf", "fcdf",
    "binompdf", "binomcdf", "poissonpdf", "poissoncdf", "geometpdf", "geometcdf",
    "ttest", "ztest", "chi2test", "anova", "tukey", "wilcoxon", "mannwhitney", "kruskal", "tinterval", "zinterval",
    "ancova", "glm", "bayesproportion", "bayesmean", "bayesrate", "bayescompare", "padjust", "cohend", "eta2", "levene", "bartlett", "mcnemar", "survivalanalysis", "kaplanmeier", "logrank", "cox", "repeatedanova", "mixedmodel", "glmm", "gee", "multinomial", "ordinal", "poissonreg", "nbreg", "bootstrapci", "bayesbootstrap", "testpower", "samplesize", "kstest", "crossvalidate", "pca", "kmeans", "impute",
    "tvmfv", "tvmpv", "tvmpmt", "tvmn", "tvmrate", "npv", "irr", "amort", "cagr"
)

/** Entry helpers that keep previewing while typing even though they take more than one argument. */
private val previewFunctions = setOf("log", "nthroot", "mixed", "mod", "divmod")

fun requiresExplicitEvaluation(tree: Expr, userFunctions: Set<String> = emptySet()): Boolean =
    tree.nodes().any { node ->
        node.kind == "call" && node.value !in previewFunctions &&
            (node.args.size > 1 || node.value in multiArgumentFunctions || node.value in userFunctions)
    }
