package com.nova.browser

import android.content.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
private fun AlertBtn(text: String, color: Color, bold: Boolean, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val ios = LocalIos.current
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    Box(
        modifier.height(44.dp).background(if (pressed) ios.fill2 else Color.Transparent).alpha(if (enabled) 1f else 0.4f)
            .clickable(interactionSource = src, indication = null, enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text, style = MaterialTheme.typography.bodyLarge, fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 8.dp))
    }
}

@Composable
fun NovaDialog(
    title: String,
    onDismiss: () -> Unit,
    icon: ImageVector? = null,
    confirmText: String? = null,
    onConfirm: (() -> Unit)? = null,
    dismissText: String? = L("إغلاق"),
    onDismissClick: (() -> Unit)? = null,
    danger: Boolean = false,
    extraAction: (@Composable () -> Unit)? = null,
    confirmEnabled: Boolean = true,
    dismissEnabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    val ios = LocalIos.current
    val hasConfirm = confirmText != null && onConfirm != null
    val hasDismiss = dismissText != null
    val total = (if (hasConfirm) 1 else 0) + (if (hasDismiss) 1 else 0) + (if (extraAction != null) 1 else 0)
    val confirmColor = if (danger) ios.red else ios.blue
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.width(280.dp).clip(RoundedCornerShape(30.dp)).background(ios.alert)) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 20.dp, bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = ios.label, textAlign = TextAlign.Center, maxLines = 3, overflow = TextOverflow.Ellipsis)
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
            }
            if (total > 0) IosDivider()
            if (total == 2 && extraAction == null) {
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                    AlertBtn(dismissText ?: "", ios.blue, false, dismissEnabled, Modifier.weight(1f)) { (onDismissClick ?: onDismiss)() }
                    Box(Modifier.fillMaxHeight().width(hairlineDp()).background(ios.separator))
                    AlertBtn(confirmText ?: "", confirmColor, true, confirmEnabled, Modifier.weight(1f)) { onConfirm?.invoke() }
                }
            } else {
                if (hasConfirm) {
                    AlertBtn(confirmText ?: "", confirmColor, true, confirmEnabled, Modifier.fillMaxWidth()) { onConfirm?.invoke() }
                    if (extraAction != null || hasDismiss) IosDivider()
                }
                if (extraAction != null) {
                    Box(Modifier.fillMaxWidth().height(44.dp), contentAlignment = Alignment.Center) { extraAction() }
                    if (hasDismiss) IosDivider()
                }
                if (hasDismiss) AlertBtn(dismissText ?: "", ios.blue, total == 1, dismissEnabled, Modifier.fillMaxWidth()) { (onDismissClick ?: onDismiss)() }
            }
        }
    }
}

@Composable
fun NovaChoiceDialog(
    title: String, options: List<String>, selected: Int, icon: ImageVector? = null,
    onSelect: (Int) -> Unit, onDismiss: () -> Unit
) {
    val ios = LocalIos.current
    NovaDialog(title = title, onDismiss = onDismiss, icon = icon, dismissText = L("إغلاق")) {
        Column(Modifier.fillMaxWidth().heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
            options.forEachIndexed { i, o ->
                val on = i == selected
                IosCell(
                    title = o, onClick = { onSelect(i); onDismiss() }, divider = i < options.size - 1, dividerInset = 0.dp,
                    trailing = if (on) ({ IosIcon(IosGlyph.Check, ios.blue, 20.dp, weight = 1.1f) }) else null
                )
            }
        }
    }
}

@Composable
fun DialogText(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = LocalIos.current.label, textAlign = TextAlign.Center)
}
