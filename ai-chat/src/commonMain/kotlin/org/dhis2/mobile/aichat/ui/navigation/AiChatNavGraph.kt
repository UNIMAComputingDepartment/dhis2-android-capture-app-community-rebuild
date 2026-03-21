package org.dhis2.mobile.aichat.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.padding
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
import org.hisp.dhis.mobile.ui.designsystem.component.TopBar
import org.hisp.dhis.mobile.ui.designsystem.component.TopBarActionIcon
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

private object AiChatRoutes {
    const val CHAT_LIST = "chatList"
    const val CREATE_CHAT = "createChat"
    const val CONVERSATION = "conversation/{chatId}"

    fun conversation(chatId: String): String = "conversation/$chatId"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiChatNavGraph(
    onOpenOrgUnitSelector: (String?, (String, String) -> Unit) -> Unit = { _, _ -> },
    onExit: () -> Unit = {},
) {
    val navController = rememberNavController()
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route
    val showBack = currentRoute != AiChatRoutes.CHAT_LIST

    val title =
        when {
            currentRoute == AiChatRoutes.CHAT_LIST -> "AI Chat"
            currentRoute == AiChatRoutes.CREATE_CHAT -> "Create AI Chat"
            currentRoute?.startsWith("conversation/") == true -> "Conversation"
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
                actions = {},
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

                ChatListScreen(
                    uiState = state,
                    onCreateChatClick = { navController.navigate(AiChatRoutes.CREATE_CHAT) },
                    onSessionClick = { chatId -> navController.navigate(AiChatRoutes.conversation(chatId)) },
                    onDeleteChatClick = viewModel::deleteChat,
                    onRefresh = viewModel::refresh,
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
                        navController.navigate(AiChatRoutes.conversation(created.chatId)) {
                            popUpTo(AiChatRoutes.CHAT_LIST)
                        }
                    }
                }
            }

            composable(
                route = AiChatRoutes.CONVERSATION,
                arguments = listOf(navArgument("chatId") { type = NavType.StringType }),
            ) { backStackEntry ->
                val chatId = backStackEntry.arguments?.getString("chatId") ?: return@composable
                val viewModel: ConversationViewModel = koinViewModel(parameters = { parametersOf(chatId) })
                val state by viewModel.uiState.collectAsState()

                ConversationScreen(
                    uiState = state,
                    onInputChanged = viewModel::onInputChanged,
                    onSendClick = viewModel::send,
                )
            }
        }
    }
}
