package com.bittv.home.ui.assistant

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.bittv.home.assistant.Answer
import com.bittv.home.assistant.AssistantState
import com.bittv.home.assistant.FitMode
import com.bittv.home.assistant.fitMode
import com.bittv.home.ui.home.drawWeatherSymbol
import com.bittv.home.ui.theme.Ink
import com.bittv.home.ui.theme.Ink2
import com.bittv.home.ui.theme.Ink3
import com.bittv.home.ui.theme.Type
import androidx.compose.foundation.Canvas

/**
 * The Assistant inside the active section's fixed box. Selecting it (OK) dismisses at once; otherwise it goes after
 * 30 s, counted only once the answer has been read out.
 */
@Composable
fun AssistantArea(state: AssistantState, onDismiss: () -> Unit, modifier: Modifier = Modifier, showForMs: Int = 30_000) {
    var focused by remember { mutableStateOf(false) }
    Column(
        modifier.testTag("assist").fillMaxSize()
            .onFocusChanged { focused = it.isFocused }
            .clickable(remember { MutableInteractionSource() }, null, onClick = onDismiss)
            .then(if (focused) Modifier.border(1.5.dp, Color.White, RoundedCornerShape(17.dp)).background(Color(0x0FFFFFFF)) else Modifier)
            .padding(horizontal = 13.dp, vertical = 11.dp),
    ) {
        val listening = state is AssistantState.Listening
        Row(verticalAlignment = Alignment.CenterVertically) {
            GoogleDots(bouncing = listening)
            Spacer(Modifier.width(7.dp))
            when (state) {
                is AssistantState.Showing -> Quote(state.query)
                is AssistantState.Thinking -> Quote(state.query)
                else -> Text("Google Assistant", style = Type.Caption.copy(color = Ink2, fontWeight = FontWeight.SemiBold))
            }
        }
        when (state) {
            AssistantState.Idle -> {}
            is AssistantState.Listening -> {
                Spacer(Modifier.height(22.dp))
                Text(state.partial.ifEmpty { "Hi, how can I help?" }, style = Type.Title.copy(fontSize = 19.sp))
                Spacer(Modifier.height(6.dp))
                Text("Listening…", style = Type.Caption)
            }
            is AssistantState.Thinking -> { Spacer(Modifier.height(10.dp)); Text("One moment…", style = Type.Caption) }
            is AssistantState.Showing -> {
                Box(Modifier.weight(1f).fillMaxWidth()) { AnswerBody(state.answer) }
                Foot(state.counting, showForMs)
            }
        }
    }
}

@Composable
private fun Quote(q: String) = Text("“$q”", style = Type.Caption.copy(color = Ink, fontStyle = FontStyle.Italic), maxLines = 1, overflow = TextOverflow.Ellipsis)

@Composable
private fun AnswerBody(a: Answer) {
    when (a) {
        is Answer.Handoff -> {}   // never shown: handed to Google Assistant
        is Answer.Weather -> Column(Modifier.padding(top = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                WeatherScene(a.kind, Modifier.size(85.dp, 75.dp))
                Spacer(Modifier.width(11.dp))
                Column {
                    Text("${a.tempC}°", style = Type.Title.copy(fontSize = 38.sp, fontWeight = FontWeight.SemiBold, lineHeight = 40.sp))
                    Text(a.label, style = Type.Caption.copy(color = Ink2)); Text(a.place, style = Type.Caption.copy(color = Ink2))
                }
            }
            Spacer(Modifier.height(7.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                a.days.forEach { d ->
                    Column(Modifier.weight(1f).background(Color(0x0FFFFFFF), RoundedCornerShape(8.dp)).padding(vertical = 5.dp),
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(d.label, style = Type.Caption.copy(color = Ink))
                        Canvas(Modifier.size(16.dp)) { drawWeatherSymbol(d.kind) }
                        Text("${d.hi}° ${d.lo}°", style = Type.Caption.copy(color = Ink))
                    }
                }
            }
        }
        is Answer.Clock -> Column(Modifier.padding(top = 14.dp)) {
            Text(a.time, style = Type.Title.copy(fontSize = 56.sp, fontWeight = FontWeight.SemiBold))
            Text(a.date, style = Type.Caption)
        }
        is Answer.Picture -> PictureAnswer(a)
        is Answer.Text -> Column(Modifier.padding(top = 10.dp)) {
            Text(a.title, style = Type.Body.copy(fontWeight = FontWeight.Bold))
            Spacer(Modifier.height(3.dp))
            Text(a.body, style = Type.Caption.copy(color = Color(0xD9FFFFFF)), maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** The whole picture over a blurred copy of itself; cropped only when its shape is extreme (see [fitMode]). */
@Composable
private fun PictureAnswer(a: Answer.Picture) {
    var box by remember { mutableStateOf(0 to 0) }
    var img by remember { mutableStateOf(0 to 0) }
    var url by remember(a.imageUrl) { mutableStateOf(a.imageUrl) }
    val mode = fitMode(img.first, img.second, box.first, box.second)
    Box(Modifier.padding(top = 6.dp, bottom = 7.dp).fillMaxSize().clip(RoundedCornerShape(9.dp)).background(Color(0xFF0B0E14))
        .onSizeChanged { box = it.width to it.height }) {
        AsyncImage(url, null, Modifier.fillMaxSize().blur(13.dp).graphicsLayer { alpha = 0.5f; scaleX = 1.25f; scaleY = 1.25f },
            contentScale = ContentScale.Crop)
        AsyncImage(url, a.title, Modifier.fillMaxSize(),
            contentScale = if (mode == FitMode.CROP) ContentScale.Crop else ContentScale.Fit,
            onSuccess = { s -> img = s.result.image.width to s.result.image.height },
            onError = { if (a.fallbackUrl != null && url != a.fallbackUrl) url = a.fallbackUrl })
    }
}

@Composable
private fun ColumnScope.Foot(counting: Boolean, showForMs: Int) {
    val line = remember { Animatable(1f) }
    // the line animates even with Animations off: it tells you how long the answer stays
    LaunchedEffect(counting) { if (counting) line.animateTo(0f, tween(showForMs, easing = LinearEasing)) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (!counting) { SpeakingBars(); Spacer(Modifier.width(6.dp)) }
        Text(if (counting) "OK to close" else "Speaking…", style = Type.Caption.copy(color = Ink3))
        Spacer(Modifier.width(9.dp))
        Box(Modifier.weight(1f).height(2.dp).background(Color(0x1FFFFFFF), RoundedCornerShape(1.dp))) {
            Box(Modifier.fillMaxSize().graphicsLayer { scaleX = line.value; transformOrigin = TransformOrigin(0f, 0.5f) }
                .background(Color(0x99FFFFFF), RoundedCornerShape(1.dp)))
        }
    }
}

@Composable
private fun SpeakingBars() {
    val tr = rememberInfiniteTransition(label = "eq")
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom, modifier = Modifier.height(11.dp)) {
        listOf(0, 200, 400, 100).forEach { d ->
            val s by tr.animateFloat(0.23f, 1f, infiniteRepeatable(tween(700, delayMillis = d), RepeatMode.Reverse), label = "bar")
            Box(Modifier.width(2.dp).height(11.dp).graphicsLayer { scaleY = s; transformOrigin = TransformOrigin(0.5f, 1f) }
                .background(Color(0xFF8FB8FF), RoundedCornerShape(1.dp)))
        }
    }
}

@Composable
private fun GoogleDots(bouncing: Boolean) {
    val tr = rememberInfiniteTransition(label = "dots")
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        listOf(0xFF4285F4, 0xFFEA4335, 0xFFFBBC04, 0xFF34A853).forEachIndexed { i, c ->
            val y = if (bouncing) tr.animateFloat(0f, -5f, infiniteRepeatable(tween(450, delayMillis = i * 120), RepeatMode.Reverse), label = "d$i").value else 0f
            Box(Modifier.graphicsLayer { translationY = y * density }.size(5.5.dp).background(Color(c), CircleShape))
        }
    }
}
