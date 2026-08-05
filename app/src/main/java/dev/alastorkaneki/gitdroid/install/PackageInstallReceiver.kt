package dev.alastorkaneki.gitdroid.install

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.widget.Toast

class PackageInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val appName = intent.getStringExtra(EXTRA_APP_NAME).orEmpty().ifBlank { "App" }
        val packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME).orEmpty()
        val detail = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE).orEmpty()

        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirmation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_INTENT)
                }

                if (confirmation == null) {
                    Toast.makeText(context, "Android did not return an install confirmation screen.", Toast.LENGTH_LONG).show()
                    return
                }

                confirmation.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { context.startActivity(confirmation) }
                    .onFailure {
                        Toast.makeText(
                            context,
                            "Could not open Android's installer: ${it.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
            }

            PackageInstaller.STATUS_SUCCESS -> {
                Toast.makeText(context, "$appName installed successfully.", Toast.LENGTH_LONG).show()
                context.sendBroadcast(
                    Intent(ACTION_INSTALL_FINISHED)
                        .setPackage(context.packageName)
                        .putExtra(EXTRA_PACKAGE_NAME, packageName)
                )
            }

            else -> {
                val reason = detail.ifBlank { statusLabel(status) }
                Toast.makeText(context, "$appName was not installed: $reason", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun statusLabel(status: Int): String = when (status) {
        PackageInstaller.STATUS_FAILURE_ABORTED -> "installation was cancelled"
        PackageInstaller.STATUS_FAILURE_BLOCKED -> "installation was blocked by Android"
        PackageInstaller.STATUS_FAILURE_CONFLICT -> "the installed app has a conflicting signature or version"
        PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> "the APK is not compatible with this device"
        PackageInstaller.STATUS_FAILURE_INVALID -> "the APK is invalid or corrupt"
        PackageInstaller.STATUS_FAILURE_STORAGE -> "there is not enough storage"
        PackageInstaller.STATUS_FAILURE_TIMEOUT -> "the installer timed out"
        else -> "Android package installer returned status $status"
    }

    companion object {
        const val ACTION_INSTALL_STATUS = "dev.alastorkaneki.gitdroid.action.INSTALL_STATUS"
        const val ACTION_INSTALL_FINISHED = "dev.alastorkaneki.gitdroid.action.INSTALL_FINISHED"
        const val EXTRA_APP_NAME = "app_name"
        const val EXTRA_PACKAGE_NAME = "package_name"
    }
}
