package com.example.calcmax.ui

import com.example.calcmax.calculator.ResultDisplayMode
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs

/** Formatting helpers used only for the answer view; the engine result remains lossless. */
object ResultDisplayFormat {
    private const val MAX_DISPLAY_DIGITS = 40_000

    data class NotationParts(val mantissa: String, val exponent: Int)

    fun formatTree(tree: JSONObject, displayMode: ResultDisplayMode, grouping: Boolean, engineeringShift: Int = 0, showZeroExponent: Boolean = false): JSONObject {
        if (displayMode == ResultDisplayMode.OFF && !grouping) return tree
        return formatNode(tree, displayMode, grouping, engineeringShift, showZeroExponent, allowNotation = true)
    }

    fun formatText(text: String, displayMode: ResultDisplayMode, grouping: Boolean, engineeringShift: Int = 0, showZeroExponent: Boolean = false): String {
        if (text.isBlank()) return text
        if (displayMode != ResultDisplayMode.OFF) {
            notationParts(text, displayMode, engineeringShift)?.let { parts ->
                if (parts.exponent != 0 || showZeroExponent) {
                    val mantissa = if (grouping) groupNumber(parts.mantissa) ?: parts.mantissa else parts.mantissa
                    return "$mantissa×10^${parts.exponent}"
                }
            }
        }
        return if (grouping) groupNumber(text) ?: text else text
    }

    private fun formatNode(
        node: JSONObject,
        displayMode: ResultDisplayMode,
        grouping: Boolean,
        engineeringShift: Int,
        showZeroExponent: Boolean,
        allowNotation: Boolean
    ): JSONObject {
        val kind = node.optString("kind")
        val value = node.optString("value")
        val numericLeaf = kind in setOf("number", "float", "text")
        if (displayMode != ResultDisplayMode.OFF && allowNotation && numericLeaf) {
            notationParts(value, displayMode, engineeringShift)?.let { parts ->
                if (parts.exponent != 0 || showZeroExponent) {
                    val mantissa = if (grouping) groupNumber(parts.mantissa) ?: parts.mantissa else parts.mantissa
                    return notationNode(mantissa, parts.exponent)
                }
            }
        }

        val copy = JSONObject(node.toString())
        if (grouping && numericLeaf) {
            groupNumber(value)?.let { copy.put("value", it) }
        }
        val args = copy.optJSONArray("args")
        if (args != null) {
            val formatted = JSONArray()
            for (index in 0 until args.length()) {
                val child = args.opt(index)
                val childNotation = displayMode != ResultDisplayMode.OFF && allowNotation && kind == "unary" && args.length() == 1
                formatted.put(if (child is JSONObject) formatNode(child, displayMode, grouping, engineeringShift, showZeroExponent, childNotation) else child)
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
            .put("kind", "product")
            .put("value", "")
            .put("args", JSONArray().put(JSONObject().put("kind", "number").put("value", mantissa)).put(power))
    }

    private fun notationParts(value: String, displayMode: ResultDisplayMode, engineeringShift: Int): NotationParts? {
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
            val mantissa = number.scaleByPowerOfTen(-exponent).stripTrailingZeros().toPlainString()
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
}
