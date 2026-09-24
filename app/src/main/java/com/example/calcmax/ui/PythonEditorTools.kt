package com.example.calcmax.ui

data class PythonEdit(val source:String,val cursor:Int)
data class PythonSnippet(val label:String,val code:String,val cursorOffset:Int=code.length)

object PythonEditorTools {
    val imports=listOf("import math","import statistics","import random","import itertools","from fractions import Fraction","import sympy as sp")
    val snippets=listOf(
        PythonSnippet("Function","def function_name(value):\n    return value\n",4),
        PythonSnippet("Main","if __name__ == \"__main__\":\n    main()\n"),
        PythonSnippet("Class","class ClassName:\n    def __init__(self):\n        pass\n",6),
        PythonSnippet("For loop","for item in items:\n    print(item)\n",4),
        PythonSnippet("Try / except","try:\n    pass\nexcept Exception as exc:\n    print(exc)\n",9)
    )
    private val common=listOf("abs","all","any","bool","dict","enumerate","filter","float","int","len","list","map","max","min","open","print","range","round","set","sorted","str","sum","tuple","zip","False","None","True","and","as","class","def","elif","else","except","for","from","if","import","in","is","lambda","not","or","pass","return","try","while","with","yield","math","statistics","random","itertools","sympy","sp","Fraction")
    private val members=mapOf(
        "math" to listOf("acos","asin","atan","ceil","cos","e","exp","floor","log","pi","sin","sqrt","tan"),
        "statistics" to listOf("mean","median","mode","stdev","variance"),
        "random" to listOf("choice","randint","random","shuffle"),
        "sp" to listOf("diff","expand","factor","integrate","Matrix","pi","simplify","sin","solve","sqrt","symbols")
    )

    fun replace(source:String,start:Int,end:Int,text:String,cursorOffset:Int=text.length):PythonEdit {
        val a=start.coerceIn(0,source.length);val b=end.coerceIn(a,source.length)
        return PythonEdit(source.substring(0,a)+text+source.substring(b),a+cursorOffset.coerceIn(0,text.length))
    }
    fun wordStart(source:String,cursor:Int):Int {
        var at=cursor.coerceIn(0,source.length)
        while(at>0 && (source[at-1].isLetterOrDigit() || source[at-1]=='_'))at--
        return at
    }
    fun completions(source:String,cursor:Int):List<String> {
        val end=cursor.coerceIn(0,source.length);val start=wordStart(source,end)
        val prefix=source.substring(start,end)
        if(prefix.length<2)return emptyList()
        val module=if(start>0 && source[start-1]=='.')Regex("[A-Za-z_][A-Za-z_0-9]*$").find(source.substring(0,start-1))?.value else null
        val names=Regex("\\b[A-Za-z_][A-Za-z_0-9]*\\b").findAll(source).map {it.value}.toSet()
        return ((if(module!=null)members[module].orEmpty() else common+names)).distinct().filter {it.startsWith(prefix) && it!=prefix}.sorted().take(8)
    }
    fun requiredImport(source:String,wordStart:Int,candidate:String):String? {
        if(wordStart>0 && source[wordStart-1]=='.') {
            val module=Regex("[A-Za-z_][A-Za-z_0-9]*$").find(source.substring(0,wordStart-1))?.value
            if(module!=null && candidate in members[module].orEmpty())return if(module=="sp")"import sympy as sp" else "import $module"
        }
        return when(candidate){"math","statistics","random"->"import $candidate";"sp"->"import sympy as sp";"Fraction"->"from fractions import Fraction";else->null}
    }
    fun insertImport(source:String,cursor:Int,line:String):PythonEdit {
        if(source.lineSequence().any {it.trim()==line})return PythonEdit(source,cursor)
        val offset=if(source.startsWith("#!"))source.indexOf('\n').let {if(it<0)source.length else it+1} else 0
        val inserted="$line\n"
        return replace(source,offset,offset,inserted,inserted.length).let {it.copy(cursor=(cursor+inserted.length).coerceAtMost(it.source.length))}
    }
}
