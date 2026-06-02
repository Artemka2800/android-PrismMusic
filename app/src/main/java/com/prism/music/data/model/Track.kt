package com.prism.music.data.model

import android.content.Context
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.decodeStructure
import kotlinx.serialization.encoding.encodeStructure
import java.io.File

@Serializable
enum class TrackSource(val value: String, val label: String) {
    yandex("yandex", "Я.Музыка"),
    soundcloud("soundcloud", "SoundCloud"),
    spotify("spotify", "Spotify"),
    other("other", "Другое");

    companion object {
        fun fromString(source: String?): TrackSource {
            return entries.firstOrNull { it.value == source?.lowercase() } ?: other
        }
    }
}

@Serializable(with = TrackSerializer::class)
data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val album: String? = null,
    val durationSeconds: Double? = null,
    val cover: String? = null,
    val streamURL: String? = null,
    val source: TrackSource? = null
) {
    // Computes artwork URL. If local file exists, returns local URI. Else proxies SoundCloud artwork.
    fun getArtworkURL(context: Context, backendURL: String): String? {
        val safeId = id.replace(":", "_")
        val localCover = File(context.filesDir, "PrismDownloads/${safeId}_cover.jpg")
        if (localCover.exists()) {
            return localCover.absolutePath
        }
        return proxyArtworkURL(cover, backendURL)
    }

    val durationLabel: String
        get() {
            val d = durationSeconds
            if (d == null || !d.isFinite() || d <= 0) return "—"
            val minutes = d.toInt() / 60
            val seconds = d.toInt() % 60
            return String.format("%d:%02d", minutes, seconds)
        }
}

// Proxies SoundCloud cover art URL through Next.js proxy endpoint to bypass CORS and geo-blocks.
fun proxyArtworkURL(originalURL: String?, backendURL: String): String? {
    if (originalURL == null) return null
    if (originalURL.contains("sndcdn.com")) {
        val cleanBackend = backendURL.trim().removeSuffix("/")
        return "$cleanBackend/api/music/artwork?url=${android.net.Uri.encode(originalURL)}"
    }
    return originalURL
}

// Custom serializer to handle backend naming variations like cover/coverUrl and url/audioUrl
object TrackSerializer : KSerializer<Track> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("Track") {
        element<String>("id")
        element<String>("title")
        element<String>("artist")
        element<String?>("album", isOptional = true)
        element<Double?>("duration", isOptional = true)
        element<String?>("cover", isOptional = true)
        element<String?>("coverUrl", isOptional = true)
        element<String?>("url", isOptional = true)
        element<String?>("audioUrl", isOptional = true)
        element<TrackSource?>("source", isOptional = true)
    }

    override fun deserialize(decoder: Decoder): Track = decoder.decodeStructure(descriptor) {
        var id = ""
        var title = ""
        var artist = ""
        var album: String? = null
        var durationSeconds: Double? = null
        var cover: String? = null
        var coverUrl: String? = null
        var url: String? = null
        var audioUrl: String? = null
        var source: TrackSource? = null

        while (true) {
            when (val index = decodeElementIndex(descriptor)) {
                CompositeDecoder.DECODE_DONE -> break
                0 -> id = decodeStringElement(descriptor, index)
                1 -> title = decodeStringElement(descriptor, index)
                2 -> artist = decodeStringElement(descriptor, index)
                3 -> album = decodeNullableSerializableElement(descriptor, index, kotlinx.serialization.serializer<String?>())
                4 -> durationSeconds = decodeNullableSerializableElement(descriptor, index, kotlinx.serialization.serializer<Double?>())
                5 -> cover = decodeNullableSerializableElement(descriptor, index, kotlinx.serialization.serializer<String?>())
                6 -> coverUrl = decodeNullableSerializableElement(descriptor, index, kotlinx.serialization.serializer<String?>())
                7 -> url = decodeNullableSerializableElement(descriptor, index, kotlinx.serialization.serializer<String?>())
                8 -> audioUrl = decodeNullableSerializableElement(descriptor, index, kotlinx.serialization.serializer<String?>())
                9 -> source = decodeNullableSerializableElement(descriptor, index, kotlinx.serialization.serializer<TrackSource?>())
                else -> throw kotlinx.serialization.SerializationException("Unknown index $index")
            }
        }

        val finalCover = cover ?: coverUrl
        val finalUrl = url ?: audioUrl

        Track(
            id = id,
            title = title,
            artist = artist,
            album = album,
            durationSeconds = durationSeconds,
            cover = finalCover,
            streamURL = finalUrl,
            source = source
        )
    }

    override fun serialize(encoder: Encoder, value: Track) = encoder.encodeStructure(descriptor) {
        encodeStringElement(descriptor, 0, value.id)
        encodeStringElement(descriptor, 1, value.title)
        encodeStringElement(descriptor, 2, value.artist)
        encodeNullableSerializableElement(descriptor, 3, kotlinx.serialization.serializer<String?>(), value.album)
        encodeNullableSerializableElement(descriptor, 4, kotlinx.serialization.serializer<Double?>(), value.durationSeconds)
        encodeNullableSerializableElement(descriptor, 5, kotlinx.serialization.serializer<String?>(), value.cover)
        encodeNullableSerializableElement(descriptor, 6, kotlinx.serialization.serializer<String?>(), null) // coverUrl fallback
        encodeNullableSerializableElement(descriptor, 7, kotlinx.serialization.serializer<String?>(), value.streamURL)
        encodeNullableSerializableElement(descriptor, 8, kotlinx.serialization.serializer<String?>(), null) // audioUrl fallback
        encodeNullableSerializableElement(descriptor, 9, kotlinx.serialization.serializer<TrackSource?>(), value.source)
    }
}
