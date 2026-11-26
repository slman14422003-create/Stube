package com.nova.browser

import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.draw.drawBehind
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.abs

private val PILL_HEIGHT = 58.dp
private val PILL_SIDE = 12.dp
private val PILL_TOP = 6.dp
private val PILL_BOTTOM = 8.dp

/** Total space the floating pill occupies (capsule + vertical margins), without the system inset. */
val YT_MINI_BAR_HEIGHT = PILL_TOP + PILL_HEIGHT + PILL_BOTTOM

private val YtRed = Color(0xFFFF0000)

private fun thumbUrl(id: String) = "https://i.ytimg.com/vi/$id/mqdefault.jpg"

@Composable
fun YtMiniBar(modifier: Modifier = Modifier, bottomInset: androidx.compose.ui.unit.Dp = 0.dp, onOpen: () -> Unit) {
    val ios = LocalIos.current
    val haptics = rememberHaptics()
    val shadow = if (ios.dark) Color(0x80000000) else Color(0x33000000)
    val scope = rememberCoroutineScope()
    val dx = remember { Animatable(0f) }
    val dy = remember { Animatable(0f) }
    val density = LocalDensity.current
    val dismissPx = with(density) { 96.dp.toPx() }
    val openPx = with(density) { 40.dp.toPx() }

    // Preload the next track's thumbnail once the current one has resolved
    LaunchedEffect(YtAudio.videoId, YtAudio.loading, Adaptive.level) {
        if (YtAudio.loading || ImageLoader.prefetchCount(1) == 0) return@LaunchedEffect
        val next = YtAudio.nextVideoId ?: return@LaunchedEffect
        val w = with(density) { (42.dp * 16f / 9f).roundToPx() }
        val h = with(density) { 42.dp.roundToPx() }
        ImageLoader.prefetch(listOf(thumbUrl(next)), w, h)
    }
    // A new session must not inherit the off-screen position left by a swipe dismiss
    LaunchedEffect(YtAudio.active) {
        if (YtAudio.active) { dx.snapTo(0f); dy.snapTo(0f) }
    }
    val open = { if (YtAudio.active) { haptics.confirm(); onOpen() } }

    Column(
        modifier.fillMaxWidth().height(YT_MINI_BAR_HEIGHT + bottomInset)
            .graphicsLayer {
                translationX = dx.value
                translationY = dy.value
                alpha = (1f - abs(dx.value) / (dismissPx * 2f)).coerceIn(0f, 1f)
            }
            .padding(start = PILL_SIDE, end = PILL_SIDE, top = PILL_TOP, bottom = PILL_BOTTOM + bottomInset)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var axis = 0
                    val first = awaitTouchSlopOrCancellation(down.id) { change, over ->
                        axis = if (abs(over.x) > abs(over.y)) 1 else 2
                        change.consume()
                    }
                    if (first != null) {
                        drag(first.id) { c ->
                            val p = c.positionChange()
                            c.consume()
                            scope.launch {
                                if (axis == 1) dx.snapTo(dx.value + p.x)
                                else dy.snapTo((dy.value + p.y).coerceAtMost(0f))
                            }
                        }
                        scope.launch {
                            when {
                                axis == 1 && abs(dx.value) > dismissPx -> {
                                    dx.animateTo(if (dx.value > 0) size.width + PILL_SIDE.toPx() else -(size.width + PILL_SIDE.toPx()), tween(Adaptive.ms(160)))
                                    haptics.confirm()
                                    YtAudio.close()
                                }
                                axis == 2 && dy.value < -openPx -> {
                                    dy.snapTo(0f)
                                    open()
                                }
                                else -> {
                                    launch { dx.animateTo(0f, tween(Adaptive.ms(180))) }
                                    launch { dy.animateTo(0f, tween(Adaptive.ms(180))) }
                                }
                            }
                        }
                    }
                }
            }
            .shadow(8.dp, IosShape.pill, ambientColor = shadow, spotColor = shadow)
            .clip(IosShape.pill)
            .background(if (ios.dark) ios.gray5 else ios.cell)
            .border(hairlineDp(), ios.separator, IosShape.pill)
            .clickable { open() }
    ) {
        // Position is read in the draw phase: the once-a-second tick redraws this line without recomposing the pill
        Box(Modifier.fillMaxWidth().padding(horizontal = 30.dp).height(2.dp).background(ios.fill3).drawBehind {
            val frac = if (YtAudio.durMs > 0) (YtAudio.posMs.toFloat() / YtAudio.durMs).coerceIn(0f, 1f) else 0f
            val w = size.width * frac
            val x = if (layoutDirection == LayoutDirection.Rtl) size.width - w else 0f
            drawRect(YtRed, Offset(x, 0f), androidx.compose.ui.geometry.Size(w, size.height))
        })
        Row(Modifier.fillMaxWidth().weight(1f).padding(start = 8.dp, end = 8.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.height(42.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(16.dp)).background(Color.Black)) {
                NetImage(thumbUrl(YtAudio.videoId), Modifier.fillMaxSize())
            }
            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                Text(
                    YtAudio.title.ifBlank { "YouTube" }, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = ios.label
                )
                if (YtAudio.author.isNotBlank()) Text(
                    YtAudio.author, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall, color = ios.label2
                )
            }
            MiniBtn(enabled = !YtAudio.loading, onClick = {
                haptics.toggle(!YtAudio.playing)
                YtAudio.control(if (YtAudio.playing) "pause" else "play")
            }) {
                if (YtAudio.loading) NovaSpinner(size = 20.dp, color = ios.label)
                else MiniIcon(if (YtAudio.playing) MiniGlyph.Pause else MiniGlyph.Play, ios.label)
            }
            MiniBtn(onClick = { haptics.tick(); YtAudio.control("next") }) { MiniIcon(MiniGlyph.Next, ios.label) }
            MiniBtn(onClick = { haptics.tick(); YtAudio.close() }) { MiniIcon(MiniGlyph.Close, ios.label) }
        }
    }
}

@Composable
private fun MiniBtn(enabled: Boolean = true, onClick: () -> Unit, content: @Composable () -> Unit) {
    val src = remember { MutableInteractionSource() }
    Box(
        Modifier.size(44.dp).pressScale(src, 0.88f).clip(CircleShape)
            .clickable(interactionSource = src, indication = null, enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) { content() }
}

private enum class MiniGlyph { Play, Pause, Next, Close }

@Composable
private fun MiniIcon(kind: MiniGlyph, tint: Color) {
    Canvas(Modifier.size(24.dp)) {
        val s = size.minDimension / 24f
        when (kind) {
            MiniGlyph.Play -> drawPath(Path().apply {
                moveTo(7f * s, 4.5f * s); lineTo(19f * s, 12f * s); lineTo(7f * s, 19.5f * s); close()
            }, tint)
            MiniGlyph.Pause -> {
                drawRect(tint, Offset(6f * s, 4.5f * s), androidx.compose.ui.geometry.Size(4f * s, 15f * s))
                drawRect(tint, Offset(14f * s, 4.5f * s), androidx.compose.ui.geometry.Size(4f * s, 15f * s))
            }
            MiniGlyph.Next -> {
                drawPath(Path().apply {
                    moveTo(5f * s, 5f * s); lineTo(15f * s, 12f * s); lineTo(5f * s, 19f * s); close()
                }, tint)
                drawRect(tint, Offset(16f * s, 5f * s), androidx.compose.ui.geometry.Size(3f * s, 14f * s))
            }
            MiniGlyph.Close -> {
                drawLine(tint, Offset(6f * s, 6f * s), Offset(18f * s, 18f * s), 2.2f * s, StrokeCap.Round)
                drawLine(tint, Offset(18f * s, 6f * s), Offset(6f * s, 18f * s), 2.2f * s, StrokeCap.Round)
            }
        }
    }
}
