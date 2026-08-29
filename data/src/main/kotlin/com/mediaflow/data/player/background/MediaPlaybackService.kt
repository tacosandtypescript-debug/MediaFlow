package com.mediaflow.data.player.background

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat
import com.mediaflow.core.model.XSpace
import com.mediaflow.data.provider.x.live.LiveSpaceEndHandler
import com.mediaflow.data.provider.x.live.LiveSpaceEndMonitor
import com.mediaflow.data.provider.x.live.PendingLiveDownloadRepositoryImpl
import com.mediaflow.data.provider.x.spaces.XSpaceMetadataResolver
import com.mediaflow.data.repository.Media3DownloadRepository
import com.mediaflow.data.repository.XSpaceRepositoryImpl
import com.mediaflow.domain.player.EnginePlaybackState
import com.mediaflow.domain.player.PlaybackEvent
import com.mediaflow.domain.player.PlayerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Android Foreground Service managing media playback lifecycles across screen off,
 * app switching, Bluetooth/lockscreen interactions, and post-live automatic downloads.
 */
class MediaPlaybackService : Service() {

    companion object {
        const val EXTRA_MEDIA_URI = "extra_media_uri"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_IS_LIVE = "extra_is_live"
        const val EXTRA_SPACE_ID = "extra_space_id"
        const val EXTRA_SPACE_URL = "extra_space_url"
        const val EXTRA_AUTO_DOWNLOAD = "extra_auto_download"

        fun start(
            context: Context,
            mediaUri: String,
            title: String? = null,
            isLive: Boolean = false,
            spaceId: String? = null,
            spaceUrl: String? = null,
            autoDownloadAfterEnd: Boolean = false,
        ) {
            val intent = Intent(context, MediaPlaybackService::class.java).apply {
                action = PlaybackNotificationManager.ACTION_PLAY
                putExtra(EXTRA_MEDIA_URI, mediaUri)
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_IS_LIVE, isLive)
                putExtra(EXTRA_SPACE_ID, spaceId)
                putExtra(EXTRA_SPACE_URL, spaceUrl)
                putExtra(EXTRA_AUTO_DOWNLOAD, autoDownloadAfterEnd)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, MediaPlaybackService::class.java).apply {
                action = PlaybackNotificationManager.ACTION_STOP
            }
            context.startService(intent)
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val binder = LocalBinder()

    private lateinit var playerService: PlayerService
    private lateinit var audioFocusManager: AudioFocusManager
    private lateinit var mediaSessionController: MediaSessionController
    private lateinit var notificationManager: PlaybackNotificationManager
    private lateinit var wakeLockManager: WakeLockManager
    private lateinit var networkMonitor: NetworkPlaybackMonitor

    private val spaceRepository by lazy { XSpaceRepositoryImpl(this) }
    private val spaceMetadataResolver by lazy { XSpaceMetadataResolver() }
    private val liveEndMonitor by lazy { LiveSpaceEndMonitor(spaceMetadataResolver) }
    private val pendingDownloadRepo by lazy { PendingLiveDownloadRepositoryImpl(this) }
    private val downloadRepo by lazy { Media3DownloadRepository.get(this) }

    private var currentSpace: XSpace? = null
    private var cachedArtwork: Bitmap? = null
    private var isForegroundActive = false
    private var activeSpaceId: String? = null
    private var activeSpaceUrl: String? = null
    private var autoDownloadWhenEnded = false
    private val liveEndHandler by lazy {
        LiveSpaceEndHandler(
            liveEndMonitor = liveEndMonitor,
            pendingDownloadRepo = pendingDownloadRepo,
            spaceRepository = spaceRepository,
            downloadRepository = downloadRepo,
            onBroadcastEnded = { playerService.markBroadcastEnded() },
            onSpaceUpdated = { currentSpace = it },
        )
    }

    inner class LocalBinder : Binder() {
        fun getService(): MediaPlaybackService = this@MediaPlaybackService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        playerService = PlayerSessionHolder.get(this)

        audioFocusManager = AudioFocusManager(
            context = this,
            onPauseRequested = { playerService.pause() },
            onResumeRequested = { playerService.play() },
            onStopRequested = {
                playerService.stop()
                stopForegroundAndSelf()
            },
        )

        mediaSessionController = MediaSessionController(
            context = this,
            onPlayRequested = {
                if (audioFocusManager.requestAudioFocus()) {
                    playerService.play()
                }
            },
            onPauseRequested = { playerService.pause() },
            onStopRequested = {
                playerService.stop()
                stopForegroundAndSelf()
            },
            onSeekRequested = { pos -> playerService.seekTo(pos) },
        )

        notificationManager = PlaybackNotificationManager(this)
        wakeLockManager = WakeLockManager(this)

        networkMonitor = NetworkPlaybackMonitor(this) {
            // On network change, if live audio was playing and stalled, reconnect
            val state = playerService.uiState.value
            if (state.isLive && (state.isError || state.playbackState == EnginePlaybackState.PREPARING)) {
                state.filePath?.let { uri ->
                    playerService.openMedia(uri, uri, state.title, autoPlay = true, isLive = true)
                }
            }
        }
        networkMonitor.startMonitoring()

        observePlayerState()
        observePlayerEvents()
        serviceScope.launch {
            liveEndHandler.resumeInterruptedWaits(serviceScope)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action

        when (action) {
            PlaybackNotificationManager.ACTION_PLAY -> {
                val mediaUri = intent.getStringExtra(EXTRA_MEDIA_URI)
                val title = intent.getStringExtra(EXTRA_TITLE)
                val isLive = intent.getBooleanExtra(EXTRA_IS_LIVE, false)
                activeSpaceId = intent.getStringExtra(EXTRA_SPACE_ID)
                activeSpaceUrl = intent.getStringExtra(EXTRA_SPACE_URL)
                autoDownloadWhenEnded = intent.getBooleanExtra(EXTRA_AUTO_DOWNLOAD, false)

                if (!mediaUri.isNullOrBlank()) {
                    // Load space metadata and artwork if available
                    serviceScope.launch {
                        currentSpace = activeSpaceId?.let { spaceRepository.getSpace(it) }
                            ?: spaceRepository.getSpaceForMedia(mediaUri)

                        cachedArtwork = notificationManager.loadArtworkBitmap(currentSpace?.host?.avatarUrl)
                        updateForegroundNotification()
                    }

                    // Open media in shared player session
                    if (audioFocusManager.requestAudioFocus()) {
                        playerService.openMedia(
                            mediaId = mediaUri,
                            filePath = mediaUri,
                            title = title,
                            autoPlay = true,
                            isLive = isLive,
                        )
                    }
                } else {
                    if (audioFocusManager.requestAudioFocus()) {
                        playerService.play()
                    }
                }
            }
            PlaybackNotificationManager.ACTION_PAUSE -> {
                playerService.pause()
            }
            PlaybackNotificationManager.ACTION_STOP -> {
                playerService.stop()
                stopForegroundAndSelf()
            }
        }

        return START_NOT_STICKY
    }

    private fun observePlayerState() {
        serviceScope.launch {
            playerService.uiState.collect { state ->
                mediaSessionController.updatePlaybackState(state)

                if (state.isPlaying) {
                    networkMonitor.clearReconnecting()
                    wakeLockManager.acquireLocks(isLive = state.isLive)
                    updateForegroundNotification()
                } else {
                    wakeLockManager.releaseLocks()
                    if (state.playbackState == EnginePlaybackState.ENDED || state.playbackState == EnginePlaybackState.IDLE) {
                        audioFocusManager.abandonAudioFocus()
                    }
                    updateForegroundNotification()
                }
            }
        }
    }

    private fun observePlayerEvents() {
        serviceScope.launch {
            playerService.events.collect { event ->
                when (event) {
                    is PlaybackEvent.PlaybackFinished -> {
                        handleStreamEnded()
                    }
                    is PlaybackEvent.PlaybackError -> {
                        handleStreamEnded()
                    }
                    else -> Unit
                }
            }
        }
    }

    private fun handleStreamEnded() {
        val spaceId = activeSpaceId ?: currentSpace?.id ?: return
        val spaceUrl = activeSpaceUrl ?: currentSpace?.url ?: "https://x.com/i/spaces/$spaceId"
        serviceScope.launch {
            liveEndHandler.handleStreamEnded(
                spaceId = spaceId,
                spaceUrl = spaceUrl,
                autoDownloadWhenEnded = autoDownloadWhenEnded,
                scope = serviceScope,
            )
        }
    }

    private fun updateForegroundNotification() {
        val state = playerService.uiState.value
        if (state.playbackState == EnginePlaybackState.IDLE) {
            return
        }

        val notification = notificationManager.buildNotification(
            serviceState = state,
            space = currentSpace,
            sessionToken = mediaSessionController.sessionToken,
            artwork = cachedArtwork,
        )

        if (!isForegroundActive) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    PlaybackNotificationManager.NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
                )
            } else {
                startForeground(PlaybackNotificationManager.NOTIFICATION_ID, notification)
            }
            isForegroundActive = true
        } else {
            val notifyMgr = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            notifyMgr.notify(PlaybackNotificationManager.NOTIFICATION_ID, notification)
        }
    }

    private fun stopForegroundAndSelf() {
        isForegroundActive = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        wakeLockManager.releaseLocks()
        audioFocusManager.abandonAudioFocus()
        stopSelf()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val state = playerService.uiState.value
        if (!state.isPlaying) {
            stopForegroundAndSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        serviceScope.cancel()
        networkMonitor.stopMonitoring()
        wakeLockManager.releaseLocks()
        audioFocusManager.abandonAudioFocus()
        mediaSessionController.release()
        super.onDestroy()
    }
}
