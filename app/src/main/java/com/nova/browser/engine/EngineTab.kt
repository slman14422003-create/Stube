package com.nova.browser

import android.graphics.Bitmap
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings
import org.mozilla.geckoview.GeckoView
import org.mozilla.geckoview.MediaSession
import org.mozilla.geckoview.WebResponse

interface EngineListener {
    fun onUrl(tab: EngineTab, url: String) {}
    fun onTitle(tab: EngineTab, title: String) {}
    fun onProgress(tab: EngineTab, progress: Int) {}
    fun onLoading(tab: EngineTab, loading: Boolean) {}
    fun onNav(tab: EngineTab, canBack: Boolean, canForward: Boolean) {}
    fun onLoadRequest(tab: EngineTab, uri: String, target: Int, gesture: Boolean, redirect: Boolean): Boolean = true
    fun onNewTab(tab: EngineTab, uri: String): EngineTab? = null
    fun onDownload(tab: EngineTab, response: WebResponse) {}
    fun onCrash(tab: EngineTab) {}
    fun onFullScreen(tab: EngineTab, on: Boolean) {}
    fun onFind(tab: EngineTab, current: Int, total: Int) {}
    fun onClose(tab: EngineTab) {}
    fun onContextMenu(tab: EngineTab, element: GeckoSession.ContentDelegate.ContextElement) {}
}

class EngineTab(
    val id: Int,
    private val listener: EngineListener,
    restore: GeckoSession.SessionState? = null,
    desktop: Boolean = false,
    ui: GeckoUi? = null,
    selection: GeckoSession.SelectionActionDelegate? = null,
    media: MediaSession.Delegate? = null,
    open: Boolean = true,
    priv: Boolean = false
) {
    val session: GeckoSession = GeckoSession(
        GeckoSessionSettings.Builder()
            .usePrivateMode(priv)
            .useTrackingProtection(true)
            .userAgentMode(if (desktop) GeckoSessionSettings.USER_AGENT_MODE_DESKTOP else GeckoSessionSettings.USER_AGENT_MODE_MOBILE)
            .viewportMode(if (desktop) GeckoSessionSettings.VIEWPORT_MODE_DESKTOP else GeckoSessionSettings.VIEWPORT_MODE_MOBILE)
            .build()
    )

    var url: String = ""
        private set
    var title: String = ""
        private set
    var progress: Int = 0
        private set
    var loading: Boolean = false
        private set
    var canBack: Boolean = false
        private set
    var canForward: Boolean = false
        private set
    var scrollY: Int = 0
        private set
    var history: List<Pair<String, String>> = emptyList()
        private set
    var historyIndex: Int = -1
        private set
    var state: GeckoSession.SessionState? = null
        private set
    val restored: Boolean = restore != null

    private var attached: GeckoView? = null

    init {
        session.progressDelegate = object : GeckoSession.ProgressDelegate {
            override fun onPageStart(s: GeckoSession, u: String) {
                loading = true
                listener.onLoading(this@EngineTab, true)
            }

            override fun onPageStop(s: GeckoSession, success: Boolean) {
                loading = false
                progress = 100
                listener.onLoading(this@EngineTab, false)
            }

            override fun onProgressChange(s: GeckoSession, p: Int) {
                progress = p
                listener.onProgress(this@EngineTab, p)
            }

            override fun onSessionStateChange(s: GeckoSession, st: GeckoSession.SessionState) {
                state = st
            }
        }

        session.navigationDelegate = object : GeckoSession.NavigationDelegate {
            override fun onLocationChange(
                s: GeckoSession,
                u: String?,
                perms: MutableList<GeckoSession.PermissionDelegate.ContentPermission>,
                hasUserGesture: Boolean
            ) {
                url = u.orEmpty()
                listener.onUrl(this@EngineTab, url)
            }

            override fun onCanGoBack(s: GeckoSession, v: Boolean) {
                canBack = v
                listener.onNav(this@EngineTab, canBack, canForward)
            }

            override fun onCanGoForward(s: GeckoSession, v: Boolean) {
                canForward = v
                listener.onNav(this@EngineTab, canBack, canForward)
            }

            override fun onLoadRequest(
                s: GeckoSession,
                request: GeckoSession.NavigationDelegate.LoadRequest
            ): GeckoResult<AllowOrDeny>? {
                val ok = listener.onLoadRequest(this@EngineTab, request.uri, request.target, request.hasUserGesture, request.isRedirect)
                return GeckoResult.fromValue(if (ok) AllowOrDeny.ALLOW else AllowOrDeny.DENY)
            }

            override fun onNewSession(s: GeckoSession, uri: String): GeckoResult<GeckoSession>? {
                val child = listener.onNewTab(this@EngineTab, uri) ?: return null
                return GeckoResult.fromValue(child.session)
            }
        }

        session.contentDelegate = object : GeckoSession.ContentDelegate {
            override fun onTitleChange(s: GeckoSession, t: String?) {
                title = t.orEmpty()
                listener.onTitle(this@EngineTab, title)
            }

            override fun onContextMenu(s: GeckoSession, screenX: Int, screenY: Int, element: GeckoSession.ContentDelegate.ContextElement) {
                listener.onContextMenu(this@EngineTab, element)
            }

            override fun onExternalResponse(s: GeckoSession, response: WebResponse) {
                listener.onDownload(this@EngineTab, response)
            }

            override fun onFullScreen(s: GeckoSession, fullScreen: Boolean) {
                listener.onFullScreen(this@EngineTab, fullScreen)
            }

            override fun onCloseRequest(s: GeckoSession) {
                listener.onClose(this@EngineTab)
            }

            override fun onCrash(s: GeckoSession) {
                listener.onCrash(this@EngineTab)
            }

            override fun onKill(s: GeckoSession) {
                listener.onCrash(this@EngineTab)
            }
        }

        session.scrollDelegate = object : GeckoSession.ScrollDelegate {
            override fun onScrollChanged(s: GeckoSession, x: Int, y: Int) {
                scrollY = y
            }
        }

        if (ui != null) {
            session.promptDelegate = ui
            session.permissionDelegate = ui
        }
        if (selection != null) session.selectionActionDelegate = selection
        if (media != null) session.setMediaSessionDelegate(media)
        session.historyDelegate = object : GeckoSession.HistoryDelegate {
            override fun onHistoryStateChange(s: GeckoSession, list: GeckoSession.HistoryDelegate.HistoryList) {
                historyIndex = list.currentIndex
                history = list.map { (it.title ?: "") to (it.uri ?: "") }
            }
        }
        if (open) session.open(NovaApp.runtime())
        if (restore != null) session.restoreState(restore)
    }

    fun attach(view: GeckoView) {
        val old = attached
        if (old != null && old !== view) old.releaseSession()
        if (view.session !== session) {
            if (view.session != null) view.releaseSession()
            view.setSession(session)
        }
        attached = view
    }

    fun detach() {
        attached?.releaseSession()
        attached = null
    }

    fun load(target: String) = session.loadUri(target)

    fun loadHtml(html: String) {
        session.load(GeckoSession.Loader().data(html, "text/html"))
    }

    fun exitFullScreen() = session.exitFullScreen()
    fun reload() = session.reload()
    fun stop() = session.stop()
    fun back() = session.goBack()
    fun forward() = session.goForward()

    fun goRelative(offset: Int) {
        val i = historyIndex + offset
        if (i >= 0 && i < history.size) session.gotoHistoryIndex(i)
    }
    fun setActive(active: Boolean) = session.setActive(active)

    fun relaxBlocking(on: Boolean) {
        runCatching { session.settings.useTrackingProtection = !on }
    }

    fun scrollTop() {
        attached?.panZoomController?.scrollTo(org.mozilla.geckoview.ScreenLength.zero(), org.mozilla.geckoview.ScreenLength.zero(), org.mozilla.geckoview.PanZoomController.SCROLL_BEHAVIOR_SMOOTH)
    }

    fun pdf(cb: (java.io.InputStream?) -> Unit) {
        session.saveAsPdf().accept({ cb(it) }, { cb(null) })
    }

    fun setDesktop(desktop: Boolean) {
        session.settings.userAgentMode =
            if (desktop) GeckoSessionSettings.USER_AGENT_MODE_DESKTOP else GeckoSessionSettings.USER_AGENT_MODE_MOBILE
        session.settings.viewportMode =
            if (desktop) GeckoSessionSettings.VIEWPORT_MODE_DESKTOP else GeckoSessionSettings.VIEWPORT_MODE_MOBILE
        session.reload()
    }

    fun setJs(on: Boolean) {
        if (session.settings.allowJavascript != on) {
            session.settings.allowJavascript = on
            session.reload()
        }
    }

    fun find(q: String) {
        if (q.isEmpty()) {
            clearFind()
            return
        }
        session.finder.find(q, 0).accept { r -> if (r != null) listener.onFind(this, if (r.found) r.current + 1 else 0, r.total) }
    }

    fun findNext(forward: Boolean) {
        val flags = if (forward) 0 else GeckoSession.FINDER_FIND_BACKWARDS
        session.finder.find(null, flags).accept { r -> if (r != null) listener.onFind(this, if (r.found) r.current + 1 else 0, r.total) }
    }

    fun clearFind() {
        session.finder.clear()
    }

    fun capture(cb: (Bitmap) -> Unit) {
        val v = attached ?: return
        v.capturePixels().accept { b -> if (b != null) cb(b) }
    }

    fun close() {
        detach()
        session.close()
    }
}
