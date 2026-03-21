# AI Chat Module

This module adds an offline-first AI chat experience for data insights.

## Features

- KMP module layout with `commonMain` + `androidMain`
- Compose UI for chat list, create-chat wizard, and conversation
- Retrofit client for the iCHIS AI Insights API
- Room persistence for sessions/messages with sync state flags
- WorkManager sync worker (`SyncAiChatWorker`)
- Koin module wiring (`aiChatModule`)

## Try it

From repository root:

```bash
./gradlew :ai-chat:testDebugUnitTest
./gradlew :ai-chat:ktlintCheck
```

