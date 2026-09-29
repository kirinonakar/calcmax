package com.kirinonakar.calcmax.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.kirinonakar.calcmax.calculator.CalculatorModel
import com.kirinonakar.calcmax.math.Editor
import com.kirinonakar.calcmax.ui.theme.LocalInstrument

@Composable internal fun DistributionSection(m: CalculatorModel) {
    val c=LocalInstrument.current
    var family by rememberSaveable {mutableStateOf("Normal")}
    var query by rememberSaveable {mutableStateOf("Cumulative P(X ≤ x)")}
    var mu by rememberSaveable {mutableStateOf("0")}
    var sigma by rememberSaveable {mutableStateOf("1")}
    var df by rememberSaveable {mutableStateOf("10")}
    var df2 by rememberSaveable {mutableStateOf("10")}
    var trials by rememberSaveable {mutableStateOf("10")}
    var success by rememberSaveable {mutableStateOf("0.5")}
    var poissonMean by rememberSaveable {mutableStateOf("2")}
    var x by rememberSaveable {mutableStateOf("1")}
    var low by rememberSaveable {mutableStateOf("-1.96")}
    var high by rememberSaveable {mutableStateOf("1.96")}
    var probability by rememberSaveable {mutableStateOf("0.975")}
    var k by rememberSaveable {mutableStateOf("3")}
    val queries=when(family) {
        "Normal","Student t" -> listOf("Density f(x)","Cumulative P(X ≤ x)","Interval P(a ≤ X ≤ b)","Quantile")
        "χ²","F" -> listOf("Density f(x)","Cumulative P(X ≤ x)","Interval P(a ≤ X ≤ b)")
        "Binomial" -> listOf("P(X = k)","P(X ≤ k)","List P(X = k)","List P(X ≤ k)")
        else -> listOf("P(X = k)","P(X ≤ k)")
    }
    val active=query.takeIf {it in queries} ?: queries[1]
    fun expression():String=when(family) {
        "Normal" -> when(active) {
            "Density f(x)" -> "normpdf($x,$mu,$sigma)"
            "Interval P(a ≤ X ≤ b)" -> "normcdf($low,$high,$mu,$sigma)"
            "Quantile" -> "invnorm($probability,$mu,$sigma)"
            else -> "normcdf(-oo,$x,$mu,$sigma)"
        }
        "Student t" -> when(active) {
            "Density f(x)" -> "tpdf($x,$df)"
            "Interval P(a ≤ X ≤ b)" -> "tcdf($low,$high,$df)"
            "Quantile" -> "invt($probability,$df)"
            else -> "tcdf($x,$df)"
        }
        "χ²" -> when(active) {
            "Density f(x)" -> "chi2pdf($x,$df)"
            "Interval P(a ≤ X ≤ b)" -> "chi2cdf($low,$high,$df)"
            else -> "chi2cdf($x,$df)"
        }
        "F" -> when(active) {
            "Density f(x)" -> "fpdf($x,$df,$df2)"
            "Interval P(a ≤ X ≤ b)" -> "fcdf($low,$high,$df,$df2)"
            else -> "fcdf($x,$df,$df2)"
        }
        "Binomial" -> when(active) {
            "P(X = k)" -> "binompdf($trials,$success,$k)"
            "P(X ≤ k)" -> "binomcdf($trials,$success,$k)"
            "List P(X = k)" -> "binompdf($trials,$success)"
            else -> "binomcdf($trials,$success)"
        }
        "Poisson" -> if(active=="P(X = k)")"poissonpdf($poissonMean,$k)" else "poissoncdf($poissonMean,$k)"
        else -> if(active=="P(X = k)")"geometpdf($success,$k)" else "geometcdf($success,$k)"
    }
    fun ready():Boolean {
        val used=when(family) {
            "Normal" -> when(active) {
                "Interval P(a ≤ X ≤ b)" -> listOf(low,high,mu,sigma)
                "Quantile" -> listOf(probability,mu,sigma)
                else -> listOf(x,mu,sigma)
            }
            "Student t" -> when(active) {
                "Interval P(a ≤ X ≤ b)" -> listOf(low,high,df)
                "Quantile" -> listOf(probability,df)
                else -> listOf(x,df)
            }
            "χ²" -> if(active=="Interval P(a ≤ X ≤ b)")listOf(low,high,df) else listOf(x,df)
            "F" -> if(active=="Interval P(a ≤ X ≤ b)")listOf(low,high,df,df2) else listOf(x,df,df2)
            "Binomial" -> if(active.startsWith("List"))listOf(trials,success) else listOf(trials,success,k)
            "Poisson" -> listOf(poissonMean,k)
            else -> listOf(success,k)
        }
        return used.all {it.isNotBlank()}
    }
    Text(tr("Distribution"),fontSize=12.sp,fontWeight=FontWeight.SemiBold)
    Choices(listOf("Normal","Student t","χ²","F","Binomial","Poisson","Geometric"),family,{family=it})
    Text(tr("Query"),fontSize=12.sp,fontWeight=FontWeight.SemiBold)
    Choices(queries,active,{query=it})
    Text(tr("Parameters"),fontSize=12.sp,fontWeight=FontWeight.SemiBold)
    when(family) {
        "Student t","χ²" -> Field(df,"Degrees of freedom",Modifier.fillMaxWidth()){df=it}
        "F" -> Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Field(df,"df₁",Modifier.weight(1f)){df=it}
            Field(df2,"df₂",Modifier.weight(1f)){df2=it}
        }
        "Binomial" -> Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Field(trials,"Trials n",Modifier.weight(1f)){trials=it}
            Field(success,"Success probability p",Modifier.weight(1f)){success=it}
        }
        "Poisson" -> Field(poissonMean,"Mean λ",Modifier.fillMaxWidth()){poissonMean=it}
        "Geometric" -> Field(success,"Success probability p",Modifier.fillMaxWidth()){success=it}
        else -> Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Field(mu,"Mean μ",Modifier.weight(1f)){mu=it}
            Field(sigma,"Standard deviation σ",Modifier.weight(1f)){sigma=it}
        }
    }
    Text(tr("Input"),fontSize=12.sp,fontWeight=FontWeight.SemiBold)
    when(active) {
        "Interval P(a ≤ X ≤ b)" -> Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Field(low,"a (lower bound)",Modifier.weight(1f)){low=it}
            Field(high,"b (upper bound)",Modifier.weight(1f)){high=it}
        }
        "Quantile" -> Field(probability,"Probability p (0–1)",Modifier.fillMaxWidth()){probability=it}
        "P(X = k)","P(X ≤ k)" -> Field(k,"k",Modifier.fillMaxWidth()){k=it}
        "List P(X = k)","List P(X ≤ k)" -> Text(if(isKorean())"단일 입력 없음: k=0부터 n까지의 값이 나열됩니다." else "No single input: every k from 0 to n is listed.",fontSize=11.sp,color=c.muted)
        else -> Field(x,"x",Modifier.fillMaxWidth()){x=it}
    }
    Text((if(isKorean())"수식  " else "Expression  ")+expression(),fontFamily=FontFamily.Monospace,fontSize=11.sp,color=c.muted)
    Row(Modifier.horizontalScroll(rememberScrollState()),verticalAlignment=Alignment.CenterVertically) {
        Button(onClick={m.edit(Editor(expression()));m.calculate()},enabled=ready()){Text(tr("Compute"))}
        SmallAction("Insert into calculator"){m.edit(Editor(expression()));m.mode="Scientific/CAS"}
    }
    Display(m,requestInitialFocus=false)
    Text(if(isKorean())"닫힌 형태가 있으면 정확값으로 표시합니다. 나머지 확률은 내부 정밀도를 사용합니다. 이항분포 목록은 n ≤ 100이어야 하며, 정규 누적확률에서 μ와 σ를 적용하려면 하한으로 -oo를 사용합니다." else "Closed forms stay exact where the engine has one; other probabilities use the internal precision. Binomial list forms need n ≤ 100, and the normal cumulative uses -oo as its lower bound so μ and σ apply.",fontSize=11.sp,color=c.muted)
}
