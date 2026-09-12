package com.kokoro.tts.reader.model

import android.graphics.Color

enum class ReaderTheme(
    val id: String,
    val title: String,
    val backgroundColor: Int,
    val surfaceColor: Int,
    val primaryTextColor: Int,
    val secondaryTextColor: Int,
    val activeHighlightColor: Int,
    val activeTextColor: Int,
    val isDark: Boolean
) {
    CLEAN_PAPER(
        id = "clean_paper",
        title = "Clean Paper",
        backgroundColor = Color.parseColor("#FAF9F6"),
        surfaceColor = Color.parseColor("#FFFFFF"),
        primaryTextColor = Color.parseColor("#1E2022"),
        secondaryTextColor = Color.parseColor("#666666"),
        activeHighlightColor = Color.parseColor("#FFE8A3"),
        activeTextColor = Color.parseColor("#111111"),
        isDark = false
    ),
    WARM_SEPIA(
        id = "warm_sepia",
        title = "Warm Sepia",
        backgroundColor = Color.parseColor("#F4ECD8"),
        surfaceColor = Color.parseColor("#EBDDBF"),
        primaryTextColor = Color.parseColor("#3C2E1F"),
        secondaryTextColor = Color.parseColor("#7D6650"),
        activeHighlightColor = Color.parseColor("#E2CCA0"),
        activeTextColor = Color.parseColor("#261C13"),
        isDark = false
    ),
    OLED_DARK(
        id = "oled_dark",
        title = "OLED Dark",
        backgroundColor = Color.parseColor("#000000"),
        surfaceColor = Color.parseColor("#161616"),
        primaryTextColor = Color.parseColor("#DCDCDC"),
        secondaryTextColor = Color.parseColor("#888888"),
        activeHighlightColor = Color.parseColor("#243048"),
        activeTextColor = Color.parseColor("#FFFFFF"),
        isDark = true
    );

    companion object {
        fun fromId(id: String?): ReaderTheme {
            return values().firstOrNull { it.id.equals(id, ignoreCase = true) } ?: CLEAN_PAPER
        }
    }
}
