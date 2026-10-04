package com.kirinonakar.calcmax.ui

import com.kirinonakar.calcmax.calculator.HistoryEntry
import com.kirinonakar.calcmax.math.Parser
import org.json.JSONObject

internal data class HistoryMathPreview(val input: JSONObject?, val response: JSONObject?)

/** Bound work before parsing old records, then validate trees before composing math layouts. */
internal fun HistoryEntry.mathPreview(): HistoryMathPreview {
    val input = if (source.length <= 800) {
        historyObject(inputTree)?.takeIf(::safeHistoryTree)
            ?: historyExpression(source)
    } else null
    val savedResponse = historyObject(response)?.takeIf { it.has("exact") || it.has("tree") }
    val legacyExpression = exact.trim().let {
        if (it.startsWith("Matrix(") && it.endsWith(")")) it.substring(7, it.length - 1) else it
    }
    val result = if (response.length > 20_000) null else {
        savedResponse ?: historyExpression(legacyExpression)?.let {
            JSONObject().put("exact", exact).put("decimal", decimal).put("tree", it)
        }
    }
    // ResultMath can fall back from decimalTree to tree, or use the numeric DMS trees.
    val safeResult = result?.takeIf(::safeHistoryResponse)
    return HistoryMathPreview(input, safeResult)
}

private fun historyExpression(source: String): JSONObject? {
    if (source.length > 800 || !historyNestingWithinLimit(source)) return null
    return runCatching { JSONObject(Parser(source, true).parse().json()) }
        .getOrNull()?.takeIf(::safeHistoryTree)
}

private fun historyObject(source: String): JSONObject? {
    if (source.length !in 2..20_000 || !historyNestingWithinLimit(source)) return null
    return runCatching { JSONObject(source) }.getOrNull()
}

/** Avoid recursive JSON/parser stack growth; brackets inside quoted values are ordinary text. */
private fun historyNestingWithinLimit(source: String): Boolean {
    var depth = 0
    var quoted = false
    var escaped = false
    for (char in source) {
        if (quoted) {
            if (escaped) escaped = false
            else if (char == '\\') escaped = true
            else if (char == '"') quoted = false
        } else when (char) {
            '"' -> quoted = true
            '{', '[', '(' -> if (++depth > 64) return false
            '}', ']', ')' -> depth--
        }
    }
    return true
}

internal fun safeHistoryResponse(value: JSONObject): Boolean =
    listOf("tree", "decimalTree", "numericTree", "numericDecimalTree").all { key ->
        !value.has(key) || value.isNull(key) || value.optJSONObject(key)?.let(::safeHistoryTree) == true
    }

internal fun safeHistoryTree(root: JSONObject): Boolean {
    val pending = ArrayDeque<Pair<JSONObject, Int>>()
    pending.add(root to 0)
    var count = 0
    while (pending.isNotEmpty()) {
        val (node, depth) = pending.removeLast()
        if (++count > 120 || depth > 32 || node.optString("kind").isBlank()) return false
        if (!node.has("args")) continue
        val args = node.optJSONArray("args") ?: return false
        if (args.length() > 120) return false
        for (index in 0 until args.length()) {
            val child = args.optJSONObject(index) ?: return false
            pending.add(child to depth + 1)
        }
    }
    return true
}

internal fun String.historyPreview(): String {
    var end = 0
    var count = 0
    while (end < length && count < 50) {
        end += Character.charCount(codePointAt(end))
        count++
    }
    return if (end < length) substring(0, offsetByCodePoints(0, 49)) + "…" else this
}
