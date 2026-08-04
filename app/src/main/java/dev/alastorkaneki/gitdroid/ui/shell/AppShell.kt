package dev.alastorkaneki.gitdroid.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.alastorkaneki.gitdroid.data.AppSettings
import dev.alastorkaneki.gitdroid.data.StoreApp
import dev.alastorkaneki.gitdroid.data.UiState

enum class AppTab(val label: String) {
    DISCOVER("Discover"),
    INSTALLED("Installed"),
    CATEGORIES("Categories"),
    SOURCES("Sources"),
    SETTINGS("Settings")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GitDroidApp(
    activity: ComponentActivity,
    state: UiState,
    settings: AppSettings,
    onRefresh: () -> Unit,
    onRefreshInstalled: () -> Unit,
    onSelectApp: (StoreApp) -> Unit,
    onCloseDetails: () -> Unit,
    onUpdateSettings: (Boolean, (AppSettings) -> AppSettings) -> Unit
) {
    if (state.selectedApp != null) {
        AppDetailScreen(
            activity = activity,
            app = state.selectedApp,
            resolving = state.resolvingRelease,
            preferShizuku = settings.shizukuInstall,
            onInstallFinished = onRefreshInstalled,
            onBack = onCloseDetails
        )
        return
    }

    var tab by rememberSaveable { mutableStateOf(AppTab.DISCOVER) }
    var query by rememberSaveable { mutableStateOf("") }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    androidx.compose.foundation.layout.Column {
                        Text("GitDroid")
                        if (state.status.isNotBlank()) Text(state.status, style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
                    }
                },
                actions = {
                    if (state.loading) CircularProgressIndicator(Modifier.padding(12.dp), strokeWidth = 2.dp)
                    else IconButton(
                        onClick = if (tab == AppTab.INSTALLED) onRefreshInstalled else onRefresh
                    ) {
                        Icon(Icons.Default.Refresh, if (tab == AppTab.INSTALLED) "Refresh installed apps" else "Refresh catalogs")
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                AppTab.entries.forEach { item ->
                    val icon = when (item) {
                        AppTab.DISCOVER -> Icons.Default.Home
                        AppTab.INSTALLED -> Icons.Default.DownloadDone
                        AppTab.CATEGORIES -> Icons.Default.Category
                        AppTab.SOURCES -> Icons.Default.Dns
                        AppTab.SETTINGS -> Icons.Default.Settings
                    }
                    NavigationBarItem(
                        selected = tab == item,
                        onClick = {
                            tab = item
                            if (item == AppTab.INSTALLED) onRefreshInstalled()
                        },
                        icon = { Icon(icon, null) },
                        label = { Text(item.label) }
                    )
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (tab) {
                AppTab.DISCOVER -> DiscoverScreen(state, query, { query = it }, onRefresh, onSelectApp)
                AppTab.INSTALLED -> InstalledScreen(state.apps, onRefresh, onSelectApp)
                AppTab.CATEGORIES -> CategoriesScreen(state.apps, onSelectApp)
                AppTab.SOURCES -> SourcesScreen(settings, state, onUpdateSettings)
                AppTab.SETTINGS -> SettingsScreen(settings, onUpdateSettings)
            }
            if (state.loading && state.apps.isEmpty()) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            }
        }
    }
}
