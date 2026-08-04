package dev.alastorkaneki.gitdroid

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.alastorkaneki.gitdroid.data.AppSettings
import dev.alastorkaneki.gitdroid.data.CatalogRepository
import dev.alastorkaneki.gitdroid.data.SettingsStore
import dev.alastorkaneki.gitdroid.data.StoreApp
import dev.alastorkaneki.gitdroid.data.UiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GitDroidViewModel(application: Application) : AndroidViewModel(application) {
    private val settingsStore = SettingsStore(application)
    private val catalog = CatalogRepository(application)
    private val mutableUiState = MutableStateFlow(UiState())

    val uiState: StateFlow<UiState> = mutableUiState.asStateFlow()
    val settings: StateFlow<AppSettings> = settingsStore.settings

    init {
        refresh()
    }

    fun refresh() {
        if (mutableUiState.value.loading) return
        viewModelScope.launch {
            mutableUiState.value = mutableUiState.value.copy(loading = true, error = null, status = "Preparing catalogs")
            runCatching {
                withContext(Dispatchers.IO) {
                    catalog.load(settings.value) { message ->
                        mutableUiState.value = mutableUiState.value.copy(status = message)
                    }
                }
            }.onSuccess { result ->
                mutableUiState.value = mutableUiState.value.copy(
                    loading = false,
                    status = "",
                    apps = result.apps,
                    warnings = result.warnings,
                    error = if (result.apps.isEmpty()) "No apps were returned by the enabled sources." else null
                )
            }.onFailure { error ->
                mutableUiState.value = mutableUiState.value.copy(
                    loading = false,
                    status = "",
                    error = error.message ?: error.javaClass.simpleName
                )
            }
        }
    }

    fun refreshInstalled() {
        val current = mutableUiState.value
        if (current.apps.isEmpty()) return
        viewModelScope.launch {
            val refreshed = withContext(Dispatchers.IO) { catalog.refreshInstalledState(current.apps) }
            val selected = current.selectedApp?.let { selectedApp ->
                refreshed.firstOrNull { it.id == selectedApp.id } ?: selectedApp
            }
            mutableUiState.value = mutableUiState.value.copy(apps = refreshed, selectedApp = selected)
        }
    }

    fun selectApp(app: StoreApp) {
        mutableUiState.value = mutableUiState.value.copy(selectedApp = app, resolvingRelease = false)
        if (app.repositoryFullName == null || app.apkUrl != null) return
        viewModelScope.launch {
            mutableUiState.value = mutableUiState.value.copy(resolvingRelease = true)
            runCatching {
                withContext(Dispatchers.IO) { catalog.resolveRelease(app, settings.value.githubToken) }
            }.onSuccess { resolved ->
                mutableUiState.value = mutableUiState.value.copy(selectedApp = resolved, resolvingRelease = false)
            }.onFailure { error ->
                mutableUiState.value = mutableUiState.value.copy(
                    selectedApp = app,
                    resolvingRelease = false,
                    warnings = mutableUiState.value.warnings + "GitHub release: ${error.message ?: error.javaClass.simpleName}"
                )
            }
        }
    }

    fun closeDetails() {
        mutableUiState.value = mutableUiState.value.copy(selectedApp = null, resolvingRelease = false)
    }

    fun updateSettings(refreshCatalog: Boolean = false, transform: (AppSettings) -> AppSettings) {
        settingsStore.update(transform)
        if (refreshCatalog) refresh()
    }
}
