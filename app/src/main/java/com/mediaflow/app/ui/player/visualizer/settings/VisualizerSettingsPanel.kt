package com.mediaflow.app.ui.player.visualizer.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mediaflow.app.R

@Composable
fun VisualizerSettingsPanel(
    settings: VisualizerSettings,
    onEnabled: (Boolean) -> Unit,
    onStyle: (VisualizerStyle) -> Unit,
    onIntensity: (Float) -> Unit,
    onMotion: (Float) -> Unit,
    onCoverColors: (Boolean) -> Unit,
    onBars: (Boolean) -> Unit,
    onReducePause: (Boolean) -> Unit,
    onBattery: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        RowSwitch(
            label = stringResource(R.string.settings_visualizer_enable),
            checked = settings.enabled,
            onChecked = onEnabled,
            tag = "visualizer_enabled",
        )
        if (settings.enabled) {
            Text(
                text = stringResource(R.string.settings_visualizer_style),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(top = 12.dp),
            )
            VisualizerStyle.entries.forEach { style ->
                TextButton(
                    onClick = { onStyle(style) },
                    modifier = Modifier.testTag("visualizer_style_${style.name.lowercase()}"),
                ) {
                    Text(
                        text = styleLabel(style),
                        color = if (settings.style == style) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                }
            }
            Text(stringResource(R.string.settings_visualizer_intensity), style = MaterialTheme.typography.labelLarge)
            Slider(value = settings.intensity, onValueChange = onIntensity, modifier = Modifier.testTag("visualizer_intensity"))
            Text(stringResource(R.string.settings_visualizer_motion), style = MaterialTheme.typography.labelLarge)
            Slider(value = settings.motion, onValueChange = onMotion, modifier = Modifier.testTag("visualizer_motion"))
            RowSwitch(stringResource(R.string.settings_visualizer_cover), settings.useCoverColors, onCoverColors, "visualizer_cover")
            RowSwitch(stringResource(R.string.settings_visualizer_bars), settings.dynamicSystemBars, onBars, "visualizer_bars")
            RowSwitch(stringResource(R.string.settings_visualizer_pause), settings.reduceOnPause, onReducePause, "visualizer_pause")
            RowSwitch(stringResource(R.string.settings_visualizer_battery), settings.batterySaver, onBattery, "visualizer_battery")
        }
    }
}

@Composable
private fun RowSwitch(label: String, checked: Boolean, onChecked: (Boolean) -> Unit, tag: String) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChecked, modifier = Modifier.testTag(tag))
    }
}

@Composable
private fun styleLabel(style: VisualizerStyle): String = stringResource(
    when (style) {
        VisualizerStyle.BALLS -> R.string.visualizer_style_balls
        VisualizerStyle.AURORA -> R.string.visualizer_style_aurora
        VisualizerStyle.WAVES -> R.string.visualizer_style_waves
        VisualizerStyle.RINGS -> R.string.visualizer_style_rings
        VisualizerStyle.PARTICLES -> R.string.visualizer_style_particles
        VisualizerStyle.SPECTRUM -> R.string.visualizer_style_spectrum
    },
)
