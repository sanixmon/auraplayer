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
    private val bandCountProvider: () -> Int = { visualizerBus.targetBandCount },
    private val beatSensitivityProvider: () -> Float = { visualizerBus.beatSensitivity },
    private val bassBoostProvider: () -> Float = { visualizerBus.bassBoost },
    private val freqMinHzProvider: () -> Int = { visualizerBus.freqMinHz },
    private val freqMaxHzProvider: () -> Int = { visualizerBus.freqMaxHz }
) : BaseAudioProcessor() {

    private val ringBuffer = FloatArray(FFT_SIZE)
    private val hopQueueSamples = Array(HOP_QUEUE_CAPACITY) { FloatArray(FFT_SIZE) }
    private val hopQueueCount = IntArray(HOP_QUEUE_CAPACITY)
    private val hopQueueRate = IntArray(HOP_QUEUE_CAPACITY)
    private val hopQueueTimestampMs = LongArray(HOP_QUEUE_CAPACITY)
    private val workerSamples = FloatArray(FFT_SIZE)

    private var ringWritePos = 0
    private var samplesFilled = 0
    private var samplesSinceLastHop = 0
    private var streamTotalSamples = 0L
    private var streamAnchorElapsedMs = 0L
    private var streamAnchored = false

    private var hopQueueHead = 0
    private var hopQueueTail = 0
    private var hopQueueSize = 0

    private val handoffLock = Any()
    private val workerScheduled = AtomicBoolean(false)
    private val fftExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "aura-fft-worker").apply { isDaemon = true }
    }

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

        val nowElapsedMs = SystemClock.elapsedRealtime()
        appendAndScheduleHops(inputBuffer.duplicate(), nowElapsedMs)

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
            samplesSinceLastHop = 0
            streamTotalSamples = 0L
            streamAnchorElapsedMs = 0L
            streamAnchored = false
            hopQueueHead = 0
            hopQueueTail = 0
            hopQueueSize = 0
        }
        visualizerBus.clear()
        if (!fftExecutor.isShutdown) {
            fftExecutor.execute {
                analyzer.reset()
            }
        }
    }

    private fun appendAndScheduleHops(snapshot: ByteBuffer, nowElapsedMs: Long) {
        snapshot.order(ByteOrder.LITTLE_ENDIAN)
        val channels = inputAudioFormat.channelCount.coerceAtLeast(1)
        val sampleRate = inputAudioFormat.sampleRate.takeIf { it > 0 } ?: 44100
        val hopSamples = (sampleRate / TARGET_FPS).coerceAtLeast(MIN_HOP_SAMPLES)

        if (!streamAnchored) {
            streamAnchored = true
            streamTotalSamples = 0L
            streamAnchorElapsedMs = nowElapsedMs + HAL_OUTPUT_LATENCY_MS
        }

        var enqueuedAny = false

        when (inputAudioFormat.encoding) {
            C.ENCODING_PCM_16BIT -> {
                val bytesPerFrame = channels * 2
                while (snapshot.remaining() >= bytesPerFrame) {
                    var sum = 0f
                    for (ch in 0 until channels) {
                        sum += snapshot.short / 32768f
                    }
                    if (pushSampleAndCheckHop((sum / channels).coerceIn(-1f, 1f), hopSamples, sampleRate, nowElapsedMs)) {
                        enqueuedAny = true
                    }
                }
            }

            C.ENCODING_PCM_24BIT -> {
                val bytesPerFrame = channels * 3
                while (snapshot.remaining() >= bytesPerFrame) {
                    var sum = 0f
                    for (ch in 0 until channels) {
                        val b0 = snapshot.get().toInt() and 0xFF
                        val b1 = snapshot.get().toInt() and 0xFF
                        val b2 = snapshot.get().toInt()
                        val sample24 = (b2 shl 16) or (b1 shl 8) or b0
                        sum += sample24 / 8_388_608f
                    }
                    if (pushSampleAndCheckHop((sum / channels).coerceIn(-1f, 1f), hopSamples, sampleRate, nowElapsedMs)) {
                        enqueuedAny = true
                    }
                }
            }

            C.ENCODING_PCM_32BIT -> {
                val bytesPerFrame = channels * 4
                while (snapshot.remaining() >= bytesPerFrame) {
                    var sum = 0f
                    for (ch in 0 until channels) {
                        sum += snapshot.int / 2_147_483_648f
                    }
                    if (pushSampleAndCheckHop((sum / channels).coerceIn(-1f, 1f), hopSamples, sampleRate, nowElapsedMs)) {
                        enqueuedAny = true
                    }
                }
            }

            C.ENCODING_PCM_FLOAT -> {
                val bytesPerFrame = channels * 4
                while (snapshot.remaining() >= bytesPerFrame) {
                    var sum = 0f
                    for (ch in 0 until channels) {
                        sum += snapshot.float
                    }
                    if (pushSampleAndCheckHop((sum / channels).coerceIn(-1f, 1f), hopSamples, sampleRate, nowElapsedMs)) {
                        enqueuedAny = true
                    }
                }
            }

            else -> return
        }

        if (enqueuedAny) {
            scheduleWorkerDrain()
        }
    }

    private fun pushSampleAndCheckHop(
        sample: Float,
        hopSamples: Int,
        sampleRate: Int,
        nowElapsedMs: Long
    ): Boolean {
        ringBuffer[ringWritePos] = sample
        ringWritePos = (ringWritePos + 1) % FFT_SIZE
        if (samplesFilled < FFT_SIZE) {
            samplesFilled++
        }
        streamTotalSamples++
        samplesSinceLastHop++

        if (samplesSinceLastHop < hopSamples || samplesFilled < MIN_SAMPLES_TO_ANALYZE) {
            return false
        }
        samplesSinceLastHop -= hopSamples

        val audioElapsedMs = (streamTotalSamples * 1000L) / sampleRate
        var playoutTsMs = streamAnchorElapsedMs + audioElapsedMs

        // Re-anchor smoothly after pause/resume or unexpected decoder stall, while preserving
        // sample-accurate monotonic spacing across normal ExoPlayer decode bursts.
        val minExpectedPlayout = nowElapsedMs + HAL_OUTPUT_LATENCY_MS - 35L
        val maxExpectedPlayout = nowElapsedMs + MAX_PIPELINE_LEAD_MS
        if (playoutTsMs < minExpectedPlayout) {
            val targetPlayout = nowElapsedMs + HAL_OUTPUT_LATENCY_MS
            streamAnchorElapsedMs = targetPlayout - audioElapsedMs
            playoutTsMs = targetPlayout
        } else if (playoutTsMs > maxExpectedPlayout) {
            val targetPlayout = nowElapsedMs + MAX_PIPELINE_LEAD_MS
            streamAnchorElapsedMs = targetPlayout - audioElapsedMs
            playoutTsMs = targetPlayout
        }

        enqueueHopSnapshot(sampleRate = sampleRate, timestampMs = playoutTsMs)
        return true
    }

    private fun enqueueHopSnapshot(sampleRate: Int, timestampMs: Long) {
        synchronized(handoffLock) {
            val count = samplesFilled
            if (count < MIN_SAMPLES_TO_ANALYZE) return

            if (hopQueueSize == HOP_QUEUE_CAPACITY) {
                hopQueueHead = (hopQueueHead + 1) % HOP_QUEUE_CAPACITY
                hopQueueSize--
            }

            val dst = hopQueueSamples[hopQueueTail]
            if (count == FFT_SIZE) {
                val firstPart = FFT_SIZE - ringWritePos
                System.arraycopy(ringBuffer, ringWritePos, dst, 0, firstPart)
                if (ringWritePos > 0) {
                    System.arraycopy(ringBuffer, 0, dst, firstPart, ringWritePos)
                }
            } else {
                System.arraycopy(ringBuffer, 0, dst, 0, count)
            }
            hopQueueCount[hopQueueTail] = count
            hopQueueRate[hopQueueTail] = sampleRate
            hopQueueTimestampMs[hopQueueTail] = timestampMs

            hopQueueTail = (hopQueueTail + 1) % HOP_QUEUE_CAPACITY
            hopQueueSize++
        }
    }

    private fun scheduleWorkerDrain() {
        if (workerScheduled.compareAndSet(false, true) && !fftExecutor.isShutdown) {
            fftExecutor.execute {
                try {
                    while (true) {
                        var count = 0
                        var rate = 44100
                        var ts = 0L
                        synchronized(handoffLock) {
                            if (hopQueueSize == 0) {
                                workerScheduled.set(false)
                                return@execute
                            }
                            count = hopQueueCount[hopQueueHead]
                            rate = hopQueueRate[hopQueueHead]
                            ts = hopQueueTimestampMs[hopQueueHead]
                            if (count > 0) {
                                System.arraycopy(hopQueueSamples[hopQueueHead], 0, workerSamples, 0, count)
                            }
                            hopQueueHead = (hopQueueHead + 1) % HOP_QUEUE_CAPACITY
                            hopQueueSize--
                        }
                        if (count >= MIN_SAMPLES_TO_ANALYZE) {
                            val frame = analyzer.analyze(
                                monoSamples = workerSamples,
                                sampleCount = count,
                                bandCount = bandCountProvider(),
                                sampleRateHz = rate,
                                timestampMs = ts,
                                beatSensitivity = beatSensitivityProvider(),
                                bassBoost = bassBoostProvider(),
                                minHz = freqMinHzProvider(),
                                maxHz = freqMaxHzProvider()
                            )
                            visualizerBus.publish(frame)
                        }
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
        private const val MIN_SAMPLES_TO_ANALYZE = 512
        private const val TARGET_FPS = 60
        private const val MIN_HOP_SAMPLES = 256
        private const val HOP_QUEUE_CAPACITY = 16
        private const val HAL_OUTPUT_LATENCY_MS = 35L
        private const val MAX_PIPELINE_LEAD_MS = 260L
    }
}
