package com.kirinonakar.symvacas.ui

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

/** In long-format x,y data, x names a group and each nonblank y is one observation. */
internal fun statisticsGroupedValues(rows:List<List<String>>):List<Pair<String,List<String>>> {
    val groups=linkedMapOf<String,MutableList<String>>()
    rows.forEach {row->
        val name=row.getOrNull(0)?.trim()?.takeIf(String::isNotBlank)
        val value=row.getOrNull(1)?.trim()?.takeIf(String::isNotBlank)
        if(name!=null&&value!=null)groups.getOrPut(name){mutableListOf()}.add(value)
    }
    return groups.map {(name,values)->name to values.toList()}
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
    grouping: String = "columns",
    firstGroup: String? = null,
    secondGroup: String? = null,
    yatesCorrection: Boolean = true,
): String? {
    fun values(index: Int) = rows.mapNotNull { it.getOrNull(index)?.trim()?.takeIf(String::isNotBlank) }
    fun vector(entries: List<String>) = entries.joinToString(",", "[", "]")
    fun validNumber(text: String) = text.toDoubleOrNull()?.isFinite() == true
    val names=statisticsColumnNames(kind)
    val x = values(0)
    val y = if (names.size>1) values(1) else emptyList()
    val pairs = if (names.size>1) rows.mapNotNull { row ->
        val left = row.getOrNull(0)?.trim()?.takeIf(String::isNotBlank)
        val right = row.getOrNull(1)?.trim()?.takeIf(String::isNotBlank)
        if (left != null && right != null) left to right else null
    } else emptyList()
    val categoryX=pairs.map {it.first}.distinct()
    val categoryY=pairs.map {it.second}.distinct()
    val categoryPairs=if(grouping=="group-value")pairs.map {(left,right)->(categoryX.indexOf(left)+1).toString() to (categoryY.indexOf(right)+1).toString()} else pairs
    val grouped=if(kind=="xy"&&grouping=="group-value")statisticsGroupedValues(rows) else emptyList()
    val first=grouped.firstOrNull {it.first==firstGroup} ?: grouped.firstOrNull()
    val second=if(secondGroup==first?.first)null else grouped.firstOrNull {it.first==secondGroup} ?: grouped.firstOrNull {it.first!=first?.first}
    val sample = if(grouped.isNotEmpty())first?.second.orEmpty() else values(names.indexOf(column).coerceAtLeast(0))
    val groups=when {
        kind=="xy"&&grouping=="group-value"->grouped.map {it.second}
        names.size>1->names.indices.map(::values)
        else->emptyList()
    }
    val tailArgument = when (tail) { "Left" -> ",left"; "Right" -> ",right"; else -> "" }
    val validSigma = sigma.toDoubleOrNull()?.let { it.isFinite() && it > 0 } == true
    val validSigmaY = sigmaY.toDoubleOrNull()?.let { it.isFinite() && it > 0 } == true
    val validLevel = level.toDoubleOrNull()?.let { it.isFinite() && it > 0 && it < 100 && it != 1.0 } == true
    return when (procedure) {
        "Wilcoxon" -> if(kind!="list"&&pairs.isNotEmpty())"wilcoxon(${vector(pairs.map {it.first})},${vector(pairs.map {it.second})}$tailArgument)" else if(kind=="list"&&sample.isNotEmpty())"wilcoxon(${vector(sample)}$tailArgument)" else null
        "Mann–Whitney" -> if(grouping=="group-value"&&kind=="xy") {
            if(first!=null&&second!=null&&first.second.isNotEmpty()&&second.second.isNotEmpty())"mannwhitney(${vector(first.second)},${vector(second.second)}$tailArgument)" else null
        } else {
            val left=firstGroup?.takeIf {it in names} ?: "x"
            val right=secondGroup?.takeIf {it in names} ?: "y"
            val leftValues=values(names.indexOf(left).coerceAtLeast(0))
            val rightValues=values(names.indexOf(right).coerceAtLeast(0))
            if(names.size>=2&&left!=right&&leftValues.isNotEmpty()&&rightValues.isNotEmpty())"mannwhitney(${vector(leftValues)},${vector(rightValues)}$tailArgument)" else null
        }
        "Kruskal–Wallis" -> if(groups.size>=2&&groups.all {it.isNotEmpty()})"kruskal(${groups.joinToString(","){vector(it)}})" else null
        "t test" -> when {
            !validNumber(mu0) -> null
            grouping=="group-value"&&kind=="xy" -> if(first!=null&&second!=null&&first.second.size>=2&&second.second.size>=2)"ttest2($mu0,${vector(first.second)},${vector(second.second)}$tailArgument)" else null
            kind == "xy" && column == "x-y" && x.size >= 2 && y.size >= 2 -> "ttest2($mu0,${vector(x)},${vector(y)}$tailArgument)"
            kind == "xy" && column == "paired" && pairs.size >= 2 -> "ttestpaired($mu0,${vector(pairs.map { it.first })},${vector(pairs.map { it.second })}$tailArgument)"
            column in names -> sample.takeIf { it.size >= 2 }?.let { "ttest($mu0,${vector(it)}$tailArgument)" }
            else -> null
        }
        "z test" -> when {
            !validNumber(mu0) || !validSigma -> null
            grouping=="group-value"&&kind=="xy" -> if(first!=null&&second!=null&&validSigmaY&&first.second.isNotEmpty()&&second.second.isNotEmpty())"ztest2($mu0,$sigma,$sigmaY,${vector(first.second)},${vector(second.second)}$tailArgument)" else null
            kind == "xy" && column == "x-y" && validSigmaY && x.isNotEmpty() && y.isNotEmpty() -> "ztest2($mu0,$sigma,$sigmaY,${vector(x)},${vector(y)}$tailArgument)"
            column in names -> sample.takeIf { it.isNotEmpty() }?.let { "ztest($mu0,$sigma,${vector(it)}$tailArgument)" }
            else -> null
        }
        "χ² test" -> if (names.size>1 && pairs.size >= 2 && (grouping!="group-value"||categoryX.size>=2&&categoryY.size>=2)) "chi2independence(${vector(categoryPairs.map { it.first })},${vector(categoryPairs.map { it.second })},${if(yatesCorrection)1 else 0})" else null
        "Fisher exact" -> if (names.size>1 && pairs.size >= 2 && (grouping!="group-value"||categoryX.size==2&&categoryY.size==2)) "fisherexact(${vector(categoryPairs.map { it.first })},${vector(categoryPairs.map { it.second })}$tailArgument)" else null
        "ANOVA","Tukey HSD" -> if (groups.size>=2&&groups.all {it.size>=2}) "${if(procedure=="ANOVA")"anova" else "tukey"}(${groups.joinToString(",") {vector(it)}})" else null
        "Shapiro–Wilk" -> sample.takeIf { it.size in 3..5000 }?.let { "shapiro(${vector(it)})" }
        "t interval" -> sample.takeIf { it.size >= 2 && validLevel }?.let { "tinterval($level,${vector(it)})" }
        "z interval" -> sample.takeIf { it.isNotEmpty() && validLevel && validSigma }?.let { "zinterval($level,$sigma,${vector(it)})" }
        else -> null
    }
}
