package com.example.calcmax.math
import java.math.BigDecimal
import org.junit.Test
import org.junit.Assert.*
class MoneyTest {
    @Test fun splittingNeverLosesCents() {
        val r=Money.tip(BigDecimal("100"),BigDecimal("15"),BigDecimal.ZERO,3)
        assertEquals(BigDecimal("115.00"),r.total)
        assertEquals(BigDecimal("38.33"),r.share)
        assertEquals(1,r.extraPeople)
        assertEquals(r.total,r.share*BigDecimal(2)+r.extraShare)
    }
    @Test fun wonUsesWholeUnitsAndTaxIsSeparate() {
        val r=Money.tip(BigDecimal("50000"),BigDecimal("10"),BigDecimal("5"),3,0)
        assertEquals(BigDecimal("57500"),r.total)
        assertEquals(BigDecimal("5000"),r.tip)
        assertEquals(2,r.extraPeople)
    }
    @Test fun crossRatesAndDailyExpiry() {
        val table=RateTable("USD",mapOf("USD" to BigDecimal.ONE,"KRW" to BigDecimal("1300"),"JPY" to BigDecimal("150")),1000,2000)
        assertEquals(BigDecimal("1300"),table.rate("USD","KRW"))
        assertTrue((table.rate("JPY","KRW").multiply(BigDecimal("150"))-BigDecimal("1300")).abs()<BigDecimal("1e-28"))
        assertFalse(table.due(2000+RateTable.TTL-1));assertTrue(table.due(2000+RateTable.TTL))
    }
    @Test fun invalidAmountsAreRejected(){assertThrows(IllegalArgumentException::class.java){Money.tip(BigDecimal.TEN,BigDecimal.TEN,BigDecimal.ZERO,0)}}
    @Test fun halfCentRoundsUp(){assertEquals(BigDecimal("0.11"),Money.tip(BigDecimal("1.05"),BigDecimal.TEN,BigDecimal.ZERO,1).tip)}
}
