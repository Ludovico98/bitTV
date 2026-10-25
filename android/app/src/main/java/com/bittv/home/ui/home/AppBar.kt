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
import com.bittv.home.ui.components.FocusTile

/**
 * The left app bar: its own focus column. Up/Down stay inside it, Right returns to the content (which restores its
 * last focus), and it is reached only with Left from the first item of a row.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun AppBar(apps: List<AppEntry>, installed: Set<String>, onOpen: (AppEntry) -> Unit, modifier: Modifier = Modifier,
           returnTo: () -> FocusRequester? = { null }) {
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
                        FocusDirection.Right -> returnTo()?.let { cancelFocusChange(); it.requestFocus() }
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
            items(apps, key = { it.id }) { app ->
                val isInstalled = app.id in installed
                Box {
                    FocusTile(onClick = { onOpen(app) }, width = 75.dp, height = 45.dp, shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("app:${app.id}")) {
                        AppIcon(app, 75.dp, 45.dp, Modifier.alpha(if (isInstalled) 1f else 0.45f))
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
