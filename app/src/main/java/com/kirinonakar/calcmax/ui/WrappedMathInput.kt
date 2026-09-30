package com.kirinonakar.calcmax.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.kirinonakar.calcmax.math.Expr
import com.kirinonakar.calcmax.math.Lexer
import org.json.JSONObject

/** Split only outer infix operations; fractions, powers and functions stay mathematical units. */
internal fun inputMathParts(tree:JSONObject?,source:String):List<JSONObject> {
    val parts=mutableListOf<JSONObject>()
    fun append(node:JSONObject) {
        val kind=node.optString("kind");val value=node.optString("value")
        val args=node.optJSONArray("args")
        val coefficient=kind=="binary"&&value=="*"&&args?.length()==2&&args.getJSONObject(0).optString("kind")=="number"&&args.getJSONObject(1).optString("kind")=="symbol"
        val infix=kind=="binary"&&value !in listOf("^","/")&&!coefficient||kind=="relation"
        if(infix&&args?.length()==2) {
            val left=args.getJSONObject(0);val right=args.getJSONObject(1)
            append(left)
            val op=when(value){"*"->if(node.optString("displayOperator")=="∘")"" else "×";"=="->"=";"!="->"≠";"<="->"≤";">="->"≥";"-"->"−";else->value}
            if(op.isNotEmpty())parts+=JSONObject(Expr("text"," $op ",start=left.optInt("end"),end=right.optInt("start")).json())
            append(right)
        }else if(kind=="number"&&value.length>18&&!value.contains('e',true)&&source.substring(node.optInt("start"),node.optInt("end"))==value) {
            value.forEachIndexed {i,ch->parts+=JSONObject(Expr("number",ch.toString(),start=node.optInt("start")+i,end=node.optInt("start")+i+1).json())}
        }else parts+=node
    }
    if(tree!=null)append(tree)else runCatching {Lexer.scan(source)}.getOrElse {source.mapIndexed {i,ch->com.kirinonakar.calcmax.math.Token(ch.toString(),i,i+1)}}.filter{it.text.isNotEmpty()}.forEach {token->
        parts+=JSONObject(Expr("text",source.substring(token.start,token.end).replace("*","×"),start=token.start,end=token.end).json())
    }
    return parts
}

@Composable internal fun MathInputLayout(tree:JSONObject?,source:String,size:Float,cursor:Int,target:IntRange?,select:(Int,Int)->Unit,selection:IntRange,after:()->Unit) {
    val parts=remember(tree,source){inputMathParts(tree,source)}
    fun hasRange(node:JSONObject,range:IntRange):Boolean {
        if(node.optInt("start")==range.first&&node.optInt("end")==range.last)return true
        val args=node.optJSONArray("args")
        return (0 until (args?.length() ?: 0)).any {hasRange(args!!.getJSONObject(it),range)}
    }
    // A cursor on a flattened parent must follow its displayed child instead.
    val caretTarget=if(target==null||parts.any{hasRange(it,target)})target else parts.filter{cursor in it.optInt("start")..it.optInt("end")}.minByOrNull{if(cursor==it.optInt("end"))0 else 1}?.let{it.optInt("start")..it.optInt("end")}
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val viewportWidth=maxWidth
        @Composable fun RowScope.renderParts() {
            CompositionLocalProvider(LocalMathCursorTarget provides caretTarget) {
                if(parts.isEmpty())MathText("│",size,blink=true)
                parts.forEachIndexed {index,part->
                    // Only a finite wrapped line may contain its own scroller.
                    // The unwrapped row already lives inside the input's horizontal scroller.
                    val viewport=if(viewportWidth==androidx.compose.ui.unit.Dp.Infinity)Modifier else Modifier.widthIn(max=viewportWidth).horizontalScroll(rememberScrollState())
                    Box(viewport.alignBy(MathAxis).testTag("input-math-part-$index")) {
                        // Outer empty input and pending infix operands show only the caret.
                        MathNode(part,size,select=select,selection=selection,operandHole=true)
                    }
                }
            }
            Box(Modifier.width(32.dp).height(48.dp).clickable(onClick=after))
        }
        if(viewportWidth==androidx.compose.ui.unit.Dp.Infinity)Row {renderParts()}
        else FlowRow(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(6.dp)) {renderParts()}
    }
}
