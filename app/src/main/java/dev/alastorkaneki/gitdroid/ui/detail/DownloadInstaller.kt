package dev.alastorkaneki.gitdroid.ui

import android.Manifest
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import dev.alastorkaneki.gitdroid.data.StoreApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

fun downloadAndInstall(activity: ComponentActivity, app: StoreApp) {
    val url = app.apkUrl ?: return
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    ) {
        ActivityCompat.requestPermissions(activity, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 701)
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !activity.packageManager.canRequestPackageInstalls()) {
        Toast.makeText(activity, "Enable ‘Install unknown apps’ for GitDroid, then tap download again.", Toast.LENGTH_LONG).show()
        activity.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${activity.packageName}")))
        return
    }

    val manager = activity.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    val fileName = (app.apkName ?: "${app.name}-${app.versionName ?: "latest"}.apk")
        .replace(Regex("[^A-Za-z0-9._-]"), "_")
        .let { if (it.endsWith(".apk", true)) it else "$it.apk" }
    val request = DownloadManager.Request(Uri.parse(url))
        .setTitle(app.name)
        .setDescription("Downloading ${app.versionName ?: "latest release"}")
        .setMimeType(APK_MIME)
        .setAllowedOverMetered(true)
        .setAllowedOverRoaming(false)
        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "GitDroid/$fileName")

    val id = runCatching { manager.enqueue(request) }.getOrElse {
        Toast.makeText(activity, "Download failed: ${it.message}", Toast.LENGTH_LONG).show()
        return
    }
    Toast.makeText(activity, "Downloading $fileName", Toast.LENGTH_SHORT).show()
    activity.lifecycleScope.launch {
        val successful = withContext(Dispatchers.IO) { waitForDownload(manager, id) }
        if (!successful) {
            Toast.makeText(activity, "The APK download did not complete.", Toast.LENGTH_LONG).show()
            return@launch
        }
        val uri = manager.getUriForDownloadedFile(id)
        if (uri == null) {
            Toast.makeText(activity, "Open Downloads/GitDroid to install the APK.", Toast.LENGTH_LONG).show()
            return@launch
        }
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, APK_MIME)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { activity.startActivity(intent) }.onFailure {
            Toast.makeText(activity, "Open Downloads/GitDroid to install the APK.", Toast.LENGTH_LONG).show()
        }
    }
}

private suspend fun waitForDownload(manager: DownloadManager, id: Long): Boolean {
    val query = DownloadManager.Query().setFilterById(id)
    repeat(1_800) {
        manager.query(query)?.use { cursor ->
            if (cursor.moveToFirst()) {
                when (cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))) {
                    DownloadManager.STATUS_SUCCESSFUL -> return true
                    DownloadManager.STATUS_FAILED -> return false
                }
            }
        }
        delay(1_000)
    }
    return false
}

private const val APK_MIME = "application/vnd.android.package-archive"
