package com.prism.music.audio

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.prism.music.data.model.Track
import com.prism.music.data.model.TrackSource
import com.prism.music.data.network.APIClient
import com.prism.music.data.store.SettingsStore
import com.prism.music.util.LyricsParser
import com.prism.music.util.ParsedLyrics
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import java.io.File

class AudioPlayer private constructor(
    private val context: Context,
    private val api: APIClient,
    private val settings: SettingsStore
) {

    companion object {
        @Volatile
        private var INSTANCE: AudioPlayer? = null

        fun getInstance(context: Context): AudioPlayer {
            return INSTANCE ?: synchronized(this) {
                val settingsStore = SettingsStore(context.applicationContext)
                val apiClient = APIClient(context.applicationContext, settingsStore)
                val instance = AudioPlayer(context.applicationContext, apiClient, settingsStore)
                INSTANCE = instance
                instance
            }
        }
    }

    // Underlying ExoPlayer instance
    val exoPlayer: ExoPlayer = ExoPlayer.Builder(context).build()
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // Observable states exposed to Compose
    private val _currentTrack = MutableStateFlow<Track?>(null)
    val currentTrack: StateFlow<Track?> = _currentTrack.asStateFlow()

    private val _queue = MutableStateFlow<List<Track>>(emptyList())
    val queue: StateFlow<List<Track>> = _queue.asStateFlow()

    private val _currentIndex = MutableStateFlow(0)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _progress = MutableStateFlow(0.0)
    val progress: StateFlow<Double> = _progress.asStateFlow()

    private val _duration = MutableStateFlow(0.0)
    val duration: StateFlow<Double> = _duration.asStateFlow()

    private val _isBuffering = MutableStateFlow(false)
    val isBuffering: StateFlow<Boolean> = _isBuffering.asStateFlow()

    private val _isShuffled = MutableStateFlow(false)
    val isShuffled: StateFlow<Boolean> = _isShuffled.asStateFlow()

    private val _repeatMode = MutableStateFlow(Player.REPEAT_MODE_OFF)
    val repeatMode: StateFlow<Int> = _repeatMode.asStateFlow()

    private val _lyrics = MutableStateFlow<ParsedLyrics?>(null)
    val lyrics: StateFlow<ParsedLyrics?> = _lyrics.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private var progressJob: Job? = null
    private var transitionJob: Job? = null
    private var lyricsJob: Job? = null

    private var hasTriggeredAutoNext = false
    private var trackLoadRetryCount = 0
    private var storedVolume = 1.0f
    private var isMuted = false

    init {
        bootstrap()
    }

    private fun bootstrap() {
        exoPlayer.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                _isPlaying.value = playing
                if (playing) {
                    startProgressObserver()
                } else {
                    stopProgressObserver()
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                _isBuffering.value = playbackState == Player.STATE_BUFFERING
                
                if (playbackState == Player.STATE_READY) {
                    _duration.value = (exoPlayer.duration.coerceAtLeast(0) / 1000.0)
                    _isBuffering.value = false
                } else if (playbackState == Player.STATE_ENDED) {
                    handleTrackEnded()
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.e("AudioPlayer", "Player error: ${error.message}")
                _errorMessage.value = "Ошибка воспроизведения: ${error.localizedMessage}"
                _isBuffering.value = false
                
                // Fallover: Rotate host and retry playback if hosts not exhausted
                scope.launch {
                    val hosts = SettingsStore.HOSTS
                    if (trackLoadRetryCount < hosts.size) {
                        trackLoadRetryCount++
                        Log.i("AudioPlayer", "Rotating host and retrying (attempt $trackLoadRetryCount)...")
                        api.rotateHost()
                        val track = _currentTrack.value
                        if (track != null) {
                            load(track, autoplay = _isPlaying.value, isRetry = true)
                        } else {
                            next()
                        }
                    } else {
                        trackLoadRetryCount = 0
                        _errorMessage.value = "Не удалось воспроизвести трек ни на одном из доступных серверов."
                        next()
                    }
                }
            }
        })

        // Volume setup
        exoPlayer.volume = storedVolume
    }

    // Play a queue starting from a specific index
    fun play(tracks: List<Track>, startAt: Int = 0) {
        if (tracks.isEmpty()) return
        val clampedIndex = startAt.coerceIn(0, tracks.size - 1)
        _queue.value = tracks
        _currentIndex.value = clampedIndex
        load(tracks[clampedIndex], autoplay = true)
    }

    fun togglePlay() {
        if (exoPlayer.isPlaying) {
            exoPlayer.pause()
        } else {
            if (exoPlayer.playbackState == Player.STATE_IDLE) {
                _currentTrack.value?.let { load(it, autoplay = true) }
            } else {
                exoPlayer.play()
            }
        }
    }

    fun next(isAutomatic: Boolean = false) {
        val tracks = _queue.value
        if (tracks.isEmpty()) return
        
        val nextIdx: Int
        if (_isShuffled.value) {
            nextIdx = (0 until tracks.size).random()
        } else if (_currentIndex.value + 1 >= tracks.size) {
            if (_repeatMode.value == Player.REPEAT_MODE_ALL) {
                nextIdx = 0
            } else {
                exoPlayer.pause()
                return
            }
        } else {
            nextIdx = _currentIndex.value + 1
        }

        _currentIndex.value = nextIdx
        if (isAutomatic) {
            performCrossfadeAndLoad(tracks[nextIdx], autoplay = true)
        } else {
            load(tracks[nextIdx], autoplay = true)
        }
    }

    fun previous() {
        val tracks = _queue.value
        if (tracks.isEmpty()) return

        if (_progress.value > 3.0) {
            seek(0.0)
            return
        }

        val prevIdx = if (_currentIndex.value == 0) tracks.size - 1 else _currentIndex.value - 1
        _currentIndex.value = prevIdx
        load(tracks[prevIdx], autoplay = true)
    }

    fun seek(seconds: Double) {
        exoPlayer.seekTo((seconds * 1000.0).toLong())
        _progress.value = seconds
    }

    fun setVolume(vol: Float) {
        val clamped = vol.coerceIn(0.0f, 1.0f)
        storedVolume = clamped
        if (!isMuted) {
            exoPlayer.volume = clamped
        }
    }

    fun toggleMute() {
        isMuted = !isMuted
        exoPlayer.volume = if (isMuted) 0.0f else storedVolume
    }

    fun toggleRepeat() {
        val mode = when (_repeatMode.value) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_OFF
            else -> Player.REPEAT_MODE_OFF
        }
        _repeatMode.value = mode
        exoPlayer.repeatMode = if (mode == Player.REPEAT_MODE_ONE) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
    }

    fun toggleShuffle() {
        val shuffled = !_isShuffled.value
        _isShuffled.value = shuffled
    }

    fun toggleLike() {
        val track = _currentTrack.value ?: return
        scope.launch {
            val userId = settings.getUserId()
            if (userId.isNotEmpty()) {
                api.toggleLikeOnServer(userId, track)
            }
        }
    }

    fun replaceTrackInQueue(oldTrackId: String, withTrack: Track) {
        val currentQueue = _queue.value.toMutableList()
        val idx = currentQueue.indexOfFirst { it.id == oldTrackId }
        if (idx != -1) {
            currentQueue[idx] = withTrack
            _queue.value = currentQueue
        }

        if (_currentTrack.value?.id == oldTrackId) {
            val savedProgress = _progress.value
            val wasPlaying = _isPlaying.value
            load(withTrack, autoplay = wasPlaying)
            seek(savedProgress)
        }
    }

    fun findAndReplace(track: Track, targetSource: TrackSource, onCompletion: (String) -> Unit) {
        scope.launch {
            try {
                val results = api.findTrack(
                    title = track.title,
                    artist = track.artist,
                    targetSource = targetSource.value
                )
                val matched = results.firstOrNull()
                if (matched == null) {
                    onCompletion("Трек не найден на ${targetSource.label}")
                    return@launch
                }
                
                val newId = "${targetSource.value}:${matched.id}"
                val replacedTrack = Track(
                    id = newId,
                    title = matched.title,
                    artist = matched.artist,
                    album = matched.album ?: track.album,
                    durationSeconds = matched.durationSeconds ?: track.durationSeconds,
                    cover = matched.cover ?: track.cover,
                    streamURL = matched.streamURL ?: track.streamURL,
                    source = targetSource
                )
                
                // If it was liked, replace it on server
                val userId = settings.getUserId()
                if (userId.isNotEmpty()) {
                    val liked = api.fetchLikedTracks(userId)
                    if (liked.any { it.id == track.id }) {
                        api.replaceLikedTrack(userId, track.id, replacedTrack)
                    }
                }
                
                // Replace in queue
                replaceTrackInQueue(track.id, replacedTrack)
                onCompletion("Трек успешно заменен на ${targetSource.label}!")
            } catch (e: Exception) {
                Log.e("AudioPlayer", "Find and replace failed: ${e.message}")
                onCompletion("Ошибка замены: ${e.localizedMessage}")
            }
        }
    }

    private fun startService() {
        try {
            val intent = android.content.Intent(context, PrismPlaybackService::class.java)
            context.startService(intent)
        } catch (e: Exception) {
            Log.e("AudioPlayer", "Failed to start service: ${e.message}")
        }
    }

    private fun load(track: Track, autoplay: Boolean, isRetry: Boolean = false) {
        if (!isRetry) {
            trackLoadRetryCount = 0
        }
        _currentTrack.value = track
        _progress.value = 0.0
        _duration.value = track.durationSeconds ?: 0.0
        _isBuffering.value = true
        _lyrics.value = null
        hasTriggeredAutoNext = false

        if (autoplay) {
            startService()
        }

        // Fetch lyrics
        lyricsJob?.cancel()
        lyricsJob = scope.launch {
            fetchLyrics(track)
        }

        scope.launch {
            val backendURL = settings.getBackendURL()
            val yandexToken = settings.getYandexToken()

            // Check if track is downloaded locally
            val safeId = track.id.replace(":", "_")
            val localFile = File(context.filesDir, "PrismDownloads/$safeId.mp3")
            
            val streamUrl = if (localFile.exists()) {
                Uri.fromFile(localFile).toString()
            } else {
                api.streamURL(track, backendURL, yandexToken)
            }

            withContext(Dispatchers.Main) {
                exoPlayer.stop()
                exoPlayer.clearMediaItems()
                
                val metadata = MediaMetadata.Builder()
                    .setTitle(track.title)
                    .setArtist(track.artist)
                    .setAlbumTitle(track.album)
                    .build()

                val mediaItem = MediaItem.Builder()
                    .setUri(streamUrl)
                    .setMediaMetadata(metadata)
                    .build()

                exoPlayer.setMediaItem(mediaItem)
                exoPlayer.prepare()
                exoPlayer.volume = if (isMuted) 0.0f else storedVolume
                exoPlayer.playWhenReady = autoplay
            }
        }
    }

    // Handles smooth volume interpolation for crossfading when transitioning
    private fun performCrossfadeAndLoad(track: Track, autoplay: Boolean) {
        transitionJob?.cancel()
        transitionJob = scope.launch {
            val crossfadeDurationMs = 2000L
            val steps = 20
            val delayStep = crossfadeDurationMs / steps
            val currentVol = if (isMuted) 0.0f else storedVolume

            // Fade Out
            for (i in steps downTo 0) {
                exoPlayer.volume = currentVol * (i.toFloat() / steps)
                delay(delayStep)
            }

            // Load next track
            load(track, autoplay = autoplay)

            // Fade In
            for (i in 0..steps) {
                exoPlayer.volume = currentVol * (i.toFloat() / steps)
                delay(delayStep)
            }
            exoPlayer.volume = currentVol
        }
    }

    private fun handleTrackEnded() {
        if (_repeatMode.value == Player.REPEAT_MODE_ONE) {
            seek(0.0)
            exoPlayer.play()
        } else {
            next(isAutomatic = true)
        }
    }

    private fun startProgressObserver() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive) {
                val currentPos = exoPlayer.currentPosition / 1000.0
                _progress.value = currentPos
                
                // Trigger auto-next crossfade 3 seconds before ending
                val rem = _duration.value - currentPos
                if (rem in 0.0..3.0 && !hasTriggeredAutoNext && _repeatMode.value != Player.REPEAT_MODE_ONE && _duration.value > 10.0) {
                    val tracks = _queue.value
                    if (tracks.isNotEmpty() && (_currentIndex.value + 1 < tracks.size || _repeatMode.value == Player.REPEAT_MODE_ALL)) {
                        hasTriggeredAutoNext = true
                        next(isAutomatic = true)
                    }
                }
                delay(250)
            }
        }
    }

    private fun stopProgressObserver() {
        progressJob?.cancel()
        progressJob = null
    }

    private suspend fun fetchLyrics(track: Track) {
        val res = api.lyrics(
            artist = track.artist,
            title = track.title,
            id = track.id,
            duration = track.durationSeconds
        )
        if (res?.lyrics != null && res.lyrics.isNotEmpty()) {
            val parsed = LyricsParser.parse(res.lyrics, track.durationSeconds)
            if (_currentTrack.value?.id == track.id) {
                _lyrics.value = parsed
            }
        }
    }
}
