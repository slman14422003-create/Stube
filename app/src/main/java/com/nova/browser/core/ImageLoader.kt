package com.nova.browser

import android.app.ActivityManager
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.coroutineContext

/** Remote images: byte-sized memory LRU, shared in-flight requests, downsampled decode, idle prefetch. */
object ImageLoader {
    private const val MAX_BYTES = 8 * 1024 * 1024
    private const val IDLE_MS = 150L

    /** [full] means decoded without downsampling, so it satisfies any size. */
    private class Entry(val bmp: Bitmap, val full: Boolean) {
        fun fits(w: Int, h: Int) = full || (bmp.width >= w && bmp.height >= h)
    }

    private class Flight(var w: Int, var h: Int) {
        var waiters = 0
        lateinit var job: Deferred<Entry?>
    }

    private fun newCache(kb: Int) = object : LruCache<String, Entry>(kb) {
        override fun sizeOf(key: String, value: Entry) = (value.bmp.allocationByteCount / 1024).coerceAtLeast(1)
    }

    @Volatile private var cache = newCache(4 * 1024)
    private val flights = HashMap<String, Flight>()
    private val scope by lazy { CoroutineScope(SupervisorJob() + Work.netD) }

    fun init(app: Context) {
        // 1/16 of the app heap class, between 4 and 16 MB
        val memoryClass = app.getSystemService(ActivityManager::class.java)?.memoryClass ?: 128
        val kb = (memoryClass * 1024 / 16).coerceIn(4 * 1024, 16 * 1024)
        cache = newCache(kb)
        app.registerComponentCallbacks(object : ComponentCallbacks2 {
            override fun onTrimMemory(level: Int) {
                if (level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) cache.evictAll()
                else if (level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) cache.trimToSize(kb / 2)
            }
            override fun onConfigurationChanged(newConfig: Configuration) {}
            @Deprecated("Deprecated in Java")
            override fun onLowMemory() { cache.evictAll() }
        })
    }

    /** Memory lookup only; safe on the main thread. */
    fun cached(url: String): Bitmap? = cache.get(url)?.bmp

    /** Loads [url] decoded close to (not below) [w] x [h] pixels. Cancelling the last waiter cancels the request. */
    suspend fun load(url: String, w: Int, h: Int): Bitmap? {
        cache.get(url)?.let { if (it.fits(w, h)) return it.bmp }
        val f = synchronized(flights) {
            val f = flights[url] ?: Flight(w, h).also { n ->
                n.job = scope.async(start = CoroutineStart.LAZY) { download(url, n) }
                flights[url] = n
            }
            f.w = maxOf(f.w, w); f.h = maxOf(f.h, h); f.waiters++
            f
        }
        val e = try {
            f.job.await()
        } finally {
            synchronized(flights) {
                f.waiters--
                if (f.waiters == 0 || f.job.isCompleted) {
                    if (flights[url] === f) flights.remove(url)
                    if (f.waiters == 0) f.job.cancel()
                }
            }
        }
        // Joined too late to raise the decode size: run once more
        return if (e == null || e.fits(w, h)) e?.bmp else load(url, w, h)
    }

    /** Callback form for non-coroutine callers; [onResult] runs on a background thread. */
    fun fetch(url: String, w: Int, h: Int, onResult: (Bitmap) -> Unit) {
        scope.launch { load(url, w, h)?.let(onResult) }
    }

    private suspend fun download(url: String, f: Flight): Entry? = try {
        val c = URL(url).openConnection().apply { connectTimeout = 8000; readTimeout = 8000 }
        val bytes = try {
            coroutineContext.ensureActive()
            c.getInputStream().use { input ->
                val out = ByteArrayOutputStream()
                val buf = ByteArray(16 * 1024)
                while (true) {
                    coroutineContext.ensureActive()
                    val n = input.read(buf)
                    if (n < 0 || out.size() > MAX_BYTES) break
                    out.write(buf, 0, n)
                }
                if (out.size() > MAX_BYTES) null else out.toByteArray()
            }
        } catch (e: CancellationException) {
            (c as? HttpURLConnection)?.disconnect()
            throw e
        }
        val size = synchronized(flights) { f.w to f.h }
        bytes?.let { decodeEntry(it, size.first, size.second) }?.also { cache.put(url, it) }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        null
    }

    private fun decodeEntry(bytes: ByteArray, w: Int, h: Int): Entry? {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
        var sample = 1
        while (o.outWidth / (sample * 2) >= w && o.outHeight / (sample * 2) >= h) sample *= 2
        o.inJustDecodeBounds = false
        o.inSampleSize = sample
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)?.let { Entry(it, sample == 1) }
    }

    /** Downsampled decode for bytes fetched elsewhere (favicons). Call off the main thread. */
    fun decode(bytes: ByteArray, w: Int, h: Int): Bitmap? = decodeEntry(bytes, w.coerceAtLeast(1), h.coerceAtLeast(1))?.bmp

    /** How many items to preload for the current thermal / battery state. */
    fun prefetchCount(base: Int): Int = when (Adaptive.level) { 0 -> base; 1 -> base / 2; else -> 0 }

    /** Preloads one at a time after a short idle period, so visible images keep priority. Cancellable. */
    suspend fun prefetch(urls: List<String>, w: Int, h: Int) {
        if (urls.isEmpty() || w <= 0 || h <= 0) return
        delay(IDLE_MS)
        for (u in urls) {
            if (Adaptive.level >= 2) return
            load(u, w, h)
        }
    }
}

/** Preloads the next [count] images after the last visible row; any scroll or fling cancels it. */
@Composable
fun PrefetchImages(state: LazyListState, urls: List<String?>, w: Int, h: Int, count: Int = 6) {
    val items by rememberUpdatedState(urls)
    LaunchedEffect(state, w, h, count) {
        snapshotFlow {
            val last = state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            val n = ImageLoader.prefetchCount(count)
            if (state.isScrollInProgress || last < 0 || n == 0) emptyList()
            else items.drop(last + 1).take(n).filterNotNull()
        }.collectLatest { ImageLoader.prefetch(it, w, h) }
    }
}
