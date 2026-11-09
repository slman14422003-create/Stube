package com.nova.browser

import android.Manifest
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSession.PromptDelegate.PromptResponse
import java.util.Calendar

class GeckoUi(private val h: Handlers) : GeckoSession.PromptDelegate, GeckoSession.PermissionDelegate {
    private val act get() = h.activity

    private fun has(p: String) = ContextCompat.checkSelfPermission(act, p) == PackageManager.PERMISSION_GRANTED

    override fun onAlertPrompt(session: GeckoSession, prompt: GeckoSession.PromptDelegate.AlertPrompt): GeckoResult<PromptResponse>? {
        val res = GeckoResult<PromptResponse>()
        AlertDialog.Builder(act)
            .setTitle(prompt.title)
            .setMessage(prompt.message)
            .setPositiveButton(android.R.string.ok) { _, _ -> res.complete(prompt.dismiss()) }
            .setOnCancelListener { res.complete(prompt.dismiss()) }
            .show()
        return res
    }

    override fun onButtonPrompt(session: GeckoSession, prompt: GeckoSession.PromptDelegate.ButtonPrompt): GeckoResult<PromptResponse>? {
        val res = GeckoResult<PromptResponse>()
        AlertDialog.Builder(act)
            .setTitle(prompt.title)
            .setMessage(prompt.message)
            .setPositiveButton(android.R.string.ok) { _, _ -> res.complete(prompt.confirm(GeckoSession.PromptDelegate.ButtonPrompt.Type.POSITIVE)) }
            .setNegativeButton(android.R.string.cancel) { _, _ -> res.complete(prompt.confirm(GeckoSession.PromptDelegate.ButtonPrompt.Type.NEGATIVE)) }
            .setOnCancelListener { res.complete(prompt.dismiss()) }
            .show()
        return res
    }

    override fun onTextPrompt(session: GeckoSession, prompt: GeckoSession.PromptDelegate.TextPrompt): GeckoResult<PromptResponse>? {
        val res = GeckoResult<PromptResponse>()
        val input = EditText(act)
        input.setText(prompt.defaultValue ?: "")
        AlertDialog.Builder(act)
            .setTitle(prompt.title)
            .setMessage(prompt.message)
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ -> res.complete(prompt.confirm(input.text.toString())) }
            .setNegativeButton(android.R.string.cancel) { _, _ -> res.complete(prompt.dismiss()) }
            .setOnCancelListener { res.complete(prompt.dismiss()) }
            .show()
        return res
    }

    override fun onAuthPrompt(session: GeckoSession, prompt: GeckoSession.PromptDelegate.AuthPrompt): GeckoResult<PromptResponse>? {
        val res = GeckoResult<PromptResponse>()
        val box = LinearLayout(act)
        box.orientation = LinearLayout.VERTICAL
        val pad = (16 * act.resources.displayMetrics.density).toInt()
        box.setPadding(pad, pad / 2, pad, 0)
        val user = EditText(act)
        user.hint = "Username"
        val pass = EditText(act)
        pass.hint = "Password"
        pass.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        box.addView(user)
        box.addView(pass)
        AlertDialog.Builder(act)
            .setTitle(prompt.title)
            .setMessage(prompt.message)
            .setView(box)
            .setPositiveButton(android.R.string.ok) { _, _ -> res.complete(prompt.confirm(user.text.toString(), pass.text.toString())) }
            .setNegativeButton(android.R.string.cancel) { _, _ -> res.complete(prompt.dismiss()) }
            .setOnCancelListener { res.complete(prompt.dismiss()) }
            .show()
        return res
    }

    override fun onChoicePrompt(session: GeckoSession, prompt: GeckoSession.PromptDelegate.ChoicePrompt): GeckoResult<PromptResponse>? {
        val res = GeckoResult<PromptResponse>()
        val flat = ArrayList<GeckoSession.PromptDelegate.ChoicePrompt.Choice>()
        fun add(cs: Array<GeckoSession.PromptDelegate.ChoicePrompt.Choice>?) {
            cs?.forEach { c ->
                if (c.items != null) add(c.items)
                else if (!c.separator && !c.disabled) flat.add(c)
            }
        }
        add(prompt.choices)
        val labels = flat.map { it.label }.toTypedArray()
        val b = AlertDialog.Builder(act)
        if (!prompt.title.isNullOrBlank()) b.setTitle(prompt.title)
        if (prompt.type == GeckoSession.PromptDelegate.ChoicePrompt.Type.MULTIPLE) {
            val sel = BooleanArray(flat.size) { flat[it].selected }
            b.setMultiChoiceItems(labels, sel) { _, i, c -> sel[i] = c }
            b.setPositiveButton(android.R.string.ok) { _, _ ->
                val ids = flat.filterIndexed { i, _ -> sel[i] }.map { it.id }.toTypedArray()
                res.complete(prompt.confirm(ids))
            }
            b.setNegativeButton(android.R.string.cancel) { _, _ -> res.complete(prompt.dismiss()) }
        } else {
            b.setItems(labels) { _, i -> res.complete(prompt.confirm(flat[i])) }
        }
        b.setOnCancelListener { res.complete(prompt.dismiss()) }
        b.show()
        return res
    }

    override fun onDateTimePrompt(session: GeckoSession, prompt: GeckoSession.PromptDelegate.DateTimePrompt): GeckoResult<PromptResponse>? {
        val res = GeckoResult<PromptResponse>()
        val cal = Calendar.getInstance()
        when (prompt.type) {
            GeckoSession.PromptDelegate.DateTimePrompt.Type.DATE -> {
                val d = DatePickerDialog(act, { _, y, m, day ->
                    res.complete(prompt.confirm(String.format("%04d-%02d-%02d", y, m + 1, day)))
                }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH))
                d.setOnCancelListener { res.complete(prompt.dismiss()) }
                d.show()
            }
            GeckoSession.PromptDelegate.DateTimePrompt.Type.TIME -> {
                val d = TimePickerDialog(act, { _, hh, mm ->
                    res.complete(prompt.confirm(String.format("%02d:%02d", hh, mm)))
                }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true)
                d.setOnCancelListener { res.complete(prompt.dismiss()) }
                d.show()
            }
            else -> res.complete(prompt.dismiss())
        }
        return res
    }

    override fun onColorPrompt(session: GeckoSession, prompt: GeckoSession.PromptDelegate.ColorPrompt): GeckoResult<PromptResponse>? {
        val res = GeckoResult<PromptResponse>()
        val colors = intArrayOf(
            0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0xFFFF3B30.toInt(), 0xFFFF9500.toInt(), 0xFFFFCC00.toInt(), 0xFF34C759.toInt(),
            0xFF00C7BE.toInt(), 0xFF007AFF.toInt(), 0xFF5856D6.toInt(), 0xFFAF52DE.toInt(), 0xFFFF2D55.toInt(), 0xFF8E8E93.toInt()
        )
        val names = arrayOf("#000000", "#FFFFFF", "#FF3B30", "#FF9500", "#FFCC00", "#34C759", "#00C7BE", "#007AFF", "#5856D6", "#AF52DE", "#FF2D55", "#8E8E93")
        AlertDialog.Builder(act)
            .setItems(names) { _, i -> res.complete(prompt.confirm(names[i].lowercase())) }
            .setNegativeButton(android.R.string.cancel) { _, _ -> res.complete(prompt.dismiss()) }
            .setOnCancelListener { res.complete(prompt.dismiss()) }
            .show()
        return res
    }

    override fun onPopupPrompt(session: GeckoSession, prompt: GeckoSession.PromptDelegate.PopupPrompt): GeckoResult<PromptResponse>? {
        val host = runCatching { Uri.parse(prompt.targetUri ?: "").host }.getOrNull().orEmpty()
        val ok = GoogleAccounts.isAuthHost(host)
        return GeckoResult.fromValue(prompt.confirm(if (ok) AllowOrDeny.ALLOW else AllowOrDeny.DENY))
    }

    override fun onRepostConfirmPrompt(session: GeckoSession, prompt: GeckoSession.PromptDelegate.RepostConfirmPrompt): GeckoResult<PromptResponse>? =
        GeckoResult.fromValue(prompt.confirm(AllowOrDeny.ALLOW))

    override fun onBeforeUnloadPrompt(session: GeckoSession, prompt: GeckoSession.PromptDelegate.BeforeUnloadPrompt): GeckoResult<PromptResponse>? =
        GeckoResult.fromValue(prompt.confirm(AllowOrDeny.ALLOW))

    override fun onFilePrompt(session: GeckoSession, prompt: GeckoSession.PromptDelegate.FilePrompt): GeckoResult<PromptResponse>? {
        val res = GeckoResult<PromptResponse>()
        val multiple = prompt.type == GeckoSession.PromptDelegate.FilePrompt.Type.MULTIPLE
        val types = prompt.mimeTypes?.filter { it.isNotBlank() && !it.startsWith(".") }.orEmpty()
        val i = Intent(Intent.ACTION_GET_CONTENT)
        i.addCategory(Intent.CATEGORY_OPENABLE)
        if (types.size == 1) i.type = types[0]
        else {
            i.type = "*/*"
            if (types.isNotEmpty()) i.putExtra(Intent.EXTRA_MIME_TYPES, types.toTypedArray())
        }
        if (multiple) i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        val cb: (Array<Uri>?) -> Unit = { uris ->
            if (uris == null || uris.isEmpty()) res.complete(prompt.dismiss())
            else if (multiple) res.complete(prompt.confirm(act, uris))
            else res.complete(prompt.confirm(act, uris[0]))
        }
        h.filePick(Intent.createChooser(i, null), cb)
        return res
    }

    override fun onAndroidPermissionsRequest(
        session: GeckoSession,
        permissions: Array<out String>?,
        callback: GeckoSession.PermissionDelegate.Callback
    ) {
        val list = permissions?.toList().orEmpty()
        h.androidPerm(list) {
            if (list.all { has(it) }) callback.grant() else callback.reject()
        }
    }

    override fun onContentPermissionRequest(
        session: GeckoSession,
        perm: GeckoSession.PermissionDelegate.ContentPermission
    ): GeckoResult<Int>? {
        val allow = GeckoSession.PermissionDelegate.ContentPermission.VALUE_ALLOW
        val deny = GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY
        return when (perm.permission) {
            GeckoSession.PermissionDelegate.PERMISSION_GEOLOCATION -> {
                val res = GeckoResult<Int>()
                h.sitePerm(
                    perm.uri,
                    "location",
                    listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                ) { ok -> res.complete(if (ok) allow else deny) }
                res
            }
            GeckoSession.PermissionDelegate.PERMISSION_PERSISTENT_STORAGE,
            GeckoSession.PermissionDelegate.PERMISSION_MEDIA_KEY_SYSTEM_ACCESS,
            GeckoSession.PermissionDelegate.PERMISSION_STORAGE_ACCESS,
            GeckoSession.PermissionDelegate.PERMISSION_AUTOPLAY_INAUDIBLE -> GeckoResult.fromValue(allow)
            else -> GeckoResult.fromValue(deny)
        }
    }

    override fun onMediaPermissionRequest(
        session: GeckoSession,
        uri: String,
        video: Array<out GeckoSession.PermissionDelegate.MediaSource>?,
        audio: Array<out GeckoSession.PermissionDelegate.MediaSource>?,
        callback: GeckoSession.PermissionDelegate.MediaCallback
    ) {
        val need = ArrayList<String>()
        val labels = ArrayList<String>()
        if (!video.isNullOrEmpty()) { need.add(Manifest.permission.CAMERA); labels.add("camera") }
        if (!audio.isNullOrEmpty()) { need.add(Manifest.permission.RECORD_AUDIO); labels.add("microphone") }
        if (need.isEmpty()) { callback.reject(); return }
        h.sitePerm(uri, labels.joinToString(" and "), need) { ok ->
            val v: GeckoSession.PermissionDelegate.MediaSource? =
                if (ok && has(Manifest.permission.CAMERA)) video?.firstOrNull() else null
            val a: GeckoSession.PermissionDelegate.MediaSource? =
                if (ok && has(Manifest.permission.RECORD_AUDIO)) audio?.firstOrNull() else null
            if (v == null && a == null) callback.reject() else callback.grant(v, a)
        }
    }
}
