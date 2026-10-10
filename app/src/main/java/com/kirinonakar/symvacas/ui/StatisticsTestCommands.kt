package com.kirinonakar.symvacas.ui

/** Route results by the selected test family, independently of edited input values. */
internal fun statisticsTestAnalyses(procedure:String):Set<String> = when(procedure) {
    "t test"->setOf("ttest","ttest2","ttestpaired")
    "z test"->setOf("ztest","ztest2")
    "χ² test"->setOf("chi2test","chi2independence")
    "Fisher exact"->setOf("fisherexact")
    "ANOVA"->setOf("anova","welchanova")
    "Games–Howell"->setOf("gameshowell")
    "Tukey HSD"->setOf("tukey")
    "Shapiro–Wilk"->setOf("shapiro")
    "Wilcoxon"->setOf("wilcoxon")
    "Mann–Whitney"->setOf("mannwhitney")
    "Kruskal–Wallis"->setOf("kruskal")
    "t interval"->setOf("tinterval")
    "z interval"->setOf("zinterval")
    else->emptySet()
}

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
internal fun statisticsGroupedValues(rows:List<List<String>>,groupColumn:Int=0,valueColumn:Int=1):List<Pair<String,List<String>>> {
    val groups=linkedMapOf<String,MutableList<String>>()
    rows.forEach {row->
        val name=row.getOrNull(groupColumn)?.trim()?.takeIf(String::isNotBlank)
        val value=row.getOrNull(valueColumn)?.trim()?.takeIf(String::isNotBlank)
        if(name!=null&&value!=null)groups.getOrPut(name){mutableListOf()}.add(value)
    }
    return groups.map {(name,values)->name to values.toList()}
}

internal fun statisticsCategoryPairs(rows:List<List<String>>,first:Int,second:Int):List<Pair<String,String>> =
    if(first<0||second<0||first==second)emptyList() else rows.mapNotNull {row->
        val left=row.getOrNull(first)?.trim()?.takeIf(String::isNotBlank)
        val right=row.getOrNull(second)?.trim()?.takeIf(String::isNotBlank)
        if(left!=null&&right!=null)left to right else null
    }

internal fun statisticsCategoryLabels(pairs:List<Pair<String,String>>,first:String,second:String,shared:Boolean=false):Map<String,String> {
    val numeric=!shared&&pairs.all {it.first.toBigDecimalOrNull()!=null&&it.second.toBigDecimalOrNull()!=null}
    val left=if(shared)pairs.flatMap {listOf(it.first,it.second)}.distinct() else statisticsCategories(pairs.map {it.first},numeric)
    val right=if(shared)left else statisticsCategories(pairs.map {it.second},numeric)
    return mapOf("table:row" to first,"table:column" to second)+
        left.mapIndexed {i,label->"table:row:${i+1}" to label}+
        right.mapIndexed {i,label->"table:column:${i+1}" to label}
}

internal fun statisticsCategories(values:List<String>,numeric:Boolean):List<String> =
    if(numeric)values.distinctBy {it.toBigDecimal().stripTrailingZeros()}.sortedBy {it.toBigDecimal()} else values.distinct()

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
    anovaMethod:String="welch",
    groupColumn:Int=0,
    valueColumn:Int=1,
    matching:String="order",
    subjectColumn:Int=0,
    pairedComparison:Boolean=false,
    independentMethod:String="welch",
    groupColumns:String="auto",
): String? {
    fun values(index: Int) = rows.mapNotNull { it.getOrNull(index)?.trim()?.takeIf(String::isNotBlank) }
    fun vector(entries: List<String>) = entries.joinToString(",", "[", "]")
    fun validNumber(text: String) = text.toDoubleOrNull()?.isFinite() == true
    val names=statisticsColumnNames(kind)
    if(grouping=="group-value"&&(groupColumn==valueColumn||groupColumn !in names.indices||valueColumn !in names.indices))return null
    val x = values(0)
    val y = if (names.size>1) values(1) else emptyList()
    val categorical=procedure in listOf("χ² test","Fisher exact")
    val firstColumn=if(categorical&&grouping=="group-value")groupColumn else if((categorical||procedure=="Wilcoxon"||procedure in listOf("t test","z test")&&column in listOf("x-y","paired"))&&firstGroup!=null)names.indexOf(firstGroup) else 0
    val secondColumn=if(categorical&&grouping=="group-value")valueColumn else if((categorical||procedure=="Wilcoxon"||procedure in listOf("t test","z test")&&column in listOf("x-y","paired"))&&secondGroup!=null)names.indexOf(secondGroup) else 1
    val pairs = statisticsCategoryPairs(rows,firstColumn,secondColumn)
    val numericCategories=grouping!="group-value"&&pairs.all {it.first.toBigDecimalOrNull()!=null&&it.second.toBigDecimalOrNull()!=null}
    val categoryX=statisticsCategories(pairs.map {it.first},numericCategories)
    val categoryY=statisticsCategories(pairs.map {it.second},numericCategories)
    val categoryPairs=if(numericCategories)pairs else pairs.map {(left,right)->(categoryX.indexOf(left)+1).toString() to (categoryY.indexOf(right)+1).toString()}
    val grouped=if(names.size>1&&grouping=="group-value")statisticsGroupedValues(rows,groupColumn,valueColumn) else emptyList()
    val first=grouped.firstOrNull {it.first==firstGroup} ?: grouped.firstOrNull()
    val second=if(secondGroup==first?.first)null else grouped.firstOrNull {it.first==secondGroup} ?: grouped.firstOrNull {it.first!=first?.first}
    val sample = if(grouped.isNotEmpty())first?.second.orEmpty() else values(names.indexOf(column).coerceAtLeast(0))
    val selectedColumns=if(groupColumns=="auto")names.indices.toList() else groupColumns.split(',').filter(String::isNotBlank).map {it.toIntOrNull() ?: -1}
    if(selectedColumns.distinct().size!=selectedColumns.size||selectedColumns.any {it !in names.indices})return null
    val groups=when {
        names.size>1&&grouping=="group-value"->grouped.map {it.second}
        names.size>1->selectedColumns.map(::values)
        else->emptyList()
    }
    val tailArgument = when (tail) { "Left" -> ",left"; "Right" -> ",right"; else -> "" }
    val independentArgument=if(independentMethod=="student")",student" else ""
    val validSigma = sigma.toDoubleOrNull()?.let { it.isFinite() && it > 0 } == true
    val validSigmaY = sigmaY.toDoubleOrNull()?.let { it.isFinite() && it > 0 } == true
    val validLevel = level.toDoubleOrNull()?.let { it.isFinite() && it > 0 && it < 100 && it != 1.0 } == true
    return when (procedure) {
        "Wilcoxon" -> if(grouping=="group-value")runCatching {
            val plan=statisticsComparisonData(rows,mapOf("grouping" to "groups","group" to groupColumn.toString(),"value" to valueColumn.toString(),"firstGroup" to firstGroup.orEmpty(),"secondGroup" to secondGroup.orEmpty(),"matching" to matching,"subject" to subjectColumn.toString()),paired=true)
            if(plan.matrix.isEmpty())null else "wilcoxon(${plan.samples.joinToString(",",transform=::vector)}$tailArgument)"
        }.getOrNull() else if(kind!="list"&&pairs.isNotEmpty())"wilcoxon(${vector(pairs.map {it.first})},${vector(pairs.map {it.second})}$tailArgument)" else if(kind=="list"&&sample.isNotEmpty())"wilcoxon(${vector(sample)}$tailArgument)" else null
        "Mann–Whitney" -> if(grouping=="group-value") {
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
            grouping=="group-value"&&pairedComparison->runCatching {
                val plan=statisticsComparisonData(rows,mapOf("grouping" to "groups","group" to groupColumn.toString(),"value" to valueColumn.toString(),"firstGroup" to firstGroup.orEmpty(),"secondGroup" to secondGroup.orEmpty(),"matching" to matching,"subject" to subjectColumn.toString()),paired=true)
                if(plan.matrix.size<2)null else "ttestpaired($mu0,${plan.samples.joinToString(",",transform=::vector)}$tailArgument)"
            }.getOrNull()
            grouping=="group-value" -> if(first!=null&&second!=null&&first.second.size>=2&&second.second.size>=2)"ttest2($mu0,${vector(first.second)},${vector(second.second)}$independentArgument$tailArgument)" else null
            names.size>1 && column == "x-y" && values(firstColumn).size>=2 && values(secondColumn).size>=2 -> "ttest2($mu0,${vector(values(firstColumn))},${vector(values(secondColumn))}$independentArgument$tailArgument)"
            names.size>1 && column == "paired" && pairs.size >= 2 -> "ttestpaired($mu0,${vector(pairs.map { it.first })},${vector(pairs.map { it.second })}$tailArgument)"
            column in names -> sample.takeIf { it.size >= 2 }?.let { "ttest($mu0,${vector(it)}$tailArgument)" }
            else -> null
        }
        "z test" -> when {
            !validNumber(mu0) || !validSigma -> null
            grouping=="group-value" -> if(first!=null&&second!=null&&validSigmaY&&first.second.isNotEmpty()&&second.second.isNotEmpty())"ztest2($mu0,$sigma,$sigmaY,${vector(first.second)},${vector(second.second)}$tailArgument)" else null
            names.size>1 && column == "x-y" && validSigmaY && x.isNotEmpty() && y.isNotEmpty() -> "ztest2($mu0,$sigma,$sigmaY,${vector(values(firstColumn))},${vector(values(secondColumn))}$tailArgument)"
            column in names -> sample.takeIf { it.isNotEmpty() }?.let { "ztest($mu0,$sigma,${vector(it)}$tailArgument)" }
            else -> null
        }
        "χ² test" -> if (names.size>1 && pairs.size >= 2 && categoryX.size>=2&&categoryY.size>=2) "chi2independence(${vector(categoryPairs.map { it.first })},${vector(categoryPairs.map { it.second })},${if(yatesCorrection)1 else 0})" else null
        "Fisher exact" -> if (names.size>1 && pairs.size >= 2 && categoryX.size==2&&categoryY.size==2) "fisherexact(${vector(categoryPairs.map { it.first })},${vector(categoryPairs.map { it.second })}$tailArgument)" else null
        "ANOVA","Tukey HSD","Games–Howell" -> if (groupColumn!=valueColumn&&groups.size>=2&&groups.all {it.size>=2}) "${when(procedure){"ANOVA"->if(anovaMethod=="classic")"anova" else "welchanova";"Games–Howell"->"gameshowell";else->"tukey"}}(${groups.joinToString(",") {vector(it)}})" else null
        "Shapiro–Wilk" -> sample.takeIf { it.size in 3..5000 }?.let { "shapiro(${vector(it)})" }
        "t interval" -> sample.takeIf { it.size >= 2 && validLevel }?.let { "tinterval($level,${vector(it)})" }
        "z interval" -> sample.takeIf { it.isNotEmpty() && validLevel && validSigma }?.let { "zinterval($level,$sigma,${vector(it)})" }
        else -> null
    }
}
