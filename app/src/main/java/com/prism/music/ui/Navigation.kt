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

    Scaffold(
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

                    // Navigation BottomBar
                    NavigationBar(
                        containerColor = DarkSurface,
                        contentColor = Color.White
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
                                    selectedIconColor = Color.Black,
                                    selectedTextColor = Color.White,
                                    unselectedIconColor = MutedText,
                                    unselectedTextColor = MutedText,
                                    indicatorColor = Color.White
                                )
                            )
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
                    LibraryScreen(api, player, settings)
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
                        AlbumDetailView(album = album, settings = settings, player = player) {
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
                imageVector = if (isPlaying) Icons.Default.Close else Icons.Default.PlayArrow, // Close represents Pause in mini player
                contentDescription = "Воспроизведение",
                tint = Color.White
            )
        }
        IconButton(onClick = onNextClick) {
            Icon(
                imageVector = Icons.Default.ArrowForward,
                contentDescription = "Вперед",
                tint = Color.White
            )
        }
    }
}

// --- ALBUM DETAIL VIEW ---
@Composable
fun AlbumDetailView(
    album: Album,
    settings: SettingsStore,
    player: AudioPlayer,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val backendURL by settings.backendURL.collectAsState(initial = SettingsStore.DEFAULT_BACKEND_URL)
    val tracks = album.tracks ?: emptyList()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Назад", tint = Color.White)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text("Детали", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
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
                        model = album.getArtworkURL(backendURL) ?: "https://pm.standrise.net/icons/default_cover.png",
                        contentDescription = album.title,
                        modifier = Modifier
                            .size(100.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(album.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        Text(album.artist, color = MutedText, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        if (tracks.isNotEmpty()) {
                            Button(
                                onClick = { player.play(tracks, 0) },
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
            if (tracks.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(top = 32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color.White)
                    }
                }
            } else {
                items(tracks) { track ->
                    TrackRow(track = track, backendURL = backendURL) {
                        player.play(tracks, tracks.indexOf(track))
                    }
                }
            }
        }
    }
}
