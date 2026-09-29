package com.kirinonakar.calcmax.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.kirinonakar.calcmax.calculator.CalculatorModel
import com.kirinonakar.calcmax.math.Editor
import com.kirinonakar.calcmax.ui.theme.LocalInstrument

@Composable internal fun StatisticsAnalysis(m: CalculatorModel,rows:List<List<String>>,kind:String) {
    val c=LocalInstrument.current
    var test by rememberSaveable {mutableStateOf("t test")}
    var column by rememberSaveable {mutableStateOf("x")}
    var tail by rememberSaveable {mutableStateOf("Two-sided")}
    var mu0 by rememberSaveable {mutableStateOf("0")}
    var sigma by rememberSaveable {mutableStateOf("2")}
    var sigmaY by rememberSaveable {mutableStateOf("2")}
    var level by rememberSaveable {mutableStateOf("95")}
    val columnOptions=when {
        kind!="xy"->listOf("x")
        test=="t test"->listOf("x","y","x-y","paired")
        test=="z test"->listOf("x","y","x-y")
        else->listOf("x","y")
    }
    val activeColumn=column.takeIf {it in columnOptions} ?: "x"
    fun values(index:Int)=rows.mapNotNull {it.getOrNull(index)?.trim()?.takeIf(String::isNotBlank)}
    val x=values(0)
    val y=if(kind=="xy")values(1) else null
    val sample=if(kind=="xy"&&activeColumn=="y")y else x
    val pairCount=if(kind=="xy")rows.count {it.getOrNull(0)?.isNotBlank()==true&&it.getOrNull(1)?.isNotBlank()==true} else 0
    val twoColumnTest=test=="χ² test"||test=="Fisher exact"||test=="ANOVA"
    val twoSample=kind=="xy"&&activeColumn=="x-y"&&(test=="t test"||test=="z test")
    val pairedTest=kind=="xy"&&activeColumn=="paired"&&test=="t test"
    val command=statisticsTestCommand(test,rows,kind,activeColumn,tail,mu0,sigma,level,sigmaY)
    HorizontalDivider()
    Text(tr("Analyze current data"),style=MaterialTheme.typography.titleMedium)
    Text(if(kind=="xy")"Blank cells are omitted. Paired, χ², and Fisher tests use rows with both values; independent tests use each column separately. Fisher requires exactly two categories per column." else "Blank cells are omitted from tests. Choose x,y data for two-column tests.",fontSize=12.sp,color=c.muted)
    Column(verticalArrangement=Arrangement.spacedBy(2.dp)) {
        Choices(listOf("t test","z test","χ² test","Fisher exact","ANOVA","Shapiro–Wilk","t interval","z interval"),test,{test=it})
        if(kind=="xy"&&!twoColumnTest)Choices(columnOptions,activeColumn,{column=it})
        if(twoSample||pairedTest)Text(if(pairedTest)"Paired t test uses x−y for rows with both values." else "Independent samples compare the means of x and y.",fontSize=11.sp,color=c.muted)
    }
    if(!twoColumnTest&&test!="Shapiro–Wilk") {
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
    if(test=="Fisher exact") {
        Column(verticalArrangement=Arrangement.spacedBy(2.dp)) {
            Text(tr("Alternative odds ratio (ordered categories)"),fontSize=12.sp,fontWeight=FontWeight.SemiBold)
            Choices(listOf("Two-sided","Left","Right"),tail,{tail=it})
        }
    }
    val dataStatus=when {
        x.isEmpty()&&(y==null||y.isEmpty())->"Add values to the table to run this analysis."
        twoColumnTest&&kind!="xy"->"Switch to x,y data and enter both columns."
        (pairedTest||test=="χ² test"||test=="Fisher exact")&&pairCount<2->"Enter at least two complete x,y rows."
        (test=="ANOVA"||twoSample&&test=="t test")&&(x.size<2||y==null||y.size<2)->"Enter at least two values in each column."
        twoSample&&(x.isEmpty()||y.isNullOrEmpty())->"Enter values in both columns."
        test=="Shapiro–Wilk"&&(sample?.size ?: 0)<3->"Enter at least three values in the selected column."
        !twoColumnTest&&sample?.isEmpty()==true->"Enter values in the selected column."
        else->"Check the required values and sample size."
    }
    if(command==null)Text(dataStatus,fontSize=12.sp,color=c.muted)
    else Text("${if(pairedTest||test=="χ² test"||test=="Fisher exact")"$pairCount pairs" else if(twoColumnTest||twoSample)"${x.size} x, ${y?.size ?: 0} y" else "${sample?.size ?: 0} values"} ready",fontSize=12.sp,color=c.muted)
    Row(Modifier.horizontalScroll(rememberScrollState()),verticalAlignment=Alignment.CenterVertically) {
        Button(onClick={command?.let {m.edit(Editor(it));m.calculate()}},enabled=command!=null,modifier=Modifier.testTag("statistics-run-test")){Text(if(test.endsWith("interval"))"Compute interval" else "Run test")}
        SmallAction("Insert expression"){command?.let {m.edit(Editor(it));m.mode="Scientific/CAS"}}
    }
    Text(if(isKorean())"검정의 기본 대립가설은 양측입니다. 신뢰수준은 0.95 또는 95로 입력할 수 있습니다." else "Tests use a two-sided alternative by default. Confidence levels accept 0.95 or 95.",fontSize=11.sp,color=c.muted)
}
