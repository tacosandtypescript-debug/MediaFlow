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
    private var pausedByUser = false
    private var pausedByAudioFocus = false
    private var focusRequest: AudioFocusRequest? = null

    val hasAudioFocus: Boolean get() = hasFocus
    val isPausedByUser: Boolean get() = pausedByUser
    val isPausedByAudioFocus: Boolean get() = pausedByAudioFocus

    private val focusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        dispatchFocusChange(focusChange)
    }

    /**
     * Visible for tests. Permanent LOSS pauses without auto-resume and without
     * telling the caller to stop/release the engine.
     */
    fun dispatchFocusChange(focusChange: Int) {
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                hasFocus = true
                onVolumeDuckRequested(false)
                if (resumeOnFocusGain && !pausedByUser) {
                    resumeOnFocusGain = false
                    pausedByAudioFocus = false
                    onResumeRequested()
                }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                onVolumeDuckRequested(true)
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                resumeOnFocusGain = !pausedByUser
                hasFocus = false
                pausedByAudioFocus = true
                onPauseRequested()
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                // Permanent loss: pause in place. Do not auto-resume on GAIN.
                resumeOnFocusGain = false
                hasFocus = false
                pausedByAudioFocus = true
                onPauseRequested()
            }
        }
    }

    /** User tapped pause: never auto-resume on AUDIOFOCUS_GAIN. */
    fun markPausedByUser() {
        pausedByUser = true
        pausedByAudioFocus = false
        resumeOnFocusGain = false
    }

    /**
     * Requests audio focus. Returns true if granted.
     */
    fun requestAudioFocus(): Boolean {
        if (audioManager == null) {
            pausedByUser = false
            pausedByAudioFocus = false
            resumeOnFocusGain = false
            hasFocus = true
            return true
        }
        if (hasFocus) {
            pausedByUser = false
            pausedByAudioFocus = false
            resumeOnFocusGain = false
            return true
        }

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
        if (hasFocus) {
            pausedByUser = false
            pausedByAudioFocus = false
            resumeOnFocusGain = false
        }
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
