package com.mediaflow.app.navigation

import android.net.Uri
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.mediaflow.app.R
import com.mediaflow.app.ui.components.MediaFlowBackground
import com.mediaflow.app.ui.downloads.DownloadEvent
import com.mediaflow.app.ui.downloads.DownloadStartResult
import com.mediaflow.app.ui.downloads.DownloadViewModel
import com.mediaflow.app.ui.downloads.DownloadsScreen
import com.mediaflow.app.ui.home.HomeScreen
import com.mediaflow.app.ui.library.LibraryScreen
import com.mediaflow.app.ui.player.PlayerScreen
import com.mediaflow.app.ui.player.miniplayer.MiniPlayer
import com.mediaflow.app.ui.settings.SettingsScreen
import com.mediaflow.app.ui.theme.MediaFlowTheme
import com.mediaflow.app.ui.theme.ThemeViewModel
import com.mediaflow.data.player.background.PlayerSessionHolder
import com.mediaflow.data.resolver.YtDlpSourceResolver
import kotlinx.coroutines.launch

private val navEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition? = {
    fadeIn(tween(250)) + slideInVertically(tween(250)) { it / 12 }
}
private val navExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition? = {
    fadeOut(tween(150))
}
private val navPopEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition? = {
    fadeIn(tween(250))
}
private val navPopExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition? = {
    fadeOut(tween(150)) + slideOutVertically(tween(250)) { it / 12 }
}

/** Root navigation, mini player integration, and app-level download/theme state wiring. */
@Composable
fun AppNavigation(
    requestNotificationPermission: (((Boolean) -> Unit) -> Unit)? = null,
) {
    val context = LocalContext.current
    val application = context.applicationContext as android.app.Application
    val themeViewModel: ThemeViewModel = viewModel()
    val downloadViewModel: DownloadViewModel = viewModel(
        factory = DownloadViewModel.Factory(application),
    )
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
                    "Descarga añadida. Puedes consultar el progreso en Descargas",
                )
                is DownloadEvent.Failed -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    MediaFlowTheme(themeMode = themeMode) {
        val navController = rememberNavController()
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
                    if (showBottomBar) {
                        Column {
                            // Persistent Mini Player above NavigationBar
                            MiniPlayer(
                                serviceState = playerServiceState,
                                onOpenPlayer = { mediaUri ->
                                    navController.navigate(
                                        MediaFlowDestination.Player.routeFor(Uri.encode(mediaUri)),
                                    )
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
                                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                                tonalElevation = 8.dp,
                            ) {
                                MediaFlowDestination.bottomBarItems.forEach { destination ->
                                    val selected = destination.route == currentRoute
                                    val iconScale by animateFloatAsState(
                                        targetValue = if (selected) 1.12f else 1f,
                                        animationSpec = tween(220),
                                        label = "navIconScale",
                                    )
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
                                        icon = {
                                            Icon(
                                                imageVector = destination.icon!!,
                                                contentDescription = stringResource(destination.labelRes),
                                                modifier = Modifier
                                                    .size(24.dp)
                                                    .graphicsLayer {
                                                        scaleX = iconScale
                                                        scaleY = iconScale
                                                    },
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
                ) {
                    composable(
                        route = MediaFlowDestination.Home.route,
                        enterTransition = navEnter,
                        exitTransition = navExit,
                        popEnterTransition = navPopEnter,
                        popExitTransition = navPopExit,
                    ) {
                        HomeScreen(
                            sourceResolver = sourceResolver,
                            onPlayLive = { liveUrl ->
                                navController.navigate(
                                    MediaFlowDestination.Player.routeFor(Uri.encode(liveUrl)),
                                )
                            },
                            onDownloadRequested = downloadViewModel::start,
                        )
                    }

                    composable(
                        route = MediaFlowDestination.Gallery.route,
                        enterTransition = navEnter,
                        exitTransition = navExit,
                        popEnterTransition = navPopEnter,
                        popExitTransition = navPopExit,
                    ) {
                        LibraryScreen(
                            onOpenItem = { item ->
                                val uri = item.localUri ?: item.id
                                navController.navigate(
                                    MediaFlowDestination.Player.routeFor(Uri.encode(uri)),
                                )
                            },
                        )
                    }

                    composable(
                        route = MediaFlowDestination.Downloads.route,
                        enterTransition = navEnter,
                        exitTransition = navExit,
                        popEnterTransition = navPopEnter,
                        popExitTransition = navPopExit,
                    ) {
                        DownloadsScreen(
                            downloads = downloads,
                            spacesMap = spacesMap,
                            progressMap = downloadProgressMap,
                            onOpen = downloadViewModel::open,
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

                    composable(
                        route = MediaFlowDestination.Settings.route,
                        enterTransition = navEnter,
                        exitTransition = navExit,
                        popEnterTransition = navPopEnter,
                        popExitTransition = navPopExit,
                    ) {
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
                        enterTransition = navEnter,
                        exitTransition = navExit,
                        popEnterTransition = navPopEnter,
                        popExitTransition = navPopExit,
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
