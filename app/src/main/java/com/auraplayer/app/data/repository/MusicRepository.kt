package com.auraplayer.app.data.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import com.auraplayer.app.data.local.db.TrackDao
import com.auraplayer.app.data.local.db.TrackEntity
import com.auraplayer.app.data.local.db.WatchedFolderDao
import com.auraplayer.app.data.local.db.WatchedFolderEntity
import com.auraplayer.app.data.model.AudioFormat
import com.auraplayer.app.data.model.Track
import com.auraplayer.app.data.model.WatchedFolder
import com.auraplayer.app.data.scanner.LocalAudioScanner
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class LibrarySortOrder(val displayName: String) {
    TITLE("Title (A-Z)"),
    ARTIST("Artist"),
    ALBUM("Album"),
    DURATION("Duration"),
    DATE_ADDED("Recently Added")
}

class MusicRepository(
    private val trackDao: TrackDao,
    private val watchedFolderDao: WatchedFolderDao,
    private val scanner: LocalAudioScanner,
    private val clockMs: () -> Long = { System.currentTimeMillis() }
) {

    fun observeAvailableTracks(): Flow<List<Track>> =
        trackDao.observeAvailableTracks().map { list -> list.map { it.toDomain() } }

    fun observeUnavailableCount(): Flow<Int> =
        trackDao.observeUnavailableCount()

    fun observeWatchedFolders(): Flow<List<WatchedFolder>> =
        watchedFolderDao.observeAll().map { folders -> folders.map { it.toDomain() } }

    fun observePagedTracks(
        query: String,
        formatFilter: AudioFormat?,
        sortOrder: LibrarySortOrder,
        includeUnavailable: Boolean
    ): Flow<PagingData<Track>> {
        val cleanQuery = query.trim()
        return Pager(
            config = PagingConfig(
                pageSize = 60,
                prefetchDistance = 20,
                enablePlaceholders = false
            ),
            pagingSourceFactory = {
                trackDao.pagingSource(
                    query = cleanQuery,
                    formatFilter = formatFilter?.name,
                    sortBy = sortOrder.name,
                    includeUnavailable = includeUnavailable
                )
            }
        ).flow.map { pagingData ->
            pagingData.map { it.toDomain() }
        }
    }

    suspend fun getPlayableTracks(
        query: String,
        formatFilter: AudioFormat?,
        sortOrder: LibrarySortOrder
    ): List<Track> = trackDao.getFilteredAvailableTracks(
        query = query.trim(),
        formatFilter = formatFilter?.name,
        sortBy = sortOrder.name
    ).map { it.toDomain() }

    suspend fun syncDeviceLibrary(): Int {
        val mediaStoreTracks = scanner.scanDeviceMediaStore()
        val seenFingerprints = mediaStoreTracks.mapTo(mutableSetOf()) { it.fingerprint }
        val dedupedTracks = ArrayList<TrackEntity>(mediaStoreTracks.size)
        dedupedTracks.addAll(mediaStoreTracks)

        val watchedFolderTrackUris = mutableSetOf<String>()
        val watchedFolders = watchedFolderDao.getAll()
        val scanNowMs = clockMs()

        for (folder in watchedFolders) {
            val folderTracks = scanner.scanWatchedFolder(folder.uri)
            watchedFolderDao.updateLastScanned(folder.uri, scanNowMs)
            for (candidate in folderTracks) {
                watchedFolderTrackUris += candidate.uri
                if (seenFingerprints.add(candidate.fingerprint)) {
                    dedupedTracks += candidate
                }
            }
        }

        // Check readability for existing standalone external imports so missing ones become Unavailable.
        val existingTracks = trackDao.getAllTracksSnapshot()
        val checkedExternalUris = mutableSetOf<String>()
        checkedExternalUris.addAll(watchedFolderTrackUris)

        for (existing in existingTracks) {
            if (existing.isExternalImport && existing.uri !in watchedFolderTrackUris) {
                checkedExternalUris += existing.uri
                if (existing.isAvailable &&
                    !existing.uri.startsWith("unavailable://") &&
                    scanner.isUriReadable(existing.uri)
                ) {
                    dedupedTracks += existing
                }
            }
        }

        trackDao.syncScannedTracks(
            scannedTracks = dedupedTracks,
            checkedExternalUris = checkedExternalUris
        )
        return dedupedTracks.size
    }

    suspend fun addWatchedFolder(treeUriString: String): Boolean {
        val displayName = scanner.takeFolderPermissionAndName(treeUriString) ?: return false
        val now = clockMs()
        watchedFolderDao.upsert(
            WatchedFolderEntity(
                uri = treeUriString,
                displayName = displayName,
                addedAtMs = now,
                lastScannedAtMs = 0L
            )
        )
        syncDeviceLibrary()
        return true
    }

    suspend fun removeWatchedFolder(treeUriString: String) {
        watchedFolderDao.deleteByUri(treeUriString)
    }

    suspend fun importExternalFiles(uriStrings: List<String>): Int {
        var importedCount = 0
        for (uri in uriStrings) {
            val extracted = scanner.extractExternalTrack(uri) ?: continue
            val existingByUri = trackDao.getTrackByUri(uri)
            if (existingByUri != null) {
                if (!existingByUri.isAvailable) {
                    trackDao.updateAndRelinkTrack(
                        id = existingByUri.id,
                        mediaStoreId = null,
                        uri = extracted.uri,
                        title = extracted.title,
                        artist = extracted.artist,
                        album = extracted.album,
                        durationMs = extracted.durationMs,
                        format = extracted.format,
                        mimeType = extracted.mimeType,
                        sizeBytes = extracted.sizeBytes,
                        dateAddedSec = extracted.dateAddedSec,
                        albumArtUri = null,
                        isExternalImport = true,
                        fingerprint = extracted.fingerprint
                    )
                    importedCount++
                }
                continue
            }

            val existingUnavailableByFp = trackDao.getTrackByFingerprint(extracted.fingerprint)
                ?.takeIf { !it.isAvailable }
            if (existingUnavailableByFp != null) {
                trackDao.updateAndRelinkTrack(
                    id = existingUnavailableByFp.id,
                    mediaStoreId = null,
                    uri = extracted.uri,
                    title = extracted.title,
                    artist = extracted.artist,
                    album = extracted.album,
                    durationMs = extracted.durationMs,
                    format = extracted.format,
                    mimeType = extracted.mimeType,
                    sizeBytes = extracted.sizeBytes,
                    dateAddedSec = extracted.dateAddedSec,
                    albumArtUri = null,
                    isExternalImport = true,
                    fingerprint = extracted.fingerprint
                )
            } else {
                trackDao.insertSingleIgnore(extracted)
            }
            importedCount++
        }
        return importedCount
    }

    suspend fun relocateTrack(trackId: Long, newUriString: String): Boolean {
        val extracted = scanner.extractExternalTrack(newUriString) ?: return false
        trackDao.updateAndRelinkTrack(
            id = trackId,
            mediaStoreId = null,
            uri = extracted.uri,
            title = extracted.title,
            artist = extracted.artist,
            album = extracted.album,
            durationMs = extracted.durationMs,
            format = extracted.format,
            mimeType = extracted.mimeType,
            sizeBytes = extracted.sizeBytes,
            dateAddedSec = extracted.dateAddedSec,
            albumArtUri = null,
            isExternalImport = true,
            fingerprint = extracted.fingerprint
        )
        return true
    }

    suspend fun purgeUnavailableTracks(): Int = trackDao.purgeUnavailableTracks()

    suspend fun deleteTrack(trackId: Long) {
        trackDao.deleteTrackById(trackId)
    }
}
