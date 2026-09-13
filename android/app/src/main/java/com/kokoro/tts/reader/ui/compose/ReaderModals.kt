package com.kokoro.tts.reader.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import com.kokoro.tts.engine.director.SpeechDirector
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kokoro.tts.reader.model.Chapter
import com.kokoro.tts.reader.model.ReaderTheme
import com.kokoro.tts.reader.model.SpeakerProfile
import com.kokoro.tts.reader.player.SleepTimerManager
import com.kokoro.tts.ui.theme.ReaderColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderModalContainer(
    activeModal: ReaderModalSheet?,
    readerColors: ReaderColors,
    currentTheme: ReaderTheme,
    currentFontSizeSp: Float,
    currentLineSpacing: Float,
    currentVoiceId: String,
    currentSpeed: Float,
    currentExpressionIntensity: Float,
    currentTemperament: SpeechDirector.TemperamentPreset,
    currentEnableDualTone: Boolean,
    availableVoiceIds: List<String>,
    chapters: List<Chapter>,
    currentChapterIndex: Int,
    sleepTimerMode: SleepTimerManager.SleepTimerMode,
    isWindDownEnabled: Boolean,
    pronunciationRules: Map<String, String>,
    onDismiss: () -> Unit,
    onThemeSelected: (ReaderTheme) -> Unit,
    onFontSizeSelected: (Float) -> Unit,
    onLineSpacingSelected: (Float) -> Unit,
    onVoiceSelected: (String) -> Unit,
    onSpeedSelected: (Float) -> Unit,
    onExpressionIntensitySelected: (Float) -> Unit,
    onTemperamentSelected: (SpeechDirector.TemperamentPreset) -> Unit,
    onDualToneToggled: (Boolean) -> Unit,
    onChapterSelected: (Int) -> Unit,
    onSleepTimerSelected: (SleepTimerManager.SleepTimerMode) -> Unit,
    onWindDownToggled: (Boolean) -> Unit,
    onAddPronunciationRule: (word: String, replacement: String) -> Unit,
    onDeletePronunciationRule: (word: String) -> Unit
) {
    if (activeModal == null) return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = readerColors.surface,
        contentColor = readerColors.primaryText
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 8.dp)
        ) {
            when (activeModal) {
                ReaderModalSheet.APPEARANCE -> {
                    AppearanceSheetContent(
                        currentTheme = currentTheme,
                        currentFontSizeSp = currentFontSizeSp,
                        currentLineSpacing = currentLineSpacing,
                        readerColors = readerColors,
                        onThemeSelected = onThemeSelected,
                        onFontSizeSelected = onFontSizeSelected,
                        onLineSpacingSelected = onLineSpacingSelected
                    )
                }

                ReaderModalSheet.VOICE_SPEED -> {
                    VoiceSpeedSheetContent(
                        currentVoiceId = currentVoiceId,
                        currentSpeed = currentSpeed,
                        currentExpressionIntensity = currentExpressionIntensity,
                        currentTemperament = currentTemperament,
                        currentEnableDualTone = currentEnableDualTone,
                        availableVoiceIds = availableVoiceIds,
                        readerColors = readerColors,
                        onVoiceSelected = onVoiceSelected,
                        onSpeedSelected = onSpeedSelected,
                        onExpressionIntensitySelected = onExpressionIntensitySelected,
                        onTemperamentSelected = onTemperamentSelected,
                        onDualToneToggled = onDualToneToggled
                    )
                }

                ReaderModalSheet.SLEEP_TIMER -> {
                    SleepTimerSheetContent(
                        currentMode = sleepTimerMode,
                        isWindDownEnabled = isWindDownEnabled,
                        readerColors = readerColors,
                        onSelectMode = {
                            onSleepTimerSelected(it)
                            onDismiss()
                        },
                        onWindDownToggled = onWindDownToggled
                    )
                }

                ReaderModalSheet.TABLE_OF_CONTENTS -> {
                    TocSheetContent(
                        chapters = chapters,
                        currentChapterIndex = currentChapterIndex,
                        readerColors = readerColors,
                        onChapterClick = {
                            onChapterSelected(it)
                            onDismiss()
                        }
                    )
                }

                ReaderModalSheet.PRONUNCIATION -> {
                    PronunciationSheetContent(
                        rules = pronunciationRules,
                        readerColors = readerColors,
                        onAddRule = onAddPronunciationRule,
                        onDeleteRule = onDeletePronunciationRule
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

// -------------------------------------------------------------------------
// Appearance Modal Sheet
// -------------------------------------------------------------------------
@Composable
private fun AppearanceSheetContent(
    currentTheme: ReaderTheme,
    currentFontSizeSp: Float,
    currentLineSpacing: Float,
    readerColors: ReaderColors,
    onThemeSelected: (ReaderTheme) -> Unit,
    onFontSizeSelected: (Float) -> Unit,
    onLineSpacingSelected: (Float) -> Unit
) {
    Text(
        text = "Reading Appearance",
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        color = readerColors.primaryText
    )

    Spacer(modifier = Modifier.height(16.dp))
    Text(
        text = "THEME PALETTE",
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        color = readerColors.secondaryText
    )
    Spacer(modifier = Modifier.height(8.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ReaderTheme.values().forEach { theme ->
            val isSelected = theme == currentTheme
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(theme.backgroundColor))
                    .border(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = if (isSelected) Color(0xFF1976D2) else Color(0x33000000),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .clickable { onThemeSelected(theme) }
                    .padding(vertical = 12.dp, horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = Color(theme.primaryTextColor),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                }
                Text(
                    text = theme.title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(theme.primaryTextColor)
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(20.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "FONT SIZE",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = readerColors.secondaryText
        )
        Text(
            text = "${currentFontSizeSp.toInt()} sp",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = readerColors.primaryText
        )
    }

    Slider(
        value = currentFontSizeSp,
        onValueChange = onFontSizeSelected,
        valueRange = 14f..28f,
        steps = 6,
        colors = SliderDefaults.colors(
            thumbColor = Color(0xFF1976D2),
            activeTrackColor = Color(0xFF1976D2)
        )
    )

    Spacer(modifier = Modifier.height(14.dp))
    Text(
        text = "LINE SPACING",
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        color = readerColors.secondaryText
    )
    Spacer(modifier = Modifier.height(8.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val spacings = listOf(1.25f to "Compact", 1.45f to "Normal", 1.70f to "Relaxed")
        spacings.forEach { (spacing, label) ->
            val isSelected = Math.abs(currentLineSpacing - spacing) < 0.05f
            FilterChip(
                selected = isSelected,
                onClick = { onLineSpacingSelected(spacing) },
                label = { Text(label, fontSize = 12.sp) },
                modifier = Modifier.weight(1f),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = if (readerColors.isDark) Color(0xFF243048) else Color(0xFFD6E4FF),
                    selectedLabelColor = if (readerColors.isDark) Color.White else Color(0xFF001B3E)
                )
            )
        }
    }
}

// -------------------------------------------------------------------------
// Voice & Speed Modal Sheet
// -------------------------------------------------------------------------
@Composable
private fun VoiceSpeedSheetContent(
    currentVoiceId: String,
    currentSpeed: Float,
    currentExpressionIntensity: Float,
    currentTemperament: SpeechDirector.TemperamentPreset,
    currentEnableDualTone: Boolean,
    availableVoiceIds: List<String>,
    readerColors: ReaderColors,
    onVoiceSelected: (String) -> Unit,
    onSpeedSelected: (Float) -> Unit,
    onExpressionIntensitySelected: (Float) -> Unit,
    onTemperamentSelected: (SpeechDirector.TemperamentPreset) -> Unit,
    onDualToneToggled: (Boolean) -> Unit
) {
    Text(
        text = "Voice & Delivery Performance",
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        color = readerColors.primaryText
    )

    Spacer(modifier = Modifier.height(16.dp))
    Text(
        text = "SPEAKER PROFILE",
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        color = readerColors.secondaryText
    )
    Spacer(modifier = Modifier.height(8.dp))

    val profiles = SpeakerProfile.PROFILES.filter { availableVoiceIds.contains(it.id) }
    val displayList = if (profiles.isNotEmpty()) profiles else availableVoiceIds.map {
        SpeakerProfile(it, it, "Voice", "US", "Standard")
    }

    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        items(displayList) { profile ->
            val isSelected = profile.id == currentVoiceId
            FilterChip(
                selected = isSelected,
                onClick = { onVoiceSelected(profile.id) },
                label = {
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        Text(
                            text = profile.name,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 13.sp
                        )
                        Text(
                            text = profile.tone,
                            fontSize = 10.sp,
                            color = if (isSelected) Color(0xFF1976D2) else readerColors.secondaryText
                        )
                    }
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = if (readerColors.isDark) Color(0xFF243048) else Color(0xFFD6E4FF),
                    selectedLabelColor = if (readerColors.isDark) Color.White else Color(0xFF001B3E)
                )
            )
        }
    }

    Spacer(modifier = Modifier.height(20.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "PLAYBACK SPEED",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = readerColors.secondaryText
        )
        Text(
            text = "${String.format(java.util.Locale.US, "%.2fx", currentSpeed)}",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1976D2)
        )
    }

    Slider(
        value = currentSpeed,
        onValueChange = onSpeedSelected,
        valueRange = 0.5f..2.0f,
        steps = 14,
        colors = SliderDefaults.colors(
            thumbColor = Color(0xFF1976D2),
            activeTrackColor = Color(0xFF1976D2)
        )
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        listOf(0.75f, 1.0f, 1.25f, 1.5f, 1.75f).forEach { preset ->
            FilterChip(
                selected = Math.abs(currentSpeed - preset) < 0.05f,
                onClick = { onSpeedSelected(preset) },
                label = { Text("${preset}x", fontSize = 11.sp) }
            )
        }
    }

    Spacer(modifier = Modifier.height(20.dp))
    HorizontalDivider(color = if (readerColors.isDark) Color(0x22FFFFFF) else Color(0x11000000))
    Spacer(modifier = Modifier.height(16.dp))

    // -------------------------------------------------------------------------
    // Narrator Temperament Presets
    // -------------------------------------------------------------------------
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "NARRATOR TEMPERAMENT",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = readerColors.secondaryText
        )
        Text(
            text = currentTemperament.title,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1976D2)
        )
    }
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = SpeechDirector.getPersonaDescription(currentVoiceId, currentTemperament),
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        color = if (readerColors.isDark) Color(0xFF90CAF9) else Color(0xFF1565C0)
    )
    Spacer(modifier = Modifier.height(8.dp))

    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        items(SpeechDirector.TemperamentPreset.values()) { preset ->
            val isSelected = preset == currentTemperament
            FilterChip(
                selected = isSelected,
                onClick = {
                    onTemperamentSelected(preset)
                },
                label = {
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        Text(
                            text = preset.title,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 13.sp
                        )
                        Text(
                            text = preset.description,
                            fontSize = 10.sp,
                            color = if (isSelected) Color(0xFF1976D2) else readerColors.secondaryText
                        )
                    }
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = if (readerColors.isDark) Color(0xFF243048) else Color(0xFFD6E4FF),
                    selectedLabelColor = if (readerColors.isDark) Color.White else Color(0xFF001B3E)
                )
            )
        }
    }

    Spacer(modifier = Modifier.height(20.dp))

    // -------------------------------------------------------------------------
    // Expression Intensity with Outcome-Focused Cognitive Feedback
    // -------------------------------------------------------------------------
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "EXPRESSION INTENSITY",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = readerColors.secondaryText
        )
        Text(
            text = "${(currentExpressionIntensity * 100).toInt()}%",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1976D2)
        )
    }

    Slider(
        value = currentExpressionIntensity,
        onValueChange = onExpressionIntensitySelected,
        valueRange = 0.0f..1.0f,
        steps = 20,
        colors = SliderDefaults.colors(
            thumbColor = Color(0xFF1976D2),
            activeTrackColor = Color(0xFF1976D2)
        )
    )

    // Dynamic Outcome Feedback Card (framing the cognitive outcome trade-off)
    val (outcomeTitle, outcomeBadge, outcomeBadgeBg, outcomeDesc) = when {
        currentExpressionIntensity < 0.25f -> listOf(
            "Strictly Predictable Cadence",
            "Lowest Fatigue",
            Color(0xFF2E7D32),
            "Steady, drone-resistant pacing with zero sudden tempo jumps. Lowest cognitive strain for multi-hour sessions, but can feel monotonous over time."
        )
        currentExpressionIntensity <= 0.70f -> listOf(
            "Balanced Storyteller",
            "Recommended Focus",
            Color(0xFF1976D2),
            "Subtle prosodic shifts and character nuance without jarring tempo swings. Keeps attention engaged while preventing headphone fatigue."
        )
        else -> listOf(
            "Dynamic Theatrical Drama",
            "Higher Fatigue",
            Color(0xFFE65100),
            "Vivid emotional voice blends and dramatic tempo contrasts (whispers slow down, shouts speed up). Highly engaging, but higher listening fatigue over long sessions."
        )
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (readerColors.isDark) Color(0x18FFFFFF) else Color(0x0A000000))
            .border(
                1.dp,
                if (readerColors.isDark) Color(0x28FFFFFF) else Color(0x18000000),
                RoundedCornerShape(10.dp)
            )
            .padding(12.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = outcomeTitle as String,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = readerColors.primaryText
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background((outcomeBadgeBg as Color).copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = outcomeBadge as String,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = outcomeBadgeBg
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = outcomeDesc as String,
                fontSize = 11.sp,
                color = readerColors.secondaryText,
                lineHeight = 15.sp
            )
        }
    }

    Spacer(modifier = Modifier.height(20.dp))
    HorizontalDivider(color = if (readerColors.isDark) Color(0x22FFFFFF) else Color(0x11000000))
    Spacer(modifier = Modifier.height(16.dp))

    // -------------------------------------------------------------------------
    // Dual-Tone Character Dialogue Toggle
    // -------------------------------------------------------------------------
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                text = "Dual-Tone Dialogue",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = readerColors.primaryText
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Subtly shifts character speech timbre and tempo so quotes stand out clearly from narrative exposition.",
                fontSize = 11.sp,
                color = readerColors.secondaryText,
                lineHeight = 15.sp
            )
        }
        Switch(
            checked = currentEnableDualTone,
            onCheckedChange = onDualToneToggled
        )
    }
}

// -------------------------------------------------------------------------
// Sleep Timer Modal Sheet
// -------------------------------------------------------------------------
@Composable
private fun SleepTimerSheetContent(
    currentMode: SleepTimerManager.SleepTimerMode,
    isWindDownEnabled: Boolean,
    readerColors: ReaderColors,
    onSelectMode: (SleepTimerManager.SleepTimerMode) -> Unit,
    onWindDownToggled: (Boolean) -> Unit
) {
    Text(
        text = "Sleep Timer",
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        color = readerColors.primaryText
    )
    Spacer(modifier = Modifier.height(12.dp))

    SleepTimerManager.SleepTimerMode.values().forEach { mode ->
        val isSelected = mode == currentMode
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable { onSelectMode(mode) }
                .padding(vertical = 12.dp, horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = mode.label,
                fontSize = 15.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = if (isSelected) Color(0xFF1976D2) else readerColors.primaryText
            )
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Active",
                    tint = Color(0xFF1976D2),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(16.dp))
    HorizontalDivider(color = if (readerColors.isDark) Color(0x22FFFFFF) else Color(0x11000000))
    Spacer(modifier = Modifier.height(16.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                text = "Prosody Wind-Down (Sleep Curve)",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = readerColors.primaryText
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Gradually slows tempo, expands pauses, and softens voice volume over the final 10 minutes to help you drift to sleep.",
                fontSize = 11.sp,
                color = readerColors.secondaryText,
                lineHeight = 15.sp
            )
        }
        Switch(
            checked = isWindDownEnabled,
            onCheckedChange = onWindDownToggled
        )
    }
}

// -------------------------------------------------------------------------
// Table of Contents Modal Sheet
// -------------------------------------------------------------------------
@Composable
private fun TocSheetContent(
    chapters: List<Chapter>,
    currentChapterIndex: Int,
    readerColors: ReaderColors,
    onChapterClick: (Int) -> Unit
) {
    Text(
        text = "Table of Contents",
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        color = readerColors.primaryText
    )
    Spacer(modifier = Modifier.height(12.dp))

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .height(360.dp)
    ) {
        items(chapters) { chapter ->
            val isCurrent = chapter.index == currentChapterIndex
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isCurrent) (if (readerColors.isDark) Color(0xFF243048) else Color(0xFFE3F2FD)) else Color.Transparent)
                    .clickable { onChapterClick(chapter.index) }
                    .padding(vertical = 12.dp, horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${chapter.index + 1}. ${chapter.title}",
                    fontSize = 14.sp,
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                    color = if (isCurrent) Color(0xFF1976D2) else readerColors.primaryText,
                    maxLines = 1
                )
            }
        }
    }
}

// -------------------------------------------------------------------------
// Pronunciation Rules Lexicon Modal Sheet
// -------------------------------------------------------------------------
@Composable
private fun PronunciationSheetContent(
    rules: Map<String, String>,
    readerColors: ReaderColors,
    onAddRule: (word: String, replacement: String) -> Unit,
    onDeleteRule: (word: String) -> Unit
) {
    var originalWord by remember { mutableStateOf("") }
    var replacementText by remember { mutableStateOf("") }

    Text(
        text = "Custom Pronunciation Lexicon",
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        color = readerColors.primaryText
    )
    Text(
        text = "Override how specific names, acronyms, or symbols are spoken.",
        fontSize = 12.sp,
        color = readerColors.secondaryText,
        modifier = Modifier.padding(top = 2.dp, bottom = 12.dp)
    )

    // Add new rule inputs
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = originalWord,
            onValueChange = { originalWord = it },
            label = { Text("Word (e.g. Kokoro)", fontSize = 11.sp) },
            modifier = Modifier.weight(1f),
            singleLine = true
        )

        OutlinedTextField(
            value = replacementText,
            onValueChange = { replacementText = it },
            label = { Text("Spoken (e.g. Co-core-oh)", fontSize = 11.sp) },
            modifier = Modifier.weight(1.2f),
            singleLine = true
        )

        IconButton(
            onClick = {
                if (originalWord.isNotBlank() && replacementText.isNotBlank()) {
                    onAddRule(originalWord.trim(), replacementText.trim())
                    originalWord = ""
                    replacementText = ""
                }
            }
        ) {
            Icon(imageVector = Icons.Default.Add, contentDescription = "Add Rule", tint = Color(0xFF1976D2))
        }
    }

    Spacer(modifier = Modifier.height(14.dp))

    // List of active rules
    if (rules.isEmpty()) {
        Text(
            text = "No custom pronunciation rules added yet.",
            fontSize = 13.sp,
            color = readerColors.secondaryText,
            modifier = Modifier.padding(vertical = 12.dp)
        )
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
        ) {
            items(rules.toList()) { (word, replacement) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "$word  ➔  $replacement",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = readerColors.primaryText
                    )
                    IconButton(
                        onClick = { onDeleteRule(word) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete Rule",
                            tint = Color(0xFFC62828),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}
