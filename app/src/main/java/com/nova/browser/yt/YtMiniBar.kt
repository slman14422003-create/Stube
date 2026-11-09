package com.nova.browser

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
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
import androidx.compose.material3.CircularProgressIndicator
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

val YT_MINI_BAR_HEIGHT = 58.dp

private val YtRed = Color(0xFFFF0000)

@Composable
fun YtMiniBar(modifier: Modifier = Modifier, bottomInset: androidx.compose.ui.unit.Dp = 0.dp, onOpen: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val dx = remember { Animatable(0f) }
    val dy = remember { Animatable(0f) }
    val density = LocalDensity.current
    val dismissPx = with(density) { 96.dp.toPx() }
    val openPx = with(density) { 40.dp.toPx() }
    val frac = if (YtAudio.durMs > 0) (YtAudio.posMs.toFloat() / YtAudio.durMs).coerceIn(0f, 1f) else 0f

    Column(
        modifier.fillMaxWidth().height(YT_MINI_BAR_HEIGHT + bottomInset)
            .graphicsLayer {
                translationX = dx.value
                translationY = dy.value
                alpha = (1f - abs(dx.value) / (dismissPx * 2f)).coerceIn(0f, 1f)
            }
            .background(cs.surfaceContainerHigh)
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
                                    dx.animateTo(if (dx.value > 0) size.width.toFloat() else -size.width.toFloat(), tween(160))
                                    YtAudio.close()
                                    dx.snapTo(0f)
                                }
                                axis == 2 && dy.value < -openPx -> {
                                    dy.snapTo(0f)
                                    onOpen()
                                }
                                else -> {
                                    launch { dx.animateTo(0f, tween(180)) }
                                    launch { dy.animateTo(0f, tween(180)) }
                                }
                            }
                        }
                    }
                }
            }
            .clickable { onOpen() }
    ) {
        Box(Modifier.fillMaxWidth().height(2.dp).background(cs.onSurface.copy(alpha = 0.18f))) {
            Box(Modifier.fillMaxWidth(frac).fillMaxHeight().background(YtRed))
        }
        Row(Modifier.fillMaxWidth().weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.fillMaxHeight().aspectRatio(16f / 9f).background(Color.Black)) {
                NetImage("https://i.ytimg.com/vi/${YtAudio.videoId}/mqdefault.jpg", Modifier.fillMaxHeight().aspectRatio(16f / 9f))
            }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(
                    YtAudio.title.ifBlank { "YouTube" }, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = cs.onSurface
                )
                if (YtAudio.author.isNotBlank()) Text(
                    YtAudio.author, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant
                )
            }
            MiniBtn(Modifier.clickable(enabled = !YtAudio.loading) { YtAudio.control(if (YtAudio.playing) "pause" else "play") }) {
                if (YtAudio.loading) CircularProgressIndicator(Modifier.size(20.dp), color = cs.onSurface, strokeWidth = 2.dp)
                else MiniIcon(if (YtAudio.playing) MiniGlyph.Pause else MiniGlyph.Play, cs.onSurface)
            }
            MiniBtn(Modifier.clickable { YtAudio.control("next") }) { MiniIcon(MiniGlyph.Next, cs.onSurface) }
            MiniBtn(Modifier.clickable { YtAudio.close() }) { MiniIcon(MiniGlyph.Close, cs.onSurface) }
        }
        androidx.compose.foundation.layout.Spacer(Modifier.height(bottomInset))
    }
}

@Composable
private fun MiniBtn(modifier: Modifier, content: @Composable () -> Unit) {
    Box(Modifier.size(44.dp).clip(CircleShape).then(modifier), contentAlignment = Alignment.Center) { content() }
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
