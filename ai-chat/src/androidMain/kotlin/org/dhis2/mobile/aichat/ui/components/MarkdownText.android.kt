package org.dhis2.mobile.aichat.ui.components

import android.widget.TextView
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import io.noties.markwon.Markwon
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import androidx.core.graphics.ColorUtils

@Composable
actual fun MarkdownText(
    markdown: String,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val markwon =
        Markwon
            .builder(context)
            .usePlugin(TablePlugin.create(context))
            .usePlugin(StrikethroughPlugin.create())
            .build()

    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val linkColor = ColorUtils.setAlphaComponent(MaterialTheme.colorScheme.primary.toArgb(), 220)

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            TextView(ctx).also { textView ->
                textView.setTextColor(textColor)
                textView.setLinkTextColor(linkColor)
                textView.setBackgroundColor(android.graphics.Color.TRANSPARENT)
                markwon.setMarkdown(textView, markdown)
            }
        },
        update = { textView ->
            textView.setTextColor(textColor)
            textView.setLinkTextColor(linkColor)
            markwon.setMarkdown(textView, markdown)
        },
    )
}
