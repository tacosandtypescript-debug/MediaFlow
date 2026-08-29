package com.mediaflow.app.ui.player.live

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mediaflow.app.R
import com.mediaflow.app.ui.theme.customColors
import com.mediaflow.domain.live.LiveSpaceEndState

/**
 * Overlay card displayed when an active live Space finishes.
 */
@Composable
fun LiveEndedContent(
    endState: LiveSpaceEndState,
    onDownloadReplay: (replayUrl: String) -> Unit,
    onCheckReplayAgain: () -> Unit,
    modifier: Modifier = Modifier,
    isAutoDownloadEnabled: Boolean = false,
) {
    if (endState is LiveSpaceEndState.ActiveLive) return

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
            .testTag("live_ended_content"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.customColors.outlineSoft),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ) {
                Text(
                    text = stringResource(R.string.space_ended_pill),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }

            Spacer(Modifier.height(14.dp))

            Text(
                text = stringResource(R.string.space_ended_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(8.dp))

            when (endState) {
                is LiveSpaceEndState.EndedResolvingReplay,
                is LiveSpaceEndState.EndedReplayProcessing,
                -> {
                    Text(
                        text = stringResource(R.string.space_waiting_replay),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.space_waiting_replay_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(18.dp))
                    CircularProgressIndicator(
                        modifier = Modifier.size(32.dp),
                        strokeWidth = 3.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                is LiveSpaceEndState.EndedReplayAvailable -> {
                    Text(
                        text = stringResource(R.string.space_replay_ready),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    if (isAutoDownloadEnabled) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = stringResource(R.string.space_auto_download_hint),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                        )
                    } else {
                        Spacer(Modifier.height(20.dp))
                        Button(
                            onClick = { onDownloadReplay(endState.replayUrl) },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Download,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(18.dp)
                                    .padding(end = 6.dp),
                            )
                            Text(stringResource(R.string.space_download_replay), fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                is LiveSpaceEndState.EndedNoReplay -> {
                    Text(
                        text = endState.reason.ifBlank { stringResource(R.string.space_no_replay) },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    if (endState.canCheckAgain) {
                        Spacer(Modifier.height(20.dp))
                        OutlinedButton(
                            onClick = onCheckReplayAgain,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Refresh,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(18.dp)
                                    .padding(end = 6.dp),
                            )
                            Text(stringResource(R.string.space_check_again), fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                is LiveSpaceEndState.EndedDownloadStarted -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                        Text(
                            text = stringResource(R.string.space_download_queued),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                else -> Unit
            }
        }
    }
}
