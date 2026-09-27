package com.kirinonakar.calcmax.ui

/** Correlation uses complete x,y rows so missing cells cannot shift the pairing. */
internal fun statisticsCorrelationCommand(rows: List<List<String>>, kind: String): String? {
    if (kind != "xy") return null
    val pairs = rows.mapNotNull { row ->
        val x = row.getOrNull(0)?.trim()?.takeIf(String::isNotBlank)
        val y = row.getOrNull(1)?.trim()?.takeIf(String::isNotBlank)
        if (x != null && y != null) x to y else null
    }
    if (pairs.size < 2) return null
    fun vector(values: List<String>) = values.joinToString(",", "[", "]")
    return "correlation(${vector(pairs.map { it.first })},${vector(pairs.map { it.second })})"
}

/** Build a test from the visible data table, never from a second copy of its values. */
internal fun statisticsTestCommand(
    procedure: String,
    rows: List<List<String>>,
    kind: String,
    column: String,
    tail: String,
    mu0: String,
    sigma: String,
    level: String,
    sigmaY: String = sigma,
): String? {
    fun values(index: Int) = rows.mapNotNull { it.getOrNull(index)?.trim()?.takeIf(String::isNotBlank) }
    fun vector(entries: List<String>) = entries.joinToString(",", "[", "]")
    fun validNumber(text: String) = text.toDoubleOrNull()?.isFinite() == true
    val x = values(0)
    val y = if (kind == "xy") values(1) else emptyList()
    val pairs = if (kind == "xy") rows.mapNotNull { row ->
        val left = row.getOrNull(0)?.trim()?.takeIf(String::isNotBlank)
        val right = row.getOrNull(1)?.trim()?.takeIf(String::isNotBlank)
        if (left != null && right != null) left to right else null
    } else emptyList()
    val sample = if (kind == "xy" && column == "y") y else x
    val tailArgument = when (tail) { "Left" -> ",left"; "Right" -> ",right"; else -> "" }
    val validSigma = sigma.toDoubleOrNull()?.let { it.isFinite() && it > 0 } == true
    val validSigmaY = sigmaY.toDoubleOrNull()?.let { it.isFinite() && it > 0 } == true
    val validLevel = level.toDoubleOrNull()?.let { it.isFinite() && it > 0 && it < 100 && it != 1.0 } == true
    return when (procedure) {
        "t test" -> when {
            !validNumber(mu0) -> null
            kind == "xy" && column == "x-y" && x.size >= 2 && y.size >= 2 -> "ttest2($mu0,${vector(x)},${vector(y)}$tailArgument)"
            kind == "xy" && column == "paired" && pairs.size >= 2 -> "ttestpaired($mu0,${vector(pairs.map { it.first })},${vector(pairs.map { it.second })}$tailArgument)"
            column == "x" || column == "y" -> sample.takeIf { it.size >= 2 }?.let { "ttest($mu0,${vector(it)}$tailArgument)" }
            else -> null
        }
        "z test" -> when {
            !validNumber(mu0) || !validSigma -> null
            kind == "xy" && column == "x-y" && validSigmaY && x.isNotEmpty() && y.isNotEmpty() -> "ztest2($mu0,$sigma,$sigmaY,${vector(x)},${vector(y)}$tailArgument)"
            column == "x" || column == "y" -> sample.takeIf { it.isNotEmpty() }?.let { "ztest($mu0,$sigma,${vector(it)}$tailArgument)" }
            else -> null
        }
        "χ² test" -> if (kind == "xy" && pairs.size >= 2) "chi2independence(${vector(pairs.map { it.first })},${vector(pairs.map { it.second })})" else null
        "Fisher exact" -> if (kind == "xy" && pairs.size >= 2) "fisherexact(${vector(pairs.map { it.first })},${vector(pairs.map { it.second })}$tailArgument)" else null
        "ANOVA" -> if (kind == "xy" && x.size >= 2 && y.size >= 2) "anova(${vector(x)},${vector(y)})" else null
        "Shapiro–Wilk" -> sample.takeIf { it.size in 3..5000 }?.let { "shapiro(${vector(it)})" }
        "t interval" -> sample.takeIf { it.size >= 2 && validLevel }?.let { "tinterval($level,${vector(it)})" }
        "z interval" -> sample.takeIf { it.isNotEmpty() && validLevel && validSigma }?.let { "zinterval($level,$sigma,${vector(it)})" }
        else -> null
    }
}
