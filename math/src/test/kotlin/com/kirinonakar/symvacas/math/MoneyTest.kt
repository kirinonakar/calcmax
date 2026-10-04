package com.kirinonakar.symvacas.math
import java.math.BigDecimal
import org.junit.Test
import org.junit.Assert.*
class MoneyTest {
    @Test fun splittingNeverLosesCents() {
        val r=Money.tip(BigDecimal("100"),BigDecimal("15"),BigDecimal.ZERO,3,whole=false)
        assertEquals(BigDecimal("115.00"),r.total)
        assertEquals(BigDecimal("38.33"),r.share)
        assertEquals(1,r.extraPeople)
        assertEquals(r.total,r.share*BigDecimal(2)+r.extraShare)
    }
    @Test fun crossRatesAndDailyExpiry() {
        val table=RateTable("USD",mapOf("USD" to BigDecimal.ONE,"KRW" to BigDecimal("1300"),"JPY" to BigDecimal("150")),1000,2000)
        assertEquals(BigDecimal("1300"),table.rate("USD","KRW"))
        assertTrue((table.rate("JPY","KRW").multiply(BigDecimal("150"))-BigDecimal("1300")).abs()<BigDecimal("1e-28"))
        assertFalse(table.due(2000+RateTable.TTL-1));assertTrue(table.due(2000+RateTable.TTL))
    }
    @Test fun invalidAmountsAreRejected(){assertThrows(IllegalArgumentException::class.java){Money.tip(BigDecimal.TEN,BigDecimal.TEN,BigDecimal.ZERO,0)}}
    @Test fun singlePersonWholeAmountsApplyToBothTipMethods() {
        for(whole in listOf(true,false)) {
            val percent=Money.tip(BigDecimal("19.99"),BigDecimal("15"),BigDecimal("8"),1,whole=whole)
            val fixed=Money.tipFromAmount(BigDecimal("19.99"),BigDecimal("2.50"),BigDecimal("8"),1,whole=whole)
            assertEquals(BigDecimal(if(whole)"25.00" else "24.59"),percent.total)
            assertEquals(BigDecimal(if(whole)"3.41" else "3.00"),percent.tip)
            assertEquals(BigDecimal(if(whole)"25.00" else "24.09"),fixed.total)
            assertEquals(BigDecimal(if(whole)"3.41" else "2.50"),fixed.tip)
            for(result in listOf(percent,fixed)) {
                assertEquals(result.total,result.share)
                assertEquals(BigDecimal("1.60"),result.tax)
                assertEquals(0,result.extraPeople)
                assertEquals(result.total,BigDecimal("19.99")+result.tip+result.tax)
            }
        }
    }
}
