package com.nova.browser

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.FloatState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle

private const val SWEEP_MS = 1400L

/** Sweep position 0..1, or -1 when static. Read it in the draw phase only. */
@Composable
private fun rememberShimmerPhase(): FloatState {
    val phase = remember { mutableFloatStateOf(-1f) }
    val owner = LocalLifecycleOwner.current
    val animate = NovaMotion.infiniteAllowed
    LaunchedEffect(animate, owner) {
        if (!animate) {
            phase.floatValue = -1f
            return@LaunchedEffect
        }
        val frame: (Long) -> Unit = { phase.floatValue = (it % SWEEP_MS) / SWEEP_MS.toFloat() }
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) withFrameMillis(frame)
        }
    }
    return phase
}

/** Highlights only the pixels already drawn by the content (placeholders). */
@Composable
private fun Modifier.shimmer(phase: FloatState): Modifier {
    val ios = LocalIos.current
    val highlight = if (ios.dark) ios.label.copy(alpha = 0.12f) else ios.cell.copy(alpha = 0.7f)
    return graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithCache {
            val band = size.width * 0.4f
            val brush = Brush.horizontalGradient(
                listOf(highlight.copy(alpha = 0f), highlight, highlight.copy(alpha = 0f)), startX = 0f, endX = band
            )
            val bandSize = Size(band, size.height)
            onDrawWithContent {
                drawContent()
                val p = phase.floatValue
                if (p >= 0f) {
                    val t = if (layoutDirection == LayoutDirection.Rtl) 1f - p else p
                    translate(left = (size.width + band) * t - band) {
                        drawRect(brush, size = bandSize, blendMode = BlendMode.SrcAtop)
                    }
                }
            }
        }
}

/** Placeholder for a ListRow group: same paddings, 29dp tile, two text lines, divider and corners. */
@Composable
fun SkeletonList(modifier: Modifier = Modifier, rows: Int = 8, caption: String? = null) {
    val ios = LocalIos.current
    val phase = rememberShimmerPhase()
    val density = LocalDensity.current
    val titleLine = with(density) { 22.sp.toDp() }
    val subLine = with(density) { 18.sp.toDp() }
    val round = IosShape.pill
    Column(modifier.fillMaxWidth()) {
        if (caption != null) Text(
            caption, Modifier.fillMaxWidth().padding(bottom = 8.dp), textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium, color = ios.label2
        )
        repeat(rows) { i ->
            val pos = groupShape(i, rows)
            val top = if (pos.first) 24.dp else 0.dp
            val bottom = if (pos.last) 24.dp else 0.dp
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(top, top, bottom, bottom)).background(ios.cell)) {
                Row(
                    Modifier.fillMaxWidth().shimmer(phase).heightIn(min = 44.dp).padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(29.dp).clip(RoundedCornerShape(7.dp)).background(ios.fill3))
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Box(Modifier.height(titleLine), contentAlignment = Alignment.CenterStart) {
                            Box(Modifier.fillMaxWidth(if (i % 2 == 0) 0.62f else 0.48f).height(12.dp).clip(round).background(ios.fill3))
                        }
                        Box(Modifier.height(subLine), contentAlignment = Alignment.CenterStart) {
                            Box(Modifier.fillMaxWidth(if (i % 2 == 0) 0.4f else 0.32f).height(10.dp).clip(round).background(ios.fill3))
                        }
                    }
                }
                if (!pos.last) IosDivider(61.dp)
            }
        }
    }
}
