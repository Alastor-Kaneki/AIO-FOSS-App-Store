package dev.alastorkaneki.gitdroid.data

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import java.util.Locale

class CatalogRepository(private val context: Context) {
    private val fdroid = FdroidClient(context.cacheDir)
    private val github = GitHubClient()
    private val origins = InstallOriginStore(context)

    fun load(settings: AppSettings, progress: (String) -> Unit): CatalogResult {
        val apps = mutableListOf<StoreApp>()
        val warnings = mutableListOf<String>()

        fun collect(label: String, block: () -> List<StoreApp>) {
            progress("Loading $label")
            runCatching(block)
                .onSuccess(apps::addAll)
                .onFailure { warnings += "$label: ${it.message ?: it.javaClass.simpleName}" }
        }

        if (settings.fdroidEnabled) collect("F-Droid") {
            fdroid.load(FDROID_REPOSITORY, SourceKind.FDROID)
        }
        if (settings.izzyEnabled) collect("IzzyOnDroid") {
            fdroid.load(IZZY_REPOSITORY, SourceKind.IZZY)
        }
        if (settings.githubEnabled) collect("GitHub") {
            github.search(settings.githubToken)
        }

        val unique = linkedMapOf<String, StoreApp>()
        apps.sortedWith(
            compareByDescending<StoreApp> { it.stars ?: 0L }
                .thenBy { it.name.lowercase(Locale.ROOT) }
        ).forEach { unique.putIfAbsent(it.id, it) }

        val installed = installedPackages()
        origins.records().forEach { remembered ->
            val packageName = remembered.packageName ?: return@forEach
            if (packageName in installed && unique.values.none { it.id == remembered.id }) {
                unique[remembered.id] = remembered
            }
        }

        return CatalogResult(applyInstalledState(unique.values.toList(), installed), warnings)
    }

    fun resolveRelease(app: StoreApp, token: String): StoreApp =
        if (app.source == SourceKind.GITHUB && app.apkUrl == null) {
            github.resolveLatestRelease(app, token)
        } else app

    fun refreshInstalledState(apps: List<StoreApp>): List<StoreApp> {
        val installed = installedPackages()
        val merged = linkedMapOf<String, StoreApp>()
        apps.forEach { merged[it.id] = it }
        origins.records().forEach { remembered ->
            val packageName = remembered.packageName ?: return@forEach
            if (packageName in installed && remembered.id !in merged) merged[remembered.id] = remembered
        }
        return applyInstalledState(merged.values.toList(), installed)
    }

    private fun applyInstalledState(
        apps: List<StoreApp>,
        installed: Map<String, PackageInfo>
    ): List<StoreApp> {
        val rememberedByPackage = origins.records().mapNotNull { app ->
            app.packageName?.let { it to app }
        }.toMap()

        return apps.map { app ->
            val packageName = app.packageName
            val packageInfo = packageName?.let(installed::get)
            val remembered = packageName?.let(rememberedByPackage::get)
            val sourceMatches = remembered == null || remembered.id == app.id || remembered.source == app.source
            if (packageInfo == null || !sourceMatches) {
                app.copy(installedVersionName = null, installedVersionCode = null)
            } else {
                app.copy(
                    installedVersionName = packageInfo.versionName ?: "Installed",
                    installedVersionCode = PackageInfoCompat.getLongVersionCode(packageInfo)
                )
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun installedPackages(): Map<String, PackageInfo> {
        val manager = context.packageManager
        val packages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            manager.getInstalledPackages(PackageManager.PackageInfoFlags.of(0L))
        } else {
            manager.getInstalledPackages(0)
        }
        return packages.associateBy(PackageInfo::packageName)
    }

    companion object {
        const val FDROID_REPOSITORY = "https://f-droid.org/repo"
        const val IZZY_REPOSITORY = "https://apt.izzysoft.de/fdroid/repo"
    }
}
