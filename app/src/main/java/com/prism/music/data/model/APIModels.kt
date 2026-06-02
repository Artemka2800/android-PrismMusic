package com.prism.music.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement

@Serializable
data class SearchResponse(
    val tracks: List<Track> = emptyList(),
    val albums: List<Album> = emptyList(),
    val playlists: List<SearchPlaylistDTO> = emptyList()
) {
    // Helper to map search playlists to Album models
    val mappedAlbums: List<Album>
        get() {
            if (playlists.isNotEmpty()) {
                return playlists.map { p ->
                    Album(
                        id = p.id,
                        title = p.name ?: "Плейлист",
                        artist = p.description ?: "SoundCloud",
                        year = null,
                        cover = p.coverUrl,
                        source = TrackSource.fromString(p.source ?: "soundcloud"),
                        tracks = null
                    )
                }
            }
            return albums
        }
}

@Serializable
data class SearchPlaylistDTO(
    val id: String,
    val name: String? = null,
    val coverUrl: String? = null,
    val description: String? = null,
    val source: String? = null
)

@Serializable
data class RecommendationsResponse(
    val title: String? = null,
    val playlists: List<PlaylistDTO> = emptyList(),
    val albums: List<Album> = emptyList()
) {
    // Helper to map recommendation playlists to Album models
    val mappedAlbums: List<Album>
        get() {
            if (playlists.isNotEmpty()) {
                return playlists.map { p ->
                    Album(
                        id = p.id,
                        title = p.name,
                        artist = p.description ?: p.source ?: "SoundCloud",
                        year = null,
                        cover = p.coverUrl,
                        source = TrackSource.fromString(p.source ?: "soundcloud"),
                        tracks = null
                    )
                }
            }
            return albums
        }
}

@Serializable
data class PlaylistDTO(
    val id: String,
    val name: String,
    val coverUrl: String? = null,
    val description: String? = null,
    val source: String? = null,
    val isSystem: Boolean? = null
)

@Serializable
data class LyricsResponse(
    val lyrics: String? = null,
    val source: String? = null
)

@Serializable
data class PlaylistDetailResponse(
    val id: String? = null,
    val name: String? = null,
    val coverUrl: String? = null,
    val description: String? = null,
    val tracks: List<Track> = emptyList(),
    val source: String? = null
)

@Serializable
data class YandexImportResponse(
    val importedLikes: List<Track> = emptyList()
)

@Serializable
data class UserResponse(
    val id: String,
    val username: String,
    val token: String? = null, // Yandex token if configured
    val avatarUrl: String? = null,
    val role: String? = null,
    val createdAt: String? = null
)

@Serializable
data class UserPlaylistDTO(
    val id: String,
    val name: String,
    val description: String? = null,
    val coverUrl: String? = null,
    val isSystem: Boolean? = null,
    val tracks: List<Track>? = null
) {
    fun toAlbum(): Album = Album(
        id = id,
        title = name,
        artist = description ?: "Мой плейлист",
        year = null,
        cover = coverUrl,
        source = TrackSource.other,
        tracks = tracks
    )
}

@Serializable
data class PlaylistSuccessResponse(
    val success: Boolean
)
