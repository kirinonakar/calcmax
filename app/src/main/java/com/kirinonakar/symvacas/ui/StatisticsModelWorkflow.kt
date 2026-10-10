package com.kirinonakar.symvacas.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Checkbox
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kirinonakar.symvacas.ui.theme.LocalInstrument
import org.json.JSONArray
import org.json.JSONObject

internal data class StatisticsModelWorkflowPlan(val target:String,val expression:String,val termLabels:Map<String,String>)
internal data class StatisticsCrossLoading(val indicator:Int,val factor:Int,val loading:Double)

internal fun statisticsDetectedCrossLoadings(workflow:JSONObject,factors:String,threshold:Double=workflow.optDouble("crossThreshold",.3)):List<StatisticsCrossLoading> {
    require(threshold.isFinite()&&threshold>0){"Cross-loading cutoff must be a positive finite number"}
    val matrix=workflow.optJSONArray("efaLoadings") ?: return emptyList()
    val ids=factors.split(',').map {it.trim().toIntOrNull()}
    require(ids.size==matrix.length()&&ids.all {it!=null&&it in 1..workflow.getInt("factorCount")}){"Factor ID count must match selected indicators"}
    return (0 until matrix.length()).flatMap {i->val row=matrix.getJSONArray(i);(0 until row.length()).mapNotNull {j->val value=row.getDouble(j);if(j+1!=ids[i]&&kotlin.math.abs(value)>=threshold)StatisticsCrossLoading(i+1,j+1,value) else null}}
}

internal fun statisticsModelWorkflowPlan(workflow:JSONObject,factors:String,paths:String="",cross:List<List<Int>>?=null):StatisticsModelWorkflowPlan {
    val target=workflow.getString("target");val data=workflow.getJSONArray("data");val count=workflow.getInt("factorCount")
    require(target in listOf("cfa","sem")&&data.length()>0&&count>0){"Invalid measurement model transfer"}
    val assignment=factors.trim().split(',').map {value->require(value.trim().matches(Regex("\\d+"))){"Specify one positive factor ID per selected indicator"};value.trim().toInt()}
    require(assignment.size==data.getJSONArray(0).length()){"Factor ID count must match selected indicators"}
    require(assignment.all {it in 1..count}&&(1..count).all {it in assignment}){"Keep all analyzed factor IDs consecutive from 1"}
    require((1..count).all {id->assignment.count {it==id}>=3}){"Each factor needs at least three indicators"}
    val existing=workflow.getJSONArray("cross")
    val crossPairs=if(target=="cfa"&&cross!=null)cross else if(target=="cfa"&&workflow.has("efaLoadings"))statisticsDetectedCrossLoadings(workflow,factors).map {listOf(it.indicator,it.factor)} else List(existing.length()){i->val pair=existing.getJSONArray(i);List(pair.length()){pair.getInt(it)}}
    require(crossPairs.all {it.size==2&&it[0] in 1..assignment.size&&it[1] in 1..count&&it[1]!=assignment[it[0]-1]}&&crossPairs.distinct().size==crossPairs.size){"Cross-loadings must be distinct additional indicator-factor pairs"}
    require((1..count).all {id->assignment.indices.any {i->assignment[i]==id&&crossPairs.none {it[0]==i+1}}}){"Keep at least one indicator without cross-loadings per factor"}
    val pairs=if(paths.isBlank())emptyList() else paths.split(';').map {pair->pair.split(',').map {value->require(value.trim().matches(Regex("\\d+"))){"Use latent paths like 1,2;2,3"};value.trim().toInt()}}
    if(target=="sem") {
        require(count>=2){"SEM structural paths require at least two latent factors"}
        require(pairs.isNotEmpty()){"Enter at least one latent structural path"}
        require(pairs.all {it.size==2&&it.all {id->id in 1..count}&&it[0]!=it[1]}&&pairs.distinct().size==pairs.size){"Use distinct paths between different analyzed factors"}
        val remaining=assignment.toMutableSet()
        while(remaining.isNotEmpty()) {
            val ready=remaining.filter {id->pairs.all {it[1]!=id||it[0] !in remaining}}
            require(ready.isNotEmpty()){"SEM requires acyclic directed paths"};remaining.removeAll(ready.toSet())
        }
    }
    fun token(value:String):String {require(value=="NA"||value.matches(Regex("[+-]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)(?:[eE][+-]?\\d+)?"))){"Invalid measurement model transfer"};return value}
    fun vector(values:List<String>)=values.joinToString(",","[","]")
    fun table(rows:List<List<String>>)=rows.joinToString(",","[","]",transform=::vector)
    val rows=List(data.length()){i->val row=data.getJSONArray(i);require(row.length()==assignment.size){"Invalid measurement model transfer"};List(row.length()){token(row.getString(it))}}
    val groups=workflow.getJSONArray("groups")
    val crossRows=crossPairs.map {pair->pair.map(Int::toString)}
    val groupIds=List(groups.length()){token(groups.getString(it))}
    val missing=workflow.getString("missing");val invariance=workflow.getString("invariance");val estimator=workflow.getString("estimator")
    require(missing in listOf("complete","fiml")&&invariance in listOf("configural","metric","scalar","strict")&&estimator in listOf("ml","wlsmv")){"Invalid measurement model transfer"}
    val expression="$target(${table(rows)},${vector(assignment.map(Int::toString))}${if(target=="sem")","+table(pairs.map {it.map(Int::toString)}) else ""},${table(crossRows)},$missing,${vector(groupIds)},$invariance,$estimator)"
    val labels=workflow.getJSONObject("termLabels")
    return StatisticsModelWorkflowPlan(target,expression,labels.keys().asSequence().associateWith {labels.getString(it)})
}

@Composable internal fun StatisticsModelWorkflow(workflow:JSONObject,enabled:Boolean,onRun:(StatisticsModelWorkflowPlan)->Unit) {
    val c=LocalInstrument.current;val target=workflow.getString("target");val initial=workflow.getJSONArray("factors")
    var factors by remember(workflow){mutableStateOf(List(initial.length()){initial.getInt(it).toString()}.joinToString(","))}
    var paths by remember(workflow){mutableStateOf("")}
    var cutoff by remember(workflow){mutableStateOf(workflow.optDouble("crossThreshold",.3).toString())}
    var automatic by remember(workflow){mutableStateOf(true)}
    var excluded by remember(workflow){mutableStateOf(setOf<Pair<Int,Int>>())}
    val detection=runCatching {statisticsDetectedCrossLoadings(workflow,factors,cutoff.toDoubleOrNull() ?: Double.NaN)}
    val efa=target=="cfa"&&workflow.has("efaLoadings")
    val plan=runCatching {
        val cross=if(efa){if(automatic)detection.getOrThrow().filter {(it.indicator to it.factor) !in excluded}.map {listOf(it.indicator,it.factor)} else emptyList()} else null
        statisticsModelWorkflowPlan(workflow,factors,paths,cross)
    }
    Column(Modifier.fillMaxWidth().testTag("statistics-model-workflow"),verticalArrangement=Arrangement.spacedBy(6.dp)) {
        Text(tr(if(target=="cfa")"CFA from EFA" else "SEM from CFA"),fontSize=14.sp,color=c.ink)
        Field(factors,"Factor IDs in analyzed column order",Modifier.fillMaxWidth(),enabled=target=="cfa"&&enabled){factors=it}
        if(target=="cfa")Text(tr("Indicators are assigned to the factor with the largest absolute rotated loading. Review or edit the assignments before CFA."),fontSize=11.sp,color=c.muted)
        val ids=factors.split(',').map {it.trim().toIntOrNull()};val labels=workflow.getJSONObject("termLabels")
        for(factor in 1..workflow.getInt("factorCount"))Text("${tr("Factor")} $factor: "+ids.mapIndexedNotNull {i,id->if(id==factor)labels.optString("feature:${i+1}","${tr("Feature")} ${i+1}") else null}.joinToString(", "),fontSize=11.sp,color=c.muted)
        if(efa) {
            Field(cutoff,"Minimum absolute secondary loading",Modifier.fillMaxWidth(),enabled=enabled&&automatic){cutoff=it}
            Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically){Checkbox(automatic,{automatic=it},enabled=enabled);Text(tr("Automatically include detected cross-loadings"),fontSize=12.sp,color=c.ink)}
            val detected=detection.getOrDefault(emptyList())
            if(detection.isSuccess&&detected.isEmpty())Text(tr("No cross-loadings meet the current cutoff."),fontSize=11.sp,color=c.muted)
            for(row in detected) {
                val key=row.indicator to row.factor
                Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically){Checkbox(automatic&&key !in excluded,{checked->excluded=if(checked)excluded-key else excluded+key},enabled=enabled&&automatic);Text("${labels.optString("feature:${row.indicator}")} → ${tr("Factor")} ${row.factor} · ${String.format(java.util.Locale.ROOT,"%.4g",row.loading)}",fontSize=11.sp,color=c.ink)}
            }
            Text(tr("Detection uses absolute rotated pattern loadings, not statistical significance. Uncheck candidates to exclude them. Each factor needs an indicator without cross-loadings."),fontSize=11.sp,color=c.muted)
        }else if(workflow.getJSONArray("cross").length()>0) {
            val pairs=workflow.getJSONArray("cross")
            Text(tr("Cross-loadings")+": "+List(pairs.length()){i->val pair=pairs.getJSONArray(i);"${labels.optString("feature:${pair.getInt(0)}")} → ${tr("Factor")} ${pair.getInt(1)}"}.joinToString("; "),fontSize=11.sp,color=c.muted)
        }
        if(target=="sem") {
            Field(paths,"Latent paths: source,target;…",Modifier.fillMaxWidth(),enabled=enabled){paths=it}
            Text(tr("CFA transfers the measurement model, data and estimation options. Specify structural paths before running SEM."),fontSize=11.sp,color=c.muted)
        }
        plan.exceptionOrNull()?.message?.let {Text(tr(it),fontSize=11.sp,color=c.muted)}
        TextButton(onClick={plan.getOrNull()?.let(onRun)},enabled=enabled&&plan.isSuccess,modifier=Modifier.testTag("statistics-workflow-$target-run")){Text(tr(if(target=="cfa")"Run CFA" else "Run SEM"))}
    }
}
