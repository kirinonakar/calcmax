package com.example.calcmax.ui

/** One press of the Copy button: the text for the clipboard and the state of the next press. */
data class CopyTarget(val text:String,val expression:Boolean,val expressionNext:Boolean) {
    /** Button label for this press: = copies the answer, ƒ copies the expression. */
    val label:String get()=if(expression)"Copy ƒ" else "Copy ="
}

/** The Copy button walks the cycle answer → expression → answer → ... */
object CopyCycle {
    fun next(answer:String?,source:String,copyExpression:Boolean):CopyTarget {
        val result=answer?.takeIf{it.isNotBlank()}
        val expression=source.takeIf{it.isNotBlank()}
        return when {
            copyExpression&&expression!=null->CopyTarget(expression,true,false)
            result!=null->CopyTarget(result,false,true)
            expression!=null->CopyTarget(expression,true,false)
            else->CopyTarget("",false,false)
        }
    }
}
