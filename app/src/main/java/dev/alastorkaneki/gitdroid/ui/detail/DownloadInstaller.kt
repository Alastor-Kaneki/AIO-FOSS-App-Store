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
import dev.alastorkaneki.gitdroid.data.InstallOriginStore
import dev.alastorkaneki.gitdroid.data.StoreApp
import dev.alastorkaneki.gitdroid.shizuku.ShizukuInstallerClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

fun downloadAndInstall(
    activity: ComponentActivity,
    app: StoreApp,
    preferShizuku: Boolean,
    onInstallFinished: () -> Unit
) {
    val url = app.apkUrl ?: return
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    ) {
        ActivityCompat.requestPermissions(activity, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 701)
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

        val downloadUri = manager.getUriForDownloadedFile(id)
        if (downloadUri == null) {
            Toast.makeText(activity, "Open Downloads/GitDroid to install the APK.", Toast.LENGTH_LONG).show()
            return@launch
        }

        val cachedApk = withContext(Dispatchers.IO) { copyToPrivateCache(activity, downloadUri, fileName) }
        val detectedPackage = cachedApk?.let { withContext(Dispatchers.IO) { packageNameFromArchive(activity, it) } }
            ?: app.packageName

        if (preferShizuku && cachedApk != null) {
            val result = ShizukuInstallerClient.install(activity, cachedApk)
            if (result.success) {
                detectedPackage?.let { InstallOriginStore(activity).record(it, app.copy(packageName = it)) }
                Toast.makeText(activity, "${app.name} installed through Shizuku.", Toast.LENGTH_LONG).show()
                cachedApk.delete()
                onInstallFinished()
                return@launch
            }
            Toast.makeText(
                activity,
                "Shizuku install unavailable: ${result.message}. Using Android's installer.",
                Toast.LENGTH_LONG
            ).show()
        }

        detectedPackage?.let { InstallOriginStore(activity).record(it, app.copy(packageName = it)) }
        cachedApk?.delete()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !activity.packageManager.canRequestPackageInstalls()) {
            Toast.makeText(
                activity,
                "The APK is saved in Downloads/GitDroid. Enable ‘Install unknown apps’ for GitDroid, then open it.",
                Toast.LENGTH_LONG
            ).show()
            activity.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${activity.packageName}"))
            )
            return@launch
        }

        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(downloadUri, APK_MIME)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { activity.startActivity(intent) }.onFailure {
            Toast.makeText(activity, "Open Downloads/GitDroid to install the APK.", Toast.LENGTH_LONG).show()
        }
    }
}

private fun copyToPrivateCache(context: Context, source: Uri, fileName: String): File? = runCatching {
    val directory = File(context.cacheDir, "install").apply { mkdirs() }
    val destination = File(directory, fileName)
    context.contentResolver.openInputStream(source)?.use { input ->
        FileOutputStream(destination).use { output -> input.copyTo(output) }
    } ?: return null
    destination.takeIf { it.length() > 0L }
}.getOrNull()

@Suppress("DEPRECATION")
private fun packageNameFromArchive(context: Context, file: File): String? {
    val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        context.packageManager.getPackageArchiveInfo(file.absolutePath, PackageManager.PackageInfoFlags.of(0L))
    } else {
        context.packageManager.getPackageArchiveInfo(file.absolutePath, 0)
    }
    return info?.packageName
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
