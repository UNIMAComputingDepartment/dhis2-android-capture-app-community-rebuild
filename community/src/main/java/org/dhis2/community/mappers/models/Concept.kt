package org.dhis2.community.mappers.models

data class Concept(
    val id: String,
    val label: String?,
    val canonical: Canonical,
    val bindings: List<Binding>,
)

data class Canonical(
    val type: CanonicalType,
    val unit: String? = null,
    val codes: List<String>? = null,
)

enum class CanonicalType {
    TEXT, NUMBER, INTEGER, BOOLEAN, DATE, DATETIME, OPTION
}

data class Binding(
    val programUid: String,
    val at: ValueAddress,
    val unit: String? = null,

    /** Reading: this program's code -> canonical code. */
    val options: Map<String, String>? = null,

    /**
     * Writing: canonical code -> this program's code. Optional.
     *
     * Without it, writing is the inverse of [options], which only exists when that map is one-to-one.
     * That is the right default — the engine must never *infer* that two distinct clinical findings
     * can be collapsed into one. But programs genuinely do disagree about vocabulary size: one
     * records positive/negative/unknown, the next has no code for unknown at all, and the honest
     * answer may well be "record unknown as negative here" or "leave it blank".
     *
     * Declaring that here makes the collapse an explicit clinical decision with an author behind it,
     * rather than something the engine guessed. Lossy by construction, which is exactly why it cannot
     * be derived and has to be typed out — the same bargain as [Derivation].
     */
    val writeOptions: Map<String, String>? = null,

    /**
     * What to do when a canonical code has no code in this program: [UnmatchedPolicy.FAIL] reports
     * it, [UnmatchedPolicy.SKIP] leaves the target untouched.
     *
     * SKIP is the honest choice where the target simply has no way to express the value — writing
     * nothing says "not recorded here", which is true, whereas failing implies a fault.
     */
    val onUnmapped: UnmatchedPolicy? = null,

    val derive: Derivation? = null,
    val direction: Direction = Direction.BOTH,
    val onConflict: WritePolicy? = null,
)

enum class Direction { BOTH, READ_ONLY, WRITE_ONLY }

enum class WritePolicy {
    SKIP_IF_PRESENT,
    OVERWRITE,
    OVERWRITE_IF_SOURCE_NEWER,
    FAIL_ON_CONFLICT,
}

enum class Trigger {
    TARGET_ENROLLMENT_CREATED,
    TARGET_ENROLLMENT_FORM_OPEN,
    SOURCE_EVENT_SAVED,
}

/**
 * What fired a mapping run, and the handles it supplies.
 *
 * [targetProgramUid] and [sourceProgramUid] both narrow which transfers apply; either may be null,
 * meaning "any". The enrollment-created trigger knows its target, the event-saved trigger knows its
 * source and may fan out to several targets.
 */
data class TriggerContext(
    val trigger: Trigger,
    val teiUid: String,
    val targetProgramUid: String? = null,
    val targetEnrollmentUid: String? = null,
    val sourceEnrollmentUid: String? = null,
    val sourceEventUid: String? = null,
    val sourceProgramUid: String? = null,
)

data class Derivation(
    val op: DerivationOp,
    val unit: String? = null,
    val asOf: String? = null,
)

enum class DerivationOp {
    /** Reads a date and stores an age. Lossy: an age cannot recover the date it came from. */
    AGE_FROM,

    /**
     * Stores "this happened" as a boolean, derived from the canonical value merely being present.
     *
     * The honest direction of a date/flag pair. A recorded date of administration implies it was
     * administered, so `DATE -> BOOLEAN` is sound; the reverse is not, because a ticked box says
     * nothing about *when*. Marking the binding derived makes that asymmetry structural rather than a
     * matter of authoring discipline — it defaults to WRITE_ONLY and the validator refuses to read it.
     *
     * Never writes `false`: absence of a date is not evidence that something did not happen, and a
     * mapping that turned "not recorded here" into a positive "no" would be inventing a clinical
     * finding.
     */
    PRESENCE,
}

data class ResolvedMapping(
    val id: String,
    val from: ValueAddress,
    val to: ValueAddress,
    val pipeline: List<TransformSpec>,
    val policy: WritePolicy,
    val conceptId: String? = null,
)

data class ValidatedConfig(
    val settings: MappingSettings,
    val concepts: List<Concept>,
    val transfers: List<ValidatedTransfer>,
    val errors: List<ConfigError>,
)

data class MappingSettings(
    val defaultOnConflict: WritePolicy = WritePolicy.SKIP_IF_PRESENT,
    val targetEventPolicy: TargetEventPolicy = TargetEventPolicy.REQUIRE_EXISTING,
    val failFast: Boolean = false,
)

enum class TargetEventPolicy { REQUIRE_EXISTING, CREATE_IF_MISSING }

data class ValidatedTransfer(
    val id: String,
    val fromProgramUid: String,
    val toProgramUid: String,
    val triggers: List<Trigger>,
    val resolvedMappings: List<ResolvedMapping>,
)

data class ConfigError(
    val severity: ErrorSeverity,
    val message: String,
    val context: String? = null,
)

enum class ErrorSeverity { ERROR, WARNING }
