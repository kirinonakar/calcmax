package com.kirinonakar.calcmax.math
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
    @Test fun wonUsesWholeUnitsAndTaxIsSeparate() {
        val r=Money.tip(BigDecimal("50000"),BigDecimal("10"),BigDecimal("5"),3,0,whole=false)
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
    @Test fun moneyFormattingGroupsThousands() {
        assertEquals("1,234,567.89",Money.format(BigDecimal("1234567.89")))
        assertEquals("-1,234.5",Money.format(BigDecimal("-1234.50")))
        assertEquals("123.45",Money.format(BigDecimal("123.4500")))
    }
    @Test fun invalidAmountsAreRejected(){assertThrows(IllegalArgumentException::class.java){Money.tip(BigDecimal.TEN,BigDecimal.TEN,BigDecimal.ZERO,0)}}
    @Test fun halfCentRoundsUp(){assertEquals(BigDecimal("0.11"),Money.tip(BigDecimal("1.05"),BigDecimal.TEN,BigDecimal.ZERO,1).tip)}
    @Test fun wholeSharesIncreaseOnlyTheTip() {
        val r=Money.tip(BigDecimal("100"),BigDecimal("15"),BigDecimal.ZERO,2)
        assertEquals(BigDecimal("16.00"),r.tip)
        assertEquals(BigDecimal("116.00"),r.total)
        assertEquals(BigDecimal("58.00"),r.share)
        assertEquals(0,r.extraPeople)
        assertEquals(BigDecimal("16"),Money.impliedTipPercent(BigDecimal("100"),r.tip))
    }
    @Test fun wholeFixedTipsPreserveTaxAndSinglePersonAmounts() {
        val r=Money.tipFromAmount(BigDecimal("19.99"),BigDecimal("2.50"),BigDecimal("8"),3)
        assertEquals(BigDecimal("1.60"),r.tax)
        assertEquals(BigDecimal("5.41"),r.tip)
        assertEquals(BigDecimal("27.00"),r.total)
        assertEquals(BigDecimal("9.00"),r.share)
        assertEquals(0,r.extraPeople)
        val single=Money.tipFromAmount(BigDecimal("19.99"),BigDecimal("2.50"),BigDecimal("8"),1)
        assertEquals(BigDecimal("24.09"),single.total)
        assertEquals(BigDecimal("2.50"),single.tip)
        val exact=Money.tip(BigDecimal("100"),BigDecimal("20"),BigDecimal.ZERO,3)
        assertEquals(BigDecimal("20.00"),exact.tip)
        val zero=Money.tip(BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,999)
        assertEquals(BigDecimal("0.00"),zero.share)
    }
}
