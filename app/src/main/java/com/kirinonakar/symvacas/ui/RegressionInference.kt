package com.kirinonakar.symvacas.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kirinonakar.symvacas.ui.theme.LocalInstrument
import com.kirinonakar.symvacas.calculator.ResultDisplayMode
import org.json.JSONObject
import kotlin.math.abs

@Composable internal fun RegressionInference(report:JSONObject,digits:Int) {
    val colors=LocalInstrument.current
    val clipboard=LocalClipboardManager.current
    var expanded by remember(report) {mutableStateOf(false)}
    fun value(objectValue:JSONObject,key:String):String {
        val raw=objectValue.optString(key).takeUnless {objectValue.isNull(key)||it.isBlank()} ?: return "—"
        return ResultDisplayFormat.formatText(raw,ResultDisplayMode.OFF,false,maxFractionDigits=digits)
    }
    val metrics=listOf("R²" to "rSquared","Adjusted R²" to "adjustedRSquared","RMSE" to "rmse","Residual SE" to "residualSE",
        "C-statistic (AUC)" to "auc","McFadden R²" to "pseudoRSquared","Deviance" to "deviance","AIC" to "aic","LR p" to "likelihoodP",
        "Durbin–Watson" to "durbinWatson","Residual Shapiro p" to "shapiroP")
    Text("n=${report.optInt("n")} · df=${report.optInt("df")} · "+metrics.filter {report.has(it.second)&&(!report.isNull(it.second)||it.second in listOf("rSquared","adjustedRSquared"))}.map {"${tr(it.first)}=${value(report,it.second)}"}.joinToString(" · "),fontSize=11.sp)
    Text(tr(when {
        report.optString("fitScale")=="binomial"->"Binomial MLE; Wald intervals."
        report.optString("fitScale")=="log(y)"->"Inference in log(y); R² and RMSE in original y units."
        report.optBoolean("approximate")->"Local Jacobian approximation; independent errors with constant variance."
        else->"OLS inference; independent errors with constant variance."
    }),fontSize=11.sp,color=colors.muted)
    report.optJSONArray("warnings")?.let {warnings->repeat(warnings.length()){Text(tr(warnings.optString(it)),fontSize=11.sp,color=colors.muted)}}
    val coefficients=report.optJSONArray("coefficients")?.let {array->
        (0 until array.length()).mapNotNull {array.optJSONObject(it)}
    }.orEmpty()
    val headers=listOf("Parameter","Estimate","SE","95% CI","p").map {tr(it)}
    val coefficientRows=coefficients.map {coefficient->
        listOf(coefficient.optString("name"),value(coefficient,"estimate"),value(coefficient,"se"),
            "${value(coefficient,"low")} … ${value(coefficient,"high")}",value(coefficient,"p"))
    }
    Column(Modifier.horizontalScroll(rememberScrollState())) {
        Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            headers.forEachIndexed {column,header->
                // Each column takes the widest header/value, keeping every row aligned.
                Column(Modifier.width(IntrinsicSize.Max)) {
                    Text(header,fontSize=11.sp,maxLines=1,softWrap=false)
                    coefficientRows.forEach {row->
                        Text(row[column],fontSize=11.sp,fontFamily=FontFamily.Monospace,maxLines=1,softWrap=false)
                    }
                }
            }
        }
        coefficients.forEach {coefficient->
            if(coefficient.has("oddsRatio"))Text("${coefficient.optString("name")} · ${tr("Odds ratio")}: ${value(coefficient,"oddsRatio")} · ${tr("OR 95% CI")}: ${value(coefficient,"oddsLow")} … ${value(coefficient,"oddsHigh")}",fontSize=11.sp)
        }
    }
    RegressionRoc(report,digits)
    TextButton(onClick={expanded=!expanded}) {Text(tr("Residual diagnostics"))}
    if(expanded) {
        val binomial=report.optString("fitScale")=="binomial"
        Text(tr(if(binomial)"Deviance residual vs fitted probability." else "Residual vs fitted; Durbin–Watson uses input row order."),fontSize=11.sp,color=colors.muted)
        val rows=report.optJSONArray("residuals")
        val residuals=if(rows==null)emptyList() else (0 until rows.length()).mapNotNull {rows.optJSONObject(it)}
        val points=residuals.mapNotNull {row->
            val x=row.optDouble("fitted",Double.NaN);val y=row.optDouble(if(binomial)"deviance" else "residual",Double.NaN)
            if(x.isFinite()&&y.isFinite())Triple(x,y,abs(row.optDouble("standardized"))>3) else null
        }
        if(points.isNotEmpty()) {
            val minimum=points.minOf {it.first};val maximum=points.maxOf {it.first}
            val spread=points.maxOf {abs(it.second)}.takeIf {it>0} ?: 1.0
            Canvas(Modifier.fillMaxWidth().height(150.dp)) {
                val half=size.height/2
                drawLine(colors.muted,Offset(8f,half),Offset(size.width-8f,half))
                points.forEach {(x,y,outlier)->drawCircle(if(outlier)Color(0xffda5545) else colors.accent,2.5f,
                    Offset((8+(size.width-16)*(x-minimum)/((maximum-minimum).takeIf {it>0} ?: 1.0)).toFloat(),(half-0.85*half*y/spread).toFloat()))}
            }
            Text(tr("Fitted value"),fontSize=11.sp,color=colors.muted)
        }
        Column(Modifier.horizontalScroll(rememberScrollState())) {
            Text((if(binomial)listOf("Observation","Observed","Fitted value","Residual","Pearson residual","Deviance residual") else listOf("Observation","Observed","Fitted value","Residual","Standardized","Leverage","Cook's D")).map {tr(it)}.joinToString(" | "),fontSize=11.sp)
            residuals.take(100).forEach {row->Text((listOf("row","observed","fitted","residual","standardized")+if(binomial)listOf("deviance") else listOf("leverage","cook")).joinToString(" | "){value(row,it)},fontSize=11.sp,fontFamily=FontFamily.Monospace)}
        }
        if(residuals.size>100)Text(tr("Showing first 100 rows; copy includes all rows."),fontSize=11.sp,color=colors.muted)
        TextButton(onClick={
            val keys=listOf("row","observed","fitted","residual","standardized","leverage","cook","deviance")
            clipboard.setText(AnnotatedString(keys.joinToString(",")+"\n"+residuals.joinToString("\n") {row->keys.joinToString(","){key->if(row.isNull(key))"" else row.optString(key)}}))
        }) {Text(tr("Copy residual CSV"))}
    }
}
