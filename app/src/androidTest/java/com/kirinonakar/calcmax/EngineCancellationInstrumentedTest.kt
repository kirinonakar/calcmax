package com.kirinonakar.calcmax

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kirinonakar.calcmax.calculator.EngineClient
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Service/IPC tests: no activity or UI automation is required. */
@RunWith(AndroidJUnit4::class)
class EngineCancellationInstrumentedTest {
    private fun script(source:String)=JSONObject().put("action","python").put("source",source)
    private suspend fun pid(client:EngineClient):String {
        val response=client.execute(script("import os; print(os.getpid())"))
        assertTrue(response.toString(),response.optBoolean("ok"))
        return response.getString("output").trim()
    }
    private fun checkService(block:suspend CoroutineScope.(EngineClient)->Unit)=runBlocking {
        withTimeout(60000) {
            val context=ApplicationProvider.getApplicationContext<Context>()
            val client=withContext(Dispatchers.Main){EngineClient(context)}
            try { block(client) }
            finally { withContext(Dispatchers.Main){client.close()} }
        }
    }

    @Test fun cancellingInputKeepsTheInterpreter()=checkService { client ->
        val initial=pid(client)
        val waiting=CompletableDeferred<Unit>()
        val job=launch {
            client.execute(script("input('value=')")) { _,_,_ -> waiting.complete(Unit) }
        }
        waiting.await()
        job.cancelAndJoin()
        assertEquals(initial,pid(client))
        delay(900) // A completed input request must not trigger the kill watchdog later.
        assertEquals(initial,pid(client))
    }

    @Test fun cancellingALoopDoesNotCancelTheQueuedRequest()=checkService { client ->
        val initial=pid(client)
        val ready=CompletableDeferred<Unit>()
        val looping=launch {
            client.execute(script("input('ready');\nwhile True: pass")) { _,_,submit ->
                submit("")
                ready.complete(Unit)
            }
        }
        ready.await()
        val queued=async { pid(client) }
        delay(100)
        looping.cancelAndJoin()
        assertEquals(initial,queued.await())
        delay(900)
        assertEquals(initial,pid(client))
    }

    @Test fun cancellingAQueuedRequestLeavesTheRunningRequestAlone()=checkService { client ->
        val initial=pid(client)
        val waiting=CompletableDeferred<(String)->Unit>()
        val running=async {
            client.execute(script("input('value='); print('done')")) { _,_,submit -> waiting.complete(submit) }
        }
        val submit=waiting.await()
        val queued=launch {client.execute(script("raise AssertionError('cancelled queue ran')"))}
        delay(100)
        queued.cancelAndJoin()
        delay(900)
        withContext(Dispatchers.Main){submit("answer")}
        assertTrue(running.await().getBoolean("ok"))
        assertEquals(initial,pid(client))
    }

    @Test fun aBlockedNativeCallStillHasAHardCancellationFallback()=checkService { client ->
        val initial=pid(client)
        val ready=CompletableDeferred<Unit>()
        val blocked=launch {
            client.execute(script("input('ready'); import time; time.sleep(30)")) { _,_,submit ->
                submit("")
                ready.complete(Unit)
            }
        }
        ready.await()
        delay(150)
        blocked.cancelAndJoin()
        delay(1500)
        assertNotEquals(initial,pid(client))
    }
}
