package dev.alastorkaneki.gitdroid.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.alastorkaneki.gitdroid.data.SourceKind
import dev.alastorkaneki.gitdroid.data.StoreApp
import dev.alastorkaneki.gitdroid.data.UiState
import java.util.Locale

@Composable
fun DiscoverScreen(
    state: UiState,
    query: String,
    onQueryChange: (String) -> Unit,
    onRetry: () -> Unit,
    onSelectApp: (StoreApp) -> Unit
) {
    var source by rememberSaveable { mutableStateOf<SourceKind?>(null) }
    val filtered = remember(state.apps, query, source) {
        val needle = query.trim().lowercase(Locale.ROOT)
        state.apps.filter { app ->
            (source == null || app.source == source) &&
                (needle.isBlank() || listOf(app.name, app.summary, app.categories.joinToString()).any {
                    needle in it.lowercase(Locale.ROOT)
                })
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, null) },
                label = { Text("Search FOSS apps") }
            )
        }
        item { SourceFilters(source = source, onSource = { source = it }) }
        state.warnings.forEach { warning -> item { WarningBanner(warning) } }
        if (state.error != null && !state.loading) {
            item { EmptyMessage(state.error, onRetry) }
        } else if (filtered.isEmpty() && !state.loading) {
            item { EmptyMessage("No apps match the current search and source filters.", onRetry) }
        } else {
            items(filtered, key = { it.id }) { app -> AppRow(app, onClick = { onSelectApp(app) }) }
        }
    }
}

@Composable
fun InstalledScreen(
    apps: List<StoreApp>,
    onRetry: () -> Unit,
    onSelectApp: (StoreApp) -> Unit
) {
    var source by rememberSaveable { mutableStateOf<SourceKind?>(null) }
    val installed = remember(apps, source) {
        apps.filter { it.isInstalled && (source == null || it.source == source) }
    }
    val totalInstalled = remember(apps) { apps.count(StoreApp::isInstalled) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Installed apps", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                "$totalInstalled apps matched to enabled catalogs and remembered GitDroid installs",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        item { SourceFilters(source = source, onSource = { source = it }) }
        if (installed.isEmpty()) {
            item {
                EmptyMessage(
                    if (source == null) "No installed apps have been matched yet." else "No installed ${source?.label} apps were found.",
                    onRetry
                )
            }
        } else {
            items(installed, key = { "installed:${it.id}" }) { app ->
                AppRow(app, onClick = { onSelectApp(app) })
            }
        }
    }
}

@Composable
private fun SourceFilters(source: SourceKind?, onSource: (SourceKind?) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = source == null, onClick = { onSource(null) }, label = { Text("All") })
        SourceKind.entries.forEach { kind ->
            FilterChip(selected = source == kind, onClick = { onSource(kind) }, label = { Text(kind.label) })
        }
    }
}

@Composable
fun CategoriesScreen(apps: List<StoreApp>, onSelectApp: (StoreApp) -> Unit) {
    val categories = remember(apps) {
        apps.flatMap(StoreApp::categories).groupingBy { it }.eachCount().toList().sortedByDescending { it.second }
    }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    val visible = remember(apps, selected) { selected?.let { category -> apps.filter { category in it.categories } } ?: emptyList() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text(selected ?: "App categories", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
        if (selected == null) {
            items(categories, key = { it.first }) { (category, count) ->
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { selected = category },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                ) {
                    Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Apps, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(14.dp))
                        Text(category, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                        Text("$count apps", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        } else {
            item { AssistChip(onClick = { selected = null }, label = { Text("All categories") }) }
            items(visible, key = { it.id }) { app -> AppRow(app, onClick = { onSelectApp(app) }) }
        }
    }
}

@Composable
fun AppRow(app: StoreApp, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            AppGlyph(app, Modifier.size(58.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(app.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    app.summary.ifBlank { "Open-source Android application" },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    SourceBadge(app.source)
                    app.versionName?.let { Text(it, style = MaterialTheme.typography.labelMedium) }
                    app.stars?.let { Text("★ ${compactNumber(it)}", style = MaterialTheme.typography.labelMedium) }
                }
                if (app.isInstalled) {
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Icon(
                            Icons.Default.CheckCircle,
                            null,
                            Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            "Installed ${app.installedVersionName.orEmpty()}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AppGlyph(app: StoreApp, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = sourceColor(app.source).copy(alpha = 0.18f))
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                app.name.firstOrNull()?.uppercase() ?: "?",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
                color = sourceColor(app.source)
            )
            app.iconUrl?.let { iconUrl ->
                AsyncImage(
                    model = iconUrl,
                    contentDescription = "${app.name} icon",
                    modifier = Modifier.fillMaxSize().padding(5.dp),
                    contentScale = ContentScale.Fit
                )
            }
        }
    }
}

@Composable
fun SourceBadge(source: SourceKind) {
    AssistChip(onClick = {}, enabled = false, label = { Text(source.label) })
}

@Composable
private fun WarningBanner(message: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.onErrorContainer)
            Spacer(Modifier.width(10.dp))
            Text(message, color = MaterialTheme.colorScheme.onErrorContainer)
        }
    }
}

@Composable
private fun EmptyMessage(message: String, onRetry: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.Apps, null, Modifier.size(52.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(12.dp))
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        AssistChip(onClick = onRetry, label = { Text("Reload catalogs") })
    }
}

private fun sourceColor(source: SourceKind): Color = when (source) {
    SourceKind.FDROID -> Color(0xFF7CB342)
    SourceKind.IZZY -> Color(0xFFFFB300)
    SourceKind.GITHUB -> Color(0xFF9C7CFF)
}

private fun compactNumber(value: Long): String = when {
    value >= 1_000_000 -> String.format(Locale.ROOT, "%.1fM", value / 1_000_000.0)
    value >= 1_000 -> String.format(Locale.ROOT, "%.1fk", value / 1_000.0)
    else -> value.toString()
}
