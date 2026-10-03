package com.kirinonakar.calcmax.calculator

import org.json.JSONArray

internal fun normalizeGraphColors(colors:List<String?>):List<String?> = List(6) {index->
    colors.getOrNull(index)?.takeIf {it.matches(Regex("#[0-9a-fA-F]{6}"))}?.lowercase()
}

internal fun loadGraphColors(source:String?):List<String?> = runCatching {
    val array=JSONArray(source ?: "[]")
    normalizeGraphColors(List(6) {array.optString(it).takeIf(String::isNotBlank)})
}.getOrDefault(List(6) {null})
