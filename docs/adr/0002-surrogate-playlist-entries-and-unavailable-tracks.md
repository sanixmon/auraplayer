# Surrogate Playlist Entries and Soft-Unavailable Tracks

## Status
Accepted

## Context
A naive many-to-many playlist table using `(playlistId, trackId)` with `ON_DELETE CASCADE` blocks duplicate occurrences of a track in a playlist and silently wipes playlist entries whenever Android temporarily unmounts external storage or a file is moved before a Library Scan.

## Decision
Model playlist membership with a surrogate `entryId` primary key (`PlaylistEntryEntity`), and represent missing tracks with an `isAvailable = false` (`Unavailable`) status rather than deleting `TrackEntity` rows referenced by playlists or restored Aura Backups. During Library Scans, newly scanned tracks match and relink `Unavailable` tracks via `TrackFingerprint` (`sizeBytes`, `durationMs`, normalized filename/title).

## Consequences
- Users can repeat tracks inside a playlist, survive SD card hiccups, and restore Aura Backups before transferring audio files.
- `LibraryScan` must perform fingerprint relinking and keep `Unavailable` rows hidden from the default Library view while auto-skipping them in the `PlaybackQueue`.
