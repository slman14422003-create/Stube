package com.nova.browser

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.SharedPreferences
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.compose.runtime.mutableStateListOf
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** بيانات دخول محفوظة (غير قابلة للتعديل؛ التعديل يستبدل الكائن كي تتحدّث الواجهة). */
class Cred(val id: String, val host: String, val user: String, val pass: String, val updated: Long)

enum class SaveKind { NEW, UPDATE, SAME }

/**
 * مخزن كلمات المرور: يُشفَّر كاملاً بـ AES-256-GCM بمفتاح داخل Android Keystore (لا يخرج من الجهاز).
 * إظهار/نسخ/تعبئة كلمة المرور يتطلب بصمة أو قفل الشاشة (انظر Auth).
 */
object Vault {
    private const val ALIAS = "nova_vault_key"
    private lateinit var sp: SharedPreferences
    private var inited = false
    val items = mutableStateListOf<Cred>()

    fun init(c: Context) {
        if (inited) return
        inited = true
        sp = c.applicationContext.getSharedPreferences("vault", Context.MODE_PRIVATE)
        load()
    }

    // ───────────── التشفير ─────────────
    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val g = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        g.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).build()
        )
        return g.generateKey()
    }

    private fun encrypt(plain: ByteArray): String {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key())
        val ct = c.doFinal(plain)
        return Base64.encodeToString(c.iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(ct, Base64.NO_WRAP)
    }

    private fun decrypt(s: String): ByteArray {
        val parts = s.split(":")
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)))
        return c.doFinal(Base64.decode(parts[1], Base64.NO_WRAP))
    }

    private fun load() {
        val s = sp.getString("data", null) ?: return
        runCatching {
            val arr = JSONArray(String(decrypt(s), Charsets.UTF_8))
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                items.add(Cred(o.getString("id"), o.getString("h"), o.getString("u"), o.getString("p"), o.optLong("t")))
            }
        }.onFailure {
            // فشل فك التشفير (خلل مؤقت في Keystore مثلاً): نحتفظ بنسخة من الملف المشفّر كي لا يمحوها أول حفظ جديد
            items.clear()
            if (!sp.contains("data_bak")) sp.edit().putString("data_bak", s).apply()
        }
    }

    @Synchronized
    private fun persist() {
        val arr = JSONArray()
        items.toList().forEach {
            arr.put(JSONObject().put("id", it.id).put("h", it.host).put("u", it.user).put("p", it.pass).put("t", it.updated))
        }
        runCatching { sp.edit().putString("data", encrypt(arr.toString().toByteArray(Charsets.UTF_8))).apply() }
    }

    // ───────────── الواجهة ─────────────
    fun norm(host: String) = host.trim().lowercase().removePrefix("www.")

    /** ينظّف ما يكتبه المستخدم (رابط كامل مثلاً) إلى اسم النطاق فقط. */
    fun cleanHost(raw: String) =
        norm(raw.trim().removePrefix("https://").removePrefix("http://").substringBefore('/').substringBefore('?'))

    /** مطابقة تامّة للنطاق فقط (لا نطاقات فرعية) لتقليل خطر التصيّد. */
    fun forHost(host: String): List<Cred> { val h = norm(host); return items.filter { it.host == h } }

    fun classify(host: String, user: String, pass: String): SaveKind {
        val h = norm(host)
        val same = items.firstOrNull { it.host == h && it.user == user } ?: return SaveKind.NEW
        return if (same.pass == pass) SaveKind.SAME else SaveKind.UPDATE
    }

    /** إضافة أو تحديث؛ id فارغ = جديد (يحدّث الموجود إن تطابق الموقع والمستخدم). */
    fun upsert(id: String, host: String, user: String, pass: String) {
        val h = norm(host)
        val idx = items.indexOfFirst { if (id.isNotEmpty()) it.id == id else it.host == h && it.user == user }
        val c = Cred(if (idx >= 0) items[idx].id else UUID.randomUUID().toString(), h, user, pass, System.currentTimeMillis())
        if (idx >= 0) items[idx] = c else items.add(c)
        persist()
    }

    fun delete(id: String) { items.removeAll { it.id == id }; persist() }

    // مواقع اختار المستخدم عدم حفظ كلمات المرور لها
    fun isNever(host: String) = sp.getStringSet("never", emptySet())!!.contains(norm(host))
    fun neverSave(host: String) {
        val s = HashSet(sp.getStringSet("never", emptySet())!!); s.add(norm(host))
        sp.edit().putStringSet("never", s).apply()
    }
}

/** تأكيد الهوية ببصمة/وجه/قفل الشاشة. إن لم يكن للجهاز قفل يُسمح مباشرة (لا يمكن التحقق). */
object Auth {
    fun run(activity: Activity, title: String, onOk: () -> Unit) {
        val km = activity.getSystemService(KeyguardManager::class.java)
        if (km == null || !km.isDeviceSecure) { onOk(); return }
        val b = BiometricPrompt.Builder(activity).setTitle(title)
        b.setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
        runCatching {
            b.build().authenticate(CancellationSignal(), activity.mainExecutor, object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) { onOk() }
            })
        }.onFailure { toast(activity, L("تعذّر التحقق من الهوية")) }
    }
}
