package com.bittv.home.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.bittv.home.ui.components.PillButton
import com.bittv.home.ui.theme.GlassLine
import com.bittv.home.ui.theme.Ink2
import com.bittv.home.ui.theme.Ink3
import com.bittv.home.ui.theme.Type

/** One remote button: its label on the remote, what it does in BitTV, and the key that stands in for it on the demo. */
data class RemoteKey(val button: String, val does: String, val keyboard: String? = null, val onTvOnly: Boolean = false)

val RemoteKeys = listOf(
    RemoteKey("◀ ▲ ▶ ▼", "Move. Left from the first item in a row opens the apps bar; Up and Down stay in the page.", "Arrow keys"),
    RemoteKey("OK", "Open, play or select. On an Assistant answer, closes it straight away.", "Enter"),
    RemoteKey("Back", "Close the menu, answer or page you are on. On Home, back to the top.", "Esc"),
    RemoteKey("Home", "Back to the top of BitTV Home from anywhere, inside any app.", onTvOnly = true),
    RemoteKey("Mic", "Ask the Assistant: the answer appears in the active section, read aloud.", "M", onTvOnly = true),
    RemoteKey("Netflix", "Opens Netflix.", onTvOnly = true),
    RemoteKey("YouTube", "Opens SmartTube (remapped with Button Mapper).", onTvOnly = true),
    RemoteKey("Input", "Switch HDMI input: the PlayStation 5 is HDMI 1.", onTvOnly = true),
    RemoteKey("Vol / Mute", "TV volume, as always.", onTvOnly = true),
)

/** Settings → Remote shortcuts. Back (or Close) returns to Settings. */
@Composable
fun RemoteGuide(onClose: () -> Unit, modifier: Modifier = Modifier) {
    val close = remember { FocusRequester() }
    LaunchedEffect(Unit) { close.requestFocus() }
    BackHandler(onBack = onClose)
    Column(
        modifier.testTag("remote:guide").width(800.dp).background(Color(0xFF121722), RoundedCornerShape(18.dp))
            .border(1.dp, GlassLine, RoundedCornerShape(18.dp)).padding(horizontal = 22.dp, vertical = 22.dp),
    ) {
        Text("Remote shortcuts", style = Type.Title.copy(fontSize = 22.sp))
        Text("What each button does in BitTV. On this computer's demo, the keyboard keys on the right stand in for them.",
            style = Type.Caption)
        Spacer(Modifier.height(14.dp))
        RemoteKeys.forEach { k ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(112.dp)) {
                    Text(k.button, style = Type.Label.copy(fontWeight = FontWeight.Bold), textAlign = TextAlign.Center,
                        modifier = Modifier.testTag("key:${k.button.substringBefore(' ')}")
                            .background(Color(0x14FFFFFF), RoundedCornerShape(8.dp)).border(1.dp, GlassLine, RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 5.dp))
                }
                Text(k.does, style = Type.Body.copy(fontSize = 13.5.sp, lineHeight = 17.sp), modifier = Modifier.weight(1f))
                Text(k.keyboard ?: "—", style = Type.Caption.copy(color = if (k.keyboard != null) Ink2 else Ink3),
                    textAlign = TextAlign.End, modifier = Modifier.width(92.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("Home, Mic and the app buttons work once BitTV is installed on the TV.", style = Type.Caption.copy(color = Ink3))
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            PillButton("Close", onClose, Modifier.focusRequester(close).testTag("remote:close"))
        }
    }
}
