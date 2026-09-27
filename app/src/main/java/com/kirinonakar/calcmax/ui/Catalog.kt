package com.kirinonakar.calcmax.ui
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.kirinonakar.calcmax.calculator.CalculatorModel
import com.kirinonakar.calcmax.ui.theme.LocalInstrument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val Catalog=linkedMapOf(
    "Scientific" to listOf("sin()","cos()","tan()","asin()","acos()","atan()","abs()","floor()","ceil()","round(,0)","sign()","sqrt()","cbrt()","nthroot(,3)","atan2(,1)","frac()","iPart()","log(,10)","ln()","exp()","sinc()","sinh()","cosh()","tanh()","asinh()","acosh()","atanh()","gamma()","erf()","erfc()","Ei()","Si()","Ci()","zeta()","factorial()","nCr(,)","nPr(,)","gcd(,)","lcm(,)","prime()","isprime()","factorint()","divisors()","rnd()","eng()","pol(,)","rec(,)","randInt(,)","sexagesimal(,,)","dms()","mixed(,,)","quotient(,)","remainder(,)","mod(,)","divmod(,)","sumdata([])"),
    "Symbolic" to listOf("simplify()","expand()","factor()","collect(,x)","subs(,x,0)","diff(,x)","diff(,x,2)","integrate(,x)","integrate(,x,0,1)","limit(,x,0)","limit(,x,0,left)","limit(,x,0,right)","series(,x,0,6)","taylor(,x,0,4)","sum(,x,1,10)","product(,x,1,10)","solve(,x)","nsolve(,x,0,1)","nintegrate(,x,0,1)","nderivative(,x,0)","minimum(,x,0,1)","maximum(,x,0,1)","piecewise([,x>0],[0,true])","apart(,x)","partfrac(,x)","together()","cancel()","trigsimp()","trigexpand()","powsimp()","powdenest()","hyperexpand()","nsimplify()","comDenom()","numden()","coeff(,x)","quo(,,x)","rem(,,x)","resultant(,,x)","discriminant(,x)","domain(,x)","range(,x)"),
    "Complex" to listOf("re()","im()","conj()","abs()","arg()","polar(,pi/2)","rectpolar()"),
    "ODE & transforms" to listOf("dsolve(,,)","laplace(,t,s)","ilaplace(,s,t)","fourier(,t,w)","ifourier(,w,t)","fft([])","ifft([])"),
    "Vector calculus" to listOf("gradient(,[x,y])","divergence(,[x,y])","curl(,[x,y])","hessian(,[x,y])","jacobian(,[x,y])","laplacian(,[x,y])"),
    "Matrix & vector" to listOf("det()","inverse()","transpose()","rank()","trace()","ref()","rref()","lu()","linsolve(,)","eigenvalues()","eigenvectors()","dot(,)","cross(,)","norm()","normalize()","angle(,)","projection(,)","charpoly(,x)","identity(2)","diag([])","qr()","cholesky()","nullspace()","cofactor()","adjugate()","rowspace()","singularvalues()","frob()","jordan()","dim()"),
    "Data & units" to listOf("stats([])","mean([])","median([])","variance([])","stdev([])","quartiles([])","sumdata([])","regression([],linear)","covariance([],[])","correlation([],[])","qty(,m)","convert(,m,cm)"),
    "Distributions" to listOf("normpdf(,0,1)","normcdf()","normcdf(,)","normcdf(,,0,1)","invnorm(,0,1)","tpdf(,10)","tcdf(,10)","tcdf(,,10)","invt(,10)","chi2pdf(,5)","chi2cdf(,5)","chi2cdf(,,5)","fpdf(,5,10)","fcdf(,5,10)","fcdf(,,5,10)","binompdf(,0.5,)","binomcdf(,0.5,)","poissonpdf(,)","poissoncdf(,)","geometpdf(,)","geometcdf(,)"),
    "Tests & intervals" to listOf("ttest(,[])","ttest(,,,)","ztest(,,[])","ztest(,,,)","chi2test([],[])","anova([],[])","tinterval(,[])","tinterval(,,,)","zinterval(,,[])","zinterval(,,,)"),
    "Finance" to listOf("tvmfv(,,,)","tvmpv(,,,)","tvmpmt(,,,)","tvmn(,,,)","tvmrate(,,,)","npv(,[])","npv(,,[])","irr([])","irr(,[])","amort(,,)","amort(,,,)","cagr(,,)")
)
@Composable fun CatalogDialog(m: CalculatorModel,close: ()->Unit) {
    var category by remember {mutableStateOf("Scientific")};var search by remember {mutableStateOf("")};var showHelp by remember {mutableStateOf(false)}
    val custom=m.functions.keys().asSequence().toList().sorted().map {name->
        val count=m.functions.getJSONObject(name).getJSONArray("parameters").length()
        "$name(${if(count>0)",".repeat(count-1) else ""})"
    }
    val categories=linkedMapOf("Custom" to custom).apply {putAll(Catalog)}
    AlertDialog(onDismissRequest=close,title={Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {Text("Function catalog",Modifier.weight(1f));SmallAction("Help",description="Open the function catalog help"){showHelp=true}}},text={Column(Modifier.fillMaxWidth().heightIn(max=480.dp)) {
        SearchField(search,"Find function") {search=it}
        Choices(categories.keys.toList(),category,{category=it})
        val entries=if(search.isBlank())categories[category].orEmpty() else categories.values.flatten().filter {it.contains(search,true)}
        Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())) {
            if(category=="Custom"&&entries.isEmpty()&&search.isBlank())Text("Save a function in Functions to see it here.",fontSize=12.sp)
            entries.chunked(2).forEach { row->Row {row.forEach { source->TextButton(onClick={val list=source.indexOf("[]");val at=if(list>=0)list+1 else source.indexOf('(')+1;if(m.mode=="Python") {val edit=PythonEditorTools.insertCatalog(m.pythonSource,m.pythonSelectionStart,m.pythonSelectionEnd,source,at);m.editPython(edit.source,edit.cursor)} else m.insert(source,at);close()},modifier=Modifier.weight(1f)) {Text(source,fontSize=12.sp)} } } }
            val hint=when(category) {
                "ODE & transforms" -> "ODE example: dsolve(diff(y(t),t)=y(t),y(t),t). Use t for time and s for Laplace frequency."
                "Vector calculus" -> "Vector functions take a coordinate list, e.g. gradient(x^2+y^2,[x,y])."
                "Matrix & vector" -> "Matrix commands accept a matrix literal such as [[1,2],[3,4]]."
                "Scientific" -> "Numeric trig follows the selected angle unit; explicit π and ° override it."
                "Distributions" -> "normcdf takes one bound, two bounds, or two bounds with μ and σ; the t, χ² and F entries take a bound and their degrees of freedom."
                "Tests & intervals" -> "Tests take a data list or a summary, e.g. ttest(μ0,[...]) or ttest(μ0,x̄,s,n). Append left or right for a one-sided p value."
                "Finance" -> "Rates are per payment period (0.05/12 for 5% a year); add begin for payments at the start of each period."
                else -> "Tap a template, then tap its empty slots to fill them. ↑ selects the enclosing expression; ↓ selects a child."
            }
            Text(hint,fontSize=11.sp)
        }
    }},confirmButton={TextButton(onClick=close) {Text("Done")}})
    if(showHelp)CatalogHelpDialog{showHelp=false}
}

private sealed interface HelpBlock {
    data class Section(val text:String):HelpBlock
    data class Category(val text:String):HelpBlock
    data class Entry(val signature:String,val description:String,val example:String?=null):HelpBlock
    data class Bullet(val text:String):HelpBlock
    data class Body(val text:String):HelpBlock
}

@Composable fun CatalogHelpDialog(close:()->Unit) {
    val context=LocalContext.current
    var document by remember {mutableStateOf<String?>(null)}
    var search by remember {mutableStateOf("")}
    LaunchedEffect(Unit) {
        document=withContext(Dispatchers.IO) {runCatching {context.assets.open("catalog_help.md").bufferedReader().use {it.readText()}}.getOrNull()}
    }
    AlertDialog(onDismissRequest=close,title={Text("Function catalog - help")},text={Column(Modifier.fillMaxWidth()) {
        SearchField(search,"Search"){search=it}
        val loaded=document
        if(loaded==null) Text("Loading the catalog reference...",fontSize=12.sp)
        else SelectionContainer {Column(Modifier.fillMaxWidth().heightIn(max=460.dp).verticalScroll(rememberScrollState())) {HelpDocument(loaded,search)}}
    }},confirmButton={TextButton(onClick=close){Text("Close")}})
}

@Composable private fun HelpDocument(markdown:String,query:String) {
    val c=LocalInstrument.current
    val blocks=remember(markdown,query){parseHelp(markdown,query)}
    if(blocks.isEmpty()) {Text("No entries match \"${query.trim()}\".",fontSize=12.sp,color=c.muted);return}
    blocks.forEach {block->
        when(block) {
            is HelpBlock.Section -> Text(plainHelp(block.text),style=MaterialTheme.typography.titleMedium,modifier=Modifier.padding(top=4.dp,bottom=2.dp))
            is HelpBlock.Category -> Text(plainHelp(block.text),style=MaterialTheme.typography.titleSmall,color=c.accent,fontWeight=FontWeight.SemiBold,modifier=Modifier.padding(top=10.dp))
            is HelpBlock.Entry -> Column(Modifier.fillMaxWidth().padding(vertical=2.dp)) {
                Text(block.signature,fontFamily=FontFamily.Monospace,fontSize=13.sp,color=c.ink)
                Text(plainHelp(block.description),fontSize=12.sp,color=c.muted)
                if(!block.example.isNullOrBlank()) Text("Example: "+plainHelp(block.example),fontFamily=FontFamily.Monospace,fontSize=12.sp,color=c.accent,modifier=Modifier.padding(top=1.dp))
            }
            is HelpBlock.Bullet -> Text("- "+plainHelp(block.text),fontSize=12.sp,color=c.ink,modifier=Modifier.padding(vertical=1.dp))
            is HelpBlock.Body -> Text(plainHelp(block.text),fontSize=12.sp,color=c.ink,modifier=Modifier.padding(vertical=2.dp))
        }
    }
}

private fun plainHelp(text:String)=text.replace("**","").replace("`","")

@Composable private fun SearchField(value:String,label:String,onValue:(String)->Unit) {
    OutlinedTextField(value,onValue,modifier=Modifier.fillMaxWidth(),label={Text(label)},singleLine=true,
        trailingIcon={if(value.isNotEmpty()) IconButton(onClick={onValue("")}){Text("\u2715",fontSize=15.sp)}})
}

private fun parseHelp(markdown:String,query:String):List<HelpBlock> {
    val all=parseHelpEntries(markdown)
    val needle=query.trim().lowercase()
    if(needle.isEmpty())return all
    val result=mutableListOf<HelpBlock>()
    var heading:HelpBlock.Category?=null
    all.forEach {block->
        when(block) {
            is HelpBlock.Section -> {}
            is HelpBlock.Category -> heading=block
            else -> if(block.searchText().contains(needle)) {
                val pending=heading
                if(pending!=null) {result+=pending;heading=null}
                result+=block
            }
        }
    }
    return result
}

private fun HelpBlock.searchText():String=when(this) {
    is HelpBlock.Entry -> "$signature $description ${example.orEmpty()}"
    is HelpBlock.Bullet -> text
    is HelpBlock.Body -> text
    else -> ""
}.lowercase()

private fun parseHelpEntries(markdown:String):List<HelpBlock> {
    val result=mutableListOf<HelpBlock>()
    var lastEntry=-1
    markdown.lineSequence().forEach {raw->
        val line=raw.trimEnd()
        when {
            line.isBlank() -> {}
            line.startsWith("## ") -> {result+=HelpBlock.Category(line.removePrefix("## ").trim());lastEntry=-1}
            line.startsWith("# ") -> {result+=HelpBlock.Section(line.removePrefix("# ").trim());lastEntry=-1}
            line.startsWith("Example:") -> if(lastEntry>=0) {
                val current=result[lastEntry]
                if(current is HelpBlock.Entry)result[lastEntry]=current.copy(example=line.removePrefix("Example:").trim())
            }
            line.startsWith("- ") -> {result+=HelpBlock.Bullet(line.removePrefix("- "));lastEntry=-1}
            line.startsWith("`") -> {
                val end=line.indexOf('`',1)
                if(end>1) {
                    result+=HelpBlock.Entry(line.substring(1,end),line.substring(end+1).trim().trimStart(' ','\t','—','–','-'))
                    lastEntry=result.lastIndex
                } else {result+=HelpBlock.Body(line);lastEntry=-1}
            }
            else -> {result+=HelpBlock.Body(line);lastEntry=-1}
        }
    }
    return result
}
