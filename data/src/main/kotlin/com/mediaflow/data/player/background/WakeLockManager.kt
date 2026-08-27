package com.mediaflow.data.player.background

import android.content.Context
import android.net.wifi.WifiManager
import android.os.PowerManager

/**
 * Manages CPU WakeLock and Wi-Fi Lock during live stream playback with the screen locked/off.
 * Ensures locks are cleanly released when playback pauses, ends, or service terminates.
 */
class WakeLockManager(context: Context) {

    private val powerManager = context.applicationContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager

    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    /**
     * Acquires wake lock and wifi lock for active streaming.
     */
    @Synchronized
    fun acquireLocks(isLive: Boolean = false) {
        // CPU Partial WakeLock: keep audio processing uninterrupted when screen is off
        if (wakeLock == null && powerManager != null) {
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "MediaFlow:LiveAudioPlaybackWakeLock",
            ).apply {
                setReferenceCounted(false)
                acquire(4 * 60 * 60 * 1000L) // 4 hours maximum safety timeout
            }
        }

        // Wi-Fi Lock for live streaming over network
        if (isLive && wifiLock == null && wifiManager != null) {
            wifiLock = wifiManager.createWifiLock(
                WifiManager.WIFI_MODE_FULL_HIGH_PERF,
                "MediaFlow:LiveStreamWifiLock",
            ).apply {
                setReferenceCounted(false)
                acquire()
            }
        }
    }

    /**
     * Cleanly releases all held locks.
     */
    @Synchronized
    fun releaseLocks() {
        runCatching {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        }
        wakeLock = null

        runCatching {
            if (wifiLock?.isHeld == true) {
                wifiLock?.release()
            }
        }
        wifiLock = null
    }
}
