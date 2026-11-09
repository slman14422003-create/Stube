package com.nova.browser

import android.app.Activity
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import org.mozilla.geckoview.BasicSelectionActionDelegate
import org.mozilla.geckoview.GeckoSession

class GeckoSelection(activity: Activity, private val onAction: (Int, String) -> Unit) : BasicSelectionActionDelegate(activity) {
    private var text = ""

    private val items = listOf(
        SmartAssist.SEL_ASK to R.string.smart_ask_ai,
        SmartAssist.SEL_TRANSLATE to R.string.smart_translate,
        SmartAssist.SEL_SEARCH to R.string.smart_search,
        SmartAssist.SEL_SUMMARIZE to R.string.smart_summarize_text
    )

    override fun onShowActionRequest(session: GeckoSession, selection: GeckoSession.SelectionActionDelegate.Selection) {
        text = selection.text.orEmpty()
        super.onShowActionRequest(session, selection)
    }

    override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
        val base = super.onPrepareActionMode(mode, menu)
        if (!Prefs.smart || text.isBlank()) return base
        var added = false
        items.forEach { (id, res) ->
            if (menu.findItem(id) == null) {
                menu.add(Menu.NONE, id, Menu.NONE, S(res)).setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
                added = true
            }
        }
        return base || added
    }

    override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
        val id = item.itemId
        if (items.none { it.first == id }) return super.onActionItemClicked(mode, item)
        val t = text
        mode.finish()
        onAction(id, t)
        return true
    }
}
