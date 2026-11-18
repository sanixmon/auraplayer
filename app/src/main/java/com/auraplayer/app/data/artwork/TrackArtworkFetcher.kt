package com.auraplayer.app.data.artwork

import android.media.MediaMetadataRetriever
import android.net.Uri
import coil.ImageLoader
import coil.decode.DataSource
import coil.decode.ImageSource
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.key.Keyer
import coil.request.Options
import com.auraplayer.app.data.model.Track
import okio.Buffer

data class TrackArtworkRequest(
    val trackUri: String,
    val albumArtUri: String?
)

fun Track.toArtworkRequest(): TrackArtworkRequest = TrackArtworkRequest(
    trackUri = uri,
    albumArtUri = albumArtUri
)

class TrackArtworkKeyer : Keyer<TrackArtworkRequest> {
    override fun key(data: TrackArtworkRequest, options: Options): String {
        return "aura_art:${data.trackUri}|${data.albumArtUri.orEmpty()}"
    }
}

/**
 * Loads track album art from MediaStore first, falling back to embedded ID3/Vorbis/MP4
 * artwork via MediaMetadataRetriever when MediaStore has no cached thumbnail.
 */
class TrackArtworkFetcher(
    private val data: TrackArtworkRequest,
    private val options: Options
) : Fetcher {

    override suspend fun fetch(): FetchResult? {
        val resolver = options.context.contentResolver

        // 1. Prefer embedded artwork inside the audio file itself so tracks in a shared
        // MediaStore folder/album bucket don't collide onto a stale cached thumbnail.
        if (!data.trackUri.startsWith("unavailable://")) {
            val retriever = MediaMetadataRetriever()
            val embeddedBytes = try {
                retriever.setDataSource(options.context, Uri.parse(data.trackUri))
                retriever.embeddedPicture
            } catch (_: Exception) {
                null
            } finally {
                runCatching { retriever.release() }
            }

            if (embeddedBytes != null && embeddedBytes.isNotEmpty()) {
                return SourceResult(
                    source = ImageSource(
                        source = Buffer().write(embeddedBytes),
                        context = options.context
                    ),
                    mimeType = null,
                    dataSource = DataSource.DISK
                )
            }
        }

        // 2. Fallback to MediaStore album art URI if the track has no embedded image
        val mediaStoreArt = data.albumArtUri
        if (!mediaStoreArt.isNullOrBlank()) {
            val bytes = runCatching {
                resolver.openInputStream(Uri.parse(mediaStoreArt))?.use { it.readBytes() }
            }.getOrNull()
            if (bytes != null && bytes.isNotEmpty()) {
                return SourceResult(
                    source = ImageSource(
                        source = Buffer().write(bytes),
                        context = options.context
                    ),
                    mimeType = null,
                    dataSource = DataSource.DISK
                )
            }
        }

        return null
    }

    class Factory : Fetcher.Factory<TrackArtworkRequest> {
        override fun create(
            data: TrackArtworkRequest,
            options: Options,
            imageLoader: ImageLoader
        ): Fetcher {
            return TrackArtworkFetcher(data, options)
        }
    }
}
