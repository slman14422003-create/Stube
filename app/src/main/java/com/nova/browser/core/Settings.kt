package com.nova.browser

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** إعدادات التطبيق (محفوظة، وقابلة للقراءة من أي مكان كحالة Compose) */
object Prefs {
    private var sp: ConfStore? = null
    var engine by mutableIntStateOf(0); private set
    var theme by mutableIntStateOf(0); private set          // 0 نظام، 1 فاتح، 2 داكن
    var hiddenFavs by mutableStateOf<Set<String>>(emptySet()); private set
    var desktop by mutableStateOf(false); private set
    var js by mutableStateOf(true); private set
    var restore by mutableStateOf(true); private set
    var maxConns by mutableIntStateOf(0); private set       // 0 = تلقائي
    var autoClean by mutableStateOf(false); private set     // مسح الكاش كلياً عند كل تشغيل (معطّل افتراضياً: الكاش يُنظَّف فقط عند تجاوز الحد)
    var blockAds by mutableStateOf(true); private set       // حجب الإعلانات والمتتبعات
    var dataSaver by mutableStateOf(false); private set     // عدم تحميل الصور
    var popups by mutableStateOf(true); private set         // إخفاء لافتات الموافقة على الكوكيز وفتح قفل التمرير
    var lazyMedia by mutableStateOf(true); private set      // تحميل كسول للصور
    var httpsFirst by mutableStateOf(true); private set     // ترقية http إلى https
    var cleanUrls by mutableStateOf(true); private set      // إزالة معرّفات التتبع من الروابط
    var blockThirdCookies by mutableStateOf(true); private set
    var secureScreen by mutableStateOf(false); private set  // منع لقطات الشاشة
    var antiFingerprint by mutableStateOf(true); private set // الحماية من البصمة
    var shield by mutableStateOf(true); private set         // الحماية الفورية من الهجمات والتتبع (Shield)
    var lang by mutableIntStateOf(0); private set           // لغة التطبيق: 0 تلقائي، 1 عربية، 2 English
    var siteLang by mutableIntStateOf(0); private set       // لغة المواقع (فهرس في siteLangs)
    var textZoom by mutableIntStateOf(1); private set       // فهرس في zoomValues
    var autoClose by mutableIntStateOf(0); private set
    var siteDark by mutableStateOf(false); private set      // الوضع الداكن للمواقع
    val siteLangs = listOf("" to "تلقائي", "ar" to "العربية", "en" to "English", "fr" to "Français", "es" to "Español",
        "de" to "Deutsch", "tr" to "Türkçe", "ru" to "Русский", "fa" to "فارسی", "ur" to "اردو", "id" to "Indonesia",
        // أُضيفت في النهاية عمداً: الفهرس محفوظ في الإعدادات فلا يتغيّر اختيار المستخدمين الحاليين
        "zh" to "中文", "ja" to "日本語", "ko" to "한국어", "hi" to "हिन्दी", "pt" to "Português", "it" to "Italiano",
        "bn" to "বাংলা", "he" to "עברית", "th" to "ไทย", "vi" to "Tiếng Việt", "nl" to "Nederlands", "pl" to "Polski", "uk" to "Українська")
    val zoomValues = listOf(85, 100, 115, 130)
    fun siteLangCode() = siteLangs.getOrNull(siteLang)?.first ?: ""
    var pauseBg by mutableStateOf(true); private set        // إيقاف الصفحات عند الخروج من التطبيق
    var pwMode by mutableIntStateOf(0); private set         // كلمات المرور: 0 مدمج، 1 تعبئة النظام (Samsung Pass…)، 2 معطّل
    var autoPip by mutableStateOf(true); private set        // نافذة منبثقة تلقائياً لفيديو يوتيوب عند الخروج
    var ytBg by mutableStateOf(true); private set           // متابعة تشغيل يوتيوب في الخلفية
    var autoUpdate by mutableStateOf(true); private set     // فحص التحديثات تلقائياً
    var adaptive by mutableStateOf(true); private set       // التكيف مع حرارة الجهاز/توفير الطاقة
    var smoothAnim by mutableStateOf(true); private set     // الأنيميشن
    var cap60 by mutableStateOf(true); private set          // تحديد 60Hz عند السخونة
    var fitPages by mutableStateOf(true); private set       // تحسين عرض الصفحات (render.js)
    var forceZoom by mutableStateOf(true); private set
    var unlockCopy by mutableStateOf(true); private set
    var draftSave by mutableStateOf(true); private set
    var pwaMode by mutableStateOf(true); private set        // وضع التطبيق (PWA) ليوتيوب ومواقع الذكاء الاصطناعي
    var ytCcSize by mutableIntStateOf(1); private set       // حجم الترجمة في يوتيوب: 0 صغير 1 عادي 2 كبير 3 كبير جداً
    var ytCcBg by mutableIntStateOf(0); private set         // خلفية الترجمة: 0 زجاجية 1 غامقة 2 بلا
    var ytCcPos by mutableIntStateOf(0); private set        // موضع الترجمة: 0 أسفل 1 وسط 2 أعلى
    var ytAds by mutableStateOf(true); private set
    var ytNoShorts by mutableStateOf(false); private set    // إخفاء شورتس من صفحات يوتيوب
    var ytResume by mutableStateOf(true); private set       // استئناف فيديوهات يوتيوب من حيث توقفت
    var ytKeepRate by mutableStateOf(true); private set      // تذكّر سرعة التشغيل بين الفيديوهات
    var ytHold2x by mutableStateOf(true); private set        // ضغط مطوّل على المشغّل = تسريع ×2 مؤقتاً
    var pwaHaptics by mutableStateOf(true); private set     // اهتزاز خفيف على أزرار صفحات وضع التطبيق
    var boost by mutableStateOf(true); private set          // تسريع التنقل: جلب مسبق + تسخين DNS + إيقاف فيديو خارج الشاشة
    var smart by mutableStateOf(true); private set
    var suggest by mutableStateOf(true); private set        // اقتراحات البحث أثناء الكتابة
    var notifDone by mutableStateOf(true); private set      // إشعار اكتمال التنزيل
    var notifUpdate by mutableStateOf(true); private set    // إشعار تحديث متاح
    var notifAsked by mutableStateOf(false); private set    // سبق عرض طلب إذن الإشعارات

    val engines = listOf(
        "Google" to "https://www.google.com/search?q=",
        "DuckDuckGo" to "https://duckduckgo.com/?q=",
        "Bing" to "https://www.bing.com/search?q=",
        "Brave" to "https://search.brave.com/search?q=",
        "Ecosia" to "https://www.ecosia.org/search?q="
    )

    fun init(c: Context) {
        val p = ConfStore.open(c); sp = p   // كل الإعدادات من ملف nova.conf دفعة واحدة إلى الرام
        engine = p.getInt("engine", 0).coerceIn(0, engines.lastIndex)
        hiddenFavs = p.getString("hidefav", "").split(",").filter { it.isNotBlank() }.toSet()
        theme = p.getInt("theme", 0); desktop = p.getBoolean("desktop", false)
        js = p.getBoolean("js", true); restore = p.getBoolean("restore", true); maxConns = p.getInt("maxc", 0)
        autoClean = p.getBoolean("autoclean", false); blockAds = p.getBoolean("blockads", true)
        dataSaver = p.getBoolean("saver", false); lazyMedia = p.getBoolean("lazy", true); popups = p.getBoolean("popups", true)
        httpsFirst = p.getBoolean("https1", true); cleanUrls = p.getBoolean("cleanurl", true)
        blockThirdCookies = p.getBoolean("c3p", true); secureScreen = p.getBoolean("secscr", false)
        antiFingerprint = p.getBoolean("antifp", true); shield = p.getBoolean("shield", true); pauseBg = p.getBoolean("pausebg", true)
        lang = p.getInt("lang", 0); siteLang = p.getInt("sitelang", 0).coerceIn(0, siteLangs.lastIndex)
        autoClose = p.getInt("autoclose", 0).coerceIn(0, 3)
        textZoom = p.getInt("zoom", 1).coerceIn(0, zoomValues.lastIndex); siteDark = p.getBoolean("sitedark", false)
        pwMode = if (p.contains("pwmode")) p.getInt("pwmode", 0).coerceIn(0, 2) else defaultPwMode(c)
        autoPip = p.getBoolean("autopip", true); ytBg = p.getBoolean("ytbg", true)
        autoUpdate = p.getBoolean("autoupd", true); adaptive = p.getBoolean("adaptive", true)
        smoothAnim = p.getBoolean("anim", true); cap60 = p.getBoolean("cap60", true)
        fitPages = p.getBoolean("fitpages", true); forceZoom = p.getBoolean("fzoom", true); unlockCopy = p.getBoolean("ucopy", true); draftSave = p.getBoolean("draft", true); pwaMode = p.getBoolean("pwa", true); pwaHaptics = p.getBoolean("pwahap", true);
        ytCcSize = p.getInt("yccsize", 1).coerceIn(0, 3); ytCcBg = p.getInt("yccbg", 0).coerceIn(0, 2); ytCcPos = p.getInt("yccpos", 0).coerceIn(0, 2)
        ytNoShorts = p.getBoolean("ytnoshorts", false); ytAds = p.getBoolean("ytads", true)
        ytResume = p.getBoolean("ytresume", true); ytKeepRate = p.getBoolean("ytkeeprate", true); ytHold2x = p.getBoolean("ythold2x", true)
        boost = p.getBoolean("boost", true); suggest = p.getBoolean("suggest", true); smart = p.getBoolean("smart", true);
        notifDone = p.getBoolean("notifdone", true); notifUpdate = p.getBoolean("notifupd", true); notifAsked = p.getBoolean("notifasked", false)
    }

    /** إن كانت خدمة تعبئة (Samsung Pass مثلاً) مفعّلة في النظام نبدأ بها تلقائياً، وإلا نستخدم المدير المدمج. */
    private fun defaultPwMode(c: Context): Int = runCatching {
        val am = c.getSystemService(android.view.autofill.AutofillManager::class.java)
        if (am != null && am.isEnabled && am.hasEnabledAutofillServices()) 1 else 0
    }.getOrDefault(0)
    fun pickEngine(v: Int) { engine = v; sp?.putInt("engine", v) }
    fun pickTheme(v: Int, c: Context? = null) { theme = v; sp?.putInt("theme", v); c?.let { syncNightMode(it) } }
    fun hideFav(host: String) { hiddenFavs = hiddenFavs + host; sp?.putString("hidefav", hiddenFavs.joinToString(",")) }
    fun restoreFavs() { hiddenFavs = emptySet(); sp?.putString("hidefav", "") }
    fun syncNightMode(c: Context) {
        val m = c.applicationContext.getSystemService(android.app.UiModeManager::class.java) ?: return
        val want = when (theme) {
            1 -> android.app.UiModeManager.MODE_NIGHT_NO
            2 -> android.app.UiModeManager.MODE_NIGHT_YES
            else -> android.app.UiModeManager.MODE_NIGHT_AUTO
        }
        runCatching { m.setApplicationNightMode(want) }
    }
    fun pickDesktop(v: Boolean) { desktop = v; sp?.putBoolean("desktop", v) }
    fun pickJs(v: Boolean) { js = v; sp?.putBoolean("js", v) }
    fun pickRestore(v: Boolean) { restore = v; sp?.putBoolean("restore", v) }
    fun pickMaxConns(v: Int) { maxConns = v; sp?.putInt("maxc", v) }
    fun pickAutoClean(v: Boolean) { autoClean = v; sp?.putBoolean("autoclean", v) }
    fun pickBlockAds(v: Boolean) { blockAds = v; sp?.putBoolean("blockads", v) }
    fun pickDataSaver(v: Boolean) { dataSaver = v; sp?.putBoolean("saver", v) }
    fun pickHttpsFirst(v: Boolean) { httpsFirst = v; sp?.putBoolean("https1", v) }
    fun pickCleanUrls(v: Boolean) { cleanUrls = v; sp?.putBoolean("cleanurl", v) }
    fun pickThirdCookies(v: Boolean) { blockThirdCookies = v; sp?.putBoolean("c3p", v) }
    fun pickAntiFingerprint(v: Boolean) { antiFingerprint = v; sp?.putBoolean("antifp", v) }
    fun pickShield(v: Boolean) { shield = v; sp?.putBoolean("shield", v) }
    fun pickLang(v: Int) { lang = v; sp?.putInt("lang", v) }
    fun pickSiteLang(v: Int) { siteLang = v; sp?.putInt("sitelang", v) }
    fun pickTextZoom(v: Int) { textZoom = v; sp?.putInt("zoom", v) }
    fun pickAutoClose(v: Int) { autoClose = v; sp?.putInt("autoclose", v) }
    val autoCloseMs = longArrayOf(0L, 86_400_000L, 7 * 86_400_000L, 30 * 86_400_000L)
    fun pickSiteDark(v: Boolean) { siteDark = v; sp?.putBoolean("sitedark", v) }
    fun pickPauseBg(v: Boolean) { pauseBg = v; sp?.putBoolean("pausebg", v) }
    fun pickSecureScreen(v: Boolean) { secureScreen = v; sp?.putBoolean("secscr", v) }
    fun pickPwMode(v: Int) { pwMode = v; sp?.putInt("pwmode", v) }
    fun pickSmart(v: Boolean) { smart = v; sp?.putBoolean("smart", v) }
    fun pickAutoPip(v: Boolean) { autoPip = v; sp?.putBoolean("autopip", v) }
    fun pickYtBg(v: Boolean) { ytBg = v; sp?.putBoolean("ytbg", v) }
    fun pickAutoUpdate(v: Boolean) { autoUpdate = v; sp?.putBoolean("autoupd", v) }
    fun pickAdaptive(v: Boolean) { adaptive = v; sp?.putBoolean("adaptive", v); Adaptive.refresh() }
    fun pickSmoothAnim(v: Boolean) { smoothAnim = v; sp?.putBoolean("anim", v) }
    fun pickCap60(v: Boolean) { cap60 = v; sp?.putBoolean("cap60", v) }
    fun pickFitPages(v: Boolean) { fitPages = v; sp?.putBoolean("fitpages", v) }
    fun pickForceZoom(v: Boolean) { forceZoom = v; sp?.putBoolean("fzoom", v) }
    fun pickUnlockCopy(v: Boolean) { unlockCopy = v; sp?.putBoolean("ucopy", v) }
    fun pickDraftSave(v: Boolean) { draftSave = v; sp?.putBoolean("draft", v) }
    fun pickPwaMode(v: Boolean) { pwaMode = v; sp?.putBoolean("pwa", v) }
    fun pickYtResume(v: Boolean) { ytResume = v; sp?.putBoolean("ytresume", v) }
    fun pickYtKeepRate(v: Boolean) { ytKeepRate = v; sp?.putBoolean("ytkeeprate", v) }
    fun pickYtHold2x(v: Boolean) { ytHold2x = v; sp?.putBoolean("ythold2x", v) }
    fun pickYtCcSize(v: Int) { ytCcSize = v; sp?.putInt("yccsize", v) }
    fun pickYtCcBg(v: Int) { ytCcBg = v; sp?.putInt("yccbg", v) }
    fun pickYtCcPos(v: Int) { ytCcPos = v; sp?.putInt("yccpos", v) }
    fun pickYtAds(v: Boolean) { ytAds = v; sp?.putBoolean("ytads", v) }
    fun pickYtNoShorts(v: Boolean) { ytNoShorts = v; sp?.putBoolean("ytnoshorts", v) }
    fun pickPwaHaptics(v: Boolean) { pwaHaptics = v; sp?.putBoolean("pwahap", v) }
    fun pickBoost(v: Boolean) { boost = v; sp?.putBoolean("boost", v) }
    fun pickSuggest(v: Boolean) { suggest = v; sp?.putBoolean("suggest", v) }
    fun pickNotifDone(v: Boolean) { notifDone = v; sp?.putBoolean("notifdone", v) }
    fun pickNotifUpdate(v: Boolean) { notifUpdate = v; sp?.putBoolean("notifupd", v) }
    fun pickNotifAsked(v: Boolean) { notifAsked = v; sp?.putBoolean("notifasked", v) }
    fun pickPopups(v: Boolean) { popups = v; sp?.putBoolean("popups", v) }
    fun pickLazyMedia(v: Boolean) { lazyMedia = v; sp?.putBoolean("lazy", v) }
}

private class RowSpec(
    val title: String, val sub: String?, val icon: ImageVector, val onClick: () -> Unit,
    val trailing: (@Composable () -> Unit)? = null
)

private class SectionSpec(val id: String, val title: String, val sub: String, val icon: ImageVector, val rows: List<RowSpec>)

@Composable
private fun ChoiceDialog(title: String, options: List<String>, selected: Int, onSelect: (Int) -> Unit, onDismiss: () -> Unit) =
    NovaChoiceDialog(title, options, selected, Icons.Default.Settings, onSelect, onDismiss)

private fun autofillStatus(c: Context): String {
    val svc = android.provider.Settings.Secure.getString(c.contentResolver, "autofill_service") ?: ""
    return when {
        svc.isBlank() -> L("لا توجد خدمة تعبئة مفعّلة في النظام")
        svc.contains("samsung", true) -> "Samsung Pass"
        else -> svc.substringBefore('/').substringAfterLast('.')
    }
}

/** يفتح اختيار خدمة التعبئة التلقائية في النظام (حيث تُفعّل Samsung Pass أو غيرها). */
private fun openAutofillSettings(c: Context) {
    val pm = c.packageManager
    val pkg = listOf("com.samsung.android.samsungpassautofill", "com.samsung.android.samsungpass")
        .firstOrNull { runCatching { pm.getPackageInfo(it, 0) }.isSuccess }
    val i = Intent(android.provider.Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE).setData(Uri.parse("package:" + (pkg ?: "android")))
    runCatching { c.startActivity(i) }.onFailure { runCatching { c.startActivity(Intent(android.provider.Settings.ACTION_SETTINGS)) } }
}

private fun addSiteDialog(ctx: Context) {
    val et = android.widget.EditText(ctx)
    et.hint = "https://example.com"
    et.inputType = android.text.InputType.TYPE_TEXT_VARIATION_URI
    novaDialog(ctx).setTitle("Add site as app").setView(et)
        .setPositiveButton("Add") { _, _ ->
            var u = et.text.toString().trim()
            if (u.isNotEmpty()) {
                if (!u.contains("://")) u = "https://$u"
                val host = hostOf(u)
                Pwa.install(ctx, u, host)
                Pwa.launch(ctx, u)
            }
        }
        .setNegativeButton("Cancel", null).show()
}

private fun installedAppsDialog(ctx: Context) {
    val list = Pwa.installed(ctx)
    if (list.isEmpty()) { toast(ctx, "No apps installed"); return }
    novaDialog(ctx).setTitle("Installed apps (tap to remove)")
        .setItems(list.map { it.second }.toTypedArray()) { _, i -> Pwa.remove(ctx, list[i].first); toast(ctx, "App removed") }
        .setNegativeButton("Close", null).show()
}

@Composable
fun SettingsScreen(onBack: () -> Unit, onClearData: () -> Unit, onClearCache: () -> Unit = {}, onPasswords: () -> Unit = {}, overlayOpen: Boolean = false) {
    val cs = MaterialTheme.colorScheme
    var dialog by remember { mutableStateOf<String?>(null) }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var cacheSize by remember { mutableStateOf("…") }
    var cacheTick by remember { mutableIntStateOf(0) }
    var gEmail by remember { mutableStateOf("") }
    var gErr by remember { mutableStateOf(false) }
    val gPicker = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { r ->
        if (GoogleAccounts.addFromResult(r.data)) gErr = false
    }
    val warnings = remember { Security.deviceWarnings(ctx) }
    LaunchedEffect(cacheTick) {
        cacheSize = kotlinx.coroutines.withContext(Work.ioD) { CacheCleaner.format(CacheCleaner.size(ctx)) }
    }
    val themeNames = listOf(L("تلقائي (حسب النظام)"), L("فاتح"), L("داكن"))
    val langNames = listOf(L("تلقائي"), "العربية", "English")
    val zoomNames = listOf(L("صغير"), L("عادي"), L("كبير"), L("كبير جداً"))
    val autoCloseNames = listOf(L("يدوياً"), L("بعد يوم"), L("بعد أسبوع"), L("بعد شهر"))
    val connOpts = listOf(0, 4, 8, 16)
    val connNames = listOf(L("تلقائي (حتى 16)"), "4", "8", "16")
    val ccSizeNames = listOf(L("صغير"), L("عادي"), L("كبير"), L("كبير جداً"))
    val ccBgNames = listOf(L("زجاجية"), L("غامقة"), L("بلا خلفية"))
    val ccPosNames = listOf(L("أسفل"), L("وسط"), L("أعلى"))
    val pwNames = listOf(L("مدمج في المتصفح"), L("خدمة النظام (Samsung Pass وغيرها)"), L("متوقف"))

    val notif = rememberNotifState()
    var section by remember { mutableStateOf<String?>(null) }
    BackHandler(enabled = section != null && !overlayOpen) { section = null }
    val sections = listOf(
        SectionSpec("general", L("عام"), L("المتصفح الافتراضي • محرك البحث • المظهر"), Icons.Default.Settings, listOf(
            RowSpec(L("محرك البحث"), Prefs.engines[Prefs.engine].first, Icons.Default.Search, { dialog = "engine" }),
            RowSpec(L("المظهر"), themeNames[Prefs.theme], Icons.Default.Star, { dialog = "theme" }),
            RowSpec(L("المكتبة"), L("المفضلة") + " " + Library.bookmarks.size + " • " + L("السجل") + " " + Library.history.size, Icons.Default.Favorite, { Library.show = true }),
            RowSpec(L("نسخة سطح المكتب افتراضياً"), L("للتبويبات الجديدة"), Icons.Default.Build, { Prefs.pickDesktop(!Prefs.desktop) },
                { IosSwitch(checked = Prefs.desktop, onCheckedChange = null) })
        )),
        SectionSpec("browsing", L("التصفح والخصوصية"), L("JavaScript • الإعلانات • مسح البيانات"), Icons.Default.Search, listOf(
            RowSpec("JavaScript", L("تعطيله قد يكسر بعض المواقع"), Icons.Default.Check, { Prefs.pickJs(!Prefs.js) },
                { IosSwitch(checked = Prefs.js, onCheckedChange = null) }),
            RowSpec(L("استعادة التبويبات"), L("عند فتح التطبيق"), Icons.Default.Refresh, { Prefs.pickRestore(!Prefs.restore) },
                { IosSwitch(checked = Prefs.restore, onCheckedChange = null) }),
            RowSpec(L("حجب الإعلانات والمتتبعات"), L("يسرّع الصفحات ويوفر البيانات"), Icons.Default.Check, { Prefs.pickBlockAds(!Prefs.blockAds) },
                { IosSwitch(checked = Prefs.blockAds, onCheckedChange = null) }),
            RowSpec(L("مسح بيانات التصفح"), L("الكوكيز والذاكرة المؤقتة والسجل"), Icons.Default.Delete, { dialog = "clear" })
        )),
        SectionSpec("passwords", L("كلمات المرور وتسجيل الدخول"), L("كلمات المرور المحفوظة • التعبئة التلقائية"), Icons.Default.Lock, listOf(
            RowSpec(L("كلمات المرور المحفوظة"), Vault.items.size.toString(), Icons.Default.Lock, { onPasswords() }),
            RowSpec(L("وضع التعبئة التلقائية"), pwNames[Prefs.pwMode.coerceIn(0, 2)], Icons.Default.Person, { dialog = "pwmode" }),
            RowSpec(L("خدمة التعبئة في النظام"), autofillStatus(ctx), Icons.Default.Settings, { openAutofillSettings(ctx) })
        )),
        SectionSpec("google", L("حساب Google"), L("الحسابات واقتراح تسجيل الدخول"), Icons.Default.Person, listOf(
            RowSpec(L("حسابات Google"),
                if (GoogleAccounts.accounts.isEmpty()) L("أضف بريدك ليُقترح عند طلب تسجيل الدخول") else GoogleAccounts.primary,
                Icons.Default.Person, { gEmail = ""; gErr = false; dialog = "gacct" }),
            RowSpec(L("اقتراح الحساب عند تسجيل الدخول"), L("يعرض بريدك فوق الصفحة عندما يطلب الموقع تسجيل الدخول"), Icons.Default.Check,
                { GoogleAccounts.pickSuggest(!GoogleAccounts.suggest) }, { IosSwitch(checked = GoogleAccounts.suggest, onCheckedChange = null) }),
            RowSpec(L("تسجيل الخروج من Google"), L("يمسح جلسة Google ويوتيوب من المتصفح"), Icons.Default.Delete,
                { GoogleAccounts.signOut(); toast(ctx, L("تم تسجيل الخروج من Google")) })
        )),
        SectionSpec("apps", "Apps", "Add or remove standalone apps", Icons.Default.Add, listOf(
            RowSpec("Add site as app", "Install any https site as a standalone app", Icons.Default.Add, { addSiteDialog(ctx) }),
            RowSpec("Installed apps", Pwa.installed(ctx).size.toString(), Icons.Default.Delete, { installedAppsDialog(ctx) })
        )),
        SectionSpec("youtube", L("يوتيوب"), L("التشغيل في الخلفية • الترجمة • Shorts"), Icons.Default.PlayArrow, listOf(
            RowSpec(L("متابعة التشغيل في الخلفية"), L("مع أزرار التحكم في الإشعار وشاشة القفل"), Icons.Default.PlayArrow, { Prefs.pickYtBg(!Prefs.ytBg) },
                { IosSwitch(checked = Prefs.ytBg, onCheckedChange = null) }),
            RowSpec(L("نافذة منبثقة تلقائية"), L("عند الخروج من التطبيق أثناء تشغيل فيديو"), Icons.Default.Share, { Prefs.pickAutoPip(!Prefs.autoPip) },
                { IosSwitch(checked = Prefs.autoPip, onCheckedChange = null) }),
            RowSpec(L("استئناف الفيديو"), L("يكمل فيديوهات يوتيوب الطويلة من حيث توقفت. يُطبَّق على التبويبات الجديدة"), Icons.Default.PlayArrow, { Prefs.pickYtResume(!Prefs.ytResume) },
                { IosSwitch(checked = Prefs.ytResume, onCheckedChange = null) }),
            RowSpec(L("تذكّر سرعة التشغيل"), L("تبقى السرعة التي اخترتها في الفيديوهات التالية. يُطبَّق على التبويبات الجديدة"), Icons.Default.PlayArrow, { Prefs.pickYtKeepRate(!Prefs.ytKeepRate) },
                { IosSwitch(checked = Prefs.ytKeepRate, onCheckedChange = null) }),
            RowSpec(L("ضغط مطوّل للتسريع ×2"), L("اضغط مطوّلاً على المشغّل لتسريع مؤقت، وعند الرفع تعود السرعة. يُطبَّق على التبويبات الجديدة"), Icons.Default.PlayArrow, { Prefs.pickYtHold2x(!Prefs.ytHold2x) },
                { IosSwitch(checked = Prefs.ytHold2x, onCheckedChange = null) }),
            RowSpec(L("حجم الترجمة"), ccSizeNames[Prefs.ytCcSize.coerceIn(0, 3)], Icons.Default.Edit, { dialog = "yccsize" }),
            RowSpec(L("خلفية الترجمة"), ccBgNames[Prefs.ytCcBg.coerceIn(0, 2)], Icons.Default.Edit, { dialog = "yccbg" }),
            RowSpec(L("موضع الترجمة"), ccPosNames[Prefs.ytCcPos.coerceIn(0, 2)], Icons.Default.Edit, { dialog = "yccpos" }),
            RowSpec(L("حظر إعلانات يوتيوب"), L("يزيل الإعلانات من الفيديو والصفحات داخل التطبيق نفسه، فيعمل مع VPN وبدونه. يُطبَّق عند فتح يوتيوب من جديد"), Icons.Default.Close, { Prefs.pickYtAds(!Prefs.ytAds) },
                { IosSwitch(checked = Prefs.ytAds, onCheckedChange = null) }),
            RowSpec(L("إخفاء Shorts"), L("يخفي أرفف ومقاطع شورتس من قوائم يوتيوب. يُطبَّق على الصفحات الجديدة"), Icons.Default.Close, { Prefs.pickYtNoShorts(!Prefs.ytNoShorts) },
                { IosSwitch(checked = Prefs.ytNoShorts, onCheckedChange = null) }),
            RowSpec(L("سجل التشخيص"), L("يساعد في معرفة سبب توقف الفيديو (انسخه وأرسله)"), Icons.Default.Info, { dialog = "ytlog" })
        )),
        SectionSpec("notifications", L("الإشعارات"), L("الإذن • اكتمال التنزيل • التحديثات"), Icons.Default.Notifications, listOf(
            RowSpec(L("إشعارات Stube"),
                if (notif.enabled) L("مفعّلة — اضغط لفتح إعدادات النظام") else if (Notif.needsPermission) L("معطّلة — اضغط للسماح بالإشعارات") else L("معطّلة من إعدادات النظام — اضغط لتفعيلها"),
                Icons.Default.Notifications, { notif.request() },
                { IosSwitch(checked = notif.enabled, onCheckedChange = null) }),
            RowSpec(L("اكتمال التنزيل"), L("إشعار عند انتهاء كل ملف مع فتحه بلمسة"), Icons.Default.KeyboardArrowDown, { Prefs.pickNotifDone(!Prefs.notifDone) },
                { IosSwitch(checked = Prefs.notifDone, onCheckedChange = null) }),
            RowSpec(L("توفّر تحديث"), L("إشعار عند صدور إصدار جديد من Stube"), Icons.Default.Refresh, { Prefs.pickNotifUpdate(!Prefs.notifUpdate) },
                { IosSwitch(checked = Prefs.notifUpdate, onCheckedChange = null) }),
            RowSpec(L("أزرار التشغيل في الخلفية"), L("إشعار التحكم بيوتيوب وشاشة القفل — يُضبط من قسم يوتيوب"), Icons.Default.PlayArrow, { Prefs.pickYtBg(!Prefs.ytBg) },
                { IosSwitch(checked = Prefs.ytBg, onCheckedChange = null) }),
            RowSpec(L("قنوات الإشعارات"), L("الصوت والأهمية لكل نوع من إعدادات النظام"), Icons.Default.Settings, { Notif.openSystemSettings(ctx) })
        )),
        SectionSpec("language", L("اللغة والعرض"), L("لغة التطبيق والمواقع • حجم الخط"), Icons.Default.Edit, listOf(
            RowSpec(L("لغة التطبيق"), langNames[Prefs.lang.coerceIn(0, 2)], Icons.Default.Settings, { dialog = "lang" }),
            RowSpec(L("لغة المواقع"), Prefs.siteLangs[Prefs.siteLang].let { if (it.first.isEmpty()) L(it.second) else it.second }, Icons.Default.Search, { dialog = "sitelang" }),
            RowSpec(L("حجم الخط في المواقع"), zoomNames[Prefs.textZoom], Icons.Default.Edit, { dialog = "zoom" }),
            RowSpec(L("إغلاق التبويبات تلقائياً"), autoCloseNames[Prefs.autoClose.coerceIn(0, 3)], Icons.Default.Delete, { dialog = "autoclose" }),
            RowSpec(L("الوضع الداكن للمواقع"), L("يتبع وضع النظام الداكن"), Icons.Default.Star, { Prefs.pickSiteDark(!Prefs.siteDark) },
                { IosSwitch(checked = Prefs.siteDark, onCheckedChange = null) })
        )),
        SectionSpec("security", L("الأمان"), L("الحماية الفورية • HTTPS • سجل الأمان"), Icons.Default.Check, listOfNotNull(
            RowSpec(L("الحماية الفورية"),
                (if (Shield.version >= 0) L("يحجب المتتبعات والروابط المخادعة وإغراق النوافذ والتنزيلات المتتابعة — تم صدّ ") + Shield.total else ""),
                Icons.Default.Lock, { Prefs.pickShield(!Prefs.shield) },
                { IosSwitch(checked = Prefs.shield, onCheckedChange = null) }),
            RowSpec(L("HTTPS أولاً"), L("ترقية الروابط إلى اتصال مشفّر مع رجوع تلقائي عند عدم الدعم"), Icons.Default.Lock, { Prefs.pickHttpsFirst(!Prefs.httpsFirst) },
                { IosSwitch(checked = Prefs.httpsFirst, onCheckedChange = null) }),
            RowSpec(L("الحماية من البصمة"), L("توحيد وتشويش قيم Canvas وWebGL والصوت والجهاز (يُطبَّق على التبويبات الجديدة)"), Icons.Default.Lock, { Prefs.pickAntiFingerprint(!Prefs.antiFingerprint) },
                { IosSwitch(checked = Prefs.antiFingerprint, onCheckedChange = null) }),
            RowSpec(L("إزالة معرّفات التتبع"), L("utm و fbclid و gclid وغيرها من الروابط"), Icons.Default.Check, { Prefs.pickCleanUrls(!Prefs.cleanUrls) },
                { IosSwitch(checked = Prefs.cleanUrls, onCheckedChange = null) }),
            RowSpec(L("حظر كوكيز الطرف الثالث"), L("قد تتأثر بعض مواقع تسجيل الدخول المشترك"), Icons.Default.Info, { Prefs.pickThirdCookies(!Prefs.blockThirdCookies) },
                { IosSwitch(checked = Prefs.blockThirdCookies, onCheckedChange = null) }),
            RowSpec(L("حماية الشاشة"), L("منع لقطات الشاشة وتسجيلها داخل التطبيق"), Icons.Default.Lock, { Prefs.pickSecureScreen(!Prefs.secureScreen) },
                { IosSwitch(checked = Prefs.secureScreen, onCheckedChange = null) }),
            RowSpec(L("سجل الأمان"), L("التهديدات والمتتبعات المحجوبة"), Icons.Default.Info, { dialog = "seclog" }),
            RowSpec(L("فحص سلامة الجهاز"), if (warnings.isEmpty()) L("لا مؤشرات مقلقة") else warnings.joinToString(" • "), Icons.Default.Warning, {})
        )),
        SectionSpec("performance", L("الأداء والذاكرة المؤقتة"), L("الكاش • السرعة • توفير البيانات"), Icons.Default.Refresh, listOf(
            RowSpec(L("مسح الكاش عند كل تشغيل"), L("يُحذف الكاش كلياً عند فتح التطبيق؛ تبقى كلمات المرور وإعدادات المواقع وتسجيلات الدخول"), Icons.Default.Refresh, { Prefs.pickAutoClean(!Prefs.autoClean) },
                { IosSwitch(checked = Prefs.autoClean, onCheckedChange = null) }),
            RowSpec(L("تحسين عرض الصفحات"), L("يضبط الصور والأكواد على عرض الشاشة ويمنع التمرير الأفقي (يُطبَّق على التبويبات الجديدة)"), Icons.Default.Settings, { Prefs.pickFitPages(!Prefs.fitPages) },
                { IosSwitch(checked = Prefs.fitPages, onCheckedChange = null) }),
            RowSpec(S(R.string.x_zoom_title), S(R.string.x_zoom_sub), Icons.Default.Search, { Prefs.pickForceZoom(!Prefs.forceZoom) },
                { IosSwitch(checked = Prefs.forceZoom, onCheckedChange = null) }),
            RowSpec(S(R.string.x_copy_title), S(R.string.x_copy_sub), Icons.Default.Edit, { Prefs.pickUnlockCopy(!Prefs.unlockCopy) },
                { IosSwitch(checked = Prefs.unlockCopy, onCheckedChange = null) }),
            RowSpec(S(R.string.x_draft_title), S(R.string.x_draft_sub), Icons.Default.Edit, { Prefs.pickDraftSave(!Prefs.draftSave) },
                { IosSwitch(checked = Prefs.draftSave, onCheckedChange = null) }),
            RowSpec(L("تسريع التنقل"), L("جلب مسبق لروابط الموقع عند اللمس، تسخين DNS، وإيقاف الفيديوهات الصامتة خارج الشاشة (يُطبَّق على التبويبات الجديدة)"), Icons.Default.PlayArrow, { Prefs.pickBoost(!Prefs.boost) },
                { IosSwitch(checked = Prefs.boost, onCheckedChange = null) }),
            RowSpec(S(R.string.smart_settings_title), S(R.string.smart_settings_sub), Icons.Default.Star, { Prefs.pickSmart(!Prefs.smart) },
                { IosSwitch(checked = Prefs.smart, onCheckedChange = null) }),
            RowSpec(L("اقتراحات البحث"), L("تظهر أثناء الكتابة من المفضلة والسجل ومن محرك البحث المختار (يُرسل ما تكتبه إليه)"), Icons.Default.Search, { Prefs.pickSuggest(!Prefs.suggest) },
                { IosSwitch(checked = Prefs.suggest, onCheckedChange = null) }),
            RowSpec(L("وضع التطبيق (PWA)"), L("شريط علوي وتجربة تطبيق ليوتيوب ومواقع الذكاء الاصطناعي (يُطبَّق على التبويبات الجديدة)"), Icons.Default.Star, { Prefs.pickPwaMode(!Prefs.pwaMode) },
                { IosSwitch(checked = Prefs.pwaMode, onCheckedChange = null) }),
            RowSpec(L("اهتزاز الأزرار في وضع التطبيق"), L("لمسة اهتزاز خفيفة عند ضغط الأزرار داخل يوتيوب ومواقع الذكاء الاصطناعي"), Icons.Default.Face, { Prefs.pickPwaHaptics(!Prefs.pwaHaptics) },
                { IosSwitch(checked = Prefs.pwaHaptics, onCheckedChange = null) }),
            RowSpec(L("محرك العرض"), GeckoSettings.engineSummary(), Icons.Default.Info, {}),
            RowSpec(L("مسح الذاكرة المؤقتة الآن"), (L("الحجم الحالي: ") + cacheSize), Icons.Default.Delete, { dialog = "cache" }),
            RowSpec(L("إيقاف الصفحات في الخلفية"), L("يوفر المعالج والبطارية عند الخروج من التطبيق (يوقف الصوت أيضاً)"), Icons.Default.Refresh, { Prefs.pickPauseBg(!Prefs.pauseBg) },
                { IosSwitch(checked = Prefs.pauseBg, onCheckedChange = null) }),
            RowSpec(L("إخفاء لافتات الكوكيز"), L("يخفي نوافذ الموافقة المعروفة ويفتح قفل التمرير الذي تسببه (يُطبَّق على الصفحات الجديدة)"), Icons.Default.Info, { Prefs.pickPopups(!Prefs.popups) },
                { IosSwitch(checked = Prefs.popups, onCheckedChange = null) }),
            RowSpec(L("تحميل كسول للصور"), L("تحميل الصور عند الاقتراب منها فقط"), Icons.Default.KeyboardArrowDown, { Prefs.pickLazyMedia(!Prefs.lazyMedia) },
                { IosSwitch(checked = Prefs.lazyMedia, onCheckedChange = null) }),
            RowSpec(L("توفير البيانات"), L("عدم تحميل الصور (يُطبَّق على التبويبات الجديدة)"), Icons.Default.Info, { Prefs.pickDataSaver(!Prefs.dataSaver) },
                { IosSwitch(checked = Prefs.dataSaver, onCheckedChange = null) })
        )),
        SectionSpec("thermal", L("الحرارة والسلاسة"), L("الحرارة • الحركات"), Icons.Default.Warning, listOfNotNull(
            RowSpec(L("التكيف مع حرارة الجهاز"), L("يخفّف الحركة ويحرّر التبويبات الخلفية عند السخونة أو توفير الطاقة"), Icons.Default.Warning, { Prefs.pickAdaptive(!Prefs.adaptive) },
                { IosSwitch(checked = Prefs.adaptive, onCheckedChange = null) }),
            RowSpec(L("تحديد 60Hz عند السخونة"), L("يقلل استهلاك الشاشة والمعالج"), Icons.Default.Refresh, { Prefs.pickCap60(!Prefs.cap60) },
                { IosSwitch(checked = Prefs.cap60, onCheckedChange = null) }),
            RowSpec(L("حركات الواجهة"), L("إيقافها يجعل التنقل فورياً ويوفر الطاقة"), Icons.Default.Star, { Prefs.pickSmoothAnim(!Prefs.smoothAnim) },
                { IosSwitch(checked = Prefs.smoothAnim, onCheckedChange = null) }),
        )),
        SectionSpec("downloads", L("التنزيلات"), L("الاتصالات • مكان الحفظ"), Icons.Default.KeyboardArrowDown, listOf(
            RowSpec(L("الحد الأقصى للاتصالات"), connNames[connOpts.indexOf(Prefs.maxConns).coerceAtLeast(0)], Icons.Default.KeyboardArrowDown, { dialog = "conns" }),
            RowSpec(L("مكان الحفظ"), "Download/Stube", Icons.Default.Info, {})
        )),
        SectionSpec("about", L("حول"), L("الإصدار والتحديثات"), Icons.Default.Info, listOf(
            RowSpec("Stube", L("الإصدار ") + BuildConfig.VERSION_NAME, Icons.Default.Star, {}),
            RowSpec(L("التحديث التلقائي"), L("فحص الإصدارات الجديدة من GitHub كل 12 ساعة"), Icons.Default.Refresh, { Prefs.pickAutoUpdate(!Prefs.autoUpdate) },
                { IosSwitch(checked = Prefs.autoUpdate, onCheckedChange = null) }),
            RowSpec(L("سجل الأعطال"), if (CrashLog.text().isBlank()) L("لا توجد أعطال مسجلة") else L("اضغط لعرض آخر عطل ونسخه"), Icons.Default.Warning, { dialog = "crash" }),
            RowSpec(L("التحقق من تحديث الآن"), updateStatus(), Icons.Default.Info, {
                when (Updater.phase) {
                    UpdPhase.AVAILABLE -> Updater.showPrompt()
                    UpdPhase.READY -> Updater.install(ctx)
                    else -> Updater.check(ctx, true)
                }
            })
        )),
    )

    val ios = LocalIos.current
    val tints = mapOf(
        "general" to ios.gray, "browsing" to ios.blue, "passwords" to ios.gray, "google" to ios.blue,
        "youtube" to ios.red, "notifications" to ios.red, "language" to ios.blue,
        "security" to ios.green, "performance" to ios.orange, "thermal" to ios.orange,
        "downloads" to ios.blue, "about" to ios.gray
    )
    val rootGroups = listOf(
        listOf("general", "browsing", "passwords", "google"),
        listOf("youtube", "notifications", "language"),
        listOf("security", "performance", "thermal"),
        listOf("downloads", "about")
    )
    Surface(Modifier.fillMaxSize(), color = ios.groupedBg) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            val cur = sections.firstOrNull { it.id == section }
            IosNavBar(cur?.title, { if (section != null) section = null else onBack() }, if (cur != null) L("الإعدادات") else null)
            AnimatedContent(
                targetState = section, modifier = Modifier.weight(1f), label = "settings",
                transitionSpec = { val fwd = targetState != null; NovaMotion.axisEnter(fwd) togetherWith NovaMotion.axisExit(fwd) }
            ) { id ->
                val sec = sections.firstOrNull { it.id == id }
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
                    if (sec == null) {
                        IosLargeTitle(L("الإعدادات"))
                        rootGroups.forEach { ids ->
                            val list = ids.mapNotNull { gid -> sections.firstOrNull { it.id == gid } }
                            if (list.isNotEmpty()) {
                                Spacer(Modifier.height(20.dp))
                                IosGroup {
                                    list.forEachIndexed { i, sp ->
                                        IosCell(
                                            sp.title, { section = sp.id }, chevron = true, divider = i < list.size - 1,
                                            leading = { IosTile(tints[sp.id] ?: ios.gray) { Icon(sp.icon, null, Modifier.size(18.dp)) } }
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        Spacer(Modifier.height(12.dp))
                        IosGroup {
                            sec.rows.forEachIndexed { i, r ->
                                val asDetail = r.trailing == null && r.sub != null && r.sub.length <= 24
                                IosCell(
                                    r.title, r.onClick, sub = if (asDetail) null else r.sub, detail = if (asDetail) r.sub else null,
                                    trailing = r.trailing, chevron = asDetail, divider = i < sec.rows.size - 1
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    when (dialog) {
        "ytlog" -> NovaDialog(
            title = L("سجل التشخيص"), icon = Icons.Default.Info, onDismiss = { dialog = null },
            confirmText = L("نسخ"), onConfirm = { copyText(ctx, YtLog.text()) },
            extraAction = { TextButton(onClick = { YtLog.clear(); dialog = null }) { Text(L("مسح السجل")) } }
        ) {
            Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
                Text(YtLog.text().ifBlank { L("لا يوجد سجل بعد") }, style = MaterialTheme.typography.bodySmall)
            }
        }
        "gacct" -> NovaDialog(
            title = L("حسابات Google"), icon = Icons.Default.Person, onDismiss = { dialog = null },
            confirmText = L("إضافة"), onConfirm = { if (GoogleAccounts.add(gEmail)) { gEmail = ""; gErr = false } else gErr = true }
        ) {
                Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
                    GoogleAccounts.accounts.toList().forEach { a ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { GoogleAccounts.choosePrimary(a) }.padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = a == GoogleAccounts.primary, onClick = null)
                            Spacer(Modifier.width(12.dp))
                            Text(a, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            IconButton(onClick = { GoogleAccounts.remove(a) }) { Icon(Icons.Default.Delete, L("حذف")) }
                        }
                    }
                    TextButton(onClick = { runCatching { gPicker.launch(GoogleAccounts.deviceAccountIntent()) } }) {
                        Text("Choose from accounts on this device")
                    }
                    OutlinedTextField(
                        value = gEmail, onValueChange = { gEmail = it; gErr = false }, singleLine = true,
                        label = { Text(L("البريد الإلكتروني")) }, isError = gErr,
                        supportingText = if (gErr) ({ Text(L("بريد غير صالح")) }) else null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Done),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    )
                    Text(
                        L("يُحفظ البريد فقط على جهازك. لا تُخزَّن كلمة مرور Google، ولا تنتقل جلسة Google إلى Stube."),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp)
                    )
                }
        }
        "pwmode" -> NovaChoiceDialog(L("وضع التعبئة التلقائية"), pwNames, Prefs.pwMode.coerceIn(0, 2), Icons.Default.Lock, { Prefs.pickPwMode(it) }) { dialog = null }
        "lang" -> NovaChoiceDialog(L("لغة التطبيق"), langNames, Prefs.lang.coerceIn(0, 2), Icons.Default.Settings, { Prefs.pickLang(it) }) { dialog = null }
        "sitelang" -> NovaChoiceDialog(L("لغة المواقع"), Prefs.siteLangs.map { if (it.first.isEmpty()) L(it.second) else it.second }, Prefs.siteLang, Icons.Default.Search, { Prefs.pickSiteLang(it) }) { dialog = null }
        "autoclose" -> ChoiceDialog(L("إغلاق التبويبات تلقائياً"), autoCloseNames, Prefs.autoClose.coerceIn(0, 3), { Prefs.pickAutoClose(it) }) { dialog = null }
        "zoom" -> ChoiceDialog(L("حجم الخط في المواقع"), zoomNames, Prefs.textZoom, { Prefs.pickTextZoom(it) }) { dialog = null }
        "yccsize" -> ChoiceDialog(L("حجم الترجمة"), ccSizeNames, Prefs.ytCcSize.coerceIn(0, 3), { Prefs.pickYtCcSize(it) }) { dialog = null }
        "yccbg" -> ChoiceDialog(L("خلفية الترجمة"), ccBgNames, Prefs.ytCcBg.coerceIn(0, 2), { Prefs.pickYtCcBg(it) }) { dialog = null }
        "yccpos" -> ChoiceDialog(L("موضع الترجمة"), ccPosNames, Prefs.ytCcPos.coerceIn(0, 2), { Prefs.pickYtCcPos(it) }) { dialog = null }
        "engine" -> NovaChoiceDialog(L("محرك البحث"), Prefs.engines.map { it.first }, Prefs.engine, Icons.Default.Search, { Prefs.pickEngine(it) }) { dialog = null }
        "theme" -> NovaChoiceDialog(L("المظهر"), themeNames, Prefs.theme, Icons.Default.Star, { Prefs.pickTheme(it, ctx) }) { dialog = null }
        "conns" -> ChoiceDialog(L("الحد الأقصى للاتصالات"), connNames, connOpts.indexOf(Prefs.maxConns).coerceAtLeast(0), { Prefs.pickMaxConns(connOpts[it]) }) { dialog = null }
        "crash" -> NovaDialog(
            title = L("سجل الأعطال"), icon = Icons.Default.Warning, onDismiss = { dialog = null },
            confirmText = L("مشاركة"), onConfirm = { shareText(ctx, CrashLog.text()) },
            extraAction = { TextButton(onClick = { CrashLog.clear(); dialog = null }) { Text(L("مسح السجل")) } }
        ) {
            Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
                androidx.compose.foundation.text.selection.SelectionContainer {
                    Text(CrashLog.text().ifBlank { L("لا توجد أعطال مسجلة") }, style = MaterialTheme.typography.bodySmall, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                }
            }
        }
        "seclog" -> NovaDialog(
            title = L("سجل الأمان"), icon = Icons.Default.Lock, onDismiss = { dialog = null },
            confirmText = L("إغلاق"), onConfirm = { dialog = null },
            dismissText = L("مسح السجل"), onDismissClick = { Security.clearLog(); dialog = null }
        ) {
            Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                androidx.compose.foundation.text.selection.SelectionContainer { Text(Security.summary(), style = MaterialTheme.typography.bodySmall) }
            }
        }
        "cache" -> NovaDialog(
            title = L("مسح الذاكرة المؤقتة؟"), icon = Icons.Default.Delete, danger = true, onDismiss = { dialog = null },
            confirmText = L("مسح"), onConfirm = { dialog = null; onClearCache(); cacheTick++ }, dismissText = L("إلغاء")
        ) { DialogText(L("سيتم حذف ملفات الكاش المؤقتة فقط. قد يبطؤ تحميل الصفحات المرة القادمة قليلاً.")) }
        "clear" -> NovaDialog(
            title = L("مسح بيانات التصفح؟"), icon = Icons.Default.Delete, danger = true, onDismiss = { dialog = null },
            confirmText = L("مسح"), onConfirm = { dialog = null; onClearData() }, dismissText = L("إلغاء")
        ) { DialogText(L("سيتم حذف الكوكيز والذاكرة المؤقتة وسجل التبويبات. لن تُحذف التنزيلات.")) }
    }
}
