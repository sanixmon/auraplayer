# CI-Compiled LGPL Media3 FFmpeg Extension

## Status
Accepted

## Context
Supporting 20+ offline formats (including ALAC, WMA, APE, AC3, EAC3, DTS, WavPack, TTA, Musepack) across diverse Android 8+ OEMs exceeds platform `MediaCodec` coverage. Downloading native `.so` binaries at runtime violates Google Play's dynamic code loading policy and would require `INTERNET` permission, while prebuilt community Maven artifacts (such as Jellyfin's `media3-ffmpeg-decoder`) carry GPLv3 copyleft obligations.

## Decision
Compile `androidx/media` `decoder_ffmpeg` (`1.5.1`) from source inside a cached GitHub Actions CI step using LGPL-only flags (`--disable-gpl --disable-nonfree`) and bundle the resulting AAR into `:app` with ABI splits. Configure `DefaultRenderersFactory` with `EXTENSION_RENDERER_MODE_ON` so platform hardware decoders run first for battery efficiency and FFmpeg serves as automatic fallback.

## Consequences
- Zero `INTERNET` permission, full Google Play Store compliance, and unrestricted app licensing.
- GitHub CI includes an NDK r26b + CMake build step (cached by Media3/FFmpeg version key) to produce `lib-decoder-ffmpeg-release.aar`.
