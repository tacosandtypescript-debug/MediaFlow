package com.mediaflow.data.provider.x.live

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicReference

/**
 * Anonymous, guest-token powered HTTP client for querying X live video stream status.
 */
class XLiveStreamClient(
    private val connectTimeoutMs: Int = 10_000,
    private val readTimeoutMs: Int = 15_000,
) {
    companion object {
        private const val PUBLIC_BEARER_TOKEN =
            "Bearer AAAAAAAAAAAAAAAAAAAAANRILgAAAAAAnNwIzUejRCOuH5E6I8xnZz4puTs%3D1Zv7ttfk8LF81IUq16cHjhLTvJu4FA33AGWWjCpTnA"
        private const val GUEST_ACTIVATE_URL = "https://api.x.com/1.1/guest/activate.json"
        private const val LIVE_STREAM_API_BASE = "https://api.x.com/1.1/live_video_stream/status"
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    }

    private val cachedGuestToken = AtomicReference<String?>(null)

    suspend fun fetchStreamUrl(mediaKey: String): String? = withContext(Dispatchers.IO) {
        if (mediaKey.isBlank()) return@withContext null

        val token = getOrRefreshGuestToken() ?: return@withContext null
        val url = fetchStreamWithToken(mediaKey, token)
        if (url != null) return@withContext url

        // Invalidate token and retry once in case of token expiry
        cachedGuestToken.set(null)
        val refreshedToken = getOrRefreshGuestToken() ?: return@withContext null
        fetchStreamWithToken(mediaKey, refreshedToken)
    }

    private fun fetchStreamWithToken(mediaKey: String, guestToken: String): String? {
        return runCatching {
            val urlString = "$LIVE_STREAM_API_BASE/$mediaKey"
            val connection = (URL(urlString).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = connectTimeoutMs
                readTimeout = readTimeoutMs
                setRequestProperty("Authorization", PUBLIC_BEARER_TOKEN)
                setRequestProperty("x-guest-token", guestToken)
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", "application/json")
            }

            if (connection.responseCode in 200..299) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val body = reader.readText()
                reader.close()
                val root = JSONObject(body)
                val source = root.optJSONObject("source")
                source?.optString("location")?.takeIf { it.isNotBlank() }
                    ?: source?.optString("noRedirectPlaybackUrl")?.takeIf { it.isNotBlank() }
            } else {
                null
            }
        }.getOrNull()
    }

    private fun getOrRefreshGuestToken(): String? {
        val existing = cachedGuestToken.get()
        if (!existing.isNullOrBlank()) return existing

        return runCatching {
            val connection = (URL(GUEST_ACTIVATE_URL).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = connectTimeoutMs
                readTimeout = readTimeoutMs
                setRequestProperty("Authorization", PUBLIC_BEARER_TOKEN)
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", "application/json")
                doOutput = true
            }

            if (connection.responseCode in 200..299) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val body = reader.readText()
                reader.close()
                val token = JSONObject(body).optString("guest_token").takeIf { it.isNotBlank() }
                if (token != null) {
                    cachedGuestToken.set(token)
                }
                token
            } else {
                null
            }
        }.getOrNull()
    }
}
