package com.example.calcmax.ui
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.*
import com.example.calcmax.calculator.CalculatorModel

private val Catalog=linkedMapOf(
    "Scientific" to listOf("abs()","floor()","ceil()","round(,0)","sign()","sqrt()","cbrt()","nthroot(,3)","log(,10)","ln()","exp()","sinc()","sinh()","cosh()","tanh()","asinh()","acosh()","atanh()","gamma()","factorial()","nCr(,)","nPr(,)","gcd(,)","lcm(,)","prime()","isprime()","factorint()","divisors()"),
    "Symbolic" to listOf("simplify()","expand()","factor()","collect(,x)","subs(,x,0)","diff(,x)","diff(,x,2)","integrate(,x)","integrate(,x,0,1)","limit(,x,0)","limit(,x,0,left)","limit(,x,0,right)","series(,x,0,6)","sum(,x,1,10)","product(,x,1,10)","solve(,x)","nsolve(,x,0,1)","nintegrate(,x,0,1)","nderivative(,x,0)","minimum(,x,0,1)","maximum(,x,0,1)","piecewise([,x>0],[0,true])"),
    "Complex" to listOf("re()","im()","conj()","abs()","arg()","polar(,pi/2)","rectpolar()"),
    "Matrix & vector" to listOf("det()","inverse()","transpose()","rank()","trace()","ref()","rref()","lu()","linsolve(,)","eigenvalues()","eigenvectors()","dot(,)","cross(,)","norm()","normalize()","angle(,)","projection(,)"),
    "Data & units" to listOf("stats([])","mean([])","median([])","variance([])","stdev([])","quartiles([])","regression([],linear)","qty(,m)","convert(,m,cm)")
)
@Composable fun CatalogDialog(m: CalculatorModel,close: ()->Unit) {
    var category by remember {mutableStateOf("Scientific")};var search by remember {mutableStateOf("")}
    val custom=m.functions.keys().asSequence().toList().sorted().map {name->
        val count=m.functions.getJSONObject(name).getJSONArray("parameters").length()
        "$name(${if(count>0)",".repeat(count-1) else ""})"
    }
    val categories=linkedMapOf("Custom" to custom).apply {putAll(Catalog)}
    AlertDialog(onDismissRequest=close,title={Text("Function catalog")},text={Column(Modifier.verticalScroll(rememberScrollState())) {
        Field(search,"Find function",Modifier.fillMaxWidth()) {search=it}
        Choices(categories.keys.toList(),category,{category=it})
        val entries=if(search.isBlank())categories[category].orEmpty() else categories.values.flatten().filter {it.contains(search,true)}
        if(category=="Custom"&&entries.isEmpty()&&search.isBlank())Text("Save a function in Functions to see it here.",fontSize=12.sp)
        entries.chunked(2).forEach { row->Row {row.forEach { source->TextButton(onClick={val list=source.indexOf("[]");val at=if(list>=0)list+1 else source.indexOf('(')+1;if(m.mode=="Python") {val edit=PythonEditorTools.insertCatalog(m.pythonSource,m.pythonSelectionStart,m.pythonSelectionEnd,source,at);m.editPython(edit.source,edit.cursor)} else m.insert(source,at);close()},modifier=Modifier.weight(1f)) {Text(source,fontSize=12.sp)} } } }
        Text("Tap a template, then tap its empty slots to fill them. ↑ selects the enclosing expression; ↓ selects a child.",fontSize=11.sp)
    }},confirmButton={TextButton(onClick=close) {Text("Done")}})
}
