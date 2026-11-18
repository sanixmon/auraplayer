package com.auraplayer.app.data.model

/**
 * Offline audio formats decoded via Android MediaCodec and the bundled LGPL Media3 FFmpeg extension.
 */
enum class AudioFormat(
    val displayName: String,
    val extensions: Set<String>,
    val mimeTypes: Set<String>,
    val isLossless: Boolean = false
) {
    MP3("MP3", setOf("mp3"), setOf("audio/mpeg", "audio/mp3")),
    FLAC("FLAC", setOf("flac"), setOf("audio/flac", "audio/x-flac"), isLossless = true),
    WAV("WAV", setOf("wav", "wave"), setOf("audio/wav", "audio/x-wav", "audio/vnd.wave"), isLossless = true),
    ALAC("ALAC", setOf("alac", "caf"), setOf("audio/alac", "audio/x-alac"), isLossless = true),
    AAC("AAC", setOf("aac", "adts"), setOf("audio/aac", "audio/aac-adts")),
    M4A("M4A", setOf("m4a", "mp4"), setOf("audio/mp4", "audio/m4a", "audio/x-m4a", "audio/mp4a-latm")),
    OGG("OGG", setOf("ogg", "oga"), setOf("audio/ogg", "application/ogg", "audio/vorbis")),
    OPUS("OPUS", setOf("opus"), setOf("audio/opus")),
    WMA("WMA", setOf("wma"), setOf("audio/x-ms-wma", "audio/wma")),
    APE("APE", setOf("ape"), setOf("audio/ape", "audio/x-ape"), isLossless = true),
    WV("WavPack", setOf("wv"), setOf("audio/wavpack", "audio/x-wavpack"), isLossless = true),
    TTA("TTA", setOf("tta"), setOf("audio/tta", "audio/x-tta"), isLossless = true),
    MPC("Musepack", setOf("mpc", "mp+", "mpp"), setOf("audio/musepack", "audio/x-musepack")),
    AC3("AC3", setOf("ac3"), setOf("audio/ac3")),
    EAC3("E-AC3", setOf("ec3", "eac3"), setOf("audio/eac3")),
    DTS("DTS", setOf("dts", "dtshd"), setOf("audio/vnd.dts", "audio/vnd.dts.hd")),
    WEBM("WebM", setOf("webm"), setOf("audio/webm")),
    MKA("MKA", setOf("mka"), setOf("audio/x-matroska")),
    AMR("AMR", setOf("amr", "awb"), setOf("audio/amr", "audio/amr-wb")),
    MIDI("MIDI", setOf("mid", "midi", "xmf"), setOf("audio/midi", "audio/sp-midi", "audio/x-midi")),
    THREE_GP("3GP", setOf("3gp", "3gpp"), setOf("audio/3gpp", "audio/3gpp2")),
    OTHER("Audio", emptySet(), emptySet());

    companion object {
        val supportedFormats: List<AudioFormat> = entries.filter { it != OTHER }

        val supportedExtensions: Set<String> = supportedFormats.flatMapTo(mutableSetOf()) { it.extensions }

        fun fromMetadata(
            mimeType: String?,
            fileNameOrUri: String?
        ): AudioFormat {
            val normalizedMime = mimeType?.trim()?.lowercase()
            if (!normalizedMime.isNullOrEmpty()) {
                supportedFormats.firstOrNull { format ->
                    normalizedMime in format.mimeTypes
                }?.let { return it }
            }

            val ext = fileNameOrUri
                ?.substringBefore('?')
                ?.substringAfterLast('.', "")
                ?.trim()
                ?.lowercase()
            if (!ext.isNullOrEmpty()) {
                supportedFormats.firstOrNull { format ->
                    ext in format.extensions
                }?.let { return it }
            }

            return OTHER
        }
    }
}
