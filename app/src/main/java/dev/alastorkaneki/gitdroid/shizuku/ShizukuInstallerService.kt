package dev.alastorkaneki.gitdroid.shizuku

import android.os.ParcelFileDescriptor
import java.io.BufferedReader
import java.io.InputStreamReader

class ShizukuInstallerService : IShizukuInstaller.Stub() {
    override fun install(apk: ParcelFileDescriptor, sizeBytes: Long): String {
        if (sizeBytes <= 0L) return "Invalid APK size"

        val process = runCatching {
            ProcessBuilder(
                "/system/bin/pm",
                "install",
                "-r",
                "-S",
                sizeBytes.toString()
            )
                .redirectErrorStream(true)
                .start()
        }.getOrElse { return "Could not start package manager: ${it.message}" }

        val copyError = runCatching {
            ParcelFileDescriptor.AutoCloseInputStream(apk).use { input ->
                process.outputStream.use { output ->
                    input.copyTo(output, DEFAULT_BUFFER_SIZE)
                    output.flush()
                }
            }
        }.exceptionOrNull()

        if (copyError != null) {
            process.destroy()
            return "Could not stream APK to package manager: ${copyError.message}"
        }

        val output = BufferedReader(InputStreamReader(process.inputStream)).use { it.readText() }.trim()
        val exitCode = runCatching { process.waitFor() }.getOrDefault(-1)
        return if (exitCode == 0 && output.contains("Success", ignoreCase = true)) {
            "Success"
        } else {
            "Package manager failed (exit $exitCode): ${output.ifBlank { "No details returned" }}"
        }
    }
}
