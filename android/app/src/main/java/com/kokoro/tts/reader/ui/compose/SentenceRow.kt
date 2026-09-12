package com.kokoro.tts.reader.ui.compose

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kokoro.tts.reader.model.SentenceItem
import com.kokoro.tts.ui.theme.ReaderColors

@Composable
fun SentenceRow(
    sentence: SentenceItem,
    isActive: Boolean,
    fontSizeSp: Float,
    lineSpacing: Float,
    readerColors: ReaderColors,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val highlightColor by animateColorAsState(
        targetValue = if (isActive) readerColors.activeHighlight else Color.Transparent,
        animationSpec = tween(durationMillis = 250),
        label = "sentence_highlight"
    )

    val textColor by animateColorAsState(
        targetValue = if (isActive) readerColors.activeText else readerColors.primaryText,
        animationSpec = tween(durationMillis = 200),
        label = "sentence_text_color"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = highlightColor,
                    shape = RoundedCornerShape(8.dp)
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    onClick()
                }
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(
                text = sentence.text,
                color = textColor,
                fontSize = fontSizeSp.sp,
                lineHeight = (fontSizeSp * lineSpacing).sp,
                fontFamily = FontFamily.Serif,
                fontWeight = if (isActive) FontWeight.Medium else FontWeight.Normal
            )
        }

        if (sentence.isParagraphEnd) {
            Spacer(modifier = Modifier.height(14.dp))
        }
    }
}
