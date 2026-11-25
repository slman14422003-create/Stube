package com.nova.browser

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * صف قابل للسحب للحذف بأسلوب iOS: خلفية حمراء ورمز سلة، واهتزاز تأكيد عند الإتمام.
 * يقصّ بنفس شكل المجموعة (أول/آخر صف بزوايا دائرية) كي لا تظهر زوايا حادة خلف الصف.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeDeleteRow(shape: IosPos, onDelete: () -> Unit, content: @Composable () -> Unit) {
    val ios = LocalIos.current
    val haptics = rememberHaptics()
    val r = 24.dp
    val clipShape = RoundedCornerShape(
        topStart = if (shape.first) r else 0.dp, topEnd = if (shape.first) r else 0.dp,
        bottomStart = if (shape.last) r else 0.dp, bottomEnd = if (shape.last) r else 0.dp
    )
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { v ->
            if (v == SwipeToDismissBoxValue.Settled) false
            else { haptics.confirm(); onDelete(); true }
        }
    )
    Box(Modifier.clip(clipShape)) {
        SwipeToDismissBox(
            state = state,
            backgroundContent = {
                Box(
                    Modifier.fillMaxSize().background(ios.red).padding(horizontal = 22.dp),
                    contentAlignment = if (state.dismissDirection == SwipeToDismissBoxValue.StartToEnd) Alignment.CenterStart else Alignment.CenterEnd
                ) { IosIcon(IosGlyph.Trash, Color.White, 22.dp) }
            }
        ) { content() }
    }
}
