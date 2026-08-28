package com.mediaflow.app.ui.common.media

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import com.mediaflow.app.ui.theme.customColors

/**
 * Animated Heart button toggling favorite state with tactile scale animation.
 */
@Composable
fun FavoriteButton(
    isFavorite: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color? = null,
) {
    val scale by animateFloatAsState(
        targetValue = if (isFavorite) 1.2f else 1.0f,
        animationSpec = spring(dampingRatio = 0.4f, stiffness = 400f),
        label = "favorite_scale",
    )

    val activeColor = tint ?: MaterialTheme.customColors.favorite
    val inactiveColor = MaterialTheme.customColors.favoriteInactive

    IconButton(
        onClick = onToggle,
        modifier = modifier
            .testTag("favorite_btn_${if (isFavorite) "active" else "inactive"}")
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
    ) {
        if (isFavorite) {
            Icon(
                imageVector = Icons.Filled.Favorite,
                contentDescription = "Quitar de favoritos",
                tint = activeColor,
            )
        } else {
            Icon(
                imageVector = Icons.Outlined.FavoriteBorder,
                contentDescription = "Añadir a favoritos",
                tint = inactiveColor,
            )
        }
    }
}
