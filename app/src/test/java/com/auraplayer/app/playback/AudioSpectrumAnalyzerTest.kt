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
        // Min/max envelope preserves both positive peaks and negative troughs
        assertTrue(lowFrame.waveform.max() > 0.6f)
        assertTrue(lowFrame.waveform.min() < -0.6f)
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
}
