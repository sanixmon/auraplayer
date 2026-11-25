package com.auraplayer.app.playback

import com.auraplayer.app.data.model.VisualizerFrame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class AudioSpectrumAnalyzerTest {

    private val analyzer = AudioSpectrumAnalyzer(
        bassFftSize = 4096,
        detailFftSize = 1024,
        waveformPoints = 64
    )

    @Test
    fun `empty samples produce empty frame`() {
        val frame = analyzer.analyze(FloatArray(0), sampleCount = 0, bandCount = 48)
        assertEquals(VisualizerFrame.EMPTY, frame)
    }

    @Test
    fun `noise gate suppresses quiet floor below -60 dB to zero`() {
        val quietNoise = FloatArray(4096) { i ->
            (0.0004 * sin(2.0 * PI * 440.0 * i / 44100.0)).toFloat()
        }
        val frame = analyzer.analyze(quietNoise, bandCount = 64, sampleRateHz = 44100, timestampMs = 100L)

        assertEquals(64, frame.spectrumBands.size)
        assertEquals(64, frame.waveform.size)
        assertTrue(frame.rmsLevel < 0.001f)
        assertTrue(frame.spectrumBands.all { it == 0f })
    }

    @Test
    fun `multi-res FFT separates low bass vs treble across 30 Hz - 16 kHz log bands`() {
        val localAnalyzer = AudioSpectrumAnalyzer(bassFftSize = 4096, detailFftSize = 1024)
        val sampleRate = 44100
        val lowSine = FloatArray(4096) { i ->
            (0.8 * sin(2.0 * PI * 90.0 * i / sampleRate)).toFloat()
        }
        val highSine = FloatArray(4096) { i ->
            (0.8 * sin(2.0 * PI * 9000.0 * i / sampleRate)).toFloat()
        }

        val lowFrame = localAnalyzer.analyze(lowSine, bandCount = 64, sampleRateHz = sampleRate, timestampMs = 16L)
        localAnalyzer.reset()
        val highFrame = localAnalyzer.analyze(highSine, bandCount = 64, sampleRateHz = sampleRate, timestampMs = 16L)

        assertTrue(lowFrame.rmsLevel > 0.4f)
        assertTrue("Expected higher bass than treble for 90Hz sine", lowFrame.bassEnergy > lowFrame.trebleEnergy)
        assertTrue("Expected higher treble than bass for 9kHz sine", highFrame.trebleEnergy > highFrame.bassEnergy)
        assertTrue(lowFrame.spectrumBands.all { it in 0f..1f })
        assertTrue(lowFrame.waveform.all { it in -1f..1f })
        // Smooth waveform & min/max envelope preserve both positive peaks and negative troughs
        assertTrue(lowFrame.waveform.max() > 0.6f)
        assertTrue(lowFrame.waveform.min() < -0.6f)
        assertTrue(lowFrame.waveformMax.max() > 0.7f)
        assertTrue(lowFrame.waveformMin.min() < -0.7f)
    }

    @Test
    fun `pre-AGC bass spectral flux triggers beatPulse and estimates BPM across regular kicks`() {
        val localAnalyzer = AudioSpectrumAnalyzer(bassFftSize = 4096, detailFftSize = 1024)
        val silence = FloatArray(4096) { 0f }
        var ts = 1000L

        // Feed quiet frames to populate adaptive rolling flux baseline
        repeat(10) {
            localAnalyzer.analyze(silence, bandCount = 64, sampleRateHz = 44100, timestampMs = ts)
            ts += 16L
        }

        val kick = FloatArray(4096) { i ->
            (0.85 * sin(2.0 * PI * 80.0 * i / 44100.0)).toFloat()
        }

        // Simulate 120 BPM (500 ms interval between beats)
        val beat1 = localAnalyzer.analyze(kick, bandCount = 64, sampleRateHz = 44100, timestampMs = ts)
        assertTrue("Expected beatPulse on kick onset, got ${beat1.beatPulse}", beat1.beatPulse > 0.4f)

        ts += 500L
        repeat(6) {
            localAnalyzer.analyze(silence, bandCount = 64, sampleRateHz = 44100, timestampMs = ts - 120L + it * 16L)
        }
        val beat2 = localAnalyzer.analyze(kick, bandCount = 64, sampleRateHz = 44100, timestampMs = ts)
        assertTrue("Expected second beatPulse, got ${beat2.beatPulse}", beat2.beatPulse > 0.4f)
        assertTrue("Expected BPM estimate near 120 BPM, got ${beat2.bpmEstimate}", beat2.bpmEstimate in 90f..150f)

        localAnalyzer.reset()
        val afterReset = localAnalyzer.analyze(silence, bandCount = 64, sampleRateHz = 44100, timestampMs = ts + 100L)
        assertEquals(0f, afterReset.beatPulse, 0.0001f)
    }

    @Test
    fun `fast-FFT kick detector triggers cleanly over continuous loud bassline`() {
        val localAnalyzer = AudioSpectrumAnalyzer(bassFftSize = 4096, detailFftSize = 1024)
        val sampleRate = 44100
        val continuousBass = FloatArray(4096) { i ->
            (0.22 * sin(2.0 * PI * 65.0 * i / sampleRate)).toFloat()
        }
        val kickOverBass = FloatArray(4096) { i ->
            val base = 0.22 * sin(2.0 * PI * 65.0 * i / sampleRate)
            val kick = if (i >= 3072) 0.70 * sin(2.0 * PI * 85.0 * i / sampleRate) else 0.0
            (base + kick).toFloat().coerceIn(-1f, 1f)
        }

        var ts = 2000L
        var lastSteadyBeat = 1f
        repeat(20) {
            val f = localAnalyzer.analyze(continuousBass, bandCount = 64, sampleRateHz = sampleRate, timestampMs = ts)
            lastSteadyBeat = f.beatPulse
            ts += 16L
        }
        assertTrue("Expected calm beatPulse during steady bassline, got $lastSteadyBeat", lastSteadyBeat < 0.2f)

        val kickFrame = localAnalyzer.analyze(kickOverBass, bandCount = 64, sampleRateHz = sampleRate, timestampMs = ts)
        assertTrue(
            "Expected immediate beatPulse on short 23ms kick transient over bassline, got ${kickFrame.beatPulse}",
            kickFrame.beatPulse > 0.5f
        )
    }

    @Test
    fun `bassBoost lifts low-frequency response relative to treble`() {
        val sampleRate = 44100
        val mix = FloatArray(4096) { i ->
            (0.18 * sin(2.0 * PI * 80.0 * i / sampleRate) +
                0.55 * sin(2.0 * PI * 6000.0 * i / sampleRate)).toFloat()
        }
        val flatAnalyzer = AudioSpectrumAnalyzer(bassFftSize = 4096, detailFftSize = 1024)
        val boostedAnalyzer = AudioSpectrumAnalyzer(bassFftSize = 4096, detailFftSize = 1024)

        val flat = flatAnalyzer.analyze(mix, bandCount = 64, sampleRateHz = sampleRate, timestampMs = 100L, bassBoost = 1.0f)
        val boosted = boostedAnalyzer.analyze(mix, bandCount = 64, sampleRateHz = sampleRate, timestampMs = 100L, bassBoost = 1.8f)

        assertTrue(
            "Expected higher bassEnergy with bassBoost=1.8 (${boosted.bassEnergy}) than flat (${flat.bassEnergy})",
            boosted.bassEnergy > flat.bassEnergy
        )
    }

    @Test
    fun `custom frequency window focuses spectrum bands within requested Hz range`() {
        val sampleRate = 44100
        val highTone = FloatArray(4096) { i ->
            (0.8 * sin(2.0 * PI * 6000.0 * i / sampleRate)).toFloat()
        }
        val fullAnalyzer = AudioSpectrumAnalyzer(bassFftSize = 4096, detailFftSize = 1024)
        val bassWindowAnalyzer = AudioSpectrumAnalyzer(bassFftSize = 4096, detailFftSize = 1024)

        val fullFrame = fullAnalyzer.analyze(
            highTone,
            bandCount = 48,
            sampleRateHz = sampleRate,
            timestampMs = 100L,
            minHz = 30,
            maxHz = 16000
        )
        val bassOnlyFrame = bassWindowAnalyzer.analyze(
            highTone,
            bandCount = 48,
            sampleRateHz = sampleRate,
            timestampMs = 100L,
            minHz = 30,
            maxHz = 1000
        )

        assertTrue(
            "Expected full spectrum to detect 6kHz tone (${fullFrame.spectrumBands.max()})",
            fullFrame.spectrumBands.max() > 0.7f
        )
        assertTrue(
            "Expected 30-1000Hz window to ignore 6kHz tone (${bassOnlyFrame.spectrumBands.max()})",
            bassOnlyFrame.spectrumBands.max() < 0.25f
        )
    }
}
