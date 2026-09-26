package com.kirinonakar.calcmax.math
import kotlin.math.*
import java.util.Locale
object PiAxis {
    fun step(range:Double):Double=PI*2.0.pow(ceil(log2(range/PI/8)))
    fun label(value:Double):String {
        if(abs(value)<1e-12)return "0"
        val multiple=value/PI
        for(power in 0..10){val denominator=1 shl power;val numerator=(multiple*denominator).roundToLong()
            if(abs(multiple-numerator.toDouble()/denominator)<1e-9){
                val sign=if(numerator<0)"−" else "";val n=abs(numerator)
                return sign+(if(n==1L)"π" else "${n}π")+(if(denominator==1)"" else "/$denominator")
            }
        }
        return "%.3gπ".format(Locale.ROOT,multiple)
    }
}
