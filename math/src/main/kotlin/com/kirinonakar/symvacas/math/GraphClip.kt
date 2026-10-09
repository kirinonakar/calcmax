package com.kirinonakar.symvacas.math

import kotlin.math.abs

/** Clip before conversion to screen floats, including exponential offscreen values. */
object GraphClip {
    fun segment(first:Pair<Double,Double>?,last:Pair<Double,Double>?,xmin:Double,xmax:Double,ymin:Double,ymax:Double):Pair<Pair<Double,Double>,Pair<Double,Double>>? {
        if(first==null || last==null || !first.first.isFinite() || !first.second.isFinite() || !last.first.isFinite() || !last.second.isFinite())return null
        fun code(p:Pair<Double,Double>)=(if(p.first<xmin)1 else if(p.first>xmax)2 else 0) or (if(p.second<ymin)4 else if(p.second>ymax)8 else 0)
        var a:Pair<Double,Double> = first;var b:Pair<Double,Double> = last
        repeat(8) {
            val ca=code(a);val cb=code(b)
            if((ca or cb)==0)return a to b
            if((ca and cb)!=0)return null
            val outside=if(ca!=0)ca else cb
            val vertical=(outside and 12)!=0
            val edge=when {outside and 8!=0->ymax;outside and 4!=0->ymin;outside and 2!=0->xmax;else->xmin}
            val av=if(vertical)a.second else a.first;val bv=if(vertical)b.second else b.first
            val nearA=abs(edge-av)<=abs(edge-bv)
            val near=if(nearA)a else b;val far=if(nearA)b else a
            val fraction=(edge-(if(vertical)near.second else near.first))/((if(vertical)far.second else far.first)-(if(vertical)near.second else near.first))
            val point=if(vertical)(near.first+fraction*(far.first-near.first)) to edge else edge to (near.second+fraction*(far.second-near.second))
            if(!point.first.isFinite() || !point.second.isFinite())return null
            if(ca!=0)a=point else b=point
        }
        return null
    }
}
