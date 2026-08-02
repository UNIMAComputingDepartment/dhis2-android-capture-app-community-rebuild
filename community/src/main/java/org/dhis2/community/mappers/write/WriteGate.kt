package org.dhis2.community.mappers.write

import org.dhis2.community.mappers.models.FailReason
import org.dhis2.community.mappers.models.SkipReason
import org.dhis2.community.mappers.models.WritePolicy
import org.dhis2.community.mappers.resolve.StoredValue

/** Whether a computed value may be written. */
sealed interface Decision {
    object Write : Decision
    data class Skip(val reason: SkipReason, val detail: String? = null) : Decision
    data class Fail(val reason: FailReason, val detail: String) : Decision
}

/**
 * Decides whether a write may proceed. Pure, so every conflict rule is unit-testable.
 *
 * Two invariants hold no matter which [WritePolicy] is in force, because both protect data a policy
 * has no business destroying:
 *
 *  - a blank value never overwrites anything, so no mapping can erase a record;
 *  - a target a human has edited is left alone, so a re-run cannot revert a deliberate correction.
 *
 * Everything else is policy.
 */
object WriteGate {

    fun decide(
        newValue: String?,
        current: StoredValue,
        ledger: LedgerEntry?,
        policy: WritePolicy,
        sourceLastUpdated: Long?,
        /** Escape hatch for the rare "the source is authoritative, always mirror it" case. */
        respectManualEdits: Boolean = true,
        /**
         * The mapping is removing the target's value rather than setting one.
         *
         * Deliberately routed through every guard below rather than around them. Erasure is the most
         * destructive thing a mapping can do, so it must be *harder* than an ordinary write, never
         * easier: the manual-edit guard still holds, and a policy that would not overwrite will not
         * clear either.
         */
        clearing: Boolean = false,
    ): Decision {
        if (clearing) {
            // Nothing there to remove. Not a failure — the target is already in the state we want.
            if (!current.isPresent) {
                return Decision.Skip(SkipReason.UNCHANGED, "target is already empty")
            }
            if (respectManualEdits && ledger != null && ledger.writtenValue != current.value) {
                return Decision.Skip(
                    SkipReason.HUMAN_EDITED,
                    "target holds '${current.value}' but this mapping last wrote " +
                        "'${ledger.writtenValue}', so the value was not ours to remove",
                )
            }
            return clearanceUnderPolicy(current, ledger, policy, sourceLastUpdated)
        }

        if (newValue.isNullOrBlank()) {
            return Decision.Skip(SkipReason.BLANK_NOT_WRITTEN)
        }

        if (current.value == newValue) {
            // Writing an identical value would bump lastUpdated and queue a pointless sync.
            return Decision.Skip(SkipReason.UNCHANGED)
        }

        if (!current.isPresent) {
            return Decision.Write
        }

        // The target holds a different value. If it is not the value we last wrote, someone changed
        // it by hand — honoured ahead of policy, since no configured policy is evidence that
        // overwriting a clinician's correction was intended (F7).
        if (respectManualEdits && ledger != null && ledger.writtenValue != current.value) {
            return Decision.Skip(
                SkipReason.HUMAN_EDITED,
                "target holds '${current.value}' but this mapping last wrote '${ledger.writtenValue}'",
            )
        }

        return when (policy) {
            WritePolicy.SKIP_IF_PRESENT -> Decision.Skip(SkipReason.TARGET_PRESENT)

            WritePolicy.OVERWRITE -> Decision.Write

            WritePolicy.FAIL_ON_CONFLICT -> Decision.Fail(
                FailReason.CONFLICT,
                "target already holds '${current.value}' and the new value is '$newValue'",
            )

            WritePolicy.OVERWRITE_IF_SOURCE_NEWER -> when {
                // With no record of our own write there is no timestamp to compare against, so
                // "newer" is unknowable and the conservative branch wins.
                ledger == null -> Decision.Skip(
                    SkipReason.TARGET_PRESENT,
                    "no previous write recorded, so the source cannot be shown to be newer",
                )

                sourceLastUpdated == null -> Decision.Skip(
                    SkipReason.TARGET_PRESENT,
                    "source has no lastUpdated timestamp to compare",
                )

                sourceLastUpdated > ledger.writtenAt -> Decision.Write

                else -> Decision.Skip(
                    SkipReason.TARGET_PRESENT,
                    "source is not newer than the last write",
                )
            }
        }
    }

    /**
     * The conflict policy applied to a removal.
     *
     * Deliberately the same rules as an ordinary overwrite. The consequence worth knowing is that
     * under the default SKIP_IF_PRESENT a clear never fires — clearing only matters when the target
     * holds something, which is exactly the case that policy declines to touch. `ConfigValidator`
     * warns rather than leaving that as a silent no-op.
     */
    private fun clearanceUnderPolicy(
        current: StoredValue,
        ledger: LedgerEntry?,
        policy: WritePolicy,
        sourceLastUpdated: Long?,
    ): Decision = when (policy) {
        WritePolicy.SKIP_IF_PRESENT -> Decision.Skip(
            SkipReason.TARGET_PRESENT,
            "target holds '${current.value}' and the policy is SKIP_IF_PRESENT, which does not " +
                "permit removing it; use OVERWRITE to let this mapping clear the field",
        )

        WritePolicy.OVERWRITE -> Decision.Write

        WritePolicy.FAIL_ON_CONFLICT -> Decision.Fail(
            FailReason.CONFLICT,
            "target holds '${current.value}' and this mapping would remove it",
        )

        WritePolicy.OVERWRITE_IF_SOURCE_NEWER -> when {
            ledger == null -> Decision.Skip(
                SkipReason.TARGET_PRESENT,
                "no previous write recorded, so the source cannot be shown to be newer",
            )

            sourceLastUpdated == null -> Decision.Skip(
                SkipReason.TARGET_PRESENT,
                "source has no lastUpdated timestamp to compare",
            )

            sourceLastUpdated > ledger.writtenAt -> Decision.Write

            else -> Decision.Skip(
                SkipReason.TARGET_PRESENT,
                "source is not newer than the last write",
            )
        }
    }
}
