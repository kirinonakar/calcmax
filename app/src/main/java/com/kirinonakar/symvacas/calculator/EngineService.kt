package com.kirinonakar.symvacas.calculator

import android.app.Service
import android.content.*
import android.os.*
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import org.json.JSONObject
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.*
import kotlin.coroutines.resume

/** Polled by Python without acquiring its GIL on the Android main thread. */
class ExecutionControl {
    private val cancelled = AtomicBoolean(false)
    val started = AtomicBoolean(false)
    fun isCancelled(): Boolean = cancelled.get()
    fun cancel(): Boolean = cancelled.compareAndSet(false, true)
}

class ScriptInputBridge(private val reply: Messenger, private val id: Int, private val control: ExecutionControl) {
    private val values = LinkedBlockingQueue<String>()
    fun request(prompt: String, output: String): String {
        if(control.isCancelled())return ""
        reply.send(Message.obtain(null, 3, id, 0).apply {
            data = Bundle().apply { putString("prompt", prompt); putString("output", output) }
        })
        return values.take()
    }
    fun submit(value: String) { values.put(value) }
    fun cancel() { values.offer("") }
}

class EngineService : Service() {
    private val worker = Executors.newSingleThreadExecutor()
    private val inputBridges = ConcurrentHashMap<Int, ScriptInputBridge>()
    private val requests = ConcurrentHashMap<Int, ExecutionControl>()
    private val handler = Handler(Looper.getMainLooper())
    private fun cancelRequest(id: Int) {
        val control = requests[id] ?: return
        if(!control.cancel())return
        inputBridges[id]?.cancel()
        // Keep the interpreter and warm SymPy caches for normal cancellations.
        // Native calls or scripts which suppress cancellation still need a process boundary.
        handler.postDelayed({
            if(requests[id] === control && control.started.get())
                android.os.Process.killProcess(android.os.Process.myPid())
        }, 750)
    }
    private val messenger = Messenger(Handler(Looper.getMainLooper()) { msg ->
        if (msg.what == 2) { cancelRequest(msg.arg1); true }
        else if (msg.what == 3) { inputBridges[msg.arg1]?.submit(msg.data.getString("value") ?: ""); true }
        else {
            val reply = msg.replyTo
            val id = msg.arg1
            val payload = msg.data.getString("payload") ?: "{}"
            val control = ExecutionControl()
            requests[id] = control
            worker.execute {
                val result = try {
                    control.started.set(true)
                    check(!control.isCancelled()) { "Calculation cancelled" }
                    if (!Python.isStarted()) Python.start(AndroidPlatform(this))
                    val module=if(JSONObject(payload).optString("action")=="python") "script_runner" else "calc_engine"
                    if (module == "script_runner") {
                        val bridge = ScriptInputBridge(reply, id, control)
                        inputBridges[id] = bridge
                        try { Python.getInstance().getModule(module).callAttr("run", payload, bridge, control).toString() }
                        finally { inputBridges.remove(id) }
                    } else Python.getInstance().getModule(module).callAttr("dispatch", payload, control).toString()
                } catch (e: Exception) { JSONObject().put("ok", false).put("error", e.message ?: "Engine error").toString() }
                finally { requests.remove(id, control) }
                runCatching {
                    val compressed=EngineResultCodec.compress(result)
                    reply.send(Message.obtain(null, 1, id, 0).apply {
                        data=Bundle().apply {if(compressed==null)putString("result",result) else putByteArray("resultGzip",compressed)}
                    })
                }.onFailure {
                    // Report a transport failure immediately rather than leaving
                    // the client waiting until its calculation timeout expires.
                    runCatching {reply.send(Message.obtain(null,1,id,0).apply {
                        data=Bundle().apply {putString("result",JSONObject().put("ok",false).put("error","Could not transfer calculation result. Reduce graph density and try again.").toString())}
                    })}
                }
            }
            true
        }
    })
    override fun onBind(intent: Intent): IBinder = messenger.binder
    override fun onCreate() {
        super.onCreate()
        worker.execute {runCatching {if(!Python.isStarted())Python.start(AndroidPlatform(this));Python.getInstance().getModule("calc_engine")}}
    }
    override fun onDestroy() {
        requests.keys.toList().forEach(::cancelRequest)
        worker.shutdownNow()
        super.onDestroy()
    }
}

class EngineClient(private val context: Context) {
    private var remote: Messenger? = null
    private var connected = CompletableDeferred<Unit>()
    private val pending = mutableMapOf<Int, CancellableContinuation<JSONObject>>()
    private val inputHandlers = mutableMapOf<Int, (String, String, (String) -> Unit) -> Unit>()
    private val timeouts = mutableMapOf<Int, Runnable>()
    private val handler = Handler(Looper.getMainLooper())
    companion object { private val counter = AtomicInteger() }
    private var closed = false
    private val incoming = Messenger(Handler(Looper.getMainLooper()) { msg ->
        if (msg.what == 3) {
            timeouts.remove(msg.arg1)?.let(handler::removeCallbacks)
            val id = msg.arg1
            inputHandlers[id]?.invoke(msg.data.getString("prompt") ?: "", msg.data.getString("output") ?: "") { value ->
                if (pending[id]?.isActive == true) {
                    runCatching { remote?.send(Message.obtain(null, 3, id, 0).apply { data = Bundle().apply { putString("value", value) } }) }
                    startTimeout(id)
                }
            }
        } else {
            timeouts.remove(msg.arg1)?.let(handler::removeCallbacks)
            inputHandlers.remove(msg.arg1)
            val continuation = pending.remove(msg.arg1)
            if (continuation?.isActive == true) {
                val response=runCatching {JSONObject(EngineResultCodec.decode(msg.data.getString("result"),msg.data.getByteArray("resultGzip")))}
                    .getOrElse {JSONObject().put("ok",false).put("error","Could not read calculation result. Try again.")}
                continuation.resume(response)
            }
        }
        true
    })
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) { remote = Messenger(binder); connected.complete(Unit) }
        override fun onServiceDisconnected(name: ComponentName) {
            remote = null; if(connected.isCompleted) connected = CompletableDeferred()
            timeouts.values.forEach(handler::removeCallbacks); timeouts.clear(); inputHandlers.clear()
            pending.values.toList().forEach { if(it.isActive) it.resume(JSONObject().put("ok",false).put("error","Calculation cancelled or engine restarted")) }; pending.clear()
        }
        override fun onBindingDied(name: ComponentName) {
            onServiceDisconnected(name)
            if(!closed) { runCatching { context.unbindService(this) }; bind() }
        }
    }
    init { bind() }
    private fun bind() { context.bindService(Intent(context,EngineService::class.java), connection, Context.BIND_AUTO_CREATE) }
    private fun startTimeout(id: Int) {
        timeouts.remove(id)?.let(handler::removeCallbacks)
        val timeout = Runnable {
            pending.remove(id)?.let { if(it.isActive) it.resume(JSONObject().put("ok",false).put("error","Computation timed out. Reduce complexity and try again.")) }
            inputHandlers.remove(id); timeouts.remove(id); cancelRequest(id)
        }
        timeouts[id] = timeout
        handler.postDelayed(timeout, 20000)
    }
    suspend fun execute(request: JSONObject, onInput: ((String, String, (String) -> Unit) -> Unit)? = null): JSONObject = withContext(Dispatchers.Main.immediate) {
        try {
            withTimeout(20000) { connected.await() }
            suspendCancellableCoroutine { continuation ->
                    val id = counter.incrementAndGet()
                    pending[id] = continuation
                    if (onInput != null) inputHandlers[id] = onInput
                    continuation.invokeOnCancellation { handler.post {
                        if(pending.remove(id) != null)cancelRequest(id)
                        inputHandlers.remove(id); timeouts.remove(id)?.let(handler::removeCallbacks)
                    } }
                    try {
                        remote!!.send(Message.obtain(null,1,id,0).apply { replyTo = incoming; data = Bundle().apply { putString("payload",request.toString()) } })
                        startTimeout(id)
                    } catch(e: Exception) { pending.remove(id); inputHandlers.remove(id); continuation.resume(JSONObject().put("ok",false).put("error",e.message)) }
            }
        } catch(e: TimeoutCancellationException) { JSONObject().put("ok",false).put("error","Computation timed out. Reduce complexity and try again.") }
    }
    private fun cancelRequest(id: Int) {
        runCatching { remote?.send(Message.obtain(null,2,id,0)) }
    }
    fun cancel() {
        val cancelled=pending.toMap()
        pending.clear()
        cancelled.forEach { (id, continuation) ->
            inputHandlers.remove(id); timeouts.remove(id)?.let(handler::removeCallbacks)
            cancelRequest(id)
            if(continuation.isActive)continuation.resume(JSONObject().put("ok",false).put("error","Calculation cancelled"))
        }
    }
    fun close() { closed = true; cancel(); runCatching { context.unbindService(connection) } }
}
