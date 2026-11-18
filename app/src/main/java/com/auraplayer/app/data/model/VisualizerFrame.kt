package com.auraplayer.app.data.model

data class VisualizerFrame(
    val spectrumBands: List<Float> = emptyList(),
    val waveform: List<Float> = emptyList(),
    val waveformMax: List<Float> = emptyList(),
    val waveformMin: List<Float> = emptyList(),
    val bassEnergy: Float = 0f,
    val midEnergy: Float = 0f,
    val trebleEnergy: Float = 0f,
    val rmsLevel: Float = 0f,
    val beatPulse: Float = 0f,
    val timestampMs: Long = 0L,
    val bpmEstimate: Float = 0f
) {
    companion object {
        val EMPTY = VisualizerFrame()
    }
}
