package com.prism.music.ui.screens

import android.util.Log
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.prism.music.PrismMusicApp
import com.prism.music.audio.AudioPlayer
import com.prism.music.data.model.Album
import com.prism.music.data.model.Track
import com.prism.music.data.model.TrackSource
import com.prism.music.data.network.APIClient
import com.prism.music.data.store.SettingsStore
import com.prism.music.ui.theme.DarkBackground
import com.prism.music.ui.theme.DarkSurface
import com.prism.music.ui.theme.DarkSurfaceVariant
import com.prism.music.ui.theme.MutedText
import kotlinx.coroutines.launch

// --- LOGIN SCREEN ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    api: APIClient,
    settings: SettingsStore,
    onLoginSuccess: () -> Unit
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF1E1E24), DarkBackground)
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "PrismMusic",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "Вход или регистрация",
                    fontSize = 14.sp,
                    color = MutedText
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Имя пользователя") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Пароль") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = {
                        if (username.isBlank() || password.isBlank()) {
                            Toast.makeText(context, "Заполните все поля", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        isLoading = true
                        scope.launch {
                            try {
                                val res = api.login(username, password)
                                settings.setUserId(res.id)
                                settings.setUsername(res.username)
                                res.token?.let { settings.setYandexToken(it) }
                                onLoginSuccess()
                            } catch (e: Exception) {
                                Toast.makeText(context, "Ошибка входа: ${e.message}", Toast.LENGTH_SHORT).show()
                            } finally {
                                isLoading = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)
                ) {
                    if (isLoading) CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(20.dp))
                    else Text("Войти")
                }

                TextButton(
                    onClick = {
                        if (username.isBlank() || password.isBlank()) {
                            Toast.makeText(context, "Заполните все поля", Toast.LENGTH_SHORT).show()
                            return@TextButton
                        }
                        isLoading = true
                        scope.launch {
                            try {
                                val res = api.register(username, password)
                                settings.setUserId(res.id)
                                settings.setUsername(res.username)
                                onLoginSuccess()
                            } catch (e: Exception) {
                                Toast.makeText(context, "Ошибка регистрации: ${e.message}", Toast.LENGTH_SHORT).show()
                            } finally {
                                isLoading = false
                            }
                        }
                    }
                ) {
                    Text("Создать новый аккаунт", color = Color.White)
                }
            }
        }
    }
}

// --- HOME SCREEN ---
@Composable
fun HomeScreen(
    api: APIClient,
    settings: SettingsStore,
    player: AudioPlayer,
    onAlbumClick: (Album) -> Unit
) {
    var recommendations by remember { mutableStateOf<List<Album>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var title by remember { mutableStateOf("Подборки для вас") }
    val scope = rememberCoroutineScope()
    val backendURL by settings.backendURL.collectAsState(initial = SettingsStore.DEFAULT_BACKEND_URL)

    LaunchedEffect(Unit) {
        scope.launch {
            try {
                val res = api.recommendations()
                recommendations = res.mappedAlbums
                title = res.title ?: "Подборки для вас"
            } catch (e: Exception) {
                Log.e("HomeScreen", "Error: ${e.message}")
            } finally {
                isLoading = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(16.dp)
    ) {
        Text(
            text = title,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color.White)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(recommendations) { album ->
                    AlbumGridItem(album = album, backendURL = backendURL) {
                        // When clicked, fetch tracks and show detail
                        scope.launch {
                            try {
                                val tracks = api.playlistTracks(album.id, album.source?.value ?: "soundcloud")
                                val detailedAlbum = album.copy(tracks = tracks)
                                onAlbumClick(detailedAlbum)
                            } catch (e: Exception) {
                                // Fallback: play album directly if tracks loaded already
                                onAlbumClick(album)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AlbumGridItem(album: Album, backendURL: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
    ) {
        AsyncImage(
            model = album.getArtworkURL(backendURL) ?: "https://pm.standrise.net/icons/default_cover.png",
            contentDescription = album.title,
            modifier = Modifier
                .aspectRatio(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = album.title,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = album.artist,
            color = MutedText,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// --- SEARCH SCREEN ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    api: APIClient,
    player: AudioPlayer,
    settings: SettingsStore
) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Track>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val backendURL by settings.backendURL.collectAsState(initial = SettingsStore.DEFAULT_BACKEND_URL)
    val userId by settings.userId.collectAsState(initial = "")

    var playlists by remember { mutableStateOf<List<Album>>(emptyList()) }
    LaunchedEffect(userId) {
        if (userId.isNotEmpty()) {
            try {
                playlists = api.fetchPlaylists(userId).map { it.toAlbum() }
            } catch (e: Exception) {
                Log.e("SearchScreen", "Error fetching playlists: ${e.message}")
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = {
                query = it
                if (it.length > 2) {
                    isLoading = true
                    scope.launch {
                        try {
                            val res = api.search(it)
                            results = res.tracks
                        } catch (e: Exception) {
                            Log.e("SearchScreen", "Error: ${e.message}")
                        } finally {
                            isLoading = false
                        }
                    }
                }
            },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Поиск", tint = MutedText) },
            placeholder = { Text("Треки, артисты...", color = MutedText) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color.White)
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(results) { track ->
                    TrackRow(
                        track = track,
                        backendURL = backendURL,
                        playlists = playlists,
                        onPlaylistChanged = {
                            scope.launch {
                                try {
                                    playlists = api.fetchPlaylists(userId).map { it.toAlbum() }
                                } catch (e: Exception) {}
                            }
                        },
                        onPlay = {
                            player.play(results, results.indexOf(track))
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun TrackRow(
    track: Track,
    backendURL: String,
    playlists: List<Album> = emptyList(),
    currentPlaylistId: String? = null,
    onPlaylistChanged: (() -> Unit)? = null,
    onPlay: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val app = context.applicationContext as PrismMusicApp
    val api = app.apiClient
    val settings = app.settingsStore
    val player = app.audioPlayer
    val userId by settings.userId.collectAsState(initial = "")

    var isMenuExpanded by remember { mutableStateOf(false) }
    var isSubMenuExpanded by remember { mutableStateOf(false) }
    
    var isLiked by remember(track) { mutableStateOf(false) }

    LaunchedEffect(track.id, userId) {
        if (userId.isNotEmpty()) {
            try {
                val liked = api.fetchLikedTracks(userId)
                isLiked = liked.any { it.id == track.id }
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onPlay() }
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = track.getArtworkURL(context, backendURL) ?: "https://pm.standrise.net/icons/default_cover.png",
            contentDescription = track.title,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(4.dp)),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = track.artist,
                    color = MutedText,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                track.source?.let { src ->
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "·",
                        color = MutedText,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = src.label,
                        color = MutedText,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = track.durationLabel,
            color = MutedText,
            fontSize = 12.sp
        )
        Spacer(modifier = Modifier.width(4.dp))
        Box {
            IconButton(onClick = { isMenuExpanded = true }) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Меню",
                    tint = Color.White
                )
            }

            DropdownMenu(
                expanded = isMenuExpanded,
                onDismissRequest = { isMenuExpanded = false },
                modifier = Modifier.background(DarkSurface)
            ) {
                // Like / Unlike
                DropdownMenuItem(
                    text = { Text(if (isLiked) "Удалить из любимых" else "В любимые", color = Color.White) },
                    onClick = {
                        isMenuExpanded = false
                        scope.launch {
                            if (userId.isNotEmpty()) {
                                try {
                                    api.toggleLikeOnServer(userId, track)
                                    isLiked = !isLiked
                                    onPlaylistChanged?.invoke()
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                )

                // Add to Playlist
                if (playlists.isNotEmpty()) {
                    DropdownMenuItem(
                        text = { Text("Добавить в плейлист...", color = Color.White) },
                        onClick = {
                            isMenuExpanded = false
                            isSubMenuExpanded = true
                        }
                    )
                }

                // Remove from Playlist (if currentPlaylistId is set and is user playlist)
                if (currentPlaylistId != null) {
                    DropdownMenuItem(
                        text = { Text("Удалить из плейлиста", color = Color(0xFFFF453A)) },
                        onClick = {
                            isMenuExpanded = false
                            scope.launch {
                                try {
                                    api.removeTrackFromPlaylist(currentPlaylistId, track.id)
                                    Toast.makeText(context, "Удалено", Toast.LENGTH_SHORT).show()
                                    onPlaylistChanged?.invoke()
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    )
                }

                // Find on other services
                if (track.source == TrackSource.yandex || track.source == TrackSource.soundcloud) {
                    DropdownMenuItem(
                        text = { Text("Найти на Spotify", color = Color.White) },
                        onClick = {
                            isMenuExpanded = false
                            player.findAndReplace(track, TrackSource.spotify) { msg ->
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                onPlaylistChanged?.invoke()
                            }
                        }
                    )
                }
                if (track.source == TrackSource.spotify || track.source == TrackSource.soundcloud) {
                    DropdownMenuItem(
                        text = { Text("Найти в Яндекс.Музыке", color = Color.White) },
                        onClick = {
                            isMenuExpanded = false
                            player.findAndReplace(track, TrackSource.yandex) { msg ->
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                onPlaylistChanged?.invoke()
                            }
                        }
                    )
                }
            }

            // Submenu Dialog/Dropdown for selecting playlist to add to
            if (isSubMenuExpanded) {
                DropdownMenu(
                    expanded = isSubMenuExpanded,
                    onDismissRequest = { isSubMenuExpanded = false },
                    modifier = Modifier.background(DarkSurface)
                ) {
                    playlists.forEach { pl ->
                        DropdownMenuItem(
                            text = { Text(pl.title, color = Color.White) },
                            onClick = {
                                isSubMenuExpanded = false
                                scope.launch {
                                    try {
                                        api.addTrackToPlaylist(pl.id, track)
                                        Toast.makeText(context, "Добавлено в ${pl.title}", Toast.LENGTH_SHORT).show()
                                        onPlaylistChanged?.invoke()
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

// --- LIBRARY SCREEN ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    api: APIClient,
    player: AudioPlayer,
    settings: SettingsStore,
    onAlbumClick: (Album) -> Unit
) {
    var likedTracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    var playlists by remember { mutableStateOf<List<Album>>(emptyList()) }
    var activeTab by remember { mutableStateOf("favorites") } // "favorites" or "playlists"
    var isLoading by remember { mutableStateOf(true) }
    
    var isShowingCreateDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }
    var newPlaylistDescription by remember { mutableStateOf("") }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val backendURL by settings.backendURL.collectAsState(initial = SettingsStore.DEFAULT_BACKEND_URL)

    fun loadData() {
        isLoading = true
        scope.launch {
            try {
                val userId = settings.getUserId()
                if (userId.isNotEmpty()) {
                    likedTracks = api.fetchLikedTracks(userId)
                    playlists = api.fetchPlaylists(userId).map { it.toAlbum }
                }
            } catch (e: Exception) {
                Log.e("LibraryScreen", "Error: ${e.message}")
            } finally {
                isLoading = false
            }
        }
    }

    val userId by settings.userId.collectAsState(initial = "")

    LaunchedEffect(userId) {
        if (userId.isNotEmpty()) {
            loadData()
        } else {
            likedTracks = emptyList()
            playlists = emptyList()
            isLoading = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Медиатека",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            // Show Plus Button if in Playlists tab and logged in
            if (activeTab == "playlists") {
                IconButton(
                    onClick = { isShowingCreateDialog = true },
                    modifier = Modifier
                        .size(38.dp)
                        .background(Color.White.copy(alpha = 0.08f), androidx.compose.foundation.shape.CircleShape)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Создать плейлист", tint = Color.White)
                }
            }
        }

        // Tab Selector (iOS-style Capsule Picker)
        Row(
            modifier = Modifier
                .wrapContentWidth()
                .padding(bottom = 16.dp)
                .background(Color.White.copy(alpha = 0.06f), RoundedCornerShape(20.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.Start
        ) {
            val tabs = listOf(
                "favorites" to "Избранное",
                "playlists" to "Плейлисты"
            )
            tabs.forEach { (tabId, tabName) ->
                val isSelected = activeTab == tabId
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (isSelected) Color.White else Color.Transparent)
                        .clickable { activeTab = tabId }
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = tabName,
                        color = if (isSelected) Color.Black else Color.White.copy(alpha = 0.7f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Content
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color.White)
            }
        } else {
            if (activeTab == "favorites") {
                if (likedTracks.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("У вас пока нет любимых треков", color = MutedText)
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(likedTracks) { track ->
                            TrackRow(
                                track = track,
                                backendURL = backendURL,
                                playlists = playlists,
                                onPlaylistChanged = { loadData() },
                                onPlay = {
                                    player.play(likedTracks, likedTracks.indexOf(track))
                                }
                            )
                        }
                    }
                }
            } else {
                if (playlists.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("У вас пока нет плейлистов", color = MutedText)
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(playlists) { playlist ->
                            Box(modifier = Modifier.fillMaxWidth()) {
                                AlbumGridItem(album = playlist, backendURL = backendURL) {
                                    scope.launch {
                                        try {
                                            val tracks = api.playlistTracks(playlist.id, "other")
                                            onAlbumClick(playlist.copy(tracks = tracks))
                                        } catch (e: Exception) {
                                            onAlbumClick(playlist)
                                        }
                                    }
                                }
                                
                                // Delete button overlay on the card
                                IconButton(
                                    onClick = {
                                        scope.launch {
                                            try {
                                                val success = api.deletePlaylist(playlist.id)
                                                if (success) {
                                                    Toast.makeText(context, "Плейлист удален", Toast.LENGTH_SHORT).show()
                                                    loadData()
                                                }
                                            } catch (e: Exception) {
                                                Toast.makeText(context, "Ошибка удаления: ${e.message}", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    },
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(4.dp)
                                        .size(28.dp)
                                        .background(Color.Black.copy(alpha = 0.6f), androidx.compose.foundation.shape.CircleShape)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Удалить",
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Create Playlist Dialog
    if (isShowingCreateDialog) {
        AlertDialog(
            onDismissRequest = { isShowingCreateDialog = false },
            title = { Text("Новый плейлист", color = Color.White) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = newPlaylistName,
                        onValueChange = { newPlaylistName = it },
                        label = { Text("Название") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                    OutlinedTextField(
                        value = newPlaylistDescription,
                        onValueChange = { newPlaylistDescription = it },
                        label = { Text("Описание (необязательно)") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val name = newPlaylistName.trim()
                        if (name.isNotEmpty()) {
                            scope.launch {
                                try {
                                    val userId = settings.getUserId()
                                    api.createPlaylist(userId, name, newPlaylistDescription.trim().ifEmpty { null })
                                    isShowingCreateDialog = false
                                    newPlaylistName = ""
                                    newPlaylistDescription = ""
                                    loadData()
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)
                ) {
                    Text("Создать")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    isShowingCreateDialog = false
                    newPlaylistName = ""
                    newPlaylistDescription = ""
                }) {
                    Text("Отмена", color = Color.White)
                }
            },
            containerColor = DarkSurface
        )
    }
}

// --- SETTINGS SCREEN ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: SettingsStore,
    onLogout: () -> Unit
) {
    var backendURL by remember { mutableStateOf("") }
    var yandexToken by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        backendURL = settings.getBackendURL()
        yandexToken = settings.getYandexToken()
        username = settings.getUsername()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Настройки",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        if (username.isNotEmpty()) {
            Text("Вошел как: $username", color = Color.White, fontWeight = FontWeight.SemiBold)
        }

        OutlinedTextField(
            value = backendURL,
            onValueChange = { backendURL = it },
            label = { Text("Адрес сервера (Backend URL)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            )
        )

        OutlinedTextField(
            value = yandexToken,
            onValueChange = { yandexToken = it },
            label = { Text("Токен Яндекс.Музыки") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            )
        )

        Button(
            onClick = {
                scope.launch {
                    settings.setBackendURL(backendURL)
                    settings.setYandexToken(yandexToken)
                    Toast.makeText(context, "Настройки сохранены", Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)
        ) {
            Text("Сохранить")
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = {
                scope.launch {
                    settings.logout()
                    onLogout()
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF453A), contentColor = Color.White)
        ) {
            Text("Выйти из аккаунта")
        }
    }
}
