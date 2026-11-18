package com.auraplayer.app.playback

import com.auraplayer.app.data.model.VisualizerFrame
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Thread-safe bridge between the Media3 audio rendering thread and the Compose UI layer.
 */
class VisualizerBus {
    private val _frames = MutableStateFlow(VisualizerFrame.EMPTY)
    val frames: StateFlow<VisualizerFrame> = _frames.asStateFlow()

    fun publish(frame: VisualizerFrame) {
        _frames.value = frame
    }

    fun clear() {
        _frames.value = VisualizerFrame.EMPTY
    }
}
