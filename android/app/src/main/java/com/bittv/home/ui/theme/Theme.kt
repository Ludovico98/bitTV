package com.bittv.home.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

// Colour tokens, the same values as the prototype's :root variables.
val Bg = Color(0xFF06080D)
val Ink = Color(0xFFF4F6FA)
val Ink2 = Color(0x9EF4F6FA)   // 62 %
val Ink3 = Color(0x99F4F6FA)   // 60 %: the prototype's 38 % failed 10-foot contrast (review)
val Glass = Color(0x0EFFFFFF)  // 5.5 %
val GlassLine = Color(0x17FFFFFF)

/** Layout on a 960x540 dp canvas (1920x1080 px at xhdpi): Apple tvOS safe area 80/60 px. */
object Dims {
    val SafeX = 40.dp
    val SafeY = 30.dp
    val Gap = 20.dp
}

@Composable
fun BitTvTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(background = Bg, surface = Bg, onSurface = Ink, onBackground = Ink),
        content = content,
    )
}
