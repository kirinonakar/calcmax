package com.kirinonakar.symvacas.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.kirinonakar.symvacas.calculator.CalculatorModel
import com.kirinonakar.symvacas.calculator.FunctionTransfer
import com.kirinonakar.symvacas.calculator.ResultDisplayMode
import com.kirinonakar.symvacas.math.Editor
import com.kirinonakar.symvacas.math.LatexInput
import com.kirinonakar.symvacas.math.Parser
import org.json.JSONObject
import org.json.JSONArray

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
    val expression=when(kind){
        "General"->LatexInput.convert(equation) ?: equation
        "System"->"["+equations.lines().filter{it.isNotBlank()}.joinToString(","){LatexInput.convert(it) ?: it}+"]"
        "dsolve"->LatexInput.convert(m.equationOde) ?: m.equationOde
        "pdsolve"->LatexInput.convert(m.equationPde) ?: m.equationPde
        else->polynomial
    }
    Panel("Equation solver","") {
        Choices(listOf("Linear","Quadratic","Cubic","System","General","dsolve","pdsolve"),kind,{m.equationKind=it;m.error=""},translate=false)
        if(kind=="dsolve") {
            Field(m.equationOde,"Differential equation",Modifier.fillMaxWidth()){m.equationOde=it}
            EquationInputPreview(m.equationOde,m.inputFont)
            Field(m.equationOdeFunction,"Dependent function",Modifier.fillMaxWidth()){m.equationOdeFunction=it}
            Field(m.equationOdeVariable,"Independent variable",Modifier.fillMaxWidth()){m.equationOdeVariable=it}
            Field(m.equationOdeInitial,"Initial conditions (optional)",Modifier.fillMaxWidth()){m.equationOdeInitial=it}
            Text(tr("Example: y(0)=1 or [y(0)=1,y(1)=2]"),style=MaterialTheme.typography.bodySmall)
        }else if(kind=="pdsolve") {
            Field(m.equationPde,"Partial differential equation",Modifier.fillMaxWidth()){m.equationPde=it}
            EquationInputPreview(m.equationPde,m.inputFont)
            Field(m.equationPdeFunction,"Dependent function",Modifier.fillMaxWidth()){m.equationPdeFunction=it}
            Field(m.equationPdeHint,"Hint (optional)",Modifier.fillMaxWidth()){m.equationPdeHint=it}
        }else if(kind=="System") {
            OutlinedTextField(equations,{m.equationSystem=it},Modifier.fillMaxWidth().keepInputVisible(),label={Text("One equation per line")},minLines=2)
            EquationInputPreview(equations,m.inputFont,multiline=true)
            Field(variables,"Variables · comma separated",Modifier.fillMaxWidth(),translate=false){m.equationVariables=it}
        }else {
            Field(variable,"Solve for",Modifier.fillMaxWidth(),translate=false){m.equationVariable=it}
            if(kind=="General") {
                Field(equation,"Equation",Modifier.fillMaxWidth(),translate=false){m.equationGeneral=it}
                EquationInputPreview(equation,m.inputFont)
            }
            else {
                Text(when(degree){1->"a x + b = 0";2->"a x² + b x + c = 0";else->"a x³ + b x² + c x + d = 0"})
                Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){(0..degree).forEach{i->Field(coefficients[i],('a'+i).toString(),Modifier.weight(1f),translate=false){v->m.equationCoefficients=coefficients.toMutableList().apply{set(i,v)}}}}
                EquationInputPreview(polynomial,m.inputFont)
            }
            Choices(listOf("Exact","Numeric"),if(numerical)"Numeric" else "Exact",{m.equationNumeric=it=="Numeric"},translate=false)
            if(numerical)Field(guess,"Initial guess",Modifier.fillMaxWidth(),translate=false){m.equationGuess=it}
        }
        CalculationButton("Solve",m.busy,m.inputVersion,onCancel={m.cancel()},enabled=!m.busy,onClick={
            val command=when(kind) {
                "dsolve"->if(expression.isBlank()||m.equationOdeFunction.isBlank()||!m.equationOdeVariable.trim().matches(Regex("[A-Za-z][A-Za-z0-9_]*"))) {
                    m.error="Enter an equation, dependent function and valid independent variable";null
                } else "dsolve(${expression.trim()},${m.equationOdeFunction.trim()},${m.equationOdeVariable.trim()}${m.equationOdeInitial.trim().let{if(it.isEmpty())"" else ",$it"}})"
                "pdsolve"->if(expression.isBlank()||m.equationPdeFunction.isBlank()) {
                    m.error="Enter an equation and dependent function";null
                } else "pdsolve(${expression.trim()},${m.equationPdeFunction.trim()}${m.equationPdeHint.trim().let{if(it.isEmpty())"" else ",$it"}})"
                else->{
                    val names=if(kind=="System")variables.split(',').map{it.trim()}else listOf(variable.trim())
                    if(names.isEmpty()||names.any{!it.matches(Regex("[A-Za-z][A-Za-z0-9_]*"))}) {m.error="Enter valid variable names";null}
                    else if(kind=="System")"solve($expression,[${names.joinToString(",")}])" else if(numerical)"nsolve($expression,${names[0]},$guess)" else "solve($expression,${names[0]})"
                }
            }
            if(command!=null){m.fresh(Editor(command));m.calculate()}
        })
        if(m.error.isNotBlank())Text(m.error,color=MaterialTheme.colorScheme.error)
        if(m.result!=null) {HorizontalDivider();Text("Solution");Box(Modifier.fillMaxWidth()){ResultMath(m.result!!,m.decimal,m.outputFont,
            displayMode=m.resultDisplayMode,thousandsSeparator=m.thousandsSeparator,displayDigits=m.displayDigits)};SmallAction(if(m.decimal)"Show exact" else "Show decimal",translate=false){m.decimal=!m.decimal}}
        ResultGuidance(m)
        SolutionSteps(m,m.result?.optJSONObject("solutionSteps") ?: m.result?.optJSONObject("equationSteps"))
    }
}

@Composable fun SolutionSteps(m:CalculatorModel,report:JSONObject?) {
    if(report!=null) {
            var expanded by remember(report){mutableStateOf(false)}
            var advancedExpanded by remember(report){mutableStateOf(false)}
            val expansionDescription=tr(if(expanded)"Expanded" else "Collapsed")
            val clipboard=LocalClipboardManager.current
            val language=LocalLanguage.current
            val copyText=remember(report.toString(),m.displayDigits,language) {
                solutionStepsCopyText(report,m.displayDigits){translateLabel(it,language)}
            }
            HorizontalDivider()
            Row(Modifier.fillMaxWidth()) {
            TextButton(onClick={expanded=!expanded},modifier=Modifier.weight(1f).semantics {
                stateDescription=expansionDescription
            }) {Text((if(expanded)"▾ " else "▸ ")+tr("Step-by-step solution"))}
            SmallAction("Copy full solution"){clipboard.setText(AnnotatedString(copyText))}
            }
            if(expanded) {SelectionContainer {Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                report.optString("note").lines().filter{it.isNotBlank()}.forEach {line->Text(tr(line),style=MaterialTheme.typography.bodySmall)}
                if(report.optString("method").isNotBlank())Text(tr(report.optString("method")),style=MaterialTheme.typography.titleMedium)
                EquationStepList(report.optJSONArray("steps"),m)
                report.optJSONArray("advancedSteps")?.let {advanced->
                    val advancedDescription=tr(if(advancedExpanded)"Expanded" else "Collapsed")
                    DisableSelection {TextButton(onClick={advancedExpanded=!advancedExpanded},modifier=Modifier.fillMaxWidth().semantics{stateDescription=advancedDescription}) {
                        Text((if(advancedExpanded)"▾ " else "▸ ")+tr("Advanced solution · Gaussian elimination"))
                    }}
                    if(advancedExpanded)EquationStepList(advanced,m)
                }
            }}}
        }
}

@Composable private fun EquationStepList(steps:JSONArray?,m:CalculatorModel) {
    if(steps==null)return
    (0 until steps.length()).forEach {index->
        val step=steps.getJSONObject(index)
        Text("${index+1}. ${tr(step.optString("title"))}",style=MaterialTheme.typography.titleSmall)
        val language=LocalLanguage.current
        val explanation=equationStepExplanation(step){translateLabel(it,language)}
        if(explanation.isNotBlank())Text(explanation,style=MaterialTheme.typography.bodySmall)
        step.optJSONObject("variableOrder")?.let{EquationStepFormula(it,m)}
        val operation=step.optJSONObject("operation")
        val formulas=step.optJSONArray("equations")
        val combined=operation!=null&&formulas!=null&&formulas.length()>0
        if(combined)EquationStepFormula(JSONObject().put("tree",JSONObject().put("kind","row-operation").put("args",JSONArray().put(operation).put(formulas.getJSONObject(0).getJSONObject("tree")))),m)
        else operation?.let{EquationStepFormula(JSONObject().put("tree",it),m)}
        if(step.has("tree"))EquationStepFormula(step,m)
        step.optJSONArray("equations")?.let {formulas->
            ((if(combined)1 else 0) until formulas.length()).forEach {EquationStepFormula(formulas.getJSONObject(it),m)}
        }
    }
}

@Composable fun ResultGuidance(m:CalculatorModel) {
    m.result?.optJSONObject("guidance")?.let {guidance->
        if(guidance.optString("status")!="unresolved_equation")Text(tr(guidance.optString("message")),style=MaterialTheme.typography.bodySmall)
        if(guidance.optString("detail").isNotBlank())Text(tr(guidance.optString("detail")),style=MaterialTheme.typography.bodySmall)
        guidance.optJSONArray("suggestions")?.let {suggestions->
            (0 until suggestions.length()).forEach {index->
                val suggestion=suggestions.getJSONObject(index)
                TextButton(onClick={m.mode="Scientific/CAS";m.fresh(Editor(suggestion.getString("command")))},enabled=!m.busy) {
                    Text(tr(suggestion.optString("label"))+" · "+suggestion.optString("detail"))
                }
            }
            if(suggestions.length()>0)Text(tr("Choose a suggestion to fill the input, then press Solve or =. Numerical convergence is not guaranteed."),style=MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable private fun EquationStepFormula(formula:JSONObject,m:CalculatorModel) {
    formula.optJSONObject("tree")?.let {tree->
        val displayTree=ResultDisplayFormat.formatTree(tree,ResultDisplayMode.OFF,false,maxFractionDigits=m.displayDigits)
        val text=remember(displayTree.toString()){equationFormulaText(displayTree)}
        val clipboard=LocalClipboardManager.current
        var selectionOpen by remember(text){mutableStateOf(false)}
        var selectedText by remember(text){mutableStateOf(TextFieldValue(text,TextRange(0,text.length)))}
        fun selectFormula(){selectedText=TextFieldValue(text,TextRange(0,text.length));selectionOpen=true}
        // Native text handles operate on one coherent formula, preserving
        // fractions and parentheses rather than copying separate layout labels.
        DisableSelection {Box(Modifier.fillMaxWidth().combinedClickable(onClick=::selectFormula,onLongClick=::selectFormula)
            .horizontalScroll(rememberScrollState()).semantics {contentDescription=text}){MathNode(displayTree,m.outputFont)}}
        if(selectionOpen)AlertDialog(onDismissRequest={selectionOpen=false},title={Text(tr("Select formula"))},text={
            OutlinedTextField(selectedText,{selectedText=it},readOnly=true,maxLines=8,modifier=Modifier.fillMaxWidth())
        },confirmButton={TextButton(onClick={
            val range=selectedText.selection
            clipboard.setText(AnnotatedString(if(range.collapsed)text else text.substring(range.min,range.max)))
            selectionOpen=false
        }){Text(tr("Copy"))}},dismissButton={TextButton(onClick={selectionOpen=false}){Text(tr("Close"))}})
    }
}

/** Render immediately beneath the input, independently for each equation in a system. */
@Composable private fun EquationInputPreview(source:String,size:Float,multiline:Boolean=false) {
    val previews=remember(source,multiline) {
        (if(multiline)source.lines() else listOf(source)).filter{it.isNotBlank()}.mapNotNull {line->
            runCatching{JSONObject(Parser(LatexInput.convert(line) ?: line,true).parse().json())}.getOrNull()
        }
    }
    if(previews.isNotEmpty())Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(6.dp)) {
        previews.forEach {preview->
            Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())){MathNode(preview,size)}
        }
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
            Text(tr("Custom functions"),style=MaterialTheme.typography.titleLarge)
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Field(name,"Name",Modifier.weight(1f)){name=it;message=""};Field(parameters,"Parameters",Modifier.weight(2f)){parameters=it;message=""}}
            Field(body,"Formula",Modifier.fillMaxWidth()){body=it;message=""}
            val preview=runCatching{JSONObject(Parser(body,true).parse().json())}.getOrNull()
            if(preview!=null)Box(Modifier.horizontalScroll(rememberScrollState())){MathNode(preview,m.inputFont)}
            Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Button(onClick={m.define(name.trim(),parameters,body,showResult=false);message=if(m.error.isEmpty())"Saved ${name.trim()}($parameters)" else ""}){Text(tr("Save function"))}
                SmallAction("Clear"){name="";parameters="";body="";message="";m.error=""}
                SmallAction("Export"){if(m.functions.length()==0)message="No custom functions to export" else exportFile.launch("symvacas-functions.json")}
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
