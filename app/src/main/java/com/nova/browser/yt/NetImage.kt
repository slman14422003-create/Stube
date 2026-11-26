package com.nova.browser

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize

/** Remote image decoded for its on-screen size (see ImageLoader). [modifier] must define the size. */
@Composable
fun NetImage(url: String, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Crop) {
    var size by remember { mutableStateOf(IntSize.Zero) }
    var bmp by remember(url) { mutableStateOf(ImageLoader.cached(url)) }
    LaunchedEffect(url, size) {
        if (size.width > 0 && size.height > 0) ImageLoader.load(url, size.width, size.height)?.let { bmp = it }
    }
    Box(modifier.onSizeChanged { size = it }) {
        val b = bmp
        if (b != null) Image(remember(b) { b.asImageBitmap() }, null, Modifier.fillMaxSize(), contentScale = contentScale)
        else Box(Modifier.fillMaxSize().background(Color(0x22888888)))
    }
}
