package com.bittv.home.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.bittv.home.data.PrefsRepo
import com.bittv.home.model.AppEntry
import com.bittv.home.model.Prefs
import com.bittv.home.model.Svc
import com.bittv.home.ui.components.AppIcon
import com.bittv.home.ui.components.OptionRow
import com.bittv.home.ui.theme.GlassLine
import com.bittv.home.ui.theme.Type
import kotlinx.coroutines.launch

/** Settings: subscriptions, the Assistant's voice, and how BitTV looks and moves. Every change is saved at once. */
@Composable
fun SettingsScreen(
    prefs: Prefs, repo: PrefsRepo, apps: List<AppEntry>, voices: List<String>,
    onSample: () -> Unit, onReplayBoot: () -> Unit, onBack: () -> Unit, onTvSettings: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val first = remember { FocusRequester() }
    val remoteButton = remember { FocusRequester() }
    var guide by remember { mutableStateOf(false) }
    var returnToGuideButton by remember { mutableStateOf(false) }
    LaunchedEffect(guide) {
        when {
            guide -> {}
            returnToGuideButton -> { returnToGuideButton = false; remoteButton.requestFocus() }
            else -> first.requestFocus()
        }
    }
    Box(Modifier.fillMaxSize().background(Color(0xD1030408)), contentAlignment = Alignment.Center) {
        if (guide) { RemoteGuide(onClose = { guide = false; returnToGuideButton = true }); return@Box }
        Column(
            Modifier.width(800.dp).background(Color(0xFF121722), RoundedCornerShape(18.dp)).border(1.dp, GlassLine, RoundedCornerShape(18.dp))
                .padding(horizontal = 16.dp, vertical = 22.dp),
        ) {
            Text("Settings", style = Type.Title.copy(fontSize = 22.sp))
            Text("Suggested, the banner and Search show titles from the apps installed on this TV.", style = Type.Caption)
            Section("Google Assistant")
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                OptionRow("Voice", Modifier.width(509.dp).testTag("opt:voice").focusRequester(first), value = prefs.voice ?: "Device default", onClick = {
                    if (voices.isNotEmpty()) {
                        val next = voices[(voices.indexOf(prefs.voice) + 1) % voices.size]
                        scope.launch { repo.setVoice(next) }
                    }
                    onSample()
                })
                OptionRow("Hear a sample", Modifier.width(252.dp), value = "Play", onClick = onSample)
            }
            Section("Experience")
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.padding(bottom = 7.dp)) {
                OptionRow("Animations", Modifier.width(251.dp).testTag("opt:animations"), checked = prefs.animations,
                    onClick = { scope.launch { repo.setAnimations(!prefs.animations) } })
                OptionRow("Overscan guides", Modifier.width(251.dp).testTag("opt:guides"), checked = prefs.overscanGuides,
                    onClick = { scope.launch { repo.setOverscanGuides(!prefs.overscanGuides) } })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                OptionRow("Replay boot animation", Modifier.width(251.dp), value = "Play", onClick = onReplayBoot)
                OptionRow("Reset Watch Later", Modifier.width(251.dp), value = "Reset", onClick = { scope.launch { repo.clearWatchLater() } })
                OptionRow("Remote shortcuts", Modifier.width(251.dp).testTag("opt:remote").focusRequester(remoteButton), value = "Show",
                    onClick = { guide = true })
            }
            Section("TV")
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                // the stock home is off, so the TV's own settings (picture, sound, network, inputs) open from here too
                OptionRow("TV settings", Modifier.width(251.dp).testTag("opt:tvsettings"), value = "Open", onClick = onTvSettings)
            }
        }
    }
}

@Composable
private fun Section(text: String) {
    Spacer(Modifier.height(14.dp))
    Text(text.uppercase(), style = Type.Kicker)
    Spacer(Modifier.height(8.dp))
}
