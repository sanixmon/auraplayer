package com.auraplayer.app.data.scanner

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import com.auraplayer.app.data.local.db.TrackEntity
import com.auraplayer.app.data.model.AudioFormat
import com.auraplayer.app.data.model.TrackFingerprint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface LocalAudioScanner {
    suspend fun scanDeviceMediaStore(): List<TrackEntity>
    suspend fun scanWatchedFolder(treeUriString: String): List<TrackEntity>
    suspend fun extractExternalTrack(uriString: String): TrackEntity?
    suspend fun takeFolderPermissionAndName(treeUriString: String): String?
    suspend fun isUriReadable(uriString: String): Boolean
}

class AndroidLocalAudioScanner(
    private val context: Context
) : LocalAudioScanner {

    override suspend fun scanDeviceMediaStore(): List<TrackEntity> = withContext(Dispatchers.IO) {
        val tracks = mutableListOf<TrackEntity>()
        val contentResolver = context.contentResolver

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.IS_MUSIC
        )

        val selection = "${MediaStore.Audio.Media.DURATION} >= ?"
        val selectionArgs = arrayOf("1000")
        val sortOrder = "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"

        contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            sortOrder
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
            val displayNameCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
            val dateAddedCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            val isMusicCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.IS_MUSIC)

            val albumArtBaseUri = Uri.parse("content://media/external/audio/albumart")

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val mimeType = cursor.getString(mimeCol).orEmpty()
                val displayName = cursor.getString(displayNameCol).orEmpty()
                val format = AudioFormat.fromMetadata(mimeType, displayName)
                val isMusic = cursor.getInt(isMusicCol) != 0

                if (!isMusic && format == AudioFormat.OTHER) {
                    continue
                }

                val rawTitle = cursor.getString(titleCol).orEmpty().trim()
                val title = rawTitle.ifEmpty {
                    displayName.substringBeforeLast('.').ifEmpty { "Unknown Track" }
                }
                val artist = cursor.getString(artistCol)
                    ?.takeIf { it.isNotBlank() && it != "<unknown>" }
                    ?: "Unknown Artist"
                val album = cursor.getString(albumCol)
                    ?.takeIf { it.isNotBlank() && it != "<unknown>" }
                    ?: "Unknown Album"
                val albumId = cursor.getLong(albumIdCol)
                val durationMs = cursor.getLong(durationCol).coerceAtLeast(0L)
                val sizeBytes = cursor.getLong(sizeCol).coerceAtLeast(0L)
                val dateAddedSec = cursor.getLong(dateAddedCol).coerceAtLeast(0L)

                val contentUri = ContentUris.withAppendedId(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    id
                ).toString()

                val albumArtUri = if (albumId > 0L) {
                    ContentUris.withAppendedId(albumArtBaseUri, albumId).toString()
                } else {
                    null
                }

                tracks += TrackEntity(
                    mediaStoreId = id,
                    uri = contentUri,
                    title = title,
                    artist = artist,
                    album = album,
                    durationMs = durationMs,
                    format = format.name,
                    mimeType = mimeType.ifEmpty { "audio/*" },
                    sizeBytes = sizeBytes,
                    dateAddedSec = dateAddedSec,
                    albumArtUri = albumArtUri,
                    isExternalImport = false,
                    isAvailable = true,
                    fingerprint = TrackFingerprint.compute(
                        titleOrFileName = displayName.ifEmpty { title },
                        durationMs = durationMs,
                        sizeBytes = sizeBytes
                    )
                )
            }
        }

        tracks
    }

    override suspend fun takeFolderPermissionAndName(treeUriString: String): String? =
        withContext(Dispatchers.IO) {
            val uri = runCatching { Uri.parse(treeUriString) }.getOrNull() ?: return@withContext null
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            val tree = DocumentFile.fromTreeUri(context, uri) ?: return@withContext null
            tree.name ?: uri.lastPathSegment ?: "External Folder"
        }

    override suspend fun scanWatchedFolder(treeUriString: String): List<TrackEntity> =
        withContext(Dispatchers.IO) {
            val uri = runCatching { Uri.parse(treeUriString) }.getOrNull() ?: return@withContext emptyList()
            val root = DocumentFile.fromTreeUri(context, uri) ?: return@withContext emptyList()
            if (!root.exists() || !root.isDirectory) return@withContext emptyList()

            val results = mutableListOf<TrackEntity>()
            traverseDocumentTree(root, depth = 0, out = results)
            results
        }

    private fun traverseDocumentTree(
        dir: DocumentFile,
        depth: Int,
        out: MutableList<TrackEntity>
    ) {
        if (depth > MAX_FOLDER_DEPTH) return
        val children = runCatching { dir.listFiles() }.getOrNull() ?: return
        for (child in children) {
            if (child.isDirectory) {
                traverseDocumentTree(child, depth + 1, out)
            } else if (child.isFile) {
                val name = child.name.orEmpty()
                val mime = child.type.orEmpty()
                val format = AudioFormat.fromMetadata(mime, name)
                if (format != AudioFormat.OTHER || mime.startsWith("audio/")) {
                    extractTrackBlocking(
                        uri = child.uri,
                        fallbackName = name,
                        fallbackSize = child.length().coerceAtLeast(0L),
                        takePermission = false
                    )?.let { out += it }
                }
            }
        }
    }

    override suspend fun extractExternalTrack(uriString: String): TrackEntity? =
        withContext(Dispatchers.IO) {
            val uri = runCatching { Uri.parse(uriString) }.getOrNull() ?: return@withContext null
            extractTrackBlocking(
                uri = uri,
                fallbackName = "",
                fallbackSize = 0L,
                takePermission = true
            )
        }

    override suspend fun isUriReadable(uriString: String): Boolean = withContext(Dispatchers.IO) {
        val uri = runCatching { Uri.parse(uriString) }.getOrNull() ?: return@withContext false
        runCatching {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { true } ?: false
        }.getOrDefault(false)
    }

    private fun extractTrackBlocking(
        uri: Uri,
        fallbackName: String,
        fallbackSize: Long,
        takePermission: Boolean
    ): TrackEntity? {
        val resolver = context.contentResolver
        if (takePermission) {
            runCatching {
                resolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
        }

        var displayName = fallbackName
        var sizeBytes = fallbackSize
        if (displayName.isEmpty() || sizeBytes <= 0L) {
            runCatching {
                resolver.query(
                    uri,
                    arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                    null,
                    null,
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (nameIdx >= 0 && displayName.isEmpty()) {
                            displayName = cursor.getString(nameIdx).orEmpty()
                        }
                        if (sizeIdx >= 0 && sizeBytes <= 0L) {
                            sizeBytes = cursor.getLong(sizeIdx).coerceAtLeast(0L)
                        }
                    }
                }
            }
        }

        val resolverMime = runCatching { resolver.getType(uri) }.getOrNull().orEmpty()
        var title = ""
        var artist = ""
        var album = ""
        var durationMs = 0L
        var retrieverMime = ""

        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE).orEmpty().trim()
            artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST).orEmpty().trim()
            album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM).orEmpty().trim()
            durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?.coerceAtLeast(0L)
                ?: 0L
            retrieverMime = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE).orEmpty()
        } catch (_: Exception) {
            // Fall back to OpenableColumns metadata when embedded tags cannot be parsed.
        } finally {
            runCatching { retriever.release() }
        }

        val effectiveMime = retrieverMime.ifEmpty { resolverMime }.ifEmpty { "audio/*" }
        val format = AudioFormat.fromMetadata(effectiveMime, displayName.ifEmpty { uri.toString() })
        val finalTitle = title.ifEmpty {
            displayName.substringBeforeLast('.').ifEmpty { "Imported Audio" }
        }

        return TrackEntity(
            mediaStoreId = null,
            uri = uri.toString(),
            title = finalTitle,
            artist = artist.ifEmpty { "Unknown Artist" },
            album = album.ifEmpty { "External Storage" },
            durationMs = durationMs,
            format = format.name,
            mimeType = effectiveMime,
            sizeBytes = sizeBytes,
            dateAddedSec = System.currentTimeMillis() / 1000L,
            albumArtUri = null,
            isExternalImport = true,
            isAvailable = true,
            fingerprint = TrackFingerprint.compute(
                titleOrFileName = displayName.ifEmpty { finalTitle },
                durationMs = durationMs,
                sizeBytes = sizeBytes
            )
        )
    }

    companion object {
        private const val MAX_FOLDER_DEPTH = 8
    }
}
