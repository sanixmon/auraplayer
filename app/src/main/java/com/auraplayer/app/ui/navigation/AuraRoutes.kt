package com.auraplayer.app.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
sealed interface AuraRoute {
    @Serializable
    data object Library : AuraRoute

    @Serializable
    data object Playlists : AuraRoute

    @Serializable
    data class PlaylistDetail(val playlistId: Long) : AuraRoute

    @Serializable
    data object NowPlaying : AuraRoute

    @Serializable
    data object Settings : AuraRoute
}
