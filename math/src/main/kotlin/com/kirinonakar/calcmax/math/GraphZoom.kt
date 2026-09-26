package com.kirinonakar.calcmax.math

import kotlin.math.*

/** Lock the axis from the finger separation when a pinch starts. */
object GraphZoom {
    fun axis(dx:Float,dy:Float):String=when {
        abs(dx)>2*abs(dy)->"x"
        abs(dy)>2*abs(dx)->"y"
        else->"xy"
    }
    fun factors(axis:String,oldX:Float,oldY:Float,newX:Float,newY:Float):Pair<Double,Double> {
        fun ratio(a:Float,b:Float)=if(abs(a)<4f)1.0 else (abs(b/a).toDouble()).coerceIn(.5,2.0)
        return when(axis){
            "x"->ratio(oldX,newX) to 1.0
            "y"->1.0 to ratio(oldY,newY)
            else->{val z=ratio(hypot(oldX,oldY),hypot(newX,newY));z to z}
        }
    }
}
