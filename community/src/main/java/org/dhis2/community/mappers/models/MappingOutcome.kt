package org.dhis2.community.mappers.models

/**
 * The result of one mapping. Every planned mapping produces exactly one outcome — nothing is
 * swallowed, so "the value didn't arrive" always has a recorded reason.
 */
sealed interface MappingOutcome {

    /** A write that happened (or, in a plan, one that would happen). */
    data class Applied(
        val target: ValueAddress,
        val value: String,
        val previous: String?,
        /**
         * The write removes the target's value rather than setting one.
         *
         * Explicit rather than inferred from a blank [value], because "blank" reaching the writer by
         * accident must stay impossible to confuse with a deliberate erasure.
         */
        val clears: Boolean = false,
    ) : MappingOutcome

    /** Nothing to do, legitimately. Not an error. */
    data class Skipped(val reason: SkipReason, val detail: String? = null) : MappingOutcome

    /** The mapping could not be completed correctly. Never results in a partial or coerced write. */
    data class Failed(val reason: FailReason, val detail: String) : MappingOutcome
}

enum class SkipReason {
    /** The source held no value (or only whitespace). */
    NO_SOURCE_VALUE,

    /** The target already had a value and the policy is SKIP_IF_PRESENT. */
    TARGET_PRESENT,

    /**
     * The target's current value differs from what we last wrote, so a human edited it. Honoured
     * regardless of the configured policy (F7).
     */
    HUMAN_EDITED,

    /** The computed value equals the current target value; writing would be pointless churn. */
    UNCHANGED,

    /** The source binding is WRITE_ONLY, or the target binding is READ_ONLY. */
    DIRECTION_BLOCKED,

    /** No event satisfied the source selector. */
    NO_SOURCE_EVENT,

    /** The target stage has no event and targetEventPolicy is REQUIRE_EXISTING (F10). */
    NO_TARGET_EVENT,

    /** The source value resolved to blank and writeBlanks is off; blanks never erase data. */
    BLANK_NOT_WRITTEN,
}

enum class FailReason {
    /** The value could not be converted to the target's type without guessing (F1). */
    TYPE_MISMATCH,

    /** An option code had no entry in the translation map and the policy is FAIL (F2). */
    UNMAPPED_OPTION,

    /** Conversion would lose information (e.g. 12.5 into an INTEGER) and allowLossy is off. */
    LOSSY_CONVERSION,

    /** Units could not be reconciled; the engine refuses to assume a 1.0 factor (F3). */
    UNIT_MISMATCH,

    /** A referenced uid is absent from local metadata. */
    METADATA_MISSING,

    /** The target already had a different value and the policy is FAIL_ON_CONFLICT. */
    CONFLICT,

    /** The write itself threw. */
    WRITE_FAILED,

    /** Config was structurally invalid in a way that survived to runtime. */
    INVALID_CONFIG,
}

/**
 * A read-only description of everything a run would do. Produced by
 * [org.dhis2.community.mappers.engine.DataMappingEngine.plan] with no side effects, so the same code
 * path drives dry-run previews, unit tests, and real execution.
 */
data class MappingPlan(
    val context: TriggerContext,
    val entries: List<PlanEntry>,
    val configErrors: List<ConfigError> = emptyList(),
) {
    val writes: List<PlanEntry> get() = entries.filter { it.outcome is MappingOutcome.Applied }
    val failures: List<PlanEntry> get() = entries.filter { it.outcome is MappingOutcome.Failed }
}

/**
 * One mapping's planned outcome, carrying the intermediate values so a diagnostics screen can show
 * *why* a value came out the way it did.
 */
data class PlanEntry(
    val mapping: ResolvedMapping,
    val outcome: MappingOutcome,
    val sourceRaw: String? = null,
    val sourceEventUid: String? = null,
    /** Value after each transform, in pipeline order — the audit trail for a conversion. */
    val transformTrace: List<String> = emptyList(),
    val targetEventUid: String? = null,
    val sourceLastUpdated: Long? = null,
)

/** What actually happened when a plan was applied. */
data class MappingReport(
    val applied: Int,
    val skipped: Int,
    val failed: Int,
    val entries: List<PlanEntry>,
) {
    val hasFailures: Boolean get() = failed > 0

    companion object {
        fun from(entries: List<PlanEntry>) = MappingReport(
            applied = entries.count { it.outcome is MappingOutcome.Applied },
            skipped = entries.count { it.outcome is MappingOutcome.Skipped },
            failed = entries.count { it.outcome is MappingOutcome.Failed },
            entries = entries,
        )
    }
}
