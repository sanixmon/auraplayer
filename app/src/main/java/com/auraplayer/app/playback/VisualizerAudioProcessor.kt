package com.auraplayer.app.playback

import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Media3 AudioProcessor that taps decoded PCM samples into a continuous 4096-sample ring buffer
 * on the audio thread and runs multi-resolution FFT off the audio thread on a dedicated worker.
 */
@OptIn(UnstableApi::class)
class VisualizerAudioProcessor(
    private val visualizerBus: VisualizerBus,
    private val analyzer: AudioSpectrumAnalyzer = AudioSpectrumAnalyzer(
        bassFftSize = FFT_SIZE,
        detailFftSize = DETAIL_FFT_SIZE
    ),
    private val bandCountProvider: () -> Int = { visualizerBus.targetBandCount }
) : BaseAudioProcessor() {

    private val ringBuffer = FloatArray(FFT_SIZE)
    private val stagingSamples = FloatArray(FFT_SIZE)
    private val workerSamples = FloatArray(FFT_SIZE)
    private var ringWritePos = 0
    private var samplesFilled = 0
    private var stagingCount = 0
    private var stagingSampleRate = 44100
    private var stagingTimestampMs = 0L

    private val handoffLock = Any()
    private val workerScheduled = AtomicBoolean(false)
    private val fftExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "aura-fft-worker").apply { isDaemon = true }
    }

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
            scheduleOffThreadAnalysis(now)
        }

        val out = replaceOutputBuffer(remaining)
        out.put(inputBuffer)
        out.flip()
    }

    override fun onFlush() {
        clearBuffersAndResetAnalyzer()
    }

    override fun onReset() {
        clearBuffersAndResetAnalyzer()
    }

    fun release() {
        fftExecutor.shutdownNow()
    }

    private fun clearBuffersAndResetAnalyzer() {
        synchronized(handoffLock) {
            ringWritePos = 0
            samplesFilled = 0
            stagingCount = 0
        }
        visualizerBus.clear()
        if (!fftExecutor.isShutdown) {
            fftExecutor.execute {
                analyzer.reset()
            }
        }
    }

    private fun appendToRingBuffer(snapshot: ByteBuffer) {
        snapshot.order(ByteOrder.LITTLE_ENDIAN)
        val channels = inputAudioFormat.channelCount.coerceAtLeast(1)

        when (inputAudioFormat.encoding) {
            C.ENCODING_PCM_16BIT -> {
                val bytesPerFrame = channels * 2
                val totalFrames = snapshot.remaining() / bytesPerFrame
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

    private fun scheduleOffThreadAnalysis(nowElapsedMs: Long) {
        val sampleRate = inputAudioFormat.sampleRate.takeIf { it > 0 } ?: 44100
        synchronized(handoffLock) {
            val count = samplesFilled
            if (count < MIN_SAMPLES_TO_ANALYZE) return
            if (count == FFT_SIZE) {
                val firstPart = FFT_SIZE - ringWritePos
                System.arraycopy(ringBuffer, ringWritePos, stagingSamples, 0, firstPart)
                if (ringWritePos > 0) {
                    System.arraycopy(ringBuffer, 0, stagingSamples, firstPart, ringWritePos)
                }
            } else {
                System.arraycopy(ringBuffer, 0, stagingSamples, 0, count)
            }
            stagingCount = count
            stagingSampleRate = sampleRate
            stagingTimestampMs = nowElapsedMs
        }

        // Conflate pending hops so a busy CPU never builds an analysis backlog.
        if (workerScheduled.compareAndSet(false, true) && !fftExecutor.isShutdown) {
            fftExecutor.execute {
                try {
                    var count: Int
                    var rate: Int
                    var ts: Long
                    synchronized(handoffLock) {
                        count = stagingCount
                        rate = stagingSampleRate
                        ts = stagingTimestampMs
                        if (count > 0) {
                            System.arraycopy(stagingSamples, 0, workerSamples, 0, count)
                        }
                        workerScheduled.set(false)
                    }
                    if (count >= MIN_SAMPLES_TO_ANALYZE) {
                        val frame = analyzer.analyze(
                            monoSamples = workerSamples,
                            sampleCount = count,
                            bandCount = bandCountProvider(),
                            sampleRateHz = rate,
                            timestampMs = ts
                        )
                        visualizerBus.publish(frame)
                    }
                } finally {
                    workerScheduled.set(false)
                }
            }
        }
    }

    companion object {
        private const val FFT_SIZE = 4096
        private const val DETAIL_FFT_SIZE = 1024
        private const val MIN_SAMPLES_TO_ANALYZE = 256
        private const val FRAME_INTERVAL_MS = 16L
    }
}
