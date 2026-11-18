package com.auraplayer.app.playback

import com.auraplayer.app.data.model.VisualizerFrame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class AudioSpectrumAnalyzerTest {

    private val analyzer = AudioSpectrumAnalyzer(fftSize = 512, waveformPoints = 64)

    @Test
    fun `empty samples produce empty frame`() {
        val frame = analyzer.analyze(FloatArray(0), sampleCount = 0, bandCount = 48)
        assertEquals(VisualizerFrame.EMPTY, frame)
    }

    @Test
    fun `silent samples produce near-zero energies and requested band count`() {
        val silence = FloatArray(512) { 0f }
        val frame = analyzer.analyze(silence, bandCount = 32)

        assertEquals(32, frame.spectrumBands.size)
        assertEquals(64, frame.waveform.size)
        assertEquals(0f, frame.rmsLevel, 0.0001f)
        assertTrue(frame.spectrumBands.all { it == 0f })
    }

    @Test
    fun `sine wave produces bounded normalized spectrum and higher bass vs treble for low frequency`() {
        val sampleRate = 44100.0
        val lowFreqHz = 180.0
        val highFreqHz = 10000.0

        val lowSine = FloatArray(512) { i ->
            (0.8 * sin(2.0 * PI * lowFreqHz * i / sampleRate)).toFloat()
        }
        val highSine = FloatArray(512) { i ->
            (0.8 * sin(2.0 * PI * highFreqHz * i / sampleRate)).toFloat()
        }

        val lowFrame = analyzer.analyze(lowSine, bandCount = 48)
        val highFrame = analyzer.analyze(highSine, bandCount = 48)

        assertTrue(lowFrame.rmsLevel > 0.4f)
        assertTrue(lowFrame.bassEnergy > lowFrame.trebleEnergy)
        assertTrue(highFrame.trebleEnergy > highFrame.bassEnergy)
        assertTrue(lowFrame.spectrumBands.all { it in 0f..1f })
        assertTrue(lowFrame.waveform.all { it in -1f..1f })
    }

    @Test
    fun `loud bass onset after silence triggers beatPulse and reset clears rolling state`() {
        val localAnalyzer = AudioSpectrumAnalyzer(fftSize = 512, waveformPoints = 64)
        val silence = FloatArray(512) { 0f }
        repeat(6) {
            val frame = localAnalyzer.analyze(silence, bandCount = 48)
            assertEquals(0f, frame.beatPulse, 0.0001f)
        }

        val sampleRate = 44100.0
        val kick = FloatArray(512) { i ->
            (0.85 * sin(2.0 * PI * 100.0 * i / sampleRate)).toFloat()
        }
        val onsetFrame = localAnalyzer.analyze(kick, bandCount = 48)
        assertTrue("Expected noticeable beatPulse on bass kick, got ${onsetFrame.beatPulse}", onsetFrame.beatPulse > 0.3f)

        // Reset restores initial moving baseline; silence produces zero beatPulse again.
        localAnalyzer.reset()
        val afterReset = localAnalyzer.analyze(silence, bandCount = 48)
        assertEquals(0f, afterReset.beatPulse, 0.0001f)
    }
}
