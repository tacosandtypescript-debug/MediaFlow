package com.mediaflow.app.player.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.mediaflow.data.player.artwork.PlaybackArtworkLoader
import com.mediaflow.data.player.background.PlayerSessionHolder
import com.mediaflow.data.player.external.PlayerExternalSnapshotFactory
import com.mediaflow.data.player.widget.PlaybackWidgetBroadcast
import com.mediaflow.domain.player.EnginePlaybackState

/**
 * Home-screen mini player. Reads the same [PlayerSessionHolder] as the app.
 */
class PlayerWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        render(context, appWidgetIds)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == PlaybackWidgetBroadcast.ACTION_SYNC) {
            val ids = AppWidgetManager.getInstance(context)
                .getAppWidgetIds(ComponentName(context, PlayerWidgetProvider::class.java))
            if (ids.isNotEmpty()) render(context, ids)
        }
    }

    companion object {
        fun render(context: Context, appWidgetIds: IntArray) {
            val app = context.applicationContext
            val state = runCatching { PlayerSessionHolder.get(app).uiState.value }.getOrNull()
            val snapshot = state
                ?.takeIf { it.playbackState != EnginePlaybackState.IDLE }
                ?.let { PlayerExternalSnapshotFactory.from(it) }
            val artwork = PlaybackArtworkLoader.load(app, snapshot?.artworkUrl)
            val views = PlayerWidgetBinder.bind(app, snapshot, artwork)
            PlayerWidgetBinder.push(app, appWidgetIds, views)
        }
    }
}
