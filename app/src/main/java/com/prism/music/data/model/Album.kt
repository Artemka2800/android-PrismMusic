package com.prism.music.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Album(
    val id: String,
    val title: String,
    val artist: String,
    val year: Int? = null,
    val cover: String? = null,
    val source: TrackSource? = null,
    val tracks: List<Track>? = null
) {
    fun getArtworkURL(backendURL: String): String? {
        return proxyArtworkURL(cover, backendURL)
    }
}
