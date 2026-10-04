package com.kirinonakar.symvacas.calculator

import android.content.Context
import com.kirinonakar.symvacas.math.RateTable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.math.BigDecimal
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/** Daily reference rates are the only network data used by the calculator. */
class ExchangeRepository(context:Context,private val clock:()->Long=System::currentTimeMillis,
    private val fetch:suspend ()->String={download()},name:String="exchange-rates") {
    private val prefs=context.getSharedPreferences(name,Context.MODE_PRIVATE)
    fun cached():RateTable?=runCatching{decode(JSONObject(prefs.getString("snapshot",null)!!))}.getOrNull()
    suspend fun refresh():RateTable=refreshLock.withLock {
        cached()?.takeUnless{it.due(clock())}?.let{return@withLock it}
        withContext(Dispatchers.IO) {
            val raw=JSONObject(fetch())
            require(raw.optString("result")=="success"&&raw.optString("base_code")=="USD"){"Currency provider is unavailable"}
            raw.put("fetchedMillis",clock())
            val table=decode(raw)
            require(table.rates["USD"]?.compareTo(BigDecimal.ONE)==0&&table.rates.size>1){"Invalid exchange rate data"}
            check(prefs.edit().putString("snapshot",raw.toString()).commit()){"Could not save offline rates"}
            table
        }
    }
    private fun decode(raw:JSONObject):RateTable {
        val values=raw.getJSONObject("rates")
        val rates=values.keys().asSequence().associateWith{key->values.get(key).toString().toBigDecimal().also{require(it.signum()>0)}}
        val reference=raw.getLong("time_last_update_unix")*1000
        require(reference>0&&rates.isNotEmpty())
        return RateTable(raw.getString("base_code"),rates,reference,raw.getLong("fetchedMillis"))
    }
    companion object {
        private val refreshLock=Mutex()
        const val SOURCE="https://www.exchangerate-api.com"
        private fun download():String {
            val connection=URL("https://open.er-api.com/v6/latest/USD").openConnection() as HttpsURLConnection
            return try {
                connection.connectTimeout=8000;connection.readTimeout=8000
                connection.setRequestProperty("Accept","application/json")
                check(connection.responseCode==200){"Online rates are temporarily unavailable"}
                connection.inputStream.bufferedReader().use {reader->
                    val content=StringBuilder();val buffer=CharArray(4096)
                    while(true){val count=reader.read(buffer);if(count<0)break;require(content.length+count<=200000){"Invalid currency response"};content.append(buffer,0,count)}
                    content.toString()
                }
            }finally{connection.disconnect()}
        }
    }
}
