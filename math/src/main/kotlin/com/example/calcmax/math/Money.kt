package com.example.calcmax.math

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

data class TipResult(val tip:BigDecimal,val tax:BigDecimal,val total:BigDecimal,val share:BigDecimal,val extraShare:BigDecimal,val extraPeople:Int)
object Money {
    fun tip(bill:BigDecimal,percent:BigDecimal,taxPercent:BigDecimal,people:Int,places:Int=2):TipResult {
        require(bill.signum()>=0&&percent.signum()>=0&&taxPercent.signum()>=0){"Amounts and percentages must be non-negative"}
        require(people in 1..999){"People must be between 1 and 999"}
        require(places in 0..4)
        val tip=bill.multiply(percent).divide(BigDecimal(100)).setScale(places,RoundingMode.HALF_UP)
        val tax=bill.multiply(taxPercent).divide(BigDecimal(100)).setScale(places,RoundingMode.HALF_UP)
        val total=bill.setScale(places,RoundingMode.HALF_UP)+tip+tax
        val minor=total.movePointRight(places).toBigIntegerExact()
        val division=minor.divideAndRemainder(people.toBigInteger())
        val share=BigDecimal(division[0],places)
        return TipResult(tip,tax,total,share,share+BigDecimal.ONE.movePointLeft(places),division[1].toInt())
    }
    fun format(value:BigDecimal):String=value.stripTrailingZeros().toPlainString()
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
