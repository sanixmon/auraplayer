package com.auraplayer.app.data.local.db

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackDao {

    @Query(
        """
        SELECT * FROM tracks
        WHERE (:includeUnavailable = 1 OR isAvailable = 1)
          AND (:query = '' OR title LIKE '%' || :query || '%' OR artist LIKE '%' || :query || '%' OR album LIKE '%' || :query || '%')
          AND (:formatFilter IS NULL OR format = :formatFilter)
        ORDER BY
            CASE WHEN :sortBy = 'TITLE' THEN title END COLLATE NOCASE ASC,
            CASE WHEN :sortBy = 'ARTIST' THEN artist END COLLATE NOCASE ASC,
            CASE WHEN :sortBy = 'ALBUM' THEN album END COLLATE NOCASE ASC,
            CASE WHEN :sortBy = 'DURATION' THEN durationMs END DESC,
            CASE WHEN :sortBy = 'DATE_ADDED' THEN dateAddedSec END DESC,
            title COLLATE NOCASE ASC
        """
    )
    fun pagingSource(
        query: String,
        formatFilter: String?,
        sortBy: String,
        includeUnavailable: Boolean
    ): PagingSource<Int, TrackEntity>

    @Query(
        """
        SELECT * FROM tracks
        WHERE isAvailable = 1
          AND (:query = '' OR title LIKE '%' || :query || '%' OR artist LIKE '%' || :query || '%' OR album LIKE '%' || :query || '%')
          AND (:formatFilter IS NULL OR format = :formatFilter)
        ORDER BY
            CASE WHEN :sortBy = 'TITLE' THEN title END COLLATE NOCASE ASC,
            CASE WHEN :sortBy = 'ARTIST' THEN artist END COLLATE NOCASE ASC,
            CASE WHEN :sortBy = 'ALBUM' THEN album END COLLATE NOCASE ASC,
            CASE WHEN :sortBy = 'DURATION' THEN durationMs END DESC,
            CASE WHEN :sortBy = 'DATE_ADDED' THEN dateAddedSec END DESC,
            title COLLATE NOCASE ASC
        """
    )
    suspend fun getFilteredAvailableTracks(
        query: String,
        formatFilter: String?,
        sortBy: String
    ): List<TrackEntity>

    @Query("SELECT * FROM tracks WHERE isAvailable = 1 ORDER BY title COLLATE NOCASE ASC")
    fun observeAvailableTracks(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks")
    suspend fun getAllTracksSnapshot(): List<TrackEntity>

    @Query("SELECT COUNT(*) FROM tracks WHERE isAvailable = 0")
    fun observeUnavailableCount(): Flow<Int>

    @Query("SELECT * FROM tracks WHERE uri = :uri LIMIT 1")
    suspend fun getTrackByUri(uri: String): TrackEntity?

    @Query("SELECT * FROM tracks WHERE fingerprint = :fingerprint LIMIT 1")
    suspend fun getTrackByFingerprint(fingerprint: String): TrackEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSingleIgnore(track: TrackEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(tracks: List<TrackEntity>): List<Long>

    @Query(
        """
        UPDATE tracks
        SET mediaStoreId = :mediaStoreId,
            uri = :uri,
            title = :title,
            artist = :artist,
            album = :album,
            durationMs = :durationMs,
            format = :format,
            mimeType = :mimeType,
            sizeBytes = :sizeBytes,
            dateAddedSec = :dateAddedSec,
            albumArtUri = :albumArtUri,
            isExternalImport = :isExternalImport,
            isAvailable = 1,
            fingerprint = :fingerprint
        WHERE id = :id
        """
    )
    suspend fun updateAndRelinkTrack(
        id: Long,
        mediaStoreId: Long?,
        uri: String,
        title: String,
        artist: String,
        album: String,
        durationMs: Long,
        format: String,
        mimeType: String,
        sizeBytes: Long,
        dateAddedSec: Long,
        albumArtUri: String?,
        isExternalImport: Boolean,
        fingerprint: String
    )

    @Query("UPDATE tracks SET isAvailable = 0 WHERE id IN (:ids)")
    suspend fun markUnavailableByIds(ids: List<Long>)

    @Query("DELETE FROM tracks WHERE isAvailable = 0")
    suspend fun purgeUnavailableTracks(): Int

    @Query("DELETE FROM tracks WHERE id = :trackId")
    suspend fun deleteTrackById(trackId: Long)

    @Transaction
    suspend fun syncScannedTracks(
        scannedTracks: List<TrackEntity>,
        checkedExternalUris: Set<String>
    ) {
        val existingTracks = getAllTracksSnapshot()
        val byUri = existingTracks.associateBy { it.uri }.toMutableMap()
        val byFingerprint = existingTracks.groupBy { it.fingerprint }

        val activeIds = mutableSetOf<Long>()
        val relinkedIds = mutableSetOf<Long>()
        val toInsert = mutableListOf<TrackEntity>()

        for (scanned in scannedTracks) {
            val matchedByUri = byUri[scanned.uri]
            val matchedUnavailableByFingerprint = if (matchedByUri == null) {
                byFingerprint[scanned.fingerprint]?.firstOrNull {
                    !it.isAvailable && it.id !in relinkedIds
                }
            } else {
                null
            }
            val existing = matchedByUri ?: matchedUnavailableByFingerprint

            if (existing != null) {
                activeIds += existing.id
                relinkedIds += existing.id
                // Ensure URI uniqueness when relinking an unavailable placeholder to a newly discovered URI.
                if (matchedByUri == null && byUri.containsKey(scanned.uri)) {
                    continue
                }
                updateAndRelinkTrack(
                    id = existing.id,
                    mediaStoreId = scanned.mediaStoreId,
                    uri = scanned.uri,
                    title = scanned.title,
                    artist = scanned.artist,
                    album = scanned.album,
                    durationMs = scanned.durationMs,
                    format = scanned.format,
                    mimeType = scanned.mimeType,
                    sizeBytes = scanned.sizeBytes,
                    dateAddedSec = scanned.dateAddedSec,
                    albumArtUri = scanned.albumArtUri,
                    isExternalImport = scanned.isExternalImport,
                    fingerprint = scanned.fingerprint
                )
                byUri[scanned.uri] = existing.copy(uri = scanned.uri, isAvailable = true)
            } else {
                toInsert += scanned
            }
        }

        toInsert.chunked(250).forEach { batch ->
            insertIgnore(batch)
        }

        val missingIds = existingTracks
            .filter { existing ->
                val wasChecked = !existing.isExternalImport || existing.uri in checkedExternalUris
                wasChecked && existing.isAvailable && existing.id !in activeIds
            }
            .map { it.id }

        missingIds.chunked(500).forEach { chunk ->
            markUnavailableByIds(chunk)
        }
    }
}
