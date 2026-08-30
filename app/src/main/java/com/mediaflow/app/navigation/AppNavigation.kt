package com.mediaflow.app.navigation

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.mediaflow.app.ui.home.HomeViewModel
import com.mediaflow.app.R
import com.mediaflow.app.ui.components.MediaFlowBackground
import com.mediaflow.app.ui.downloads.DownloadEvent
import com.mediaflow.app.ui.downloads.DownloadStartResult
import com.mediaflow.app.ui.downloads.DownloadViewModel
import com.mediaflow.app.ui.downloads.DownloadsScreen
import com.mediaflow.app.ui.home.HomeScreen
import com.mediaflow.app.ui.library.LibraryScreen
import com.mediaflow.app.ui.motion.MiniPlayerTransitions
import com.mediaflow.app.ui.motion.PlayerTransitions
import com.mediaflow.app.ui.player.PlayerScreen
import com.mediaflow.app.ui.player.miniplayer.MiniPlayer
import com.mediaflow.app.ui.settings.SettingsScreen
import com.mediaflow.app.ui.theme.MediaFlowTheme
import com.mediaflow.app.ui.theme.ThemeViewModel
import com.mediaflow.app.ui.theme.customColors
import com.mediaflow.data.player.background.MediaPlaybackService
import com.mediaflow.data.player.background.PlayerSessionHolder
import com.mediaflow.data.player.notification.PlaybackTransportActions
import com.mediaflow.data.resolver.YtDlpSourceResolver
import kotlinx.coroutines.launch

/** Root navigation, mini player integration, and app-level download/theme state wiring. */
@Composable
fun AppNavigation(
    requestNotificationPermission: (((Boolean) -> Unit) -> Unit)? = null,
    launchIntent: Intent? = null,
) {
    val context = LocalContext.current
    val application = context.applicationContext as android.app.Application
    val themeViewModel: ThemeViewModel = viewModel()
    val downloadViewModel: DownloadViewModel = viewModel(
        factory = DownloadViewModel.Factory(application),
    )
    val homeViewModel: HomeViewModel = viewModel()
    val sourceResolver = remember(application) { YtDlpSourceResolver(application) }
    val themeMode by themeViewModel.themeMode.collectAsState()
    val downloads by downloadViewModel.downloads.collectAsState()
    val spacesMap by downloadViewModel.spacesMap.collectAsState()
    val downloadProgressMap by downloadViewModel.progressMap.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val snackbarScope = rememberCoroutineScope()
    val notificationDeniedMessage = stringResource(R.string.download_notifications_denied)

    val playerService = remember(application) { PlayerSessionHolder.get(application) }
    val playerServiceState by playerService.uiState.collectAsState()

    LaunchedEffect(downloadViewModel) {
        downloadViewModel.events.collect { event ->
            when (event) {
                is DownloadEvent.Started -> snackbarHostState.showSnackbar(
                    context.getString(R.string.home_info_download_started),
                )
                is DownloadEvent.Failed -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    MediaFlowTheme(themeMode = themeMode) {
        val navController = rememberNavController()
        LaunchedEffect(launchIntent, playerServiceState.mediaId) {
            val intent = launchIntent ?: return@LaunchedEffect
            if (!intent.getBooleanExtra(PlaybackTransportActions.EXTRA_OPEN_PLAYER, false)) {
                return@LaunchedEffect
            }
            val mediaId = intent.getStringExtra(MediaPlaybackService.EXTRA_MEDIA_URI)
                ?: playerServiceState.mediaId
                ?: playerServiceState.filePath
                ?: return@LaunchedEffect
            navController.navigate(MediaFlowDestination.Player.routeFor(Uri.encode(mediaId))) {
                launchSingleTop = true
            }
        }
        val backStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = backStackEntry?.destination?.route
        val showBottomBar = MediaFlowDestination.bottomBarItems.any { it.route == currentRoute }

        MediaFlowBackground {
            Scaffold(
                containerColor = Color.Transparent,
                snackbarHost = {
                    SnackbarHost(
                        hostState = snackbarHostState,
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .imePadding()
                            .navigationBarsPadding(),
                    )
                },
                bottomBar = {
                    AnimatedVisibility(
                        visible = showBottomBar,
                        enter = MiniPlayerTransitions.enter(),
                        exit = MiniPlayerTransitions.exit(),
                    ) {
                        Column {
                            // Persistent Mini Player above NavigationBar
                            MiniPlayer(
                                serviceState = playerServiceState,
                                onOpenPlayer = { mediaUri ->
                                    navController.navigate(
                                        MediaFlowDestination.Player.routeFor(Uri.encode(mediaUri)),
                                    ) {
                                        launchSingleTop = true
                                    }
                                },
                                onTogglePlayPause = {
                                    if (playerServiceState.isPlaying) {
                                        playerService.pause()
                                    } else {
                                        playerService.play()
                                    }
                                },
                                onSkipNext = {
                                    playerService.playNext()
                                },
                            )

                            NavigationBar(
                                containerColor = MaterialTheme.customColors.navigationBackground,
                                tonalElevation = 0.dp,
                            ) {
                                MediaFlowDestination.bottomBarItems.forEach { destination ->
                                    val selected = destination.route == currentRoute
                                    NavigationBarItem(
                                        selected = selected,
                                        modifier = Modifier.testTag("tab_${destination.route}"),
                                        onClick = {
                                            navController.navigate(destination.route) {
                                                popUpTo(navController.graph.findStartDestination().id) {
                                                    saveState = true
                                                }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        },
                                        colors = NavigationBarItemDefaults.colors(
                                            selectedIconColor = MaterialTheme.customColors.navigationSelected,
                                            selectedTextColor = MaterialTheme.customColors.navigationSelected,
                                            unselectedIconColor = MaterialTheme.customColors.navigationUnselected,
                                            unselectedTextColor = MaterialTheme.customColors.navigationUnselected,
                                            indicatorColor = MaterialTheme.customColors.navigationIndicator,
                                        ),
                                        icon = {
                                            Icon(
                                                imageVector = destination.icon!!,
                                                contentDescription = stringResource(destination.labelRes),
                                                modifier = Modifier.size(24.dp),
                                            )
                                        },
                                        label = { Text(stringResource(destination.labelRes)) },
                                    )
                                }
                            }
                        }
                    }
                },
            ) { innerPadding ->
                NavHost(
                    navController = navController,
                    startDestination = MediaFlowDestination.Home.route,
                    modifier = Modifier.padding(innerPadding),
                    enterTransition = { EnterTransition.None },
                    exitTransition = { ExitTransition.None },
                    popEnterTransition = { EnterTransition.None },
                    popExitTransition = { ExitTransition.None },
                ) {
                    composable(route = MediaFlowDestination.Home.route) {
                        HomeScreen(
                            viewModel = homeViewModel,
                            sourceResolver = sourceResolver,
                            onPlayLive = { liveUrl ->
                                navController.navigate(
                                    MediaFlowDestination.Player.routeFor(Uri.encode(liveUrl)),
                                )
                            },
                            onDownloadRequested = downloadViewModel::start,
                            recentDownloads = downloads,
                            onRecentClick = { item ->
                                val uri = item.localUri ?: item.id
                                navController.navigate(
                                    MediaFlowDestination.Player.routeFor(Uri.encode(uri)),
                                )
                            },
                        )
                    }

                    composable(route = MediaFlowDestination.Gallery.route) {
                        LibraryScreen(
                            onOpenItem = { item ->
                                val uri = item.localUri ?: item.id
                                navController.navigate(
                                    MediaFlowDestination.Player.routeFor(Uri.encode(uri)),
                                )
                            },
                        )
                    }

                    composable(route = MediaFlowDestination.Downloads.route) {
                        val downloadsViewMode by downloadViewModel.viewMode.collectAsState()
                        DownloadsScreen(
                            downloads = downloads,
                            spacesMap = spacesMap,
                            progressMap = downloadProgressMap,
                            viewMode = downloadsViewMode,
                            onViewModeChange = downloadViewModel::setViewMode,
                            onOpen = { item ->
                                val uri = item.localUri ?: item.id
                                navController.navigate(
                                    MediaFlowDestination.Player.routeFor(Uri.encode(uri)),
                                )
                            },
                            onPause = downloadViewModel::pause,
                            onResume = downloadViewModel::resume,
                            onCancel = downloadViewModel::cancel,
                            onRetry = downloadViewModel::retry,
                            onRemove = downloadViewModel::remove,
                            onBackToHome = {
                                navController.popBackStack(
                                    MediaFlowDestination.Home.route,
                                    inclusive = false,
                                )
                            },
                        )
                    }

                    composable(route = MediaFlowDestination.Settings.route) {
                        SettingsScreen(
                            themeMode = themeMode,
                            onThemeModeChange = themeViewModel::setThemeMode,
                        )
                    }

                    composable(
                        route = MediaFlowDestination.Player.route,
                        arguments = listOf(
                            navArgument("mediaId") { type = NavType.StringType },
                        ),
                        enterTransition = { PlayerTransitions.enter() },
                        exitTransition = { PlayerTransitions.exit() },
                        popEnterTransition = { PlayerTransitions.popEnter() },
                        popExitTransition = { PlayerTransitions.popExit() },
                    ) { entry ->
                        val encodedMediaId = entry.arguments?.getString("mediaId").orEmpty()
                        val mediaUri = Uri.decode(encodedMediaId)
                        PlayerScreen(
                            mediaUri = mediaUri,
                            onBack = { navController.popBackStack() },
                        )
                    }
                }
            }
        }
    }
}
