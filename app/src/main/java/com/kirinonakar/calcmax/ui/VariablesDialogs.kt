package com.kirinonakar.calcmax.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.*
import com.kirinonakar.calcmax.calculator.CalculatorModel
import com.kirinonakar.calcmax.ui.theme.LocalInstrument
import kotlin.math.max
import org.json.JSONArray
import org.json.JSONObject

private fun storedVariableDisplayTree(node:JSONObject):JSONObject {
    val display=JSONObject(node.toString())
    val args=display.optJSONArray("args")
    if(args!=null)for(index in 0 until args.length())args.optJSONObject(index)?.let {args.put(index,storedVariableDisplayTree(it))}
    val radicand=args?.optJSONObject(0)
    return if(display.optString("kind")=="binary"&&display.optString("value")=="^"&&radicand!=null&&isSquareRootExponent(args.optJSONObject(1)))
        JSONObject().put("kind","root").put("args",JSONArray().put(radicand))
    else display
}

@Composable private fun StoredVariableRow(name:String,stored:JSONObject,selected:Boolean,labelSize:Float,valueSize:Float,onSelect:()->Unit) {
    val display=remember(stored){storedVariableDisplayTree(stored)}
    Row(Modifier.fillMaxWidth().clickable(onClick=onSelect).testTag("stored-variable-$name").padding(vertical=4.dp)) {
        MathText("$name = ",labelSize,modifier=Modifier.alignBy(MathAxis),tint=if(selected)LocalInstrument.current.accent else LocalInstrument.current.ink)
        Box(Modifier.alignBy(MathAxis).horizontalScroll(rememberScrollState())) {
            MathNode(display,valueSize)
        }
    }
}

private fun storedVariableNames(variables:JSONObject):List<String> =
    variables.keys().asSequence().toList().sortedWith(compareBy<String> {when(it){"Ans"->0;"M"->1;else->2}}.thenBy {it})

private fun storedDataSetNames(dataSets:JSONObject):List<String> = dataSets.keys().asSequence().toList().sorted()

@Composable private fun StoredDataSetRow(name:String,onRecall:()->Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick=onRecall).testTag("stored-stats-data-$name").padding(vertical=8.dp)) {
        Text(name,fontSize=16.sp,color=LocalInstrument.current.ink,modifier=Modifier.weight(1f))
        Text(tr("Stats data"),fontSize=12.sp,color=LocalInstrument.current.muted)
    }
}

@Composable private fun StoredDataSets(m:CalculatorModel,close:()->Unit) {
    val names=storedDataSetNames(m.dataSets)
    if(names.isNotEmpty()) {
        Text(tr("Saved stats data · tap to recall"),fontSize=12.sp,color=LocalInstrument.current.muted)
        names.forEach {name->
            m.dataSets.optJSONObject(name)?.let {dataSet->
                StoredDataSetRow(name) {
                    m.insert(statisticsRecallSource(m.editor,dataSet.optString("csv"),dataSet.optString("kind","list"),m.committed))
                    close()
                }
            }
        }
    }
}

@Composable fun RecallDialog(m: CalculatorModel,close: ()->Unit) {
    val names=storedVariableNames(m.variables)
    AlertDialog(onDismissRequest=close,title={Text(tr("Recall variable"))},text={Column(Modifier.fillMaxWidth().heightIn(max=480.dp).verticalScroll(rememberScrollState())) {
        if(names.isEmpty()&&m.dataSets.length()==0)Text(tr("No stored variables"))
        names.forEach {name->
            m.variables.optJSONObject(name)?.let {stored->
                StoredVariableRow(name,stored,false,16f,m.outputFont*.72f){m.insert(name);close()}
            }
        }
        StoredDataSets(m,close)
    }},confirmButton={TextButton(onClick=close){Text(tr("Close"))}},dismissButton={if(names.isNotEmpty())TextButton(onClick={m.deleteAllVariables();close()}){Text(tr(if(m.dataSets.length()>0)"Delete all variables" else "Delete all"))}})
}

@Composable fun VariablesDialog(m: CalculatorModel,close: ()->Unit) {
    var name by rememberSaveable { mutableStateOf("A") }; var value by rememberSaveable { mutableStateOf(m.editor.source.ifBlank { "0" }) }; var parameters by rememberSaveable { mutableStateOf("x") }; var function by rememberSaveable { mutableStateOf(false) }
    fun selectVariable(key:String) {
        name=key
        function=false
        m.variables.optJSONObject(key)?.let {stored->value=treeSource(stored) ?: ""}
    }
    AlertDialog(onDismissRequest=close,title={Text(tr("Variables & functions"))},text={Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
        Choices(listOf("A","B","C","D","F","x","y","z","t","n","r","M"),name,{selectVariable(it)})
        Column(Modifier.fillMaxWidth().heightIn(max=560.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(6.dp)) {
        Text(tr("Stored values · tap to select"),fontSize=12.sp,color=LocalInstrument.current.muted)
        if(m.variables.length()==0&&m.functions.length()==0&&m.dataSets.length()==0)Text(tr("No stored variables"))
        storedVariableNames(m.variables).forEach{key->
            m.variables.optJSONObject(key)?.let {stored->
                StoredVariableRow(key,stored,name==key&&!function,12f,m.outputFont*.55f){selectVariable(key)}
            }
        }
        m.functions.keys().asSequence().toList().sorted().forEach {key->
            Text("$key(…) = ${m.functions.optJSONObject(key)?.optString("source").orEmpty()}",Modifier.fillMaxWidth().clickable {
                name=key;function=true
                m.functions.optJSONObject(key)?.let {definition->
                    value=definition.optString("source")
                    parameters=definition.optJSONArray("parameters")?.let {args->(0 until args.length()).joinToString(","){args.optString(it)}} ?: "x"
                }
            }.padding(vertical=4.dp),fontSize=12.sp,color=if(name==key&&function)LocalInstrument.current.accent else LocalInstrument.current.ink)
        }
        StoredDataSets(m,close)
        Field(name,"Name",Modifier.fillMaxWidth()) { name=it }
        OutlinedTextField(value,{value=it},modifier=Modifier.fillMaxWidth().testTag("stored-variable-value"),label={Text(tr("Value / expression"))},singleLine=true,
            trailingIcon={TextButton(onClick={value=m.editor.source},enabled=m.editor.source.isNotBlank(),contentPadding=PaddingValues(horizontal=4.dp),modifier=Modifier.semantics{contentDescription="Paste current expression"}){Text(tr("Paste"),fontSize=11.sp)}})
        Row(verticalAlignment=Alignment.CenterVertically) { Checkbox(function,{function=it});Text(tr("User function")) }
        if(function) Field(parameters,"Parameters (comma separated)",Modifier.fillMaxWidth()) { parameters=it }
        Row { SmallAction("Store") { if(function)m.define(name,parameters,value) else m.store(name,value) }; SmallAction("Recall") { m.insert(if(function) "$name()" else name);close() }; SmallAction("Delete") { m.removeVariable(name) } }
        Text(if(isKorean())"${name}에 대한 가정" else "Assumption for $name",fontSize=12.sp)
        Choices(listOf("none","real","positive","integer","nonzero"),m.assumptions.optJSONArray(name)?.optString(0) ?: "none",{m.assume(name,it)})
        Text((if(isKorean())"저장값: " else "Stored: ")+m.variables.keys().asSequence().toList().joinToString()+(if(isKorean())"\n함수: " else "\nFunctions: ")+m.functions.keys().asSequence().toList().joinToString(),fontSize=12.sp)
        if(m.error.isNotBlank()) Text(m.error,color=LocalInstrument.current.danger)
        }
    }},confirmButton={TextButton(onClick=close) { Text(tr("Done")) }},dismissButton={if(m.variables.length()>0)TextButton(onClick={m.deleteAllVariables()}){Text(tr(if(m.dataSets.length()>0)"Delete all variables" else "Delete all"))}})
}
