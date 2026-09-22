package com.example.calcmax.math

/** Source is the serialization; cursor and selection can address whole AST subtrees. */
data class Editor(val source: String = "", val cursor: Int = source.length, val anchor: Int = cursor) {
    fun insert(text: String, inside: Int = text.length): Editor {
        val a = minOf(cursor, anchor).coerceIn(0, source.length)
        val b = maxOf(cursor, anchor).coerceIn(a, source.length)
        val result = source.substring(0,a) + text + source.substring(b)
        return Editor(result, a + inside)
    }
    fun delete(): Editor = if(cursor != anchor) insert("") else if(cursor > 0) copy(anchor = cursor-1).insert("") else this
    fun move(delta: Int): Editor = Editor(source, (cursor+delta).coerceIn(0,source.length))
    fun select(node: Expr) = copy(cursor = node.end, anchor = node.start)
    fun tree(): Expr? = runCatching { Parser(source,true).parse() }.getOrNull()
    fun parent(): Editor {
        val node = tree()?.nodes()?.filter { it.start <= minOf(cursor,anchor) && it.end >= maxOf(cursor,anchor) && (it.start < minOf(cursor,anchor) || it.end > maxOf(cursor,anchor)) }?.minByOrNull { it.end-it.start }
        return node?.let(::select) ?: this
    }
    fun child(): Editor = tree()?.nodes()?.firstOrNull { it.start == minOf(cursor,anchor) && it.end == maxOf(cursor,anchor) }?.args?.firstOrNull()?.let(::select) ?: this
}
