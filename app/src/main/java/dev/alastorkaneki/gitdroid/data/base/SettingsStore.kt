package dev.alastorkaneki.gitdroid.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsStore(context: Context) {
    private val preferences = context.getSharedPreferences("gitdroid_settings", Context.MODE_PRIVATE)
    private val mutableSettings = MutableStateFlow(read())
    val settings: StateFlow<AppSettings> = mutableSettings.asStateFlow()

    fun update(transform: (AppSettings) -> AppSettings) {
        val next = transform(mutableSettings.value)
        preferences.edit()
            .putBoolean("fdroid", next.fdroidEnabled)
            .putBoolean("izzy", next.izzyEnabled)
            .putBoolean("github", next.githubEnabled)
            .putBoolean("amoled", next.amoled)
            .putBoolean("dynamic", next.dynamicColor)
            .putBoolean("immersive", next.immersive)
            .putString("github_token", next.githubToken)
            .apply()
        mutableSettings.value = next
    }

    private fun read() = AppSettings(
        fdroidEnabled = preferences.getBoolean("fdroid", true),
        izzyEnabled = preferences.getBoolean("izzy", false),
        githubEnabled = preferences.getBoolean("github", true),
        amoled = preferences.getBoolean("amoled", true),
        dynamicColor = preferences.getBoolean("dynamic", true),
        immersive = preferences.getBoolean("immersive", false),
        githubToken = preferences.getString("github_token", "").orEmpty()
    )
}
