package com.mediaflow.data.player.background

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build

/**
 * Handles Android audio focus acquisition, transient interruptions (phone calls, navigation prompts),
 * ducking, and clean abandonment.
 */
class AudioFocusManager(
    context: Context,
    private val onPauseRequested: () -> Unit,
    private val onResumeRequested: () -> Unit,
    private val onStopRequested: () -> Unit,
    private val onVolumeDuckRequested: (ducked: Boolean) -> Unit = {},
) {
    private val audioManager = context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private var hasFocus = false
    private var resumeOnFocusGain = false
    private var focusRequest: AudioFocusRequest? = null

    private val focusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                hasFocus = true
                onVolumeDuckRequested(false)
                if (resumeOnFocusGain) {
                    resumeOnFocusGain = false
                    onResumeRequested()
                }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                // Duck volume or pause
                onVolumeDuckRequested(true)
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                // Pause temporarily for phone call or other app transient playback
                resumeOnFocusGain = true
                hasFocus = false
                onPauseRequested()
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                // Permanent focus loss
                resumeOnFocusGain = false
                hasFocus = false
                onStopRequested()
            }
        }
    }

    /**
     * Requests audio focus. Returns true if granted.
     */
    fun requestAudioFocus(): Boolean {
        if (audioManager == null) return true
        if (hasFocus) return true

        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val playbackAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()

            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(playbackAttributes)
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener(focusChangeListener)
                .build()

            focusRequest = request
            audioManager.requestAudioFocus(request)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                focusChangeListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN,
            )
        }

        hasFocus = (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
        return hasFocus
    }

    /**
     * Abandons audio focus.
     */
    fun abandonAudioFocus() {
        if (audioManager == null || !hasFocus) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
            focusRequest = null
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(focusChangeListener)
        }
        hasFocus = false
        resumeOnFocusGain = false
    }
}
