# Aura Player

Offline-only Android local music player with real-time customizable PCM sound visualization and local playlist management.

## Language

**Track**:
An indexed local audio file (discovered via `MediaStore`, a Watched Folder, or External Import) with playback metadata, audio format, and availability (`Available` or `Unavailable`).
_Avoid_: Song, File, MediaItem

**Track Fingerprint**:
The deduplication and cross-device relinking key (`sizeBytes`, `durationMs`, and normalized filename/title) used to prevent duplicates across `MediaStore` and Watched Folders and to relink restored playlists.
_Avoid_: Hash, File ID

**Unavailable**:
The state of a Track whose backing URI is temporarily unreachable or missing during a Library Scan, or restored from an Aura Backup before the audio file exists on the device; kept in Playlists and auto-skipped during playback until relinked.
_Avoid_: Broken, Deleted, Orphaned

**Library**:
The local catalog of all indexed Tracks on the device, stored purely on-device with zero cloud or peer connectivity.
_Avoid_: Sync, Cloud Library, Offline Sync

**Library Scan**:
Reconciling the Library against `MediaStore` and Watched Folders; missing Tracks become `Unavailable` rather than being purged from Playlists, and newly matching files relink existing `Unavailable` Tracks.
_Avoid_: Sync, Offline Syncing, Refresh Sync

**Watched Folder**:
A user-granted directory (`OpenDocumentTree`) with a persisted read-only permission that is automatically traversed during every Library Scan.
_Avoid_: Synced Folder, Mount

**External Import**:
User-initiated addition of individual Tracks (`OpenMultipleDocuments`) or Watched Folders (`OpenDocumentTree`) via Android's Storage Access Framework.
_Avoid_: Upload, Download, External Sync

**Playlist**:
A user-curated, ordered collection of Playlist Entries that persists across Library Scans.
_Avoid_: Queue, Folder

**Playlist Entry**:
A positioned slot within a Playlist referencing a Track; the same Track may appear multiple times in one Playlist, and the entry survives if the underlying Track becomes `Unavailable`.
_Avoid_: CrossRef, Playlist Track, Playlist Item

**Playback Queue**:
The ephemeral, active sequence of Available Tracks loaded in the playback service (with shuffle and repeat state), separate from persisted Playlists.
_Avoid_: Playlist, Up Next List

**Aura Backup**:
A versioned, user-exported local JSON document containing one or more Playlists (with Track Fingerprints) and optional Visualizer settings for offline migration.
_Avoid_: Cloud Sync, Auto Backup

**Visualizer**:
The real-time in-process PCM spectrum and waveform renderer driven directly by decoded audio samples inside the playback pipeline without microphone access.
_Avoid_: AudioFX, Mic Visualizer
