package dev.alastorkaneki.gitdroid.shizuku

import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class ShizukuInstallerService : IShizukuInstaller.Stub() {
    override fun install(apk: ParcelFileDescriptor, sizeBytes: Long): String {
        if (sizeBytes <= 0L) return "Invalid APK size"

        val temporaryApk = File(
            "/data/local/tmp",
            "gitdroid-${System.currentTimeMillis()}-${android.os.Process.myPid()}.apk"
        )

        return try {
            val copiedBytes = ParcelFileDescriptor.AutoCloseInputStream(apk).use { input ->
                FileOutputStream(temporaryApk).use { output ->
                    val copied = input.copyTo(output, DEFAULT_BUFFER_SIZE)
                    output.flush()
                    output.fd.sync()
                    copied
                }
            }

            if (copiedBytes != sizeBytes || temporaryApk.length() != sizeBytes) {
                return "APK transfer was incomplete: expected $sizeBytes bytes, received $copiedBytes"
            }

            temporaryApk.setReadable(true, false)
            runPackageManager(temporaryApk)
        } catch (error: Throwable) {
            "Shizuku installer failed: ${error.message ?: error.javaClass.simpleName}"
        } finally {
            runCatching { temporaryApk.delete() }
        }
    }

    private fun runPackageManager(apk: File): String {
        val process = runCatching {
            ProcessBuilder(
                "/system/bin/pm",
                "install",
                "-r",
                "--user",
                "current",
                "-i",
                INSTALLER_PACKAGE,
                apk.absolutePath
            )
                .redirectErrorStream(true)
                .start()
        }.getOrElse { return "Could not start Android package manager: ${it.message}" }

        val output = StringBuilder()
        val reader = thread(name = "gitdroid-pm-output", isDaemon = true) {
            process.inputStream.bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    if (output.isNotEmpty()) output.append('\n')
                    output.append(line)
                }
            }
        }

        val completed = runCatching { process.waitFor(INSTALL_TIMEOUT_MINUTES, TimeUnit.MINUTES) }
            .getOrDefault(false)
        if (!completed) {
            process.destroyForcibly()
            reader.join(2_000)
            return "Android package manager timed out"
        }

        reader.join(2_000)
        val message = output.toString().trim()
        return if (process.exitValue() == 0 && message.contains("Success", ignoreCase = true)) {
            "Success"
        } else {
            "Package manager failed (exit ${process.exitValue()}): ${message.ifBlank { "No details returned" }}"
        }
    }

    private companion object {
        const val INSTALLER_PACKAGE = "dev.alastorkaneki.gitdroid"
        const val INSTALL_TIMEOUT_MINUTES = 5L
    }
}
