package com.example.calcmax.math

/** Source is the serialization; cursor and selection can address whole AST subtrees. */
data class Editor(val source: String = "", val cursor: Int = source.length, val anchor: Int = cursor,
                  val exponent: IntRange? = null, val outside: IntRange? = null, val activeToken: IntRange? = null) {
    fun insert(text: String, inside: Int = text.length): Editor {
        exponent?.let {region->
            if(cursor in region && anchor in region) {
                val grouped=source.substring(0,region.first)+"("+source.substring(region.first,region.last)+")"+source.substring(region.last)
                return Editor(grouped,cursor+1,anchor+1).insert(text,inside)
            }
        }
        val a = minOf(cursor, anchor).coerceIn(0, source.length)
        val b = maxOf(cursor, anchor).coerceIn(a, source.length)
        val result = source.substring(0,a) + text + source.substring(b)
        val active=activeToken?.let{old->
            val end=old.last+text.length-(b-a)
            Lexer.scan(result).firstOrNull{it.start==old.first&&it.end==end&&it.text.isNotEmpty()}?.let{it.start..it.end}
        }
        return Editor(result, a + inside, activeToken=active)
    }
    fun delete(): Editor = if(cursor != anchor) insert("") else if(cursor > 0) copy(anchor = cursor-1).insert("") else this
    fun move(delta: Int): Editor {
        if(cursor!=anchor) {
            val position=if(delta>0)maxOf(cursor,anchor) else minOf(cursor,anchor)
            val scope=barePower(minOf(cursor,anchor),maxOf(cursor,anchor))?.args?.get(1)?.let{it.start..it.end}
            return Editor(source,position,exponent=scope)
        }
        if(delta>0&&exponent!=null&&cursor==exponent.last) {
            val power=barePower(exponent.first,exponent.last)
            return Editor(source,cursor,outside=power?.let{it.start..it.end})
        }
        if(delta<0&&exponent==null) {
            val power=tree()?.nodes()?.filter{it.kind=="binary"&&it.value=="^"&&it.end==cursor&&it.args[1].kind!="group"}?.minByOrNull{it.end-it.start}
            if(power!=null)return Editor(source,cursor,exponent=power.args[1].let{it.start..it.end})
        }
        val position=(cursor+delta).coerceIn(0,source.length)
        if(delta>0&&cursor<source.length&&source[cursor]==')') {
            val container=tree()?.nodes()?.filter {it.kind=="binary"&&it.value in listOf("/","^")&&it.args[1].kind=="group"&&it.args[1].end==position}?.minByOrNull{it.end-it.start}
            if(container!=null)return Editor(source,position,outside=container.start..container.end)
        }
        return Editor(source,position,exponent=exponent?.takeIf{position in it})
    }
    private fun barePower(start:Int,end:Int):Expr?=tree()?.nodes()?.filter{
        it.kind=="binary"&&it.value=="^"&&it.args[1].kind!="group"&&start>=it.args[1].start&&end<=it.args[1].end
    }?.minByOrNull{it.args[1].end-it.args[1].start}
    fun selectRange(start:Int,end:Int):Editor=copy(cursor=end,anchor=start,exponent=barePower(start,end)?.args?.get(1)?.let{it.start..it.end},outside=null,activeToken=null)
    fun placeInToken(start:Int,end:Int,position:Int)=Editor(source,position.coerceIn(start,end),exponent=barePower(start,end)?.args?.get(1)?.let{it.start..it.end},activeToken=start..end)
    fun select(node: Expr) = selectRange(node.start,node.end)
    fun after(start:Int,end:Int)=Editor(source,end,outside=start..end)
    /** Exactly one presentation node owns a collapsed cursor, including shared source boundaries. */
    fun cursorTarget():IntRange? {
        if(cursor!=anchor)return null
        outside?.let{return it}
        activeToken?.let {if(cursor in it)return it}
        val root=tree() ?: return runCatching{Lexer.scan(source).filter{it.text.isNotEmpty()&&cursor in it.start..it.end}.minByOrNull{if(it.end==cursor)0 else 1}?.let{it.start..it.end}}.getOrNull()
        if(cursor==source.length&&exponent==null)return root.start..root.end
        val leaves=root.nodes().filter{it.args.isEmpty()&&cursor in it.start..it.end}
        val leaf=leaves.sortedWith(compareBy<Expr>{if(it.kind=="hole")0 else if(it.end==cursor)1 else 2}.thenBy{it.end-it.start}).firstOrNull()
        if(leaf!=null)return leaf.start..leaf.end
        return root.nodes().filter{cursor in it.start..it.end}.minByOrNull{it.end-it.start}?.let{it.start..it.end}
    }
    fun tree(): Expr? = runCatching { Parser(source,true).parse() }.getOrNull()
    fun parent(): Editor {
        val node = tree()?.nodes()?.filter { it.start <= minOf(cursor,anchor) && it.end >= maxOf(cursor,anchor) && (it.start < minOf(cursor,anchor) || it.end > maxOf(cursor,anchor)) }?.minByOrNull { it.end-it.start }
        return node?.let(::select) ?: this
    }
    fun child(): Editor = tree()?.nodes()?.firstOrNull { it.start == minOf(cursor,anchor) && it.end == maxOf(cursor,anchor) }?.args?.firstOrNull()?.let(::select) ?: this
}
