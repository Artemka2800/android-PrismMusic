package com.prism.music.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.prism.music.audio.AudioPlayer
import com.prism.music.data.store.SettingsStore
import com.prism.music.ui.theme.DarkBackground

@Composable
fun ImmersiveBackground(
    player: AudioPlayer,
    settings: SettingsStore,
    modifier: Modifier = Modifier
) {
    val track by player.currentTrack.collectAsState()
    val context = LocalContext.current
    val backendURL by settings.backendURL.collectAsState(initial = SettingsStore.DEFAULT_BACKEND_URL)

    Box(modifier = modifier.fillMaxSize().background(DarkBackground)) {
        Crossfade(
            targetState = track,
            animationSpec = tween(800),
            label = "BackgroundTransition"
        ) { activeTrack ->
            if (activeTrack != null) {
                Box(modifier = Modifier.fillMaxSize()) {
                    AsyncImage(
                        model = activeTrack.getArtworkURL(context, backendURL) ?: "https://pm.standrise.net/icons/default_cover.png",
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .blur(60.dp),
                        contentScale = ContentScale.Crop,
                        alpha = 0.45f
                    )
                    
                    // Dark overlay gradient for readability
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Black.copy(alpha = 0.55f),
                                        Color.Black.copy(alpha = 0.85f)
                                    )
                                )
                            )
                    )
                }
            } else {
                // Flat dark gradient
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    DarkBackground,
                                    Color(0xFF0F0F12)
                                )
                            )
                        )
                )
            }
        }
    }
}
