package com.prism.music

import android.app.Application
import com.prism.music.audio.AudioPlayer
import com.prism.music.data.network.APIClient
import com.prism.music.data.store.SettingsStore

class PrismMusicApp : Application() {
    
    lateinit var settingsStore: SettingsStore
        private set
    
    lateinit var apiClient: APIClient
        private set
        
    lateinit var audioPlayer: AudioPlayer
        private set

    override fun onCreate() {
        super.onCreate()
        
        // Initialize Core Dependencies
        settingsStore = SettingsStore(this)
        apiClient = APIClient(this, settingsStore)
        audioPlayer = AudioPlayer.getInstance(this)
    }
}
