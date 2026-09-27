package com.kirinonakar.calcmax.ui

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
): String? {
    val populated = rows.filter { row -> row.any { it.isNotBlank() } }
    fun values(index: Int): List<String>? {
        if (populated.isEmpty()) return null
        return populated.map { it.getOrNull(index)?.trim().orEmpty() }
            .takeIf { entries -> entries.all { it.isNotBlank() } }
    }
    fun vector(entries: List<String>) = entries.joinToString(",", "[", "]")
    fun validNumber(text: String) = text.toDoubleOrNull()?.isFinite() == true
    val x = values(0)
    val y = if (kind == "xy") values(1) else null
    val sample = if (kind == "xy" && column == "y") y else x
    val tailArgument = when (tail) { "Left" -> ",left"; "Right" -> ",right"; else -> "" }
    val validSigma = sigma.toDoubleOrNull()?.let { it.isFinite() && it > 0 } == true
    val validLevel = level.toDoubleOrNull()?.let { it.isFinite() && it > 0 && it < 100 && it != 1.0 } == true
    return when (procedure) {
        "t test" -> sample?.takeIf { it.size >= 2 && validNumber(mu0) }?.let { "ttest($mu0,${vector(it)}$tailArgument)" }
        "z test" -> sample?.takeIf { it.isNotEmpty() && validNumber(mu0) && validSigma }?.let { "ztest($mu0,$sigma,${vector(it)}$tailArgument)" }
        "χ² test" -> if (x != null && y != null && x.size >= 2) "chi2test(${vector(x)},${vector(y)})" else null
        "ANOVA" -> if (x != null && y != null && x.size >= 2) "anova(${vector(x)},${vector(y)})" else null
        "t interval" -> sample?.takeIf { it.size >= 2 && validLevel }?.let { "tinterval($level,${vector(it)})" }
        "z interval" -> sample?.takeIf { it.isNotEmpty() && validLevel && validSigma }?.let { "zinterval($level,$sigma,${vector(it)})" }
        else -> null
    }
}
