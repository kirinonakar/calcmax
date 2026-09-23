package com.example.calcmax
import androidx.test.platform.app.InstrumentationRegistry
import com.example.calcmax.calculator.ExchangeRepository
import com.example.calcmax.math.RateTable
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Test
import org.junit.Assert.*
class ExchangeRepositoryTest {
    @Test fun dailyCacheSurvivesOfflineAndRepositoryRecreation()=runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val name="rates-test-${System.nanoTime()}"
        var now=1700000000000L;var calls=0;var offline=false
        val fetch:suspend ()->String={
            calls++;if(offline)throw java.io.IOException("offline")
            JSONObject().put("result","success").put("base_code","USD").put("time_last_update_unix",now/1000)
                .put("rates",JSONObject().put("USD",1).put("KRW",1300).put("EUR",0.9)).toString()
        }
        try {
            val repo=ExchangeRepository(context,{now},fetch,name)
            val original=repo.refresh();assertEquals(1,calls)
            now+=RateTable.TTL-1;assertEquals(original,repo.refresh());assertEquals(1,calls)
            val restored=ExchangeRepository(context,{now},fetch,name)
            assertEquals(original,restored.cached())
            now+=2;offline=true
            assertTrue(runCatching{restored.refresh()}.isFailure)
            assertEquals(original,restored.cached())
            offline=false;val refreshed=restored.refresh()
            assertEquals(now,refreshed.fetchedMillis);assertTrue(refreshed.referenceMillis>original.referenceMillis)
        }finally{context.deleteSharedPreferences(name)}
    }
}
