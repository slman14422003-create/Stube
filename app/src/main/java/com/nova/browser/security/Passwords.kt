package com.nova.browser

import android.app.Activity
import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.json.JSONObject

class PendingSave(val host: String, val user: String, val pass: String, val kind: SaveKind)
class FillOffer(val tabId: Int, val host: String, val creds: List<Cred>)

@Composable
fun SavePasswordDialog(p: PendingSave, onSave: () -> Unit, onNever: () -> Unit, onDismiss: () -> Unit) {
    val upd = p.kind == SaveKind.UPDATE
    NovaDialog(
        title = if (upd) L("تحديث كلمة المرور؟") else L("حفظ كلمة المرور؟"), icon = Icons.Default.Lock, onDismiss = onDismiss,
        confirmText = if (upd) L("تحديث") else L("حفظ"), onConfirm = onSave,
        dismissText = L("ليس الآن"), onDismissClick = onDismiss,
        extraAction = { IosTextButton(L("عدم الحفظ لهذا الموقع"), onNever) }
    ) {
        Text(p.host, fontWeight = FontWeight.Bold)
        if (p.user.isNotBlank()) Text(p.user)
        DialogText(L("تُخزَّن مشفّرة على جهازك فقط."))
    }
}

@Composable
fun FillBanner(o: FillOffer, onFill: (Cred) -> Unit, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val ios = LocalIos.current
    Surface(
        modifier.padding(horizontal = 12.dp).fillMaxWidth(), shape = RoundedCornerShape(14.dp),
        color = ios.menu, tonalElevation = 0.dp, shadowElevation = 10.dp
    ) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IosIcon(IosGlyph.Lock, ios.label, 18.dp)
                Spacer(Modifier.width(8.dp))
                Text(L("تعبئة تلقائية"), style = MaterialTheme.typography.labelLarge, color = ios.label, modifier = Modifier.weight(1f))
                Box(Modifier.size(32.dp).clickable { onClose() }, contentAlignment = Alignment.Center) { IosIcon(IosGlyph.Close, ios.label2, 18.dp) }
            }
            o.creds.take(3).forEach { c ->
                IosTextButton(c.user.ifBlank { c.host }, { onFill(c) })
            }
        }
    }
}

@Composable
fun PasswordsScreen(onBack: () -> Unit) {
    val ios = LocalIos.current
    val activity = LocalContext.current as Activity
    var query by remember { mutableStateOf("") }
    var sel by remember { mutableStateOf<Cred?>(null) }
    var edit by remember { mutableStateOf<Cred?>(null) }
    var revealed by remember { mutableStateOf(false) }
    var confirmDel by remember { mutableStateOf<Cred?>(null) }
    val q = query.trim()
    val list = Vault.items.filter { q.isBlank() || it.host.contains(q, true) || it.user.contains(q, true) }.sortedBy { it.host }

    Surface(Modifier.fillMaxSize(), color = ios.groupedBg) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            PanelTopBar(L("كلمات المرور"), null, onBack) {
                RoundBtn(onClick = { edit = Cred("", "", "", "", 0L) }) { IosIcon(IosGlyph.Plus, ios.blue, 24.dp) }
            }
            PanelSearch(query, { query = it }, L("بحث"))
            if (list.isEmpty()) {
                PanelEmpty(Icons.Default.Lock, L("لا توجد كلمات مرور محفوظة"))
            } else {
                LazyColumn(
                    Modifier.weight(1f).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(0.dp),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    itemsIndexed(list, key = { _, c -> c.id }) { i, c ->
                        ListRow(groupShape(i, list.size), c.host, c.user.ifBlank { "—" }, { sel = c; revealed = false }, modifier = Modifier.animateItem()) {
                            IconCircle { IosIcon(IosGlyph.Lock, ios.label2, 16.dp) }
                        }
                    }
                }
            }
        }
    }

    sel?.let { c ->
        NovaDialog(
            title = c.host, icon = Icons.Default.Lock, onDismiss = { sel = null }, dismissText = L("إغلاق")
        ) {
                Column {
                    Text(L("اسم المستخدم"), style = MaterialTheme.typography.labelMedium, color = ios.label2)
                    Text(c.user.ifBlank { "—" }, color = ios.label)
                    Spacer(Modifier.height(12.dp))
                    Text(L("كلمة المرور"), style = MaterialTheme.typography.labelMedium, color = ios.label2)
                    Text(if (revealed) c.pass else "••••••••••", color = ios.label)
                    Spacer(Modifier.height(8.dp))
                    Row {
                        IosTextButton(if (revealed) L("إخفاء") else L("إظهار"), {
                            if (revealed) revealed = false else Auth.run(activity, L("تأكيد الهوية")) { revealed = true }
                        })
                        IosTextButton(L("نسخ كلمة المرور"), { Auth.run(activity, L("تأكيد الهوية")) { copyText(activity, c.pass, sensitive = true) } })
                    }
                    Row {
                        IosTextButton(L("نسخ المستخدم"), { copyText(activity, c.user) })
                        IosTextButton(L("تعديل"), { Auth.run(activity, L("تأكيد الهوية")) { edit = c; sel = null } })
                        IosTextButton(L("حذف"), { confirmDel = c }, color = ios.red)
                    }
                }
        }
    }

    confirmDel?.let { c ->
        NovaDialog(
            title = L("حذف كلمة المرور؟"), icon = Icons.Default.Delete, danger = true, onDismiss = { confirmDel = null },
            confirmText = L("حذف"), onConfirm = { Vault.delete(c.id); confirmDel = null; sel = null }, dismissText = L("إلغاء")
        ) { DialogText(c.host + (if (c.user.isNotBlank()) " — " + c.user else "")) }
    }

    edit?.let { e ->
        var host by remember(e) { mutableStateOf(e.host) }
        var user by remember(e) { mutableStateOf(e.user) }
        var pass by remember(e) { mutableStateOf(e.pass) }
        NovaDialog(
            title = if (e.id.isEmpty()) L("إضافة كلمة مرور") else L("تعديل"), icon = Icons.Default.Lock, onDismiss = { edit = null },
            confirmText = L("حفظ"), dismissText = L("إلغاء"),
            onConfirm = { if (Vault.cleanHost(host).isNotBlank() && pass.isNotEmpty()) { Vault.upsert(e.id, Vault.cleanHost(host), user.trim(), pass); edit = null } }
        ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    IosField(host, { host = it }, L("الموقع (مثال: example.com)"), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
                    IosField(user, { user = it }, L("اسم المستخدم"))
                    IosField(pass, { pass = it }, L("كلمة المرور"), password = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
                }
        }
    }
}
