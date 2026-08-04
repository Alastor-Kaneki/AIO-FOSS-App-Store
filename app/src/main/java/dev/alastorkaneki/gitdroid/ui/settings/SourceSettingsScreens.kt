package dev.alastorkaneki.gitdroid.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import dev.alastorkaneki.gitdroid.data.AppSettings
import dev.alastorkaneki.gitdroid.data.CatalogRepository
import dev.alastorkaneki.gitdroid.data.SourceKind
import dev.alastorkaneki.gitdroid.data.UiState
import dev.alastorkaneki.gitdroid.shizuku.ShizukuInstallerClient

@Composable
fun SourcesScreen(
    settings: AppSettings,
    state: UiState,
    onUpdate: (Boolean, (AppSettings) -> AppSettings) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Catalog sources", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("Choose which independent FOSS catalogs GitDroid combines.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            SourceCard(
                title = "F-Droid",
                subtitle = "Official F-Droid repository index",
                endpoint = CatalogRepository.FDROID_REPOSITORY,
                enabled = settings.fdroidEnabled,
                appCount = state.apps.count { it.source == SourceKind.FDROID },
                onEnabled = { value -> onUpdate(true) { it.copy(fdroidEnabled = value) } }
            )
        }
        item {
            SourceCard(
                title = "IzzyOnDroid",
                subtitle = "Optional F-Droid-compatible repository of upstream APKs",
                endpoint = CatalogRepository.IZZY_REPOSITORY,
                enabled = settings.izzyEnabled,
                appCount = state.apps.count { it.source == SourceKind.IZZY },
                onEnabled = { value -> onUpdate(true) { it.copy(izzyEnabled = value) } }
            )
        }
        item {
            SourceCard(
                title = "GitHub",
                subtitle = "Public Android repositories and their latest release APKs",
                endpoint = "api.github.com",
                enabled = settings.githubEnabled,
                appCount = state.apps.count { it.source == SourceKind.GITHUB },
                onEnabled = { value -> onUpdate(true) { it.copy(githubEnabled = value) } }
            )
        }
    }
}

@Composable
private fun SourceCard(
    title: String,
    subtitle: String,
    endpoint: String,
    enabled: Boolean,
    appCount: Int,
    onEnabled: (Boolean) -> Unit
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Dns, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = enabled, onCheckedChange = onEnabled)
            }
            Text(endpoint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("$appCount apps loaded", style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
fun SettingsScreen(
    settings: AppSettings,
    onUpdate: (Boolean, (AppSettings) -> AppSettings) -> Unit
) {
    var revealToken by remember { mutableStateOf(false) }
    val shizukuStatus = remember(settings.shizukuInstall) { ShizukuInstallerClient.statusLabel() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item { Text("Appearance", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
        item {
            SettingSwitch(
                icon = { Icon(Icons.Default.DarkMode, null) },
                title = "AMOLED black",
                subtitle = "Keep Material You accents with true-black surfaces",
                checked = settings.amoled,
                onChecked = { value -> onUpdate(false) { it.copy(amoled = value) } }
            )
        }
        item {
            SettingSwitch(
                icon = { Icon(Icons.Default.Palette, null) },
                title = "Dynamic color",
                subtitle = "Use the device wallpaper palette on Android 12+",
                checked = settings.dynamicColor,
                onChecked = { value -> onUpdate(false) { it.copy(dynamicColor = value) } }
            )
        }
        item {
            SettingSwitch(
                icon = { Icon(Icons.Default.Code, null) },
                title = "Immersive mode",
                subtitle = "Hide system bars; swipe from an edge to reveal them",
                checked = settings.immersive,
                onChecked = { value -> onUpdate(false) { it.copy(immersive = value) } }
            )
        }
        item {
            Text("Installation", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 14.dp))
        }
        item {
            SettingSwitch(
                icon = { Icon(Icons.Default.InstallMobile, null) },
                title = "Install with Shizuku",
                subtitle = "Use Shizuku's ADB or root service, with the normal installer as fallback",
                checked = settings.shizukuInstall,
                onChecked = { value -> onUpdate(false) { it.copy(shizukuInstall = value) } }
            )
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Shizuku status", fontWeight = FontWeight.SemiBold)
                    Text(shizukuStatus, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        "Permission is requested only when an installation starts.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        item {
            Text("GitHub", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 14.dp))
        }
        item {
            OutlinedTextField(
                value = settings.githubToken,
                onValueChange = { value -> onUpdate(false) { it.copy(githubToken = value.trim()) } },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Optional personal access token") },
                supportingText = { Text("Stored only in this app's private preferences; improves API rate limits.") },
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Key, null) },
                trailingIcon = {
                    IconButton(onClick = { revealToken = !revealToken }) {
                        Icon(if (revealToken) Icons.Default.VisibilityOff else Icons.Default.Visibility, null)
                    }
                },
                visualTransformation = if (revealToken) VisualTransformation.None else PasswordVisualTransformation()
            )
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Text(
                    "GitDroid never sends the token anywhere except api.github.com. F-Droid and IzzyOnDroid requests do not receive it.",
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SettingSwitch(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        ListItem(
            headlineContent = { Text(title, fontWeight = FontWeight.SemiBold) },
            supportingContent = { Text(subtitle) },
            leadingContent = icon,
            trailingContent = { Switch(checked = checked, onCheckedChange = onChecked) }
        )
    }
}
