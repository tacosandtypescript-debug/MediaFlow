package com.mediaflow.app.ui.home.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mediaflow.app.R
import com.mediaflow.app.ui.home.AnalysisState
import com.mediaflow.app.ui.home.ContentType
import com.mediaflow.core.model.MediaFormat
import com.mediaflow.core.model.MediaType
import com.mediaflow.domain.repository.SourceInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL

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
    Card(
        modifier = modifier.fillMaxWidth().testTag("source_analysis_card"),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = when (state) {
                    AnalysisState.ANALYZING -> stringResource(R.string.analysis_loading)
                    AnalysisState.READY -> stringResource(R.string.analysis_ready)
                    AnalysisState.FAILED -> stringResource(R.string.analysis_failed)
                    AnalysisState.IDLE -> ""
                },
                style = MaterialTheme.typography.titleMedium,
            )
            if (state == AnalysisState.ANALYZING) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            if (state == AnalysisState.FAILED) {
                Text(
                    text = errorMessage ?: sourceInfo?.errorMessage ?: stringResource(R.string.analysis_failed),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (state == AnalysisState.READY && sourceInfo != null) {
                ThumbnailPreview(sourceInfo.thumbnailUrl)
                Text(sourceInfo.title ?: stringResource(R.string.analysis_untitled), style = MaterialTheme.typography.titleLarge)
                val playlistCount = sourceInfo.playlistEntries.size
                if (playlistCount > 0) {
                    Text(
                        text = stringResource(R.string.analysis_playlist_count, playlistCount),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    sourceInfo.durationSeconds?.let { seconds ->
                        Text(
                            text = formatDuration(seconds),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Text(
                    text = stringResource(
                        R.string.analysis_download_type,
                        stringResource(selectedType.descriptionRes),
                    ),
                    style = MaterialTheme.typography.labelLarge,
                )
                if (selectedFormat != null) {
                    Text(
                        text = formatLabel(selectedFormat),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ThumbnailPreview(url: String?) {
    val bitmap by produceState<android.graphics.Bitmap?>(initialValue = null, key1 = url) {
        value = withContext(Dispatchers.IO) {
            runCatching { url?.takeIf { it.startsWith("https://") }?.let { BitmapFactory.decodeStream(URL(it).openStream()) } }.getOrNull()
        }
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap!!.asImageBitmap(),
            contentDescription = stringResource(R.string.analysis_thumbnail),
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(20.dp)),
        )
    } else {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Image, contentDescription = null, modifier = Modifier.size(28.dp))
            Text(stringResource(R.string.analysis_no_thumbnail), modifier = Modifier.padding(start = 8.dp))
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
