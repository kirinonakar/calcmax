package com.kirinonakar.symvacas.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.kirinonakar.symvacas.calculator.CalculatorModel
import com.kirinonakar.symvacas.math.Editor
import com.kirinonakar.symvacas.ui.theme.LocalInstrument

@Composable internal fun StatisticsAnalysis(m: CalculatorModel,rows:List<List<String>>,kind:String,data:String="",rawRows:List<List<String>> = rows) {
    val c=LocalInstrument.current
    var expanded by rememberSaveable {mutableStateOf(true)}
    var test by rememberSaveable {mutableStateOf("t test")}
    var column by rememberSaveable {mutableStateOf("x")}
    var tail by rememberSaveable {mutableStateOf("Two-sided")}
    var mu0 by rememberSaveable {mutableStateOf("0")}
    var sigma by rememberSaveable {mutableStateOf("2")}
    var sigmaY by rememberSaveable {mutableStateOf("2")}
    var level by rememberSaveable {mutableStateOf("95")}
    var grouping by rememberSaveable {mutableStateOf("Columns")}
    var firstGroup by rememberSaveable {mutableStateOf("")}
    var secondGroup by rememberSaveable {mutableStateOf("")}
    var firstColumn by rememberSaveable {mutableStateOf("x")}
    var secondColumn by rememberSaveable {mutableStateOf("y")}
    var yatesCorrection by rememberSaveable {mutableStateOf(true)}
    val dataColumns=statisticsColumnNames(kind)
    val columnLabels=statisticsColumnLabels(data,kind)
    val categorySelection=test in listOf("χ² test","Fisher exact")&&dataColumns.size>1
    val categoryFirst=firstColumn.takeIf {it in dataColumns} ?: dataColumns.first()
    val categorySecond=secondColumn.takeIf {it in dataColumns&&it!=categoryFirst} ?: dataColumns.firstOrNull {it!=categoryFirst}.orEmpty()
    val firstIndex=dataColumns.indexOf(categoryFirst)
    val secondIndex=dataColumns.indexOf(categorySecond)
    val columnOptions=when {
        kind=="list"->listOf("x")
        kind!="xy"->dataColumns
        test=="t test"->listOf("x","y","x-y","paired")
        test=="z test"->listOf("x","y","x-y")
        else->listOf("x","y")
    }
    val activeColumn=column.takeIf {it in columnOptions} ?: "x"
    fun values(index:Int)=rows.mapNotNull {it.getOrNull(index)?.trim()?.takeIf(String::isNotBlank)}
    val x=values(0)
    val y=if(kind!="list")values(1) else null
    val z=if(kind=="xyz")values(2) else null
    val groupedMode=kind=="xy"&&grouping=="x=group, y=value"&&test!="Wilcoxon"&&!categorySelection
    val groupedValues=if(groupedMode)statisticsGroupedValues(rows) else emptyList()
    val categoricalPairs=if(categorySelection)statisticsCategoryPairs(rawRows,firstIndex,secondIndex) else emptyList()
    val numericCategories=categoricalPairs.all {it.first.toBigDecimalOrNull()!=null&&it.second.toBigDecimalOrNull()!=null}
    val xCategories=statisticsCategories(categoricalPairs.map {it.first},numericCategories)
    val yCategories=statisticsCategories(categoricalPairs.map {it.second},numericCategories)
    val groupNames=groupedValues.map {it.first}
    val activeFirst=firstGroup.takeIf {it in groupNames} ?: groupNames.firstOrNull().orEmpty()
    val activeSecond=secondGroup.takeIf {it in groupNames&&it!=activeFirst} ?: groupNames.firstOrNull {it!=activeFirst}.orEmpty()
    val rankColumns=dataColumns.takeIf {it.size>1} ?: listOf("x","y")
    val rankFirst=firstGroup.takeIf {it in rankColumns} ?: "x"
    val rankSecond=secondGroup.takeIf {it in rankColumns&&it!=rankFirst} ?: rankColumns.first {it!=rankFirst}
    val sample=if(groupedMode)groupedValues.firstOrNull {it.first==activeFirst}?.second else values(dataColumns.indexOf(activeColumn).coerceAtLeast(0))
    val pairCount=if(categorySelection)categoricalPairs.size else if(kind!="list")rows.count {it.getOrNull(0)?.isNotBlank()==true&&it.getOrNull(1)?.isNotBlank()==true} else 0
    val multiColumnTest=test in listOf("χ² test","Fisher exact","ANOVA","Tukey HSD","Mann–Whitney","Kruskal–Wallis")||test=="Wilcoxon"&&kind!="list"
    val groupComparison=test in listOf("ANOVA","Tukey HSD","Kruskal–Wallis")
    val groupedComparison=groupedMode&&groupComparison
    val groupedTwoSample=groupedMode&&test in listOf("t test","z test","Mann–Whitney")
    val twoSample=groupedTwoSample||!groupedMode&&kind=="xy"&&activeColumn=="x-y"&&test in listOf("t test","z test")
    val pairedTest=kind!="list"&&(test=="Wilcoxon"||kind=="xy"&&!groupedMode&&activeColumn=="paired"&&test=="t test")
    val rankSelection=test=="Mann–Whitney"&&!groupedMode&&kind!="list"
    val command=statisticsTestCommand(test,if(categorySelection)rawRows else rows,kind,activeColumn,tail,mu0,sigma,level,sigmaY,if(groupedMode)"group-value" else "columns",if(categorySelection)categoryFirst else if(rankSelection)rankFirst else activeFirst,if(categorySelection)categorySecond else if(rankSelection)rankSecond else activeSecond,yatesCorrection)
    val categoryLabels=if(categorySelection)statisticsCategoryLabels(categoricalPairs,columnLabels[firstIndex],columnLabels[secondIndex]) else emptyMap()
    HorizontalDivider()
    StatisticsSectionToggle("Analyze current data",expanded,"statistics-analysis-toggle") {expanded=!expanded}
    if(!expanded)return
    Text(if(kind.startsWith("columns:"))tr("Blank cells are omitted. Group comparisons use all columns.") else when(kind){"xy"->"Blank cells are omitted. Paired, χ², and Fisher tests use rows with both values; independent tests use each column separately. Fisher requires exactly two categories per column.";"xyz"->"Blank cells are omitted. ANOVA and Tukey HSD use x, y, and z as three independent groups.";else->"Blank cells are omitted from tests. Choose x,y or x,y,z data for group comparisons."},fontSize=12.sp,color=c.muted)
    Column(verticalArrangement=Arrangement.spacedBy(2.dp)) {
        Choices(listOf("t test","z test","χ² test","Fisher exact","ANOVA","Tukey HSD","Wilcoxon","Mann–Whitney","Kruskal–Wallis","Shapiro–Wilk","t interval","z interval"),test,{test=it})
        if(kind=="xy"&&test!="Wilcoxon"&&!categorySelection)Choices(listOf("Columns","x=group, y=value"),grouping,{grouping=it})
        if(categorySelection) {
            StatisticsSelectionTitle("First column")
            Choices(columnLabels,columnLabels[firstIndex],{firstColumn=dataColumns[columnLabels.indexOf(it)]},translate=false)
            StatisticsSelectionTitle("Second column")
            Choices(columnLabels.filterIndexed {index,_->index!=firstIndex},columnLabels[secondIndex],{secondColumn=dataColumns[columnLabels.indexOf(it)]},translate=false)
        } else if(rankSelection) {
            StatisticsSelectionTitle("First group")
            Choices(rankColumns,rankFirst,{firstGroup=it},translate=false)
            StatisticsSelectionTitle("Second group")
            Choices(rankColumns.filter {it!=rankFirst},rankSecond,{secondGroup=it},translate=false)
        } else if(groupedMode&&groupedTwoSample) {
            StatisticsSelectionTitle("First group")
            Choices(groupNames,activeFirst,{firstGroup=it},translate=false)
            StatisticsSelectionTitle("Second group")
            Choices(groupNames.filter {it!=activeFirst},activeSecond,{secondGroup=it},translate=false)
        } else if(groupedMode&&!multiColumnTest) {
            StatisticsSelectionTitle("Group")
            Choices(groupNames,activeFirst,{firstGroup=it},translate=false)
        } else if(!groupedMode&&kind!="list"&&!multiColumnTest)Choices(columnOptions,activeColumn,{column=it})
        if(categorySelection)Text(tr("Only complete rows in the two selected columns are analyzed."),fontSize=11.sp,color=c.muted)
        else if(groupedMode)Text("For t/z tests, compare two groups of y values. χ² and Fisher use each row's x/y categories; ANOVA and Tukey use all groups.",fontSize=11.sp,color=c.muted)
        else if(test=="Wilcoxon"&&pairedTest)Text(tr("Wilcoxon uses x−y for complete pairs."),fontSize=11.sp,color=c.muted)
        else if(twoSample||pairedTest)Text(if(pairedTest)"Paired t test uses x−y for rows with both values." else "Independent samples compare the means of x and y.",fontSize=11.sp,color=c.muted)
    }
    if(!multiColumnTest&&test !in listOf("Shapiro–Wilk","Wilcoxon")) {
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                if(test=="t test"||test=="z test") {
                    Field(mu0,if(twoSample||pairedTest)"Hypothesized difference Δ₀" else "Hypothesized mean μ₀",Modifier.weight(1f)){mu0=it}
                } else {
                    Field(level,"Confidence level (0–1 or %)",Modifier.weight(1f)){level=it}
                }
                if(test=="z test"||test=="z interval")Field(sigma,if(twoSample)"Known σx" else "Known σ",Modifier.weight(1f)){sigma=it}
            }
            if(test=="z test"&&twoSample)Field(sigmaY,"Known σy",Modifier.fillMaxWidth()){sigmaY=it}
            if(test=="t test"||test=="z test") {
                Column(verticalArrangement=Arrangement.spacedBy(2.dp)) {
                    StatisticsSelectionTitle("Alternative hypothesis")
                    Choices(listOf("Two-sided","Left","Right"),tail,{tail=it})
                }
            }
    }
    if(test=="χ² test") {
        Row(verticalAlignment=Alignment.CenterVertically) {
            Checkbox(checked=yatesCorrection,onCheckedChange={yatesCorrection=it},modifier=Modifier.testTag("statistics-yates"))
            Text(tr("Yates continuity correction"),fontSize=12.sp)
        }
        Text(tr("Applies only to 2×2 tables. Turn off for Pearson χ²."),fontSize=11.sp,color=c.muted)
    }
    if(test in listOf("Fisher exact","Wilcoxon","Mann–Whitney")) {
        Column(verticalArrangement=Arrangement.spacedBy(2.dp)) {
            StatisticsSelectionTitle(if(test=="Fisher exact")"Alternative odds ratio (ordered categories)" else "Alternative hypothesis")
            Choices(listOf("Two-sided","Left","Right"),tail,{tail=it})
        }
    }
    val dataStatus=when {
        dataColumns.indices.all {values(it).isEmpty()}->"Add values to the table to run this analysis."
        multiColumnTest&&kind=="list"->"Switch to x,y or x,y,z data and enter each group."
        test in listOf("χ² test","Fisher exact")&&dataColumns.size<2->"Switch to x,y data and enter both columns."
        groupedComparison&&(groupedValues.size<2||groupedValues.any {it.second.size<2})->"Enter at least two groups with two y values in each group."
        groupedTwoSample&&(activeSecond.isBlank()||(test=="t test"&&((sample?.size ?: 0)<2||(groupedValues.firstOrNull {it.first==activeSecond}?.second?.size ?: 0)<2)))->"Choose two groups with enough y values."
        categorySelection&&pairCount<2->tr("Enter at least two complete rows in the selected columns.")
        pairedTest&&pairCount<2->"Enter at least two complete x,y rows."
        categorySelection&&test=="χ² test"&&(xCategories.size<2||yCategories.size<2)->tr("Each selected column needs at least two categories.")
        categorySelection&&test=="Fisher exact"&&(xCategories.size!=2||yCategories.size!=2)->tr("Fisher exact needs exactly two categories in each selected column.")
        test in listOf("ANOVA","Tukey HSD","Kruskal–Wallis")&&kind=="xyz"&&(x.size<2||y==null||y.size<2||z==null||z.size<2)->"Enter at least two values in each x, y, and z column."
        (!groupedMode&&groupComparison||!groupedMode&&twoSample&&test=="t test")&&(x.size<2||y==null||y.size<2)->"Enter at least two values in each column."
        !groupedMode&&twoSample&&(x.isEmpty()||y.isNullOrEmpty())->"Enter values in both columns."
        test=="Shapiro–Wilk"&&(sample?.size ?: 0)<3->"Enter at least three values in the selected column."
        !multiColumnTest&&sample?.isEmpty()==true->"Enter values in the selected column."
        else->"Check the required values and sample size."
    }
    if(command==null)Text(dataStatus,fontSize=12.sp,color=c.muted)
    else Text("${if(rankSelection)"$rankFirst (${values(rankColumns.indexOf(rankFirst)).size}), $rankSecond (${values(rankColumns.indexOf(rankSecond)).size})" else if(groupedComparison)"${groupedValues.size} groups" else if(groupedTwoSample)"$activeFirst (${sample?.size ?: 0}), $activeSecond (${groupedValues.firstOrNull {it.first==activeSecond}?.second?.size ?: 0})" else if(pairedTest||test=="χ² test"||test=="Fisher exact")"$pairCount pairs" else if(groupComparison) dataColumns.mapIndexed {index,name->"${values(index).size} $name"}.joinToString(", ") else if(multiColumnTest||twoSample)"${x.size} x, ${y?.size ?: 0} y" else "${sample?.size ?: 0} values"} ready",fontSize=12.sp,color=c.muted)
    if(groupedComparison&&test=="Tukey HSD")Text(groupedValues.mapIndexed {index,(name,_)->"${listOf("x","y","z").getOrNull(index) ?: "group ${index+1}"} = $name"}.joinToString(" · "),fontSize=11.sp,color=c.muted)
    if(categorySelection)Text("${columnLabels[firstIndex]}: ${xCategories.joinToString(", ")} · ${columnLabels[secondIndex]}: ${yCategories.joinToString(", ")}",fontSize=11.sp,color=c.muted)
    Row(Modifier.horizontalScroll(rememberScrollState()),verticalAlignment=Alignment.CenterVertically) {
        Button(onClick={command?.let {m.edit(Editor(it));m.calculate(statisticsTermLabels=categoryLabels)}},enabled=command!=null,modifier=Modifier.testTag("statistics-run-test")){Text(if(test.endsWith("interval"))"Compute interval" else "Run test")}
        SmallAction("Insert expression"){command?.let {m.edit(Editor(it));m.mode="Scientific/CAS"}}
    }
    statisticsReportFor(m.result,m.resultSource.ifBlank {m.editor.source},statisticsTestAnalyses(test))?.let {StatisticsResultReport(m,it)}
    Text(if(isKorean())"검정의 기본 대립가설은 양측입니다. 신뢰수준은 0.95 또는 95로 입력할 수 있습니다." else "Tests use a two-sided alternative by default. Confidence levels accept 0.95 or 95.",fontSize=11.sp,color=c.muted)
}
