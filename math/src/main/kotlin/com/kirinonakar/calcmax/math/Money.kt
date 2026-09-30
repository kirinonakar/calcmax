package com.kirinonakar.calcmax.math

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

data class TipResult(val tip:BigDecimal,val tax:BigDecimal,val total:BigDecimal,val share:BigDecimal,val extraShare:BigDecimal,val extraPeople:Int)
object Money {
    fun tip(bill:BigDecimal,percent:BigDecimal,taxPercent:BigDecimal,people:Int,places:Int=2,whole:Boolean=true):TipResult {
        require(bill.signum()>=0&&percent.signum()>=0&&taxPercent.signum()>=0){"Amounts and percentages must be non-negative"}
        require(people in 1..999){"People must be between 1 and 999"}
        require(places in 0..4)
        val tip=bill.multiply(percent).divide(BigDecimal(100)).setScale(places,RoundingMode.HALF_UP)
        return tipFromAmount(bill,tip,taxPercent,people,places,whole)
    }
    fun tipFromAmount(bill:BigDecimal,tipAmount:BigDecimal,taxPercent:BigDecimal,people:Int,places:Int=2,whole:Boolean=true):TipResult {
        require(bill.signum()>=0&&tipAmount.signum()>=0&&taxPercent.signum()>=0){"Amounts and percentages must be non-negative"}
        require(people in 1..999){"People must be between 1 and 999"}
        require(places in 0..4)
        val originalTip=tipAmount.setScale(places,RoundingMode.HALF_UP)
        val tax=bill.multiply(taxPercent).divide(BigDecimal(100)).setScale(places,RoundingMode.HALF_UP)
        val originalTotal=bill.setScale(places,RoundingMode.HALF_UP)+originalTip+tax
        val total=if(whole)originalTotal.divide(BigDecimal(people),0,RoundingMode.CEILING).multiply(BigDecimal(people)).setScale(places)else originalTotal
        val tip=originalTip+total-originalTotal
        val minor=total.movePointRight(places).toBigIntegerExact()
        val division=minor.divideAndRemainder(people.toBigInteger())
        val share=BigDecimal(division[0],places)
        return TipResult(tip,tax,total,share,share+BigDecimal.ONE.movePointLeft(places),division[1].toInt())
    }
    fun impliedTipPercent(bill:BigDecimal,tipAmount:BigDecimal):BigDecimal? {
        if(bill.signum()<=0)return null
        require(tipAmount.signum()>=0){"Amounts and percentages must be non-negative"}
        return tipAmount.multiply(BigDecimal(100)).divide(bill,MathContext.DECIMAL128).stripTrailingZeros()
    }
    fun format(value:BigDecimal):String {
        val plain=value.stripTrailingZeros().toPlainString()
        val sign=if(plain.startsWith("-"))"-" else ""
        val unsigned=if(sign.isEmpty())plain else plain.drop(1)
        val dot=unsigned.indexOf('.')
        val integer=if(dot>=0)unsigned.substring(0,dot) else unsigned
        val fraction=if(dot>=0)unsigned.substring(dot) else ""
        if(integer.length<=3)return sign+integer+fraction
        val grouped=integer.reversed().chunked(3).joinToString(",").reversed()
        return sign+grouped+fraction
    }
}
data class RateTable(val base:String,val rates:Map<String,BigDecimal>,val referenceMillis:Long,val fetchedMillis:Long) {
    companion object {const val TTL=24*60*60*1000L}
    fun due(now:Long)=now-fetchedMillis>=TTL || fetchedMillis<=0
    fun rate(from:String,to:String,precision:Int=34):BigDecimal {
        val source=rates[from] ?: throw IllegalArgumentException("No cached rate for $from")
        val target=rates[to] ?: throw IllegalArgumentException("No cached rate for $to")
        require(source.signum()>0&&target.signum()>0){"Invalid exchange rate"}
        return target.divide(source,MathContext(precision.coerceIn(3,200),RoundingMode.HALF_EVEN))
    }
}
