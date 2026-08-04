package dev.alastorkaneki.gitdroid.data

import android.content.Context
import java.util.Locale

class CatalogRepository(context: Context) {
    private val fdroid = FdroidClient(context.cacheDir)
    private val github = GitHubClient()

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

        return CatalogResult(unique.values.toList(), warnings)
    }

    fun resolveRelease(app: StoreApp, token: String): StoreApp =
        if (app.source == SourceKind.GITHUB && app.apkUrl == null) {
            github.resolveLatestRelease(app, token)
        } else app

    companion object {
        const val FDROID_REPOSITORY = "https://f-droid.org/repo"
        const val IZZY_REPOSITORY = "https://apt.izzysoft.de/fdroid/repo"
    }
}
