package com.prism.music.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import coil.compose.AsyncImage
import com.prism.music.audio.AudioPlayer
import com.prism.music.data.model.Album
import com.prism.music.data.model.Track
import com.prism.music.data.network.APIClient
import com.prism.music.data.store.SettingsStore
import com.prism.music.ui.screens.*
import com.prism.music.ui.theme.DarkBackground
import com.prism.music.ui.theme.DarkSurface
import com.prism.music.ui.theme.MutedText
import kotlinx.coroutines.launch
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import com.prism.music.data.model.TrackSource
import com.prism.music.data.model.proxyArtworkURL
import com.prism.music.ui.components.ImmersiveBackground
import android.widget.Toast
import android.util.Log
import androidx.compose.ui.text.style.TextAlign

sealed class Screen(val route: String, val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector?) {
    object Login : Screen("login", "Login", null)
    object Home : Screen("home", "Обзор", Icons.Default.Home)
    object Search : Screen("search", "Поиск", Icons.Default.Search)
    object Library : Screen("library", "Медиатека", Icons.Default.List) // List instead of LibraryMusic
    object Settings : Screen("settings", "Настройки", Icons.Default.Settings)
    object AlbumDetail : Screen("album_detail", "Детали", null)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavigation(
    api: APIClient,
    settings: SettingsStore,
    player: AudioPlayer
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val userId by settings.userId.collectAsState(initial = "")
    val currentTrack by player.currentTrack.collectAsState()
    val isPlaying by player.isPlaying.collectAsState()

    var activeDetailedAlbum by remember { mutableStateOf<Album?>(null) }
    var isPlayerExpanded by remember { mutableStateOf(false) }

    val startDest = if (userId.isEmpty()) Screen.Login.route else Screen.Home.route

    // Synchronize start destination with login status changes
    LaunchedEffect(userId) {
        if (userId.isEmpty() && currentRoute != Screen.Login.route) {
            navController.navigate(Screen.Login.route) {
                popUpTo(0) { inclusive = true }
            }
        } else if (userId.isNotEmpty() && currentRoute == Screen.Login.route) {
            navController.navigate(Screen.Home.route) {
                popUpTo(0) { inclusive = true }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        ImmersiveBackground(player = player, settings = settings)

        Scaffold(
            containerColor = Color.Transparent,
            bottomBar = {
                if (currentRoute != Screen.Login.route) {
                    Column {
                        // MiniPlayer overlay
                        AnimatedVisibility(
                            visible = currentTrack != null,
                            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                        ) {
                            currentTrack?.let { track ->
                                MiniPlayer(
                                    track = track,
                                    isPlaying = isPlaying,
                                    settings = settings,
                                    onPlayPauseClick = { player.togglePlay() },
                                    onNextClick = { player.next() },
                                    onClick = { isPlayerExpanded = true }
                                )
                            }
                        }

                        // Navigation BottomBar (iOS Glassmorphism style)
                        Column {
                            HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 0.5.dp)
                            NavigationBar(
                                containerColor = Color.Black.copy(alpha = 0.5f),
                                contentColor = Color.White,
                                tonalElevation = 0.dp,
                                modifier = Modifier.background(Color.Transparent)
                            ) {
                                val items = listOf(Screen.Home, Screen.Search, Screen.Library, Screen.Settings)
                                items.forEach { item ->
                                    NavigationBarItem(
                                        icon = { Icon(item.icon!!, contentDescription = item.title) },
                                        label = { Text(item.title) },
                                        selected = currentRoute == item.route,
                                        onClick = {
                                            navController.navigate(item.route) {
                                                popUpTo(navController.graph.findStartDestination().id) {
                                                    saveState = true
                                                }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        },
                                        colors = NavigationBarItemDefaults.colors(
                                            selectedIconColor = Color.White,
                                            selectedTextColor = Color.White,
                                            unselectedIconColor = MutedText.copy(alpha = 0.6f),
                                            unselectedTextColor = MutedText.copy(alpha = 0.6f),
                                            indicatorColor = Color.Transparent // Removes the Material 3 oval background pill
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                NavHost(
                    navController = navController,
                    startDestination = startDest
                ) {
                    composable(Screen.Login.route) {
                        LoginScreen(api, settings) {
                            navController.navigate(Screen.Home.route) {
                                popUpTo(Screen.Login.route) { inclusive = true }
                            }
                        }
                    }
                    composable(Screen.Home.route) {
                        HomeScreen(api, settings, player) { album ->
                            activeDetailedAlbum = album
                            navController.navigate(Screen.AlbumDetail.route)
                        }
                    }
                    composable(Screen.Search.route) {
                        SearchScreen(api, player, settings)
                    }
                    composable(Screen.Library.route) {
                        LibraryScreen(api, player, settings) { album ->
                            activeDetailedAlbum = album
                            navController.navigate(Screen.AlbumDetail.route)
                        }
                    }
                    composable(Screen.Settings.route) {
                        SettingsScreen(settings) {
                            navController.navigate(Screen.Login.route) {
                                popUpTo(0) { inclusive = true }
                            }
                        }
                    }
                    composable(Screen.AlbumDetail.route) {
                        activeDetailedAlbum?.let { album ->
                            AlbumDetailView(album = album, settings = settings, api = api, player = player) {
                                navController.popBackStack()
                            }
                        }
                    }
                }
            }
        }

        // Modal Expandable Player Screen
        AnimatedVisibility(
            visible = isPlayerExpanded,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
        ) {
            PlayerScreen(player = player, settings = settings) {
                isPlayerExpanded = false
            }
        }
    }
}

// --- MINI PLAYER ---
@Composable
fun MiniPlayer(
    track: Track,
    isPlaying: Boolean,
    settings: SettingsStore,
    onPlayPauseClick: () -> Unit,
    onNextClick: () -> Unit,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val backendURL by settings.backendURL.collectAsState(initial = SettingsStore.DEFAULT_BACKEND_URL)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(DarkSurface)
            .clickable { onClick() }
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = track.getArtworkURL(context, backendURL) ?: "https://pm.standrise.net/icons/default_cover.png",
            contentDescription = track.title,
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(4.dp)),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = track.artist,
                color = MutedText,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        IconButton(onClick = onPlayPauseClick) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = "Воспроизведение",
                tint = Color.White
            )
        }
        IconButton(onClick = onNextClick) {
            Icon(
                imageVector = Icons.Default.SkipNext,
                contentDescription = "Вперед",
                tint = Color.White
            )
        }
    }
}

// --- ALBUM DETAIL VIEW ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumDetailView(
    album: Album,
    settings: SettingsStore,
    api: APIClient,
    player: AudioPlayer,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val backendURL by settings.backendURL.collectAsState(initial = SettingsStore.DEFAULT_BACKEND_URL)
    val userId by settings.userId.collectAsState(initial = "")
    val scope = rememberCoroutineScope()

    var tracks by remember { mutableStateOf<List<Track>?>(album.tracks) }
    var isLoading by remember { mutableStateOf(album.tracks == null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Dialog state for editing playlist properties
    var isEditing by remember { mutableStateOf(false) }
    var editName by remember { mutableStateOf(album.title) }
    var editDesc by remember { mutableStateOf(album.artist) }
    var editCover by remember { mutableStateOf(album.cover ?: "") }
    var isSaving by remember { mutableStateOf(false) }

    // Playlist details that can change dynamically on edit
    var playlistTitle by remember { mutableStateOf(album.title) }
    var playlistDescription by remember { mutableStateOf(album.artist) }
    var playlistCover by remember { mutableStateOf(album.cover ?: "") }

    LaunchedEffect(album.id) {
        if (album.tracks == null) {
            isLoading = true
            errorMessage = null
            try {
                val fetched = if (album.source == TrackSource.other) {
                    if (userId.isNotEmpty()) {
                        api.fetchUserPlaylistTracks(userId, album.id)
                    } else {
                        emptyList()
                    }
                } else {
                    api.playlistTracks(album.id, album.source?.value ?: "soundcloud")
                }
                tracks = fetched
            } catch (e: Exception) {
                errorMessage = "Не удалось загрузить треки"
                Log.e("AlbumDetailView", "Error: ${e.message}")
            } finally {
                isLoading = false
            }
        }
    }

    var userPlaylists by remember { mutableStateOf<List<Album>>(emptyList()) }
    LaunchedEffect(userId) {
        if (userId.isNotEmpty()) {
            try {
                userPlaylists = api.fetchPlaylists(userId).map { it.toAlbum() }
            } catch (e: Exception) {
                Log.e("AlbumDetailView", "Failed to fetch playlists: ${e.message}")
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Blurred artwork backdrop
        if (playlistCover.isNotEmpty()) {
            AsyncImage(
                model = proxyArtworkURL(playlistCover, backendURL),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(60.dp),
                contentScale = ContentScale.Crop,
                alpha = 0.45f
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Black.copy(alpha = 0.45f),
                                Color.Black.copy(alpha = 0.85f)
                            )
                        )
                    )
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                DarkBackground,
                                Color(0xFF0F0F12)
                            )
                        )
                    )
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
        ) {
            // Top bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад", tint = Color.White)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Детали", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }

                if (album.source == TrackSource.other) {
                    IconButton(onClick = { isEditing = true }) {
                        Icon(Icons.Default.Edit, contentDescription = "Редактировать", tint = Color.White)
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Album Metadata Header
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 24.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = proxyArtworkURL(playlistCover.ifEmpty { null }, backendURL) ?: "https://pm.standrise.net/icons/default_cover.png",
                            contentDescription = playlistTitle,
                            modifier = Modifier
                                .size(100.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(playlistTitle, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                            Text(playlistDescription, color = MutedText, fontSize = 14.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            if (tracks != null && tracks!!.isNotEmpty()) {
                                Button(
                                    onClick = { player.play(tracks!!, 0) },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Слушать")
                                }
                            }
                        }
                    }
                }

                // Track list
                if (isLoading) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().padding(top = 32.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = Color.White)
                        }
                    }
                } else if (errorMessage != null) {
                    item {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(top = 40.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = Color.Yellow, modifier = Modifier.size(36.dp))
                            Text(errorMessage!!, color = MutedText, textAlign = TextAlign.Center)
                            Button(
                                onClick = {
                                    scope.launch {
                                        isLoading = true
                                        errorMessage = null
                                        try {
                                            val fetched = if (album.source == TrackSource.other) {
                                                api.fetchUserPlaylistTracks(userId, album.id)
                                            } else {
                                                api.playlistTracks(album.id, album.source?.value ?: "soundcloud")
                                            }
                                            tracks = fetched
                                        } catch (e: Exception) {
                                            errorMessage = "Не удалось загрузить треки"
                                        } finally {
                                            isLoading = false
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)
                            ) {
                                Text("Повторить")
                            }
                        }
                    }
                } else if (tracks == null || tracks!!.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(top = 60.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.List,
                                contentDescription = null,
                                tint = MutedText,
                                modifier = Modifier.size(48.dp)
                            )
                            Text("Нет треков", color = MutedText, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    items(tracks!!) { track ->
                        TrackRow(
                            track = track,
                            backendURL = backendURL,
                            playlists = userPlaylists,
                            currentPlaylistId = album.id,
                            onPlaylistChanged = {
                                // Reload tracks
                                scope.launch {
                                    try {
                                        tracks = if (album.source == TrackSource.other) {
                                            api.fetchUserPlaylistTracks(userId, album.id)
                                        } else {
                                            api.playlistTracks(album.id, album.source?.value ?: "soundcloud")
                                        }
                                    } catch (e: Exception) {
                                        Log.e("AlbumDetailView", "Reload failed: ${e.message}")
                                    }
                                }
                            },
                            onPlay = {
                                player.play(tracks!!, tracks!!.indexOf(track))
                            }
                        )
                    }
                }
            }
        }
    }

    // Edit Playlist Dialog
    if (isEditing) {
        AlertDialog(
            onDismissRequest = { isEditing = false },
            title = { Text("Редактировать плейлист", color = Color.White) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text("Название") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                    OutlinedTextField(
                        value = editDesc,
                        onValueChange = { editDesc = it },
                        label = { Text("Описание") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                    OutlinedTextField(
                        value = editCover,
                        onValueChange = { editCover = it },
                        label = { Text("Ссылка на обложку (необязательно)") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                }
            },
            confirmButton = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = {
                            val name = editName.trim()
                            if (name.isNotEmpty()) {
                                isSaving = true
                                scope.launch {
                                    try {
                                        val updated = api.updatePlaylist(
                                            playlistId = album.id,
                                            name = name,
                                            description = editDesc.trim().ifEmpty { null },
                                            coverUrl = editCover.trim().ifEmpty { null }
                                        )
                                        playlistTitle = updated.name
                                        playlistDescription = updated.description ?: "Мой плейлист"
                                        playlistCover = updated.coverUrl ?: ""
                                        isEditing = false
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
                                    } finally {
                                        isSaving = false
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)
                    ) {
                        if (isSaving) CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(20.dp))
                        else Text("Сохранить")
                    }

                    Button(
                        onClick = {
                            scope.launch {
                                try {
                                    val success = api.deletePlaylist(album.id)
                                    if (success) {
                                        isEditing = false
                                        onBack()
                                    }
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF453A), contentColor = Color.White)
                    ) {
                        Text("Удалить плейлист")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { isEditing = false },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.textButtonColors(contentColor = Color.White)
                ) {
                    Text("Отмена")
                }
            },
            containerColor = DarkSurface
        )
    }
}
