package com.example.calcmax.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.calcmax.calculator.CalculatorModel
import com.example.calcmax.calculator.FunctionTransfer
import com.example.calcmax.calculator.ResultDisplayMode
import com.example.calcmax.math.Editor
import com.example.calcmax.math.Parser
import org.json.JSONObject

@Composable fun EquationScreen(m:CalculatorModel) {
    val kind=m.equationKind
    val coefficients=m.equationCoefficients
    val equations=m.equationSystem
    val equation=m.equationGeneral
    val variables=m.equationVariables
    val variable=m.equationVariable
    val guess=m.equationGuess
    val numerical=m.equationNumeric
    val degree=when(kind){"Linear"->1;"Quadratic"->2;else->3}
    val polynomial=buildString {
        (0..degree).forEach{i->
            val raw=coefficients[i].trim().ifBlank{"0"}
            if(raw!="0") {
                val numeric=raw.toBigDecimalOrNull()
                val negative=numeric?.signum()==-1
                val magnitude=if(negative)raw.removePrefix("-")else raw
                if(isNotEmpty())append(if(negative)"-"else "+")else if(negative)append("-")
                val power=degree-i
                if(power==0)append(if(numeric!=null)magnitude else "($magnitude)")
                else {
                    if(magnitude!="1")append(if(numeric!=null)"$magnitude*"else "($magnitude)*")
                    append(variable);if(power>1)append("^$power")
                }
            }
        }
        if(isEmpty())append("0")
        append("=0")
    }
    val expression=when(kind){"General"->equation;"System"->"["+equations.lines().filter{it.isNotBlank()}.joinToString(",")+"]";else->polynomial}
    Panel("Equation solver","") {
        Choices(listOf("Linear","Quadratic","Cubic","System","General"),kind,{m.equationKind=it})
        if(kind=="System") {
            OutlinedTextField(equations,{m.equationSystem=it},Modifier.fillMaxWidth(),label={Text("One equation per line")},minLines=2)
            Field(variables,"Variables · comma separated",Modifier.fillMaxWidth()){m.equationVariables=it}
        }else {
            Field(variable,"Solve for",Modifier.fillMaxWidth()){m.equationVariable=it}
            if(kind=="General")Field(equation,"Equation",Modifier.fillMaxWidth()){m.equationGeneral=it}
            else {
                Text(when(degree){1->"a x + b = 0";2->"a x² + b x + c = 0";else->"a x³ + b x² + c x + d = 0"})
                Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){(0..degree).forEach{i->Field(coefficients[i],('a'+i).toString(),Modifier.weight(1f)){v->m.equationCoefficients=coefficients.toMutableList().apply{set(i,v)}}}}
            }
            Choices(listOf("Exact","Numeric"),if(numerical)"Numeric" else "Exact",{m.equationNumeric=it=="Numeric"})
            if(numerical)Field(guess,"Initial guess",Modifier.fillMaxWidth()){m.equationGuess=it}
        }
        val preview=runCatching{JSONObject(Parser(expression,true).parse().json())}.getOrNull()
        if(preview!=null)Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())){MathNode(preview,m.inputFont)}
        Button(onClick={
            val names=if(kind=="System")variables.split(',').map{it.trim()}else listOf(variable.trim())
            if(names.isEmpty()||names.any{!it.matches(Regex("[A-Za-z][A-Za-z0-9_]*"))})m.error="Enter valid variable names"
            else {val command=if(kind=="System")"solve($expression,[${names.joinToString(",")}])" else if(numerical)"nsolve($expression,${names[0]},$guess)" else "solve($expression,${names[0]})";m.fresh(Editor(command));m.calculate()}
        },enabled=!m.busy){Text(if(m.busy)"Solving…" else "Solve")}
        if(m.error.isNotBlank())Text(m.error,color=MaterialTheme.colorScheme.error)
        if(m.result!=null) {HorizontalDivider();Text("Solution");Box(Modifier.horizontalScroll(rememberScrollState())){ResultMath(m.result!!,m.decimal,m.outputFont,
            displayMode=m.resultDisplayMode,thousandsSeparator=m.thousandsSeparator,displayDigits=m.displayDigits)};SmallAction(if(m.decimal)"Show exact" else "Show decimal"){m.decimal=!m.decimal}}
    }
}

@Composable fun FunctionsScreen(m:CalculatorModel) {
    val context=LocalContext.current
    var name by rememberSaveable{mutableStateOf("f")}
    var parameters by rememberSaveable{mutableStateOf("x")}
    var body by rememberSaveable{mutableStateOf("x^2+1")}
    var message by rememberSaveable{mutableStateOf("")}
    fun write(uri:Uri) {
        try {
            context.contentResolver.openOutputStream(uri,"wt")?.use {it.write(m.exportFunctions().toByteArray(Charsets.UTF_8))} ?: error("Could not open the selected file for writing")
            message="Exported ${m.functions.length()} function${if(m.functions.length()==1)"" else "s"}"
        } catch(e:Exception) {m.error=e.message ?: "Could not save the functions file"}
    }
    val exportFile=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) {uri->if(uri!=null)write(uri)}
    val importFile=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {uri->
        if(uri!=null)try {
            val text=context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use {it.readText()} ?: error("Could not read the selected file")
            val summary=m.importFunctions(text)
            if(summary.isNotEmpty())message=summary
        } catch(e:Exception) {m.error=e.message ?: "Could not read the selected file"}
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // The editor stays fixed and only the saved list scrolls; on short screens the whole panel scrolls instead.
        val scrollAll=maxHeight<480.dp
        val panel=if(scrollAll) Modifier.fillMaxSize().verticalScroll(rememberScrollState()) else Modifier.fillMaxSize()
        Column(panel.padding(14.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Text("Custom functions",style=MaterialTheme.typography.titleLarge)
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Field(name,"Name",Modifier.weight(1f)){name=it;message=""};Field(parameters,"Parameters",Modifier.weight(2f)){parameters=it;message=""}}
            Field(body,"Formula",Modifier.fillMaxWidth()){body=it;message=""}
            val preview=runCatching{JSONObject(Parser(body,true).parse().json())}.getOrNull()
            if(preview!=null)Box(Modifier.horizontalScroll(rememberScrollState())){MathNode(preview,m.inputFont)}
            Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Button(onClick={m.define(name.trim(),parameters,body,showResult=false);message=if(m.error.isEmpty())"Saved ${name.trim()}($parameters)" else ""}){Text("Save function")}
                SmallAction("Clear"){name="";parameters="";body="";message="";m.error=""}
                SmallAction("Export"){if(m.functions.length()==0)message="No custom functions to export" else exportFile.launch("calcmax-functions.json")}
                SmallAction("Import"){importFile.launch(arrayOf("application/json","text/plain","application/octet-stream"))}
            }
            if(message.isNotEmpty())Text(message)
            if(m.error.isNotEmpty())Text(m.error,color=MaterialTheme.colorScheme.error)
            HorizontalDivider()
            val list=if(scrollAll) Modifier.fillMaxWidth() else Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())
            Column(list,verticalArrangement=Arrangement.spacedBy(10.dp)) {
                m.functions.keys().asSequence().toList().sorted().forEach{key->
                    val definition=m.functions.getJSONObject(key)
                    val args=definition.getJSONArray("parameters")
                    val params=(0 until args.length()).joinToString(","){args.getString(it)}
                    Text("$key($params)",style=MaterialTheme.typography.titleMedium)
                    Box(Modifier.horizontalScroll(rememberScrollState())){MathNode(definition.getJSONObject("body"),m.inputFont*.85f)}
                    Row {
                        SmallAction("Edit"){name=key;parameters=params;body=FunctionTransfer.storedSource(definition);message=""}
                        SmallAction("Insert"){m.mode="Scientific/CAS";m.insert("$key(${",".repeat((args.length()-1).coerceAtLeast(0))})",key.length+1)}
                        SmallAction("Delete"){m.removeVariable(key)}
                    }
                }
            }
        }
    }
}
