package com.kokoro.tts.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.kokoro.tts.reader.model.ReaderTheme

private val DarkColorScheme = darkColorScheme(
    primary = KokoroPrimaryDark,
    onPrimary = KokoroOnPrimaryDark,
    primaryContainer = KokoroPrimaryContainerDark,
    onPrimaryContainer = KokoroOnPrimaryContainerDark,
    secondary = KokoroSecondaryDark,
    onSecondary = KokoroOnSecondaryDark,
    secondaryContainer = KokoroSecondaryContainerDark,
    onSecondaryContainer = KokoroOnSecondaryContainerDark,
    tertiary = KokoroTertiaryDark,
    onTertiary = KokoroOnTertiaryDark,
    tertiaryContainer = KokoroTertiaryContainerDark,
    onTertiaryContainer = KokoroOnTertiaryContainerDark,
    background = KokoroBackgroundDark,
    onBackground = KokoroOnBackgroundDark,
    surface = KokoroSurfaceDark,
    onSurface = KokoroOnSurfaceDark,
    surfaceVariant = KokoroSurfaceVariantDark,
    onSurfaceVariant = KokoroOnSurfaceVariantDark,
    outline = KokoroOutlineDark
)

private val LightColorScheme = lightColorScheme(
    primary = KokoroPrimaryLight,
    onPrimary = KokoroOnPrimaryLight,
    primaryContainer = KokoroPrimaryContainerLight,
    onPrimaryContainer = KokoroOnPrimaryContainerLight,
    secondary = KokoroSecondaryLight,
    onSecondary = KokoroOnSecondaryLight,
    secondaryContainer = KokoroSecondaryContainerLight,
    onSecondaryContainer = KokoroOnSecondaryContainerLight,
    tertiary = KokoroTertiaryLight,
    onTertiary = KokoroOnTertiaryLight,
    tertiaryContainer = KokoroTertiaryContainerLight,
    onTertiaryContainer = KokoroOnTertiaryContainerLight,
    background = KokoroBackgroundLight,
    onBackground = KokoroOnBackgroundLight,
    surface = KokoroSurfaceLight,
    onSurface = KokoroOnSurfaceLight,
    surfaceVariant = KokoroSurfaceVariantLight,
    onSurfaceVariant = KokoroOnSurfaceVariantLight,
    outline = KokoroOutlineLight
)

val LocalReaderColors = staticCompositionLocalOf {
    ReaderTheme.CLEAN_PAPER.toComposeColors()
}

@Composable
fun KokoroTTSTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    readerTheme: ReaderTheme? = null,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val readerColors = (readerTheme ?: if (darkTheme) ReaderTheme.OLED_DARK else ReaderTheme.CLEAN_PAPER).toComposeColors()

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val isAppearanceDark = readerTheme?.isDark ?: darkTheme
            val statusBarColor = readerTheme?.surfaceColor ?: colorScheme.surface.toArgb()
            window.statusBarColor = statusBarColor
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !isAppearanceDark
        }
    }

    CompositionLocalProvider(
        LocalReaderColors provides readerColors
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = KokoroTypography,
            shapes = KokoroShapes,
            content = content
        )
    }
}
