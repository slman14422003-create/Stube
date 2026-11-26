package com.nova.browser

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedScroll.NestedScrollConnection
import androidx.compose.ui.input.nestedScroll.NestedScrollSource
import androidx.compose.ui.layout.layout
import kotlin.math.roundToInt

/**
 * عنوان كبير ينكمش عند التمرير بأسلوب iOS: يطوي العنوان الكبير ويظهر عنوان صغير في الشريط مع خط فاصل رفيع.
 * الاستخدام: ct = rememberCollapsingTitle() ← Modifier.nestedScroll(ct.connection) على حاوية الشاشة
 * ← PanelTopBar(..., collapse = ct). يطوي العنوان أولاً ثم تتحرك القائمة، ويعود عند السحب للأسفل من أعلى القائمة.
 */
@Stable
class CollapsingTitle {
    var offset by mutableFloatStateOf(0f)
    var maxPx = 0f
    val progress: Float get() = if (maxPx <= 0f) 0f else (offset / maxPx).coerceIn(0f, 1f)

    val connection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            val dy = available.y
            if (dy < 0f && maxPx > 0f && offset < maxPx) {
                val next = (offset - dy).coerceAtMost(maxPx)
                val used = next - offset
                offset = next
                return Offset(0f, -used)
            }
            return Offset.Zero
        }

        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            val dy = available.y
            if (dy > 0f && offset > 0f) {
                val next = (offset - dy).coerceAtLeast(0f)
                val used = offset - next
                offset = next
                return Offset(0f, used)
            }
            return Offset.Zero
        }
    }
}

@Composable
fun rememberCollapsingTitle(): CollapsingTitle = remember { CollapsingTitle() }

internal fun Modifier.collapsing(s: CollapsingTitle): Modifier = this
    .clipToBounds()
    .layout { m, c ->
        val p = m.measure(c)
        s.maxPx = p.height.toFloat()
        val h = (p.height - s.offset).coerceIn(0f, p.height.toFloat()).roundToInt()
        layout(p.width, h) { p.placeRelative(0, h - p.height) }
    }
    .graphicsLayer { alpha = 1f - s.progress }
