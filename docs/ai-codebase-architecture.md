# DHIS2 Android Capture App: Architecture, Standards, and Data Flow (AI Onboarding)

This document gives another model a practical mental model of the repository:
- What the major modules are
- How runtime architecture is wired (legacy + migrating KMP)
- Which coding standards matter most
- How data moves through key user flows

## 1) High-level architecture

This is a **hybrid architecture** codebase:
- **Legacy Android stack**: Java/Kotlin, Dagger components, RxJava usage in older layers
- **Migration stack**: Kotlin Multiplatform (KMP), Compose Multiplatform, Koin, Flow/coroutines
- **Data backend contract**: DHIS2 Android SDK (`org.hisp.dhis.android.core.D2`) is the primary source for persistence, sync, and API access

Key startup and DI anchors:
- `app/src/main/java/org/dhis2/App.java`
- `app/src/main/java/org/dhis2/AppComponent.java`
- `app/src/main/java/org/dhis2/di/KoinInitialization.kt`

### Runtime composition pattern

At app startup (`App.onCreate`):
1. Build the Dagger app graph (`AppComponent`)
2. Build server/user scoped components as needed
3. Start Koin via `KoinInitialization.invoke(...)`
4. Initialize security/crash/sync-related services

So the app currently runs **both Dagger and Koin** in parallel while features migrate.

## 2) Module map (from `settings.gradle.kts` + `app/build.gradle.kts`)

Included feature/library modules:
- `:app` - Android application shell and runtime composition root
- `:commons` - shared Android-side utilities, base contracts, platform helpers
- `:commonskmm` - KMP shared domain/common abstractions
- `:login` - KMP login flow (DI, domain use cases, view models)
- `:sync` - sync-related functionality and integration points
- `:community` - community features; includes Tasking
- `:form` - data entry engine, form UI state orchestration, validation pipelines
- `:tracker` - tracker functionality
- `:aggregates` - aggregate/data set related features
- `:stock-usecase` - stock management related use cases
- `:dhis_android_analytics` - analytics features
- `:dhis2_android_maps` - map features
- `:compose-table` - Compose table UI features
- `:ui-components` - reusable UI components
- `:dhis2-mobile-program-rules` - program rules layer

Interpretation for models:
- The repository is not a clean single-stack rewrite; it is a transition platform.
- Always check whether a feature path is in legacy `app/...` or KMP module source sets (`commonMain`, `androidMain`, etc.).

## 3) Source set and layering conventions

From `AGENTS.md` and module layout:
- KMP modules use source-set layering:
  - `commonMain` for shared business logic/contracts
  - `androidMain` for Android-specific implementations
  - `commonTest` and platform test sources for testing
- Preferred architecture is MVVM + repository + use-case separation:
  - **UI** (Compose/Fragments) -> **ViewModel** -> **UseCase/Repository** -> **D2 SDK**

Core boundary rule:
- **Do not bypass the DHIS2 SDK** for networking or persistence.

## 4) Standards and engineering rules

Primary standards source: `AGENTS.md`, plus root Gradle settings.

### Design and UI
- Prefer DHIS2 design system components (`org.hisp.dhis.mobile.ui.designsystem.*`)
- Use `DHIS2Theme` wrapper where available
- Keep composables as stateless/pure as possible

### Data and error handling
- Use DHIS2 SDK for data operations (`D2`)
- Repositories should map SDK/platform errors into domain-level errors (via domain mappers where available)
- Keep domain layer SDK-agnostic where possible

### Concurrency and state
- Prefer coroutines + Flow/StateFlow for new code
- In ViewModels, expose immutable state streams
- Use sealed UI states for deterministic rendering

### Dependency injection
- KMP/new features: Koin modules in common/platform source sets
- Legacy features: Dagger graph and scoped components
- During migration, both DI systems coexist

### Code style and quality gates
- Kotlin conventions + ktlint
- Root `build.gradle.kts` applies ktlint broadly
- `run_tests.sh` executes:
  - `./gradlew ktlintCheck`
  - `./gradlew testDebugUnitTest testDhis2DebugUnitTest`

## 5) End-to-end data flow patterns

## 5.1 App startup + dependency graph bootstrap

Flow:
1. `App.onCreate` creates Dagger graph (`AppComponent`)
2. Server/user components are initialized conditionally
3. Koin modules are started (`KoinInitialization`)
4. Feature modules can resolve dependencies from either graph, depending on migration state

Files:
- `app/src/main/java/org/dhis2/App.java`
- `app/src/main/java/org/dhis2/AppComponent.java`
- `app/src/main/java/org/dhis2/di/KoinInitialization.kt`

## 5.2 Login flow (KMP path)

Flow (simplified):
1. `LoginViewModel` receives `onValidateServer(serverUrl)`
2. Calls `ValidateServer` use case (with network status context)
3. Emits `ServerValidationUiState` updates
4. Routes navigation via `Navigator` to:
   - legacy credential screen (`LegacyLogin`) or
   - OAuth screen (`OauthLogin`)
5. Optional app-link handling feeds back into validation state

Files:
- `login/src/commonMain/kotlin/org/dhis2/mobile/login/main/di/LoginModule.kt`
- `login/src/commonMain/kotlin/org/dhis2/mobile/login/main/ui/viewmodel/LoginViewModel.kt`

## 5.3 Form data-entry flow

Flow (simplified):
1. UI emits `FormIntent`
2. `FormViewModel` converts intent to `RowAction`
3. Action is processed through repository pipeline (`FormRepository`/`FormRepositoryImpl`)
4. Form composition + rule effects + data integrity checks run
5. Save/validation outcomes are emitted as `StoreResult`/dialog actions
6. Completion/sync decisions propagate into worker orchestration

Files:
- `form/src/main/java/org/dhis2/form/ui/FormViewModel.kt`
- `form/src/main/java/org/dhis2/form/data/FormRepositoryImpl.kt`

Related sync orchestration:
- `app/src/main/java/org/dhis2/data/service/workManager/WorkManagerControllerImpl.kt`
- `app/src/main/java/org/dhis2/data/service/SyncDataWorker.java`

## 5.4 Community Tasking flow (important feature data flow)

Core model:
- `TaskingRepository` is a D2-backed repository that reads task config from DataStore and maps TEIs/enrollments/events into `Task` domain objects.

Configuration ingestion:
1. Read namespace `community_redesign` from D2 DataStore
2. Find `tasking` entry
3. Parse JSON into `TaskingConfig`
4. Cache only non-empty config to avoid startup races

Task fetch flow:
1. Resolve active enrollments for task program
2. Resolve TEI UIDs from enrollments
3. Fetch TEIs with attributes ordered by last update
4. Map TEI attributes -> `Task` fields (`teiToTask`)

Task update/create flow:
1. Create TEI (`TrackedEntityInstanceCreateProjection`)
2. Write task attributes using `updateTaskAttrValue(...)`
3. Create enrollment (`EnrollmentCreateProjection`) and set active status/dates

ViewModel orchestration:
1. `TaskingViewModel` loads all tasks on startup
2. Maps domain `Task` -> `TaskingUiModel`
3. Applies multi-filter state (program, org unit, priority, status, due date)
4. Exposes filtered list + progress list via `StateFlow`

Files:
- `community/src/main/java/org/dhis2/community/tasking/repositories/TaskingRepository.kt`
- `community/src/main/java/org/dhis2/community/tasking/models/Task.kt`
- `community/src/main/java/org/dhis2/community/tasking/models/TaskingConfig.kt`
- `community/src/main/java/org/dhis2/community/tasking/ui/TaskingViewModel.kt`

## 6) Integration boundaries and invariants

Treat these as architecture invariants:
- `D2` is the persistence/network/sync boundary for business data
- ViewModels should not directly implement heavy persistence logic
- Repositories own mapping between SDK entities and app models
- Worker layer (`WorkManager`) orchestrates background sync execution
- Rule-engine and validation paths can influence UI composition and completion

## 7) Testing and verification guidance for AI-generated changes

When proposing changes, default to:
1. Unit tests in nearest module test source set (`commonTest` for shared KMP logic, module unit tests for Android-only logic)
2. Robot pattern for Compose instrumented tests (per `AGENTS.md`)
3. No arbitrary delays for async UI tests; rely on idling/tracked coroutine patterns
4. Verify style/lint with ktlint before suggesting merge

## 8) Known migration realities (important for reasoning)

- There is mixed RxJava and coroutine/Flow usage.
- DI is mixed (Dagger + Koin), not fully consolidated.
- Some features (like Tasking) still have signs of iterative refactoring and should be changed conservatively.

When editing, inspect adjacent feature code first to determine whether that area is currently:
- legacy-pattern dominated, or
- KMP/coroutine-first.

## 9) Quick orientation checklist for another model

Before implementing any feature:
1. Identify owning module and source set (`commonMain` vs Android-only)
2. Identify DI path (Dagger or Koin)
3. Confirm data access path goes through D2 SDK/repository
4. Trace user action -> ViewModel -> repository/use case -> worker/sync impact
5. Add/adjust tests in matching test source set
6. Run lint + targeted tests

