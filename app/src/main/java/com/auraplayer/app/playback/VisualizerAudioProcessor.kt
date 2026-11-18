package com.auraplayer.app.playback

import android.os.SystemClock
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.min

/**
 * Media3 AudioProcessor that taps decoded PCM samples directly in the playback pipeline.
 * Supports 16-bit, 24-bit, 32-bit integer, and 32-bit float PCM without RECORD_AUDIO permission.
 */
@OptIn(UnstableApi::class)
class VisualizerAudioProcessor(
    private val visualizerBus: VisualizerBus,
    private val analyzer: AudioSpectrumAnalyzer = AudioSpectrumAnalyzer(fftSize = FFT_SIZE),
    private val bandCountProvider: () -> Int = { 96 }
) : BaseAudioProcessor() {

    private val monoScratch = FloatArray(FFT_SIZE)
    @Volatile
    private var lastEmitElapsedMs = 0L

    override fun onConfigure(
        inputAudioFormat: AudioProcessor.AudioFormat
    ): AudioProcessor.AudioFormat {
        return when (inputAudioFormat.encoding) {
            C.ENCODING_PCM_16BIT,
            C.ENCODING_PCM_24BIT,
            C.ENCODING_PCM_32BIT,
            C.ENCODING_PCM_FLOAT -> inputAudioFormat
            else -> AudioProcessor.AudioFormat.NOT_SET
        }
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        val now = SystemClock.elapsedRealtime()
        if (now - lastEmitElapsedMs >= FRAME_INTERVAL_MS) {
            lastEmitElapsedMs = now
            sampleAndPublish(inputBuffer.duplicate())
        }

        val out = replaceOutputBuffer(remaining)
        out.put(inputBuffer)
        out.flip()
    }

    override fun onFlush() {
        visualizerBus.clear()
    }

    override fun onReset() {
        visualizerBus.clear()
    }

    private fun sampleAndPublish(snapshot: ByteBuffer) {
        snapshot.order(ByteOrder.LITTLE_ENDIAN)
        val channels = inputAudioFormat.channelCount.coerceAtLeast(1)
        var sampleIndex = 0

        when (inputAudioFormat.encoding) {
            C.ENCODING_PCM_16BIT -> {
                val bytesPerFrame = channels * 2
                val framesToRead = min(snapshot.remaining() / bytesPerFrame, FFT_SIZE)
                while (sampleIndex < framesToRead && snapshot.remaining() >= bytesPerFrame) {
                    var sum = 0f
                    for (ch in 0 until channels) {
                        sum += snapshot.short / 32768f
                    }
                    monoScratch[sampleIndex++] = (sum / channels).coerceIn(-1f, 1f)
                }
            }

            C.ENCODING_PCM_24BIT -> {
                val bytesPerFrame = channels * 3
                val framesToRead = min(snapshot.remaining() / bytesPerFrame, FFT_SIZE)
                while (sampleIndex < framesToRead && snapshot.remaining() >= bytesPerFrame) {
                    var sum = 0f
                    for (ch in 0 until channels) {
                        val b0 = snapshot.get().toInt() and 0xFF
                        val b1 = snapshot.get().toInt() and 0xFF
                        val b2 = snapshot.get().toInt() // signed high byte
                        val sample24 = (b2 shl 16) or (b1 shl 8) or b0
                        sum += sample24 / 8_388_608f
                    }
                    monoScratch[sampleIndex++] = (sum / channels).coerceIn(-1f, 1f)
                }
            }

            C.ENCODING_PCM_32BIT -> {
                val bytesPerFrame = channels * 4
                val framesToRead = min(snapshot.remaining() / bytesPerFrame, FFT_SIZE)
                while (sampleIndex < framesToRead && snapshot.remaining() >= bytesPerFrame) {
                    var sum = 0f
                    for (ch in 0 until channels) {
                        sum += snapshot.int / 2_147_483_648f
                    }
                    monoScratch[sampleIndex++] = (sum / channels).coerceIn(-1f, 1f)
                }
            }

            C.ENCODING_PCM_FLOAT -> {
                val bytesPerFrame = channels * 4
                val framesToRead = min(snapshot.remaining() / bytesPerFrame, FFT_SIZE)
                while (sampleIndex < framesToRead && snapshot.remaining() >= bytesPerFrame) {
                    var sum = 0f
                    for (ch in 0 until channels) {
                        sum += snapshot.float
                    }
                    monoScratch[sampleIndex++] = (sum / channels).coerceIn(-1f, 1f)
                }
            }

            else -> return
        }

        if (sampleIndex > 0) {
            val frame = analyzer.analyze(
                monoSamples = monoScratch,
                sampleCount = sampleIndex,
                bandCount = bandCountProvider()
            )
            visualizerBus.publish(frame)
        }
    }

    companion object {
        private const val FFT_SIZE = 512
        private const val FRAME_INTERVAL_MS = 16L
    }
}
