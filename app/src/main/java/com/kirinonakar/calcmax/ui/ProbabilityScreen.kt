package com.kirinonakar.calcmax.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kirinonakar.calcmax.calculator.CalculatorModel
import com.kirinonakar.calcmax.ui.theme.LocalInstrument
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.math.RoundingMode

private fun JSONArray.objects()=List(length()){getJSONObject(it)}
private fun JSONArray.fields()=List(length()){getJSONArray(it)}
private fun JSONObject.probabilityLabel(ko:Boolean)=if(ko)optString("ko",optString("label")) else optString("label")
private fun probabilityNumber(text:String,digits:Int):String=runCatching {
    val value=BigDecimal(text)
    // Preserve scientific notation for very small probabilities.
    if(value.signum()!=0 && value.abs()<BigDecimal.ONE.scaleByPowerOfTen(-digits))value.round(java.math.MathContext(digits.coerceAtLeast(4))).stripTrailingZeros().toString()
    else value.setScale(digits,RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
}.getOrDefault(text)

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun ProbabilityScreen(m:CalculatorModel) {
    val context=LocalContext.current
    val focus=LocalFocusManager.current
    val keyboard=LocalSoftwareKeyboardController.current
    val schema=remember {context.assets.open("probability.json").bufferedReader().use {JSONObject(it.readText())}}
    val ko=isKorean()
    val c=LocalInstrument.current
    val categories=schema.getJSONArray("categories").objects()
    val distributions=schema.getJSONArray("distributions").objects()
    val draft=m.probabilityDraft
    val category=draft.optString("category","basic").takeIf {id->categories.any {it.getString("id")==id}} ?: "basic"
    val distribution=distributions.firstOrNull {it.getString("id")==draft.optString("distribution","binomial")} ?: distributions.first()
    val definition=if(category=="distribution")distribution else schema.getJSONArray("tools").objects().first {it.getString("id")==category}
    val discrete=distribution.optBoolean("discrete")
    val operations=if(category=="distribution")schema.getJSONArray("operations").objects().filter {(!it.optBoolean("discrete")||discrete)&&(!it.optBoolean("continuous")||!discrete)} else definition.getJSONArray("operations").objects()
    val operation=operations.firstOrNull {it.getString("id")==draft.optString("operation")} ?: operations.first()
    val eventMode=category=="events"&&operation.getString("id")!="conditionalCounts"
    val eventChoices=if(eventMode)definition.getJSONArray("fields").fields().filter {it.getString(0)!=operation.getString("id")} else emptyList()
    val eventInputDraft=draft.optJSONObject("eventInputs")?.optJSONArray(operation.getString("id"))
    val eventInputs=if(eventMode) {
        val saved=eventInputDraft?.let {array->List(array.length()){array.optString(it)}.distinct().filter {key->eventChoices.any {it.getString(0)==key}}}.orEmpty()
        saved.ifEmpty {val defaults=operation.getJSONArray("inputs");List(defaults.length()){defaults.getString(it)}}
    } else emptyList()
    val fields=(if(eventMode) {
        val keys=if(draft.optBoolean("independent"))listOf("pa","pb") else eventInputs
        keys.map {key->eventChoices.first {it.getString(0)==key}}
    } else if(category=="distribution")definition.getJSONArray("fields").fields()+operation.getJSONArray("fields").fields() else (operation.optJSONArray("fields") ?: definition.getJSONArray("fields")).fields())
        .filter {it.getString(0)!="k"||operation.getString("id") in listOf("exactly","atLeast","atMost")}
    val prefix=if(category=="distribution")distribution.getString("id") else category
    val savedValues=draft.optJSONObject("values") ?: JSONObject()
    fun input(field:JSONArray)=savedValues.optString("$prefix-${field.getString(0)}",field.getString(3))
    fun update(change:(JSONObject)->Unit){m.updateProbabilityDraft(JSONObject(draft.toString()).apply {remove("example");change(this)})}
    fun changeEventInputs(keys:List<String>)=update {next->
        next.put("eventInputs",JSONObject(draft.optJSONObject("eventInputs")?.toString() ?: "{}").put(operation.getString("id"),JSONArray(keys)))
    }
    val label:(JSONObject)->String={it.probabilityLabel(ko)}
    Panel("Probability","") {
        // Group presets so panel spacing and chip touch-target padding do not separate every row.
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
            Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(2.dp)) {
                schema.getJSONArray("examples").objects().chunked(2).forEach {row->
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                        row.forEach {example->
                            val id=example.getString("id")
                            FilterChip(selected=draft.optString("example")==id,onClick={
                                val next=JSONObject(draft.toString())
                                next.put("category",example.getString("category")).put("operation",example.getString("operation")).put("example",id).put("independent",false)
                                if(example.has("distribution"))next.put("distribution",example.getString("distribution"))
                                val values=JSONObject(savedValues.toString())
                                val examplePrefix=example.optString("distribution",example.getString("category"))
                                val preset=example.getJSONObject("values")
                                preset.keys().forEach {key->values.put("$examplePrefix-$key",preset.getString(key))}
                                next.put("values",values);m.updateProbabilityDraft(next)
                            },label={Text(label(example),fontSize=11.sp)},modifier=Modifier.weight(1f).testTag("probability-example-$id"))
                        }
                    }
                }
            }
        }
        ProbabilitySelect(tr("Calculation"),categories,label(categories.first {it.getString("id")==category}),ko) {selected->
            update {it.put("category",selected.getString("id")).remove("operation")}
        }
        if(category=="distribution")ProbabilitySelect(tr("Distribution"),distributions,label(distribution),ko) {selected->
            update {it.put("distribution",selected.getString("id")).remove("operation")}
        }
        // Wrap operators to keep every choice visible on phone screens.
        val operationRows=operations.chunked(if(category in listOf("normalSolver","bayes"))2 else 3)
        val operationMinimumSize=if(operationRows.size>1)0.dp else LocalMinimumInteractiveComponentSize.current
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides operationMinimumSize) {
            Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(2.dp)) {
                operationRows.forEach {row->
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(5.dp)) {
                        row.forEach {op->FilterChip(selected=op==operation,onClick={update {it.put("operation",op.getString("id"))}},label={Text(label(op),fontSize=12.sp)},modifier=Modifier.weight(1f).testTag("probability-operation-${op.getString("id")}"))}
                    }
                }
            }
        }
        fields.chunked(2).forEach {row->
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                row.forEach {field->
                    val key=field.getString(0)
                    Column(Modifier.weight(1f)) {
                        if(eventMode&&!draft.optBoolean("independent")) {
                            val choices=eventChoices.filter {it.getString(0)==key||it.getString(0) !in eventInputs}.map {JSONObject().put("id",it.getString(0)).put("label",it.getString(1)).put("ko",it.getString(2))}
                            ProbabilitySelect(tr("Given probability"),choices,field.getString(if(ko)2 else 1),ko) {selected->
                                changeEventInputs(eventInputs.map {if(it==key)selected.getString("id") else it})
                            }
                        }
                        OutlinedTextField(input(field),onValueChange={text->update {next->next.put("values",JSONObject(savedValues.toString()).put("$prefix-$key",text))}},
                            label={Text(field.getString(if(ko)2 else 1),fontSize=12.sp)},singleLine=true,
                            keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Text),
                            modifier=Modifier.fillMaxWidth().keepInputVisible().testTag("probability-input-$key"))
                        if(eventMode&&!draft.optBoolean("independent")&&eventInputs.size>1)TextButton(onClick={changeEventInputs(eventInputs.filter {it!=key})}){Text(tr("Remove given probability"),fontSize=11.sp)}
                    }
                }
            }
        }
        if(eventMode&&!draft.optBoolean("independent"))eventChoices.firstOrNull {it.getString(0) !in eventInputs}?.let {next->
            TextButton(onClick={changeEventInputs(eventInputs+next.getString(0))},modifier=Modifier.testTag("probability-add-given")){Text(tr("Add given probability"))}
        }
        if(category=="events"&&operation.getString("id")!="conditionalCounts")Row {Checkbox(draft.optBoolean("independent"),onCheckedChange={checked->update {it.put("independent",checked)}});Text(tr("Independent events"),Modifier.padding(top=12.dp),fontSize=13.sp)}
        Text((if(operation.has("hint"))operation else definition).optString(if(ko)"hintKo" else "hint"),color=c.muted,fontSize=11.sp)
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Button(onClick={
                focus.clearFocus();keyboard?.hide()
                val values=JSONObject();fields.forEach {field->values.put(field.getString(0),input(field))}
                m.calculateProbability(JSONObject().put("category",category).put("distribution",distribution.getString("id")).put("operation",operation.getString("id")).put("independent",draft.optBoolean("independent")).put("values",values))
            },enabled=!m.busy,modifier=Modifier.testTag("probability-calculate")){Text(tr(if(category=="normalSolver")"Calculate parameter" else "Calculate probability"))}
            if(m.busy)TextButton(onClick={m.cancel()}){Text(tr("Cancel"))}
            else TextButton(onClick={update {next->val values=JSONObject(savedValues.toString());fields.forEach {values.remove("$prefix-${it.getString(0)}")};next.put("values",values)}}){Text(tr("Reset inputs"))}
        }
        if(category !in listOf("basic","dice","draw","counting")&&operation.getString("id")!="conditionalCounts")Text("0.5 · 1/2 · 50%",fontSize=11.sp,color=c.muted)
        if(m.probabilityError.isNotBlank()) {
            val match=Regex("^(.*?)( \\(([^)]+)\\))?$").matchEntire(m.probabilityError)
            val field=fields.firstOrNull {it.getString(0)==match?.groupValues?.get(3)}
            Text(tr(match?.groupValues?.get(1) ?: m.probabilityError)+(field?.let {" (${it.getString(if(ko)2 else 1)})"} ?: match?.groupValues?.get(2).orEmpty()),color=c.danger,modifier=Modifier.testTag("probability-error"))
        }
        m.probabilityResult?.let {result->
            val resultView=remember {BringIntoViewRequester()}
            LaunchedEffect(result) {withFrameNanos {};resultView.bringIntoView()}
            Column(Modifier.fillMaxWidth().bringIntoViewRequester(resultView).background(c.display).padding(18.dp).testTag("probability-result"),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                Text(result.optString("formula"),fontSize=13.sp,color=c.muted)
                val fraction=result.optString("fraction")
                if('/' in fraction)Text(fraction,fontSize=23.sp,fontWeight=FontWeight.SemiBold)
                Text(probabilityNumber(result.optString("value"),m.displayDigits),fontSize=30.sp,fontWeight=FontWeight.Bold)
                if(result.optBoolean("isProbability"))Text(probabilityNumber(result.optString("percent").removeSuffix("%"),m.displayDigits)+"%",fontSize=20.sp,color=c.accent)
                result.optJSONArray("details")?.objects()?.forEach {detail->Text(tr(detail.getString("label"))+"  "+tr(probabilityNumber(detail.getString("value"),m.displayDigits)),fontSize=12.sp,color=c.muted)}
                if(result.optString("note").isNotBlank())Text(tr(result.getString("note")),fontSize=11.sp,color=c.muted)
            }
            result.optJSONObject("plot")?.let {ProbabilityPlot(it,m.displayDigits)}
        }
    }
}

@Composable private fun ProbabilitySelect(title:String,items:List<JSONObject>,selected:String,ko:Boolean,onSelect:(JSONObject)->Unit) {
    var expanded by remember {mutableStateOf(false)}
    Box {
        OutlinedButton(onClick={expanded=true},modifier=Modifier.fillMaxWidth()){Text("$title · $selected ▾")}
        DropdownMenu(expanded,onDismissRequest={expanded=false},modifier=Modifier.heightIn(max=320.dp)) {
            items.forEach {item->DropdownMenuItem(text={Text(item.probabilityLabel(ko))},onClick={expanded=false;onSelect(item)})}
        }
    }
}

@Composable private fun ProbabilityPlot(plot:JSONObject,digits:Int) {
    val c=LocalInstrument.current
    val points=plot.getJSONArray("points").fields()
    val min=points.first().getDouble(0);val max=points.last().getDouble(0)
    val height=points.maxOf {it.getDouble(1)}
    if(max<=min||height<=0)return
    val description=tr("Probability distribution preview")
    Canvas(Modifier.fillMaxWidth().height(135.dp).semantics {contentDescription=description}) {
        fun x(v:Double)=16f+((v-min)/(max-min)).toFloat()*(size.width-32f)
        fun y(v:Double)=size.height-8f-(v/height).toFloat()*(size.height-20f)
        val baseline=size.height-8f
        drawLine(c.muted.copy(alpha=.4f),Offset(16f,baseline),Offset(size.width-16f,baseline))
        if(plot.optBoolean("discrete")) {
            val width=minOf(24f,(size.width-32f)/points.size*.75f)
            points.forEach {p->val top=y(p.getDouble(1));drawRect(if(p.getBoolean(2))c.accent else c.muted.copy(alpha=.35f),Offset(x(p.getDouble(0))-width/2,top),Size(width,baseline-top))}
        }else {
            for(i in 1 until points.size) {
                val a=points[i-1];val b=points[i]
                val p1=Offset(x(a.getDouble(0)),y(a.getDouble(1)));val p2=Offset(x(b.getDouble(0)),y(b.getDouble(1)))
                if(a.getBoolean(2)&&b.getBoolean(2))drawPath(Path().apply {moveTo(p1.x,baseline);lineTo(p1.x,p1.y);lineTo(p2.x,p2.y);lineTo(p2.x,baseline);close()},c.accent.copy(alpha=.25f))
                drawLine(c.accent,p1,p2,2f)
            }
        }
    }
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(probabilityNumber(min.toString(),minOf(digits,3)),fontSize=10.sp,color=c.muted);Text(probabilityNumber(max.toString(),minOf(digits,3)),fontSize=10.sp,color=c.muted)}
    if(plot.has("range"))Text(tr("Range · 0.1%–99.9% quantiles"),fontSize=10.sp,color=c.muted)
    if(plot.optBoolean("sampled"))Text(tr("Preview samples integer masses; some counts are omitted."),fontSize=10.sp,color=c.muted)
    Text(tr(if(plot.optBoolean("event",true))"Preview · shaded region is the selected event" else "Mass / density preview"),fontSize=10.sp,color=c.muted)
}
