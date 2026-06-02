package com.prism.music.data.network

import android.content.Context
import android.net.Uri
import android.util.Log
import com.prism.music.data.model.*
import com.prism.music.data.store.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException

class ClientErrorException(message: String) : Exception(message)

class APIClient(private val context: Context, private val settings: SettingsStore) {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    private val client: OkHttpClient

    init {
        client = OkHttpClient.Builder()
            .addInterceptor(HostSelectionInterceptor(settings))
            .build()
    }

    // Custom OkHttp interceptor that applies headers (like x-yandex-token) and handles request routing
    private class HostSelectionInterceptor(private val settings: SettingsStore) : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            var request = chain.request()
            val builder = request.newBuilder()

            // Synchronously retrieve settings to apply headers
            val token = kotlinx.coroutines.runBlocking { settings.getYandexToken() }
            if (token.isNotEmpty()) {
                builder.addHeader("x-yandex-token", token)
            }
            builder.addHeader("Accept", "application/json")

            request = builder.build()
            return chain.proceed(request)
        }
    }

    // Dynamic failover method that rotates hosts upon connection timeouts or 5xx server issues
    private suspend fun <T> executeWithFailover(
        path: String,
        method: String = "GET",
        queryParams: Map<String, String> = emptyMap(),
        bodyJson: String? = null,
        headers: Map<String, String> = emptyMap(),
        decodeBlock: (String) -> T
    ): T = withContext(Dispatchers.IO) {
        var attempts = 0
        val hosts = SettingsStore.HOSTS
        val maxAttempts = hosts.size
        var lastException: Exception? = null

        while (attempts < maxAttempts) {
            val currentHost = settings.getBackendURL()
            val cleanHost = currentHost.trim().removeSuffix("/")
            val cleanPath = if (path.startsWith("/")) path else "/$path"
            
            // Build URL with query parameters
            val urlBuilder = Uri.parse("$cleanHost$cleanPath").buildUpon()
            queryParams.forEach { (key, value) ->
                urlBuilder.appendQueryParameter(key, value)
            }
            val requestUrl = urlBuilder.build().toString()

            val requestBuilder = Request.Builder().url(requestUrl)
            
            // Set HTTP method and body
            if (method == "POST" || method == "PUT" || method == "PATCH") {
                val mediaType = "application/json; charset=utf-8".toMediaType()
                val requestBody = (bodyJson ?: "{}").toRequestBody(mediaType)
                requestBuilder.method(method, requestBody)
            } else {
                requestBuilder.method(method, null)
            }

            // Custom headers
            headers.forEach { (key, value) ->
                requestBuilder.addHeader(key, value)
            }

            val request = requestBuilder.build()
            try {
                client.newCall(request).execute().use { response ->
                    if (response.code in 400..499) {
                        throw ClientErrorException("Client error: ${response.code} ${response.message}")
                    }
                    if (response.code >= 500) {
                        throw IOException("Server error: ${response.code}")
                    }
                    if (!response.isSuccessful) {
                        throw IOException("HTTP error: ${response.code} ${response.message}")
                    }
                    val bodyString = response.body?.string() ?: throw IOException("Empty response body")
                    return@withContext decodeBlock(bodyString)
                }
            } catch (e: ClientErrorException) {
                throw e
            } catch (e: Exception) {
                Log.w("APIClient", "Request failed on host $currentHost: ${e.message}. Retrying...")
                lastException = e
                rotateHost()
                attempts++
            }
        }
        throw lastException ?: IOException("Failed to execute request after $maxAttempts attempts")
    }

    suspend fun rotateHost() {
        val currentHost = settings.getBackendURL()
        val hosts = SettingsStore.HOSTS
        val nextHost: String
        val idx = hosts.indexOf(currentHost)
        nextHost = if (idx != -1) {
            val nextIdx = (idx + 1) % hosts.size
            hosts[nextIdx]
        } else {
            hosts[0]
        }
        settings.setBackendURL(nextHost)
        Log.i("APIClient", "Switched active host to: $nextHost")
    }

    // MARK: - Endpoints

    suspend fun search(query: String): SearchResponse {
        return executeWithFailover(
            path = "/api/music/search",
            queryParams = mapOf("q" to query)
        ) { body ->
            json.decodeFromString(SearchResponse.serializer(), body)
        }
    }

    suspend fun recommendations(): RecommendationsResponse {
        return executeWithFailover(
            path = "/api/music/recommendations"
        ) { body ->
            json.decodeFromString(RecommendationsResponse.serializer(), body)
        }
    }

    suspend fun lyrics(artist: String, title: String, id: String? = null, duration: Double? = null): LyricsResponse? {
        val params = mutableMapOf(
            "artist" to artist,
            "title" to title
        )
        id?.let { params["id"] = it }
        duration?.let { params["duration"] = it.toInt().toString() }

        return try {
            executeWithFailover(
                path = "/api/music/lyrics",
                queryParams = params
            ) { body ->
                json.decodeFromString(LyricsResponse.serializer(), body)
            }
        } catch (e: Exception) {
            Log.e("APIClient", "Failed to fetch lyrics: ${e.message}")
            null
        }
    }

    fun streamURL(track: Track, backendURL: String, yandexToken: String): String {
        val cleanBackend = backendURL.trim().removeSuffix("/")
        val urlBuilder = Uri.parse("$cleanBackend/api/music/stream").buildUpon()
            .appendQueryParameter("id", track.id)
            .appendQueryParameter("source", track.source?.value ?: "soundcloud")
        
        if (yandexToken.isNotEmpty()) {
            urlBuilder.appendQueryParameter("token", yandexToken)
        }
        return urlBuilder.build().toString()
    }

    suspend fun playlistTracks(id: String, source: String): List<Track> {
        return executeWithFailover(
            path = "/api/music/playlist",
            queryParams = mapOf("id" to id, "source" to source)
        ) { body ->
            val res = json.decodeFromString(PlaylistDetailResponse.serializer(), body)
            res.tracks
        }
    }

    suspend fun importYandexLikes(): YandexImportResponse {
        return executeWithFailover(
            path = "/api/music/yandex/import",
            method = "POST"
        ) { body ->
            json.decodeFromString(YandexImportResponse.serializer(), body)
        }
    }

    suspend fun login(username: String, password: String): UserResponse {
        val bodyStr = "{\"username\":\"$username\",\"password\":\"$password\"}"
        return executeWithFailover(
            path = "/api/auth/login",
            method = "POST",
            bodyJson = bodyStr
        ) { body ->
            json.decodeFromString(UserResponse.serializer(), body)
        }
    }

    suspend fun register(username: String, password: String): UserResponse {
        val bodyStr = "{\"username\":\"$username\",\"password\":\"$password\"}"
        return executeWithFailover(
            path = "/api/auth/register",
            method = "POST",
            bodyJson = bodyStr
        ) { body ->
            json.decodeFromString(UserResponse.serializer(), body)
        }
    }

    suspend fun fetchLikedTracks(userId: String): List<Track> {
        return executeWithFailover(
            path = "/api/library/likes",
            queryParams = mapOf("userId" to userId)
        ) { body ->
            json.decodeFromString<List<Track>>(body)
        }
    }

    suspend fun toggleLikeOnServer(userId: String, track: Track): Boolean {
        // Build JSON representation of track manually for the body
        val trackCover = track.cover ?: ""
        val trackDuration = track.durationSeconds?.toInt() ?: 0
        val trackSource = track.source?.value ?: "unknown"
        val bodyStr = """
            {
                "userId": "$userId",
                "track": {
                    "id": "${track.id}",
                    "title": "${track.title.replace("\"", "\\\"")}",
                    "artist": "${track.artist.replace("\"", "\\\"")}",
                    "coverUrl": "$trackCover",
                    "duration": $trackDuration,
                    "source": "$trackSource"
                }
            }
        """.trimIndent()

        @Serializable
        data class LikeToggleResponse(val liked: Boolean)

        return executeWithFailover(
            path = "/api/library/likes",
            method = "POST",
            bodyJson = bodyStr
        ) { body ->
            val res = json.decodeFromString<LikeToggleResponse>(body)
            res.liked
        }
    }

    suspend fun findTrack(title: String, artist: String, targetSource: String): List<Track> {
        val bodyStr = """
            {
                "title": "${title.replace("\"", "\\\"")}",
                "artist": "${artist.replace("\"", "\\\"")}",
                "targetSource": "$targetSource"
            }
        """.trimIndent()

        @Serializable
        data class FindResponse(val results: List<Track>)

        return executeWithFailover(
            path = "/api/music/find",
            method = "POST",
            bodyJson = bodyStr
        ) { body ->
            val res = json.decodeFromString<FindResponse>(body)
            res.results
        }
    }

    suspend fun replaceLikedTrack(userId: String, oldTrackId: String, newTrack: Track): Boolean {
        val trackCover = newTrack.cover ?: ""
        val trackDuration = newTrack.durationSeconds?.toInt() ?: 0
        val trackSource = newTrack.source?.value ?: "unknown"
        val bodyStr = """
            {
                "userId": "$userId",
                "oldTrackId": "$oldTrackId",
                "newTrack": {
                    "id": "${newTrack.id}",
                    "title": "${newTrack.title.replace("\"", "\\\"")}",
                    "artist": "${newTrack.artist.replace("\"", "\\\"")}",
                    "coverUrl": "$trackCover",
                    "duration": $trackDuration,
                    "source": "$trackSource"
                }
            }
        """.trimIndent()

        return executeWithFailover(
            path = "/api/library/likes",
            method = "PUT",
            bodyJson = bodyStr
        ) { body ->
            val res = json.decodeFromString<PlaylistSuccessResponse>(body)
            res.success
        }
    }

    suspend fun fetchPlaylists(userId: String): List<UserPlaylistDTO> {
        return executeWithFailover(
            path = "/api/library/playlists",
            queryParams = mapOf("userId" to userId)
        ) { body ->
            json.decodeFromString<List<UserPlaylistDTO>>(body)
        }
    }

    suspend fun createPlaylist(userId: String, name: String, description: String? = null): UserPlaylistDTO {
        val descStr = if (description != null) ",\"description\":\"${description.replace("\"", "\\\"")}\"" else ""
        val bodyStr = "{\"userId\":\"$userId\",\"name\":\"${name.replace("\"", "\\\"")}\"$descStr}"
        return executeWithFailover(
            path = "/api/library/playlists",
            method = "POST",
            bodyJson = bodyStr
        ) { body ->
            json.decodeFromString(UserPlaylistDTO.serializer(), body)
        }
    }

    suspend fun deletePlaylist(playlistId: String): Boolean {
        return executeWithFailover(
            path = "/api/library/playlists",
            method = "DELETE",
            queryParams = mapOf("id" to playlistId)
        ) { body ->
            val res = json.decodeFromString<PlaylistSuccessResponse>(body)
            res.success
        }
    }

    suspend fun addTrackToPlaylist(playlistId: String, track: Track): Boolean {
        val trackCover = track.cover ?: ""
        val trackDuration = track.durationSeconds?.toInt() ?: 0
        val trackSource = track.source?.value ?: "unknown"
        val bodyStr = """
            {
                "playlistId": "$playlistId",
                "track": {
                    "id": "${track.id}",
                    "title": "${track.title.replace("\"", "\\\"")}",
                    "artist": "${track.artist.replace("\"", "\\\"")}",
                    "coverUrl": "$trackCover",
                    "duration": $trackDuration,
                    "source": "$trackSource"
                }
            }
        """.trimIndent()

        return executeWithFailover(
            path = "/api/library/playlists/tracks",
            method = "POST",
            bodyJson = bodyStr
        ) { body ->
            body.contains("success") || body.contains("Track already")
        }
    }

    suspend fun removeTrackFromPlaylist(playlistId: String, trackId: String): Boolean {
        return executeWithFailover(
            path = "/api/library/playlists/tracks",
            method = "DELETE",
            queryParams = mapOf("playlistId" to playlistId, "trackId" to trackId)
        ) { body ->
            body.contains("success")
        }
    }

    suspend fun updatePlaylist(playlistId: String, name: String, description: String?, coverUrl: String?): UserPlaylistDTO {
        val descStr = if (description != null) ",\"description\":\"${description.replace("\"", "\\\"")}\"" else ""
        val coverStr = if (coverUrl != null) ",\"coverUrl\":\"${coverUrl.replace("\"", "\\\"")}\"" else ""
        val bodyStr = "{\"id\":\"$playlistId\",\"name\":\"${name.replace("\"", "\\\"")}\"$descStr$coverStr}"
        return executeWithFailover(
            path = "/api/library/playlists",
            method = "PATCH",
            bodyJson = bodyStr
        ) { body ->
            json.decodeFromString(UserPlaylistDTO.serializer(), body)
        }
    }

    suspend fun fetchUserPlaylistTracks(userId: String, playlistId: String): List<Track> {
        val allPlaylists = fetchPlaylists(userId)
        val matching = allPlaylists.firstOrNull { it.id == playlistId }
        return matching?.tracks ?: emptyList()
    }
}
