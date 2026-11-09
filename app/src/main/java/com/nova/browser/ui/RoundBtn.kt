package com.nova.browser

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp

@Composable
fun RoundBtn(onClick: () -> Unit, enabled: Boolean = true, content: @Composable () -> Unit) {
    val ios = LocalIos.current
    val src = remember { MutableInteractionSource() }
    Box(
        Modifier.size(44.dp).pressScale(src, 0.88f).alpha(if (enabled) 1f else 0.4f)
            .clickable(interactionSource = src, indication = null, enabled = enabled, role = androidx.compose.ui.semantics.Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) { CompositionLocalProvider(LocalContentColor provides ios.blue) { content() } }
}
