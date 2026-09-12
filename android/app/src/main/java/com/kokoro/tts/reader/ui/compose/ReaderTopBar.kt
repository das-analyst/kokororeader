package com.kokoro.tts.reader.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.automirrored.filled.Segment
import androidx.compose.material.icons.filled.Spellcheck
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kokoro.tts.ui.theme.ReaderColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderTopBar(
    bookTitle: String,
    chapterTitle: String,
    sleepTimerBadge: String?,
    readerColors: ReaderColors,
    onBackClick: () -> Unit,
    onSleepTimerClick: () -> Unit,
    onAppearanceClick: () -> Unit,
    onTocClick: () -> Unit,
    onVoiceSpeedClick: () -> Unit,
    onPronunciationClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    TopAppBar(
        modifier = modifier,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = readerColors.surface,
            titleContentColor = readerColors.primaryText,
            navigationIconContentColor = readerColors.primaryText,
            actionIconContentColor = readerColors.primaryText
        ),
        navigationIcon = {
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = readerColors.primaryText
                )
            }
        },
        title = {
            Column {
                Text(
                    text = bookTitle,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = readerColors.primaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (chapterTitle.isNotBlank()) {
                    Text(
                        text = chapterTitle,
                        fontSize = 12.sp,
                        color = readerColors.secondaryText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        },
        actions = {
            // Sleep Timer with optional Badge
            Box(contentAlignment = Alignment.Center) {
                IconButton(onClick = onSleepTimerClick) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = "Sleep Timer",
                        tint = readerColors.primaryText
                    )
                }
                if (sleepTimerBadge != null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .offset(x = (-4).dp, y = (-4).dp)
                            .background(
                                color = Color(0xFF1976D2),
                                shape = RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = sleepTimerBadge,
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Appearance (Aa)
            IconButton(onClick = onAppearanceClick) {
                Icon(
                    imageVector = Icons.Default.FormatSize,
                    contentDescription = "Appearance & Themes",
                    tint = readerColors.primaryText
                )
            }

            // Table of Contents
            IconButton(onClick = onTocClick) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Segment,
                    contentDescription = "Table of Contents",
                    tint = readerColors.primaryText
                )
            }

            // Voice & Speed
            IconButton(onClick = onVoiceSpeedClick) {
                Icon(
                    imageVector = Icons.Default.RecordVoiceOver,
                    contentDescription = "Voice & Speed Settings",
                    tint = readerColors.primaryText
                )
            }

            // Pronunciation Lexicon
            IconButton(onClick = onPronunciationClick) {
                Icon(
                    imageVector = Icons.Default.Spellcheck,
                    contentDescription = "Custom Pronunciation Lexicon",
                    tint = readerColors.primaryText
                )
            }
        }
    )
}
