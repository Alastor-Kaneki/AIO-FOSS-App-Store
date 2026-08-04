package dev.alastorkaneki.gitdroid

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.alastorkaneki.gitdroid.ui.GitDroidApp
import dev.alastorkaneki.gitdroid.ui.GitDroidTheme

class MainActivity : ComponentActivity() {
    private lateinit var gitDroidViewModel: GitDroidViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        gitDroidViewModel = ViewModelProvider(this)[GitDroidViewModel::class.java]

        setContent {
            val state by gitDroidViewModel.uiState.collectAsStateWithLifecycle()
            val settings by gitDroidViewModel.settings.collectAsStateWithLifecycle()

            LaunchedEffect(settings.immersive) {
                applyImmersive(settings.immersive)
            }

            GitDroidTheme(amoled = settings.amoled, dynamicColor = settings.dynamicColor) {
                GitDroidApp(
                    activity = this,
                    state = state,
                    settings = settings,
                    onRefresh = gitDroidViewModel::refresh,
                    onRefreshInstalled = gitDroidViewModel::refreshInstalled,
                    onSelectApp = gitDroidViewModel::selectApp,
                    onCloseDetails = gitDroidViewModel::closeDetails,
                    onUpdateSettings = gitDroidViewModel::updateSettings
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::gitDroidViewModel.isInitialized) gitDroidViewModel.refreshInstalled()
    }

    private fun applyImmersive(enabled: Boolean) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (enabled) hide(WindowInsetsCompat.Type.systemBars()) else show(WindowInsetsCompat.Type.systemBars())
        }
    }
}
