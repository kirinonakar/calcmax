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

@Composable internal fun StatisticsAnalysis(m: CalculatorModel,rows:List<List<String>>,kind:String) {
    val c=LocalInstrument.current
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
    var yatesCorrection by rememberSaveable {mutableStateOf(true)}
    val columnOptions=when {
        kind=="list"->listOf("x")
        kind=="xyz"->listOf("x","y","z")
        test=="t test"->listOf("x","y","x-y","paired")
        test=="z test"->listOf("x","y","x-y")
        else->listOf("x","y")
    }
    val activeColumn=column.takeIf {it in columnOptions} ?: "x"
    fun values(index:Int)=rows.mapNotNull {it.getOrNull(index)?.trim()?.takeIf(String::isNotBlank)}
    val x=values(0)
    val y=if(kind!="list")values(1) else null
    val z=if(kind=="xyz")values(2) else null
    val groupedMode=kind=="xy"&&grouping=="x=group, y=value"
    val groupedValues=if(groupedMode)statisticsGroupedValues(rows) else emptyList()
    val categoricalPairs=if(groupedMode)rows.mapNotNull {row->val left=row.getOrNull(0)?.trim()?.takeIf(String::isNotBlank);val right=row.getOrNull(1)?.trim()?.takeIf(String::isNotBlank);if(left!=null&&right!=null)left to right else null} else emptyList()
    val xCategories=categoricalPairs.map {it.first}.distinct()
    val yCategories=categoricalPairs.map {it.second}.distinct()
    val groupNames=groupedValues.map {it.first}
    val activeFirst=firstGroup.takeIf {it in groupNames} ?: groupNames.firstOrNull().orEmpty()
    val activeSecond=secondGroup.takeIf {it in groupNames&&it!=activeFirst} ?: groupNames.firstOrNull {it!=activeFirst}.orEmpty()
    val sample=if(groupedMode)groupedValues.firstOrNull {it.first==activeFirst}?.second else when(activeColumn){"y"->y;"z"->z;else->x}
    val pairCount=if(kind=="xy")rows.count {it.getOrNull(0)?.isNotBlank()==true&&it.getOrNull(1)?.isNotBlank()==true} else 0
    val multiColumnTest=test in listOf("χ² test","Fisher exact","ANOVA","Tukey HSD")
    val groupComparison=test in listOf("ANOVA","Tukey HSD")
    val groupedComparison=groupedMode&&groupComparison
    val groupedTwoSample=groupedMode&&test in listOf("t test","z test")
    val twoSample=groupedTwoSample||!groupedMode&&kind=="xy"&&activeColumn=="x-y"&&test in listOf("t test","z test")
    val pairedTest=!groupedMode&&kind=="xy"&&activeColumn=="paired"&&test=="t test"
    val command=statisticsTestCommand(test,rows,kind,activeColumn,tail,mu0,sigma,level,sigmaY,if(groupedMode)"group-value" else "columns",activeFirst,activeSecond,yatesCorrection)
    HorizontalDivider()
    Text(tr("Analyze current data"),style=MaterialTheme.typography.titleMedium)
    Text(when(kind){"xy"->"Blank cells are omitted. Paired, χ², and Fisher tests use rows with both values; independent tests use each column separately. Fisher requires exactly two categories per column.";"xyz"->"Blank cells are omitted. ANOVA and Tukey HSD use x, y, and z as three independent groups.";else->"Blank cells are omitted from tests. Choose x,y or x,y,z data for group comparisons."},fontSize=12.sp,color=c.muted)
    Column(verticalArrangement=Arrangement.spacedBy(2.dp)) {
        Choices(listOf("t test","z test","χ² test","Fisher exact","ANOVA","Tukey HSD","Shapiro–Wilk","t interval","z interval"),test,{test=it})
        if(kind=="xy")Choices(listOf("Columns","x=group, y=value"),grouping,{grouping=it})
        if(groupedMode&&groupedTwoSample) {
            Text(tr("First group"),fontSize=11.sp,color=c.muted)
            Choices(groupNames,activeFirst,{firstGroup=it},translate=false)
            Text(tr("Second group"),fontSize=11.sp,color=c.muted)
            Choices(groupNames.filter {it!=activeFirst},activeSecond,{secondGroup=it},translate=false)
        } else if(groupedMode&&!multiColumnTest) {
            Text(tr("Group"),fontSize=11.sp,color=c.muted)
            Choices(groupNames,activeFirst,{firstGroup=it},translate=false)
        } else if(!groupedMode&&kind!="list"&&!multiColumnTest)Choices(columnOptions,activeColumn,{column=it})
        if(groupedMode)Text("For t/z tests, compare two groups of y values. χ² and Fisher use each row's x/y categories; ANOVA and Tukey use all groups.",fontSize=11.sp,color=c.muted)
        else if(twoSample||pairedTest)Text(if(pairedTest)"Paired t test uses x−y for rows with both values." else "Independent samples compare the means of x and y.",fontSize=11.sp,color=c.muted)
    }
    if(!multiColumnTest&&test!="Shapiro–Wilk") {
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
                    Text(tr("Alternative hypothesis"),fontSize=12.sp,fontWeight=FontWeight.SemiBold)
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
    if(test=="Fisher exact") {
        Column(verticalArrangement=Arrangement.spacedBy(2.dp)) {
            Text(tr("Alternative odds ratio (ordered categories)"),fontSize=12.sp,fontWeight=FontWeight.SemiBold)
            Choices(listOf("Two-sided","Left","Right"),tail,{tail=it})
        }
    }
    val dataStatus=when {
        x.isEmpty()&&y.isNullOrEmpty()&&z.isNullOrEmpty()->"Add values to the table to run this analysis."
        multiColumnTest&&kind=="list"->"Switch to x,y or x,y,z data and enter each group."
        test in listOf("χ² test","Fisher exact")&&kind!="xy"->"Switch to x,y data and enter both columns."
        groupedComparison&&(groupedValues.size<2||groupedValues.any {it.second.size<2})->"Enter at least two groups with two y values in each group."
        groupedTwoSample&&(activeSecond.isBlank()||(test=="t test"&&((sample?.size ?: 0)<2||(groupedValues.firstOrNull {it.first==activeSecond}?.second?.size ?: 0)<2)))->"Choose two groups with enough y values."
        (pairedTest||test=="χ² test"||test=="Fisher exact")&&pairCount<2->"Enter at least two complete x,y rows."
        groupedMode&&test=="χ² test"&&(xCategories.size<2||yCategories.size<2)->"Enter at least two distinct x groups and y categories."
        groupedMode&&test=="Fisher exact"&&(xCategories.size!=2||yCategories.size!=2)->"Fisher exact needs exactly two x groups and two y categories."
        test in listOf("ANOVA","Tukey HSD")&&kind=="xyz"&&(x.size<2||y==null||y.size<2||z==null||z.size<2)->"Enter at least two values in each x, y, and z column."
        (!groupedMode&&groupComparison||!groupedMode&&twoSample&&test=="t test")&&(x.size<2||y==null||y.size<2)->"Enter at least two values in each column."
        !groupedMode&&twoSample&&(x.isEmpty()||y.isNullOrEmpty())->"Enter values in both columns."
        test=="Shapiro–Wilk"&&(sample?.size ?: 0)<3->"Enter at least three values in the selected column."
        !multiColumnTest&&sample?.isEmpty()==true->"Enter values in the selected column."
        else->"Check the required values and sample size."
    }
    if(command==null)Text(dataStatus,fontSize=12.sp,color=c.muted)
    else Text("${if(groupedComparison)"${groupedValues.size} groups" else if(groupedTwoSample)"$activeFirst (${sample?.size ?: 0}), $activeSecond (${groupedValues.firstOrNull {it.first==activeSecond}?.second?.size ?: 0})" else if(pairedTest||test=="χ² test"||test=="Fisher exact")"$pairCount pairs" else if(kind=="xyz"&&groupComparison)"${x.size} x, ${y?.size ?: 0} y, ${z?.size ?: 0} z" else if(multiColumnTest||twoSample)"${x.size} x, ${y?.size ?: 0} y" else "${sample?.size ?: 0} values"} ready",fontSize=12.sp,color=c.muted)
    if(groupedComparison&&test=="Tukey HSD")Text(groupedValues.mapIndexed {index,(name,_)->"${listOf("x","y","z").getOrNull(index) ?: "group ${index+1}"} = $name"}.joinToString(" · "),fontSize=11.sp,color=c.muted)
    if(groupedMode&&test in listOf("χ² test","Fisher exact"))Text("x: ${xCategories.mapIndexed {index,label->"${index+1}=$label"}.joinToString(", ")} · y: ${yCategories.mapIndexed {index,label->"${index+1}=$label"}.joinToString(", ")}",fontSize=11.sp,color=c.muted)
    Row(Modifier.horizontalScroll(rememberScrollState()),verticalAlignment=Alignment.CenterVertically) {
        Button(onClick={command?.let {m.edit(Editor(it));m.calculate()}},enabled=command!=null,modifier=Modifier.testTag("statistics-run-test")){Text(if(test.endsWith("interval"))"Compute interval" else "Run test")}
        SmallAction("Insert expression"){command?.let {m.edit(Editor(it));m.mode="Scientific/CAS"}}
    }
    Text(if(isKorean())"검정의 기본 대립가설은 양측입니다. 신뢰수준은 0.95 또는 95로 입력할 수 있습니다." else "Tests use a two-sided alternative by default. Confidence levels accept 0.95 or 95.",fontSize=11.sp,color=c.muted)
}
