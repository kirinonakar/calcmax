package com.kirinonakar.symvacas.math

import org.junit.Assert.*
import org.junit.Test

class StatisticsRequestTest {
    @Test fun largeTablesUseDecimalDatasetReferences() {
        val source="mixedmodel(["+(0 until 5000).joinToString(",") {"[${it%20},-1.25e-3,$it]"}+"],0)"
        assertThrows(IllegalArgumentException::class.java) {Parser(source).parse()}
        val request=statisticsRequest(source)
        val name=request.tree.args[0].value
        assertEquals("symbol",request.tree.args[0].kind)
        assertEquals(5000,request.datasets[name]!!.size)
        assertEquals(listOf("0","-1.25e-3","0"),request.datasets[name]!![0])
        assertTrue(request.tree.json().length<500)
    }
    @Test fun formulaListsAndExistingNamesKeepTheirMeaning() {
        val request=statisticsRequest("regression([[1,2],[3,4]],custom,SymvaStatisticsData0*x,x,[1])")
        assertEquals("SymvaStatisticsData0Data",request.tree.args[0].value)
        assertEquals(Parser("mean([1/2,2+3])").parse(),statisticsRequest("mean([1/2,2+3])").tree)
        assertThrows(IllegalArgumentException::class.java) {statisticsRequest("mean([1,,2])")}
    }
}
