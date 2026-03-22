package org.dhis2.mobile.aichat.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import org.dhis2.mobile.aichat.ui.chatlist.ChatListScreen
import org.dhis2.mobile.aichat.ui.chatlist.ChatListViewModel
import org.dhis2.mobile.aichat.ui.conversation.ConversationScreen
import org.dhis2.mobile.aichat.ui.conversation.ConversationViewModel
import org.dhis2.mobile.aichat.ui.createchat.CreateChatScreen
import org.dhis2.mobile.aichat.ui.createchat.CreateChatUiState
import org.dhis2.mobile.aichat.ui.createchat.CreateChatViewModel
import org.dhis2.mobile.aichat.ui.components.formatDataTypeLabel
import org.hisp.dhis.mobile.ui.designsystem.component.TopBar
import org.hisp.dhis.mobile.ui.designsystem.component.TopBarActionIcon
import org.hisp.dhis.mobile.ui.designsystem.theme.DHIS2Theme
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

private object AiChatRoutes {
    const val CHAT_LIST = "chatList"
    const val CREATE_CHAT = "createChat"
    const val CHAT_ID_ARG = "chatId"
    const val CHAT_TITLE_ARG = "chatTitle"
    const val CONVERSATION = "conversation/{$CHAT_ID_ARG}?$CHAT_TITLE_ARG={$CHAT_TITLE_ARG}"

    fun conversation(chatId: String, title: String?): String {
        val encodedTitle = title?.takeIf { it.isNotBlank() }?.let(::encodeRouteArg).orEmpty()
        return "conversation/$chatId?$CHAT_TITLE_ARG=$encodedTitle"
    }
}

private fun encodeRouteArg(value: String): String =
    value
        .replace("%", "%25")
        .replace("/", "%2F")
        .replace("?", "%3F")
        .replace("#", "%23")
        .replace("&", "%26")
        .replace("=", "%3D")
        .replace(" ", "%20")

private fun decodeRouteArg(value: String?): String? {
    if (value.isNullOrBlank()) return null
    return value
        .replace("%20", " ")
        .replace("%3D", "=")
        .replace("%26", "&")
        .replace("%23", "#")
        .replace("%3F", "?")
        .replace("%2F", "/")
        .replace("%25", "%")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiChatNavGraph(
    onOpenOrgUnitSelector: (String?, (String, String) -> Unit) -> Unit = { _, _ -> },
    onExit: () -> Unit = {},
) {
    DHIS2Theme {
        val navController = rememberNavController()
        var onSyncAction by remember { mutableStateOf<(() -> Unit)?>(null) }
        val currentBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = currentBackStackEntry?.destination?.route
        val showBack = currentRoute != AiChatRoutes.CHAT_LIST
        val currentConversationTitle = decodeRouteArg(currentBackStackEntry?.arguments?.getString(AiChatRoutes.CHAT_TITLE_ARG))

        BackHandler(enabled = !showBack) {
            onExit()
        }

        val title =
            when {
                currentRoute == AiChatRoutes.CHAT_LIST -> "AI Chat"
                currentRoute == AiChatRoutes.CREATE_CHAT -> "Create AI Chat"
                currentRoute?.startsWith("conversation/") == true -> currentConversationTitle ?: "Conversation"
                else -> "AI Chat"
            }

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                TopBar(
                    navigationIcon = {
                        if (showBack) {
                            TopBarActionIcon(
                                icon = Icons.AutoMirrored.Filled.ArrowBack,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                contentDescription = "Back",
                                onClick = { navController.popBackStack() },
                            )
                        }
                    },
                    actions = {
                        TopBarActionIcon(
                            icon = Icons.Default.Refresh,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            contentDescription = "Sync",
                            onClick = { onSyncAction?.invoke() },
                        )
                    },
                    title = {
                        Text(
                            text = title,
                            color = MaterialTheme.colorScheme.onPrimary,
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    colors =
                        TopAppBarDefaults.topAppBarColors().copy(
                            containerColor = MaterialTheme.colorScheme.primary,
                            titleContentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                )
            },
        ) { paddingValues ->
            NavHost(
                navController = navController,
                startDestination = AiChatRoutes.CHAT_LIST,
                modifier = Modifier.padding(paddingValues),
            ) {
                composable(AiChatRoutes.CHAT_LIST) {
                    val viewModel: ChatListViewModel = koinViewModel()
                    val state by viewModel.uiState.collectAsState()

                    DisposableEffect(Unit) {
                        onSyncAction = viewModel::refresh
                        onDispose { if (onSyncAction === viewModel::refresh) onSyncAction = null }
                    }

                    ChatListScreen(
                        uiState = state,
                        onCreateChatClick = { navController.navigate(AiChatRoutes.CREATE_CHAT) },
                        onSessionClick = { chatId ->
                            val sessionTitle =
                                (state as? org.dhis2.mobile.aichat.ui.chatlist.ChatListUiState.Content)
                                    ?.sessions
                                    ?.firstOrNull { it.id == chatId }
                                    ?.let { session -> session.title?.takeIf { it.isNotBlank() } ?: formatDataTypeLabel(session.selection.dataType) }
                            navController.navigate(AiChatRoutes.conversation(chatId, sessionTitle))
                        },
                        onDeleteChatClick = viewModel::deleteChat,
                    )
                }

                composable(AiChatRoutes.CREATE_CHAT) {
                    val viewModel: CreateChatViewModel = koinViewModel()
                    val state by viewModel.uiState.collectAsState()

                    CreateChatScreen(
                        uiState = state,
                        onDataTypeSelected = viewModel::onDataTypeSelected,
                        onPeriodSelected = viewModel::onPeriodSelected,
                        onOrgUnitSelected = {
                            val form = state as? CreateChatUiState.Form ?: return@CreateChatScreen
                            if (viewModel.shouldOpenOrgUnitSelector()) {
                                onOpenOrgUnitSelector(form.selectedOrgUnitId, viewModel::onOrgUnitSelected)
                            }
                        },
                        onProgramSelected = viewModel::onProgramSelected,
                        onToggleIncludeChildren = viewModel::onToggleIncludeChildren,
                        onToggleItem = viewModel::onItemToggled,
                        onNextStep = viewModel::nextStep,
                        onPreviousStep = viewModel::previousStep,
                        onCreateChat = viewModel::createChat,
                    )

                    if (state is CreateChatUiState.Created) {
                        val created = state as CreateChatUiState.Created
                        LaunchedEffect(created.chatId) {
                            navController.navigate(AiChatRoutes.conversation(created.chatId, null)) {
                                popUpTo(AiChatRoutes.CHAT_LIST)
                            }
                        }
                    }
                }

                composable(
                    route = AiChatRoutes.CONVERSATION,
                    arguments =
                        listOf(
                            navArgument(AiChatRoutes.CHAT_ID_ARG) { type = NavType.StringType },
                            navArgument(AiChatRoutes.CHAT_TITLE_ARG) {
                                type = NavType.StringType
                                defaultValue = ""
                            },
                        ),
                ) { backStackEntry ->
                    val chatId = backStackEntry.arguments?.getString(AiChatRoutes.CHAT_ID_ARG) ?: return@composable
                    val chatTitle = decodeRouteArg(backStackEntry.arguments?.getString(AiChatRoutes.CHAT_TITLE_ARG))
                    val viewModel: ConversationViewModel = koinViewModel(parameters = { parametersOf(chatId) })
                    val state by viewModel.uiState.collectAsState()

                    DisposableEffect(chatId) {
                        onSyncAction = viewModel::manualSync
                        onDispose { if (onSyncAction === viewModel::manualSync) onSyncAction = null }
                    }

                    ConversationScreen(
                        uiState = state,
                        chatTitle = chatTitle,
                        onInputChanged = viewModel::onInputChanged,
                        onSendClick = viewModel::send,
                    )
                }
            }
        }
    }
}
