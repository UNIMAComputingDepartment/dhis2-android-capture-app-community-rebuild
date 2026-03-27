package org.dhis2.mobile.aichat.ui

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import androidx.fragment.app.FragmentActivity
import org.dhis2.commons.orgunitselector.OUTreeFragment
import org.dhis2.mobile.aichat.ui.export.AssistantResponsesPdfExporter
import org.dhis2.mobile.aichat.ui.navigation.AiChatNavGraph
import org.dhis2.mobile.commons.orgunit.OrgUnitSelectorScope

class AiChatActivity : FragmentActivity() {

    private lateinit var pdfExporter: AssistantResponsesPdfExporter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pdfExporter = AssistantResponsesPdfExporter(this)

        setContent {
            AiChatNavGraph(
                onOpenOrgUnitSelector = ::openOrgUnitSelector,
                onExportConversationPdf = ::exportConversationPdf,
                onExit = ::finish,
            )
        }
    }

    private fun openOrgUnitSelector(
        preselectedOrgUnitId: String?,
        onSelected: (String, String) -> Unit,
    ) {
        val preselected = preselectedOrgUnitId?.let(::listOf) ?: emptyList()
        OUTreeFragment
            .Builder()
            .singleSelection()
            .orgUnitScope(OrgUnitSelectorScope.UserCaptureScope())
            .withPreselectedOrgUnits(preselected)
            .onSelection { selectedOrgUnits ->
                val selected = selectedOrgUnits.firstOrNull() ?: return@onSelection
                onSelected(selected.uid(), selected.displayName() ?: selected.uid())
            }
            .build()
            .show(supportFragmentManager, "AiChatOrgUnitTree")
    }

    @RequiresApi(Build.VERSION_CODES.KITKAT)
    private suspend fun exportConversationPdf(
        chatId: String,
        chatTitle: String?,
        assistantMessages: List<String>,
    ): Result<String> {
        return runCatching {
            val contentUri = pdfExporter.exportAssistantResponses(
                chatId = chatId,
                chatTitle = chatTitle,
                markdownResponses = assistantMessages,
            )

            val shareIntent =
                Intent(Intent.ACTION_SEND)
                    .setType("application/pdf")
                    .putExtra(Intent.EXTRA_STREAM, contentUri)
                    .putExtra(Intent.EXTRA_SUBJECT, chatTitle ?: "AI chat responses")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

            startActivity(Intent.createChooser(shareIntent, "Export responses as PDF"))
            "PDF ready to share"
        }
    }

    companion object {
        fun intent(context: Context): Intent = Intent(context, AiChatActivity::class.java)
    }
}
