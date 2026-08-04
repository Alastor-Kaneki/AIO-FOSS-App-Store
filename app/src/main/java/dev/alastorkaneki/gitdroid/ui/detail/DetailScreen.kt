package dev.alastorkaneki.gitdroid.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.alastorkaneki.gitdroid.data.StoreApp
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDetailScreen(
    activity: ComponentActivity,
    app: StoreApp,
    resolving: Boolean,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(app.name) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") } }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppGlyph(app, Modifier.size(92.dp))
                    Spacer(Modifier.width(18.dp))
                    Column(Modifier.weight(1f)) {
                        Text(app.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                        SourceBadge(app.source)
                        app.versionName?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }
            item {
                Button(
                    onClick = { downloadAndInstall(activity, app) },
                    enabled = app.apkUrl != null && !resolving,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (resolving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Default.Download, null)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        when {
                            resolving -> "Finding compatible APK"
                            app.apkUrl == null -> "No APK in latest release"
                            else -> "Download and install${app.apkSize?.let { " · ${readableBytes(it)}" }.orEmpty()}"
                        }
                    )
                }
            }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(app.summary.ifBlank { "Open-source Android application" }, style = MaterialTheme.typography.titleMedium)
                        HorizontalDivider()
                        Text(app.description.ifBlank { app.summary }, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Details", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    DetailLine("Source", app.source.label)
                    app.packageName?.let { DetailLine("Package", it) }
                    app.license?.let { DetailLine("License", it) }
                    app.categories.takeIf(List<String>::isNotEmpty)?.let { DetailLine("Categories", it.joinToString()) }
                    app.stars?.let { DetailLine("GitHub stars", compactNumberDetail(it)) }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    app.projectUrl?.let { url ->
                        OutlinedButton(onClick = { openLink(activity, url) }) {
                            Icon(Icons.Default.Language, null)
                            Spacer(Modifier.width(6.dp))
                            Text("Website")
                        }
                    }
                    app.sourceCodeUrl?.let { url ->
                        OutlinedButton(onClick = { openLink(activity, url) }) {
                            Icon(Icons.Default.Code, null)
                            Spacer(Modifier.width(6.dp))
                            Text("Source")
                        }
                    }
                }
            }
            item {
                Text(
                    "APK installation is handed to Android's system package installer. Review the source, requested permissions, and signing identity before installing.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, modifier = Modifier.width(112.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
    }
}

private fun openLink(activity: ComponentActivity, url: String) {
    runCatching { activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}

private fun readableBytes(bytes: Long): String {
    if (bytes < 1_000) return "$bytes B"
    val units = arrayOf("kB", "MB", "GB")
    var value = bytes.toDouble()
    var unit = -1
    while (value >= 1_000 && unit < units.lastIndex) {
        value /= 1_000
        unit++
    }
    return String.format(Locale.ROOT, "%.1f %s", value, units[unit])
}

private fun compactNumberDetail(value: Long): String = when {
    value >= 1_000_000 -> String.format(Locale.ROOT, "%.1fM", value / 1_000_000.0)
    value >= 1_000 -> String.format(Locale.ROOT, "%.1fk", value / 1_000.0)
    else -> value.toString()
}
