package com.example.calcmax.math

/** Immutable, source-addressable tree shared by every calculator mode. */
data class Expr(val kind: String, val value: String = "", val args: List<Expr> = emptyList(), val start: Int = 0, val end: Int = 0) {
    fun json(): String = "{\"kind\":${quote(value = kind)},\"value\":${quote(value)},\"args\":[${args.joinToString(",") { it.json() }}],\"start\":$start,\"end\":$end}"
    fun nodes(): List<Expr> = listOf(this) + args.flatMap { it.nodes() }
}

fun quote(value: String): String = "\"" + buildString {
    value.forEach { c -> append(when(c) { '\\' -> "\\\\"; '"' -> "\\\""; '\n' -> "\\n"; '\r' -> "\\r"; '\t' -> "\\t"; else -> if(c.code < 32) "\\u%04x".format(c.code) else c.toString() }) }
} + "\""

class SyntaxException(message: String, val position: Int) : IllegalArgumentException("Syntax ERROR at ${position + 1}: $message")

data class Token(val text: String, val start: Int, val end: Int)

object Lexer {
    fun scan(source: String): List<Token> {
        if (source.length > 8192) throw SyntaxException("Expression exceeds 8192 characters", 0)
        val result = mutableListOf<Token>()
        var i = 0
        while (i < source.length) {
            val c = source[i]
            if (c.isWhitespace()) { i++; continue }
            val start = i++
            if(c.isDigit() || c == '.') {
                while(i < source.length && (source[i].isDigit() || source[i] == '.')) i++
                if(i < source.length && source[i] in "eE" && i+1 < source.length && (source[i+1].isDigit() || source[i+1] in "+-")) {
                    i++; if(source[i] in "+-") i++
                    while(i < source.length && source[i].isDigit()) i++
                }
            } else if(c.isLetter() && c !in "π∞√") {
                while(i < source.length && (source[i].isLetterOrDigit() || source[i] == '_')) i++
            } else if(i < source.length && source.substring(start, i+1) in listOf("<=", ">=", "!=", "==", ":=", "**", "->")) i++
            val raw = source.substring(start, i)
            result += Token(when(raw) { "×", "·" -> "*"; "÷" -> "/"; "−" -> "-"; "π" -> "pi"; "∞" -> "oo"; "**" -> "^"; "≤" -> "<="; "≥" -> ">="; "→" -> "->"; else -> raw }, start, i)
        }
        result += Token("", source.length, source.length)
        return result
    }
}

/** Pratt parser: right-associative powers bind tighter than unary minus. No eval. */
class Parser(private val source: String, private val allowHoles: Boolean = false) {
    private val tokens = Lexer.scan(source)
    private var index = 0
    private var depth = 0
    private var count = 0
    private val token get() = tokens[index]
    private fun take() = tokens[index++]
    private fun expect(s: String): Token {
        if(allowHoles && token.text.isEmpty() && s in listOf(")","]"))return Token(s,source.length,source.length)
        if(token.text != s) fail("Expected '$s'"); return take()
    }
    private fun fail(s: String): Nothing = throw SyntaxException(s, token.start)
    private fun isDmsComponentStart(): Boolean {
        val first=token.text.firstOrNull()
        if(first?.isDigit()==true || first=='.') return true
        if(token.text !in listOf("+","-")) return false
        val next=tokens.getOrNull(index+1)?.text?.firstOrNull()
        return next?.isDigit()==true || next=='.'
    }
    private fun dmsComponent(): Expr? {
        val saved=index
        val first=take()
        val sign=if(first.text in listOf("+","-")) {
            val next=token.text.firstOrNull()
            if(next?.isDigit()!=true && next!='.') { index=saved; return null }
            take()
        } else null
        val number=if(sign==null) first else tokens[index-1]
        val raw=(sign?.text ?: "")+number.text
        return runCatching { raw.toBigDecimal(); Expr("number",raw,start=first.start,end=number.end) }.getOrNull()
            ?: run { index=saved; null }
    }
    private fun dmsHole() = Expr("hole",start=token.start,end=token.start)
    /** A Casio DMS entry is a sequence of numeric fields separated by °, ′ and ″. */
    private fun tryDms(left: Expr): Expr? {
        val saved=index
        val degreeMarker=take()
        if(!isDmsComponentStart()) { index=saved; return null }
        val minute=dmsComponent()
        if(minute==null) { index=saved; return null }
        if(token.text=="′") {
            val minuteMarker=take()
            val second=dmsComponent()
            if(second==null) return Expr("sexagesimal","pending-second",listOf(left,minute,dmsHole()),start=left.start,end=minuteMarker.end)
            if(token.text=="″") {
                val end=take().end
                return Expr("sexagesimal","",listOf(left,minute,second),start=left.start,end=end)
            }
            return Expr("sexagesimal","pending-final",listOf(left,minute,second,dmsHole()),start=left.start,end=second.end)
        }
        return Expr("sexagesimal","pending-minute",listOf(left,minute,dmsHole()),start=left.start,end=degreeMarker.end.coerceAtLeast(minute.end))
    }
    fun parse(): Expr {
        val expr = expression(0)
        if(token.text.isNotEmpty()) fail("Unexpected '${token.text}'")
        return expr
    }
    private fun expression(min: Int): Expr {
        if(allowHoles && token.text in listOf("", ")", "]", ",")) return Expr("hole", start=token.start,end=token.start)
        if(++depth > 96 || ++count > 2048) fail("Expression complexity limit")
        val first = take()
        var left = when {
            first.text in listOf("+", "-") -> Expr("unary", first.text, listOf(expression(25)), first.start, tokens[index-1].end)
            first.text == "√" -> Expr("call", "sqrt", listOf(expression(25)), first.start, tokens[index-1].end)
            first.text == "(" -> {
                val args = mutableListOf(expression(0))
                var tuple = false
                while(token.text == ",") {
                    tuple = true
                    take()
                    if(token.text == ")") break
                    args += expression(0)
                }
                val unclosed = allowHoles && token.text.isEmpty()
                val end = expect(")")
                Expr(if(tuple) "tuple" else "group", if(!tuple && unclosed) "open" else "", args = args, start = first.start, end = end.end)
            }
            first.text == "[" -> {
                val args = mutableListOf<Expr>()
                if(token.text != "]") { args += expression(0); while(token.text == ",") { take(); args += expression(0) } }
                Expr("list", args = args, start = first.start, end = expect("]").end)
            }
            first.text.firstOrNull()?.let { it.isDigit() || it == '.' } == true -> {
                try { first.text.toBigDecimal() } catch(_: Exception) { throw SyntaxException("Invalid number", first.start) }
                Expr("number", first.text, start = first.start, end = first.end)
            }
            first.text.firstOrNull()?.isLetter() == true -> {
                if(token.text == "(") {
                    take(); val args = mutableListOf<Expr>()
                    if(token.text==")" && !allowHoles) fail("Enter a function argument")
                    if(token.text != ")" || allowHoles) { args += expression(0); while(token.text == ",") { take(); args += expression(0) } }
                    Expr("call", first.text, args, first.start, expect(")").end)
                } else Expr("symbol", first.text, start = first.start, end = first.end)
            }
            else -> throw SyntaxException("Expected an expression", first.start)
        }
        while(true) {
            val op = token.text
            if(op=="°" && min<=40) {
                val dms=tryDms(left)
                if(dms!=null) { left=dms; continue }
            }
            if(op in listOf("!", "%", "°", "²", "³") && min <= 40) {
                val end = take().end
                left = when(op) {
                    "²", "³" -> Expr("binary", "^", listOf(left, Expr("number", if(op == "²") "2" else "3")), left.start, end)
                    else -> Expr("call", when(op) { "!" -> "factorial"; "%" -> "percent"; else -> "degree" }, listOf(left), left.start, end)
                }; continue
            }
            val implicit = op.isNotEmpty() && (op == "(" || op.first().isLetter() && op != "mod" || op == "√")
            val actual = if(implicit) "*" else op
            val binding = when(actual) { ":=" -> 1; "=", "==", "<", ">", "<=", ">=", "!=", "->" -> 5; "+", "-" -> 10; "*", "/", "mod", "∠" -> 20; "^" -> 30; else -> -1 }
            if(binding < min) break
            if(!implicit) take()
            val right = expression(if(actual in listOf("^", ":=")) binding else binding+1)
            left = Expr(if(binding == 5) "relation" else "binary", actual, listOf(left,right), left.start,right.end)
        }
        depth--
        return left
    }
}
