package com.bittv.home.ui.details

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.bittv.home.model.AppEntry
import com.bittv.home.ui.components.AppIcon
import com.bittv.home.ui.components.PillButton
import com.bittv.home.ui.home.Glyph
import com.bittv.home.ui.home.GlyphIcon
import com.bittv.home.ui.theme.Type

/** An app that isn't installed: just a heading and a plain "Install <App>" button (no store artwork). */
@Composable
fun NotInstalledScreen(app: AppEntry, onInstall: () -> Unit, onBack: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF1E2A3A), Color(0xFF0B0F18)))), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("${app.label} isn’t installed", style = Type.Title.copy(fontSize = 26.sp))
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                PillButton("Install ${app.label}", onInstall, Modifier.focusRequester(focus))
                PillButton("Back", onBack, leading = { c -> GlyphIcon(Glyph.BACK, c, Modifier.size(15.dp)) })
            }
        }
    }
}

/** Stand-in for handing over to an app (plan 2 launches the real app with its intent). */
@Composable
fun OpeningScreen(app: AppEntry?, label: String, line: String, onBack: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Box(Modifier.fillMaxSize().background(Color(0xFF0B0F18)), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            app?.let { AppIcon(it, 183.dp, 110.dp, radius = 24.dp) }
            Spacer(Modifier.height(20.dp))
            Text("Opening $label", style = Type.Title.copy(fontSize = 26.sp))
            Spacer(Modifier.height(8.dp))
            Text(line, style = Type.Body)
            Spacer(Modifier.height(22.dp))
            PillButton("Back to BitTV", onBack, Modifier.focusRequester(focus), leading = { c -> GlyphIcon(Glyph.BACK, c, Modifier.size(15.dp)) })
        }
    }
}
