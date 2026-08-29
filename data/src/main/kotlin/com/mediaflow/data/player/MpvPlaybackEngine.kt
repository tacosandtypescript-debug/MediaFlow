package com.mediaflow.data.player

import android.content.Context
import android.view.Surface
import com.mediaflow.domain.player.EnginePlaybackState
import com.mediaflow.domain.player.EngineState
import com.mediaflow.domain.player.PlaybackEngine
import com.mediaflow.domain.player.PlaybackEvent
import `is`.xyz.mpv.MPV
import `is`.xyz.mpv.MPVNode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.FileNotFoundException

/**
 * Production implementation of [PlaybackEngine] backed by native libmpv via [is.xyz.mpv.MPV].
 */
class MpvPlaybackEngine(
    private val context: Context,
    private val uriResolver: MpvUriResolver = MpvUriResolver(context),
) : PlaybackEngine {

    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val eventObserver = object : MPV.EventObserver {
        override fun eventProperty(property: String) {}

        override fun eventProperty(property: String, value: Long) {
            when (property) {
                "volume" -> {
                    _state.value = _state.value.copy(volume = value.toInt())
                }
            }
        }

        override fun eventProperty(property: String, value: Boolean) {
            val mediaId = currentMediaId.orEmpty()
            when (property) {
                "pause" -> {
                    val newState = if (value) EnginePlaybackState.PAUSED else EnginePlaybackState.PLAYING
                    _state.value = _state.value.copy(playbackState = newState)
                    engineScope.launch {
                        if (value) {
                            _events.emit(PlaybackEvent.PlaybackPaused(mediaId, _state.value.currentPositionMs))
                        } else {
                            _events.emit(PlaybackEvent.PlaybackStarted(mediaId, _state.value.currentPositionMs))
                        }
                    }
                }
                "eof-reached" -> {
                    if (value) {
                        emitPlaybackFinishedOnce(mediaId)
                    }
                }
                "mute" -> {
                    _state.value = _state.value.copy(isMuted = value)
                }
            }
        }

        override fun eventProperty(property: String, value: String) {
            when (property) {
                "vid" -> {
                    val hasVideo = value.isNotBlank() && value != "no" && value != "auto"
                    _state.value = _state.value.copy(
                        isAudioOnly = !hasVideo,
                        isVideoAvailable = hasVideo,
                    )
                }
            }
        }

        override fun eventProperty(property: String, value: Double) {
            val mediaId = currentMediaId.orEmpty()
            when (property) {
                "time-pos" -> {
                    val posMs = (value * 1000.0).toLong().coerceAtLeast(0L)
                    _state.value = _state.value.copy(currentPositionMs = posMs)
                    engineScope.launch {
                        _events.emit(PlaybackEvent.PositionChanged(mediaId, posMs, _state.value.durationMs))
                    }
                }
                "duration" -> {
                    val durMs = (value * 1000.0).toLong().coerceAtLeast(0L)
                    _state.value = _state.value.copy(durationMs = durMs)
                }
                "speed" -> {
                    _state.value = _state.value.copy(speed = value.toFloat())
                }
            }
        }

        override fun eventProperty(property: String, value: MPVNode) {
            if (property == "track-list") {
                inspectTrackList(value)
            }
        }

        override fun event(eventId: Int, data: MPVNode) {
            val mediaId = currentMediaId.orEmpty()
            when (eventId) {
                MPV.mpvEvent.MPV_EVENT_FILE_LOADED -> {
                    val durSec = mpv.getPropertyDouble("duration") ?: 0.0
                    val durMs = (durSec * 1000.0).toLong().coerceAtLeast(0L)
                    val vid = mpv.getPropertyString("vid")
                    val hasVideo = vid != null && vid != "no" && vid != "auto"

                    _state.value = _state.value.copy(
                        durationMs = durMs,
                        playbackState = if (_state.value.isPaused) EnginePlaybackState.PAUSED else EnginePlaybackState.PLAYING,
                        isAudioOnly = !hasVideo,
                        isVideoAvailable = hasVideo,
                    )

                    if (pendingStartPositionMs > 0L) {
                        seekTo(pendingStartPositionMs)
                        pendingStartPositionMs = 0L
                    }

                    engineScope.launch {
                        _events.emit(PlaybackEvent.MediaOpened(mediaId, durMs, isAudioOnly = !hasVideo))
                    }
                }
                MPV.mpvEvent.MPV_EVENT_END_FILE -> {
                    emitPlaybackFinishedOnce(mediaId)
                }
                MPV.mpvEvent.MPV_EVENT_PLAYBACK_RESTART -> {
                    if (_state.value.isPlaying) {
                        engineScope.launch {
                            _events.emit(PlaybackEvent.PlaybackStarted(mediaId, _state.value.currentPositionMs))
                        }
                    }
                }
            }
        }
    }

    private val mpv: MPV by lazy {
        val instance = MPV()
        instance.create(context.applicationContext)
        MpvConfig.applyDefaults(instance, context.applicationContext)
        instance.init()
        instance.observeProperty("time-pos", MPV.mpvFormat.MPV_FORMAT_DOUBLE)
        instance.observeProperty("duration", MPV.mpvFormat.MPV_FORMAT_DOUBLE)
        instance.observeProperty("pause", MPV.mpvFormat.MPV_FORMAT_FLAG)
        instance.observeProperty("eof-reached", MPV.mpvFormat.MPV_FORMAT_FLAG)
        instance.observeProperty("speed", MPV.mpvFormat.MPV_FORMAT_DOUBLE)
        instance.observeProperty("volume", MPV.mpvFormat.MPV_FORMAT_INT64)
        instance.observeProperty("mute", MPV.mpvFormat.MPV_FORMAT_FLAG)
        instance.observeProperty("vid", MPV.mpvFormat.MPV_FORMAT_STRING)
        instance.observeProperty("track-list", MPV.mpvFormat.MPV_FORMAT_NODE)
        instance.addObserver(eventObserver)
        instance
    }

    private val _state = MutableStateFlow(EngineState())
    override val state: StateFlow<EngineState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<PlaybackEvent>(extraBufferCapacity = 64)
    override val events: Flow<PlaybackEvent> = _events.asSharedFlow()

    private var currentMediaId: String? = null
    private var currentResolvedSource: ResolvedSource? = null
    private var pendingStartPositionMs: Long = 0L
    private var isReleased = false
    private val playbackFinishedGate = PlaybackFinishedGate()

    override fun load(mediaSource: String, startPositionMs: Long, autoPlay: Boolean) {
        if (isReleased) return

        currentMediaId = mediaSource
        pendingStartPositionMs = startPositionMs
        playbackFinishedGate.reset()

        _state.value = _state.value.copy(
            mediaSource = mediaSource,
            playbackState = EnginePlaybackState.PREPARING,
            currentPositionMs = startPositionMs,
            errorMessage = null,
        )

        val resolved = try {
            uriResolver.resolve(mediaSource)
        } catch (error: Throwable) {
            val message = if (error is FileNotFoundException) {
                "El archivo no existe o fue movido"
            } else {
                error.localizedMessage ?: "Error al abrir el medio"
            }
            _state.value = _state.value.copy(
                playbackState = EnginePlaybackState.ERROR,
                errorMessage = message,
            )
            engineScope.launch {
                _events.emit(PlaybackEvent.PlaybackError(mediaSource, message, isFatal = true))
            }
            return
        }

        // Close previous descriptor safely
        currentResolvedSource?.close()
        currentResolvedSource = resolved

        try {
            mpv.command("loadfile", resolved.path)
            if (!autoPlay) {
                mpv.setPropertyBoolean("pause", true)
            } else {
                mpv.setPropertyBoolean("pause", false)
            }
        } catch (error: Throwable) {
            val msg = error.message.orEmpty()
            _state.value = _state.value.copy(
                playbackState = EnginePlaybackState.ERROR,
                errorMessage = msg,
            )
            engineScope.launch {
                _events.emit(PlaybackEvent.PlaybackError(mediaSource, msg, isFatal = true))
            }
        }
    }

    override fun play() {
        if (isReleased) return
        try {
            mpv.setPropertyBoolean("pause", false)
        } catch (_: Throwable) {}
    }

    override fun pause() {
        if (isReleased) return
        try {
            mpv.setPropertyBoolean("pause", true)
        } catch (_: Throwable) {}
    }

    override fun stop() {
        if (isReleased) return
        try {
            mpv.command("stop")
        } catch (_: Throwable) {}
        _state.value = _state.value.copy(playbackState = EnginePlaybackState.IDLE)
    }

    override fun seekTo(positionMs: Long) {
        if (isReleased) return
        val targetSeconds = (positionMs / 1000.0).coerceAtLeast(0.0)
        try {
            mpv.command("seek", targetSeconds.toString(), "absolute")
        } catch (_: Throwable) {}
        _state.value = _state.value.copy(currentPositionMs = positionMs)
    }

    override fun setSpeed(speed: Float) {
        if (isReleased) return
        val coerced = speed.coerceIn(0.25f, 4.0f)
        try {
            mpv.setPropertyDouble("speed", coerced.toDouble())
        } catch (_: Throwable) {}
        _state.value = _state.value.copy(speed = coerced)
    }

    override fun setVolume(volume: Int) {
        if (isReleased) return
        val coerced = volume.coerceIn(0, 100)
        try {
            mpv.setPropertyInt("volume", coerced)
        } catch (_: Throwable) {}
        _state.value = _state.value.copy(volume = coerced)
    }

    override fun setMute(muted: Boolean) {
        if (isReleased) return
        try {
            mpv.setPropertyBoolean("mute", muted)
        } catch (_: Throwable) {}
        _state.value = _state.value.copy(isMuted = muted)
    }

    override fun attachSurface(surface: Any?) {
        if (isReleased) return
        if (surface is Surface) {
            try {
                mpv.attachSurface(surface)
            } catch (_: Throwable) {}
        }
    }

    override fun detachSurface() {
        if (isReleased) return
        try {
            mpv.detachSurface()
        } catch (_: Throwable) {}
    }

    override fun release() {
        if (isReleased) return
        isReleased = true

        val lastPos = _state.value.currentPositionMs
        val mediaId = currentMediaId.orEmpty()

        try {
            mpv.removeObserver(eventObserver)
            mpv.destroy()
        } catch (_: Throwable) {}

        currentResolvedSource?.close()
        currentResolvedSource = null

        engineScope.launch {
            _events.emit(PlaybackEvent.MediaClosed(mediaId, lastPos))
        }
        engineScope.cancel()
    }

    private fun emitPlaybackFinishedOnce(mediaId: String) {
        if (!playbackFinishedGate.tryMarkEmitted()) return
        _state.value = _state.value.copy(playbackState = EnginePlaybackState.ENDED)
        engineScope.launch {
            _events.emit(PlaybackEvent.PlaybackFinished(mediaId, _state.value.durationMs))
        }
    }

    private fun inspectTrackList(node: MPVNode) {
        val array = node.asArray() ?: return
        var foundVideo = false
        for (i in 0 until array.size) {
            val item = array[i]
            val typeNode = item.get("type")
            if (typeNode?.asString() == "video") {
                val albumartNode = item.get("albumart")
                val isAlbumArt = albumartNode?.asBoolean() == true
                if (!isAlbumArt) {
                    foundVideo = true
                }
            }
        }
        _state.value = _state.value.copy(
            isAudioOnly = !foundVideo,
            isVideoAvailable = foundVideo,
        )
    }
}
