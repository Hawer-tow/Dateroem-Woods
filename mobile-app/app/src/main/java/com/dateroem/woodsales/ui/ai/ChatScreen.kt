package com.dateroem.woodsales.ui.ai

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.Canvas
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dateroem.woodsales.ui.theme.DateroemAccent
import com.dateroem.woodsales.ui.theme.DateroemBackground
import com.dateroem.woodsales.ui.theme.DateroemSurface
import com.dateroem.woodsales.ui.theme.DateroemText
import com.dateroem.woodsales.ui.theme.DateroemTextMuted
import com.dateroem.woodsales.ui.theme.WoodButton
import com.dateroem.woodsales.ui.theme.WoodOn
import com.dateroem.woodsales.ui.theme.woodPlank

// "implement the chat bot right below that in the same section" - normal
// message-bubble chat, with a real tappable shortcut button under any
// assistant reply that pointed somewhere specific in the app.
@Composable
fun ChatScreen(viewModel: ChatViewModel, onNavigate: (String) -> Unit) {
    val state by viewModel.uiState.collectAsState()
    val sending by viewModel.sending.collectAsState()
    val sendError by viewModel.sendError.collectAsState()
    val viewingBucket by viewModel.viewingBucket.collectAsState()
    var input by remember { mutableStateOf("") }
    var showHistoryPicker by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    // "you can offer a category on conversation you click it you can see
    // conversation on this date to prevent a pile of conversations" -
    // browsing a past week/month shows only that bucket's messages,
    // read-only; the live view (viewingBucket == null) is unaffected.
    val visibleMessages = remember(state, viewingBucket) {
        val all = (state as? ChatUiState.Loaded)?.messages ?: emptyList()
        val bucket = viewingBucket
        if (bucket == null) all else all.filter { it.dateKey in bucket.dateKeys }
    }

    // "adjust history to be like a feature ... like a clock like feature
    // in it you click it you see your chat" - a proper history picker
    // (icon trigger, card rows, not a plain text dialog), one tap into
    // any past week/month.
    if (showHistoryPicker) {
        val groups = viewModel.dateGroups()
        AlertDialog(
            onDismissRequest = { showHistoryPicker = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ClockIcon(tint = DateroemAccent, modifier = Modifier.size(20.dp))
                    Text("  Conversation history", color = DateroemText)
                }
            },
            text = {
                if (groups.isEmpty()) {
                    Text("Nothing to browse yet.", color = DateroemTextMuted)
                } else {
                    Column {
                        groups.forEach { group ->
                            HistoryBucketRow(group, onClick = {
                                viewModel.viewBucket(group)
                                showHistoryPicker = false
                            })
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showHistoryPicker = false }) { Text("Close") }
            }
        )
    }

    // Standard chat-app behaviour: when the keyboard opens, keep the last
    // message in view instead of letting it hide behind the keyboard.
    val imeBottom = WindowInsets.ime.getBottom(LocalDensity.current)
    LaunchedEffect(imeBottom) {
        if (imeBottom > 0 && visibleMessages.isNotEmpty()) {
            listState.animateScrollToItem(visibleMessages.size - 1)
        }
    }

    Scaffold(containerColor = DateroemBackground) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
        ) {
            // "place that clock on the left side below the stars and the
            // assistant name" - ChatPanel's title row sits right above this
            // one, so the clock lands directly under the "Assistant" title.
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { showHistoryPicker = true }) {
                    ClockIcon(tint = DateroemAccent, modifier = Modifier.size(22.dp))
                }
                if (viewingBucket != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Viewing: ${viewingBucket!!.label}", color = DateroemAccent, fontSize = 13.sp)
                        TextButton(onClick = { viewModel.viewBucket(null) }) { Text("Back to conversation") }
                    }
                }
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when (val current = state) {
                    is ChatUiState.Loading -> {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = DateroemAccent)
                    }
                    is ChatUiState.Error -> {
                        Column(modifier = Modifier.align(Alignment.Center).padding(16.dp)) {
                            Text("Couldn't load the conversation", color = MaterialTheme.colorScheme.error)
                            Text(current.message, color = DateroemTextMuted, modifier = Modifier.padding(top = 4.dp))
                            Button(onClick = { viewModel.loadHistory() }, modifier = Modifier.padding(top = 12.dp)) {
                                Text("Retry")
                            }
                        }
                    }
                    is ChatUiState.Loaded -> {
                        if (visibleMessages.isEmpty()) {
                            Text(
                                if (viewingBucket != null) "Nothing in ${viewingBucket!!.label}."
                                else "Ask anything - e.g. \"where do I send an STK push?\" or \"do you sell cows?\"",
                                color = DateroemTextMuted,
                                modifier = Modifier.align(Alignment.Center).padding(24.dp)
                            )
                        } else {
                            LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
                                items(visibleMessages) { message ->
                                    ChatBubble(message, onNavigate = onNavigate)
                                }
                            }
                            LaunchedEffect(visibleMessages.size, viewingBucket) {
                                if (visibleMessages.isNotEmpty()) {
                                    listState.animateScrollToItem(visibleMessages.size - 1)
                                }
                            }
                        }
                    }
                }
            }

            sendError?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            // Browsing a past week/month is read-only - answering from
            // inside it wouldn't make sense, so the input is hidden rather
            // than just disabled (sendMessage() also snaps back to live).
            if (viewingBucket == null) {
                Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Ask a question...") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = DateroemAccent,
                            unfocusedBorderColor = DateroemTextMuted.copy(alpha = 0.4f)
                        ),
                        maxLines = 4
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            if (input.isNotBlank() && !sending) {
                                viewModel.sendMessage(input.trim())
                                input = ""
                            }
                        },
                        enabled = !sending
                    ) {
                        if (sending) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = DateroemAccent, strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = DateroemAccent)
                        }
                    }
                }
            }
        }
    }
}

// "like a clock like feature" - no icon pack has History bundled offline
// here, so a small literal clock face stands in for it: a circle with two
// hands, same spirit as a browser/app "recent" icon.
@Composable
private fun ClockIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val stroke = size.minDimension * 0.09f
        drawCircle(color = tint, radius = size.minDimension / 2f - stroke, style = Stroke(width = stroke))
        val center = Offset(size.width / 2f, size.height / 2f)
        val r = size.minDimension / 2f - stroke * 1.8f
        // Hour hand pointing to ~2 o'clock, minute hand to 12 - reads as a clock at a glance.
        drawLine(tint, center, Offset(center.x, center.y - r * 0.85f), strokeWidth = stroke * 0.8f, cap = StrokeCap.Round)
        drawLine(tint, center, Offset(center.x + r * 0.55f, center.y - r * 0.35f), strokeWidth = stroke * 0.8f, cap = StrokeCap.Round)
    }
}

@Composable
private fun HistoryBucketRow(group: ChatDateGroup, onClick: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DateroemSurface),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ClockIcon(tint = DateroemTextMuted, modifier = Modifier.size(16.dp))
                Text(group.label, color = DateroemText, modifier = Modifier.padding(start = 8.dp))
            }
            Card(
                colors = CardDefaults.cardColors(containerColor = DateroemAccent.copy(alpha = 0.16f)),
                shape = RoundedCornerShape(20.dp)
            ) {
                Text(
                    "${group.messageCount}",
                    color = DateroemAccent,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        }
    }
}

// Answers come in the same plain-text layout as the website's assistant - a
// heading is a short line ending with ":", items start with "- ", steps with
// "1. " - shown here as real headings and indented list lines instead of one
// block of text. Anything else stays a plain line, so an answer that strays
// from the layout still reads fine.
@Composable
private fun ChatAnswer(text: String, modifier: Modifier = Modifier) {
    val bullet = remember { Regex("^[-•*]\\s+(.+)$") }
    val step = remember { Regex("^(\\d+[.)])\\s+(.+)$") }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        text.lines().map { it.trim().removePrefix("#").trim() }.filter { it.isNotEmpty() }.forEach { line ->
            val item = bullet.find(line)
            val numbered = step.find(line)
            val plain = line.replace("**", "")
            when {
                item != null -> Row {
                    Text("•", color = DateroemAccent, fontWeight = FontWeight.Bold, modifier = Modifier.width(14.dp))
                    Text(labelled(item.groupValues[1]), color = DateroemText)
                }
                numbered != null -> Row {
                    Text(numbered.groupValues[1], color = DateroemAccent, fontWeight = FontWeight.Bold, modifier = Modifier.width(24.dp))
                    Text(labelled(numbered.groupValues[2]), color = DateroemText)
                }
                plain.endsWith(":") && plain.split(Regex("\\s+")).size <= 5 ->
                    Text(plain.dropLast(1), color = DateroemAccent, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
                else -> Text(labelled(line), color = DateroemText)
            }
        }
    }
}

// A short "Label: text" at the start of a line ("Payment: M-Pesa only") shows
// its label in bold, so the eye can scan down the labels.
private fun labelled(text: String): AnnotatedString {
    val plain = text.replace("**", "")
    val match = Regex("^([^:]{1,28}):\\s+(.+)$").find(plain)
    return buildAnnotatedString {
        if (match != null && match.groupValues[1].trim().split(Regex("\\s+")).size <= 3) {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(match.groupValues[1] + ":") }
            append(" " + match.groupValues[2])
        } else {
            append(plain)
        }
    }
}

@Composable
private fun ChatBubble(message: ChatMessage, onNavigate: (String) -> Unit) {
    val isUser = message.role == "user"
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Column(
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            val bubbleShape = RoundedCornerShape(14.dp)
            Card(
                colors = CardDefaults.cardColors(containerColor = if (isUser) Color.Transparent else DateroemSurface),
                shape = bubbleShape,
                modifier = if (isUser) Modifier.woodPlank(bubbleShape) else Modifier
            ) {
                if (isUser) {
                    Text(message.content, color = WoodOn, modifier = Modifier.padding(12.dp))
                } else {
                    ChatAnswer(message.content, modifier = Modifier.padding(12.dp))
                }
            }
            if (!isUser && message.suggestedRoute != null) {
                WoodButton(
                    onClick = { onNavigate(message.suggestedRoute) },
                    modifier = Modifier.padding(top = 6.dp)
                ) {
                    Text(message.suggestedLabel ?: "Go there", color = Color(0xFF141414))
                }
            }
        }
    }
}
