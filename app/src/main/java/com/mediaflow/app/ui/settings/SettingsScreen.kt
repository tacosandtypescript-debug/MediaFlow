package com.mediaflow.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mediaflow.app.BuildConfig
import com.mediaflow.app.R
import com.mediaflow.app.ui.theme.ThemeMode

private val themeModes: List<Pair<ThemeMode, Int>> = listOf(
    ThemeMode.SYSTEM to R.string.settings_theme_auto,
    ThemeMode.LIGHT to R.string.settings_theme_light,
    ThemeMode.DARK to R.string.settings_theme_dark,
)

/**
 * Settings screen redesigned into expressive, clearly separated sections
 * (Apariencia, Descargas, Notificaciones, Almacenamiento, Acerca de).
 *
 * The theme selector controls the global, persisted [ThemeMode]. The remaining
 * controls use local state in this phase.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    var wifiOnly by rememberSaveable { mutableStateOf(false) }
    var notifications by rememberSaveable { mutableStateOf(true) }
    var defaultQualityExpanded by rememberSaveable { mutableStateOf(false) }
    var defaultQuality by rememberSaveable { mutableIntStateOf(R.string.quality_auto) }

    Scaffold(
        modifier = modifier,
        containerColor = Color.Transparent,
        topBar = {
            CenterAlignedTopAppBar(title = { Text(stringResource(R.string.nav_settings)) })
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxWidth()
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp),
        ) {
            // Apariencia
            SettingsSectionHeader(stringResource(R.string.settings_section_appearance))
            SettingsCard {
                SettingsLabel(stringResource(R.string.settings_theme))
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                ) {
                    themeModes.forEachIndexed { index, (mode, labelRes) ->
                        SegmentedButton(
                            selected = themeMode == mode,
                            onClick = { onThemeModeChange(mode) },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = themeModes.size),
                        ) {
                            Text(stringResource(labelRes))
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Descargas
            SettingsSectionHeader(stringResource(R.string.nav_downloads))
            SettingsCard {
                SettingsSwitchRow(
                    label = stringResource(R.string.settings_wifi_only),
                    checked = wifiOnly,
                    onCheckedChange = { wifiOnly = it },
                )
                SettingsLabel(stringResource(R.string.settings_default_quality))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                        .clickable { defaultQualityExpanded = true },
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(defaultQuality),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(Icons.Outlined.ArrowDropDown, contentDescription = null)
                    }
                    DropdownMenu(
                        expanded = defaultQualityExpanded,
                        onDismissRequest = { defaultQualityExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.quality_auto)) },
                            onClick = {
                                defaultQuality = R.string.quality_auto
                                defaultQualityExpanded = false
                            },
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Notificaciones
            SettingsSectionHeader(stringResource(R.string.settings_section_notifications))
            SettingsCard {
                SettingsSwitchRow(
                    label = stringResource(R.string.settings_notifications),
                    checked = notifications,
                    onCheckedChange = { notifications = it },
                )
            }

            Spacer(Modifier.height(16.dp))

            // Almacenamiento (pending)
            SettingsSectionHeader(stringResource(R.string.settings_section_storage))
            SettingsCard {
                SettingsSwitchRow(
                    label = stringResource(R.string.settings_storage_pending),
                    checked = false,
                    onCheckedChange = {},
                )
            }

            Spacer(Modifier.height(16.dp))

            // Acerca de
            SettingsSectionHeader(stringResource(R.string.settings_section_about))
            SettingsCard {
                SettingsLabel(stringResource(R.string.app_name))
                Text(
                    text = stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            content = content,
        )
    }
}

@Composable
private fun SettingsSectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 6.dp),
    )
}

@Composable
private fun SettingsLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun SettingsSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}
