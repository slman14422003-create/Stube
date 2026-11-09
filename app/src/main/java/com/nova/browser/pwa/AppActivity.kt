package com.nova.browser

import android.Manifest
import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.app.PendingIntent
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.graphics.drawable.Icon
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.compose.runtime.mutableLongStateOf
import kotlin.math.abs
import kotlin.math.roundToInt
import android.os.Bundle
import android.util.Rational
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.delay

class AppActivity : ComponentActivity() {
    companion object {
        private var seq = 9000
        @Volatile var initialized = false
        private const val ACT_PLAY = "nova.apppip.play"
        private const val ACT_PAUSE = "nova.apppip.pause"
        private const val ACT_BACK = "nova.apppip.back"
        private const val ACT_FWD = "nova.apppip.fwd"

        fun intentFor(ctx: Context, home: String): Intent =
            Intent(Intent.ACTION_VIEW, Uri.parse(home), ctx, AppActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT or Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    private val tab = BrowserTab(seq++)
    private var startUrl = ""
    private var appName = ""
    private var fileCb: ((Array<Uri>?) -> Unit)? = null
    private var inPip by mutableStateOf(false)
    private var geckoFull by mutableStateOf(false)
    private var configTick by mutableStateOf(0)
    private var hostKey = ""
    private var optOrient by mutableIntStateOf(0)
    private var optAwake by mutableStateOf(false)
    private var optImmersive by mutableStateOf(false)
    private var optPull by mutableStateOf(true)
    private var optSheet by mutableStateOf(false)
    private var ytDlUrl by mutableStateOf<String?>(null)
    private var optMode by mutableIntStateOf(0)
    private var optShorts by mutableStateOf(false)
    private var optFocus by mutableStateOf(false)
    private var optQMobile by mutableIntStateOf(0)
    private var optQWifi by mutableIntStateOf(0)
    private var optDim by mutableStateOf(false)
    private var optGestures by mutableStateOf(true)
    private var sleepMin by mutableIntStateOf(0)
    private var sleepEnd by mutableLongStateOf(0L)
    private var sleepLeft by mutableIntStateOf(0)
    private val mainH = Handler(Looper.getMainLooper())
    private val sleepRun = Runnable { sleepFire() }
    private val hudHide = Runnable { hud = null }
    private var hud by mutableStateOf<Pair<Int, Float>?>(null)   // (1 = صوت، 2 = سطوع) + النسبة
    private var gMode = 0
    private var gActive = false
    private var gDownX = 0f
    private var gDownY = 0f
    private var gBase = 0f
    private var pipReceiverOn = false
    private var netCb: ConnectivityManager.NetworkCallback? = null
    private var lastMetered: Boolean? = null
    private val netRetry by lazy { NetRetry(this) { listOf(tab) } }
    private var wasPip = false
    private val permQueue = ArrayDeque<Pair<List<String>, () -> Unit>>()
    private var permBusy = false
    private var permCur: (() -> Unit)? = null

    private val fileLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val d = res.data
        val out: Array<Uri>? = if (res.resultCode != RESULT_OK || d == null) null else d.clipData?.let { c -> Array(c.itemCount) { c.getItemAt(it).uri } } ?: d.data?.let { arrayOf(it) }
        fileCb?.invoke(out)
        fileCb = null
    }

    private val permLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        permBusy = false
        val c = permCur
        permCur = null
        c?.invoke()
        pumpPerms()
    }

    private fun granted(p: String) = ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED

    private fun pumpPerms() {
        if (permBusy) return
        val next = permQueue.removeFirstOrNull() ?: return
        val need = next.first.filter { !granted(it) }
        if (need.isEmpty()) {
            next.second()
            pumpPerms()
            return
        }
        permBusy = true
        permCur = next.second
        runCatching { permLauncher.launch(need.toTypedArray()) }.onFailure {
            permBusy = false
            permCur = null
            next.second()
            pumpPerms()
        }
    }

    private val siteDecisions = HashMap<String, Boolean>()

    private fun askSite(origin: String, what: String, result: (Boolean) -> Unit) {
        val host = runCatching { Uri.parse(origin).host }.getOrNull()
        val secure = origin.startsWith("https://") || host == "localhost"
        if (!secure || host.isNullOrEmpty()) { result(false); return }
        val key = "$origin|$what"
        siteDecisions[key]?.let { result(it); return }
        runCatching {
            novaDialog(this).setTitle(host).setMessage("Allow this site to use your $what?")
                .setPositiveButton("Allow") { _, _ -> siteDecisions[key] = true; result(true) }
                .setNegativeButton("Deny") { _, _ -> siteDecisions[key] = false; result(false) }
                .setOnCancelListener { result(false) }
                .show()
        }.onFailure { result(false) }
    }

    private fun askPerms(perms: List<String>, done: () -> Unit) {
        permQueue.addLast(perms to done)
        pumpPerms()
    }

    private fun boot() {
        val c = applicationContext
        CrashLog.install(c)
        Robust.init(c)
        Prefs.init(c)
        SiteStore.init(c)
        GoogleAccounts.init(c)
        Adaptive.init(c)
        Library.init(c)
        Work.cpu { Vault.init(c) }
        Security.init(c)
        Perf.init(c)
        Shield.init(c)
        NetKit.init(c)
        Downloader.init(c)
        Notif.ensureChannels(c)
        initialized = true
    }

    private fun openExternal(url: String) {
        runCatching {
            startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    .addCategory(Intent.CATEGORY_BROWSABLE)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    private fun setOrient(v: Int) {
        optOrient = v
        AppPrefs.setOrient(this, hostKey, v)
        if (!geckoFull) requestedOrientation = AppPrefs.orientFlag(v)
    }

    private fun setAwake(v: Boolean) {
        optAwake = v
        AppPrefs.setKeepAwake(this, hostKey, v)
        if (v) window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    // ───────── خيارات إضافية للتطبيق (الأوضاع، الجودة، مؤقّت النوم، المشاركة، الإيماءات) ─────────

    /** أي تعديل يدوي بعد اختيار وضع يُلغي تمييز الوضع (يصير «مخصّصاً»). */
    private fun manual() {
        if (optMode != AppModes.CUSTOM) {
            optMode = AppModes.CUSTOM
            AppPrefs.setMode(this, hostKey, AppModes.CUSTOM)
        }
    }

    private fun applyImmersive(v: Boolean) { optImmersive = v; AppPrefs.setImmersive(this, hostKey, v) }
    private fun setPull(v: Boolean) { optPull = v; AppPrefs.setPull(this, hostKey, v) }
    private fun setShorts(v: Boolean) { optShorts = v; AppPrefs.setShorts(this, hostKey, v); pushOpts() }
    private fun setFocus(v: Boolean) { optFocus = v; AppPrefs.setFocus(this, hostKey, v); pushOpts() }
    private fun setQuality(mobile: Int, wifi: Int) {
        optQMobile = mobile; optQWifi = wifi
        AppPrefs.setQuality(this, hostKey, mobile, wifi)
        pushOpts()
    }
    private fun setDim(v: Boolean) { optDim = v; AppPrefs.setDim(this, hostKey, v); applyDim() }
    private fun setGestures(v: Boolean) { optGestures = v; AppPrefs.setGestures(this, hostKey, v) }

    private fun applyDim() {
        window.attributes = window.attributes.apply {
            screenBrightness = if (optDim) 0.18f else android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        }
    }

    private fun applyMode(m: Int) {
        val b = AppModes.bundle(m, Prefs.ytNoShorts)
        setOrient(b.orient); setAwake(b.awake); applyImmersive(b.immersive); setPull(b.pull)
        setDim(b.dim); setGestures(b.gestures)
        if (tab.yt) { optShorts = b.shorts; AppPrefs.setShorts(this, hostKey, b.shorts); optFocus = b.focus; AppPrefs.setFocus(this, hostKey, b.focus); setQuality(b.qMobile, b.qWifi) }
        optMode = m
        AppPrefs.setMode(this, hostKey, m)
    }

    private fun qualityJs(i: Int) = when (i) { 1 -> "360"; 2 -> "480"; 3 -> "720"; 4 -> "1080"; else -> "auto" }

    /** يدفع الخيارات الحالية (Shorts/التركيز/الجودة بحسب الشبكة) إلى صفحة يوتيوب؛ آمن إن لم تكن الصفحة جاهزة. */
    private fun pushOpts() {
        if (!tab.yt) return
        val metered = getSystemService(ConnectivityManager::class.java)?.isActiveNetworkMetered ?: false
        val q = qualityJs(if (metered) optQMobile else optQWifi)
        if (tab.engine != null) GeckoYt.pushOpts(tab, optShorts, optFocus, q)
    }

    private fun registerNetCb() {
        val cm = getSystemService(ConnectivityManager::class.java) ?: return
        if (netCb != null) return
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                val m = !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
                if (m != lastMetered) { lastMetered = m; runOnUiThread { pushOpts() } }
            }
        }
        runCatching { cm.registerDefaultNetworkCallback(cb); netCb = cb }
    }

    private fun unregisterNetCb() {
        val cb = netCb ?: return
        runCatching { getSystemService(ConnectivityManager::class.java)?.unregisterNetworkCallback(cb) }
        netCb = null
        lastMetered = null
    }

    private fun cycleSleep() {
        val next = when (sleepMin) { 0 -> 15; 15 -> 30; 30 -> 60; else -> 0 }
        mainH.removeCallbacks(sleepRun)
        sleepMin = next
        if (next == 0) {
            sleepEnd = 0L; sleepLeft = 0
        } else {
            sleepEnd = SystemClock.elapsedRealtime() + next * 60_000L
            sleepLeft = next * 60
            mainH.postDelayed(sleepRun, next * 60_000L)
            toast(this, L("سيتوقف التشغيل بعد ") + next + " " + L("دقيقة"))
        }
    }

    private fun sleepFire() {
        sleepMin = 0; sleepEnd = 0L; sleepLeft = 0
        if (YtAudio.active) YtAudio.control("pause")
        else if (YtMedia.owner === tab) YtMedia.control("pause")
        if (GeckoYt.owns(tab)) GeckoYt.control(tab, "pause")
        window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        toast(this, L("انتهى مؤقّت النوم وأُوقف التشغيل"))
    }

    private fun currentPosSec(): Int = when {
        YtAudio.active -> (YtAudio.posMs / 1000L).toInt()
        YtMedia.owner === tab -> (YtMedia.pos / 1000L).toInt()
        else -> 0
    }

    private fun ytVideoId(u: String): String? {
        val p = runCatching { Uri.parse(u) }.getOrNull() ?: return null
        val h = (p.host ?: return null).removePrefix("www.")
        val seg = p.pathSegments
        val id = when {
            h == "youtu.be" -> seg.firstOrNull()
            seg.size >= 2 && (seg[0] == "shorts" || seg[0] == "live") -> seg[1]
            else -> p.getQueryParameter("v")
        }
        return id?.takeIf { Regex("[A-Za-z0-9_-]{11}").matches(it) }
    }

    /** رابط المشاركة: لفيديو يوتيوب رابط قصير نظيف (بدل m.youtube.com وملحقاته) مع اختيار إضافة الوقت الحالي. */
    private fun shareLink(withTime: Boolean): String {
        val cur = tab.url.ifBlank { startUrl }
        val id = if (isYtVideo(cur)) ytVideoId(cur) else null
        if (id == null) return cur
        val t = if (withTime) currentPosSec() else 0
        return "https://youtu.be/$id" + (if (t > 5) "?t=$t" else "")
    }

    private fun ytAppInstalled() = runCatching { packageManager.getPackageInfo("com.google.android.youtube", 0); true }.getOrDefault(false)

    private fun openYtApp() {
        val u = shareLink(true)
        YtMedia.control("pause")
        val ok = runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u)).setPackage("com.google.android.youtube").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.isSuccess
        if (!ok) toast(this, L("تعذّر فتح تطبيق يوتيوب"))
    }

    private fun startAudioOnly() {
        val u = tab.url; val p = YtMedia.pos
        YtMedia.control("pause")
        YtAudio.start(this, u, p, YtMedia.title, YtMedia.artist)
        tab.engine?.load("https://m.youtube.com/")
    }

    // ───────── إيماءات الحواف في ملء الشاشة: يسار = سطوع، يمين = صوت ─────────
    private fun audioMgr() = getSystemService(AudioManager::class.java)

    private fun volFrac(): Float {
        val a = audioMgr() ?: return 0f
        val m = a.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        return if (m > 0) a.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / m else 0f
    }

    private fun setVolFrac(f: Float) {
        val a = audioMgr() ?: return
        val m = a.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        a.setStreamVolume(AudioManager.STREAM_MUSIC, (f * m).roundToInt().coerceIn(0, m), 0)
    }

    private fun brightFrac(): Float {
        val b = window.attributes.screenBrightness
        if (b >= 0f) return b
        return runCatching {
            android.provider.Settings.System.getInt(contentResolver, android.provider.Settings.System.SCREEN_BRIGHTNESS) / 255f
        }.getOrDefault(0.5f)
    }

    private fun setBrightFrac(f: Float) {
        window.attributes = window.attributes.apply { screenBrightness = f.coerceIn(0.02f, 1f) }
    }

    /** 0 = مرّر الحدث كما هو، 1 = بدأت إيماءة الآن (نُلغي اللمسة عند الصفحة ونستهلك)، 2 = استهلك. */
    private fun edgeGesture(ev: MotionEvent): Int {
        val v = window.decorView
        val w = v.width.toFloat()
        val h = v.height.toFloat()
        if (w <= 0f || h <= 0f) return 0
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                gActive = false
                gDownX = ev.x; gDownY = ev.y
                gMode = when { ev.x < w * 0.16f -> 2; ev.x > w * 0.84f -> 1; else -> 0 }
                if (gMode != 0) gBase = if (gMode == 1) volFrac() else brightFrac()
                return 0
            }
            MotionEvent.ACTION_MOVE -> {
                if (gMode == 0) return 0
                if (ev.pointerCount > 1) return if (gActive) 2 else 0
                val dy = gDownY - ev.y
                val dx = ev.x - gDownX
                var first = false
                if (!gActive) {
                    val slop = ViewConfiguration.get(this).scaledTouchSlop * 2f
                    if (abs(dy) < slop || abs(dy) < abs(dx) * 1.5f) return 0
                    gActive = true
                    first = true
                }
                val f = (gBase + dy / (h * 0.7f)).coerceIn(0f, 1f)
                if (gMode == 1) setVolFrac(f) else setBrightFrac(f)
                hud = gMode to f
                mainH.removeCallbacks(hudHide)
                return if (first) 1 else 2
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val was = gActive
                gActive = false
                gMode = 0
                if (was) { mainH.removeCallbacks(hudHide); mainH.postDelayed(hudHide, 700L) }
                return if (was) 2 else 0
            }
        }
        return 0
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (optGestures && geckoFull && !inPip && tab.yt) {
            when (edgeGesture(ev)) {
                1 -> {
                    val c = MotionEvent.obtain(ev)
                    c.action = MotionEvent.ACTION_CANCEL
                    super.dispatchTouchEvent(c)
                    c.recycle()
                    return true
                }
                2 -> return true
            }
        } else if (gActive || gMode != 0) {
            gActive = false
            gMode = 0
        }
        return super.dispatchTouchEvent(ev)
    }

    // ───────── أزرار النافذة المنبثقة (رجوع/تشغيل-إيقاف/تقديم) ─────────
    private val pipReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) {
            when (i?.action) {
                ACT_PLAY -> YtMedia.control("play")
                ACT_PAUSE -> YtMedia.control("pause")
                ACT_BACK -> YtMedia.control("back")
                ACT_FWD -> YtMedia.control("fwd")
            }
            window.decorView.postDelayed({ refreshPip() }, 350)   // تبديل أيقونة التشغيل/الإيقاف بعد استجابة الصفحة
        }
    }

    private fun pipAction(icon: Int, label: String, act: String, code: Int): RemoteAction {
        val pi = PendingIntent.getBroadcast(this, code, Intent(act).setPackage(packageName), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return RemoteAction(Icon.createWithResource(this, icon), label, label, pi)
    }

    private fun pipActions(): List<RemoteAction> = listOf(
        pipAction(android.R.drawable.ic_media_rew, L("رجوع 10 ثوانٍ"), ACT_BACK, 31),
        if (YtMedia.playing) pipAction(android.R.drawable.ic_media_pause, L("إيقاف مؤقت"), ACT_PAUSE, 32)
        else pipAction(android.R.drawable.ic_media_play, L("تشغيل"), ACT_PLAY, 33),
        pipAction(android.R.drawable.ic_media_ff, L("تقديم 10 ثوانٍ"), ACT_FWD, 34)
    )

    private fun canPip() = tab.yt && YtMedia.owner === tab && YtMedia.playing && isYtVideo(tab.url)

    private fun pipParams(): PictureInPictureParams {
        val w = YtMedia.vw
        val h = YtMedia.vh
        val raw = if (w > 0 && h > 0) Rational(w, h) else Rational(16, 9)
        val f = raw.toFloat()
        val ratio = if (f > 2.39f) Rational(239, 100) else if (f < 0.4184f) Rational(100, 239) else raw
        val b = PictureInPictureParams.Builder().setAspectRatio(ratio)
        b.setAutoEnterEnabled(Prefs.autoPip && canPip()).setSeamlessResizeEnabled(false)
        if (tab.yt && YtMedia.owner === tab) runCatching { b.setActions(pipActions()) }
        return b.build()
    }

    private fun refreshPip() {
        runCatching { setPictureInPictureParams(pipParams()) }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: android.content.res.Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        inPip = isInPictureInPictureMode
        wasPip = wasPip || isInPictureInPictureMode
        GeckoYt.pip = isInPictureInPictureMode
        // عودة المستخدم للواجهة (توسيع المنبثقة): لا يُعدّ أي إيقاف لاحق إغلاقاً للمنبثقة، وإلا يتوقف الصوت عند الخروج العادي
        if (!isInPictureInPictureMode) window.decorView.postDelayed({
            if (lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) wasPip = false
        }, 500)
        if (GeckoYt.owns(tab)) GeckoYt.onPip(tab, isInPictureInPictureMode, geckoFull)
    }

    private fun reapplyInsets() {
        val root = window.decorView
        ViewCompat.requestApplyInsets(root)
        root.requestLayout()
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        if (inPip || isInPictureInPictureMode) return
        configTick++
        val root = window.decorView
        for (d in longArrayOf(0L, 120L, 400L, 900L)) root.postDelayed({
            if (isFinishing || isDestroyed) return@postDelayed
            reapplyInsets()
            tab.engineView?.let { it.requestLayout(); it.invalidate() }
        }, d)
        if (GeckoYt.owns(tab)) GeckoYt.afterRotate(tab)
    }

    override fun onStart() {
        super.onStart()
        netRetry.start()
        registerNetCb()
        tab.engine?.setActive(true)
        if (!inPip && GeckoYt.owns(tab)) {
            GeckoYt.background(tab, false)
            GeckoYt.recover(tab)
        }
    }

    override fun onResume() {
        super.onResume()
        if (GeckoYt.owns(tab)) GeckoYt.pushCfg(tab)
    }

    override fun onPause() {
        super.onPause()
        if (Prefs.ytBg && YtMedia.owner === tab) {
            if (GeckoYt.owns(tab)) GeckoYt.background(tab, true)
        }
    }

    override fun onStop() {
        super.onStop()
        netRetry.stop()
        unregisterNetCb()
        if (wasPip && !isInPictureInPictureMode && !isFinishing) {
            wasPip = false
            inPip = false
            GeckoYt.pip = false
            YtMedia.stop(pause = true)
            return
        }
        val live = Prefs.ytBg && YtMedia.owner === tab
        if (Prefs.pauseBg && !geckoFull && !live) tab.engine?.setActive(false)
        if (live && GeckoYt.owns(tab)) GeckoYt.background(tab, true)
    }

    override fun onDestroy() {
        mainH.removeCallbacks(sleepRun)
        mainH.removeCallbacks(hudHide)
        if (pipReceiverOn) { runCatching { unregisterReceiver(pipReceiver) }; pipReceiverOn = false }
        if (YtMedia.owner === tab) YtMedia.killPlayback()
        GeckoYt.unregister(this)
        GeckoPage.unregister(this)
        GeckoYt.forget(tab)
        GeckoPage.forget(tab)
        tab.engine?.close()
        tab.engine = null
        tab.engineView = null
        super.onDestroy()
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        boot()
        window.attributes = window.attributes.apply {
            rotationAnimation = android.view.WindowManager.LayoutParams.ROTATION_ANIMATION_CROSSFADE
            layoutInDisplayCutoutMode = android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }
        val data = intent?.data
        if (data == null || (data.scheme != "https" && data.scheme != "http")) {
            finishAndRemoveTask()
            return
        }
        startUrl = data.toString()
        val host = hostOf(startUrl)
        appName = prettyAppName(host, Pwa.nameFor(host))
        hostKey = host.removePrefix("www.")
        optOrient = AppPrefs.orient(this, hostKey)
        optAwake = AppPrefs.keepAwake(this, hostKey)
        optImmersive = AppPrefs.immersive(this, hostKey)
        optPull = AppPrefs.pull(this, hostKey)
        optMode = AppPrefs.mode(this, hostKey)
        optShorts = AppPrefs.shorts(this, hostKey)
        optFocus = AppPrefs.focus(this, hostKey)
        optQMobile = AppPrefs.qMobile(this, hostKey)
        optQWifi = AppPrefs.qWifi(this, hostKey)
        optDim = AppPrefs.dim(this, hostKey)
        optGestures = AppPrefs.gestures(this, hostKey)
        if (optDim) applyDim()
        if (GeckoYt.isYtUrl(startUrl)) {
            tab.yt = true
        }
        GeckoPage.register(this) { tab }
        GeckoYt.register(this, applicationContext, find = { tab }, onState = { refreshPip() }, onLoaded = { pushOpts() })
        pipReceiverOn = runCatching {
            val f = IntentFilter().apply { addAction(ACT_PLAY); addAction(ACT_PAUSE); addAction(ACT_BACK); addAction(ACT_FWD) }
            ContextCompat.registerReceiver(this, pipReceiver, f, ContextCompat.RECEIVER_NOT_EXPORTED)
        }.isSuccess
        requestedOrientation = AppPrefs.orientFlag(optOrient)
        if (optAwake) window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // حركة فتح/إغلاق ناعمة للتطبيق المثبّت بدل قفزة النظام الافتراضية
        runCatching {
            overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, R.anim.nova_panel_in, R.anim.nova_hold)
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, R.anim.nova_hold, R.anim.nova_panel_out)
        }
        tab.app = true
        tab.appHome = startUrl
        tab.url = startUrl
        tab.title = appName

        val handlers = Handlers(
            activity = this,
            openTab = { openExternal(it) },
            onDownload = { u, ua, cd, mime, ref ->
                if (!Shield.downloadAllowed(hostOf(ref ?: u))) toast(this, "Download blocked")
                else if (u.startsWith("data:", true)) BlobDownload.start(this, u, cd, mime)
                else if (u.startsWith("blob:", true)) toast(this, "Download failed")
                else {
                    Downloader.start(this, u, ua, cd, mime, ref)
                    toast(this, "Download started")
                }
            },
            filePick = { intent, cb ->
                fileCb?.invoke(null)
                fileCb = cb
                runCatching { fileLauncher.launch(intent) }.onFailure { cb(null); fileCb = null }
            },
            androidPerm = { perms, cb -> runOnUiThread { askPerms(perms, cb) } },
            sitePerm = { origin, label, perms, done ->
                runOnUiThread {
                    askSite(origin, label) { allow ->
                        if (!allow) done(false) else askPerms(perms) { done(perms.isEmpty() || perms.any { granted(it) }) }
                    }
                }
            },
            onGeckoFullScreen = { geckoFull = it },
            onYtState = { refreshPip() }
        )

        setContent {
            NovaTheme(this) {
                AppHost(handlers)
            }
        }
    }

    @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
    @androidx.compose.runtime.Composable
    private fun AppHost(handlers: Handlers) {
        val cs = MaterialTheme.colorScheme
        // YouTube: until the page colour is sampled, use YouTube's own header colour (not the system background) so the status bar never looks like a separate black strip
        val bg = tab.topColor?.let { Color(it) } ?: if (tab.yt) (if (cs.background.luminance() < 0.5f) Color(0xFF0F0F0F) else Color.White) else cs.background
        val fullscreen = geckoFull
        @Suppress("UNUSED_VARIABLE") val tick = configTick
        var splash by remember { mutableStateOf(true) }
        val icon = remember { Favicons.get(hostOf(startUrl)) }

        LaunchedEffect(tab.progress, tab.loading) {
            if ((!tab.loading && tab.progress > 0f) || tab.progress >= 0.7f) splash = false
        }
        LaunchedEffect(Unit) {
            delay(6000)
            splash = false
        }
        // العدّ التنازلي لمؤقّت النوم يُحدَّث فقط والورقة مفتوحة
        LaunchedEffect(optSheet, sleepEnd) {
            while (optSheet && sleepEnd > 0L) {
                sleepLeft = ((sleepEnd - SystemClock.elapsedRealtime()) / 1000L).toInt().coerceAtLeast(0)
                delay(1000)
            }
        }
        // خيارات يوتيوب الحية (Shorts/التركيز/الجودة) تُدفع عند اكتمال التحميل وعند التنقل
        LaunchedEffect(tab.loading, tab.url, tab.epoch) { if (tab.yt) pushOpts() }
        val ytAppOk = remember { ytAppInstalled() }
        LaunchedEffect(Unit) {
            snapshotFlow { tab.topColor }.collect { c ->
                val color = (c ?: android.graphics.Color.BLACK) or (0xFF shl 24)
                window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(color))
                runCatching {
                    @Suppress("DEPRECATION")
                    setTaskDescription(ActivityManager.TaskDescription(appName, Favicons.get(hostOf(startUrl)), color))
                }
            }
        }
        SideEffect {
            val c = WindowInsetsControllerCompat(window, window.decorView)
            c.isAppearanceLightStatusBars = bg.luminance() > 0.5f
            c.isAppearanceLightNavigationBars = bg.luminance() > 0.5f
        }
        DisposableEffect(fullscreen) {
            val c = WindowInsetsControllerCompat(window, window.decorView)
            if (fullscreen) {
                c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                c.hide(WindowInsetsCompat.Type.systemBars())
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            }
            onDispose {
                c.show(WindowInsetsCompat.Type.systemBars())
                requestedOrientation = AppPrefs.orientFlag(optOrient)
                window.decorView.post { reapplyInsets() }
            }
        }

        LaunchedEffect(optImmersive, fullscreen) {
            if (fullscreen) return@LaunchedEffect
            val c = WindowInsetsControllerCompat(window, window.decorView)
            if (optImmersive) {
                c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                c.hide(WindowInsetsCompat.Type.systemBars())
            } else c.show(WindowInsetsCompat.Type.systemBars())
        }
        // الفراغ الأسود تحت صفحة التطبيق: مع تنقّل الإيماءات يُحجز ارتفاع شريط النظام السفلي كحشوة فارغة بلون الخلفية.
        // نترك الصفحة تمتدّ خلفه (الإيماءات شفافة) ونُبقي الحشوة السفلية فقط مع أزرار التنقل الثلاثة؛ الأعلى والجوانب دائماً محفوظة.
        val density = androidx.compose.ui.platform.LocalDensity.current
        val navBottom = WindowInsets.navigationBars.getBottom(density)
        val gestureNav = navBottom <= with(density) { 34.dp.roundToPx() }
        val safe = WindowInsets.systemBars.union(WindowInsets.displayCutout).let {
            if (gestureNav) it.only(androidx.compose.foundation.layout.WindowInsetsSides.Horizontal + androidx.compose.foundation.layout.WindowInsetsSides.Top) else it
        }
        val miniOn = YtAudio.active && !inPip && !fullscreen
        val navDp = with(density) { navBottom.toDp() }
        val miniPad = if (miniOn) YT_MINI_BAR_HEIGHT + (if (gestureNav) navDp else 0.dp) else 0.dp
        val primaryInt = cs.primary.toArgb()
        val bgInt = cs.surfaceContainerHigh.toArgb()
        val spinnerInt = cs.onSurface.toArgb()

        BackHandler(enabled = optSheet) { optSheet = false }
        BackHandler(enabled = fullscreen) { tab.engine?.exitFullScreen() }
        BackHandler(enabled = !fullscreen && !optSheet && tab.canBack) { tab.engine?.back() }
        BackHandler(enabled = !fullscreen && !optSheet && !tab.canBack) { finishAndRemoveTask() }

        Box(Modifier.fillMaxSize().background(bg)) {
        Box(
            Modifier
                .fillMaxSize()
                .then(
                    if (fullscreen || inPip) Modifier
                    else if (optImmersive) Modifier.windowInsetsPadding(WindowInsets.displayCutout).imePadding()
                    else Modifier
                        .windowInsetsPadding(safe)
                        .imePadding()
                )
        ) {
            key(tab.epoch) {
                AndroidView(
                    modifier = Modifier.fillMaxSize().padding(bottom = miniPad),
                    factory = { ctx -> geckoContainer(ctx, tab, handlers, primaryInt, bgInt, spinnerInt).also { (it as? SwipeRefreshLayout)?.isEnabled = optPull && !tab.yt } },
                    update = { v -> (v as? SwipeRefreshLayout)?.isEnabled = optPull }
                )
            }

            val insetPx = if (gestureNav && !fullscreen && !inPip) navDp.value else 0f
            LaunchedEffect(insetPx, tab.epoch) {
                if (tab.yt) {
                    if (GeckoYt.owns(tab)) GeckoYt.setInset(tab, insetPx)
                }
            }

            if (tab.loading && !inPip && !fullscreen) {
                Box(
                    Modifier
                        .align(Alignment.TopStart)
                        .fillMaxWidth(tab.progress.coerceIn(0.04f, 1f))
                        .height(2.dp)
                        .background(cs.primary)
                )
            }

            if (miniOn) {
                YtMiniBar(Modifier.align(Alignment.BottomCenter), bottomInset = if (gestureNav) navDp else 0.dp) {
                    val u = YtAudio.watchUrl()
                    YtAudio.close()
                    tab.engine?.load(u)
                }
            }

            // مؤشّر السطوع/الصوت أثناء إيماءات الحواف
            hud?.let { (kind, frac) ->
                if (fullscreen) {
                    Box(
                        Modifier.align(Alignment.TopCenter).padding(top = 28.dp).clip(RoundedCornerShape(50))
                            .background(Color.Black.copy(alpha = 0.62f)).padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
                            OptIcon(if (kind == 1) OptGlyph.Volume else OptGlyph.Sun, Color.White, 20.dp)
                            androidx.compose.foundation.layout.Spacer(Modifier.width(10.dp))
                            Box(Modifier.width(120.dp).height(5.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.3f))) {
                                Box(Modifier.fillMaxWidth(frac.coerceIn(0.03f, 1f)).height(5.dp).background(Color.White))
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(visible = splash && !inPip, exit = fadeOut(androidx.compose.animation.core.tween(Adaptive.ms(220))) + androidx.compose.animation.scaleOut(androidx.compose.animation.core.tween(Adaptive.ms(260), easing = NovaMotion.emphasized), targetScale = 1.04f), modifier = Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().background(cs.background), contentAlignment = Alignment.Center) {
                    SplashMark(icon, appName)
                }
            }
        }
        if (!inPip && !fullscreen && !splash) {
            // Options handle: shown as a hint for a few seconds after launch, then invisible so it never sits on top of the page header.
            // The touch target is a thin strip at the very top edge (14dp) where page buttons are not.
            var hint by remember { mutableStateOf(true) }
            LaunchedEffect(Unit) { delay(2800); hint = false }
            val hintA by androidx.compose.animation.core.animateFloatAsState(if (hint || optSheet) 1f else 0f, androidx.compose.animation.core.tween(400), label = "handle")
            Box(
                Modifier.align(Alignment.TopCenter)
                    .then(if (optImmersive) Modifier.windowInsetsPadding(WindowInsets.displayCutout) else Modifier.windowInsetsPadding(safe))
                    .width(72.dp).height(14.dp)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { optSheet = true },
                contentAlignment = Alignment.TopCenter
            ) {
                if (hintA > 0.01f) Box(Modifier.padding(top = 3.dp).width(30.dp).height(4.dp).clip(RoundedCornerShape(50)).background(cs.onBackground.copy(alpha = 0.3f * hintA)))
            }
        }
        val onVideo = tab.yt && isYtVideo(tab.url)
        AppOptionsSheet(
            visible = optSheet && !inPip && !fullscreen,
            o = AppOpts(
                name = appName, host = hostKey, icon = icon,
                isYt = tab.yt, canAudio = onVideo, canDownload = onVideo, canShareAt = onVideo, shareAtLabel = fmtTime(currentPosSec()), canYtApp = tab.yt && ytAppOk,
                orient = optOrient, awake = optAwake, immersive = optImmersive, pull = optPull,
                mode = optMode, shorts = optShorts, focus = optFocus, qMobile = optQMobile, qWifi = optQWifi,
                dim = optDim, gestures = optGestures, sleepMin = sleepMin, sleepLeftSec = sleepLeft
            ),
            a = AppOptActions(
                onOrient = { manual(); setOrient((optOrient + 1) % 3) },
                onAwake = { manual(); setAwake(it) },
                onImmersive = { manual(); applyImmersive(it) },
                onPull = { manual(); setPull(it) },
                onMode = { applyMode(it) },
                onShorts = { manual(); setShorts(it) },
                onFocus = { manual(); setFocus(it) },
                onQMobile = { manual(); setQuality((optQMobile + 1) % 5, optQWifi) },
                onQWifi = { manual(); setQuality(optQMobile, (optQWifi + 1) % 5) },
                onDim = { manual(); setDim(it) },
                onGestures = { manual(); setGestures(it) },
                onSleep = { cycleSleep() },
                onReload = { tab.engine?.reload() },
                onShare = { shareText(this@AppActivity, shareLink(false)) },
                onCopy = { copyText(this@AppActivity, shareLink(false)) },
                onOpenNova = { openExternal(tab.url.ifBlank { startUrl }) },
                onShareAt = { shareText(this@AppActivity, shareLink(true)) },
                onOpenYtApp = { openYtApp() },
                onAudioOnly = { startAudioOnly() },
                onDownload = { ytDlUrl = tab.url },
                onRemove = { Pwa.togglePin(this@AppActivity, startUrl, appName); finishAndRemoveTask() }
            ),
            onDismiss = { optSheet = false }
        )
        ytDlUrl?.let { u -> YtDownloadSheet(u, onDismiss = { ytDlUrl = null }, onStarted = { }) }
        }
    }
}
