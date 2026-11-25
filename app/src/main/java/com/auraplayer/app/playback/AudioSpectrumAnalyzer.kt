package com.auraplayer.app.playback

import com.auraplayer.app.data.model.VisualizerFrame
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Multi-resolution FFT audio spectrum, dB-domain normalizer, and pre-AGC spectral flux
 * beat-onset analyzer.
 *
 * - Bass frequencies (< 500 Hz) use a high-resolution [bassFftSize] FFT (~10.7 Hz/bin @ 44.1 kHz).
 * - Mid/treble frequencies (>= 500 Hz) use a responsive [detailFftSize] FFT.
 * - Band edges are logarithmically spaced from 30 Hz to 16 kHz with fractional bin overlap
 *   weights and +3 dB/octave tilt compensation.
 * - Beat detection operates on pre-AGC bass spectral flux with an adaptive rolling threshold.
 */
class AudioSpectrumAnalyzer(
    private val bassFftSize: Int = 4096,
    private val detailFftSize: Int = 1024,
    private val waveformPoints: Int = 64
) {
    init {
        require(bassFftSize > 0 && (bassFftSize and (bassFftSize - 1)) == 0) {
            "bassFftSize must be a positive power of 2"
        }
        require(detailFftSize > 0 && (detailFftSize and (detailFftSize - 1)) == 0) {
            "detailFftSize must be a positive power of 2"
        }
        require(waveformPoints >= 2 && waveformPoints % 2 == 0) {
            "waveformPoints must be a positive even integer"
        }
    }

    private val bassHann = FloatArray(bassFftSize) { i ->
        (0.5 * (1.0 - cos(2.0 * PI * i / (bassFftSize - 1)))).toFloat()
    }
    private val detailHann = FloatArray(detailFftSize) { i ->
        (0.5 * (1.0 - cos(2.0 * PI * i / (detailFftSize - 1)))).toFloat()
    }

    private val bassReal = FloatArray(bassFftSize)
    private val bassImag = FloatArray(bassFftSize)
    private val bassMag = FloatArray(bassFftSize / 2)

    private val detailReal = FloatArray(detailFftSize)
    private val detailImag = FloatArray(detailFftSize)
    private val detailMag = FloatArray(detailFftSize / 2)

    private val rawDbBands = FloatArray(MAX_BANDS)
    private val tiltedDbBands = FloatArray(MAX_BANDS)
    private val normalizedBands = FloatArray(MAX_BANDS)
    private val prevDetailKickMag = FloatArray(detailFftSize / 2)
    private val waveformScratch = FloatArray(waveformPoints)
    private val waveformMaxScratch = FloatArray(waveformPoints)
    private val waveformMinScratch = FloatArray(waveformPoints)

    private val bandLowHz = DoubleArray(MAX_BANDS)
    private val bandHighHz = DoubleArray(MAX_BANDS)
    private val bandCenterHz = DoubleArray(MAX_BANDS)
    private val bandTiltDb = FloatArray(MAX_BANDS)
    private var cachedPlanBandCount = -1
    private var cachedPlanSampleRate = -1
    private var cachedPlanMinHz = -1
    private var cachedPlanMaxHz = -1

    private val fluxHistory = FloatArray(FLUX_WINDOW_SIZE)
    private var fluxWriteIdx = 0
    private var fluxCount = 0

    private var peakDbTracker = INITIAL_PEAK_DB
    private var lastAnalyzeTimestampMs = -1L
    private var lastBeatTimestampMs = -1L
    private var lastBeatTriggerFlux = 0f
    private var beatEnvelope = 0f
    private var bpmEstimate = 0f

    fun reset() {
        peakDbTracker = INITIAL_PEAK_DB
        lastAnalyzeTimestampMs = -1L
        lastBeatTimestampMs = -1L
        lastBeatTriggerFlux = 0f
        beatEnvelope = 0f
        bpmEstimate = 0f
        fluxWriteIdx = 0
        fluxCount = 0
        fluxHistory.fill(0f)
        prevDetailKickMag.fill(0f)
    }

    fun analyze(
        monoSamples: FloatArray,
        sampleCount: Int = monoSamples.size,
        bandCount: Int = 64,
        sampleRateHz: Int = 44100,
        timestampMs: Long = 0L,
        beatSensitivity: Float = 1.0f,
        bassBoost: Float = 1.0f,
        minHz: Int = 30,
        maxHz: Int = 16000
    ): VisualizerFrame {
        if (sampleCount <= 0 || monoSamples.isEmpty()) {
            return VisualizerFrame.EMPTY
        }

        val count = min(sampleCount, monoSamples.size)
        val safeBandCount = bandCount.coerceIn(4, MAX_BANDS)
        val safeRate = sampleRateHz.coerceIn(8000, 192000)

        val dt = if (timestampMs > 0L && lastAnalyzeTimestampMs > 0L && timestampMs > lastAnalyzeTimestampMs) {
            ((timestampMs - lastAnalyzeTimestampMs) / 1000f).coerceIn(0.004f, 0.25f)
        } else {
            0.016f
        }
        if (timestampMs > 0L) {
            lastAnalyzeTimestampMs = timestampMs
        }

        val rmsWindow = min(count, detailFftSize)
        val rmsStart = count - rmsWindow
        var sumSquares = 0.0
        for (i in rmsStart until count) {
            val s = monoSamples[i].coerceIn(-1f, 1f)
            sumSquares += s * s
        }
        val rms = sqrt(sumSquares / max(1, rmsWindow)).toFloat().coerceIn(0f, 1f)

        downsampleWaveformMinMax(monoSamples, count)

        // Noise gate at ~ -60 dB RMS (1e-3): suppress noise floor cleanly during silence.
        if (rms < NOISE_GATE_RMS) {
            val releaseAlpha = 1f - exp(-dt / PEAK_RELEASE_SEC)
            peakDbTracker += (PEAK_FLOOR_DB - peakDbTracker) * releaseAlpha
            prevDetailKickMag.fill(0f)
            updateBeatAndBpm(
                avgFlux = 0f,
                timestampMs = timestampMs,
                dt = dt,
                beatSensitivity = beatSensitivity
            )
            val zeroBands = List(safeBandCount) { 0f }
            val waveList = waveformScratch.toList()
            val waveMaxList = waveformMaxScratch.toList()
            val waveMinList = waveformMinScratch.toList()
            return VisualizerFrame(
                spectrumBands = zeroBands,
                waveform = waveList,
                waveformMax = waveMaxList,
                waveformMin = waveMinList,
                bassEnergy = 0f,
                midEnergy = 0f,
                trebleEnergy = 0f,
                rmsLevel = rms,
                beatPulse = if (beatEnvelope > 0.02f) beatEnvelope else 0f,
                timestampMs = timestampMs,
                bpmEstimate = bpmEstimate
            )
        }

        computeWindowedFftMagnitudes(
            samples = monoSamples,
            totalSampleCount = count,
            fftSize = bassFftSize,
            hann = bassHann,
            re = bassReal,
            im = bassImag,
            outMag = bassMag
        )
        computeWindowedFftMagnitudes(
            samples = monoSamples,
            totalSampleCount = count,
            fftSize = detailFftSize,
            hann = detailHann,
            re = detailReal,
            im = detailImag,
            outMag = detailMag
        )

        ensureBandPlan(
            bandCount = safeBandCount,
            sampleRateHz = safeRate,
            minHzInt = minHz,
            maxHzInt = maxHz
        )

        val bassBinRes = safeRate.toDouble() / bassFftSize
        val detailBinRes = safeRate.toDouble() / detailFftSize
        val safeBassBoost = bassBoost.coerceIn(0.5f, 2.0f)
        val bassShelfDb = (safeBassBoost - 1.0f) * 8.0f
        var frameMaxTiltedDb = -90f

        for (b in 0 until safeBandCount) {
            val fLow = bandLowHz[b]
            val fHigh = bandHighHz[b]
            val fCenter = bandCenterHz[b]
            val useBassFft = fCenter < BASS_CROSSOVER_HZ
            val magArray = if (useBassFft) bassMag else detailMag
            val binRes = if (useBassFft) bassBinRes else detailBinRes

            val amp = sampleFractionalBandAmplitude(
                magnitudes = magArray,
                binResHz = binRes,
                fLowHz = fLow,
                fHighHz = fHigh,
                fCenterHz = fCenter
            )
            val rawDb = (20.0 * log10(max(amp.toDouble(), 1e-6))).toFloat()
            rawDbBands[b] = rawDb
            val lowShelfWeight = when {
                fCenter <= 160.0 -> 1f
                fCenter >= 400.0 -> 0f
                else -> ((400.0 - fCenter) / 240.0).toFloat()
            }
            val tilted = rawDb + bandTiltDb[b] + bassShelfDb * lowShelfWeight
            tiltedDbBands[b] = tilted
            if (rawDb > NOISE_GATE_DB && tilted > frameMaxTiltedDb) {
                frameMaxTiltedDb = tilted
            }
        }

        // Fast-FFT (~23 ms) pre-AGC kick/low-mid spectral flux in compressed linear amplitude domain.
        // Avoids the 93 ms temporal smearing of the 4096-pt bass FFT and preserves kick contrast
        // even on loudly mastered tracks with continuous basslines.
        val kStart = max(1, floor(30.0 / detailBinRes).toInt()).coerceAtMost(detailMag.lastIndex)
        val kBassEnd = ceil(220.0 / detailBinRes).toInt().coerceIn(kStart, detailMag.lastIndex)
        val kPunchEnd = ceil(420.0 / detailBinRes).toInt().coerceIn(kBassEnd, detailMag.lastIndex)
        var kickFlux = 0f
        var kickWeightSum = 0f
        for (k in kStart..kPunchEnd) {
            val weight = if (k <= kBassEnd) 1.0f else 0.35f
            val mag = sqrt(detailMag[k].coerceAtLeast(0f))
            val diff = mag - prevDetailKickMag[k]
            if (diff > 0f) {
                kickFlux += diff * weight
            }
            prevDetailKickMag[k] = mag
            kickWeightSum += weight
        }

        // Track signal ceiling in dB domain: fast attack (~45ms), slow release (~2.0s).
        if (frameMaxTiltedDb > NOISE_GATE_DB) {
            val trackerTau = if (frameMaxTiltedDb > peakDbTracker) PEAK_ATTACK_SEC else PEAK_RELEASE_SEC
            val trackerAlpha = 1f - exp(-dt / trackerTau)
            peakDbTracker += (frameMaxTiltedDb - peakDbTracker) * trackerAlpha
            peakDbTracker = peakDbTracker.coerceIn(PEAK_FLOOR_DB, 6f)
        } else {
            val releaseAlpha = 1f - exp(-dt / PEAK_RELEASE_SEC)
            peakDbTracker += (PEAK_FLOOR_DB - peakDbTracker) * releaseAlpha
        }

        val normShiftDb = (0f - peakDbTracker).coerceIn(0f, MAX_GAIN_DB)
        for (b in 0 until safeBandCount) {
            normalizedBands[b] = if (rawDbBands[b] < NOISE_GATE_DB) {
                0f
            } else {
                val scaledDb = (tiltedDbBands[b] + normShiftDb).coerceIn(-DB_SPAN, 0f)
                val linearNorm = ((scaledDb + DB_SPAN) / DB_SPAN).coerceIn(0f, 1f)
                linearNorm.pow(BAND_CONTRAST_GAMMA)
            }
        }

        // Update adaptive threshold for fast kick spectral flux beat detection.
        val avgBassFlux = kickFlux / max(1f, kickWeightSum)
        updateBeatAndBpm(
            avgFlux = avgBassFlux,
            timestampMs = timestampMs,
            dt = dt,
            beatSensitivity = beatSensitivity
        )

        // Aggregate perceptual region energies across mapped bands (log boundaries ~250 Hz / ~2.5 kHz).
        var bassSum = 0f
        var bassCount = 0
        var midSum = 0f
        var midCount = 0
        var trebleSum = 0f
        var trebleCount = 0
        for (b in 0 until safeBandCount) {
            val fc = bandCenterHz[b]
            val v = normalizedBands[b]
            when {
                fc < 250.0 -> {
                    bassSum += v
                    bassCount++
                }
                fc < 2500.0 -> {
                    midSum += v
                    midCount++
                }
                else -> {
                    trebleSum += v
                    trebleCount++
                }
            }
        }

        val bassEnergy = if (bassCount > 0) (bassSum / bassCount).coerceIn(0f, 1f) else 0f
        val midEnergy = if (midCount > 0) (midSum / midCount).coerceIn(0f, 1f) else 0f
        val trebleEnergy = if (trebleCount > 0) (trebleSum / trebleCount).coerceIn(0f, 1f) else 0f

        val bandsOut = List(safeBandCount) { i -> normalizedBands[i] }
        val waveOut = List(waveformPoints) { i -> waveformScratch[i] }
        val waveMaxOut = List(waveformPoints) { i -> waveformMaxScratch[i] }
        val waveMinOut = List(waveformPoints) { i -> waveformMinScratch[i] }

        return VisualizerFrame(
            spectrumBands = bandsOut,
            waveform = waveOut,
            waveformMax = waveMaxOut,
            waveformMin = waveMinOut,
            bassEnergy = bassEnergy,
            midEnergy = midEnergy,
            trebleEnergy = trebleEnergy,
            rmsLevel = rms,
            beatPulse = beatEnvelope.coerceIn(0f, 1f),
            timestampMs = timestampMs,
            bpmEstimate = bpmEstimate
        )
    }

    private fun computeWindowedFftMagnitudes(
        samples: FloatArray,
        totalSampleCount: Int,
        fftSize: Int,
        hann: FloatArray,
        re: FloatArray,
        im: FloatArray,
        outMag: FloatArray
    ) {
        val used = min(totalSampleCount, fftSize)
        val srcStart = totalSampleCount - used
        val dstStart = fftSize - used
        var coherentGain = 0f

        for (i in 0 until dstStart) {
            re[i] = 0f
            im[i] = 0f
        }
        for (i in 0 until used) {
            val winIdx = dstStart + i
            val w = hann[winIdx]
            re[winIdx] = samples[srcStart + i].coerceIn(-1f, 1f) * w
            im[winIdx] = 0f
            coherentGain += w
        }

        fft(re, im)

        // Single-sided coherent amplitude normalization: a full-scale sine yields amplitude 1.0 (0 dB).
        val scale = 2.0f / max(1f, coherentGain)
        val half = fftSize / 2
        for (k in 0 until half) {
            outMag[k] = hypot(re[k], im[k]) * scale
        }
    }

    private fun ensureBandPlan(
        bandCount: Int,
        sampleRateHz: Int,
        minHzInt: Int = 30,
        maxHzInt: Int = 16000
    ) {
        val safeMinInt = minHzInt.coerceIn(20, 500)
        val safeMaxInt = maxHzInt.coerceIn(max(1000, safeMinInt + 500), 16000)
        if (cachedPlanBandCount == bandCount &&
            cachedPlanSampleRate == sampleRateHz &&
            cachedPlanMinHz == safeMinInt &&
            cachedPlanMaxHz == safeMaxInt
        ) {
            return
        }

        val minHz = safeMinInt.toDouble()
        val maxHz = min(safeMaxInt.toDouble(), sampleRateHz * 0.48).coerceAtLeast(minHz + 100.0)
        val ratio = maxHz / minHz
        val ln2Inv = 1.0 / ln(2.0)

        for (b in 0 until bandCount) {
            val t0 = b.toDouble() / bandCount
            val t1 = (b + 1).toDouble() / bandCount
            val fLow = minHz * ratio.pow(t0)
            val fHigh = minHz * ratio.pow(t1)
            val fCenter = sqrt(fLow * fHigh)

            bandLowHz[b] = fLow
            bandHighHz[b] = fHigh
            bandCenterHz[b] = fCenter
            // Standard +3 dB/octave pink-spectrum compensation anchored at 100 Hz.
            val octavesFromAnchor = ln(max(0.25, fCenter / 100.0)) * ln2Inv
            bandTiltDb[b] = (3.0 * octavesFromAnchor).toFloat()
        }

        cachedPlanBandCount = bandCount
        cachedPlanSampleRate = sampleRateHz
        cachedPlanMinHz = safeMinInt
        cachedPlanMaxHz = safeMaxInt
    }

    private fun sampleFractionalBandAmplitude(
        magnitudes: FloatArray,
        binResHz: Double,
        fLowHz: Double,
        fHighHz: Double,
        fCenterHz: Double
    ): Float {
        val maxBin = magnitudes.lastIndex
        val bStart = (fLowHz / binResHz).coerceIn(1.0, maxBin.toDouble())
        val bEnd = (fHighHz / binResHz).coerceIn(bStart, maxBin.toDouble())

        if (bEnd - bStart < 1.0) {
            // Sub-bin low-frequency band: smoothly interpolate between adjacent FFT bins.
            val centerBin = (fCenterHz / binResHz).coerceIn(1.0, maxBin.toDouble())
            val k0 = floor(centerBin).toInt().coerceIn(1, maxBin)
            val k1 = (k0 + 1).coerceAtMost(maxBin)
            val frac = (centerBin - k0).toFloat().coerceIn(0f, 1f)
            return magnitudes[k0] * (1f - frac) + magnitudes[k1] * frac
        }

        val kFirst = floor(bStart).toInt().coerceIn(1, maxBin)
        val kLast = ceil(bEnd).toInt().coerceIn(kFirst, maxBin)
        var weightedPower = 0.0
        var totalWeight = 0.0

        for (k in kFirst..kLast) {
            val binLow = k - 0.5
            val binHigh = k + 0.5
            val overlap = min(bEnd, binHigh) - max(bStart, binLow)
            if (overlap > 0.0) {
                val m = magnitudes[k].toDouble()
                weightedPower += m * m * overlap
                totalWeight += overlap
            }
        }

        return if (totalWeight > 0.0) {
            sqrt(weightedPower / totalWeight).toFloat()
        } else {
            magnitudes[kFirst]
        }
    }

    private fun updateBeatAndBpm(
        avgFlux: Float,
        timestampMs: Long,
        dt: Float,
        beatSensitivity: Float
    ) {
        // Compute adaptive baseline from PRIOR history before inserting the current frame,
        // so a strong kick transient never raises its own threshold.
        val priorCount = fluxCount
        var mean = 0f
        var std = 0f
        if (priorCount > 0) {
            var sum = 0f
            for (i in 0 until priorCount) {
                sum += fluxHistory[i]
            }
            mean = sum / priorCount

            var sqDiffSum = 0f
            for (i in 0 until priorCount) {
                val d = fluxHistory[i] - mean
                sqDiffSum += d * d
            }
            std = sqrt(sqDiffSum / priorCount)
        }

        val safeSens = beatSensitivity.coerceIn(0.5f, 2.0f)
        val minThresh = (MIN_FLUX_THRESHOLD / safeSens).coerceIn(0.010f, 0.055f)
        val stdMult = (FLUX_STD_MULTIPLIER / safeSens).coerceIn(0.65f, 2.4f)
        val adaptiveThreshold = max(minThresh, mean + stdMult * std)

        val effectiveTs = if (timestampMs > 0L) timestampMs else {
            (if (lastBeatTimestampMs < 0L) 200L else lastBeatTimestampMs + 16L)
        }
        val sinceLastBeatMs = if (lastBeatTimestampMs < 0L) Long.MAX_VALUE else effectiveTs - lastBeatTimestampMs
        val cooldownPassed = lastBeatTimestampMs < 0L || sinceLastBeatMs >= MIN_BEAT_INTERVAL_MS

        if (priorCount >= 4 && avgFlux > adaptiveThreshold && cooldownPassed) {
            if (lastBeatTimestampMs > 0L && sinceLastBeatMs in 300L..1600L) {
                val instantBpm = 60_000f / sinceLastBeatMs.toFloat()
                bpmEstimate = if (bpmEstimate <= 0f) {
                    instantBpm
                } else {
                    bpmEstimate * 0.8f + instantBpm * 0.2f
                }
            }
            lastBeatTimestampMs = effectiveTs
            lastBeatTriggerFlux = avgFlux
            val excess = (avgFlux - adaptiveThreshold) / max(adaptiveThreshold, 0.035f)
            beatEnvelope = max(beatEnvelope, (0.52f + excess * 0.48f).coerceIn(0.52f, 1f))
        } else if (sinceLastBeatMs in 1L..35L && avgFlux > lastBeatTriggerFlux) {
            lastBeatTriggerFlux = avgFlux
            val excess = (avgFlux - adaptiveThreshold) / max(adaptiveThreshold, 0.035f)
            beatEnvelope = max(beatEnvelope, (0.52f + excess * 0.48f).coerceIn(0.52f, 1f))
        } else {
            beatEnvelope *= exp(-dt / BEAT_DECAY_SEC)
        }

        fluxHistory[fluxWriteIdx] = avgFlux
        fluxWriteIdx = (fluxWriteIdx + 1) % FLUX_WINDOW_SIZE
        if (fluxCount < FLUX_WINDOW_SIZE) {
            fluxCount++
        }
    }

    private fun downsampleWaveformMinMax(samples: FloatArray, sampleCount: Int) {
        val window = min(sampleCount, detailFftSize)
        val startOffset = sampleCount - window
        val samplesPerBucket = window.toFloat() / waveformPoints

        for (b in 0 until waveformPoints) {
            val bStart = startOffset + (b * samplesPerBucket).toInt().coerceIn(0, window - 1)
            val bEnd = startOffset + max(bStart - startOffset + 1, ((b + 1) * samplesPerBucket).toInt())
                .coerceIn(1, window)
            var segMin = 1f
            var segMax = -1f
            var segSum = 0f
            for (idx in bStart until bEnd) {
                val v = samples[idx].coerceIn(-1f, 1f)
                if (v < segMin) segMin = v
                if (v > segMax) segMax = v
                segSum += v
            }
            val countInSeg = max(1, bEnd - bStart)
            waveformScratch[b] = (segSum / countInSeg).coerceIn(-1f, 1f)
            waveformMaxScratch[b] = segMax
            waveformMinScratch[b] = segMin
        }
    }

    private fun fft(re: FloatArray, im: FloatArray) {
        val n = re.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                val tmpRe = re[i]
                re[i] = re[j]
                re[j] = tmpRe

                val tmpIm = im[i]
                im[i] = im[j]
                im[j] = tmpIm
            }
        }

        var len = 2
        while (len <= n) {
            val ang = -2.0 * PI / len
            val wLenRe = cos(ang).toFloat()
            val wLenIm = sin(ang).toFloat()
            var i = 0
            while (i < n) {
                var wRe = 1f
                var wIm = 0f
                val half = len shr 1
                for (k in 0 until half) {
                    val uRe = re[i + k]
                    val uIm = im[i + k]
                    val tRe = re[i + k + half] * wRe - im[i + k + half] * wIm
                    val tIm = re[i + k + half] * wIm + im[i + k + half] * wRe
                    re[i + k] = uRe + tRe
                    im[i + k] = uIm + tIm
                    re[i + k + half] = uRe - tRe
                    im[i + k + half] = uIm - tIm
                    val nextWRe = wRe * wLenRe - wIm * wLenIm
                    wIm = wRe * wLenIm + wIm * wLenRe
                    wRe = nextWRe
                }
                i += len
            }
            len = len shl 1
        }
    }

    private companion object {
        private const val MAX_BANDS = 96
        private const val BASS_CROSSOVER_HZ = 500.0
        private const val DB_SPAN = 50f
        private const val BAND_CONTRAST_GAMMA = 1.35f
        private const val NOISE_GATE_DB = -60f
        private const val NOISE_GATE_RMS = 0.001f
        private const val INITIAL_PEAK_DB = -12f
        private const val PEAK_FLOOR_DB = -34f
        private const val MAX_GAIN_DB = 34f
        private const val PEAK_ATTACK_SEC = 0.045f
        private const val PEAK_RELEASE_SEC = 2.0f
        private const val FLUX_WINDOW_SIZE = 48
        private const val MIN_FLUX_THRESHOLD = 0.022f
        private const val FLUX_STD_MULTIPLIER = 1.35f
        private const val MIN_BEAT_INTERVAL_MS = 180L
        private const val BEAT_DECAY_SEC = 0.095f
    }
}
