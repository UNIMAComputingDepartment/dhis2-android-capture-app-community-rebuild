package org.dhis2.mobile.aichat.ui.export

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.webkit.ValueCallback
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.annotation.RequiresApi
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.dhis2.mobile.aichat.data.AiChatProvider
import java.io.File
import java.io.FileOutputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val PAGE_WIDTH_PT = 595
private const val PAGE_HEIGHT_PT = 842
private const val PAGE_MARGIN_PT = 24f
private const val WEBVIEW_CONTENT_WIDTH_PX = 1080

class AssistantResponsesPdfExporter(
    private val context: Context,
) {

    @RequiresApi(Build.VERSION_CODES.KITKAT)
    suspend fun exportAssistantResponses(
        chatId: String,
        chatTitle: String?,
        markdownResponses: List<String>,
    ): Uri {
        val normalizedTitle = chatTitle?.takeIf { it.isNotBlank() } ?: "AI Chat Responses"
        val markdownBody = markdownResponses.joinToString(separator = "\n\n---\n\n")
        val fullMarkdown = "# $normalizedTitle\n\n$markdownBody"
        val html = markdownToBasicHtml(fullMarkdown)

        val pdfFile = File(context.cacheDir, "ai-chat-$chatId-responses.pdf")
        renderHtmlToPdf(html, pdfFile)

        return FileProvider.getUriForFile(
            context,
            AiChatProvider.authority(context),
            pdfFile,
        )
    }

    @RequiresApi(Build.VERSION_CODES.KITKAT)
    private suspend fun renderHtmlToPdf(
        html: String,
        outputFile: File,
    ) = withContext(Dispatchers.Main.immediate) {
        withTimeout(12_000L) {
            suspendCancellableCoroutine<Unit> { continuation ->
                val webView = WebView(context)
                webView.setBackgroundColor(Color.WHITE)
                webView.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
                webView.settings.javaScriptEnabled = true
                webView.settings.loadsImagesAutomatically = true

                val detachFromHierarchy = attachOffscreen(webView)
                var completed = false

                fun finish(result: Result<Unit>) {
                    if (completed) return
                    completed = true
                    detachFromHierarchy()
                    webView.destroy()
                    if (!continuation.isCompleted) {
                        result
                            .onSuccess { continuation.resume(Unit) }
                            .onFailure { continuation.resumeWithException(it) }
                    }
                }

                continuation.invokeOnCancellation {
                    detachFromHierarchy()
                    webView.destroy()
                }

                webView.webViewClient =
                    object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            webView.postDelayed(
                                {
                                    requestContentHeightPx(webView) { contentHeightPx ->
                                        runCatching {
                                            writeWebViewToPdf(webView, outputFile, contentHeightPx)
                                        }.onSuccess {
                                            finish(Result.success(Unit))
                                        }.onFailure { error ->
                                            finish(Result.failure(error))
                                        }
                                    }
                                },
                                120L,
                            )
                        }
                    }

                webView.loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
            }
        }
    }

    private fun attachOffscreen(webView: WebView): () -> Unit {
        val activity = context as? Activity ?: return {}
        val root = activity.findViewById<ViewGroup>(android.R.id.content) ?: return {}
        val container = FrameLayout(activity).apply {
            // Give the off-screen host a real width so WebView can layout HTML correctly.
            layoutParams = ViewGroup.LayoutParams(WEBVIEW_CONTENT_WIDTH_PX, ViewGroup.LayoutParams.WRAP_CONTENT)
            clipChildren = false
            clipToPadding = false
            isClickable = false
            isFocusable = false
            addView(webView, ViewGroup.LayoutParams(WEBVIEW_CONTENT_WIDTH_PX, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        root.addView(container)
        return {
            runCatching { root.removeView(container) }
        }
    }

    @RequiresApi(Build.VERSION_CODES.KITKAT)
    private fun requestContentHeightPx(
        webView: WebView,
        onReady: (Int) -> Unit,
    ) {
        val callback = ValueCallback<String> { raw ->
            val domHeight = raw?.replace("\"", "")?.toIntOrNull() ?: 0

            webView.measure(
                View.MeasureSpec.makeMeasureSpec(WEBVIEW_CONTENT_WIDTH_PX, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            )

            val scaledContentHeight = (webView.contentHeight * webView.scale).toInt()
            val resolvedHeight = maxOf(webView.measuredHeight, scaledContentHeight, domHeight, 1)
            onReady(resolvedHeight)
        }

        webView.evaluateJavascript(
            "(function(){return Math.max(document.body.scrollHeight, document.documentElement.scrollHeight);})();",
            callback,
        )
    }

    @RequiresApi(Build.VERSION_CODES.KITKAT)
    private fun writeWebViewToPdf(
        webView: WebView,
        outputFile: File,
        contentHeightPx: Int,
    ) {
        val contentWidthPt = PAGE_WIDTH_PT - PAGE_MARGIN_PT * 2
        val contentHeightPt = PAGE_HEIGHT_PT - PAGE_MARGIN_PT * 2
        val scale = contentWidthPt / WEBVIEW_CONTENT_WIDTH_PX
        val pageStepPx = (contentHeightPt / scale).toInt().coerceAtLeast(1)

        val widthSpec =
            android.view.View.MeasureSpec.makeMeasureSpec(
                WEBVIEW_CONTENT_WIDTH_PX,
                android.view.View.MeasureSpec.EXACTLY,
            )
        val heightSpec =
            android.view.View.MeasureSpec.makeMeasureSpec(
                0,
                android.view.View.MeasureSpec.UNSPECIFIED,
            )

        webView.measure(widthSpec, heightSpec)
        val measuredHeight = maxOf(webView.measuredHeight, contentHeightPx).coerceAtLeast(1)
        webView.layout(0, 0, WEBVIEW_CONTENT_WIDTH_PX, measuredHeight)

        val document = PdfDocument()
        try {
            var pageNumber = 1
            var yOffsetPx = 0
            while (yOffsetPx < measuredHeight) {
                val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH_PT, PAGE_HEIGHT_PT, pageNumber).create()
                val page = document.startPage(pageInfo)
                val canvas = page.canvas

                canvas.save()
                canvas.clipRect(
                    PAGE_MARGIN_PT,
                    PAGE_MARGIN_PT,
                    PAGE_MARGIN_PT + contentWidthPt,
                    PAGE_MARGIN_PT + contentHeightPt,
                )
                canvas.translate(PAGE_MARGIN_PT, PAGE_MARGIN_PT)
                canvas.scale(scale, scale)
                canvas.translate(0f, -yOffsetPx.toFloat())
                webView.draw(canvas)
                canvas.restore()

                document.finishPage(page)
                pageNumber++
                yOffsetPx += pageStepPx
            }

            FileOutputStream(outputFile).use { output ->
                document.writeTo(output)
            }
        } finally {
            document.close()
        }
    }

    private fun resolveContentHeightPx(webView: WebView): Int {
        val scaledContentHeight = (webView.contentHeight * webView.scale).toInt()
        return maxOf(webView.measuredHeight, scaledContentHeight, 1)
    }

    private fun markdownToBasicHtml(markdown: String): String {
        val escaped = markdown
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")

        val lines = escaped.split("\n")
        val htmlBody = StringBuilder()

        var index = 0
        while (index < lines.size) {
            val line = lines[index]

            if (
                isPotentialTableHeaderLine(line) &&
                index + 1 < lines.size &&
                isTableSeparatorLine(lines[index + 1])
            ) {
                val headers = parseTableCells(line)
                val rows = mutableListOf<List<String>>()

                index += 2
                while (index < lines.size && isTableRowLine(lines[index])) {
                    rows.add(parseTableCells(lines[index]))
                    index++
                }

                htmlBody.append("<table class=\"md-table\"><thead><tr>")
                headers.forEach { header -> htmlBody.append("<th>$header</th>") }
                htmlBody.append("</tr></thead><tbody>")
                rows.forEach { row ->
                    htmlBody.append("<tr>")
                    val columnCount = maxOf(headers.size, row.size)
                    repeat(columnCount) { col ->
                        htmlBody.append("<td>${row.getOrNull(col).orEmpty()}</td>")
                    }
                    htmlBody.append("</tr>")
                }
                htmlBody.append("</tbody></table>")
                continue
            }

            when {
                line.startsWith("### ") -> htmlBody.append("<h3>${line.removePrefix("### ")}</h3>")
                line.startsWith("## ") -> htmlBody.append("<h2>${line.removePrefix("## ")}</h2>")
                line.startsWith("# ") -> htmlBody.append("<h1>${line.removePrefix("# ")}</h1>")
                line == "---" -> htmlBody.append("<hr/>")
                line.startsWith("- ") -> htmlBody.append("<div>&bull; ${line.removePrefix("- ")}</div>")
                line.isBlank() -> htmlBody.append("<div style=\"height:8px\"></div>")
                else -> htmlBody.append("<div>$line</div>")
            }
            index++
        }

        return """
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0" />
                <style>
                    body { font-family: sans-serif; font-size: 14px; line-height: 1.4; margin: 18px; color: #111; }
                    h1, h2, h3 { margin: 10px 0 6px 0; }
                    .md-table { width: 100%; border-collapse: collapse; margin: 10px 0; table-layout: fixed; }
                    .md-table th, .md-table td { border: 1px solid #444; padding: 6px; text-align: left; vertical-align: top; word-wrap: break-word; }
                    .md-table th { background: #f1f1f1; }
                </style>
            </head>
            <body>
                $htmlBody
            </body>
            </html>
        """.trimIndent()
    }

    private fun isPotentialTableHeaderLine(line: String): Boolean {
        val trimmed = line.trim()
        return trimmed.contains("|") && parseTableCells(trimmed).size >= 2
    }

    private fun isTableSeparatorLine(line: String): Boolean {
        val raw = line.trim().trim('|')
        if (raw.isEmpty()) return false
        return raw.split('|').all { part ->
            part.trim().matches(Regex(":?-{3,}:?"))
        }
    }

    private fun isTableRowLine(line: String): Boolean {
        val trimmed = line.trim()
        return trimmed.contains("|") && !isTableSeparatorLine(trimmed)
    }

    private fun parseTableCells(line: String): List<String> {
        return line
            .trim()
            .trim('|')
            .split('|')
            .map { it.trim() }
    }
}
