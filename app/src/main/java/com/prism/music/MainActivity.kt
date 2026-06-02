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

        // Start background media service. Media3 automatically elevates it to foreground when playback starts
        val intent = Intent(this, PrismPlaybackService::class.java)
        startService(intent)

        // Request notification permissions for playback control on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
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
