package com.nova.browser

import android.graphics.Bitmap
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

object SplashGate {
    var revealed by mutableStateOf(false)
        private set

    fun reveal() { revealed = true }
}

@Composable
fun SplashMark(icon: Bitmap?, name: String, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val animated = Adaptive.ms(100) > 0
    val pop by animateFloatAsState(
        if (shown) 1f else 0f,
        if (animated) spring(dampingRatio = 0.55f, stiffness = 260f) else snap(),
        label = "markPop"
    )
    val iconAlpha by animateFloatAsState(
        if (shown) 1f else 0f,
        tween(Adaptive.ms(240), easing = NovaMotion.emphasized),
        label = "markAlpha"
    )
    val labelAlpha by animateFloatAsState(
        if (shown) 1f else 0f,
        tween(Adaptive.ms(300), delayMillis = Adaptive.ms(160), easing = NovaMotion.emphasized),
        label = "markLabel"
    )
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        if (icon != null) {
            Image(
                bitmap = icon.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier
                    .size(84.dp)
                    .graphicsLayer {
                        val s = 0.62f + 0.38f * pop
                        scaleX = s; scaleY = s
                        alpha = iconAlpha.coerceIn(0f, 1f)
                    }
                    .clip(RoundedCornerShape(20.dp))
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            name,
            style = MaterialTheme.typography.titleMedium,
            color = cs.onBackground,
            modifier = Modifier.graphicsLayer {
                alpha = labelAlpha.coerceIn(0f, 1f)
                translationY = (1f - labelAlpha.coerceIn(0f, 1f)) * 8.dp.toPx()
            }
        )
    }
}
