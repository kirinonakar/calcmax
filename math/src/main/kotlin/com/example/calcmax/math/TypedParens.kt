package com.example.calcmax.math

/**
 * Remembers empty parentheses the user typed directly (for example the pair an auto-close
 * option inserts) so the display can keep them visible while editor-created slots keep
 * their box look. A range stores the group start and the index right after its closing
 * parenthesis.
 */
object TypedParens {
    fun mark(ranges:List<IntRange>,source:String,cursor:Int):List<IntRange> {
        val at=when {
            source.getOrNull(cursor-1)=='(' && source.getOrNull(cursor)==')' -> cursor-1
            source.getOrNull(cursor-2)=='(' && source.getOrNull(cursor-1)==')' -> cursor-2
            else -> return ranges
        }
        return (ranges+listOf(at..(at+2))).distinct().takeLast(8)
    }
    fun shift(ranges:List<IntRange>,oldSource:String,newSource:String):List<IntRange> {
        if(ranges.isEmpty() || oldSource==newSource)return ranges
        var prefix=0
        while(prefix<oldSource.length && prefix<newSource.length && oldSource[prefix]==newSource[prefix])prefix++
        var suffix=0
        while(suffix<oldSource.length-prefix && suffix<newSource.length-prefix && oldSource[oldSource.length-1-suffix]==newSource[newSource.length-1-suffix])suffix++
        val oldEnd=oldSource.length-suffix
        val delta=newSource.length-suffix-oldEnd
        val moved=ranges.mapNotNull {range->
            val shifted=when {
                range.last<=prefix -> range
                range.first>=oldEnd -> (range.first+delta)..(range.last+delta)
                else -> null
            }
            shifted?.takeIf {it.last==it.first+2 && newSource.getOrNull(it.first)=='(' && newSource.getOrNull(it.first+1)==')'}
        }
        return if(moved==ranges)ranges else moved
    }
}
