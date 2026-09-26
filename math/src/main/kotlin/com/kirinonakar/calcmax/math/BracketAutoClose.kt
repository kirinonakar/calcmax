package com.kirinonakar.calcmax.math

/** A bracket auto-close edit: the adjusted [source] with the caret at [cursor]. */
data class BracketEdit(val source:String,val cursor:Int)

/**
 * Detects the single bracket a user just typed at a collapsed caret.
 * Openers insert their matching closer and leave the caret between the pair;
 * closers skip an identical bracket already sitting at the caret.
 * Returns null for every other edit so callers can keep their normal handling.
 */
object BracketAutoClose {
    private val pairs=mapOf('(' to ')','[' to ']','{' to '}')
    private val closers=setOf(')',']','}')
    fun typed(previousText:String,previousCursor:Int,nextText:String,nextCursor:Int):BracketEdit? {
        if(previousCursor !in 0..previousText.length)return null
        if(nextText.length!=previousText.length+1 || nextCursor!=previousCursor+1)return null
        if(!nextText.startsWith(previousText.substring(0,previousCursor)))return null
        if(!nextText.endsWith(previousText.substring(previousCursor)))return null
        val typed=nextText[previousCursor]
        pairs[typed]?.let {closer->return BracketEdit(nextText.substring(0,nextCursor)+closer+nextText.substring(nextCursor),nextCursor)}
        if(typed in closers && previousText.getOrNull(previousCursor)==typed)return BracketEdit(previousText,previousCursor+1)
        return null
    }
}
