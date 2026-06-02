package com.prism.music.ui.screens

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
import com.prism.music.audio.AudioPlayer
import com.prism.music.data.model.Album
import com.prism.music.data.model.Track
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
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
                    TrackRow(track = track, backendURL = backendURL) {
                        player.play(results, results.indexOf(track))
                    }
                }
            }
        }
    }
}

@Composable
fun TrackRow(track: Track, backendURL: String, onClick: () -> Unit) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
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
            Text(
                text = track.artist,
                color = MutedText,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = track.durationLabel,
            color = MutedText,
            fontSize = 12.sp
        )
    }
}

// --- LIBRARY SCREEN ---
@Composable
fun LibraryScreen(
    api: APIClient,
    player: AudioPlayer,
    settings: SettingsStore
) {
    var likedTracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    val backendURL by settings.backendURL.collectAsState(initial = SettingsStore.DEFAULT_BACKEND_URL)

    LaunchedEffect(Unit) {
        scope.launch {
            try {
                val userId = settings.getUserId()
                if (userId.isNotEmpty()) {
                    likedTracks = api.fetchLikedTracks(userId)
                }
            } catch (e: Exception) {
                Log.e("LibraryScreen", "Error: ${e.message}")
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
            text = "Любимые треки",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color.White)
            }
        } else if (likedTracks.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("У вас пока нет любимых треков", color = MutedText)
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(likedTracks) { track ->
                    TrackRow(track = track, backendURL = backendURL) {
                        player.play(likedTracks, likedTracks.indexOf(track))
                    }
                }
            }
        }
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
            .background(DarkBackground)
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
