package com.auraplayer.app.playback

import com.auraplayer.app.data.model.VisualizerFrame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VisualizerBusTest {

    @Test
    fun `sampleInterpolatedInto interpolates between timestamped frames at requested delay`() {
        val bus = VisualizerBus()
        bus.publish(
            VisualizerFrame(
                spectrumBands = listOf(0.0f, 0.2f, 0.4f, 0.8f),
                waveform = listOf(-0.5f, 0.5f),
                bassEnergy = 0.2f,
                midEnergy = 0.4f,
                trebleEnergy = 0.6f,
                rmsLevel = 0.3f,
                beatPulse = 0.1f,
                timestampMs = 1000L,
                bpmEstimate = 120f
            )
        )
        bus.publish(
            VisualizerFrame(
                spectrumBands = listOf(1.0f, 0.6f, 0.8f, 1.0f),
                waveform = listOf(0.5f, -0.5f),
                bassEnergy = 0.8f,
                midEnergy = 0.6f,
                trebleEnergy = 0.2f,
                rmsLevel = 0.7f,
                beatPulse = 0.9f,
                timestampMs = 1100L,
                bpmEstimate = 120f
            )
        )

        val outBands = FloatArray(4)
        val outWave = FloatArray(2)
        val outScalars = FloatArray(VisualizerBus.SCALAR_COUNT)

        // With now = 1150 and delayMs = 100, targetTs = 1050 (exactly halfway between 1000 and 1100).
        val ok = bus.sampleInterpolatedInto(
            delayMs = 100,
            outBands = outBands,
            outWave = outWave,
            outScalars = outScalars,
            nowElapsedMs = 1150L
        )

        assertTrue(ok)
        assertEquals(0.5f, outBands[0], 0.01f)
        assertEquals(0.4f, outBands[1], 0.01f)
        assertEquals(0.6f, outBands[2], 0.01f)
        assertEquals(0.9f, outBands[3], 0.01f)
        assertEquals(0.5f, outScalars[VisualizerBus.SCALAR_BASS], 0.01f)
        assertEquals(120f, outScalars[VisualizerBus.SCALAR_BPM], 0.1f)
    }
}
