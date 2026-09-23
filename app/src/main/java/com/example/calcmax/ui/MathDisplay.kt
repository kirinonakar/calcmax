package com.example.calcmax.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.*
import com.example.calcmax.math.Expr
import com.example.calcmax.ui.theme.LocalInstrument
import org.json.JSONObject
import androidx.compose.foundation.shape.RoundedCornerShape
private val LocalMathRootEnd=staticCompositionLocalOf {-1}

/** Native recursive layout, with source spans retained as logical touch targets. */
@Composable fun MathNode(node: JSONObject, size: Float=25f, select: ((Int,Int)->Unit)?=null, selection: IntRange?=null, depth: Int=0) {
    if(depth==0){CompositionLocalProvider(LocalMathRootEnd provides node.optInt("end",-1)){MathNode(node,size,select,selection,1)};return}
    val c=LocalInstrument.current
    val args=node.optJSONArray("args")
    val children=(0 until (args?.length() ?: 0)).map { args!!.getJSONObject(it) }
    val kind=when(val raw=node.optString("kind")){"snapshot_symbol"->"symbol";"constant","float"->"text";"frozen_call"->"function";else->raw}; val value=node.optString("value")
    val start=node.optInt("start",-1); val end=node.optInt("end",-1)
    val selected=selection!=null && start==selection.first && end==selection.last && start!=end
    val modifier=Modifier.then(if(selected) Modifier.background(c.accent.copy(alpha=.18f)) else Modifier).then(if(select!=null && start>=0) Modifier.clickable { select(start,end) } else Modifier)
    @Composable fun label(text: String, scale: Float=1f) { Text(text,color=c.ink,fontSize=(size*scale).sp,fontFamily=FontFamily.Serif,softWrap=false) }
    @Composable fun child(i: Int,scale: Float=1f,bracket: Boolean=false) {
        if(i<children.size) Row(verticalAlignment=Alignment.CenterVertically) {
            if(bracket) label("(",scale)
            MathNode(children[i],(size*scale).coerceAtLeast(12f),select,selection,depth+1)
            if(bracket) label(")",scale)
        }
    }
    if(depth>30) { label("…"); return }
    val fraction=kind=="fraction" || kind=="binary" && value=="/"
    val power=kind=="power" || kind=="binary" && value=="^"
    val root=kind=="root" || kind=="call" && value=="sqrt"
    Box(modifier.padding(horizontal=1.dp)) {
        when {
            fraction -> Row(verticalAlignment=Alignment.CenterVertically) {
              Column(Modifier.width(IntrinsicSize.Max),horizontalAlignment=Alignment.CenterHorizontally) {
                Box(Modifier.padding(horizontal=4.dp,vertical=2.dp)) { child(0,.85f) }
                Box(Modifier.fillMaxWidth().height(1.dp).background(c.ink))
                Box(Modifier.padding(horizontal=4.dp,vertical=2.dp)) { child(1,.85f) }
              }
              if(select!=null)Box(Modifier.width(24.dp).height(48.dp).clickable{select(end,end)}.semantics{contentDescription="After fraction"})
            }
            power -> Row(verticalAlignment=Alignment.Top) { Box(Modifier.padding(top=8.dp)) { child(0,bracket=children.firstOrNull()?.optString("kind") in listOf("sum","product","unary")) }; child(1,.65f) }
            root -> Row(verticalAlignment=Alignment.CenterVertically) { label("√",1.35f); Column(Modifier.width(IntrinsicSize.Max)) { Box(Modifier.fillMaxWidth().height(1.dp).background(c.ink)); child(0) } }
            kind=="matrix" || kind=="list" && children.firstOrNull()?.optString("kind")=="list" -> Row(verticalAlignment=Alignment.CenterVertically) {
                label("[",1.8f)
                Column(verticalArrangement=Arrangement.spacedBy(5.dp)) { children.forEach { row ->
                    Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) { val cells=row.getJSONArray("args"); for(i in 0 until cells.length()) MathNode(cells.getJSONObject(i),size*.8f,select,selection,depth+1) }
                } }
                label("]",1.8f)
            }
            kind=="rows" -> Column { children.forEach { row -> Row(verticalAlignment=Alignment.CenterVertically) { label(row.optString("value")+": ",.7f); MathNode(row.getJSONArray("args").getJSONObject(0),size*.8f,select,selection,depth+1) } } }
            kind=="hole" -> Text("□",Modifier.sizeIn(minWidth=24.dp,minHeight=36.dp).semantics { contentDescription="Empty expression slot" },fontSize=size.sp,color=c.accent)
            kind=="restricted" -> child(0)
            kind=="answer" -> Box(Modifier.border(1.dp,c.muted,RoundedCornerShape(4.dp)).padding(horizontal=6.dp,vertical=3.dp).semantics{contentDescription="Previous answer"}){if(children.isNotEmpty())MathNode(children[0],size*.9f,depth=depth+1)}
            kind=="quantity" -> Row(verticalAlignment=Alignment.CenterVertically) {child(0);label(" $value",.7f)}
            kind=="dms" -> Row(verticalAlignment=Alignment.CenterVertically){child(0);label("°");child(1);label("′");child(2);label("″")}
            kind=="call" && value=="eng" -> child(0)
            kind=="symbol" && value.contains('_') -> Row(verticalAlignment=Alignment.CenterVertically) {label(value.substringBefore('_'));Box(Modifier.padding(top=15.dp)){label(value.substringAfter('_'),.55f)}}
            kind=="call" && value=="mixed" -> Row(verticalAlignment=Alignment.CenterVertically) {
                child(0)
                Column(Modifier.width(IntrinsicSize.Max),horizontalAlignment=Alignment.CenterHorizontally) {child(1,.8f);Box(Modifier.fillMaxWidth().height(1.dp).background(c.ink));child(2,.8f)}
            }
            kind in listOf("number","symbol","text") -> {
                val shown=when(value){"pi"->"π";"oo"->"∞";"I"->"i";else->value}
                val cursor=selection?.first ?: -1
                val caret=select!=null&&selection?.last==cursor&&start>=0&&cursor in start..end&&(cursor<end||end!=LocalMathRootEnd.current)
                if(caret){val offset=if(end==start)0 else ((cursor-start)*shown.length/(end-start)).coerceIn(0,shown.length);label(shown.take(offset)+"│"+shown.drop(offset))}else label(shown)
            }
            kind=="group" -> Row(verticalAlignment=Alignment.CenterVertically) { label("("); child(0); label(")") }
            kind=="unary" -> Row(verticalAlignment=Alignment.CenterVertically) { label(value); child(0) }
            kind in listOf("call","function") && value=="factorial" -> Row(verticalAlignment=Alignment.CenterVertically){child(0,bracket=children.firstOrNull()?.optString("kind") in listOf("binary","sum","product"));label("!")}
            kind in listOf("call","function") && value=="degree" -> Row(verticalAlignment=Alignment.CenterVertically){child(0);label("°")}
            kind in listOf("call","function") && value in listOf("rad","gradian") -> Row(verticalAlignment=Alignment.CenterVertically){child(0);label(if(value=="rad")"ʳ" else "ᵍ")}
            kind in listOf("call","function") && value=="percent" -> Row(verticalAlignment=Alignment.CenterVertically){child(0);label("%")}
            kind in listOf("call","function") && value in listOf("abs","Abs") -> Row(verticalAlignment=Alignment.CenterVertically) { label("│");child(0);label("│") }
            kind=="call" && value=="nthroot" -> Row(verticalAlignment=Alignment.Top) { child(1,.55f);label("√",1.3f);child(0) }
            kind=="call" && value=="log" && children.size>1 -> Row(verticalAlignment=Alignment.CenterVertically) { label("log");Box(Modifier.padding(top=14.dp)) {child(1,.55f)};label("(");child(0);label(")") }
            kind in listOf("call","function") && value in listOf("piecewise","Piecewise") -> Row(verticalAlignment=Alignment.CenterVertically) {
                label("{",2f)
                Column { children.indices.forEach { i->child(i,.8f) } }
            }
            kind=="call" && value in listOf("integrate","diff","nderivative","limit","sum","product") -> Row(verticalAlignment=Alignment.CenterVertically) {
                when(value) {
                    "diff","nderivative" -> Column(Modifier.width(IntrinsicSize.Max),horizontalAlignment=Alignment.CenterHorizontally) {
                        Row {label("d",.8f);if(value=="diff"&&children.size>2)child(2,.5f)}
                        Box(Modifier.fillMaxWidth().height(1.dp).background(c.ink))
                        Row {label("d",.8f);child(1,.8f);if(value=="diff"&&children.size>2)child(2,.5f)}
                    }
                    "limit" -> Column(horizontalAlignment=Alignment.CenterHorizontally) {label("lim");Row {child(1,.55f);if(children.size>2){label("→",.55f);child(2,.55f)}}}
                    else -> Column(horizontalAlignment=Alignment.CenterHorizontally) {
                        if(children.size>3)child(3,.5f)
                        label(when(value){"integrate"->"∫";"sum"->"Σ";else->"Π"},1.4f)
                        if(children.size>2)Row {if(value!="integrate"){child(1,.5f);label("=",.5f)};child(2,.5f)}
                    }
                }
                child(0,.9f,bracket=value in listOf("diff","nderivative"))
                if(value=="integrate") {label(" d",.8f);child(1,.8f)}
                if(value=="nderivative") {label("│",1.3f);Box(Modifier.padding(top=16.dp)){Row{child(1,.55f);label("=",.55f);child(2,.55f)}}}
            }
            else -> Row(verticalAlignment=Alignment.CenterVertically) {
                val wrap=kind in listOf("list","set","call","function")
                if(kind in listOf("call","function")) label(value,.85f)
                if(wrap) label(if(kind=="list") "[" else if(kind=="set") "{" else "(")
                children.forEachIndexed { i,_ ->
                    val negativeTerm=kind=="sum" && children[i].optString("kind")=="unary" && children[i].optString("value")=="-"
                    if(negativeTerm) {
                        label(if(i==0)"−" else " − ",.85f)
                        MathNode(children[i].getJSONArray("args").getJSONObject(0),size,select,selection,depth+1)
                    } else {
                    if(i>0) label(when { kind=="sum" -> " + "; kind=="product" -> " · "; kind=="binary" || kind=="relation" -> " ${when(value) { "*"->"×"; "-"->"−"; else->value }} "; else -> ", " },.85f)
                    child(i,bracket=kind=="product" && children[i].optString("kind")=="sum")
                    }
                }
                if(wrap) label(if(kind=="list") "]" else if(kind=="set") "}" else ")")
                if(children.isEmpty()) label(value)
            }
        }
    }
}
