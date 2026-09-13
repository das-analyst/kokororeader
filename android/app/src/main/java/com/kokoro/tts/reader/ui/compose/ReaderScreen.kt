package com.kokoro.tts.reader.ui.compose

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kokoro.tts.engine.director.SpeechDirector
import com.kokoro.tts.reader.model.ReaderTheme
import com.kokoro.tts.reader.player.SleepTimerManager
import com.kokoro.tts.ui.theme.LocalReaderColors
import kotlinx.coroutines.launch

@Composable
fun ReaderScreen(
    uiState: ReaderUiState,
    availableVoiceIds: List<String>,
    onBackClick: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onPrevClick: () -> Unit,
    onNextClick: () -> Unit,
    onSentenceClick: (Int) -> Unit,
    onToggleChrome: () -> Unit,
    onOpenModal: (ReaderModalSheet) -> Unit,
    onCloseModal: () -> Unit,
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
    onDeletePronunciationRule: (word: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val readerColors = LocalReaderColors.current
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // Smooth inertial scroll to active sentence on playback progression
    LaunchedEffect(uiState.activeSentenceIndex) {
        if (uiState.sentences.isNotEmpty() && uiState.activeSentenceIndex in uiState.sentences.indices) {
            // Scroll to center / upper-third
            listState.animateScrollToItem(
                index = uiState.activeSentenceIndex,
                scrollOffset = -180
            )
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = readerColors.background,
        topBar = {
            AnimatedVisibility(
                visible = uiState.isControlsVisible,
                enter = fadeIn() + slideInVertically { -it },
                exit = fadeOut() + slideOutVertically { -it }
            ) {
                ReaderTopBar(
                    bookTitle = uiState.bookTitle,
                    chapterTitle = uiState.chapterTitle,
                    sleepTimerBadge = uiState.sleepTimerBadge,
                    readerColors = readerColors,
                    onBackClick = onBackClick,
                    onSleepTimerClick = { onOpenModal(ReaderModalSheet.SLEEP_TIMER) },
                    onAppearanceClick = { onOpenModal(ReaderModalSheet.APPEARANCE) },
                    onTocClick = { onOpenModal(ReaderModalSheet.TABLE_OF_CONTENTS) },
                    onVoiceSpeedClick = { onOpenModal(ReaderModalSheet.VOICE_SPEED) },
                    onPronunciationClick = { onOpenModal(ReaderModalSheet.PRONUNCIATION) }
                )
            }
        },
        bottomBar = {
            AnimatedVisibility(
                visible = uiState.isControlsVisible,
                enter = fadeIn() + slideInVertically { it },
                exit = fadeOut() + slideOutVertically { it }
            ) {
                ReaderBottomBar(
                    sentenceIndex = uiState.activeSentenceIndex,
                    totalSentences = uiState.sentences.size,
                    isBuffering = uiState.isBuffering,
                    isPlaying = uiState.isPlaying,
                    voiceId = uiState.voiceId,
                    speed = uiState.speed,
                    readerColors = readerColors,
                    onPlayPauseClick = onPlayPauseClick,
                    onPrevClick = onPrevClick,
                    onNextClick = onNextClick,
                    onVoiceSpeedBadgeClick = { onOpenModal(ReaderModalSheet.VOICE_SPEED) }
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(readerColors.background)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { onToggleChrome() }
                    )
                }
        ) {
            if (uiState.isLoadingBook) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = Color(0xFF1976D2)
                )
            } else if (uiState.sentences.isEmpty()) {
                Text(
                    text = "No sentences found in this chapter.",
                    color = readerColors.secondaryText,
                    fontSize = 14.sp,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 16.dp)
                ) {
                    itemsIndexed(
                        items = uiState.sentences,
                        key = { _, item -> item.index }
                    ) { index, sentence ->
                        SentenceRow(
                            sentence = sentence,
                            isActive = index == uiState.activeSentenceIndex,
                            fontSizeSp = uiState.fontSizeSp,
                            lineSpacing = uiState.lineSpacing,
                            readerColors = readerColors,
                            onClick = { onSentenceClick(index) }
                        )
                    }
                }
            }
        }
    }

    // Modal Bottom Sheets
    ReaderModalContainer(
        activeModal = uiState.activeModal,
        readerColors = readerColors,
        currentTheme = uiState.theme,
        currentFontSizeSp = uiState.fontSizeSp,
        currentLineSpacing = uiState.lineSpacing,
        currentVoiceId = uiState.voiceId,
        currentSpeed = uiState.speed,
        currentExpressionIntensity = uiState.expressionIntensity,
        currentTemperament = uiState.temperament,
        currentEnableDualTone = uiState.enableDualTone,
        availableVoiceIds = availableVoiceIds,
        chapters = uiState.chapters,
        currentChapterIndex = uiState.currentChapterIndex,
        sleepTimerMode = uiState.sleepTimerMode,
        isWindDownEnabled = uiState.isWindDownEnabled,
        pronunciationRules = uiState.pronunciationRules,
        onDismiss = onCloseModal,
        onThemeSelected = onThemeSelected,
        onFontSizeSelected = onFontSizeSelected,
        onLineSpacingSelected = onLineSpacingSelected,
        onVoiceSelected = onVoiceSelected,
        onSpeedSelected = onSpeedSelected,
        onExpressionIntensitySelected = onExpressionIntensitySelected,
        onTemperamentSelected = onTemperamentSelected,
        onDualToneToggled = onDualToneToggled,
        onChapterSelected = onChapterSelected,
        onSleepTimerSelected = onSleepTimerSelected,
        onWindDownToggled = onWindDownToggled,
        onAddPronunciationRule = onAddPronunciationRule,
        onDeletePronunciationRule = onDeletePronunciationRule
    )
}
