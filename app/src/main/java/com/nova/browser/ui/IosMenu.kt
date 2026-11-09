package com.nova.browser

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties

object MenuBlur {
    var open by mutableIntStateOf(0)
}

/**
 * تمويه الخلفية خلف القوائم. تحريك نصف القطر كل إطار كان يعيد معالجة الشاشة كلها (بما فيها الـ WebView) عند كل إطار فيتقطع فتح القائمة؛
 * الآن يتغيّر نصف القطر بثلاث درجات ثابتة فقط (4 ثم 8 ثم 12dp) فلا إعادة رسم إلا عند تغيّر الدرجة، وتبقى الحركة ناعمة بتلاشي القائمة نفسها.
 */
@Composable
fun Modifier.menuBlur(): Modifier {
    val on = MenuBlur.open > 0 && Prefs.smoothAnim && Adaptive.level < 2
    val p by animateFloatAsState(if (on) 1f else 0f, tween(Adaptive.ms(180)), label = "menuBlur")
    val step by remember { derivedStateOf { (p * 3f).toInt().coerceIn(0, 3) } }
    return if (step > 0) this.blur((step * 4).dp) else this
}

@Composable
fun IosMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset(0.dp, 0.dp),
    properties: PopupProperties = PopupProperties(focusable = true),
    shape: Shape = RoundedCornerShape(14.dp),
    tonalElevation: Dp = 0.dp,
    shadowElevation: Dp = 12.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    DisposableEffect(expanded) {
        if (expanded) MenuBlur.open++
        onDispose { if (expanded) MenuBlur.open-- }
    }
    DropdownMenu(
        expanded = expanded, onDismissRequest = onDismissRequest, modifier = modifier, offset = offset,
        properties = properties, shape = shape, containerColor = LocalIos.current.menu.copy(alpha = 0.78f),
        tonalElevation = tonalElevation, shadowElevation = shadowElevation, content = content
    )
}
