package com.prism.music.ui.screens

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.prism.music.audio.AudioPlayer
import com.prism.music.data.model.Track
import com.prism.music.data.store.SettingsStore
import com.prism.music.ui.theme.DarkBackground
import com.prism.music.ui.theme.DarkSurface
import com.prism.music.ui.theme.MutedText
import com.prism.music.util.LyricsLine
import com.prism.music.util.ParsedLyrics
import kotlinx.coroutines.launch

@Composable
fun PlayerScreen(
    player: AudioPlayer,
    settings: SettingsStore,
    onMinimize: () -> Unit
) {
    val track by player.currentTrack.collectAsState()
    val isPlaying by player.isPlaying.collectAsState()
    val progress by player.progress.collectAsState()
    val duration by player.duration.collectAsState()
    val isBuffering by player.isBuffering.collectAsState()
    val repeatMode by player.repeatMode.collectAsState()
    val isShuffled by player.isShuffled.collectAsState()
    val lyrics by player.lyrics.collectAsState()
    val context = LocalContext.current
    val backendURL by settings.backendURL.collectAsState(initial = SettingsStore.DEFAULT_BACKEND_URL)

    var showLyrics by remember { mutableStateOf(false) }

    if (track == null) {
        Box(modifier = Modifier.fillMaxSize().background(DarkBackground), contentAlignment = Alignment.Center) {
            Text("Ничего не воспроизводится", color = MutedText)
        }
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        // 1. Blurred background image of the cover art
        AsyncImage(
            model = track?.getArtworkURL(context, backendURL) ?: "https://pm.standrise.net/icons/default_cover.png",
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .blur(40.dp)
                .background(Color.Black.copy(alpha = 0.55f)),
            contentScale = ContentScale.Crop,
            alpha = 0.35f
        )

        // Gradient overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, DarkBackground.copy(alpha = 0.95f))
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header: Minimize button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onMinimize) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Свернуть", tint = Color.White)
                }
                Text(
                    text = "Исполняется",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                IconButton(onClick = { showLyrics = !showLyrics }) {
                    Icon(
                        Icons.Default.Menu,
                        contentDescription = "Лирика",
                        tint = if (showLyrics) Color.White else MutedText
                    )
                }
            }

            // Body: Switch between Album Art and Synced Lyrics
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                if (showLyrics && lyrics != null) {
                    SyncedLyricsView(lyrics = lyrics!!, progress = progress)
                } else {
                    AlbumArtView(track = track!!, backendURL = backendURL, isBuffering = isBuffering)
                }
            }

            // Bottom controls: Titles, Slider, Actions
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Song Metadata
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = track?.title ?: "",
                            color = Color.White,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = track?.artist ?: "",
                            color = MutedText,
                            fontSize = 16.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = { player.toggleLike() }) {
                        Icon(Icons.Default.FavoriteBorder, contentDescription = "В избранное", tint = Color.White)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Progress Slider
                Slider(
                    value = progress.toFloat(),
                    onValueChange = { player.seek(it.toDouble()) },
                    valueRange = 0f..(duration.toFloat().coerceAtLeast(1f)),
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color.White,
                        inactiveTrackColor = Color.White.copy(alpha = 0.24f)
                    )
                )

                // Times
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = formatProgress(progress), color = MutedText, fontSize = 12.sp)
                    Text(text = formatProgress(duration), color = MutedText, fontSize = 12.sp)
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Control Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { player.toggleShuffle() }) {
                        Icon(
                            Icons.Default.Shuffle,
                            contentDescription = "Перемешать",
                            tint = if (isShuffled) Color.White else MutedText
                        )
                    }

                    IconButton(onClick = { player.previous() }) {
                        Icon(Icons.Default.SkipPrevious, contentDescription = "Назад", tint = Color.White, modifier = Modifier.size(32.dp))
                    }

                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                            .clickable { player.togglePlay() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "Воспроизведение",
                            tint = Color.Black,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    IconButton(onClick = { player.next() }) {
                        Icon(Icons.Default.SkipNext, contentDescription = "Вперед", tint = Color.White, modifier = Modifier.size(32.dp))
                    }

                    IconButton(onClick = { player.toggleRepeat() }) {
                        Icon(
                            imageVector = Icons.Default.Repeat,
                            contentDescription = "Повтор",
                            tint = when (repeatMode) {
                                androidx.media3.common.Player.REPEAT_MODE_ALL -> Color.White
                                androidx.media3.common.Player.REPEAT_MODE_ONE -> Color.Yellow
                                else -> MutedText
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AlbumArtView(track: Track, backendURL: String, isBuffering: Boolean) {
    val context = LocalContext.current
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .fillMaxWidth(0.85f)
            .clip(RoundedCornerShape(16.dp)),
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = track.getArtworkURL(context, backendURL) ?: "https://pm.standrise.net/icons/default_cover.png",
            contentDescription = track.title,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        if (isBuffering) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.35f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color.White)
            }
        }
    }
}

// Synced Lyrics component that auto-scrolls to the highlighted active line
@Composable
fun SyncedLyricsView(lyrics: ParsedLyrics, progress: Double) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // Find the currently active line index
    val activeIndex = remember(lyrics, progress) {
        lyrics.lines.indexOfFirst { line ->
            progress >= line.time && (line.endTime == null || progress < line.endTime!!)
        }
    }

    // Auto-scroll when index changes to center the active line
    LaunchedEffect(activeIndex) {
        if (activeIndex != -1) {
            scope.launch {
                listState.animateScrollToItem(maxOf(0, activeIndex - 2))
            }
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(vertical = 48.dp)
    ) {
        itemsIndexed(lyrics.lines) { index, line ->
            val isActive = index == activeIndex
            val color = if (isActive) Color.White else MutedText.copy(alpha = 0.65f)
            val scale = if (isActive) 1.15f else 1.0f
            val weight = if (isActive) FontWeight.Bold else FontWeight.Medium

            Text(
                text = line.text,
                color = color,
                fontSize = (18 * scale).sp,
                fontWeight = weight,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                lineHeight = 26.sp
            )
        }
    }
}

private fun formatProgress(seconds: Double): String {
    if (!seconds.isFinite() || seconds <= 0) return "0:00"
    val minutes = seconds.toInt() / 60
    val secs = seconds.toInt() % 60
    return String.format("%d:%02d", minutes, secs)
}
