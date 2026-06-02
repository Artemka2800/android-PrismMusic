package com.prism.music

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.prism.music.audio.PrismPlaybackService
import com.prism.music.ui.AppNavigation
import com.prism.music.ui.theme.PrismMusicTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Make the app UI render edge-to-edge behind status and navigation bars
        enableEdgeToEdge()

        val app = application as PrismMusicApp
        val api = app.apiClient
        val settings = app.settingsStore
        val player = app.audioPlayer

        // Start background media service to ensure playback works in background
        val intent = Intent(this, PrismPlaybackService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }

        setContent {
            PrismMusicTheme {
                AppNavigation(
                    api = api,
                    settings = settings,
                    player = player
                )
            }
        }
    }
}
