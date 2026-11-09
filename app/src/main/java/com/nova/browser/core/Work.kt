package com.nova.browser

import android.os.Handler
import android.os.Looper
import android.os.Process
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.ExecutorService
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Central, named thread pools. Every kind of work gets its own lane so a slow lane never blocks another one:
 *  - net   : HTTP calls (update check, manifests, WebView version check, thumbnails)
 *  - disk  : file reads/writes. A SINGLE thread, so writes to the same file are naturally serialized
 *  - cpu   : parsing, crypto, start-up initialisation
 *  - media : notification work
 *  - yt    : YouTube stream resolution + audio-only engine (never queued behind artwork or downloads)
 *  - img   : artwork / thumbnail decoding
 *  - io    : general blocking IO (cache size, file scans)
 *  - dns   : DNS / connection warm-up
 *  - dl    : download workers (bounded priority, grows on demand like a cached pool)
 * Threads are daemon, named (nova-<lane>-N) and run at background priority except cpu.
 * Nothing here ever touches the UI: use Work.ui { } to hop back to the main thread.
 */
object Work {
    private val mainHandler = Handler(Looper.getMainLooper())

    private fun pool(name: String, threads: Int, prio: Int): ThreadPoolExecutor {
        val n = AtomicInteger()
        val factory = ThreadFactory { r ->
            Thread({
                runCatching { Process.setThreadPriority(prio) }
                r.run()
            }, "nova-$name-${n.incrementAndGet()}").apply { isDaemon = true }
        }
        return ThreadPoolExecutor(threads, threads, 20L, TimeUnit.SECONDS, LinkedBlockingQueue(), factory)
            .apply { allowCoreThreadTimeOut(true) }
    }

    private val cores = Runtime.getRuntime().availableProcessors()

    private fun factory(name: String, prio: Int): ThreadFactory {
        val n = AtomicInteger()
        return ThreadFactory { r ->
            Thread({
                runCatching { Process.setThreadPriority(prio) }
                r.run()
            }, "nova-$name-${n.incrementAndGet()}").apply { isDaemon = true }
        }
    }

    /** Single named background thread for periodic jobs. */
    fun scheduler(name: String): java.util.concurrent.ScheduledExecutorService =
        java.util.concurrent.Executors.newSingleThreadScheduledExecutor(factory(name, Process.THREAD_PRIORITY_BACKGROUND))

    val netPool: ExecutorService by lazy { pool("net", 4, Process.THREAD_PRIORITY_BACKGROUND) }
    val diskPool: ExecutorService by lazy { pool("disk", 1, Process.THREAD_PRIORITY_BACKGROUND) }
    val cpuPool: ExecutorService by lazy { pool("cpu", (cores - 1).coerceIn(2, 4), Process.THREAD_PRIORITY_DEFAULT) }
    val mediaPool: ExecutorService by lazy { pool("media", 2, Process.THREAD_PRIORITY_BACKGROUND) }
    val ytPool: ExecutorService by lazy { pool("yt", 2, Process.THREAD_PRIORITY_DEFAULT) }
    val imgPool: ExecutorService by lazy { pool("img", 2, Process.THREAD_PRIORITY_BACKGROUND) }
    val ioPool: ExecutorService by lazy { pool("io", 3, Process.THREAD_PRIORITY_BACKGROUND) }
    val dnsPool: ExecutorService by lazy { pool("dns", 2, Process.THREAD_PRIORITY_BACKGROUND) }
    val dlPool: ExecutorService by lazy {
        ThreadPoolExecutor(0, Int.MAX_VALUE, 20L, TimeUnit.SECONDS, SynchronousQueue(), factory("dl", Process.THREAD_PRIORITY_BACKGROUND))
    }

    /** Coroutine dispatchers over the same pools (use with withContext(...)). */
    val netD: CoroutineDispatcher by lazy { netPool.asCoroutineDispatcher() }
    val diskD: CoroutineDispatcher by lazy { diskPool.asCoroutineDispatcher() }
    val cpuD: CoroutineDispatcher by lazy { cpuPool.asCoroutineDispatcher() }
    val mediaD: CoroutineDispatcher by lazy { mediaPool.asCoroutineDispatcher() }
    val ytD: CoroutineDispatcher by lazy { ytPool.asCoroutineDispatcher() }
    val ioD: CoroutineDispatcher by lazy { ioPool.asCoroutineDispatcher() }

    // A failing task must never take the whole app down: errors are swallowed (logged in debug builds only).
    private fun guard(block: () -> Unit) = Runnable {
        try { block() } catch (e: Throwable) {
            if (BuildConfig.DEBUG) android.util.Log.e("Nova", "background task failed", e)
        }
    }

    fun net(block: () -> Unit) { netPool.execute(guard(block)) }
    fun disk(block: () -> Unit) { diskPool.execute(guard(block)) }
    fun cpu(block: () -> Unit) { cpuPool.execute(guard(block)) }
    fun media(block: () -> Unit) { mediaPool.execute(guard(block)) }
    fun yt(block: () -> Unit) { ytPool.execute(guard(block)) }
    fun img(block: () -> Unit) { imgPool.execute(guard(block)) }
    fun io(block: () -> Unit) { ioPool.execute(guard(block)) }
    fun dns(block: () -> Unit) { dnsPool.execute(guard(block)) }

    /** Runs on the main thread (immediately when already there). */
    fun ui(block: () -> Unit) {
        if (Looper.myLooper() === Looper.getMainLooper()) block() else mainHandler.post(block)
    }

    fun uiDelayed(ms: Long, block: () -> Unit) { mainHandler.postDelayed(block, ms) }
}
