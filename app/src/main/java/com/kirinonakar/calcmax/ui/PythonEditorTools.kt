package com.kirinonakar.calcmax.ui

data class PythonEdit(val source:String,val cursor:Int,val selectionEnd:Int=cursor)
data class PythonSnippet(val label:String,val code:String,val cursorOffset:Int=code.length,val importLine:String?=null)

object PythonEditorTools {
    private const val indentUnit="    "

    /** Indent the current line or selected lines, retaining the selection direction. */
    fun indent(source:String,start:Int,end:Int=start,outdent:Boolean=false):PythonEdit {
        val anchor=start.coerceIn(0,source.length);val caret=end.coerceIn(0,source.length)
        val first=minOf(anchor,caret);val last=maxOf(anchor,caret)
        val lineStart=source.lastIndexOf('\n',first-1)+1
        // A selection ending at the next line's start does not include that line.
        val includedEnd=if(last>first && source.getOrNull(last-1)=='\n')last-1 else last
        val newline=source.indexOf('\n',includedEnd)
        val limit=if(newline<0)source.length else newline
        val changes=mutableListOf<Pair<Int,Int>>()
        val replacement=StringBuilder()
        var at=lineStart
        source.substring(lineStart,limit).split('\n').forEachIndexed {index,line->
            if(index>0)replacement.append('\n')
            val removed=if(!outdent)0 else if(line.startsWith('\t'))1 else line.take(4).takeWhile {it==' '}.length
            changes+=at to if(outdent)-removed else indentUnit.length
            replacement.append(if(outdent)line.drop(removed) else indentUnit+line)
            at+=line.length+1
        }
        fun adjusted(position:Int):Int {
            var delta=0
            for((offset,change) in changes) {
                if(position<offset)break
                delta+=if(change>=0)change else -minOf(position-offset,-change)
            }
            return position+delta
        }
        return PythonEdit(source.substring(0,lineStart)+replacement+source.substring(limit),adjusted(anchor),adjusted(caret))
    }

    fun tab(source:String,start:Int,end:Int=start,shift:Boolean=false):PythonEdit =
        if(shift || start!=end)indent(source,start,end,shift) else replace(source,start,end,indentUnit)

    /** Backspace within leading whitespace removes one level, up to the previous tab stop. */
    fun backspace(source:String,start:Int,end:Int=start):PythonEdit? {
        if(start!=end || start !in 1..source.length)return null
        val lineStart=source.lastIndexOf('\n',start-1)+1
        val prefix=source.substring(lineStart,start)
        if(prefix.isEmpty() || prefix.any {it!=' ' && it!='\t'})return null
        if(prefix.last()=='\t')return replace(source,start-1,start,"")
        var column=0
        prefix.forEach {column+=if(it=='\t')indentUnit.length-column%indentUnit.length else 1}
        val width=(column-1)%indentUnit.length+1
        var from=start
        while(from>lineStart && start-from<width && source[from-1]==' ')from--
        return replace(source,from,start,"")
    }

    /** Recognize an IME Backspace without changing selections or other deletion operations. */
    fun typedBackspace(source:String,start:Int,end:Int,nextSource:String,nextCursor:Int):PythonEdit? {
        if(start!=end || start !in 1..source.length || nextCursor!=start-1)return null
        if(nextSource!=source.removeRange(start-1,start))return null
        return backspace(source,start)
    }

    fun newline(source:String,start:Int,end:Int=start):PythonEdit {
        val a=minOf(start,end).coerceIn(0,source.length);val b=maxOf(start,end).coerceIn(a,source.length)
        val line=source.substring(source.lastIndexOf('\n',a-1)+1,a)
        val prefix=line.takeWhile {it==' ' || it=='\t'}+if(line.trimEnd().endsWith(':'))indentUnit else ""
        return replace(source,a,b,"\n$prefix")
    }

    /** Handle a single newline from the IME; leave pasted or unrelated edits intact. */
    fun typedNewline(source:String,start:Int,end:Int,nextSource:String,nextCursor:Int):PythonEdit? {
        val a=minOf(start,end).coerceIn(0,source.length);val b=maxOf(start,end).coerceIn(a,source.length)
        if(nextCursor!=a+1 || nextSource!=source.substring(0,a)+"\n"+source.substring(b))return null
        return newline(source,a,b)
    }

    val imports=listOf("import math","import statistics","import random","import itertools","from fractions import Fraction","import sympy as sp","import calcmax_catalog as calc")
    val snippets=listOf(
        PythonSnippet("Function","def function_name(value):\n    return value\n",4),
        PythonSnippet("Main","if __name__ == \"__main__\":\n    main()\n"),
        PythonSnippet("Class","class ClassName:\n    def __init__(self):\n        pass\n",6),
        PythonSnippet("For loop","for item in items:\n    print(item)\n",4),
        PythonSnippet("Try / except","try:\n    pass\nexcept Exception as exc:\n    print(exc)\n",9),
        PythonSnippet("input","input()",6),
        PythonSnippet("print","print()",6),
        PythonSnippet("sp.N","sp.N()",5,"import sympy as sp")
    )
    private val common=listOf("abs","all","any","bool","dict","enumerate","filter","float","input","int","len","list","map","max","min","open","print","range","round","set","sorted","str","sum","tuple","zip","False","None","True","and","as","class","def","elif","else","except","for","from","if","import","in","is","lambda","not","or","pass","return","try","while","with","yield","math","statistics","random","itertools","sympy","sp","calc","Fraction")
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
    fun insertSnippet(source:String,start:Int,end:Int,snippet:PythonSnippet):PythonEdit {
        val inserted=replace(source,start,end,snippet.code,snippet.cursorOffset)
        return snippet.importLine?.let { insertImport(inserted.source,inserted.cursor,it) } ?: inserted
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
    fun complete(source:String,cursor:Int,candidate:String):PythonEdit {
        val position=cursor.coerceIn(0,source.length);val start=wordStart(source,position)
        var end=position
        while(end<source.length && (source[end].isLetterOrDigit() || source[end]=='_'))end++
        val edit=replace(source,start,end,candidate)
        return requiredImport(source,start,candidate)?.let {insertImport(edit.source,edit.cursor,it)} ?: edit
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
