package com.kirinonakar.calcmax.math

/** Converts pasted, supported LaTeX math into the calculator's editable expression syntax. */
object LatexInput {
    private val integral = Regex("""\\int\s*_\s*\{([^{}]+)\}\s*\^\s*\{([^{}]+)\}([\s\S]*?)(?:\\[,;! ]\s*)?d\s*([A-Za-z])(?=\s*(?:=|$))""")
    private val commands = setOf("frac", "dfrac", "tfrac", "sqrt", "sin", "cos", "tan", "arcsin", "arccos", "arctan", "ln", "log", "exp", "pi", "theta", "infty", "times", "cdot", "left", "right", "quad", "qquad")
    private val commandPattern = Regex("""\\([A-Za-z]+)""")
    private val bracedPower = Regex("""\^\s*\{""")

    /** Returns null for ordinary text or unsupported LaTeX, leaving ordinary typing alone. */
    fun convert(input: String): String? {
        val trimmed=input.trim()
        val wrapped=trimmed.startsWith("\\[") || trimmed.startsWith("\\(") || trimmed.startsWith('$')
        if(!wrapped && !bracedPower.containsMatchIn(input) && !commandPattern.findAll(input).any {it.groupValues[1] in commands || it.groupValues[1]=="int"})return null
        var source=trimmed
        source=when {
            source.startsWith("\\[") && source.endsWith("\\]") -> source.substring(2,source.length-2)
            source.startsWith("\\(") && source.endsWith("\\)") -> source.substring(2,source.length-2)
            source.startsWith("$$") && source.endsWith("$$") && source.length>=4 -> source.substring(2,source.length-2)
            source.startsWith('$') && source.endsWith('$') && source.length>=2 -> source.substring(1,source.length-1)
            else -> source
        }.trim()
        return runCatching { convertIntegrals(source) }.getOrNull()?.takeIf {runCatching {Parser(it).parse()}.isSuccess}
    }

    /** Convert only the inserted text so wrapped LaTeX also works inside an existing expression. */
    fun convertEdit(previous:Editor,updated:String):Editor? {
        val start=minOf(previous.cursor,previous.anchor)
        val end=maxOf(previous.cursor,previous.anchor)
        val prefix=previous.source.substring(0,start)
        val suffix=previous.source.substring(end)
        if(updated.length>=prefix.length+suffix.length && updated.startsWith(prefix) && updated.endsWith(suffix)) {
            val inserted=updated.substring(prefix.length,updated.length-suffix.length)
            convert(inserted)?.let {return Editor(prefix+it+suffix,start+it.length)}
        }
        return convert(updated)?.let {Editor(it)}
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
                "frac", "dfrac", "tfrac" -> {
                    val (top,afterTop)=group(source,index)
                    val (bottom,afterBottom)=group(source,afterTop)
                    append("((").append(convertBody(top)).append(")/(").append(convertBody(bottom)).append("))")
                    index=afterBottom
                }
                "sqrt" -> {
                    while(source.getOrNull(index)?.isWhitespace()==true)index++
                    val degree=if(source.getOrNull(index)=='[') {
                        val (value,next)=group(source,index,'[',']')
                        index=next
                        convertBody(value)
                    } else null
                    val (argument,next)=group(source,index)
                    if(degree==null)append("sqrt(").append(convertBody(argument)).append(")")
                    else append("nthroot(").append(convertBody(argument)).append(',').append(degree).append(")")
                    index=next
                }
                "log" -> {
                    index=skipSpacing(source,index)
                    val base=if(source.getOrNull(index)=='_') {
                        val (value,next)=argument(source,index+1,singleToken=true)
                        index=skipSpacing(source,next)
                        logOperand(value)
                    } else null
                    // Keep the calculator's existing explicit log(value,base) syntax.
                    if(base==null && (source.getOrNull(index)=='(' || source.startsWith("\\left",index))) {
                        append("log")
                    } else {
                        val (value,next)=argument(source,index)
                        append("log(").append(logOperand(value))
                        if(base!=null)append(',').append(base)
                        append(')')
                        index=next
                    }
                }
                "pi" -> append("pi")
                "infty" -> append("oo")
                "times", "cdot" -> append('*')
                "left", "right", "quad", "qquad" -> Unit
                "sin", "cos", "tan", "arcsin", "arccos", "arctan", "ln", "exp" -> {
                    val name=mapOf("arcsin" to "asin","arccos" to "acos","arctan" to "atan")[command] ?: command
                    index=skipSpacing(source,index)
                    val exponent=if(source.getOrNull(index)=='^') {
                        val (value,next)=argument(source,index+1,true)
                        index=skipSpacing(source,next)
                        convertBody(value)
                    } else null
                    if(exponent==null && (source.getOrNull(index)=='(' || source.startsWith("\\left",index)))append(name)
                    else {
                        val (value,next)=argument(source,index)
                        append(name).append('(').append(convertBody(value)).append(')')
                        if(exponent!=null)append("^(").append(exponent).append(')')
                        index=next
                    }
                }
                else -> append(command)
            }
        }
    }.replace(Regex("""\s+"""), "")

    private fun logOperand(source:String):String {
        var converted=convertBody(source)
        // The call's comma/closing parenthesis already delimits each operand.
        while(Parser(converted).parse().kind=="group")converted=converted.substring(1,converted.length-1)
        return converted
    }

    private fun skipSpacing(source:String,from:Int):Int {
        var index=from
        while(index<source.length) {
            if(source[index].isWhitespace())index++
            else if(source[index]=='\\' && source.getOrNull(index+1) in listOf(',', ';', '!', ' '))index+=2
            else break
        }
        return index
    }

    /** Read one logarithm base/argument before whitespace is removed. */
    private fun argument(source:String,from:Int,singleToken:Boolean=false):Pair<String,Int> {
        val start=skipSpacing(source,from)
        var index=start
        when(val c=source.getOrNull(index)) {
            '{','(' -> index=group(source,index,c,if(c=='{')'}' else ')').second
            '\\' -> {
                val match=commandPattern.find(source,index)?.takeIf {it.range.first==index}
                    ?: error("Expected LaTeX argument")
                val command=match.groupValues[1]
                index=match.range.last+1
                when(command) {
                    "frac","dfrac","tfrac" -> {
                        index=group(source,index).second
                        index=group(source,index).second
                    }
                    "sqrt" -> {
                        index=skipSpacing(source,index)
                        if(source.getOrNull(index)=='[')index=group(source,index,'[',']').second
                        index=group(source,index).second
                    }
                    "left" -> index=group(source,skipSpacing(source,index),'(',')').second
                    "log" -> {
                        index=skipSpacing(source,index)
                        if(source.getOrNull(index)=='_')index=argument(source,index+1,true).second
                        index=argument(source,index).second
                    }
                    "sin","cos","tan","arcsin","arccos","arctan","ln","exp" -> {
                        index=skipSpacing(source,index)
                        if(source.getOrNull(index)=='^')index=argument(source,index+1,true).second
                        index=argument(source,index).second
                    }
                }
            }
            else -> {
                require(c!=null && (c.isLetterOrDigit() || c=='.')) {"Expected LaTeX argument"}
                index++
                if(!singleToken) {
                    if(c.isDigit() || c=='.')while(source.getOrNull(index)?.let {it.isDigit() || it=='.'}==true)index++
                    else while(source.getOrNull(index)?.isLetterOrDigit()==true)index++
                }
            }
        }
        if(!singleToken) {
            val power=skipSpacing(source,index)
            if(source.getOrNull(power)=='^')index=argument(source,power+1,true).second
        }
        return source.substring(start,index) to index
    }

    private fun group(source:String,from:Int,open:Char='{',close:Char='}'):Pair<String,Int> {
        var index=from
        while(index<source.length && source[index].isWhitespace())index++
        require(source.getOrNull(index)==open) { "Expected LaTeX group" }
        val start=++index
        var depth=1
        var braces=0
        while(index<source.length && depth>0) {
            val character=source[index]
            if(open=='[' && character=='{')braces++
            else if(open=='[' && character=='}') {require(braces>0);braces--}
            else if(braces==0)when(character) {open -> depth++;close -> depth--}
            index++
        }
        require(depth==0) { "Unclosed LaTeX group" }
        return source.substring(start,index-1) to index
    }
}
