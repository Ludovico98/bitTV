package com.bittv.home.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.bittv.home.model.AppEntry
import com.bittv.home.ui.components.AppIcon
import com.bittv.home.ui.components.LocalAppArt
import com.bittv.home.ui.components.FocusTile
import com.bittv.home.ui.components.LocalMotion
import com.bittv.home.data.AppOrder
import android.view.KeyEvent as AndroidKey
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.zIndex

/**
 * The left app bar: its own focus column. Up/Down stay inside it, Right returns to the content (which restores its
 * last focus), and it is reached only with Left from the first item of a row.
 *
 * Rearranging, as on tvOS: hold OK on an app to lift it, Up/Down move it, OK or Back drop it ([onReorder] saves).
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun AppBar(apps: List<AppEntry>, installed: Set<String>, onOpen: (AppEntry) -> Unit, modifier: Modifier = Modifier,
           returnTo: () -> Boolean = { false }, onReorder: (List<String>) -> Unit = {}) {
    var order by remember(apps) { mutableStateOf(apps) }
    var moving by remember { mutableStateOf<String?>(null) }
    var swallowUp by remember { mutableStateOf(false) }   // the key-up of the press that dropped the app
    fun drop() { moving = null; swallowUp = true; onReorder(order.map { it.id }) }
    fun keys(app: AppEntry, e: KeyEvent): Boolean {
        val code = e.nativeKeyEvent.keyCode
        val ok = code == AndroidKey.KEYCODE_DPAD_CENTER || code == AndroidKey.KEYCODE_ENTER || code == AndroidKey.KEYCODE_NUMPAD_ENTER
        val down = e.type == KeyEventType.KeyDown; val repeat = e.nativeKeyEvent.repeatCount
        return when {
            moving == app.id -> {   // while lifted every key belongs to the move
                if (down && repeat == 0) when (code) {
                    AndroidKey.KEYCODE_DPAD_UP, AndroidKey.KEYCODE_DPAD_DOWN -> {
                        val ids = AppOrder.move(order.map { it.id }, app.id, if (code == AndroidKey.KEYCODE_DPAD_UP) -1 else 1)
                        order = ids.map { id -> order.first { it.id == id } }
                    }
                    AndroidKey.KEYCODE_BACK -> drop()
                    else -> if (ok) drop()
                }
                true
            }
            ok && down && repeat == 1 && moving == null -> { moving = app.id; true }   // OK held: lift it
            ok && down && repeat > 1 -> true
            swallowUp && !down && (ok || code == AndroidKey.KEYCODE_BACK) -> { swallowUp = false; true }
            else -> false
        }
    }
    Box(
        modifier
            .testTag("rail")
            .background(Color(0x9E161B26), RoundedCornerShape(22.dp))
            .border(1.dp, Color(0x1FFFFFFF), RoundedCornerShape(22.dp))
            .focusProperties {
                onExit = {
                    when (requestedFocusDirection) {
                        FocusDirection.Up, FocusDirection.Down -> cancelFocusChange()
                        // Right returns exactly where you were (lazy rows can't be restored by Compose's restorer)
                        FocusDirection.Right -> if (returnTo()) cancelFocusChange()
                        else -> {}
                    }
                }
            }
            .focusRestorer(),
    ) {
        LazyColumn(
            Modifier
                .fillMaxSize()
                // soft fade at the top and bottom edges so a cut-off icon reads as "more below"
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    drawRect(Brush.verticalGradient(0f to Color.Transparent, 17f / size.height to Color.Black,
                        1f - 17f / size.height to Color.Black, 1f to Color.Transparent), blendMode = BlendMode.DstIn)
                },
            contentPadding = PaddingValues(vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(15.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            items(order, key = { it.id }) { app ->
                val isInstalled = app.id in installed
                val lifted = moving == app.id
                val view = remember { BringIntoViewRequester() }
                LaunchedEffect(lifted, order) { if (lifted) view.bringIntoView() }   // a moved app stays on screen
                val lift by animateFloatAsState(if (lifted) 1.12f else 1f, LocalMotion.current.tween(160), label = "lift")
                Box(Modifier.bringIntoViewRequester(view).onPreviewKeyEvent { keys(app, it) }
                    .then(if (lifted) Modifier.testTag("rail:moving") else Modifier)
                    .zIndex(if (lifted) 2f else 0f)
                    .graphicsLayer { scaleX = lift; scaleY = lift; alpha = if (moving != null && !lifted) 0.55f else 1f }) {
                    if (lifted) {   // up and down arrows say "move me"
                        listOf(-1, 1).forEach { d ->
                            Box(Modifier.align(if (d < 0) Alignment.TopCenter else Alignment.BottomCenter).offset(y = (d * 13).dp).size(14.dp)
                                .background(Color.White, CircleShape), contentAlignment = Alignment.Center) {
                                GlyphIcon(Glyph.BACK, Color(0xFF111111), Modifier.size(9.dp).graphicsLayer { rotationZ = if (d < 0) 90f else -90f })
                            }
                        }
                    }
                    FocusTile(onClick = { onOpen(app) }, width = 75.dp, height = 45.dp, shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("app:${app.id}")) {
                        AppIcon(app, 75.dp, 45.dp, Modifier.alpha(if (isInstalled) 1f else 0.45f), art = LocalAppArt.current[app.id])
                    }
                    if (!isInstalled) {
                        Box(Modifier.align(Alignment.BottomEnd).offset(4.dp, 4.dp).size(15.dp).background(Color.White, CircleShape),
                            contentAlignment = Alignment.Center) {
                            GlyphIcon(Glyph.BACK, Color(0xFF111111), Modifier.size(9.dp).graphicsLayer { rotationZ = -90f })
                        }
                    }
                }
            }
        }
    }
}
