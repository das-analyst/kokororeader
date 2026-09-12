package com.kokoro.tts.ui.theme

import androidx.compose.ui.graphics.Color
import com.kokoro.tts.reader.model.ReaderTheme

// Material 3 Brand & Accent Colors
val KokoroPrimaryLight = Color(0xFF1565C0)
val KokoroOnPrimaryLight = Color(0xFFFFFFFF)
val KokoroPrimaryContainerLight = Color(0xFFD6E4FF)
val KokoroOnPrimaryContainerLight = Color(0xFF001B3E)

val KokoroSecondaryLight = Color(0xFF2E7D32)
val KokoroOnSecondaryLight = Color(0xFFFFFFFF)
val KokoroSecondaryContainerLight = Color(0xFFB7F397)
val KokoroOnSecondaryContainerLight = Color(0xFF042100)

val KokoroTertiaryLight = Color(0xFF6750A4)
val KokoroOnTertiaryLight = Color(0xFFFFFFFF)
val KokoroTertiaryContainerLight = Color(0xFFEADDFF)
val KokoroOnTertiaryContainerLight = Color(0xFF21005D)

val KokoroBackgroundLight = Color(0xFFFAF9F6)
val KokoroOnBackgroundLight = Color(0xFF1A1C1E)
val KokoroSurfaceLight = Color(0xFFFFFFFF)
val KokoroOnSurfaceLight = Color(0xFF1A1C1E)
val KokoroSurfaceVariantLight = Color(0xFFE1E2EC)
val KokoroOnSurfaceVariantLight = Color(0xFF44474F)
val KokoroOutlineLight = Color(0xFF74777F)

// Dark Palette
val KokoroPrimaryDark = Color(0xFFA8C7FA)
val KokoroOnPrimaryDark = Color(0xFF003062)
val KokoroPrimaryContainerDark = Color(0xFF00468B)
val KokoroOnPrimaryContainerDark = Color(0xFFD6E4FF)

val KokoroSecondaryDark = Color(0xFF9CD67D)
val KokoroOnSecondaryDark = Color(0xFF123800)
val KokoroSecondaryContainerDark = Color(0xFF205107)
val KokoroOnSecondaryContainerDark = Color(0xFFB7F397)

val KokoroTertiaryDark = Color(0xFFD0BCFF)
val KokoroOnTertiaryDark = Color(0xFF381E72)
val KokoroTertiaryContainerDark = Color(0xFF4F378B)
val KokoroOnTertiaryContainerDark = Color(0xFFEADDFF)

val KokoroBackgroundDark = Color(0xFF121316)
val KokoroOnBackgroundDark = Color(0xFFE2E2E6)
val KokoroSurfaceDark = Color(0xFF1A1C1E)
val KokoroOnSurfaceDark = Color(0xFFE2E2E6)
val KokoroSurfaceVariantDark = Color(0xFF44474F)
val KokoroOnSurfaceVariantDark = Color(0xFFC4C7D0)
val KokoroOutlineDark = Color(0xFF8E9099)

// Specialized Reader Canvas Color Palette
data class ReaderColors(
    val background: Color,
    val surface: Color,
    val primaryText: Color,
    val secondaryText: Color,
    val activeHighlight: Color,
    val activeText: Color,
    val isDark: Boolean
)

fun ReaderTheme.toComposeColors(): ReaderColors {
    return ReaderColors(
        background = Color(this.backgroundColor),
        surface = Color(this.surfaceColor),
        primaryText = Color(this.primaryTextColor),
        secondaryText = Color(this.secondaryTextColor),
        activeHighlight = Color(this.activeHighlightColor),
        activeText = Color(this.activeTextColor),
        isDark = this.isDark
    )
}
