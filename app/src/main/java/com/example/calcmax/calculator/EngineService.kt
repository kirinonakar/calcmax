package com.example.calcmax.calculator

import android.app.Service
import android.content.*
import android.os.*
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import org.json.JSONObject
import java.util.concurrent.Executors
import kotlinx.coroutines.*
import kotlin.coroutines.resume

/** Local process boundary permits hard cancellation of pathological CAS computations. */
class EngineService : Service() {
    private val worker = Executors.newSingleThreadExecutor()
    private val messenger = Messenger(Handler(Looper.getMainLooper()) { msg ->
        if (msg.what == 2) { android.os.Process.killProcess(android.os.Process.myPid()); true }
        else {
            val reply = msg.replyTo
            val id = msg.arg1
            val payload = msg.data.getString("payload") ?: "{}"
            worker.execute {
                val result = try {
                    if (!Python.isStarted()) Python.start(AndroidPlatform(this))
                    val module=if(JSONObject(payload).optString("action")=="python") "script_runner" else "calc_engine"
                    Python.getInstance().getModule(module).callAttr(if(module=="script_runner") "run" else "dispatch", payload).toString()
                } catch (e: Exception) { JSONObject().put("ok", false).put("error", e.message ?: "Engine error").toString() }
                runCatching { reply.send(Message.obtain(null, 1, id, 0).apply { data = Bundle().apply { putString("result", result) } }) }
            }
            true
        }
    })
    override fun onBind(intent: Intent): IBinder = messenger.binder
    override fun onCreate() {
        super.onCreate()
        worker.execute {runCatching {if(!Python.isStarted())Python.start(AndroidPlatform(this));Python.getInstance().getModule("calc_engine")}}
    }
    override fun onDestroy() { worker.shutdownNow(); super.onDestroy() }
}

class EngineClient(private val context: Context) {
    private var remote: Messenger? = null
    private var connected = CompletableDeferred<Unit>()
    private val pending = mutableMapOf<Int, CancellableContinuation<JSONObject>>()
    private var counter = 0
    private var closed = false
    private val incoming = Messenger(Handler(Looper.getMainLooper()) { msg ->
        val continuation = pending.remove(msg.arg1)
        if (continuation?.isActive == true) continuation.resume(JSONObject(msg.data.getString("result") ?: "{}"))
        true
    })
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) { remote = Messenger(binder); connected.complete(Unit) }
        override fun onServiceDisconnected(name: ComponentName) {
            remote = null; if(connected.isCompleted) connected = CompletableDeferred()
            pending.values.toList().forEach { if(it.isActive) it.resume(JSONObject().put("ok",false).put("error","Calculation cancelled or engine restarted")) }; pending.clear()
        }
        override fun onBindingDied(name: ComponentName) {
            onServiceDisconnected(name)
            if(!closed) { runCatching { context.unbindService(this) }; bind() }
        }
    }
    init { bind() }
    private fun bind() { context.bindService(Intent(context,EngineService::class.java), connection, Context.BIND_AUTO_CREATE) }
    suspend fun execute(request: JSONObject): JSONObject = withContext(Dispatchers.Main.immediate) {
        try {
            withTimeout(20000) {
                connected.await()
                suspendCancellableCoroutine { continuation ->
                    val id = ++counter
                    pending[id] = continuation
                    continuation.invokeOnCancellation { Handler(Looper.getMainLooper()).post { pending.remove(id); cancel() } }
                    try { remote!!.send(Message.obtain(null,1,id,0).apply { replyTo = incoming; data = Bundle().apply { putString("payload",request.toString()) } }) }
                    catch(e: Exception) { pending.remove(id); continuation.resume(JSONObject().put("ok",false).put("error",e.message)) }
                }
            }
        } catch(e: TimeoutCancellationException) { cancel(); JSONObject().put("ok",false).put("error","Computation timed out. Reduce complexity and try again.") }
    }
    fun cancel() {
        val process=remote ?: return
        remote=null
        if(connected.isCompleted) connected=CompletableDeferred()
        runCatching { process.send(Message.obtain(null,2)) }
    }
    fun close() { closed = true; pending.values.toList().forEach { it.cancel() }; pending.clear(); runCatching { context.unbindService(connection) } }
}
