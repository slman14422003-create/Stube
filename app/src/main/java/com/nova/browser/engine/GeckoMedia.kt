package com.nova.browser

import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.MediaSession

/**
 * مستمع جلسة الوسائط في Gecko. وجوده يبقي Gecko يعامل الصفحة كمشغّل وسائط (يمنع تعليق الصوت أثناء الخلفية).
 * إشعار الوسائط نفسه لا يأتي من هنا بل من حالة yt.js (YtMedia.updateGecko)؛ هنا نتتبّع التشغيل فقط
 * ليعمل `tab.ytPlaying` حتى قبل وصول أول رسالة حالة، ونوفّر أوامر تحكم احتياطية.
 */
class GeckoMedia(private val tab: BrowserTab) : MediaSession.Delegate {
    @Volatile private var media: MediaSession? = null

    override fun onActivated(session: GeckoSession, mediaSession: MediaSession) {
        media = mediaSession
    }

    override fun onDeactivated(session: GeckoSession, mediaSession: MediaSession) {
        if (media === mediaSession) media = null
        if (tab.yt) tab.ytPlaying = false
    }

    override fun onPlay(session: GeckoSession, mediaSession: MediaSession) {
        if (tab.yt) tab.ytPlaying = true
    }

    override fun onPause(session: GeckoSession, mediaSession: MediaSession) {
        if (tab.yt) tab.ytPlaying = false
    }

    override fun onStop(session: GeckoSession, mediaSession: MediaSession) {
        if (tab.yt) tab.ytPlaying = false
        media = null
    }

    fun play() { media?.play() }
    fun pause() { media?.pause() }
}
