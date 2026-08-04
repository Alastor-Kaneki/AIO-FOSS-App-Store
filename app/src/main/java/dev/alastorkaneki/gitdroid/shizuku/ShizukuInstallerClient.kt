package dev.alastorkaneki.gitdroid.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object ShizukuInstallerClient {
    data class Result(val success: Boolean, val message: String)

    fun statusLabel(): String = runCatching {
        when {
            !Shizuku.pingBinder() -> "Shizuku is not running"
            Shizuku.isPreV11() -> "Shizuku API is too old"
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED -> {
                val mode = if (Shizuku.getUid() == 0) "root" else "ADB"
                "Ready through Shizuku ($mode)"
            }
            else -> "Permission will be requested during installation"
        }
    }.getOrDefault("Shizuku is unavailable")

    suspend fun install(context: Context, apkFile: File): Result {
        if (!apkFile.isFile || apkFile.length() <= 0L) return Result(false, "The downloaded APK is empty")
        if (!runCatching { Shizuku.pingBinder() }.getOrDefault(false)) {
            return Result(false, "Shizuku is not running")
        }
        if (runCatching { Shizuku.isPreV11() }.getOrDefault(true)) {
            return Result(false, "This Shizuku version is too old")
        }

        val permissionGranted = runCatching {
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) true else requestPermission()
        }.getOrDefault(false)
        if (!permissionGranted) return Result(false, "Shizuku permission was not granted")

        val bound = runCatching { bind(context.applicationContext) }
            .getOrElse { return Result(false, "Could not start Shizuku installer: ${it.message}") }

        return try {
            val response = withContext(Dispatchers.IO) {
                ParcelFileDescriptor.open(apkFile, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                    bound.service.install(descriptor, apkFile.length())
                }
            }
            Result(response == "Success", response)
        } catch (error: Throwable) {
            Result(false, error.message ?: error.javaClass.simpleName)
        } finally {
            runCatching { Shizuku.unbindUserService(bound.args, bound.connection, false) }
        }
    }

    private suspend fun requestPermission(): Boolean = suspendCancellableCoroutine { continuation ->
        val listener = object : Shizuku.OnRequestPermissionResultListener {
            override fun onRequestPermissionResult(requestCode: Int, grantResult: Int) {
                if (requestCode != PERMISSION_REQUEST_CODE || !continuation.isActive) return
                Shizuku.removeRequestPermissionResultListener(this)
                continuation.resume(grantResult == PackageManager.PERMISSION_GRANTED)
            }
        }
        Shizuku.addRequestPermissionResultListener(listener)
        continuation.invokeOnCancellation { Shizuku.removeRequestPermissionResultListener(listener) }
        runCatching { Shizuku.requestPermission(PERMISSION_REQUEST_CODE) }
            .onFailure {
                Shizuku.removeRequestPermissionResultListener(listener)
                if (continuation.isActive) continuation.resume(false)
            }
    }

    private suspend fun bind(context: Context): BoundService = suspendCancellableCoroutine { continuation ->
        val args = Shizuku.UserServiceArgs(ComponentName(context, ShizukuInstallerService::class.java))
            .processNameSuffix("gitdroid_installer")
            .tag("gitdroid-installer")
            .version(USER_SERVICE_VERSION)
            .debuggable(false)
            .daemon(true)

        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                if (!continuation.isActive) return
                val service = binder?.let(IShizukuInstaller.Stub::asInterface)
                if (service == null) continuation.resumeWithException(IllegalStateException("Installer binder was null"))
                else continuation.resume(BoundService(args, this, service))
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                if (continuation.isActive) continuation.resumeWithException(IllegalStateException("Installer service disconnected"))
            }
        }

        continuation.invokeOnCancellation {
            runCatching { Shizuku.unbindUserService(args, connection, false) }
        }
        runCatching { Shizuku.bindUserService(args, connection) }
            .onFailure {
                if (continuation.isActive) continuation.resumeWithException(it)
            }
    }

    private data class BoundService(
        val args: Shizuku.UserServiceArgs,
        val connection: ServiceConnection,
        val service: IShizukuInstaller
    )

    private const val PERMISSION_REQUEST_CODE = 9021
    private const val USER_SERVICE_VERSION = 1
}
