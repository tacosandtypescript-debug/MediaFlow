package com.mediaflow.data.player.widget

import android.content.Context
import android.content.Intent

/** Asks the home-screen widget to redraw from [com.mediaflow.domain.player.PlayerService]. */
object PlaybackWidgetBroadcast {
    const val ACTION_SYNC = "com.mediaflow.action.SYNC_PLAYER_WIDGET"

    fun send(context: Context) {
        context.sendBroadcast(
            Intent(ACTION_SYNC).setPackage(context.packageName),
        )
    }
}
