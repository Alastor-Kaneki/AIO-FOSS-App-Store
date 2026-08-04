package dev.alastorkaneki.gitdroid.data

enum class SourceKind(val label: String) {
    FDROID("F-Droid"),
    IZZY("IzzyOnDroid"),
    GITHUB("GitHub")
}

data class StoreApp(
    val id: String,
    val name: String,
    val summary: String,
    val description: String = summary,
    val source: SourceKind,
    val packageName: String? = null,
    val repositoryFullName: String? = null,
    val iconUrl: String? = null,
    val featureGraphicUrl: String? = null,
    val previewUrls: List<String> = emptyList(),
    val versionName: String? = null,
    val categories: List<String> = listOf("Other"),
    val license: String? = null,
    val projectUrl: String? = null,
    val sourceCodeUrl: String? = null,
    val apkUrl: String? = null,
    val apkName: String? = null,
    val apkSize: Long? = null,
    val stars: Long? = null,
    val installedVersionName: String? = null,
    val installedVersionCode: Long? = null
) {
    val isInstalled: Boolean
        get() = installedVersionName != null || installedVersionCode != null
}

data class AppSettings(
    val fdroidEnabled: Boolean = true,
    val izzyEnabled: Boolean = false,
    val githubEnabled: Boolean = true,
    val amoled: Boolean = true,
    val dynamicColor: Boolean = true,
    val immersive: Boolean = false,
    val shizukuInstall: Boolean = true,
    val githubToken: String = ""
)

data class CatalogResult(
    val apps: List<StoreApp>,
    val warnings: List<String> = emptyList()
)

data class UiState(
    val loading: Boolean = false,
    val status: String = "",
    val apps: List<StoreApp> = emptyList(),
    val warnings: List<String> = emptyList(),
    val error: String? = null,
    val selectedApp: StoreApp? = null,
    val resolvingRelease: Boolean = false
)
