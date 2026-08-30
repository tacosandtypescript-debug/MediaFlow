package com.mediaflow.app.ui.home.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mediaflow.app.R
import com.mediaflow.app.ui.common.media.MediaArtwork
import com.mediaflow.app.ui.common.media.preferredArtworkUrl
import com.mediaflow.app.ui.home.AnalysisState
import com.mediaflow.app.ui.home.ContentType
import com.mediaflow.core.model.MediaFormat
import com.mediaflow.core.model.MediaType
import com.mediaflow.domain.repository.SourceInfo

@Composable
fun SourceAnalysisCard(
    state: AnalysisState,
    sourceInfo: SourceInfo?,
    selectedType: ContentType,
    selectedFormat: MediaFormat?,
    errorMessage: String?,
    modifier: Modifier = Modifier,
) {
    if (state == AnalysisState.IDLE) return
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("source_analysis_card"),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
    ) {
        when (state) {
            AnalysisState.ANALYZING -> {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = stringResource(R.string.analysis_loading),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
            AnalysisState.FAILED -> {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = stringResource(R.string.analysis_failed),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = errorMessage ?: sourceInfo?.errorMessage
                            ?: stringResource(R.string.analysis_failed),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            AnalysisState.READY -> {
                if (sourceInfo == null) return@Surface
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    MediaArtwork(
                        artworkUrl = preferredArtworkUrl(sourceInfo.thumbnailUrl),
                        size = 64.dp,
                        shape = RoundedCornerShape(8.dp),
                        mediaType = if (selectedType == ContentType.VIDEO) {
                            MediaType.VIDEO
                        } else {
                            MediaType.AUDIO
                        },
                        contentDescription = stringResource(
                            if (sourceInfo.thumbnailUrl.isNullOrBlank()) {
                                R.string.analysis_no_thumbnail
                            } else {
                                R.string.analysis_thumbnail
                            },
                        ),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.analysis_ready),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = sourceInfo.title ?: stringResource(R.string.analysis_untitled),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        val playlistCount = sourceInfo.playlistEntries.size
                        val meta = if (playlistCount > 0) {
                            stringResource(R.string.analysis_playlist_count, playlistCount)
                        } else {
                            sourceInfo.durationSeconds?.let(::formatDuration)
                        }
                        if (!meta.isNullOrBlank()) {
                            Text(
                                text = meta,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Text(
                            text = stringResource(
                                R.string.analysis_download_type,
                                stringResource(selectedType.descriptionRes),
                            ),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (selectedFormat != null) {
                            Text(
                                text = formatLabel(selectedFormat),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
            AnalysisState.IDLE -> Unit
        }
    }
}

@Composable
private fun formatLabel(format: MediaFormat): String = buildString {
    append(
        when {
            format.mediaType == MediaType.VIDEO && format.requiresMuxing ->
                stringResource(R.string.format_video_audio_added)
            format.mediaType == MediaType.VIDEO -> stringResource(R.string.format_video_with_audio)
            else -> stringResource(R.string.format_audio_only)
        },
    )
    append(" · ")
    append(format.qualityLabel ?: format.height?.let { "${it}p" } ?: format.extension ?: "Formato")
    format.extension?.let { append(" · .$it") }
    format.container?.let { append(" · $it") }
    format.videoCodec?.let { append(" · $it") }
    format.audioCodec?.let { append(" + $it") }
    format.fps?.let { append(" · ${it.toInt()} FPS") }
    format.fileSize?.let { append(" · ${formatBytes(it)}") }
    if (format.requiresMuxing) {
        append(" · ")
        append(stringResource(R.string.format_audio_added))
    }
}

private fun formatDuration(seconds: Long): String = "%d:%02d".format(seconds / 60, seconds % 60)

private fun formatBytes(value: Long): String = when {
    value >= 1_000_000L -> "%.1f MB".format(value / 1_000_000f)
    value >= 1_000L -> "%.1f KB".format(value / 1_000f)
    else -> "$value B"
}
