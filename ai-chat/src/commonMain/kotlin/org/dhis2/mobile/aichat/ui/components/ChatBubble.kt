package org.dhis2.mobile.aichat.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.dhis2.mobile.aichat.domain.model.ChatMessage
import org.dhis2.mobile.aichat.domain.model.ChatRole
import org.hisp.dhis.mobile.ui.designsystem.theme.SurfaceColor
import org.hisp.dhis.mobile.ui.designsystem.theme.TextColor

@Composable
fun ChatBubble(
    message: ChatMessage,
    modifier: Modifier = Modifier,
    isStreaming: Boolean = false,
) {
    val containerColor =
        when (message.role) {
            ChatRole.USER -> SurfaceColor.Primary
            ChatRole.ASSISTANT -> SurfaceColor.Container
            ChatRole.SYSTEM -> SurfaceColor.SurfaceBright
        }
    val textColor = if (message.role == ChatRole.USER) TextColor.OnPrimary else TextColor.OnSurface

    Column(
        modifier =
            modifier
                .background(containerColor, RoundedCornerShape(16.dp))
                .padding(12.dp),
    ) {
        if (message.role == ChatRole.ASSISTANT) {
            val parsed = parseThinkingContent(message.content)
            var thinkingExpanded by remember(message.id, isStreaming) { mutableStateOf(isStreaming) }

            if (!parsed.thinking.isNullOrBlank()) {
                TextButton(onClick = { thinkingExpanded = !thinkingExpanded }) {
                    Text(
                        text = if (thinkingExpanded) "Hide thinking" else "Show thinking",
                        color = TextColor.OnSurface,
                    )
                }
                val showThinkingBody = thinkingExpanded || parsed.answer.isBlank()
                if (showThinkingBody) {
                    MarkdownText(
                        markdown = parsed.thinking,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
            }

            if (parsed.answer.isNotBlank()) {
                MarkdownText(markdown = parsed.answer)
            } else if (parsed.thinking.isNullOrBlank()) {
                MarkdownText(markdown = message.content)
            }
        } else {
            Text(text = message.content, color = textColor)
        }
    }
}

private data class ParsedThinkingContent(
    val thinking: String?,
    val answer: String,
)

private fun parseThinkingContent(content: String): ParsedThinkingContent {
    val start = content.indexOf("<think>")
    if (start == -1) {
        return ParsedThinkingContent(thinking = null, answer = content)
    }

    val end = content.indexOf("</think>", startIndex = start + "<think>".length)
    return if (end == -1) {
        val answerBefore = content.substring(0, start).trim()
        val liveThinking = content.substring(start + "<think>".length).trim()
        ParsedThinkingContent(thinking = liveThinking, answer = answerBefore)
    } else {
        val thinking = content.substring(start + "<think>".length, end).trim()
        val answer = (content.substring(0, start) + content.substring(end + "</think>".length)).trim()
        ParsedThinkingContent(thinking = thinking, answer = answer)
    }
}
