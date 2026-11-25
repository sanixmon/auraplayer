package com.auraplayer.app.playback

import android.os.SystemClock
import com.auraplayer.app.data.model.VisualizerFrame
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.max

/**
 * Thread-safe bridge between the off-thread FFT analyzer and Compose UI layer.
 * Maintains a rolling timestamped ring buffer so UI can sample temporally interpolated
 * frames at 60/90/120 Hz and compensate for Bluetooth / hardware AudioTrack latency.
 */
class VisualizerBus {
    private val _frames = MutableStateFlow(VisualizerFrame.EMPTY)
    val frames: StateFlow<VisualizerFrame> = _frames.asStateFlow()

    @Volatile
    var targetBandCount: Int = 64

    @Volatile
    var beatSensitivity: Float = 1.0f

    @Volatile
    var bassBoost: Float = 1.0f

    @Volatile
    var freqMinHz: Int = 30

    @Volatile
    var freqMaxHz: Int = 16000

    private val lock = Any()
    private val history = Array<VisualizerFrame?>(HISTORY_CAPACITY) { null }
    private var writePos = 0
    private var historyCount = 0

    fun publish(frame: VisualizerFrame) {
        _frames.value = frame
        if (frame.spectrumBands.isEmpty()) return
        synchronized(lock) {
            history[writePos] = frame
            writePos = (writePos + 1) % HISTORY_CAPACITY
            if (historyCount < HISTORY_CAPACITY) {
                historyCount++
            }
        }
    }

    fun clear() {
        _frames.value = VisualizerFrame.EMPTY
        synchronized(lock) {
            history.fill(null)
            writePos = 0
            historyCount = 0
        }
    }

    /**
     * Reads and linearly interpolates a spectrum + waveform snapshot at [nowElapsedMs] - [delayMs]
     * directly into caller-owned pre-allocated arrays [outBands] and [outWave].
     *
     * Returns true when valid interpolated audio data was written, false if silent/empty.
     */
    fun sampleInterpolatedInto(
        delayMs: Int,
        outBands: FloatArray,
        outWave: FloatArray,
        outScalars: FloatArray,
        nowElapsedMs: Long = SystemClock.elapsedRealtime(),
        outWaveMax: FloatArray? = null,
        outWaveMin: FloatArray? = null
    ): Boolean {
        var f0: VisualizerFrame? = null
        var f1: VisualizerFrame? = null
        var tFraction = 0f

        synchronized(lock) {
            if (historyCount == 0) {
                return false
            }

            val latestIdx = (writePos - 1 + HISTORY_CAPACITY) % HISTORY_CAPACITY
            val latest = history[latestIdx] ?: return false
            val newestTs = latest.timestampMs
            if (newestTs <= 0L) {
                f0 = latest
                f1 = latest
                tFraction = 0f
            } else {
                // Frames carry sample-accurate audible playout timestamps; user delayMs adds
                // fine negative/positive offset for speaker or Bluetooth latency.
                val effectiveDelay = delayMs.coerceIn(-150, 500)
                val targetTs = (nowElapsedMs - effectiveDelay).coerceAtMost(newestTs)

                // If stream paused cleanly and stale history exceeds 350 ms past target, treat as settled.
                if (nowElapsedMs - newestTs > max(0, effectiveDelay) + 350L) {
                    return false
                }

                val startIdx = (writePos - historyCount + HISTORY_CAPACITY) % HISTORY_CAPACITY
                var prev: VisualizerFrame? = null
                var next: VisualizerFrame? = null
                for (i in 0 until historyCount) {
                    val idx = (startIdx + i) % HISTORY_CAPACITY
                    val candidate = history[idx] ?: continue
                    if (candidate.timestampMs <= targetTs) {
                        prev = candidate
                    } else {
                        next = candidate
                        break
                    }
                }

                when {
                    prev == null && next == null -> return false
                    prev == null -> {
                        f0 = next
                        f1 = next
                        tFraction = 0f
                    }
                    next == null -> {
                        f0 = prev
                        f1 = prev
                        tFraction = 0f
                    }
                    else -> {
                        f0 = prev
                        f1 = next
                        val span = (next.timestampMs - prev.timestampMs).coerceAtLeast(1L)
                        tFraction = ((targetTs - prev.timestampMs).toFloat() / span).coerceIn(0f, 1f)
                    }
                }
            }
        }

        val a = f0 ?: return false
        val b = f1 ?: a
        if (a.spectrumBands.isEmpty()) return false

        interpolateSeries(a.spectrumBands, b.spectrumBands, tFraction, outBands)
        interpolateSeries(a.waveform, b.waveform, tFraction, outWave)
        outWaveMax?.let { interpolateSeries(a.waveformMax, b.waveformMax, tFraction, it) }
        outWaveMin?.let { interpolateSeries(a.waveformMin, b.waveformMin, tFraction, it) }

        val inv = 1f - tFraction
        if (outScalars.size >= SCALAR_COUNT) {
            outScalars[SCALAR_BASS] = a.bassEnergy * inv + b.bassEnergy * tFraction
            outScalars[SCALAR_MID] = a.midEnergy * inv + b.midEnergy * tFraction
            outScalars[SCALAR_TREBLE] = a.trebleEnergy * inv + b.trebleEnergy * tFraction
            outScalars[SCALAR_RMS] = a.rmsLevel * inv + b.rmsLevel * tFraction
            outScalars[SCALAR_BEAT] = a.beatPulse * inv + b.beatPulse * tFraction
            outScalars[SCALAR_BPM] = if (b.bpmEstimate > 0f) b.bpmEstimate else a.bpmEstimate
        }

        return true
    }

    private fun interpolateSeries(
        listA: List<Float>,
        listB: List<Float>,
        tFraction: Float,
        out: FloatArray
    ) {
        if (listA.isEmpty()) {
            out.fill(0f)
            return
        }
        val inv = 1f - tFraction
        for (i in out.indices) {
            val idxA = ((i.toFloat() / out.size) * listA.size)
                .toInt()
                .coerceIn(0, listA.lastIndex)
            val valA = listA[idxA]
            val valB = if (listB.isNotEmpty()) {
                val idxB = ((i.toFloat() / out.size) * listB.size)
                    .toInt()
                    .coerceIn(0, listB.lastIndex)
                listB[idxB]
            } else {
                valA
            }
            out[i] = valA * inv + valB * tFraction
        }
    }

    companion object {
        private const val HISTORY_CAPACITY = 96
        const val SCALAR_BASS = 0
        const val SCALAR_MID = 1
        const val SCALAR_TREBLE = 2
        const val SCALAR_RMS = 3
        const val SCALAR_BEAT = 4
        const val SCALAR_BPM = 5
        const val SCALAR_COUNT = 6
    }
}
