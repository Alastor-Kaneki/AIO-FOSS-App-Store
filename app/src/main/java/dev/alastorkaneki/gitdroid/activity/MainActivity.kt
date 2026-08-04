package dev.alastorkaneki.gitdroid

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import dev.alastorkaneki.gitdroid.ui.GitDroidApp
import dev.alastorkaneki.gitdroid.ui.GitDroidTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: GitDroidViewModel = viewModel()
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            val settings by viewModel.settings.collectAsStateWithLifecycle()

            LaunchedEffect(settings.immersive) {
                applyImmersive(settings.immersive)
            }

            GitDroidTheme(amoled = settings.amoled, dynamicColor = settings.dynamicColor) {
                GitDroidApp(
                    activity = this,
                    state = state,
                    settings = settings,
                    onRefresh = viewModel::refresh,
                    onSelectApp = viewModel::selectApp,
                    onCloseDetails = viewModel::closeDetails,
                    onUpdateSettings = viewModel::updateSettings
                )
            }
        }
    }

    private fun applyImmersive(enabled: Boolean) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (enabled) hide(WindowInsetsCompat.Type.systemBars()) else show(WindowInsetsCompat.Type.systemBars())
        }
    }
}
