# Aura Player — Offline Local Music Player

Aura Player is an offline-only Android local music player built with **Kotlin**, **Jetpack Compose Material 3**, **Jetpack Media3 (ExoPlayer + MediaSessionService)**, **Room + Paging 3**, and **DataStore**, featuring a real-time customizable PCM **Visualizer**.

> **Build Policy:** All compilation, LGPL FFmpeg native compilation, Android Lint, R8 release verification, and unit tests run exclusively in **GitHub Actions CI** ([`.github/workflows/android-ci.yml`](.github/workflows/android-ci.yml)).

---

## Domain & Architecture Documentation
- **Domain Glossary:** [`CONTEXT.md`](CONTEXT.md)
- **Architecture Decision Records:**
  - [`docs/adr/0001-in-process-pcm-visualizer.md`](docs/adr/0001-in-process-pcm-visualizer.md)
  - [`docs/adr/0002-surrogate-playlist-entries-and-unavailable-tracks.md`](docs/adr/0002-surrogate-playlist-entries-and-unavailable-tracks.md)
  - [`docs/adr/0003-lgpl-ffmpeg-ci-bundled-extension.md`](docs/adr/0003-lgpl-ffmpeg-ci-bundled-extension.md)

---

## Key Capabilities

### 1. 21 Offline Audio Formats (Hardware MediaCodec + LGPL FFmpeg)
Configured with `EXTENSION_RENDERER_MODE_ON` in [`AuraPlaybackService.kt`](app/src/main/java/com/auraplayer/app/playback/AuraPlaybackService.kt):
- **Lossless:** `FLAC`, `WAV`, `ALAC`, `APE`, `WavPack (WV)`, `TTA`
- **Lossy & Studio Containers:** `MP3`, `AAC`, `M4A`, `OGG`, `OPUS`, `WMA`, `Musepack (MPC)`, `AC3`, `E-AC3`, `DTS`, `WebM`, `MKA`, `AMR`, `MIDI`, `3GP`

### 2. Large Library Scale & Watched Folders
- **Room Paging 3:** [`TrackDao.pagingSource`](app/src/main/java/com/auraplayer/app/data/local/db/TrackDao.kt) streams 10,000+ tracks smoothly with SQLite-indexed search, format filtering, and 5 sort orders.
- **Watched Folders + Fingerprint Deduplication:** Grant folders via `OpenDocumentTree` ([`WatchedFolderEntity.kt`](app/src/main/java/com/auraplayer/app/data/local/db/WatchedFolderEntity.kt)) or import files via `OpenMultipleDocuments`. [`TrackFingerprint`](app/src/main/java/com/auraplayer/app/data/model/TrackFingerprint.kt) prevents duplicates across `MediaStore` and Watched Folders.

### 3. Resilient Playlists & Local Aura Backup
- **Surrogate `PlaylistEntry` + `Unavailable` Survival:** Playlists allow duplicate track slots and survive temporary SD-card unmounts or moves. Missing tracks are marked `Unavailable`, greyed out in playlists with **Relocate / Remove**, auto-skipped in the Playback Queue, and auto-relinked on future Library Scans.
- **Drag-and-Drop Reordering:** Long-press and drag entries in [`PlaylistDetailScreen.kt`](app/src/main/java/com/auraplayer/app/ui/screens/playlists/PlaylistDetailScreen.kt).
- **Local JSON Aura Backup:** [`AuraBackupManager.kt`](app/src/main/java/com/auraplayer/app/data/backup/AuraBackupManager.kt) exports/imports single playlists or full backups (all Playlists + Visualizer presets) via SAF.

### 4. In-Process PCM Visualizer (8 Settings) & Adaptive Tablet UI
- [`VisualizerAudioProcessor.kt`](app/src/main/java/com/auraplayer/app/playback/VisualizerAudioProcessor.kt) taps 16/24/32-bit integer and float PCM without `RECORD_AUDIO` and disables audio offload.
- [`SettingsScreen.kt`](app/src/main/java/com/auraplayer/app/ui/screens/settings/SettingsScreen.kt) provides 5 styles, 5 color schemes, 4 spectrum densities, sensitivity, smoothing, glow, mirror symmetry, and peak caps.
- [`AuraNavHost.kt`](app/src/main/java/com/auraplayer/app/ui/navigation/AuraNavHost.kt) renders bottom `NavigationBar` on phones, `NavigationRail` on medium tablets, and a two-pane split (Library/Playlist left + docked Visualizer Deck right) on expanded tablets.

---

## GitHub CI Pipeline
[`.github/workflows/android-ci.yml`](.github/workflows/android-ci.yml):
1. Builds & caches LGPL `lib-decoder-ffmpeg-release.aar` from `androidx/media` `1.5.1` + `FFmpeg release/6.0` (NDK r26b, ABI `26`).
2. Runs `gradle testDebugUnitTest --stacktrace`.
3. Runs `gradle lintDebug --stacktrace`.
4. Builds `gradle assembleDebug --stacktrace` and unsigned `gradle assembleRelease --stacktrace` (verifying R8 + resource shrinking + ABI splits).
