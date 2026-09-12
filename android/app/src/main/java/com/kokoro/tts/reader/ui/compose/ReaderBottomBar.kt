package com.kokoro.tts.reader.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kokoro.tts.reader.model.SpeakerProfile
import com.kokoro.tts.ui.theme.ReaderColors

@Composable
fun ReaderBottomBar(
    sentenceIndex: Int,
    totalSentences: Int,
    isBuffering: Boolean,
    isPlaying: Boolean,
    voiceId: String,
    speed: Float,
    readerColors: ReaderColors,
    onPlayPauseClick: () -> Unit,
    onPrevClick: () -> Unit,
    onNextClick: () -> Unit,
    onVoiceSpeedBadgeClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .shadow(elevation = 8.dp),
        color = readerColors.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            // Meta row (Progress + Voice/Speed Pill)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val progressText = if (isBuffering) {
                    "⚡ Buffering speech..."
                } else {
                    "Sentence ${sentenceIndex + 1} of ${totalSentences.coerceAtLeast(1)}"
                }

                Text(
                    text = progressText,
                    fontSize = 13.sp,
                    color = readerColors.secondaryText,
                    fontWeight = FontWeight.Medium
                )

                // Voice + Speed Pill
                val profile = SpeakerProfile.findById(voiceId)
                val voiceLabel = profile?.name ?: voiceId
                val speedLabel = String.format("%.2fx", speed)

                Box(
                    modifier = Modifier
                        .background(
                            color = if (readerColors.isDark) Color(0xFF222834) else Color(0xFFE8F0FE),
                            shape = RoundedCornerShape(16.dp)
                        )
                        .clickable { onVoiceSpeedBadgeClick() }
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "$voiceLabel • $speedLabel",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (readerColors.isDark) Color(0xFF8AB4F8) else Color(0xFF1976D2)
                    )
                }
            }

            // Audio Player Controls (Prev, Play/Pause FAB, Next)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onPrevClick,
                    modifier = Modifier.size(52.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "Previous Sentence",
                        tint = readerColors.primaryText,
                        modifier = Modifier.size(32.dp)
                    )
                }

                FloatingActionButton(
                    onClick = onPlayPauseClick,
                    modifier = Modifier
                        .padding(horizontal = 28.dp)
                        .size(60.dp),
                    shape = CircleShape,
                    containerColor = if (readerColors.isDark) Color(0xFF3872E0) else Color(0xFF1976D2),
                    contentColor = Color.White,
                    elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        modifier = Modifier.size(36.dp)
                    )
                }

                IconButton(
                    onClick = onNextClick,
                    modifier = Modifier.size(52.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Next Sentence",
                        tint = readerColors.primaryText,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }
    }
}
