package dev.alastorkaneki.gitdroid.ui

import android.app.PendingIntent
import android.content.ClipData
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import dev.alastorkaneki.gitdroid.data.InstallOriginStore
import dev.alastorkaneki.gitdroid.data.StoreApp
import dev.alastorkaneki.gitdroid.install.PackageInstallReceiver
import dev.alastorkaneki.gitdroid.shizuku.ShizukuInstallerClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

fun downloadAndInstall(
    activity: ComponentActivity,
    app: StoreApp,
    preferShizuku: Boolean,
    onInstallFinished: () -> Unit,
    onStatus: (String?) -> Unit = {}
) {
    val url = app.apkUrl ?: return
    val fileName = (app.apkName ?: "${app.name}-${app.versionName ?: "latest"}.apk")
        .replace(Regex("[^A-Za-z0-9._-]"), "_")
        .let { if (it.endsWith(".apk", true)) it else "$it.apk" }

    onStatus("Downloading APK")
    Toast.makeText(activity, "Downloading $fileName", Toast.LENGTH_SHORT).show()

    activity.lifecycleScope.launch {
        val downloaded = withContext(Dispatchers.IO) {
            runCatching { downloadApk(activity.cacheDir, url, fileName, app.apkSize) }
        }.getOrElse { error ->
            onStatus(null)
            Toast.makeText(
                activity,
                "Download failed: ${error.message ?: error.javaClass.simpleName}",
                Toast.LENGTH_LONG
            ).show()
            return@launch
        }

        val detectedPackage = withContext(Dispatchers.IO) { packageNameFromArchive(activity, downloaded) }
        if (detectedPackage.isNullOrBlank()) {
            downloaded.delete()
            onStatus(null)
            Toast.makeText(activity, "The downloaded file is not a valid Android APK.", Toast.LENGTH_LONG).show()
            return@launch
        }

        if (app.packageName != null && app.packageName != detectedPackage) {
            downloaded.delete()
            onStatus(null)
            Toast.makeText(
                activity,
                "Install blocked: expected ${app.packageName}, but the APK contains $detectedPackage.",
                Toast.LENGTH_LONG
            ).show()
            return@launch
        }

        val attributedApp = app.copy(packageName = detectedPackage)

        if (preferShizuku) {
            onStatus("Installing through Shizuku")
            val result = ShizukuInstallerClient.install(activity, downloaded)
            if (result.success) {
                InstallOriginStore(activity).record(detectedPackage, attributedApp)
                downloaded.delete()
                onStatus(null)
                Toast.makeText(activity, "${app.name} installed through Shizuku.", Toast.LENGTH_LONG).show()
                onInstallFinished()
                return@launch
            }

            Toast.makeText(
                activity,
                "Shizuku could not install it: ${result.message}. Opening Android's installer.",
                Toast.LENGTH_LONG
            ).show()
        }

        InstallOriginStore(activity).record(detectedPackage, attributedApp)
        onStatus("Preparing Android installer")

        val queued = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !activity.packageManager.canRequestPackageInstalls()
        ) {
            false
        } else {
            withContext(Dispatchers.IO) {
                queuePackageInstallerSession(activity, downloaded, attributedApp)
            }
        }

        if (queued) {
            downloaded.delete()
            onStatus(null)
            Toast.makeText(activity, "Android installer opened for ${app.name}.", Toast.LENGTH_SHORT).show()
            return@launch
        }

        onStatus(null)
        if (!launchSystemInstaller(activity, downloaded)) {
            Toast.makeText(
                activity,
                "Could not open Android's package installer. The APK remains in GitDroid's cache.",
                Toast.LENGTH_LONG
            ).show()
        }
    }
}

private fun downloadApk(
    cacheDirectory: File,
    originalUrl: String,
    fileName: String,
    expectedSize: Long?
): File {
    val directory = File(cacheDirectory, "install").apply { mkdirs() }
    val destination = File(directory, fileName)
    val temporary = File(directory, "$fileName.part")
    temporary.delete()

    var current = URL(originalUrl)
    repeat(MAX_REDIRECTS) {
        val connection = (current.openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 180_000
            instanceFollowRedirects = false
            setRequestProperty("Accept", "application/vnd.android.package-archive, application/octet-stream")
            setRequestProperty("User-Agent", USER_AGENT)
        }

        try {
            val status = connection.responseCode
            if (status in REDIRECT_CODES) {
                val location = connection.getHeaderField("Location")
                    ?: error("Download redirect did not include a destination")
                current = URL(current, location)
                return@repeat
            }
            check(status in 200..299) { "Server returned HTTP $status" }

            BufferedInputStream(connection.inputStream).use { input ->
                FileOutputStream(temporary).use { output ->
                    input.copyTo(output, DEFAULT_BUFFER_SIZE)
                    output.flush()
                    output.fd.sync()
                }
            }

            check(temporary.length() > 0L) { "The server returned an empty file" }
            if (expectedSize != null && expectedSize > 0L) {
                check(temporary.length() == expectedSize) {
                    "Incomplete download: expected $expectedSize bytes, received ${temporary.length()}"
                }
            }

            if (!temporary.renameTo(destination)) {
                temporary.copyTo(destination, overwrite = true)
                temporary.delete()
            }
            return destination
        } finally {
            connection.disconnect()
        }
    }

    temporary.delete()
    error("Too many download redirects")
}

@Suppress("DEPRECATION")
private fun packageNameFromArchive(activity: ComponentActivity, file: File): String? {
    val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        activity.packageManager.getPackageArchiveInfo(file.absolutePath, PackageManager.PackageInfoFlags.of(0L))
    } else {
        activity.packageManager.getPackageArchiveInfo(file.absolutePath, 0)
    }
    return info?.packageName
}

private fun queuePackageInstallerSession(
    activity: ComponentActivity,
    apk: File,
    app: StoreApp
): Boolean = runCatching {
    val packageName = requireNotNull(app.packageName)
    val installer = activity.packageManager.packageInstaller
    val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
        setAppPackageName(packageName)
        setSize(apk.length())
        setOriginatingUri(Uri.parse(app.apkUrl))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            setInstallReason(PackageManager.INSTALL_REASON_USER)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            setPackageSource(PackageInstaller.PACKAGE_SOURCE_STORE)
        }
    }

    val sessionId = installer.createSession(params)
    installer.openSession(sessionId).use { session ->
        session.openWrite("base.apk", 0L, apk.length()).use { output ->
            apk.inputStream().buffered().use { input -> input.copyTo(output, DEFAULT_BUFFER_SIZE) }
            session.fsync(output)
        }

        val callback = Intent(activity, PackageInstallReceiver::class.java).apply {
            action = PackageInstallReceiver.ACTION_INSTALL_STATUS
            putExtra(PackageInstallReceiver.EXTRA_APP_NAME, app.name)
            putExtra(PackageInstallReceiver.EXTRA_PACKAGE_NAME, packageName)
        }
        var flags = PendingIntent.FLAG_UPDATE_CURRENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) flags = flags or PendingIntent.FLAG_MUTABLE
        val statusIntent = PendingIntent.getBroadcast(activity, sessionId, callback, flags)
        session.commit(statusIntent.intentSender)
    }
    true
}.getOrDefault(false)

private fun launchSystemInstaller(activity: ComponentActivity, apk: File): Boolean = runCatching {
    val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.files", apk)
    val intent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
        setDataAndType(uri, APK_MIME)
        clipData = ClipData.newRawUri("GitDroid APK", uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
    }
    activity.startActivity(intent)
    true
}.getOrDefault(false)

private const val APK_MIME = "application/vnd.android.package-archive"
private const val USER_AGENT = "GitDroid/0.2.1 (Android FOSS installer)"
private const val MAX_REDIRECTS = 8
private val REDIRECT_CODES = setOf(
    HttpURLConnection.HTTP_MOVED_PERM,
    HttpURLConnection.HTTP_MOVED_TEMP,
    HttpURLConnection.HTTP_SEE_OTHER,
    307,
    308
)
