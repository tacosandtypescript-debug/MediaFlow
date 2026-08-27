package com.mediaflow.data.player.background

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest

/**
 * Monitors network interface transitions (e.g. Wi-Fi <-> Mobile Data) to trigger
 * controlled reconnection for active live audio streams.
 */
class NetworkPlaybackMonitor(
    context: Context,
    private val onNetworkAvailable: () -> Unit,
) {
    private val connectivityManager =
        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    private var lastTriggerTimeMs = 0L
    private val throttleIntervalMs = 5_000L

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            val now = System.currentTimeMillis()
            if (now - lastTriggerTimeMs > throttleIntervalMs) {
                lastTriggerTimeMs = now
                onNetworkAvailable()
            }
        }
    }

    private var isRegistered = false

    fun startMonitoring() {
        val cm = connectivityManager
        if (isRegistered || cm == null) return
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        runCatching {
            cm.registerNetworkCallback(request, networkCallback)
            isRegistered = true
        }
    }

    fun stopMonitoring() {
        val cm = connectivityManager
        if (!isRegistered || cm == null) return
        runCatching {
            cm.unregisterNetworkCallback(networkCallback)
        }
        isRegistered = false
    }
}
