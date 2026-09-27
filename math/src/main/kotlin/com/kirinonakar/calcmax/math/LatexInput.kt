package com.kirinonakar.calcmax.math

/** Converts pasted, supported LaTeX math into the calculator's editable expression syntax. */
object LatexInput {
    private val integral = Regex("""\\int_\{([^{}]+)\}\^\{([^{}]+)\}([\s\S]*?)(?:\\[,;! ]\s*)?d([A-Za-z])(?=\s*(?:=|$))""")
    private val commands = setOf("frac", "sqrt", "sin", "cos", "tan", "arcsin", "arccos", "arctan", "ln", "log", "exp", "pi", "infty", "times", "cdot", "left", "right", "quad", "qquad")

    /** Returns null for ordinary text or unsupported LaTeX, leaving ordinary typing alone. */
    fun convert(input: String): String? {
        val trimmed=input.trim()
        val wrapped=trimmed.startsWith("\\[") || trimmed.startsWith("\\(") || trimmed.startsWith("$$")
        if(!wrapped && !Regex("""\\(?:int|frac|sqrt|sin|cos|tan|pi|infty|times|cdot)\b""").containsMatchIn(input))return null
        var source=trimmed
        source=when {
            source.startsWith("\\[") && source.endsWith("\\]") -> source.substring(2,source.length-2)
            source.startsWith("\\(") && source.endsWith("\\)") -> source.substring(2,source.length-2)
            source.startsWith("$$") && source.endsWith("$$") && source.length>=4 -> source.substring(2,source.length-2)
            else -> source
        }.trim()
        return runCatching { convertIntegrals(source) }.getOrNull()?.takeIf {runCatching {Parser(it).parse()}.isSuccess}
    }

    private fun convertIntegrals(source:String):String {
        val match=integral.find(source) ?: return convertBody(source)
        val lower=convertIntegrals(match.groupValues[1])
        val upper=convertIntegrals(match.groupValues[2])
        val body=convertIntegrals(match.groupValues[3].trim())
        val variable=match.groupValues[4]
        val replacement="integrate($body,$variable,$lower,$upper)"
        return convertIntegrals(source.substring(0,match.range.first)+replacement+source.substring(match.range.last+1))
    }

    private fun convertBody(source:String):String = buildString {
        var index=0
        while(index<source.length) {
            val c=source[index]
            if(c!='\\') {
                append(when(c){'{','}' -> if(c=='{')'(' else ')';'−' -> '-';else -> c})
                index++
                continue
            }
            index++
            if(index>=source.length)error("Incomplete LaTeX command")
            val start=index
            while(index<source.length && source[index].isLetter())index++
            if(index==start) {
                if(source[index] !in ",;! ")error("Unsupported LaTeX spacing")
                index++
                continue
            }
            val command=source.substring(start,index)
            if(command !in commands)error("Unsupported LaTeX command: $command")
            when(command) {
                "frac" -> {
                    val (top,afterTop)=group(source,index)
                    val (bottom,afterBottom)=group(source,afterTop)
                    append("(").append(convertBody(top)).append(")/(").append(convertBody(bottom)).append(")")
                    index=afterBottom
                }
                "sqrt" -> {
                    val (argument,next)=group(source,index)
                    append("sqrt(").append(convertBody(argument)).append(")")
                    index=next
                }
                "pi" -> append("pi")
                "infty" -> append("oo")
                "times", "cdot" -> append('*')
                "left", "right", "quad", "qquad" -> Unit
                "arcsin" -> append("asin")
                "arccos" -> append("acos")
                "arctan" -> append("atan")
                else -> append(command)
            }
        }
    }.replace(Regex("""\s+"""), "")

    private fun group(source:String,from:Int):Pair<String,Int> {
        var index=from
        while(index<source.length && source[index].isWhitespace())index++
        require(source.getOrNull(index)=='{') { "Expected LaTeX group" }
        val start=++index
        var depth=1
        while(index<source.length && depth>0) {
            when(source[index]) {'{' -> depth++;'}' -> depth--}
            index++
        }
        require(depth==0) { "Unclosed LaTeX group" }
        return source.substring(start,index-1) to index
    }
}
