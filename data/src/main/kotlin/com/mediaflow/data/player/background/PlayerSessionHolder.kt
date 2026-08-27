package com.mediaflow.data.player.background

import android.content.Context
import com.mediaflow.data.player.MpvPlaybackEngine
import com.mediaflow.data.repository.ProgressRepositoryImpl
import com.mediaflow.domain.player.PlayerService

/**
 * Singleton holder providing a single source of truth for the active [PlayerService].
 * Ensures that UI components (e.g. PlayerViewModel across Activities/Composables)
 * and background components (MediaPlaybackService) share the identical player session.
 */
object PlayerSessionHolder {

    @Volatile
    private var instance: PlayerService? = null

    fun get(context: Context): PlayerService {
        return instance ?: synchronized(this) {
            instance ?: PlayerService(
                engine = MpvPlaybackEngine(context.applicationContext),
                progressRepository = ProgressRepositoryImpl(context.applicationContext),
            ).also { instance = it }
        }
    }

    /**
     * For unit test injection or custom lifecycle replacements.
     */
    fun setForTesting(customService: PlayerService?) {
        instance = customService
    }
}
