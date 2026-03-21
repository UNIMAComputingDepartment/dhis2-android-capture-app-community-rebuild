package org.dhis2.mobile.aichat.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
expect fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
)

