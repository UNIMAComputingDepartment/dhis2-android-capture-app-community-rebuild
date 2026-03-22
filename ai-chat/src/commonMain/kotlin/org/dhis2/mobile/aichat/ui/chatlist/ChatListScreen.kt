package org.dhis2.mobile.aichat.ui.chatlist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.dhis2.mobile.aichat.domain.model.ChatSession
import org.dhis2.mobile.aichat.ui.components.CenteredLoadingState
import org.dhis2.mobile.aichat.ui.components.formatDataTypeLabel
import org.dhis2.mobile.aichat.ui.components.formatToken
import org.hisp.dhis.mobile.ui.designsystem.component.AdditionalInfoItem
import org.hisp.dhis.mobile.ui.designsystem.component.ListCard
import org.hisp.dhis.mobile.ui.designsystem.component.ListCardDescriptionModel
import org.hisp.dhis.mobile.ui.designsystem.component.ListCardTitleModel
import org.hisp.dhis.mobile.ui.designsystem.component.state.rememberAdditionalInfoColumnState
import org.hisp.dhis.mobile.ui.designsystem.component.state.rememberListCardState
import org.hisp.dhis.mobile.ui.designsystem.theme.DHIS2Theme

@Composable
fun ChatListScreen(
    uiState: ChatListUiState,
    onCreateChatClick: () -> Unit,
    onSessionClick: (String) -> Unit,
    onDeleteChatClick: (String) -> Unit,
) {
    DHIS2Theme {
        Scaffold(
            floatingActionButton = {
                FloatingActionButton(onClick = onCreateChatClick) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "Create AI chat")
                }
            },
        ) { padding ->
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(padding),
            ) {
                when (uiState) {
                    ChatListUiState.Loading -> CenteredLoadingState(message = "Loading chats...")
                    ChatListUiState.Empty ->
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(text = "No chats available")
                        }
                    is ChatListUiState.Error -> Text(text = uiState.message, modifier = Modifier.padding(16.dp))
                    is ChatListUiState.Content -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(uiState.sessions, key = { it.id }) { session ->
                                ChatSessionCard(
                                    session = session,
                                    onSessionClick = onSessionClick,
                                    onDeleteChatClick = onDeleteChatClick,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatSessionCard(
    session: ChatSession,
    onSessionClick: (String) -> Unit,
    onDeleteChatClick: (String) -> Unit,
) {
    val chatTitle = session.title?.takeIf { it.isNotBlank() } ?: formatDataTypeLabel(session.selection.dataType)
    val orgUnitLabel = session.selection.orgUnit.displayName.takeIf { it.isNotBlank() } ?: "-"

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ListCard(
            modifier =
                Modifier
                    .weight(1f)
                    .clickable { onSessionClick(session.id) },
            listCardState =
                rememberListCardState(
                    title = ListCardTitleModel(text = chatTitle),
                    description = ListCardDescriptionModel(text = formatToken(session.selection.period)),
                    additionalInfoColumnState =
                        rememberAdditionalInfoColumnState(
                            additionalInfoList =
                                listOf(
                                    AdditionalInfoItem(key = "Org unit", value = orgUnitLabel),
                                    AdditionalInfoItem(key = "Messages", value = session.messageCount.toString()),
                                ),
                            syncProgressItem = AdditionalInfoItem(value = ""),
                        ),
                ),
            onCardClick = { onSessionClick(session.id) },
        )

        IconButton(onClick = { onDeleteChatClick(session.id) }) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "Delete chat",
            )
        }
    }
}
