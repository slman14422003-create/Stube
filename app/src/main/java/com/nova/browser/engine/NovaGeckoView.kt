package com.nova.browser

import android.annotation.SuppressLint
import android.content.Context
import android.view.MotionEvent
import org.mozilla.geckoview.GeckoView

class NovaGeckoView(ctx: Context) : GeckoView(ctx) {
    @Volatile
    private var dirs = -1

    fun pageCanScrollUp(): Boolean? = if (dirs < 0) null else (dirs and TOP) != 0

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (session == null) return super.onTouchEvent(ev)
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) dirs = -1
        onTouchEventForDetailResult(ev).accept { d -> if (d != null) dirs = d.scrollableDirections() }
        return true
    }

    private companion object {
        const val TOP = 1
    }
}
