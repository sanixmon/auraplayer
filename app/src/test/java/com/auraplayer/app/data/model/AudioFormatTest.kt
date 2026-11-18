package com.auraplayer.app.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioFormatTest {

    @Test
    fun `at least 10 offline audio formats are supported including FFmpeg extended codecs`() {
        assertTrue(AudioFormat.supportedFormats.size >= 20)
        assertEquals(21, AudioFormat.supportedFormats.size)
    }

    @Test
    fun `fromMetadata resolves formats by MIME type`() {
        assertEquals(AudioFormat.MP3, AudioFormat.fromMetadata("audio/mpeg", "song.bin"))
        assertEquals(AudioFormat.FLAC, AudioFormat.fromMetadata("audio/flac", null))
        assertEquals(AudioFormat.WAV, AudioFormat.fromMetadata("audio/x-wav", null))
        assertEquals(AudioFormat.OGG, AudioFormat.fromMetadata("audio/ogg", null))
        assertEquals(AudioFormat.OPUS, AudioFormat.fromMetadata("audio/opus", null))
        assertEquals(AudioFormat.ALAC, AudioFormat.fromMetadata("audio/alac", null))
        assertEquals(AudioFormat.WMA, AudioFormat.fromMetadata("audio/x-ms-wma", null))
    }

    @Test
    fun `fromMetadata falls back to extension when MIME is generic`() {
        assertEquals(AudioFormat.M4A, AudioFormat.fromMetadata("audio/*", "content://doc/track.m4a"))
        assertEquals(AudioFormat.AAC, AudioFormat.fromMetadata("", "sample.aac?token=1"))
        assertEquals(AudioFormat.APE, AudioFormat.fromMetadata(null, "master.ape"))
        assertEquals(AudioFormat.WV, AudioFormat.fromMetadata(null, "archive.wv"))
        assertEquals(AudioFormat.DTS, AudioFormat.fromMetadata(null, "surround.dts"))
    }

    @Test
    fun `lossless formats are flagged accurately`() {
        assertTrue(AudioFormat.FLAC.isLossless)
        assertTrue(AudioFormat.WAV.isLossless)
        assertTrue(AudioFormat.ALAC.isLossless)
        assertTrue(AudioFormat.APE.isLossless)
        assertTrue(AudioFormat.WV.isLossless)
        assertTrue(AudioFormat.TTA.isLossless)
        assertFalse(AudioFormat.MP3.isLossless)
        assertFalse(AudioFormat.OPUS.isLossless)
        assertFalse(AudioFormat.WMA.isLossless)
    }

    @Test
    fun `TrackFingerprint normalizes punctuation, casing, and extensions`() {
        val fp1 = TrackFingerprint.compute("01 - Midnight Echo.flac", 185_400L, 24_000_000L)
        val fp2 = TrackFingerprint.compute("01 Midnight Echo", 185_900L, 24_000_000L)
        assertEquals(fp1, fp2)
    }
}
