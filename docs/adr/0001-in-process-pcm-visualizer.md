# In-Process Media3 PCM Visualizer Without Audio Offload

## Status
Accepted

## Context
Aura Player requires a real-time sound visualizer across Android 8.0+ devices while maintaining a strict zero-extra-permission security posture (no `RECORD_AUDIO`). Android's platform `android.media.audiofx.Visualizer` often requires `RECORD_AUDIO` and behaves inconsistently across OEM audio HALs and emulators.

## Decision
Tap decoded PCM audio directly inside Media3's playback pipeline using a custom `BaseAudioProcessor` (`VisualizerAudioProcessor`) attached to `DefaultAudioSink`, while keeping audio offload disabled (`setAudioOffloadMode` / offload disabled). All 16-bit, 24-bit, 32-bit, and float PCM streams pass through a 512-point Hann-windowed Radix-2 FFT (`AudioSpectrumAnalyzer`) throttled to ~60fps.

## Consequences
- Zero `RECORD_AUDIO` permission is needed and visualizer response is deterministic across devices.
- Hardware audio offload cannot be used while PCM tapping is active, slightly increasing CPU wake time during active screen-on playback.
