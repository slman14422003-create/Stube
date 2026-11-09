package com.nova.browser

import android.content.Context
import android.os.StrictMode
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

object Robust {
    private var started = false

    private val handler = CoroutineExceptionHandler { _, e ->
        if (BuildConfig.DEBUG) android.util.Log.e("Nova", "coroutine failed", e)
    }

    val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Work.cpuD + handler)
    val io: CoroutineScope = CoroutineScope(SupervisorJob() + Work.ioD + handler)
    val main: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate + handler)

    fun init(c: Context) {
        if (started) return
        started = true
        if (BuildConfig.DEBUG) runCatching {
            StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.Builder().detectAll().penaltyLog().build())
            StrictMode.setVmPolicy(StrictMode.VmPolicy.Builder().detectLeakedClosableObjects().detectLeakedSqlLiteObjects().penaltyLog().build())
        }
    }

    inline fun <T> safe(default: T, block: () -> T): T =
        try { block() } catch (e: CancellationException) { throw e } catch (_: Throwable) { default }

    inline fun attempt(block: () -> Unit) {
        try { block() } catch (e: CancellationException) { throw e } catch (_: Throwable) { }
    }

    suspend fun <T> retry(times: Int = 3, delayMs: Long = 300, block: suspend () -> T): T {
        var last: Throwable? = null
        var wait = delayMs
        repeat(times) {
            try { return block() } catch (e: CancellationException) { throw e } catch (e: Throwable) { last = e }
            kotlinx.coroutines.delay(wait)
            wait *= 2
        }
        throw last ?: IllegalStateException("retry failed")
    }

    fun launchSafe(ctx: CoroutineContext = EmptyCoroutineContext, block: suspend CoroutineScope.() -> Unit): Job =
        scope.launch(ctx, block = block)

    fun shutdown() {
        scope.cancel(); io.cancel(); main.cancel()
    }
}
