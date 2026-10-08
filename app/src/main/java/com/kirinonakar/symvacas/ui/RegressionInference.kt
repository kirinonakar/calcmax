package com.kirinonakar.symvacas.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kirinonakar.symvacas.ui.theme.LocalInstrument
import com.kirinonakar.symvacas.calculator.ResultDisplayMode
import org.json.JSONObject
import kotlin.math.abs

@Composable internal fun RegressionInference(report:JSONObject,digits:Int,parameterLabels:Map<String,String> = emptyMap(),onCopy:(()->Unit)?=null) {
    val colors=LocalInstrument.current
    val clipboard=LocalClipboardManager.current
    var expanded by remember(report) {mutableStateOf(false)}
    if(onCopy!=null)Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End) {
        SmallAction("Copy result",fontSize=12.sp,modifier=Modifier.testTag("statistics-regression-copy")){onCopy()}
    }
    fun value(objectValue:JSONObject,key:String):String {
        val raw=objectValue.optString(key).takeUnless {objectValue.isNull(key)||it.isBlank()} ?: return "—"
        return ResultDisplayFormat.formatText(raw,ResultDisplayMode.OFF,false,maxFractionDigits=digits)
    }
    val bayesian=report.optBoolean("bayesian")
    val machineLearning=report.optString("model").isNotBlank()&&!bayesian
    val credibleLabel="${report.optDouble("credibleLevel")*100}% ${tr("Credible interval")}"
    val forest=report.optString("model")=="randomforest"
    val metrics=listOf("R²" to "rSquared","Adjusted R²" to "adjustedRSquared","RMSE" to "rmse","Log loss" to "logLoss","Accuracy" to "accuracy","Sensitivity" to "sensitivity","Specificity" to "specificity","OOB C-statistic (AUC)" to "oobAuc","OOB sensitivity" to "oobSensitivity","OOB specificity" to "oobSpecificity","OOB accuracy" to "oobAccuracy","OOB R²" to "oobRSquared","OOB RMSE" to "oobRMSE","Residual SE" to "residualSE",
        "C-statistic (AUC)" to "auc","McFadden R²" to "pseudoRSquared","Deviance" to "deviance","AIC" to "aic","LR p" to "likelihoodP",
        "Durbin–Watson" to "durbinWatson","Residual Shapiro p" to "shapiroP")
    Text("n=${report.optInt("n")} · "+(if(report.isNull("df"))"" else "df=${report.optInt("df")} · ")+metrics.filter {report.has(it.second)&&(!report.isNull(it.second)||it.second in listOf("rSquared","adjustedRSquared"))}.map {"${tr(it.first)}=${value(report,it.second)}"}.joinToString(" · "),fontSize=11.sp)
    Text(tr(when {
        bayesian->when(report.optString("method")){"nuts"->"NUTS posterior samples; check R-hat, ESS and divergences.";"laplace"->"Gaussian Laplace posterior at the MAP; approximate credible intervals.";else->"Normal-inverse-gamma posterior; exact Student-t credible intervals."}
        report.optString("method")=="firth"->"Firth logistic regression; profile penalized-likelihood intervals."
        machineLearning->"Training fit; ordinary coefficient inference is unavailable."
        report.optString("fitScale")=="binomial"->"Binomial MLE; Wald intervals."
        report.optString("fitScale")=="log(y)"->"Inference in log(y); R² and RMSE in original y units."
        report.optBoolean("approximate")->"Local Jacobian approximation; independent errors with constant variance."
        else->"OLS inference; independent errors with constant variance."
    }),fontSize=11.sp,color=colors.muted)
    if(bayesian)Text("${tr("Prior SD")}=${value(report,"priorSD")} · ${tr("Credible level (0–1)")}=${value(report,"credibleLevel")}",fontSize=11.sp,color=colors.muted)
    if(machineLearning)Text(if(forest)"${tr("Random Forest")} · ${tr(if(report.optString("task")=="classification")"Binary classification" else "Regression")} · ${tr("Trees")}: ${report.optInt("trees")} · ${tr("Max depth")}: ${report.optInt("maxDepth")} · ${tr("Random seed")}: ${report.optInt("seed")} · OOB n=${report.optInt("oobN")}/${report.optInt("n")}" else "${tr(report.optString("model").removePrefix("logistic"))} · α=${value(report,"alpha")} · ${tr("Selected predictors")}: ${report.optInt("selectedPredictors")} · L1=${value(report,"l1Ratio")}",fontSize=11.sp,color=colors.muted)
    if(bayesian) {
        Text(tr(if(report.optString("method")=="laplace")"Training probabilities evaluated at the MAP." else "Training predictions evaluated at posterior mean coefficients."),fontSize=11.sp,color=colors.muted)
        if(report.has("varianceShape"))Text("${tr("Variance prior shape")}=${value(report,"varianceShape")} · ${tr("Variance prior scale")}=${value(report,"varianceScale")} · ${tr("Posterior variance mean")}=${value(report,"posteriorVarianceMean")}",fontSize=11.sp,color=colors.muted)
    }
    report.optJSONObject("nuts")?.let {h->
        Text("NUTS · ${tr("Samples per chain")}=${h.optInt("samples")} · ${tr("Warmup")}=${h.optInt("warmup")} · ${tr("Max tree depth")}=${h.optInt("maxTreeDepth")} · ${tr("Chains")}=${h.optInt("chains")} · ${tr("Random seed")}=${h.optInt("seed")} · ${tr("Mean acceptance probability")}=${value(h,"meanAcceptanceProbability")} · ${tr("Divergences")}=${h.optInt("divergences")} · ${tr("Max tree depth hits")}=${h.optInt("maxTreeDepthHits")} · ${tr("Mean tree depth")}=${value(h,"meanTreeDepth")} · ${tr("Mean leapfrog steps")}=${value(h,"meanLeapfrogSteps")}",fontSize=11.sp,color=colors.muted)
        h.optJSONArray("chainDiagnostics")?.let {array->repeat(array.length()){i->array.optJSONObject(i)?.let {c->
            Text("${tr("Chain")} ${c.optInt("chain")} · ${tr("Mean acceptance probability")}=${value(c,"meanAcceptanceProbability")} · ${tr("Step size")}=${value(c,"stepSize")} · ${tr("Divergences")}=${c.optInt("divergences")}",fontSize=11.sp,color=colors.muted)
        }}}
    }
    report.optJSONArray("warnings")?.let {warnings->repeat(warnings.length()){Text(tr(warnings.optString(it)),fontSize=11.sp,color=colors.muted)}}
    val interceptLabel=tr("Intercept")
    fun parameterName(coefficient:JSONObject):String=parameterLabels[coefficient.optString("name")]?.let {if(it=="Intercept")interceptLabel else it} ?: coefficient.optString("name")
    val coefficients=report.optJSONArray(if(forest)"featureImportance" else "coefficients")?.let {array->
        (0 until array.length()).mapNotNull {array.optJSONObject(it)}
    }.orEmpty()
    val hasVif=coefficients.any {it.has("vif")&&!it.isNull("vif")}
    val hasPenalizedOdds=machineLearning&&coefficients.any {it.has("oddsRatio")&&!it.isNull("oddsRatio")}
    val headers=((if(bayesian)listOf("Parameter","Posterior estimate","Posterior SD",credibleLabel,"P(β > 0)") else if(machineLearning)listOf("Parameter",if(forest)"Feature importance" else "Estimate") else listOf("Parameter","Estimate","SE","95% CI","p"))+(if(report.has("nuts"))listOf("Split R-hat","Autocorrelation ESS","MCSE") else emptyList())+(if(hasVif)listOf("VIF") else emptyList())+(if(hasPenalizedOdds)listOf("Odds ratio") else emptyList())).map {tr(it)}
    val coefficientRows=coefficients.map {coefficient->
        listOf(parameterName(coefficient),value(coefficient,"estimate"))+(if(bayesian)listOf(value(coefficient,"posteriorSD"),"${value(coefficient,"low")} … ${value(coefficient,"high")}",value(coefficient,"probabilityPositive")) else if(machineLearning)emptyList() else listOf(value(coefficient,"se"),
            "${value(coefficient,"low")} … ${value(coefficient,"high")}",value(coefficient,"p"))) + (if(report.has("nuts"))listOf(value(coefficient,"rHat"),value(coefficient,"ess"),value(coefficient,"mcse")) else emptyList()) + (if(hasVif)listOf(value(coefficient,"vif")) else emptyList()) + (if(hasPenalizedOdds)listOf(value(coefficient,"oddsRatio")) else emptyList())
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
            if(!machineLearning&&coefficient.has("oddsRatio"))Text("${parameterName(coefficient)} · ${tr("Odds ratio")}: ${value(coefficient,"oddsRatio")} · ${if(bayesian)tr("OR credible interval") else tr("OR 95% CI")}: ${value(coefficient,"oddsLow")} … ${value(coefficient,"oddsHigh")}",fontSize=11.sp)
        }
    }
    if(forest) {
        Text("${tr(if(report.optString("permutationMetric")=="auc")"Permutation importance (OOB ΔAUC)" else "Permutation importance (OOB ΔR²)")} · n=${report.optInt("permutationN")} · ${tr("Repeats")}: ${report.optInt("permutationRepeats")}",fontSize=11.sp,color=colors.muted)
        report.optJSONArray("permutationImportance")?.let {array->repeat(array.length()) {index->
            array.optJSONObject(index)?.let {c->Text("${parameterName(c)}: ${value(c,"estimate")}",fontSize=11.sp)}
        }}
    }
    RegressionRoc(report,digits)
    if(report.has("confusionMatrix")) {
        ClassificationMatrix(report,"Training classification")
        report.optJSONObject("oobClassification")?.let {oob->
            ClassificationMatrix(oob,"OOB classification")
            RegressionRoc(oob,digits,oob=true)
        }
    }
    TextButton(onClick={expanded=!expanded}) {Text(tr("Residual diagnostics"))}
    if(expanded) {
        val binomial=report.optString("fitScale")=="binomial"||report.optString("model").startsWith("logistic")
        Text(tr(if(binomial)"Deviance residual vs fitted probability." else if(machineLearning)"Residual vs fitted" else "Residual vs fitted; Durbin–Watson uses input row order."),fontSize=11.sp,color=colors.muted)
        if(binomial)Text(tr("Logistic leverage and Cook's D use a one-step GLM approximation."),fontSize=11.sp,color=colors.muted)
        val rows=report.optJSONArray("residuals")
        val residuals=if(rows==null)emptyList() else (0 until rows.length()).mapNotNull {rows.optJSONObject(it)}
        val points=residuals.mapNotNull {row->
            val x=row.optDouble("fitted",Double.NaN);val y=row.optDouble(if(binomial)"deviance" else "residual",Double.NaN)
            if(x.isFinite()&&y.isFinite())Triple(x,y,abs(row.optDouble("standardized"))>3) else null
        }
        if(points.isNotEmpty()) {
            val exports=remember {GraphExportState()}
            val minimum=points.minOf {it.first};val maximum=points.maxOf {it.first}
            val spread=points.maxOf {abs(it.second)}.takeIf {it>0} ?: 1.0
            ExportableGraphCanvas(Modifier.fillMaxWidth().height(150.dp),colors.display,exports) {
                val half=size.height/2
                drawLine(colors.muted,Offset(8f,half),Offset(size.width-8f,half))
                points.forEach {(x,y,outlier)->drawCircle(if(outlier)Color(0xffda5545) else colors.accent,2.5f,
                    Offset((8+(size.width-16)*(x-minimum)/((maximum-minimum).takeIf {it>0} ?: 1.0)).toFloat(),(half-0.85*half*y/spread).toFloat()))}
            }
            Text(tr("Fitted value"),fontSize=11.sp,color=colors.muted)
            PlotExportActions(exports,"symvacas-residuals")
        }
        val predictive=bayesian&&!binomial
        val residualHeaders=((if(binomial)listOf("Observation","Observed","Fitted value","Residual","Pearson residual","Deviance residual","Leverage","Cook's D") else if(bayesian||machineLearning)listOf("Observation","Observed","Fitted value","Residual") else listOf("Observation","Observed","Fitted value","Residual","Standardized","Leverage","Cook's D"))+(if(predictive)listOf("Predictive lower","Predictive upper") else emptyList())).map {tr(it)}
        val residualKeys=listOf("row","observed","fitted","residual")+(if(binomial)listOf("standardized","deviance","leverage","cook") else if(bayesian||machineLearning)emptyList() else listOf("standardized","leverage","cook"))+(if(predictive)listOf("predictiveLow","predictiveHigh") else emptyList())
        val residualRows=residuals.take(100)
        val residualGrid=colors.grid
        val residualHeaderStyle=LocalTextStyle.current.copy(fontSize=11.sp,fontWeight=FontWeight.SemiBold)
        val residualCellStyle=LocalTextStyle.current.copy(fontSize=11.sp,fontFamily=FontFamily.Monospace)
        val residualMeasurer=rememberTextMeasurer()
        val residualDensity=LocalDensity.current
        val residualColumnWidths=remember(report,digits) {
            residualKeys.indices.map {column->
                val headerWidth=residualMeasurer.measure(residualHeaders[column],residualHeaderStyle).size.width
                val cellWidth=residualRows.maxOfOrNull {row->residualMeasurer.measure(value(row,residualKeys[column]),residualCellStyle).size.width} ?: 0
                (maxOf(headerWidth,cellWidth)/residualDensity.density).dp+16.dp
            }
        }
        Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            Column(Modifier.border(1.dp,residualGrid)) {
                Row(Modifier.height(30.dp).background(colors.scientific)) {
                    residualHeaders.forEachIndexed {column,header->
                        Box(Modifier.width(residualColumnWidths[column]).fillMaxHeight().padding(horizontal=8.dp),contentAlignment=Alignment.CenterStart) {Text(header,fontSize=11.sp,fontWeight=FontWeight.SemiBold,maxLines=1,softWrap=false)}
                        if(column<residualHeaders.lastIndex)VerticalDivider(color=residualGrid,thickness=1.dp)
                    }
                }
                HorizontalDivider(color=residualGrid,thickness=1.dp)
                Column(Modifier.heightIn(max=320.dp).verticalScroll(rememberScrollState())) {
                    residualRows.forEachIndexed {index,row->
                        Row(Modifier.height(28.dp)) {
                            residualKeys.forEachIndexed {column,key->
                                Box(Modifier.width(residualColumnWidths[column]).fillMaxHeight().padding(horizontal=8.dp),contentAlignment=Alignment.CenterStart) {Text(value(row,key),fontSize=11.sp,fontFamily=FontFamily.Monospace,maxLines=1,softWrap=false)}
                                if(column<residualKeys.lastIndex)VerticalDivider(color=residualGrid,thickness=1.dp)
                            }
                        }
                        if(index<residualRows.lastIndex)HorizontalDivider(color=residualGrid,thickness=1.dp)
                    }
                }
            }
        }
        if(residuals.size>100)Text(tr("Showing first 100 rows; copy includes all rows."),fontSize=11.sp,color=colors.muted)
        TextButton(onClick={
            val keys=listOf("row","observed","fitted","residual","standardized","leverage","cook","deviance")+(if(predictive)listOf("predictiveLow","predictiveHigh") else emptyList())
            clipboard.setText(AnnotatedString(keys.joinToString(",")+"\n"+residuals.joinToString("\n") {row->keys.joinToString(","){key->if(row.isNull(key))"" else ResultDisplayFormat.formatText(row.optString(key),ResultDisplayMode.OFF,false,maxFractionDigits=digits)}}))
        }) {Text(tr("Copy residual CSV"))}
    }
}

@Composable private fun ClassificationMatrix(report:JSONObject,label:String) {
    val matrix=report.optJSONArray("confusionMatrix") ?: return
    Text("${tr(label)} · n=${report.optInt("n")} · ${tr("Decision threshold")}=${report.optDouble("threshold")} · ${tr("Positive class")}=1",fontSize=11.sp)
    val rows=listOf(listOf(tr("Confusion matrix"),tr("Predicted 0"),tr("Predicted 1")))+(0..1).map {i->
        listOf(tr(if(i==0)"Observed 0" else "Observed 1"),matrix.optJSONArray(i)?.optInt(0).toString(),matrix.optJSONArray(i)?.optInt(1).toString())
    }
    Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        (0..2).forEach {column->Column(Modifier.width(IntrinsicSize.Max)) {rows.forEach {row->Text(row[column],fontSize=11.sp,maxLines=1,softWrap=false)}}}
    }
}
