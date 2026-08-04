package dev.alastorkaneki.gitdroid.ui

import android.app.Activity
import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val GitDroidDark = darkColorScheme(
    primary = Color(0xFFFF4B55),
    secondary = Color(0xFFB86BFF),
    tertiary = Color(0xFF8B5CF6),
    background = Color(0xFF09090B),
    surface = Color(0xFF111116),
    surfaceVariant = Color(0xFF202028)
)

@Composable
fun GitDroidTheme(amoled: Boolean, dynamicColor: Boolean, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val base = if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        dynamicDarkColorScheme(context)
    } else GitDroidDark
    val scheme = if (amoled) {
        base.copy(
            background = Color.Black,
            surface = Color.Black,
            surfaceContainer = Color(0xFF08080A),
            surfaceContainerLow = Color(0xFF050506),
            surfaceContainerHigh = Color(0xFF101014),
            surfaceVariant = Color(0xFF17171D)
        )
    } else base
    MaterialTheme(colorScheme = scheme, content = content)
}
