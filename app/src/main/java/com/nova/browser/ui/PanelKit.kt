package com.nova.browser

import android.content.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.animation.core.tween
import androidx.compose.foundation.rememberScrollState
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.style.TextAlign

@Composable
fun PanelTopBar(title: String, subtitle: String? = null, onBack: () -> Unit, actions: @Composable RowScope.() -> Unit = {}) {
    val ios = LocalIos.current
    Column(Modifier.fillMaxWidth()) {
        IosNavBar(null, onBack, null, actions)
        IosLargeTitle(title)
        if (subtitle != null) Text(
            subtitle, style = MaterialTheme.typography.bodySmall, color = ios.label2,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 6.dp), maxLines = 1, overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun PanelMenu(content: @Composable ColumnScope.(close: () -> Unit) -> Unit) {
    val ios = LocalIos.current
    var open by remember { mutableStateOf(false) }
    Box {
        RoundBtn(onClick = { open = true }) { IosIcon(IosGlyph.EllipsisCircle, ios.blue, 26.dp) }
        IosMenu(
            expanded = open, onDismissRequest = { open = false }, shape = RoundedCornerShape(14.dp),
            tonalElevation = 0.dp, shadowElevation = 12.dp
        ) {
            content({ open = false })
        }
    }
}

@Composable
fun PanelSearch(value: String, onChange: (String) -> Unit, placeholder: String) {
    val ios = LocalIos.current
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).height(36.dp).clip(RoundedCornerShape(10.dp)).background(ios.fill3).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IosIcon(IosGlyph.Magnifier, ios.label2, 16.dp)
        Spacer(Modifier.width(6.dp))
        BasicTextField(
            value = value, onValueChange = onChange, singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = ios.label), cursorBrush = SolidColor(ios.blue),
            modifier = Modifier.weight(1f),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = ios.label2, maxLines = 1)
                    inner()
                }
            }
        )
        if (value.isNotEmpty()) Box(Modifier.size(28.dp).clickable { onChange("") }, contentAlignment = Alignment.Center) {
            IosIcon(IosGlyph.XCircleFill, ios.label3, 16.dp, accent = if (ios.dark) Color(0xFF3A3A3C) else Color.White)
        }
    }
}

@Composable
fun IosField(
    value: String, onChange: (String) -> Unit, label: String, modifier: Modifier = Modifier,
    placeholder: String = "", password: Boolean = false, keyboardOptions: androidx.compose.foundation.text.KeyboardOptions = androidx.compose.foundation.text.KeyboardOptions.Default
) {
    val ios = LocalIos.current
    Column(modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = ios.label2, modifier = Modifier.padding(start = 2.dp, bottom = 4.dp))
        Box(Modifier.fillMaxWidth().height(40.dp).clip(RoundedCornerShape(9.dp)).background(ios.fill3).padding(horizontal = 10.dp), contentAlignment = Alignment.CenterStart) {
            BasicTextField(
                value = value, onValueChange = onChange, singleLine = true, keyboardOptions = keyboardOptions,
                visualTransformation = if (password) androidx.compose.ui.text.input.PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = ios.label), cursorBrush = SolidColor(ios.blue),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { inner ->
                    if (value.isEmpty() && placeholder.isNotEmpty()) Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = ios.label2, maxLines = 1)
                    inner()
                }
            )
        }
    }
}

@Composable
fun PanelSegments(items: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val ios = LocalIos.current
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).height(32.dp).clip(RoundedCornerShape(9.dp)).background(ios.fill3).padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        items.forEachIndexed { i, t ->
            val on = i == selected
            val bg by animateColorAsState(if (on) (if (ios.dark) Color(0xFF636366) else Color.White) else Color.Transparent, tween(Adaptive.ms(160)), label = "seg")
            Box(
                Modifier.weight(1f).fillMaxHeight().then(if (on) Modifier.shadow(1.dp, RoundedCornerShape(7.dp)) else Modifier)
                    .clip(RoundedCornerShape(7.dp)).background(bg)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onSelect(i) },
                contentAlignment = Alignment.Center
            ) {
                Text(t, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium, color = ios.label, modifier = Modifier.padding(horizontal = 4.dp))
            }
        }
    }
}

@Composable
fun PanelChips(items: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val ios = LocalIos.current
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEachIndexed { i, t ->
            val on = i == selected
            Box(
                Modifier.height(32.dp).clip(CircleShape).background(if (on) ios.blue else ios.fill3)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onSelect(i) }.padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center
            ) { Text(t, style = MaterialTheme.typography.bodyMedium, color = if (on) Color.White else ios.label, maxLines = 1) }
        }
    }
}

@Composable
fun PanelEmpty(icon: ImageVector, title: String, sub: String? = null, modifier: Modifier = Modifier.fillMaxSize()) {
    val ios = LocalIos.current
    Box(modifier.padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, Modifier.size(52.dp), tint = ios.gray2)
            Spacer(Modifier.height(14.dp))
            Text(title, style = MaterialTheme.typography.titleLarge, color = ios.label, textAlign = TextAlign.Center)
            if (sub != null) {
                Spacer(Modifier.height(6.dp))
                Text(sub, style = MaterialTheme.typography.bodyMedium, color = ios.label2, textAlign = TextAlign.Center)
            }
        }
    }
}
