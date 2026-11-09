package com.nova.browser

import android.accounts.AccountManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

object GoogleAccounts {
    val accounts = mutableStateListOf<String>()
    var primary by mutableStateOf(""); private set
    var suggest by mutableStateOf(true); private set      // اقتراح البريد في نماذج الدخول
    private val authHosts = setOf(
        "accounts.google.com", "accounts.youtube.com", "myaccount.google.com", "oauth2.googleapis.com",
        "appleid.apple.com", "login.microsoftonline.com", "login.live.com", "github.com"
    )
    private val relaxed = java.util.Collections.newSetFromMap(java.util.WeakHashMap<BrowserTab, Boolean>())

    private var sp: ConfStore? = null
    private val emailRe = Regex("^[A-Za-z0-9._%+'-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
    private val signInPaths = listOf("/signin", "/v3/signin", "/ServiceLogin", "/o/oauth2", "/AccountChooser", "/InteractiveLogin")

    fun init(c: Context) {
        val p = ConfStore.open(c); sp = p
        accounts.clear()
        p.getString("gacc", "").split(',').map { it.trim().lowercase() }.filter { isValid(it) }.distinct().forEach { accounts.add(it) }
        primary = p.getString("gprimary", "").lowercase().takeIf { it in accounts } ?: accounts.firstOrNull().orEmpty()
        suggest = p.getBoolean("gsuggest", true)
    }

    fun deviceAccountIntent(): Intent =
        AccountManager.newChooseAccountIntent(null, null, arrayOf("com.google"), null, null, null, null)

    fun addFromResult(data: Intent?): Boolean {
        val name = data?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME) ?: return false
        return add(name)
    }

    fun isValid(email: String) = email.length <= 120 && emailRe.matches(email.trim())

    private fun save() {
        sp?.putString("gacc", accounts.joinToString(","))
        sp?.putString("gprimary", primary)
    }

    /** يضيف الحساب؛ false إن كان البريد غير صالح. */
    fun add(raw: String): Boolean {
        val e = raw.trim().lowercase()
        if (!isValid(e)) return false
        if (e !in accounts) accounts.add(e)
        if (primary.isEmpty()) primary = e
        save()
        return true
    }

    fun remove(email: String) {
        accounts.remove(email)
        if (primary == email) primary = accounts.firstOrNull().orEmpty()
        save()
    }

    fun choosePrimary(email: String) { if (email in accounts) { primary = email; save() } }
    fun pickSuggest(v: Boolean) { suggest = v; sp?.putBoolean("gsuggest", v) }

    /** هل هذا الرابط صفحة تسجيل دخول Google؟ */
    fun isSignInUrl(u: Uri): Boolean {
        if (u.scheme != "https" || u.host != "accounts.google.com") return false
        val path = u.path ?: return false
        return signInPaths.any { path.startsWith(it) }
    }

    /** يضيف login_hint بالحساب الأساسي إن لم يكن موجوداً (يعيد نفس الكائن إن لم يتغير شيء). */
    fun withHint(u: Uri): Uri {
        if (primary.isEmpty() || !u.isHierarchical || u.getQueryParameter("login_hint") != null) return u
        return u.buildUpon().appendQueryParameter("login_hint", primary).build()
    }

    fun isAuthHost(host: String?): Boolean {
        val h = host?.lowercase() ?: return false
        return h in authHosts || h.endsWith(".accounts.google.com")
    }

    fun guard(bt: BrowserTab, tab: EngineTab, url: String) {
        val host = runCatching { Uri.parse(url).host }.getOrNull()
        val want = isAuthHost(host) && host != "github.com"
        val has = relaxed.contains(bt)
        if (want && !has) { relaxed.add(bt); tab.relaxBlocking(true) }
        else if (!want && has) { relaxed.remove(bt); tab.relaxBlocking(false) }
    }

    fun signOut() {
        runCatching { GeckoSettings.clearHost("google.com") }
        runCatching { GeckoSettings.clearHost("youtube.com") }
    }
}
