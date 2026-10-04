package com.kirinonakar.symvacas.ui

import kotlin.math.abs

/** Trace at the touched x coordinate, choosing the closest y branch for implicit curves. */
internal fun graphTracePointAtX(
    curve:List<Pair<Double,Double>?>,
    x:Double,
    targetY:Double,
):Pair<Double,Double>? {
    if(!x.isFinite() || !targetY.isFinite())return null
    var nearest:Pair<Double,Double>?=null
    var distance=Double.POSITIVE_INFINITY
    var previous:Pair<Double,Double>?=null
    fun consider(y:Double) {
        if(!y.isFinite())return
        val nextDistance=abs(y-targetY)
        if(nearest==null || nextDistance<distance) {nearest=x to y;distance=nextDistance}
    }
    for(sample in curve) {
        val point=sample?.takeIf {it.first.isFinite() && it.second.isFinite()}
        if(point!=null) {
            if(point.first==x)consider(point.second)
            previous?.let {start->
                if(x>=minOf(start.first,point.first) && x<=maxOf(start.first,point.first)) {
                    if(start.first==point.first)consider(targetY.coerceIn(minOf(start.second,point.second),maxOf(start.second,point.second)))
                    else {
                        val fraction=(x-start.first)/(point.first-start.first)
                        consider(start.second*(1-fraction)+point.second*fraction)
                    }
                }
            }
        }
        // A missing/non-finite sample breaks the curve: never interpolate across an asymptote.
        previous=point
    }
    return nearest
}
