# Cross-Program Data Mapping — Design

Status: implemented (phases 1–3); see §9 for what is deferred
Package: `org.dhis2.community.mappers`
Datastore: namespace `community_redesign`, key `dataMappers`

## 1. The problem

Tracker programs frequently record *the same fact* in different places, under different UIDs,
in different value types, and at different points in the TEI's history:

- ANC records haemoglobin as a **data element** on a repeatable visit stage, in `g/L`, as `NUMBER`.
- PNC records the same fact as a **tracked entity attribute**, in `g/dL`, as `TEXT`.
- One program codes sex as option set `{1, 2}`, another as `{MALE, FEMALE}`.
- One stores `dateOfBirth` (`DATE`), another stores `ageInYears` (`INTEGER`).

We need a value captured in program A to appear in the TEI's program B enrollment, automatically.

### Why the naive approach produces wrong data

The existing `AttributeMapping` (`WorkflowConfig.kt:21`) is `sourceAttribute -> targetAttribute` with
a `defaultValue`. It is applied by blind string copy in `WorkflowRepository.kt:139`. Every failure
mode below is silently possible today, and each one writes a *plausible but wrong* value — the worst
kind of bug, because nobody notices:

| # | Failure mode | Wrong-data outcome |
|---|---|---|
| F1 | Type mismatch coerced by string copy | `"12.5"` written into an `INTEGER` field; rejected at sync, or truncated |
| F2 | Option code copied between different option sets | `"1"` means *Male* in A and *Positive* in B |
| F3 | Unit mismatch | `120 g/L` written as `120 g/dL` — a 10× clinical error |
| F4 | Wrong event chosen | Value from the *last* visit written where the *booking* visit was meant |
| F5 | Latest event has a blank value | Blank overwrites a good existing value |
| F6 | Lossy derivation run backwards | `ageInYears = 34` back-derived into a fabricated `dateOfBirth` |
| F7 | Re-run clobbers a human correction | Clinician fixes the target; next save silently overwrites it again |
| F8 | Two mappings target the same field | Last-writer-wins, non-deterministic |
| F9 | Shared attribute UID across programs | "Copy" is a no-op or an unintended global TEI mutation |
| F10 | Target event does not exist yet | Value silently dropped |

The design below is organised around making each of these **structurally impossible or loudly
reported**, never silently wrong.

### Design principles

1. **Config declares meaning; the engine derives mechanism.** Authors state *what a field means*
   (its unit, its option coding, its canonical concept). They do not hand-write conversion code.
2. **Metadata is the source of truth for types.** Value types and option sets are read from the SDK
   at runtime, never trusted from config. Config that disagrees with metadata is a validation error.
3. **No silent coercion.** A conversion either succeeds definitively or the mapping is skipped and
   reported. There is no "best effort" path.
4. **Planning is pure.** Resolving what *would* be written has no side effects, so dry-run,
   unit tests, and real execution share one code path.
5. **Writes are conservative by default.** Never overwrite, never write blank, never clobber a
   human edit — unless explicitly configured otherwise.

## 2. Core idea: two authoring surfaces, one execution core

The elegant part of this design is refusing to model mappings pairwise.

Pairwise mapping is O(n²): with 6 programs sharing haemoglobin you write 30 directed mappings, and
each one is an independent chance to get F1–F3 wrong. Instead we introduce a **semantic concept**
layer:

```
                 ┌──────────────┐
   ANC binding ──┤              ├── PNC binding
   HIV binding ──┤  "haemoglobin"  ├── U5 binding      ← declare once per program: O(n)
   OPD binding ──┤   canonical   ├── CTC binding
                 └──────────────┘
```

A **concept** has a *canonical* representation (type, unit, code vocabulary). A **binding** says
where that concept lives in one program and how that program encodes it. A transfer from any program
to any other is then *derived*: `decode(source binding) -> canonical -> encode(target binding)`.

- Adding a 7th program is one binding, not 6 mappings.
- Correctness is checked once per binding against the canonical, not once per pair.
- F3 (units) and F2 (option codes) collapse into a single per-binding declaration.

A concept pair compiles to a single internal `ResolvedMapping`, which is the only thing the engine
executes:

```
ConceptBinding pair ─► ResolvedMapping(from: ValueAddress, to: ValueAddress,
                                       pipeline: List<Transform>, policy: WritePolicy)
```

> **Removed.** An `explicit` pairwise escape hatch existed alongside concepts, for pairs that did not
> deserve one. It was cut: it synthesised a canonical from the source field's own type and ran the
> same decode/encode, so it *was* a concept with the declaration left implicit — but with no
> option-code translation, no configurable conflict policy (it was hardwired to `SKIP_IF_PRESENT`),
> and a second thing to learn. `overrides`, a third place to set a conflict policy after settings and
> the binding, went with it. Both are rejected with a migration message if found in a stored config.

## 3. Data model

### 3.1 ValueAddress — where a value lives

```kotlin
sealed interface ValueAddress {
    data class Attribute(val uid: String) : ValueAddress
    data class DataElement(
        val programUid: String,
        val stageUid: String,
        val uid: String,
        val event: EventSelector,
    ) : ValueAddress
    data class EnrollmentProperty(val programUid: String, val property: EnrollmentField) : ValueAddress
    data class EventProperty(
        val programUid: String, val stageUid: String,
        val property: EventField, val event: EventSelector,
    ) : ValueAddress
    data class Constant(val value: String) : ValueAddress   // targets: never; sources: defaults
}
```

`Attribute` is deliberately **not** program-scoped, because in the DHIS2 SDK attribute values are
stored per-TEI, not per-enrollment. This is F9: if program A and program B use the *same* attribute
UID, "copying" is a no-op and any transform applied to it mutates the value **both** programs see.
Validation rejects a mapping whose source and target `Attribute` UIDs are equal, and warns when a
target attribute is also present in the source program.

### 3.2 EventSelector — *which* event/incident

This is F4/F5, the requirement's "the data might be of different events/incidents". Selection is
three independent questions, each configurable:

```kotlin
data class EventSelector(
    val enrollment: EnrollmentScope = MOST_RECENT,  // MOST_RECENT | ALL | ACTIVE | TRIGGERING
    val where: List<EventCondition> = emptyList(),  // filter before ordering
    val select: Pick = LATEST_WITH_VALUE,           // ordering + pick
)

enum class Pick {
    TRIGGERING,        // the event that fired this run — no ambiguity at all
    LATEST,            // newest by eventDate, then lastUpdated
    LATEST_WITH_VALUE, // newest event that actually holds a non-blank value  ← default
    FIRST,
    FIRST_WITH_VALUE,
    NTH,               // repeatable stages: `index`, 0-based
}
```

`LATEST_WITH_VALUE` is the default rather than `LATEST` specifically to defuse F5: on a repeatable
stage the most recent event is very often a partially-filled one, and taking its blank value would
otherwise erase good data downstream.

`where` lets an author say "the visit where `visitType == ANC1`" rather than relying on ordinal
position, which is the robust way to express "a specific incident":

```json
"event": { "select": "FIRST", "where": [{ "dataElement": "VISITTYPE", "op": "equals", "value": "ANC1" }] }
```

Ties (same `eventDate`) are broken by `lastUpdated`, then `uid`, so selection is **total and
deterministic** — never dependent on SDK row order.

### 3.3 Concept and Binding

```kotlin
data class Concept(
    val id: String,
    val label: String?,
    val canonical: Canonical,
    val bindings: List<Binding>,
)

data class Canonical(
    val type: CanonicalType,        // TEXT | NUMBER | INTEGER | BOOLEAN | DATE | DATETIME | OPTION
    val unit: String? = null,       // NUMBER only, e.g. "g/dL"
    val codes: List<String>? = null // OPTION only: the canonical vocabulary
)

data class Binding(
    val programUid: String,
    val at: ValueAddress,
    val unit: String? = null,                   // this program's unit; engine derives the scale
    val options: Map<String, String>? = null,   // this program's code -> canonical code
    val derive: Derivation? = null,             // e.g. AGE_FROM for a DOB-vs-age pair
    val direction: Direction = BOTH,            // BOTH | READ_ONLY | WRITE_ONLY
    val onConflict: WritePolicy? = null,        // per-binding override
)
```

`direction` is how F6 is made impossible. An `ageInYears` binding against a canonical `DATE`
date-of-birth is inherently lossy in one direction: you can compute age from a birth date, but
recovering a birth date from "34" fabricates up to 364 days of precision. Any binding carrying a
lossy `derive` **defaults to `WRITE_ONLY`**, so it can receive but never be a source. Overriding that
requires the author to type `"direction": "BOTH"` — a deliberate act, not an oversight.

### 3.4 Transform pipeline

Transforms are pure `(String, TransformContext) -> Result<String?>` functions, composed in order.
Most are *derived* by the engine from the binding declaration rather than written by hand:

| Transform | Derived from | Guards |
|---|---|---|
| `Trim` | always | — |
| `ScaleUnit(factor, offset)` | `binding.unit` vs `canonical.unit` | unknown unit pair → validation error, never assumed 1.0 |
| `TranslateOptions(map, unmatched)` | `binding.options` | unmapped code → `FAIL` by default |
| `ToNumber` / `ToInteger` | value type gap | strict parse; non-numeric → `Failed`, not `0` |
| `Round(decimals, mode)` | `INTEGER`/precision target | lossy round → `Failed` unless `allowLossy` |
| `AgeFrom(unit, asOf)` | `binding.derive` | `asOf` is an address (`TODAY`, event date, …) |
| `FormatDate(pattern)` | `DATETIME` → `DATE` | — |
| `Truncate(len)` | `LONG_TEXT` → `TEXT` | opt-in; otherwise over-length → `Failed` |
| `BooleanAs(trueCode, falseCode)` | `BOOLEAN`/`TRUE_ONLY` gap | — |

Explicit `transform: [...]` in config appends to the derived pipeline for cases the engine cannot
infer. A transform returning `null` means "no value" — a clean skip, distinct from a failure.

`ScaleUnit` deserves emphasis: the engine will **not** guess. If a binding says `g/L` and the
canonical says `g/dL`, a small unit registry (mass, length, volume, time, concentration) supplies
`0.1`. If either unit is unknown to the registry, config validation **fails** and the author must
supply `scaleToCanonical` explicitly. Assuming 1.0 for an unrecognised unit is precisely how F3
happens, so it is not an available behaviour.

### 3.5 WritePolicy

```kotlin
enum class WritePolicy {
    SKIP_IF_PRESENT,            // default
    OVERWRITE,
    OVERWRITE_IF_SOURCE_NEWER,  // compares source lastUpdated vs recorded write time
    FAIL_ON_CONFLICT,           // write nothing, surface the conflict
}
```

Invariants that hold regardless of policy:

- A blank/null resolved value is **never** written (no policy can erase data). Writing blanks
  requires an explicit `"writeBlanks": true` on the mapping.
- A write whose value equals the current target value is a no-op (keeps `lastUpdated` stable and
  avoids pointless sync churn).
- `OVERWRITE` still respects the provenance check below.

### 3.6 Provenance ledger — idempotency (F7)

Mapping runs on every enrollment save and every event save. Without memory, a re-run overwrites a
clinician's manual correction in the target program — silently reverting a deliberate human fix. The
ledger prevents this:

```kotlin
data class LedgerEntry(
    val mappingId: String,
    val targetKey: String,     // stable hash of the resolved target address + tei/enrollment
    val writtenValue: String,  // what we wrote
    val sourceFingerprint: String,
    val writtenAt: Instant,
)
```

Before any write the engine compares the current target value against `writtenValue`:

- **equal** → we still own this field; policy applies normally.
- **different** → a human (or another process) edited it. Treated as `SKIP_IF_PRESENT` and reported
  as `Skipped(HumanEdited)`, regardless of the configured policy. Only `OVERWRITE` combined with
  explicit `"respectManualEdits": false` bypasses this.

The ledger is local-only (a small Room table in the community module, keyed by `targetKey`), never
synced — it describes *this device's* write history, which is exactly the scope of the question.

## 4. Config schema

Single datastore entry, namespace `community_redesign`, key `dataMappers`.

```json
{
  "version": 1,
  "settings": {
    "defaultOnConflict": "SKIP_IF_PRESENT",
    "targetEventPolicy": "REQUIRE_EXISTING"
  },

  "concepts": [
    {
      "id": "haemoglobin",
      "label": "Haemoglobin",
      "canonical": { "type": "NUMBER", "unit": "g/dL" },
      "bindings": [
        {
          "programUid": "ANCPROG0001",
          "at": {
            "kind": "DATA_ELEMENT",
            "stageUid": "ANCVISITSTG",
            "uid": "HBDATAELEM1",
            "event": { "select": "LATEST_WITH_VALUE" }
          },
          "unit": "g/L"
        },
        {
          "programUid": "PNCPROG0001",
          "at": { "kind": "ATTRIBUTE", "uid": "PNCHBATTR01" },
          "unit": "g/dL"
        }
      ]
    },

    {
      "id": "hiv_status",
      "canonical": { "type": "OPTION", "codes": ["POSITIVE", "NEGATIVE", "UNKNOWN"] },
      "bindings": [
        {
          "programUid": "ANCPROG0001",
          "at": {
            "kind": "DATA_ELEMENT",
            "stageUid": "ANCVISITSTG",
            "uid": "HIVDATAELE1",
            "event": { "select": "FIRST", "where": [
              { "dataElement": "VISITTYPEDE", "op": "equals", "value": "ANC1" }
            ]}
          },
          "options": { "1": "POSITIVE", "2": "NEGATIVE", "9": "UNKNOWN" }
        },
        {
          "programUid": "PNCPROG0001",
          "at": { "kind": "ATTRIBUTE", "uid": "PNCHIVATTR1" },
          "options": { "POS": "POSITIVE", "NEG": "NEGATIVE", "UNK": "UNKNOWN" }
        }
      ]
    },

    {
      "id": "date_of_birth",
      "canonical": { "type": "DATE" },
      "bindings": [
        { "programUid": "HHPROG00001", "at": { "kind": "ATTRIBUTE", "uid": "DOBATTRIB01" } },
        {
          "programUid": "U5PROG00001",
          "at": { "kind": "ATTRIBUTE", "uid": "AGEYRSATTR1" },
          "derive": { "op": "AGE_FROM", "unit": "YEARS", "asOf": "TODAY" }
        }
      ]
    }
  ],

  "transfers": [
    {
      "id": "anc-to-pnc",
      "from": { "programUid": "ANCPROG0001" },
      "to":   { "programUid": "PNCPROG0001" },
      "when": ["TARGET_ENROLLMENT_CREATED", "TARGET_ENROLLMENT_FORM_OPEN", "SOURCE_EVENT_SAVED"],
      "concepts": ["haemoglobin", "hiv_status", "date_of_birth"]
    }
  ]
}
```

Note the `derive` on the U5 age binding: it is lossy, so it defaults to `WRITE_ONLY`. Household DOB
flows into U5 age automatically; U5 age can never fabricate a household DOB.

The `AGE_FROM` `asOf` accepts `TODAY` or a `ValueAddress` (e.g. the target event's date), because
"age at enrolment" and "age today" are different facts and conflating them is its own quiet error.

### Gson note

Per the convention documented at `AttributeFilterConfig.kt:14`, Gson deserialises via reflection and
bypasses Kotlin's constructor, so **every field in the config DTOs is nullable with no reliance on
Kotlin defaults**. DTOs are parsed leniently, then mapped into the strict domain model above by
`ConfigValidator`. That two-stage parse is deliberate: a malformed entry becomes a validation error
attributable to one concept, not a `null` that detonates three layers deeper.

### Schema versioning

The top-level `"version"` field governs forward-compatibility. The engine only processes versions
it knows about:

- If `version` is absent or equals the engine's supported version: proceed normally.
- If `version` is *higher* than the engine's supported version: the engine falls back to "no
  mappings" and logs a clear error — "Config version N requires app version ≥ X; mapping
  disabled until the app is updated." This prevents a newer config from being silently misread
  by an older engine. It does not crash the app.
- If `version` is *lower*: the engine applies any registered migration steps to bring the DTO
  up to the current schema before validation. Migration steps live in `config/ConfigMigrations.kt`
  and are additive only — they never remove fields, only fill in new ones with safe defaults.

Version increments are required whenever a field is renamed, a new required field is added, or
the semantics of an existing field change. Adding a new optional field with a documented default
does not require a version bump.

## 5. Execution pipeline

```
        config (datastore)
              │
              ▼
     ┌─────────────────┐   metadata (SDK value types, option sets)
     │ ConfigValidator │◄──────────────────────────────────────────
     └────────┬────────┘
              │  ValidatedConfig  |  List<ConfigError>
              ▼
     ┌─────────────────┐
     │  MappingPlanner │  derive ResolvedMappings for (trigger, tei, target program)
     └────────┬────────┘
              ▼
     ┌─────────────────┐   read-only: resolve addresses, run transforms, evaluate policy
     │   plan(): Plan  │   ── PURE, no writes ──────────────────────
     └────────┬────────┘
              │  MappingPlan(entries: List<PlannedWrite | Skipped | Failed>)
              ├──────────────► dry-run / preview UI / unit tests
              ▼
     ┌─────────────────┐
     │  apply(plan)    │  the only component that writes; also updates the ledger
     └────────┬────────┘
              ▼
        MappingReport
```

```kotlin
class DataMappingEngine(
    private val config: DataMapperConfigRepository,
    private val resolver: ValueResolver,
    private val writer: ValueWriter,
    private val ledger: MappingLedger,
) {
    fun plan(trigger: Trigger, teiUid: String, targetProgramUid: String, context: TriggerContext): MappingPlan
    fun apply(plan: MappingPlan): MappingReport
}
```

Splitting `plan` from `apply` is what makes this testable and trustworthy: the entire correctness
surface — address resolution, event selection, type conversion, unit scaling, option translation,
conflict evaluation — lives in the pure half and can be unit-tested with no database, matching the
existing `WorkflowConditionEvaluatorTest` / `AttributeRangeEvaluatorTest` pattern in this module.

### Outcomes are total

```kotlin
sealed interface MappingOutcome {
    data class Applied(val target: ValueAddress, val value: String, val previous: String?) : MappingOutcome
    data class Skipped(val reason: SkipReason) : MappingOutcome   // NoSourceValue, TargetPresent,
                                                                  // HumanEdited, Unchanged, DirectionBlocked
    data class Failed(val reason: FailReason, val detail: String) : MappingOutcome  // TypeMismatch,
                                                                  // UnmappedOption, LossyConversion,
                                                                  // MissingTargetEvent, MetadataMissing
}
```

Every mapping produces exactly one outcome. Nothing is swallowed. `Failed` entries are logged via
Timber and surfaced in the diagnostics screen (§8); they never abort the remaining mappings unless
`settings.failFast` is set.

### Target event resolution (F10)

When a target address is a `DataElement`, the target event may not exist yet — a freshly created
enrollment only has events for auto-generate stages. `settings.targetEventPolicy`:

- `REQUIRE_EXISTING` (default) — no event ⇒ `Skipped(NoTargetEvent)`.
- `CREATE_IF_MISSING` — create the stage event (respecting `repeatable`), then write.

Default is `REQUIRE_EXISTING` because auto-creating events changes what the program looks like to a
health worker, and that should be a conscious choice.

> **Warning:** `CREATE_IF_MISSING` does more than create a row. Event creation fires any program
> rules bound to that stage — auto-fill rules, notifications, validation rules. An auto-created
> event from a mapping transfer is indistinguishable from one opened by the health worker.
> Administrators enabling this policy must audit the target stage's program rules and confirm
> none of them produce unintended side effects when triggered by an empty, mapping-seeded event.

## 6. Validation

`ConfigValidator` runs against live SDK metadata and returns errors, not exceptions. Checks:

**Existence** — every program/stage/data element/attribute/option-set UID resolves; every data
element belongs to its declared stage; every stage belongs to its declared program; every attribute
is assigned to its declared program.

**Type compatibility** — each binding's real `valueType` is convertible to its `canonical.type` by
the derived pipeline. `OPTION` canonical requires the bound field to actually have an option set.

**Option coverage** — The SDK metadata is the source of truth for option set membership, not the
datastore config. `ConfigValidator` reads the live option set from the SDK and checks that every
key in `binding.options` is a real option code in that field's option set as known to the SDK.
Every value must be in `canonical.codes`. This means a code added to the server's option set and
synced to the device becomes valid automatically, without a config update — and a code removed from
the server's option set becomes a validation error without any config change needed.

At runtime, `OptionSetTranslator` also re-reads option set membership from the SDK before
translating, so a code that was valid at config-load time but removed in a later sync is caught
at the point of use, not silently accepted.

`unmatched` policy controls what happens when a source value holds a code not present in
`binding.options`: `SKIP` (default — omit this mapping, continue others) or `FAIL` (surface as
`Failed(UnmappedOption)` for fields where any unknown code must be visible). Round-trip bijectivity
is checked and non-bijective maps are flagged (they are legal — many-to-one is a real modelling
choice — but the author is told).

**Units** — every `unit` pair resolves in the registry or has an explicit `scaleToCanonical`.

**Ambiguity (F8)** — no two mappings in one transfer resolve to the same target address. Two sources
writing one field is rejected at load, not resolved by luck at runtime.

**Aliasing (F9)** — source and target `Attribute` UIDs must differ; a target attribute also present
in the source program raises a warning.

**Direction** — a lossy `derive` binding used as a source without explicit `direction: BOTH` is an
error. When `direction: BOTH` is present on a lossy binding, `ConfigValidator` emits a prominent
warning (Timber.w level, also surfaced in the diagnostics screen):

> ⚠ Binding `<bindingId>` has a lossy derivation (`<op>`) but `direction: BOTH` is set. This
> enables backward derivation, which fabricates data: `<example detail, e.g. "ageInYears=34"
> back-computed to dateOfBirth introduces up to 364 days of false precision">`. Confirm this is
> intentional.

This warning is not suppressible — it is always emitted when the condition is present, so it
appears in every config load in logs and in the diagnostics screen even after the author has
acknowledged it.

An invalid concept is dropped with a logged error; valid concepts still run. A config that fails to
parse at all falls back to "no mappings" — matching `readCommunityConfig`'s existing fallback
contract in `CommunityDataStore.kt:45`. **Degrading to doing nothing is always correct here;
degrading to doing something approximate is not.**

## 7. Integration seams

All three are small, additive call sites in existing code:

| Trigger | Seam | Change |
|---|---|---|
| `TARGET_ENROLLMENT_CREATED` | `WorkflowRepository.evaluateAutoEnrollment` (`WorkflowRepository.kt:215`), after `enrolledPrograms.add(...)` at line 271 | Run mapping for the newly created enrollment. This is the requirement's headline case and today copies nothing. |
| `TARGET_ENROLLMENT_FORM_OPEN` | `EnrollmentPresenterImpl` init / `EnrollmentFormRepository` | Pre-fill target attributes **before** the form renders, so the health worker sees carried-over values instead of retyping them. |
| `SOURCE_EVENT_SAVED` | `EventCapturePresenterImpl.saveAndExit` (`EventCapturePresenterImpl.kt:243`), next to the existing `runWorkflowAutoEnrollment()` at line 255 | Propagate updates to already-existing target enrollments. `TriggerContext` carries `eventUid`, enabling `Pick.TRIGGERING`. |

`EnrollmentPresenterImpl.kt:179` and `EventCapturePresenterImpl.kt:265` already establish the
pattern — `Single.fromCallable { … }` on `schedulerProvider.io()`, Timber on error — so the mapping
call slots in alongside without new threading concepts.

### Migrating the existing mapper

`EntityAutoCreationConfig.attributesMappings` (`WorkflowConfig.kt:18`) becomes a thin adapter over
`ResolvedMapping`: each legacy `AttributeMapping` maps to an explicit attribute→attribute mapping
with `Constant` fallback for `defaultValue` and `SKIP_IF_PRESENT`. Legacy config keeps working
unchanged, but immediately gains type checking and the provenance guard. `isDuplicationKey` stays
where it is — it is a search concern, not a mapping concern.

## 8. Package layout

Mirrors the existing `tasking` module structure.

```
community/src/main/java/org/dhis2/community/mappers/
├── models/
│   ├── DataMapperConfig.kt        # Gson DTOs — all fields nullable
│   ├── Concept.kt                 # validated domain: Concept, Binding, Canonical
│   ├── ValueAddress.kt            # sealed address model + EventSelector
│   ├── Transform.kt               # sealed transform specs
│   └── MappingOutcome.kt          # Applied / Skipped / Failed
├── config/
│   ├── DataMapperConfigRepository.kt   # reads key "dataMappers" via readCommunityConfig
│   └── ConfigValidator.kt              # DTO -> domain, metadata-checked
├── resolve/
│   ├── ValueResolver.kt           # ValueAddress -> ResolvedValue(value, valueType, lastUpdated)
│   └── EventSelector.kt           # deterministic event picking
├── transform/
│   ├── Transforms.kt              # pure String -> Result<String?>
│   ├── TransformPipeline.kt       # derivation + composition
│   ├── OptionSetTranslator.kt
│   └── UnitRegistry.kt
├── write/
│   ├── ValueWriter.kt             # policy enforcement, the only writer
│   └── MappingLedger.kt           # provenance / idempotency (Room)
├── engine/
│   ├── DataMappingEngine.kt       # plan() / apply()
│   └── MappingPlan.kt
└── di/
    └── MapperModule.kt
```

`DataMappersConfigModel.kt` (currently an empty stub) is replaced by `models/DataMapperConfig.kt`.

### Diagnostics

A debug screen renders the `MappingPlan` for a chosen TEI and target program — source address,
selected event, raw value, each transform step, target address, and outcome. Because `plan()` is
side-effect free, this is a read-only view over the exact code that will run. This is the single
highest-leverage piece for the "getting this wrong means wrong data" concern: it turns mapping from
something an admin has to trust into something they can inspect before it touches a record.

## 9. Phasing

| Phase | Scope | Status |
|---|---|---|
| 1 | Address model, event selector, resolver, engine `plan`/`apply`, type coercion, `TARGET_ENROLLMENT_CREATED` trigger | **Done** |
| 2 | Concepts, option translation, unit registry, `ConfigValidator` | **Done** |
| 3 | Ledger + full write policies, `SOURCE_EVENT_SAVED` trigger | **Done** |
| 4 | Form pre-fill trigger, diagnostics screen, legacy `attributesMappings` adapter | Deferred |

### What is deliberately not wired

- **`TARGET_ENROLLMENT_FORM_OPEN`** — the trigger exists in the model but no call site dispatches it,
  because pre-filling has to happen before the form renders and that means reaching into
  `EnrollmentFormRepository` rather than adding a call after a save. `ConfigValidator` emits a warning
  when a transfer relies on it, so a config using it is never left looking configured while silently
  never firing. The config app does not offer it as a choice.
- **Cross-TEI mapping in `autoCreateEntity`** — that path creates a *different* TEI and copies across
  a relationship, whereas `TriggerContext` addresses a single TEI. Running the engine there would read
  a source enrollment the new TEI does not have, so the legacy `attributesMappings` remain the
  mechanism for it. This is the relationship-hop question below.
- **Enrollments created from a relationship** — `RelationshipRepository` and `TaskingRepository` also
  create enrollments, but for a *different* tracked entity, and mapping only ever moves values within
  one. Same reasoning as `autoCreateEntity` above.

### Where it is wired

| Trigger | Call site |
|---|---|
| `TARGET_ENROLLMENT_CREATED` | `WorkflowRepository.evaluateAutoEnrollment` — automatic enrollment |
| `TARGET_ENROLLMENT_CREATED` | `TeiProgramListRepositoryImpl.saveToEnroll` — enrolling an existing TEI from the dashboard |
| `TARGET_ENROLLMENT_CREATED` | `SearchRepositoryImpl.saveToEnroll` — enrolling from search |
| `SOURCE_EVENT_SAVED` | `WorkflowRepository.propagateMappedData`, called from `EventCapturePresenterImpl.saveAndExit` |

The trigger means *the target enrollment was created*, not *created by automation*. The two manual
sites pass no source programme, so `plan()` treats it as "any source" and every transfer into that
programme applies — which is the only sensible reading when a person, not a rule, decided to enrol.
Both run inside the existing Rx chain before it emits, so the enrolment form opens with mapped values
already written rather than appearing a moment later.

`DataMappers.init` is called from `App.onCreate` to give the provenance ledger a context. Both entry
points swallow failures: mapping is an enhancement to enrollment, never a precondition for it, so a
mapping problem must not surface as a failed enrollment or a lost record.

## 9a. Authoring: the configuration app

`ichis-configs` is where this config is written, and a datastore editor that asks for raw uids would
undo the safety the engine provides — a mistyped uid is a mapping that silently never runs, and a uid
pasted from the wrong program is worse. So the app is built on one rule: **the author selects a thing
by name and the app records its uid.** No uid is ever typed.

The mechanism is a single deep metadata fetch (`MetadataProvider`) plus a scope chain
(`FieldScope`) that lets a field declare what it depends on and resolve it by walking outward through
the enclosing form objects. That gives cascading pickers with no per-choice round trip:

| Picker | Scoped to | Prevents |
|---|---|---|
| `program` | — | — |
| `programStage` | the binding's program | a stage from another program (validator error F-stage) |
| `trackerField` | the chosen stage's data elements, or the program's attributes | the "paste the UID from DHIS2" field this replaces |
| `optionCodeMap` | the chosen field's real option set ↔ the concept's canonical codes | inventing a code that does not exist (F2) |
| `canonicalCodes` | offers bound fields' option sets as one-click seeds | retyping a vocabulary by hand |
| `conceptRef` | concepts defined on the Concepts tab | a typo'd concept id carrying one fewer field than intended |
| `unit` | the units `UnitRegistry` can actually convert | an unregistered unit the engine will reject (F3) |

Two further authoring-time defences worth noting. Every picked field displays its **DHIS2 value type
and option set** inline, so a `NUMBER`-to-`TEXT` mismatch is visible while choosing rather than at
sync time. And a picker whose prerequisite is unmet renders an explanation ("choose the program stage
first — data elements are listed per stage") instead of an empty dropdown, so the dependency order is
taught by the form rather than documented elsewhere.

The option-code editor also reports how many of the field's real codes are still unmapped, and offers
to add them all at once — the same warning `ConfigValidator` would raise on the device, surfaced at the
point where it can still be fixed cheaply.

## 10. Open questions

1. **Relationship hops** — should a source be resolvable on a *related* TEI (household → member),
   the way `autoCreateEntity` already crosses a relationship? The address model can carry an
   `entity: SELF | RELATED(relationshipType, side)` field. Deferred until there is a concrete case,
   but the model leaves room so it is not a rewrite.
2. **Ledger retention** — unbounded growth is unlikely to matter (one row per mapped field per TEI),
   but a prune on TEI deletion should be confirmed against the SDK's local deletion behaviour.
3. **Sync-side conflicts** — if the server rejects a mapped value (e.g. option not in the server's
   set), the local ledger records a write that did not stick. Worth reconciling against import
   summaries in a later phase.
4. **Multi-device ledger consistency** — the provenance ledger is local-only (by design — it
   describes *this device's* write history). In field deployments where the same TEI is touched
   on two devices, Device B has no record of what Device A wrote. When Device B sees a value it
   didn't write, it cannot distinguish "device A's mapping wrote this" from "a clinician typed
   this", so it will honour `SKIP_IF_PRESENT` and not re-propagate. This is the safe failure
   mode, but it means a TEI's first meaningful interaction on a new device may not carry over
   values from another device's mapping run. Whether this is acceptable depends on field workflow:
   if TEIs are consistently assigned to one device (common in community health worker programmes),
   the issue does not arise in practice. If TEIs routinely migrate between devices, a lightweight
   server-side sync marker (a tracked entity attribute or a datastore entry per TEI) may be
   needed to share ledger intent across devices — deferred until a concrete multi-device case
   is observed.
