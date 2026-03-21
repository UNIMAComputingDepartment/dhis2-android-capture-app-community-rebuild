package org.dhis2.mobile.aichat.di

import org.dhis2.mobile.aichat.domain.usecase.CreateChatSessionUseCase
import org.dhis2.mobile.aichat.domain.usecase.DeleteChatSessionUseCase
import org.dhis2.mobile.aichat.domain.usecase.GetChatMessagesUseCase
import org.dhis2.mobile.aichat.domain.usecase.GetChatSessionsUseCase
import org.dhis2.mobile.aichat.domain.usecase.SendMessageUseCase
import org.dhis2.mobile.aichat.domain.usecase.SyncChatsUseCase
import org.dhis2.mobile.aichat.ui.chatlist.ChatListViewModel
import org.dhis2.mobile.aichat.ui.conversation.ConversationViewModel
import org.dhis2.mobile.aichat.ui.createchat.CreateChatViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

expect val platformAiChatModule: Module

val aiChatModule =
    module {
        includes(platformAiChatModule)

        factory { GetChatSessionsUseCase(get()) }
        factory { GetChatMessagesUseCase(get()) }
        factory { CreateChatSessionUseCase(get()) }
        factory { SendMessageUseCase(get()) }
        factory { DeleteChatSessionUseCase(get()) }
        factory { SyncChatsUseCase(get()) }

        viewModel { ChatListViewModel(get(), get(), get()) }
        viewModel { CreateChatViewModel(get(), get()) }
        viewModel { (chatId: String) -> ConversationViewModel(chatId, get(), get()) }
    }
