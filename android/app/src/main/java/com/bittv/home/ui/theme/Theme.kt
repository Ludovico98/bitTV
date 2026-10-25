package com.bittv.home.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Typography
import androidx.tv.material3.darkColorScheme
import com.bittv.home.R

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

@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private fun variable(res: Int, weight: Int) =
    Font(res, FontWeight(weight), variationSettings = FontVariation.Settings(FontVariation.weight(weight)))

val Inter = FontFamily(variable(R.font.inter, 400), variable(R.font.inter, 500), variable(R.font.inter, 600),
    variable(R.font.inter, 700), variable(R.font.inter, 800))
val Bebas = FontFamily(Font(R.font.bebas_neue))
val Playfair = FontFamily(variable(R.font.playfair_display, 700))
val Oswald = FontFamily(variable(R.font.oswald, 600))
val OpenSans = FontFamily(variable(R.font.open_sans, 500))

/** Type scale in sp = prototype px / 2. Nothing is smaller than 11.5 sp (23 px, the tvOS minimum). */
object Type {
    val Caption = TextStyle(fontFamily = Inter, fontSize = 11.5.sp, fontWeight = FontWeight.Medium, color = Ink2)
    val Label = TextStyle(fontFamily = Inter, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = Ink)
    val Body = TextStyle(fontFamily = Inter, fontSize = 14.5.sp, fontWeight = FontWeight.Normal, color = Ink, lineHeight = 19.sp)
    val Title = TextStyle(fontFamily = Inter, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, color = Ink)
    val Clock = TextStyle(fontFamily = Inter, fontSize = 22.sp, fontWeight = FontWeight.Medium, color = Ink)
    val Kicker = TextStyle(fontFamily = Inter, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = Ink3, letterSpacing = 1.4.sp)
}

@Composable
fun BitTvTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(background = Bg, surface = Bg, onSurface = Ink, onBackground = Ink),
        typography = Typography(bodyMedium = Type.Body, bodyLarge = Type.Body, labelMedium = Type.Label),
        content = content,
    )
}
