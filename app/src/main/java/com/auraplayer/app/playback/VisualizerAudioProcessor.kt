package com.auraplayer.app.playback

import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Media3 AudioProcessor that taps decoded PCM samples through a continuous sliding ring buffer.
 * Supports 16-bit, 24-bit, 32-bit integer, and 32-bit float PCM without RECORD_AUDIO permission.
 */
@OptIn(UnstableApi::class)
class VisualizerAudioProcessor(
    private val visualizerBus: VisualizerBus,
    private val analyzer: AudioSpectrumAnalyzer = AudioSpectrumAnalyzer(fftSize = FFT_SIZE),
    private val bandCountProvider: () -> Int = { 96 }
) : BaseAudioProcessor() {

    private val ringBuffer = FloatArray(FFT_SIZE)
    private val orderedSamples = FloatArray(FFT_SIZE)
    private var ringWritePos = 0
    private var samplesFilled = 0

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

        appendToRingBuffer(inputBuffer.duplicate())

        val now = SystemClock.elapsedRealtime()
        if (now - lastEmitElapsedMs >= FRAME_INTERVAL_MS && samplesFilled >= MIN_SAMPLES_TO_ANALYZE) {
            lastEmitElapsedMs = now
            publishLatestWindow()
        }

        val out = replaceOutputBuffer(remaining)
        out.put(inputBuffer)
        out.flip()
    }

    override fun onFlush() {
        ringWritePos = 0
        samplesFilled = 0
        analyzer.reset()
        visualizerBus.clear()
    }

    override fun onReset() {
        ringWritePos = 0
        samplesFilled = 0
        analyzer.reset()
        visualizerBus.clear()
    }

    private fun appendToRingBuffer(snapshot: ByteBuffer) {
        snapshot.order(ByteOrder.LITTLE_ENDIAN)
        val channels = inputAudioFormat.channelCount.coerceAtLeast(1)

        when (inputAudioFormat.encoding) {
            C.ENCODING_PCM_16BIT -> {
                val bytesPerFrame = channels * 2
                val totalFrames = snapshot.remaining() / bytesPerFrame
                // If a chunk exceeds FFT_SIZE, jump to the freshest tail so latency stays tight.
                val skipFrames = (totalFrames - FFT_SIZE).coerceAtLeast(0)
                if (skipFrames > 0) {
                    snapshot.position(snapshot.position() + skipFrames * bytesPerFrame)
                }
                while (snapshot.remaining() >= bytesPerFrame) {
                    var sum = 0f
                    for (ch in 0 until channels) {
                        sum += snapshot.short / 32768f
                    }
                    pushSample((sum / channels).coerceIn(-1f, 1f))
                }
            }

            C.ENCODING_PCM_24BIT -> {
                val bytesPerFrame = channels * 3
                val totalFrames = snapshot.remaining() / bytesPerFrame
                val skipFrames = (totalFrames - FFT_SIZE).coerceAtLeast(0)
                if (skipFrames > 0) {
                    snapshot.position(snapshot.position() + skipFrames * bytesPerFrame)
                }
                while (snapshot.remaining() >= bytesPerFrame) {
                    var sum = 0f
                    for (ch in 0 until channels) {
                        val b0 = snapshot.get().toInt() and 0xFF
                        val b1 = snapshot.get().toInt() and 0xFF
                        val b2 = snapshot.get().toInt()
                        val sample24 = (b2 shl 16) or (b1 shl 8) or b0
                        sum += sample24 / 8_388_608f
                    }
                    pushSample((sum / channels).coerceIn(-1f, 1f))
                }
            }

            C.ENCODING_PCM_32BIT -> {
                val bytesPerFrame = channels * 4
                val totalFrames = snapshot.remaining() / bytesPerFrame
                val skipFrames = (totalFrames - FFT_SIZE).coerceAtLeast(0)
                if (skipFrames > 0) {
                    snapshot.position(snapshot.position() + skipFrames * bytesPerFrame)
                }
                while (snapshot.remaining() >= bytesPerFrame) {
                    var sum = 0f
                    for (ch in 0 until channels) {
                        sum += snapshot.int / 2_147_483_648f
                    }
                    pushSample((sum / channels).coerceIn(-1f, 1f))
                }
            }

            C.ENCODING_PCM_FLOAT -> {
                val bytesPerFrame = channels * 4
                val totalFrames = snapshot.remaining() / bytesPerFrame
                val skipFrames = (totalFrames - FFT_SIZE).coerceAtLeast(0)
                if (skipFrames > 0) {
                    snapshot.position(snapshot.position() + skipFrames * bytesPerFrame)
                }
                while (snapshot.remaining() >= bytesPerFrame) {
                    var sum = 0f
                    for (ch in 0 until channels) {
                        sum += snapshot.float
                    }
                    pushSample((sum / channels).coerceIn(-1f, 1f))
                }
            }

            else -> return
        }
    }

    private fun pushSample(sample: Float) {
        ringBuffer[ringWritePos] = sample
        ringWritePos = (ringWritePos + 1) % FFT_SIZE
        if (samplesFilled < FFT_SIZE) {
            samplesFilled++
        }
    }

    private fun publishLatestWindow() {
        val count = samplesFilled
        if (count == FFT_SIZE) {
            val firstPart = FFT_SIZE - ringWritePos
            System.arraycopy(ringBuffer, ringWritePos, orderedSamples, 0, firstPart)
            if (ringWritePos > 0) {
                System.arraycopy(ringBuffer, 0, orderedSamples, firstPart, ringWritePos)
            }
        } else {
            System.arraycopy(ringBuffer, 0, orderedSamples, 0, count)
        }

        val frame = analyzer.analyze(
            monoSamples = orderedSamples,
            sampleCount = count,
            bandCount = bandCountProvider()
        )
        visualizerBus.publish(frame)
    }

    companion object {
        private const val FFT_SIZE = 1024
        private const val MIN_SAMPLES_TO_ANALYZE = 256
        private const val FRAME_INTERVAL_MS = 16L
    }
}
