package com.kirinonakar.symvacas.ui

import com.kirinonakar.symvacas.calculator.ResultDisplayMode
import org.json.JSONArray
import org.json.JSONObject
import java.math.RoundingMode
import kotlin.math.abs

/** Formatting helpers used only for the answer view; the engine result remains lossless. */
object ResultDisplayFormat {
    private const val MAX_DISPLAY_DIGITS = 40_000
    private val scientificLiteral = Regex("([+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+))[eE]([+-]?[0-9]+)")

    data class NotationParts(val mantissa: String, val exponent: Int)

    /** Convert a displayed decimal result to the same compact tree used by the engine. */
    fun dmsTree(value: String): JSONObject? = runCatching {
        val number=value.trim().toBigDecimalOrNull() ?: return@runCatching null
        val negative=number.signum()<0
        val magnitude=number.abs()
        var degrees=magnitude.setScale(0,RoundingMode.FLOOR)
        val minuteValue=magnitude.subtract(degrees).multiply(java.math.BigDecimal.valueOf(60L))
        var minutes=minuteValue.setScale(0,RoundingMode.FLOOR)
        var seconds=minuteValue.subtract(minutes).multiply(java.math.BigDecimal.valueOf(60L))
        val sixty=java.math.BigDecimal.valueOf(60L)
        if(seconds.compareTo(sixty)>=0){seconds=seconds.subtract(sixty);minutes=minutes.add(java.math.BigDecimal.valueOf(1L))}
        if(minutes.compareTo(sixty)>=0){minutes=minutes.subtract(sixty);degrees=degrees.add(java.math.BigDecimal.valueOf(1L))}
        fun text(part:java.math.BigDecimal):String=part.stripTrailingZeros().toPlainString()
        val degreeText=when {
            negative&&degrees.signum()==0 -> "-0"
            negative -> "-${text(degrees)}"
            else -> text(degrees)
        }
        JSONObject().put("kind","dms").put("args",JSONArray()
            .put(JSONObject().put("kind","number").put("value",degreeText))
            .put(JSONObject().put("kind","number").put("value",text(minutes)))
            .put(JSONObject().put("kind","number").put("value",text(seconds))))
    }.getOrNull()

    fun formatTree(tree: JSONObject, displayMode: ResultDisplayMode, grouping: Boolean, engineeringShift: Int = 0, showZeroExponent: Boolean = false, maxFractionDigits: Int = 30): JSONObject {
        return formatNode(tree, displayMode, grouping, engineeringShift, showZeroExponent, maxFractionDigits, allowNotation = true)
    }

    fun formatText(text: String, displayMode: ResultDisplayMode, grouping: Boolean, engineeringShift: Int = 0, showZeroExponent: Boolean = false, maxFractionDigits: Int = 30): String {
        if (text.isBlank()) return text
        if (displayMode != ResultDisplayMode.OFF) {
            notationParts(text, displayMode, engineeringShift, maxFractionDigits)?.let { parts ->
                if (parts.exponent != 0 || showZeroExponent) {
                    val mantissa = if (grouping) groupNumber(parts.mantissa) ?: parts.mantissa else parts.mantissa
                    return "$mantissa×10^${parts.exponent}"
                }
            }
        }
        scientificParts(text)?.let { parts ->
            val rounded = roundFraction(parts.mantissa, maxFractionDigits)
            val mantissa = if (grouping) groupNumber(rounded) ?: rounded else rounded
            return "$mantissa×10^${parts.exponent}"
        }
        val rounded = roundFraction(text, maxFractionDigits)
        return if (grouping) groupNumber(rounded) ?: rounded else rounded
    }

    private fun formatNode(
        node: JSONObject,
        displayMode: ResultDisplayMode,
        grouping: Boolean,
        engineeringShift: Int,
        showZeroExponent: Boolean,
        maxFractionDigits: Int,
        allowNotation: Boolean
    ): JSONObject {
        val kind = node.optString("kind")
        val value = node.optString("value")
        val numericLeaf = kind in setOf("number", "float", "text")
        if (displayMode != ResultDisplayMode.OFF && allowNotation && numericLeaf) {
            notationParts(value, displayMode, engineeringShift, maxFractionDigits)?.let { parts ->
                if (parts.exponent != 0 || showZeroExponent) {
                    val mantissa = if (grouping) groupNumber(parts.mantissa) ?: parts.mantissa else parts.mantissa
                    return notationNode(mantissa, parts.exponent)
                }
            }
        }
        if (allowNotation && numericLeaf) scientificParts(value)?.let { parts ->
            val rounded = roundFraction(parts.mantissa, maxFractionDigits)
            val mantissa = if (grouping) groupNumber(rounded) ?: rounded else rounded
            return notationNode(mantissa, parts.exponent)
        }

        val copy = JSONObject(node.toString())
        if (numericLeaf) copy.put("value", roundFraction(value, maxFractionDigits))
        if (grouping && numericLeaf) {
            groupNumber(copy.optString("value"))?.let { copy.put("value", it) }
        }
        val args = copy.optJSONArray("args")
        if (args != null) {
            val formatted = JSONArray()
            for (index in 0 until args.length()) {
                val child = args.opt(index)
                val childNotation = allowNotation && (displayMode == ResultDisplayMode.OFF || kind == "unary" && args.length() == 1)
                formatted.put(if (child is JSONObject) formatNode(child, displayMode, grouping, engineeringShift, showZeroExponent, maxFractionDigits, childNotation) else child)
            }
            copy.put("args", formatted)
        }
        return copy
    }

    private fun notationNode(mantissa: String, exponent: Int): JSONObject {
        val power = JSONObject()
            .put("kind", "power")
            .put("value", "")
            .put(
                "args",
                JSONArray()
                    .put(JSONObject().put("kind", "text").put("value", "10"))
                    .put(JSONObject().put("kind", "text").put("value", exponent.toString()))
            )
        return JSONObject()
            .put("kind", "binary")
            .put("value", "*")
            .put("args", JSONArray().put(JSONObject().put("kind", "number").put("value", mantissa)).put(power))
    }

    private fun scientificParts(value: String): NotationParts? {
        val match = scientificLiteral.matchEntire(value.trim()) ?: return null
        val exponent = match.groupValues[2].toIntOrNull() ?: return null
        if (abs(exponent) > MAX_DISPLAY_DIGITS) return null
        return NotationParts(match.groupValues[1], exponent)
    }

    /** Display digits count places after the decimal point, without padding zeros. */
    private fun roundFraction(value: String, digits: Int): String {
        if (!value.contains('.') || value.length > MAX_DISPLAY_DIGITS || value.contains('e', ignoreCase = true)) return value
        val number = value.trim().toBigDecimalOrNull() ?: return value
        return runCatching {
            val rounded = number.setScale(digits.coerceIn(0, MAX_DISPLAY_DIGITS), RoundingMode.HALF_UP)
                .stripTrailingZeros().toPlainString()
            if (rounded.length > MAX_DISPLAY_DIGITS) value else rounded
        }.getOrDefault(value)
    }

    private fun notationParts(value: String, displayMode: ResultDisplayMode, engineeringShift: Int, maxFractionDigits: Int): NotationParts? {
        if (displayMode == ResultDisplayMode.OFF) return null
        val number = value.trim().toBigDecimalOrNull() ?: return null
        if (number.signum() == 0) return null

        return runCatching {
            val magnitude = number.abs()
            val floorLog10 = magnitude.precision() - magnitude.scale() - 1
            val baseExponent = if (displayMode == ResultDisplayMode.ENGINEERING) {
                val remainder = ((floorLog10 % 3) + 3) % 3
                floorLog10 - remainder
            } else {
                floorLog10
            }
            val exponent = baseExponent + engineeringShift
            if (abs(exponent) > MAX_DISPLAY_DIGITS) return null
            val scale = maxFractionDigits.coerceIn(0, MAX_DISPLAY_DIGITS)
            val mantissa = number.scaleByPowerOfTen(-exponent).setScale(scale, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
            if (mantissa.length > MAX_DISPLAY_DIGITS) null
            else NotationParts(mantissa, exponent)
        }.getOrNull()
    }

    private fun groupNumber(value: String): String? {
        val raw = value.trim()
        if (raw.isEmpty()) return null
        val sign = when {
            raw.startsWith("+") -> "+"
            raw.startsWith("-") -> "-"
            else -> ""
        }
        val body = raw.drop(if (sign.isEmpty()) 0 else 1)
        val number = body.toBigDecimalOrNull() ?: return null
        val plain = number.toPlainString()
        if (plain.length > MAX_DISPLAY_DIGITS) return null
        val dot = plain.indexOf('.')
        val integer = if (dot >= 0) plain.substring(0, dot) else plain
        val fraction = if (dot >= 0) plain.substring(dot) else ""
        if (integer.length <= 3) return sign + plain
        val grouped = integer.reversed().chunked(3).joinToString(",").reversed()
        return sign + grouped + fraction
    }

    /** The tree the answer view renders for these settings; the display and the Copy text share it. */
    fun resultTree(result:JSONObject,decimal:Boolean,mixed:Boolean,engineeringConversion:Boolean,dmsDisplay:Boolean,dmsConversion:Boolean):JSONObject? {
        val useDecimal=decimal||engineeringConversion
        val wantDms=dmsDisplay&&!engineeringConversion
        var tree=when {
            wantDms&&result.optBoolean("dms")->result.optJSONObject(if(decimal)"decimalTree" else "tree")
            wantDms->dmsTree(result.optString("decimal"))
            result.optBoolean("dms")->result.optJSONObject("numericDecimalTree")
                ?: result.optJSONObject("numericTree")
                ?: result.optJSONObject(if(decimal)"decimalTree" else "tree")
            dmsConversion->result.optJSONObject("decimalTree") ?: result.optJSONObject("tree")
            else->result.optJSONObject(if(useDecimal)"decimalTree" else "tree") ?: result.optJSONObject("tree")
        }
        if(mixed&&!useDecimal&&tree?.optString("kind")=="fraction") {
            val fraction=tree
            tree=runCatching {
                val numerator=fraction!!.getJSONArray("args").getJSONObject(0).getString("value").toBigInteger()
                val denominator=fraction.getJSONArray("args").getJSONObject(1).getString("value").toBigInteger()
                val parts=numerator.abs().divideAndRemainder(denominator)
                if(parts[0].signum()==0)fraction else JSONObject().put("kind","call").put("value","mixed").put("args",JSONArray(listOf(parts[0]*numerator.signum().toBigInteger(),parts[1],denominator).map {JSONObject().put("kind","number").put("value",it.toString())}))
            }.getOrDefault(tree)
        }
        return tree
    }

    /** Copy text for the answer view: the digits, grouping and notation the display shows. */
    fun resultText(result:JSONObject,decimal:Boolean,mixed:Boolean,displayMode:ResultDisplayMode,thousandsSeparator:Boolean,engineeringConversion:Boolean,engineeringShift:Int,dmsDisplay:Boolean,dmsConversion:Boolean,displayDigits:Int):String {
        val useDecimal=decimal||engineeringConversion
        val effectiveMode=if(engineeringConversion)ResultDisplayMode.ENGINEERING else displayMode
        val shift=if(engineeringConversion)engineeringShift else 0
        val compact=result.optString("exact").length>20_000||largeHistoryTree(result.optJSONObject(if(decimal)"decimalTree" else "tree"))
        val formatted=if(compact)null else resultTree(result,decimal,mixed,engineeringConversion,dmsDisplay,dmsConversion)?.let{formatTree(it,effectiveMode,thousandsSeparator,shift,engineeringConversion,displayDigits)}
        return formatted?.let(::treeText) ?: formatText(result.optString(if(useDecimal)"decimal" else "exact"),effectiveMode,thousandsSeparator,shift,engineeringConversion,displayDigits)
    }

    /** Plain text for a formatted display tree; null when the tree has no faithful text form. */
    private fun treeText(node:JSONObject):String? {
        val kind=node.optString("kind")
        val value=node.optString("value")
        val args=node.optJSONArray("args")
        fun part(index:Int):String? = args?.optJSONObject(index)?.let(::treeText)
        return when(kind) {
            "number","float","text","constant","symbol" -> value
            "fraction" -> ratioText(node)
            "binary" -> when(value) {
                "/" -> ratioText(node)
                "*" -> notationText(node)
                else -> null
            }
            "product" -> notationText(node)
            "unary" -> if(value=="-")part(0)?.let{"-$it"} else null
            "call" -> if(value=="mixed")mixedText(node) else null
            "dms","sexagesimal" -> dmsText(node)
            "relation" -> relationText(node)
            "group" -> part(0)?.let{"($it)"}
            "quantity" -> part(0)?.let{if(value.isBlank())it else "$it $value"}
            "list" -> collectionText(node,"[","]")
            "set" -> collectionText(node,"{","}")
            "tuple" -> collectionText(node,"(",")")
            "matrix" -> collectionText(node,"[","]")
            "row" -> part(0)?.let{"$value: $it"}
            "rows" -> args?.let{array->
                val lines=(0 until array.length()).map{array.optJSONObject(it)?.let(::treeText)}
                if(lines.any{it==null})null else lines.joinToString("\n")
            }
            else -> null
        }
    }

    private fun ratioText(node:JSONObject):String? {
        val args=node.optJSONArray("args") ?: return null
        if(args.length()!=2)return null
        val numerator=args.optJSONObject(0)?.let(::treeText) ?: return null
        val denominator=args.optJSONObject(1)?.let(::treeText) ?: return null
        return "$numerator/$denominator"
    }

    private fun notationText(node:JSONObject):String? {
        val args=node.optJSONArray("args") ?: return null
        if(args.length()!=2)return null
        val power=args.optJSONObject(1) ?: return null
        if(power.optString("kind")!="power")return null
        val powerArgs=power.optJSONArray("args") ?: return null
        if(powerArgs.length()!=2||powerArgs.optJSONObject(0)?.optString("value")!="10")return null
        val mantissa=args.optJSONObject(0)?.let(::treeText) ?: return null
        val exponent=powerArgs.optJSONObject(1)?.optString("value") ?: return null
        return "$mantissa×10^$exponent"
    }

    private fun dmsText(node:JSONObject):String? {
        val args=node.optJSONArray("args") ?: return null
        val markers=listOf("°","′","″")
        val parts=(0 until args.length()).map {index->(args.optJSONObject(index)?.let(::treeText) ?: return null)+markers.getOrElse(index){""}}
        return parts.takeIf{it.isNotEmpty()}?.joinToString("")
    }

    private fun mixedText(node:JSONObject):String? {
        val args=node.optJSONArray("args") ?: return null
        if(args.length()!=3)return null
        val whole=args.optJSONObject(0)?.let(::treeText) ?: return null
        val numerator=args.optJSONObject(1)?.let(::treeText) ?: return null
        val denominator=args.optJSONObject(2)?.let(::treeText) ?: return null
        return "$whole $numerator/$denominator"
    }

    private fun relationText(node:JSONObject):String? {
        val args=node.optJSONArray("args") ?: return null
        if(args.length()!=2)return null
        val left=args.optJSONObject(0)?.let(::treeText) ?: return null
        val right=args.optJSONObject(1)?.let(::treeText) ?: return null
        val operator=when(node.optString("value")){"!="->"≠";"<="->"≤";">="->"≥";else->node.optString("value")}
        return "$left $operator $right"
    }

    private fun collectionText(node:JSONObject,open:String,close:String):String? {
        val args=node.optJSONArray("args") ?: return null
        val parts=(0 until args.length()).map {index->args.optJSONObject(index)?.let(::treeText) ?: return null}
        return open+parts.joinToString(", ")+close
    }
}
