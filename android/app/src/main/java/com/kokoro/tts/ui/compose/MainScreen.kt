package com.kokoro.tts.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kokoro.tts.reader.model.SavedBook
import com.kokoro.tts.reader.model.SpeakerProfile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    books: List<SavedBook>,
    availableVoiceIds: List<String>,
    selectedVoiceId: String,
    sampleText: String,
    isSpeakingSample: Boolean,
    statusMessage: String,
    onVoiceChange: (String) -> Unit,
    onSampleTextChange: (String) -> Unit,
    onTestVoiceClick: () -> Unit,
    onOpenSettingsClick: () -> Unit,
    onConvertEbookClick: () -> Unit,
    onOpenTxtClick: () -> Unit,
    onReadBookClick: (SavedBook) -> Unit,
    onExportBookClick: (SavedBook) -> Unit,
    onDeleteBookClick: (SavedBook) -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                // App Title & Tagline
                Text(
                    text = "📖 Kokoro Reader",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Pinto Beans Labs • 100% On-device neural AI voice & ebook reader",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            // Convert & Import Card
            item {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text(
                            text = "📥 Convert or Open Ebook",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Convert EPUB or PDF into clean text with formatted chapters, or open an existing plain text file directly.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                        )

                        Button(
                            onClick = onConvertEbookClick,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(imageVector = Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Convert EPUB or PDF to Text", fontSize = 13.sp)
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedButton(
                            onClick = onOpenTxtClick,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Description, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Open Plain Text (.txt) File", fontSize = 13.sp)
                        }
                    }
                }
            }

            // Saved Books Library Header
            item {
                Text(
                    text = "📚 My Saved Books",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                )
            }


            // Books list or Empty State
            if (books.isEmpty()) {
                item {
                    OutlinedCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.MenuBook,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "No books saved yet.",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Tap 'Convert EPUB or PDF' or 'Open Plain Text' above to get started!",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(books, key = { it.id }) { book ->
                    BookItemCard(
                        book = book,
                        onReadClick = { onReadBookClick(book) },
                        onExportClick = { onExportBookClick(book) },
                        onDeleteClick = { onDeleteBookClick(book) }
                    )
                }
            }

            // Divider before TTS Engine preview
            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            }

            // System TTS Voice Preview Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "System TTS Voice Preview",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Voice Dropdown Selector
                        var dropdownExpanded by remember { mutableStateOf(false) }
                        val sortedProfiles = SpeakerProfile.PROFILES.filter { availableVoiceIds.contains(it.id) }
                        val remainingIds = availableVoiceIds.filter { id -> sortedProfiles.none { it.id == id } }
                        val allVoices = sortedProfiles.map { it.id } + remainingIds

                        ExposedDropdownMenuBox(
                            expanded = dropdownExpanded,
                            onExpandedChange = { dropdownExpanded = it },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val activeDisplayName = SpeakerProfile.getDisplayName(selectedVoiceId)
                            OutlinedTextField(
                                value = activeDisplayName,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Voice Profile", fontSize = 12.sp) },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            )

                            ExposedDropdownMenu(
                                expanded = dropdownExpanded,
                                onDismissRequest = { dropdownExpanded = false }
                            ) {
                                allVoices.forEach { voiceId ->
                                    DropdownMenuItem(
                                        text = { Text(SpeakerProfile.getDisplayName(voiceId), fontSize = 13.sp) },
                                        onClick = {
                                            onVoiceChange(voiceId)
                                            dropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Sample text input
                        OutlinedTextField(
                            value = sampleText,
                            onValueChange = onSampleTextChange,
                            label = { Text("Sample Text", fontSize = 12.sp) },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2,
                            maxLines = 4
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Test Voice Button
                        Button(
                            onClick = onTestVoiceClick,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSpeakingSample) Color(0xFFC62828) else MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(
                                imageVector = if (isSpeakingSample) Icons.Default.Stop else Icons.Default.PlayArrow,
                                contentDescription = null
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (isSpeakingSample) "Stop Playback" else "Test Voice")
                        }

                        // Open Accessibility Settings shortcut
                        TextButton(
                            onClick = onOpenSettingsClick,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Open System TTS Settings", fontSize = 12.sp)
                        }

                        // Status message
                        if (statusMessage.isNotBlank()) {
                            Text(
                                text = statusMessage,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}
