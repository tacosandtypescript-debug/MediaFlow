package com.mediaflow.app.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Photo
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import com.mediaflow.app.R

/**
 * Safe, typed constants for all MediaFlow navigation destinations.
 *
 * Screens that are not part of the bottom bar keep an [ImageVector] of null.
 */
sealed class MediaFlowDestination(
    val route: String,
    @StringRes val labelRes: Int,
    val icon: ImageVector?,
) {
    data object Home : MediaFlowDestination(
        route = "home",
        labelRes = R.string.nav_home,
        icon = Icons.Outlined.Home,
    )

    data object Downloads : MediaFlowDestination(
        route = "downloads",
        labelRes = R.string.nav_downloads,
        icon = Icons.Outlined.Download,
    )

    data object Gallery : MediaFlowDestination(
        route = "gallery",
        labelRes = R.string.nav_gallery,
        icon = Icons.Outlined.Photo,
    )

    data object Settings : MediaFlowDestination(
        route = "settings",
        labelRes = R.string.nav_settings,
        icon = Icons.Outlined.Settings,
    )

    data object Player : MediaFlowDestination(
        route = "player/{mediaId}",
        labelRes = R.string.nav_player,
        icon = Icons.Outlined.PlayArrow,
    ) {
        /** Builds the concrete route for a given media id. */
        fun routeFor(mediaId: String): String = "player/$mediaId"
    }

    companion object {
        /** Destinations shown in the bottom navigation bar. */
        val bottomBarItems: List<MediaFlowDestination> =
            listOf(Home, Downloads, Gallery, Settings)

        /**
         * Returns the bottom-bar destination matching a route string, or null
         * when the route does not correspond to a visible tab.
         */
        fun fromRoute(route: String?): MediaFlowDestination? =
            bottomBarItems.firstOrNull { it.route == route }
    }
}
