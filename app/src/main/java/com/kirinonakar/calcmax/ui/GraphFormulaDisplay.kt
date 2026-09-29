package com.kirinonakar.calcmax.ui

import com.kirinonakar.calcmax.math.Expr
import com.kirinonakar.calcmax.math.Parser
import com.kirinonakar.calcmax.calculator.ResultDisplayMode
import com.kirinonakar.calcmax.calculator.FunctionTransfer
import java.math.BigDecimal
import java.math.RoundingMode
import org.json.JSONObject

/** Round numeric fractions in graph captions without changing the expressions used to plot. */
internal fun decimalFractionFormulaTree(source:String, fractionDigits:Int=3):JSONObject? = runCatching {
    JSONObject(decimalizeRegressionFractions(Parser(source).parse(),fractionDigits).json())
}.getOrNull()

internal fun regressionFormulaDisplayTree(source:String, displayDigits:Int):JSONObject? =
    decimalFractionFormulaTree(source,displayDigits)?.let {
        ResultDisplayFormat.formatTree(it,ResultDisplayMode.OFF,false,maxFractionDigits=displayDigits)
    }

/** Make a parseable graph expression whose coefficients match the displayed fit. */
internal fun regressionFormulaGraphSource(source:String, displayDigits:Int):String? = runCatching {
    val parsed=Parser(source.replace("**","^")).parse()
    val decimalized=decimalizeRegressionFractions(parsed,displayDigits)
    fun rounded(node:Expr):Expr {
        val children=node.args.map(::rounded)
        if(node.kind!="number")return node.copy(args=children)
        val token=node.value
        val exponentAt=token.indexOfAny(charArrayOf('e','E'))
        val significand=if(exponentAt<0)token else token.substring(0,exponentAt)
        if(!significand.contains('.'))return node.copy(args=children)
        val value=significand.toBigDecimal().setScale(displayDigits.coerceIn(0,200),RoundingMode.HALF_UP)
            .stripTrailingZeros().toPlainString()
        return node.copy(value=value+if(exponentAt<0)"" else token.substring(exponentAt),args=children)
    }
    val graphSource=FunctionTransfer.editableSource(JSONObject(rounded(decimalized).json()))
    Parser(graphSource).parse()
    graphSource
}.getOrNull()

private fun decimalizeRegressionFractions(expression:Expr, fractionDigits:Int):Expr {
    val children=expression.args.map {decimalizeRegressionFractions(it,fractionDigits)}
    val updated=expression.copy(args=children)
    if(updated.kind!="binary" || updated.value!="/" || children.size!=2)return updated
    val denominator=children[1].numberValue() ?: return updated
    if(denominator.signum()==0)return updated
    val (coefficient,remainder)=children[0].numericFactor()
    val decimal=coefficient.divide(denominator,fractionDigits.coerceIn(0,200),RoundingMode.HALF_UP)
    val number=Expr("number",decimal.abs().toPlainString())
    val result=if(remainder==null)number else Expr("binary","*",listOf(number,remainder))
    return if(decimal.signum()<0)Expr("unary","-",listOf(result)) else result
}

private fun Expr.numberValue():BigDecimal? = when {
    kind=="number"->value.toBigDecimalOrNull()
    kind=="unary" && value=="-" && args.size==1->args[0].numberValue()?.negate()
    else->null
}

private fun Expr.numericFactor():Pair<BigDecimal,Expr?> {
    numberValue()?.let {return it to null}
    if(kind=="unary" && value=="-" && args.size==1) {
        val (coefficient,remainder)=args[0].numericFactor()
        return coefficient.negate() to remainder
    }
    if(kind=="binary" && value=="*" && args.size==2) {
        args[0].numberValue()?.let {return it to args[1]}
    }
    return BigDecimal.ONE to this
}
