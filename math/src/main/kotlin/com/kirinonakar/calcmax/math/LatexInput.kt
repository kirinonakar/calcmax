package com.kirinonakar.calcmax.math

/** Converts pasted, supported LaTeX math into the calculator's editable expression syntax. */
object LatexInput {
    private val integral = Regex("""\\int\s*_\s*\{([^{}]+)\}\s*\^\s*\{([^{}]+)\}([\s\S]*?)(?:\\[,;! ]\s*)?d\s*([A-Za-z])(?=\s*(?:=|$))""")
    private val commands = setOf("lim", "frac", "dfrac", "tfrac", "sqrt", "sin", "cos", "tan", "arcsin", "arccos", "arctan", "ln", "log", "exp", "pi", "theta", "infty", "times", "cdot", "left", "right", "quad", "qquad")
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
            if(c=='(') {
                val (raw,next)=group(source,index,'(',')')
                var converted=convertBody(raw)
                // An explicit fence already groups a sole fraction; reuse that fence.
                if(Regex("""^\s*\\(?:frac|dfrac|tfrac)\b""").containsMatchIn(raw)) {
                    val tree=runCatching {Parser(converted).parse()}.getOrNull()
                    if(tree?.kind=="group" && tree.args[0].kind=="binary" && tree.args[0].value=="/")converted=converted.substring(1,converted.length-1)
                }
                append('(').append(converted).append(')')
                index=next
                continue
            }
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
                "lim" -> {
                    index=skipSpacing(source,index)
                    require(source.getOrNull(index)=='_') {"Expected LaTeX limit approach"}
                    val (approach,next)=group(source,index+1)
                    index=next
                    val parts=approach.split(Regex("""\\(?:to|rightarrow)\b|->|→"""))
                    require(parts.size==2) {"Expected LaTeX limit arrow"}
                    val variable=convertBody(parts[0])
                    require(Parser(variable).parse().kind=="symbol") {"Expected limit variable"}
                    var point=parts[1].trim()
                    val side=Regex("""\^\s*(?:\{\s*([+-])\s*\}|([+-]))\s*$""").find(point)
                    val direction=if(side==null)"" else {
                        point=point.substring(0,side.range.first)
                        if((side.groupValues[1]+side.groupValues[2])=="+")",right" else ",left"
                    }
                    point=convertBody(point)
                    Parser(point).parse()
                    val end=limitExpressionEnd(source,index)
                    val expression=compactExpression(convertBody(source.substring(index,end)))
                    append("limit(").append(expression).append(',').append(variable).append(',').append(point).append(direction).append(')')
                    index=end
                }
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

    private fun compactExpression(source:String):String {
        val tree=Parser(source).parse()
        fun shape(node:Expr):Expr = if(node.kind=="group")shape(node.args[0])
            else Expr(node.kind,node.value,node.args.map(::shape))
        val expected=shape(tree)
        val removed=mutableSetOf<Int>()
        var result=source
        // Remove only parentheses whose absence preserves the complete parsed expression.
        for(group in tree.nodes().filter {it.kind=="group"}.sortedByDescending {it.start}) {
            removed.add(group.start);removed.add(group.end-1)
            val candidate=source.filterIndexed {index,_->index !in removed}
            if(runCatching {shape(Parser(candidate).parse())==expected}.getOrDefault(false))result=candidate
            else {removed.remove(group.start);removed.remove(group.end-1)}
        }
        return result
    }

    /** A limit applies to the remaining expression within its enclosing group. */
    private fun limitExpressionEnd(source:String,from:Int):Int {
        var index=from
        var depth=0
        while(index<source.length) {
            val c=source[index]
            if(depth==0 && (c in ")]},=<>" || source.startsWith("\\right",index) && source.getOrNull(index+6)?.isLetter()!=true))break
            if(c in "([{")depth++
            else if(c in ")]}")depth--
            index++
        }
        return index
    }

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
