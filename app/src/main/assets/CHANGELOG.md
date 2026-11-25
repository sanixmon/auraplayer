# Changelog

All notable changes to **Aura Player** are documented in this file.

## [1.3.0] - 2026-11-25

### Added
- Fire Ember particle visualizer with 5 directional modes (Up, Down, Left, Right, Radial) and dedicated Ember color preset.
- Beat & Audio Tuning controls: Beat Punch, Beat Sensitivity, Bass Boost, signed Audio Sync (-100 ms to +350 ms), and Quick Tuning Presets (Balanced, Punchy, Chill, Hyper).
- Quick Beat Preset selector and direct Custom Color Wheel picker inside the Now Playing Look sheet.
- Category-based Settings home with 4 focused visualizer sub-pages, per-category reset, live beat/BPM indicator, and confirmation dialog for Reset All.
- Signed release APK builds, GitHub Releases in-app updater, and bundled Changelog Wrapper viewer inside Settings.

### Fixed
- Tightened Media3 AudioTrack PCM buffer sizing and replaced wall-clock frame throttling with sample-accurate 60 Hz PCM hops so kick transients are no longer skipped or out of sync.
- Switched beat onset detection to a fast 1024-point FFT linear-magnitude kick flux detector with uncontaminated prior rolling baseline and improved 50 dB dynamic contrast.
- Redesigned Settings dropdown fields and popups to eliminate cramped labels and text wrapping.

### Changed
- Expanded Aura Backup schema (backward-compatible with v1) to preserve Fire Ember direction, custom visualizer colors, and beat tuning settings.

## [1.2.0] - 2026-11-18

### Added
- Multi-resolution FFT audio spectrum analyzer with dB-domain normalization, spectral flux beat detection, and temporal frame interpolation.
- Material 3 Expressive Now Playing controls with wavy progress scrubber, 5-second idle fullscreen mode, and cover art kick bounce.
- Line, Solid, and None fill modes across Ring, Bars, Wave, Mirror, and Pulse visualizers.
- Local JSON Aura Backup for playlists and visualizer settings.
