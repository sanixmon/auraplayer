package com.auraplayer.app.playback

import android.os.SystemClock
import com.auraplayer.app.data.model.VisualizerFrame
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.max
import kotlin.math.min

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
        nowElapsedMs: Long = SystemClock.elapsedRealtime()
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
                // Micro-lookback of 12 ms when delayMs == 0 enables smooth 60->120 Hz sub-frame tweening.
                val effectiveDelay = max(12, delayMs.coerceIn(0, 500))
                val targetTs = (nowElapsedMs - effectiveDelay).coerceAtMost(newestTs)

                // If stream paused cleanly and stale history exceeds 350 ms past target, treat as settled.
                if (nowElapsedMs - newestTs > effectiveDelay + 350L) {
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
        val aBands = a.spectrumBands
        val bBands = b.spectrumBands
        if (aBands.isEmpty()) return false

        val inv = 1f - tFraction
        for (i in outBands.indices) {
            val idxA = ((i.toFloat() / outBands.size) * aBands.size)
                .toInt()
                .coerceIn(0, aBands.lastIndex)
            val valA = aBands[idxA]
            val valB = if (bBands.isNotEmpty()) {
                val idxB = ((i.toFloat() / outBands.size) * bBands.size)
                    .toInt()
                    .coerceIn(0, bBands.lastIndex)
                bBands[idxB]
            } else {
                valA
            }
            outBands[i] = valA * inv + valB * tFraction
        }

        val aWave = a.waveform
        val bWave = b.waveform
        if (aWave.isNotEmpty()) {
            for (i in outWave.indices) {
                val idxA = ((i.toFloat() / outWave.size) * aWave.size)
                    .toInt()
                    .coerceIn(0, aWave.lastIndex)
                val valA = aWave[idxA]
                val valB = if (bWave.isNotEmpty()) {
                    val idxB = ((i.toFloat() / outWave.size) * bWave.size)
                        .toInt()
                        .coerceIn(0, bWave.lastIndex)
                    bWave[idxB]
                } else {
                    valA
                }
                outWave[i] = valA * inv + valB * tFraction
            }
        } else {
            outWave.fill(0f)
        }

        if (outScalars.size >= SCALAR_COUNT) {
            outScalars[SCALAR_BASS] = a.bassEnergy * inv + b.bassEnergy * tFraction
            outScalars[SCALAR_MID] = a.midEnergy * inv + b.midEnergy * tFraction
            outScalars[SCALAR_TREBLE] = a.trebleEnergy * inv + b.trebleEnergy * tFraction
            outScalars[SCALAR_RMS] = a.rmsLevel * inv + b.rmsLevel * tFraction
            outScalars[SCALAR_BEAT] = max(a.beatPulse, b.beatPulse * min(1f, tFraction + 0.5f))
            outScalars[SCALAR_BPM] = if (b.bpmEstimate > 0f) b.bpmEstimate else a.bpmEstimate
        }

        return true
    }

    companion object {
        private const val HISTORY_CAPACITY = 64
        const val SCALAR_BASS = 0
        const val SCALAR_MID = 1
        const val SCALAR_TREBLE = 2
        const val SCALAR_RMS = 3
        const val SCALAR_BEAT = 4
        const val SCALAR_BPM = 5
        const val SCALAR_COUNT = 6
    }
}
