package com.nova.browser

import android.content.*
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.alpha
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalLayoutDirection
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

enum class IosGlyph {
    Back, Forward, Disclosure, ChevronDown, ChevronUp, Share, Book, Tabs, Reload, Close, Plus, Magnifier, Lock,
    Ellipsis, EllipsisCircle, Check, ShieldFill, ArrowUpLeft, Copy, PlusSquare, Printer, XCircleFill, Download,
    Trash, Play, Pause
}

private fun IosGlyph.mirrors() = this == IosGlyph.Back || this == IosGlyph.Forward || this == IosGlyph.Disclosure || this == IosGlyph.ArrowUpLeft

@Composable
fun IosIcon(kind: IosGlyph, tint: Color, size: Dp = 24.dp, modifier: Modifier = Modifier, accent: Color = Color.White, weight: Float = 1f) {
    val mir = LocalLayoutDirection.current == LayoutDirection.Rtl && kind.mirrors()
    Canvas(modifier.size(size)) {
        val s = this.size.minDimension / 24f
        val sw = 2.2f * s * weight
        val st = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
        fun X(x: Float) = (if (mir) 24f - x else x) * s
        fun Y(y: Float) = y * s
        fun P(x: Float, y: Float) = Offset(X(x), Y(y))
        fun Path.m(x: Float, y: Float) = moveTo(X(x), Y(y))
        fun Path.l(x: Float, y: Float) = lineTo(X(x), Y(y))
        fun Path.q(x1: Float, y1: Float, x2: Float, y2: Float) = quadraticBezierTo(X(x1), Y(y1), X(x2), Y(y2))
        fun Path.c(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) = cubicTo(X(x1), Y(y1), X(x2), Y(y2), X(x3), Y(y3))
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) = drawLine(tint, P(x1, y1), P(x2, y2), strokeWidth = sw, cap = StrokeCap.Round)
        fun poly(vararg p: Float) {
            val path = Path()
            path.m(p[0], p[1])
            var i = 2
            while (i < p.size) { path.l(p[i], p[i + 1]); i += 2 }
            drawPath(path, tint, style = st)
        }
        when (kind) {
            IosGlyph.Back -> poly(15f, 5f, 8f, 12f, 15f, 19f)
            IosGlyph.Forward, IosGlyph.Disclosure -> poly(9f, 5f, 16f, 12f, 9f, 19f)
            IosGlyph.ChevronDown -> poly(5f, 9f, 12f, 16f, 19f, 9f)
            IosGlyph.ChevronUp -> poly(5f, 15f, 12f, 8f, 19f, 15f)
            IosGlyph.Close -> { line(6f, 6f, 18f, 18f); line(18f, 6f, 6f, 18f) }
            IosGlyph.Plus -> { line(12f, 5f, 12f, 19f); line(5f, 12f, 19f, 12f) }
            IosGlyph.Check -> poly(5f, 12.5f, 10f, 17.5f, 19f, 7f)
            IosGlyph.Magnifier -> {
                drawCircle(tint, radius = 6.2f * s, center = P(10.5f, 10.5f), style = st)
                line(15.2f, 15.2f, 20f, 20f)
            }
            IosGlyph.Lock -> {
                drawRoundRect(tint, Offset(5.5f * s, 10.5f * s), Size(13f * s, 10f * s), CornerRadius(2.4f * s))
                drawArc(tint, 180f, 180f, false, Offset(8.7f * s, 4.2f * s), Size(6.6f * s, 6.6f * s), style = st)
                line(8.7f, 7.5f, 8.7f, 10.5f); line(15.3f, 7.5f, 15.3f, 10.5f)
            }
            IosGlyph.Ellipsis -> {
                drawCircle(tint, 1.9f * s, P(5.5f, 12f)); drawCircle(tint, 1.9f * s, P(12f, 12f)); drawCircle(tint, 1.9f * s, P(18.5f, 12f))
            }
            IosGlyph.EllipsisCircle -> {
                drawCircle(tint, radius = 9f * s, center = P(12f, 12f), style = Stroke(width = sw * 0.85f))
                drawCircle(tint, 1.25f * s, P(8f, 12f)); drawCircle(tint, 1.25f * s, P(12f, 12f)); drawCircle(tint, 1.25f * s, P(16f, 12f))
            }
            IosGlyph.Share -> {
                val b = Path()
                b.m(8.5f, 10f); b.l(7.5f, 10f); b.q(5f, 10f, 5f, 12.5f); b.l(5f, 18.5f); b.q(5f, 21f, 7.5f, 21f)
                b.l(16.5f, 21f); b.q(19f, 21f, 19f, 18.5f); b.l(19f, 12.5f); b.q(19f, 10f, 16.5f, 10f); b.l(15.5f, 10f)
                drawPath(b, tint, style = st)
                line(12f, 14.5f, 12f, 3.5f)
                poly(8.2f, 7.2f, 12f, 3.5f, 15.8f, 7.2f)
            }
            IosGlyph.Book -> {
                val a = Path()
                a.m(12f, 6.8f); a.c(10.2f, 5.2f, 7.2f, 4.6f, 3.8f, 5.2f); a.l(3.8f, 18f); a.c(7.2f, 17.4f, 10.2f, 18f, 12f, 19.6f)
                val b = Path()
                b.m(12f, 6.8f); b.c(13.8f, 5.2f, 16.8f, 4.6f, 20.2f, 5.2f); b.l(20.2f, 18f); b.c(16.8f, 17.4f, 13.8f, 18f, 12f, 19.6f)
                drawPath(a, tint, style = st); drawPath(b, tint, style = st)
                line(12f, 6.8f, 12f, 19.6f)
            }
            IosGlyph.Tabs -> {
                drawRoundRect(tint, Offset(3.5f * s, 8.5f * s), Size(12f * s, 12f * s), CornerRadius(3f * s), style = st)
                val b = Path()
                b.m(8.5f, 8.5f); b.l(8.5f, 6.5f); b.q(8.5f, 3.5f, 11.5f, 3.5f); b.l(17.5f, 3.5f); b.q(20.5f, 3.5f, 20.5f, 6.5f)
                b.l(20.5f, 12.5f); b.q(20.5f, 15.5f, 17.5f, 15.5f); b.l(15.5f, 15.5f)
                drawPath(b, tint, style = st)
            }
            IosGlyph.Reload -> {
                val cx = 12f * s; val cy = 12.4f * s; val r = 7.2f * s
                drawArc(tint, -30f, 290f, false, Offset(cx - r, cy - r), Size(2 * r, 2 * r), style = st)
                val a = 260.0 * PI / 180.0
                val tip = Offset(cx + r * cos(a).toFloat(), cy + r * sin(a).toFloat())
                val bx = (sin(a)).toFloat(); val by = (-cos(a)).toFloat()
                val len = 4.4f * s
                val c40 = cos(40.0 * PI / 180.0).toFloat(); val s40 = sin(40.0 * PI / 180.0).toFloat()
                val h1 = Offset(tip.x + len * (bx * c40 - by * s40), tip.y + len * (bx * s40 + by * c40))
                val h2 = Offset(tip.x + len * (bx * c40 + by * s40), tip.y + len * (-bx * s40 + by * c40))
                drawLine(tint, tip, h1, strokeWidth = sw, cap = StrokeCap.Round)
                drawLine(tint, tip, h2, strokeWidth = sw, cap = StrokeCap.Round)
            }
            IosGlyph.ShieldFill -> {
                val p = Path()
                p.m(12f, 2.8f); p.l(19.6f, 5.8f); p.l(19.6f, 11.4f); p.c(19.6f, 16.2f, 16.4f, 19.8f, 12f, 21.4f)
                p.c(7.6f, 19.8f, 4.4f, 16.2f, 4.4f, 11.4f); p.l(4.4f, 5.8f); p.close()
                drawPath(p, tint)
                val ck = Path()
                ck.m(8.4f, 11.8f); ck.l(11f, 14.4f); ck.l(15.8f, 9.2f)
                drawPath(ck, accent, style = st)
            }
            IosGlyph.ArrowUpLeft -> { line(17f, 17f, 7.5f, 7.5f); poly(7f, 14f, 7f, 7f, 14f, 7f) }
            IosGlyph.Copy -> {
                drawRoundRect(tint, Offset(8f * s, 8f * s), Size(11f * s, 12.5f * s), CornerRadius(2.5f * s), style = st)
                val b = Path()
                b.m(8f, 16f); b.l(7.5f, 16f); b.q(5f, 16f, 5f, 13.5f); b.l(5f, 6.5f); b.q(5f, 4f, 7.5f, 4f); b.l(12.5f, 4f); b.q(15f, 4f, 15f, 6.5f); b.l(15f, 8f)
                drawPath(b, tint, style = st)
            }
            IosGlyph.PlusSquare -> {
                drawRoundRect(tint, Offset(4f * s, 4f * s), Size(16f * s, 16f * s), CornerRadius(4f * s), style = st)
                line(12f, 8.2f, 12f, 15.8f); line(8.2f, 12f, 15.8f, 12f)
            }
            IosGlyph.Printer -> {
                drawRoundRect(tint, Offset(3.5f * s, 8.5f * s), Size(17f * s, 9f * s), CornerRadius(2.2f * s), style = st)
                drawRoundRect(tint, Offset(7f * s, 3.5f * s), Size(10f * s, 5f * s), CornerRadius(1f * s), style = st)
                drawRoundRect(tint, Offset(7f * s, 14f * s), Size(10f * s, 6.5f * s), CornerRadius(1f * s), style = st)
            }
            IosGlyph.Download -> { line(12f, 4f, 12f, 15f); poly(7.5f, 10.5f, 12f, 15f, 16.5f, 10.5f); line(5f, 19.5f, 19f, 19.5f) }
            IosGlyph.Trash -> {
                line(4.5f, 7f, 19.5f, 7f)
                line(9.5f, 3.8f, 14.5f, 3.8f)
                val b = Path()
                b.m(6.2f, 7f); b.l(7f, 19.2f); b.q(7.1f, 20.5f, 8.4f, 20.5f); b.l(15.6f, 20.5f); b.q(16.9f, 20.5f, 17f, 19.2f); b.l(17.8f, 7f)
                drawPath(b, tint, style = st)
                line(10f, 10.3f, 10.3f, 17f); line(14f, 10.3f, 13.7f, 17f)
            }
            IosGlyph.Play -> {
                val p = Path()
                p.m(7.3f, 4.8f); p.l(19f, 12f); p.l(7.3f, 19.2f); p.close()
                drawPath(p, tint, style = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            IosGlyph.Pause -> {
                drawRoundRect(tint, Offset(6f * s, 4.5f * s), Size(4f * s, 15f * s), CornerRadius(1.6f * s))
                drawRoundRect(tint, Offset(14f * s, 4.5f * s), Size(4f * s, 15f * s), CornerRadius(1.6f * s))
            }
            IosGlyph.XCircleFill -> {
                drawCircle(tint, radius = 10f * s, center = P(12f, 12f))
                drawLine(accent, P(8.6f, 8.6f), P(15.4f, 15.4f), strokeWidth = 2f * s, cap = StrokeCap.Round)
                drawLine(accent, P(15.4f, 8.6f), P(8.6f, 15.4f), strokeWidth = 2f * s, cap = StrokeCap.Round)
            }
        }
    }
}

@Composable
fun IosSwitch(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)? = null, modifier: Modifier = Modifier) {
    val ios = LocalIos.current
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val src = remember { MutableInteractionSource() }
    val down by src.collectIsPressedAsState()
    val thumbW by animateDpAsState(
        if (down && onCheckedChange != null) 46.dp else 40.dp,
        androidx.compose.animation.core.spring(dampingRatio = 0.8f, stiffness = 700f), label = "swt"
    )
    val moveSpec: androidx.compose.animation.core.AnimationSpec<Dp> =
        if (Adaptive.level == 0) androidx.compose.animation.core.spring(dampingRatio = 0.78f, stiffness = 420f) else tween(Adaptive.ms(200))
    val x by animateDpAsState(if (checked) 64.dp - thumbW - 2.dp else 2.dp, moveSpec, label = "sw")
    val track by animateColorAsState(if (checked) ios.green else ios.fill2, tween(Adaptive.ms(250)), label = "swc")
    Box(
        modifier.size(width = 64.dp, height = 28.dp).clip(CircleShape).background(track)
            .then(
                if (onCheckedChange != null) Modifier.clickable(interactionSource = src, indication = null) {
                    runCatching { haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove) }
                    onCheckedChange(!checked)
                } else Modifier
            )
    ) {
        Box(Modifier.offset(x = x, y = 2.dp).size(width = thumbW, height = 24.dp).shadow(4.dp, CircleShape).background(Color.White, CircleShape))
    }
}

class IosPos(val first: Boolean, val last: Boolean)

fun groupShape(i: Int, n: Int) = IosPos(i == 0, i == n - 1)

@Composable
fun IosTile(color: Color, size: Dp = 29.dp, content: @Composable () -> Unit) {
    Box(Modifier.size(size).clip(RoundedCornerShape(size * 0.225f)).background(color), contentAlignment = Alignment.Center) {
        CompositionLocalProvider(LocalContentColor provides Color.White) { content() }
    }
}

@Composable
fun IconCircle(content: @Composable () -> Unit) {
    val ios = LocalIos.current
    Box(Modifier.size(29.dp).clip(RoundedCornerShape(7.dp)).background(ios.fill3), contentAlignment = Alignment.Center) {
        CompositionLocalProvider(LocalContentColor provides ios.label2) { content() }
    }
}

@Composable
fun hairlineDp(): Dp = (1f / androidx.compose.ui.platform.LocalDensity.current.density).dp

@Composable
fun IosDivider(inset: Dp = 0.dp, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(start = inset).height(hairlineDp()).background(LocalIos.current.separator))
}

@Composable
fun IosCell(
    title: String, onClick: (() -> Unit)?, modifier: Modifier = Modifier, sub: String? = null, detail: String? = null,
    leading: (@Composable () -> Unit)? = null, trailing: (@Composable () -> Unit)? = null, chevron: Boolean = false,
    enabled: Boolean = true, tint: Color = Color.Unspecified, divider: Boolean = true, dividerInset: Dp? = null
) {
    val ios = LocalIos.current
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    Column(modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.4f)) {
        Row(
            Modifier.fillMaxWidth()
                .background(if (pressed && onClick != null) ios.fill2 else Color.Transparent)
                .then(if (onClick != null) Modifier.clickable(interactionSource = src, indication = null, enabled = enabled, onClick = onClick) else Modifier)
                .heightIn(min = 44.dp).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (leading != null) { leading(); Spacer(Modifier.width(16.dp)) }
            Column(Modifier.weight(1f)) {
                Text(
                    title, style = MaterialTheme.typography.bodyLarge, color = if (tint != Color.Unspecified) tint else ios.label,
                    maxLines = if (sub == null) 2 else 1, overflow = TextOverflow.Ellipsis
                )
                if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = ios.label2, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            if (detail != null) {
                Spacer(Modifier.width(12.dp))
                Text(detail, style = MaterialTheme.typography.bodyLarge, color = ios.label2, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 170.dp))
            }
            if (trailing != null) { Spacer(Modifier.width(12.dp)); trailing() }
            if (chevron) { Spacer(Modifier.width(8.dp)); IosIcon(IosGlyph.Disclosure, ios.label3, 14.dp, weight = 1.3f) }
        }
        if (divider) IosDivider(dividerInset ?: if (leading != null) 61.dp else 16.dp)
    }
}

@Composable
fun ListRow(
    shape: IosPos, title: String, sub: String?, onClick: () -> Unit,
    enabled: Boolean = true, trailing: (@Composable () -> Unit)? = null, modifier: Modifier = Modifier, leading: @Composable () -> Unit
) {
    val ios = LocalIos.current
    val r = 24.dp
    val clipShape = RoundedCornerShape(
        topStart = if (shape.first) r else 0.dp, topEnd = if (shape.first) r else 0.dp,
        bottomStart = if (shape.last) r else 0.dp, bottomEnd = if (shape.last) r else 0.dp
    )
    Box(modifier.fillMaxWidth().clip(clipShape).background(ios.cell)) {
        IosCell(title = title, sub = sub, onClick = onClick, enabled = enabled, leading = leading, trailing = trailing, divider = !shape.last)
    }
}

@Composable
fun IosGroup(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(24.dp)).background(LocalIos.current.cell), content = content)
}

@Composable
fun IosNavBar(title: String?, onBack: (() -> Unit)?, backLabel: String? = null, trailing: @Composable RowScope.() -> Unit = {}) {
    val ios = LocalIos.current
    Box(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 12.dp, vertical = 4.dp)) {
        if (onBack != null) Row(
            Modifier.align(Alignment.CenterStart).height(44.dp).liquidGlass().clickable(onClick = onBack).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IosIcon(IosGlyph.Back, ios.label, 22.dp, weight = 1.15f)
            if (backLabel != null) { Spacer(Modifier.width(2.dp)); Text(backLabel, style = MaterialTheme.typography.bodyLarge, color = ios.label, maxLines = 1) }
        }
        if (title != null) Text(
            title, Modifier.align(Alignment.Center).padding(horizontal = 96.dp), style = MaterialTheme.typography.titleMedium,
            color = ios.label, maxLines = 1, overflow = TextOverflow.Ellipsis
        )
        Row(Modifier.align(Alignment.CenterEnd).fillMaxHeight(), verticalAlignment = Alignment.CenterVertically, content = trailing)
    }
}

@Composable
fun IosLargeTitle(text: String) {
    Text(text, style = MaterialTheme.typography.displayLarge, color = LocalIos.current.label, modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 2.dp, bottom = 8.dp))
}

@Composable
fun IosTextButton(text: String, onClick: () -> Unit, bold: Boolean = false, color: Color = Color.Unspecified, enabled: Boolean = true) {
    val ios = LocalIos.current
    Box(
        Modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(8.dp)).alpha(if (enabled) 1f else 0.4f)
            .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, style = MaterialTheme.typography.bodyLarge, fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal, color = if (color != Color.Unspecified) color else ios.blue, maxLines = 1)
    }
}

@Composable
fun IosPillButton(text: String, onClick: () -> Unit, filled: Boolean = false, modifier: Modifier = Modifier) {
    val ios = LocalIos.current
    val src = remember { MutableInteractionSource() }
    Box(
        modifier.height(30.dp).pressScale(src, 0.94f).clip(CircleShape).background(if (filled) ios.blue else ios.blue.copy(alpha = 0.15f))
            .clickable(interactionSource = src, indication = null, onClick = onClick).padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, style = MaterialTheme.typography.titleSmall, color = if (filled) Color.White else ios.blue, maxLines = 1)
    }
}

@Composable
fun IosButton(text: String, onClick: () -> Unit, filled: Boolean = true, modifier: Modifier = Modifier) {
    val ios = LocalIos.current
    val src = remember { MutableInteractionSource() }
    Box(
        modifier.fillMaxWidth().height(50.dp).pressScale(src, 0.97f).clip(RoundedCornerShape(25.dp))
            .background(if (filled) ios.blue else ios.fill3).clickable(interactionSource = src, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium, color = if (filled) Color.White else ios.blue, maxLines = 1)
    }
}

fun Modifier.iosHairlineTop(color: Color): Modifier = this.drawBehind { drawLine(color, Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = 1f) }

fun Modifier.iosHairlineBottom(color: Color): Modifier = this.drawBehind { drawLine(color, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1f) }
