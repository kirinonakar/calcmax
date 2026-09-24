package com.example.calcmax.ui

data class PythonEdit(val source:String,val cursor:Int)
data class PythonSnippet(val label:String,val code:String,val cursorOffset:Int=code.length)

object PythonEditorTools {
    val imports=listOf("import math","import statistics","import random","import itertools","from fractions import Fraction","import sympy as sp","import calcmax_catalog as calc")
    val snippets=listOf(
        PythonSnippet("Function","def function_name(value):\n    return value\n",4),
        PythonSnippet("Main","if __name__ == \"__main__\":\n    main()\n"),
        PythonSnippet("Class","class ClassName:\n    def __init__(self):\n        pass\n",6),
        PythonSnippet("For loop","for item in items:\n    print(item)\n",4),
        PythonSnippet("Try / except","try:\n    pass\nexcept Exception as exc:\n    print(exc)\n",9)
    )
    private val common=listOf("abs","all","any","bool","dict","enumerate","filter","float","int","len","list","map","max","min","open","print","range","round","set","sorted","str","sum","tuple","zip","False","None","True","and","as","class","def","elif","else","except","for","from","if","import","in","is","lambda","not","or","pass","return","try","while","with","yield","math","statistics","random","itertools","sympy","sp","calc","Fraction")
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
        return when(candidate){"math","statistics","random"->"import $candidate";"sp"->"import sympy as sp";"calc"->"import calcmax_catalog as calc";"Fraction"->"from fractions import Fraction";else->null}
    }
    fun insertImport(source:String,cursor:Int,line:String):PythonEdit {
        val existing=mutableListOf<IntRange>()
        var position=0
        while(position<source.length) {
            val newline=source.indexOf('\n',position)
            val end=if(newline<0)source.length else newline+1
            if(source.substring(position,end).trim()==line)existing+=position until end
            position=end
        }
        val firstLineEnd=if(source.startsWith("#!"))source.indexOf('\n').let {if(it<0)source.length else it+1} else 0
        if(existing.size==1&&existing[0].first==firstLineEnd)return PythonEdit(source,cursor)
        var cleaned=source
        var adjusted=cursor.coerceIn(0,source.length)
        existing.asReversed().forEach {range->
            cleaned=cleaned.removeRange(range.first,range.last+1)
            adjusted=when {adjusted>range.last+1->adjusted-(range.last+1-range.first);adjusted>range.first->range.first;else->adjusted}
        }
        val offset=if(cleaned.startsWith("#!"))cleaned.indexOf('\n').let {if(it<0)cleaned.length else it+1} else 0
        val prefix=if(offset==cleaned.length&&cleaned.isNotEmpty()&&!cleaned.endsWith('\n'))"\n" else ""
        val inserted="$prefix$line\n"
        return PythonEdit(cleaned.substring(0,offset)+inserted+cleaned.substring(offset),adjusted+if(adjusted>=offset)inserted.length else 0)
    }
    fun insertCatalog(source:String,start:Int,end:Int,template:String,inside:Int):PythonEdit {
        val opening=template.indexOf('(')
        if(opening<0)return replace(source,start,end,template,inside)
        val symbols=setOf("x","y","z","t","pi","true","false","left","right","linear","m","cm")
        val token=Regex("\\b[A-Za-z_][A-Za-z_0-9]*\\b")
        val body=template.substring(opening+1)
        val converted=StringBuilder("calc.").append(template.substring(0,opening+1))
        var caret=inside+5
        token.findAll(body).let {matches->
            var previous=0
            matches.forEach {match->
                converted.append(body,previous,match.range.first)
                val replacement=if(match.value in symbols)"calc.${match.value}" else match.value
                if(opening+1+match.range.first<inside)caret+=replacement.length-match.value.length
                converted.append(replacement)
                previous=match.range.last+1
            }
            converted.append(body,previous,body.length)
        }
        val empty=source.isBlank()
        val call=converted.toString()
        val inserted=if(empty)replace(source,0,source.length,"print($call)",caret+6)else replace(source,start,end,call,caret)
        val withSymbols=insertImport(inserted.source,inserted.cursor,"from calcmax_catalog import x, y, z, t, pi")
        return insertImport(withSymbols.source,withSymbols.cursor,"import calcmax_catalog as calc")
    }
}
