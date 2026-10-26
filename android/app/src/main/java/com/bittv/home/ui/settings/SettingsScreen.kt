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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import com.bittv.home.ui.components.LocalAppArt
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

/** Settings: how BitTV looks and moves, and the way into the TV's own settings. Every change is saved at once. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    prefs: Prefs, repo: PrefsRepo, apps: List<AppEntry>, installed: Set<String> = apps.map { it.id }.toSet(),
    onReplayBoot: () -> Unit, onBack: () -> Unit, onTvSettings: () -> Unit = {},
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
            Modifier.width(800.dp).heightIn(max = 480.dp).background(Color(0xFF121722), RoundedCornerShape(18.dp))
                .border(1.dp, GlassLine, RoundedCornerShape(18.dp)).clip(RoundedCornerShape(18.dp))
                .verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 22.dp),
        ) {
            Text("Settings", style = Type.Title.copy(fontSize = 22.sp))
            Text("Suggested, the banner and Search show titles from the apps installed on this TV.", style = Type.Caption)
            Section("Experience")
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.padding(bottom = 7.dp)) {
                OptionRow("Animations", Modifier.width(251.dp).testTag("opt:animations").focusRequester(first), checked = prefs.animations,
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
            Section("Apps on Home")
            Text("Every app on this TV. Switch one on to put it on the app bar; apps installed later go on it by themselves.",
                style = Type.Caption, modifier = Modifier.padding(bottom = 8.dp))
            // BitTV's own apps that aren't installed are listed too, last: on the bar they offer the Play Store
            FlowRow(maxItemsInEachRow = 3, horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                apps.sortedBy { it.id !in installed }.forEach { a ->   // the installed ones first, in the app bar's order
                    val here = a.id in installed
                    OptionRow(if (here) a.label else "${a.label} · not installed", Modifier.width(251.dp).testTag("opt:app:${a.id}"),
                        checked = a.id !in prefs.hiddenApps, onClick = { scope.launch { repo.toggleAppShown(a.id) } },
                        leading = { AppIcon(a, 30.dp, 18.dp, Modifier.alpha(if (here) 1f else 0.45f), radius = 4.dp, art = LocalAppArt.current[a.id]) })
                }
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
