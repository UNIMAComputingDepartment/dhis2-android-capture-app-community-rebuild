package org.dhis2.mobile.aichat.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.fragment.app.FragmentActivity
import org.dhis2.commons.orgunitselector.OUTreeFragment
import org.dhis2.mobile.aichat.ui.navigation.AiChatNavGraph
import org.dhis2.mobile.commons.orgunit.OrgUnitSelectorScope

class AiChatActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AiChatNavGraph(
                onOpenOrgUnitSelector = ::openOrgUnitSelector,
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

    companion object {
        fun intent(context: Context): Intent = Intent(context, AiChatActivity::class.java)
    }
}
