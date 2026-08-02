package org.dhis2.community.mappers

import org.dhis2.community.mappers.models.FailReason
import org.dhis2.community.mappers.models.SkipReason
import org.dhis2.community.mappers.models.WritePolicy
import org.dhis2.community.mappers.resolve.StoredValue
import org.dhis2.community.mappers.write.Decision
import org.dhis2.community.mappers.write.LedgerEntry
import org.dhis2.community.mappers.write.WriteGate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tests for the conflict rules — in particular the two guards no policy is allowed to override. */
class WriteGateTest {

    private fun decide(
        newValue: String?,
        current: String?,
        policy: WritePolicy = WritePolicy.SKIP_IF_PRESENT,
        ledger: LedgerEntry? = null,
        sourceLastUpdated: Long? = null,
        respectManualEdits: Boolean = true,
    ) = WriteGate.decide(
        newValue = newValue,
        current = StoredValue(current),
        ledger = ledger,
        policy = policy,
        sourceLastUpdated = sourceLastUpdated,
        respectManualEdits = respectManualEdits,
    )

    private fun skipReason(decision: Decision): SkipReason {
        assertTrue("expected a skip but got $decision", decision is Decision.Skip)
        return (decision as Decision.Skip).reason
    }

    // ─── Invariants that hold under every policy ─────────────────────────────

    @Test
    fun `a blank value never overwrites an existing one under any policy`() {
        WritePolicy.entries.forEach { policy ->
            val decision = decide(newValue = "", current = "11.2", policy = policy)
            assertEquals(
                "policy $policy allowed a blank to erase data",
                SkipReason.BLANK_NOT_WRITTEN,
                skipReason(decision),
            )
        }
    }

    @Test
    fun `a human edited target is left alone even when the policy says overwrite`() {
        // We last wrote 11.2; the field now reads 12.0, so a clinician corrected it. OVERWRITE must
        // not silently revert that.
        val decision = decide(
            newValue = "11.2",
            current = "12.0",
            policy = WritePolicy.OVERWRITE,
            ledger = LedgerEntry("m1", writtenValue = "11.2", writtenAt = 1_000L),
        )

        assertEquals(SkipReason.HUMAN_EDITED, skipReason(decision))
    }

    @Test
    fun `the manual edit guard can be waived deliberately`() {
        val decision = decide(
            newValue = "11.2",
            current = "12.0",
            policy = WritePolicy.OVERWRITE,
            ledger = LedgerEntry("m1", writtenValue = "11.2", writtenAt = 1_000L),
            respectManualEdits = false,
        )

        assertEquals(Decision.Write, decision)
    }

    @Test
    fun `a target still holding our own last write is ours to update`() {
        val decision = decide(
            newValue = "12.0",
            current = "11.2",
            policy = WritePolicy.OVERWRITE,
            ledger = LedgerEntry("m1", writtenValue = "11.2", writtenAt = 1_000L),
        )

        assertEquals(Decision.Write, decision)
    }

    // ─── Idempotency ─────────────────────────────────────────────────────────

    @Test
    fun `rewriting an identical value is skipped to avoid pointless sync churn`() {
        assertEquals(SkipReason.UNCHANGED, skipReason(decide(newValue = "11.2", current = "11.2")))
    }

    // ─── Policies ────────────────────────────────────────────────────────────

    @Test
    fun `an empty target is filled regardless of policy`() {
        WritePolicy.entries.forEach { policy ->
            assertEquals(
                "policy $policy refused to fill an empty target",
                Decision.Write,
                decide(newValue = "11.2", current = null, policy = policy),
            )
        }
    }

    @Test
    fun `skip if present leaves a populated target untouched`() {
        assertEquals(
            SkipReason.TARGET_PRESENT,
            skipReason(decide(newValue = "11.2", current = "9.9", policy = WritePolicy.SKIP_IF_PRESENT)),
        )
    }

    @Test
    fun `fail on conflict reports rather than choosing a winner`() {
        val decision = decide(
            newValue = "11.2",
            current = "9.9",
            policy = WritePolicy.FAIL_ON_CONFLICT,
        )

        assertTrue(decision is Decision.Fail)
        assertEquals(FailReason.CONFLICT, (decision as Decision.Fail).reason)
    }

    @Test
    fun `overwrite if source newer writes when the source postdates our last write`() {
        val decision = decide(
            newValue = "12.0",
            current = "11.2",
            policy = WritePolicy.OVERWRITE_IF_SOURCE_NEWER,
            ledger = LedgerEntry("m1", writtenValue = "11.2", writtenAt = 1_000L),
            sourceLastUpdated = 5_000L,
        )

        assertEquals(Decision.Write, decision)
    }

    @Test
    fun `overwrite if source newer holds off when the source is older`() {
        val decision = decide(
            newValue = "12.0",
            current = "11.2",
            policy = WritePolicy.OVERWRITE_IF_SOURCE_NEWER,
            ledger = LedgerEntry("m1", writtenValue = "11.2", writtenAt = 5_000L),
            sourceLastUpdated = 1_000L,
        )

        assertEquals(SkipReason.TARGET_PRESENT, skipReason(decision))
    }

    @Test
    fun `overwrite if source newer holds off when there is nothing to compare against`() {
        // No previous write means "newer" is unknowable, so the conservative branch wins rather than
        // the engine assuming its value is the fresher one.
        val decision = decide(
            newValue = "12.0",
            current = "11.2",
            policy = WritePolicy.OVERWRITE_IF_SOURCE_NEWER,
            ledger = null,
            sourceLastUpdated = 9_999L,
        )

        assertEquals(SkipReason.TARGET_PRESENT, skipReason(decision))
    }

    // ─── Clearing ────────────────────────────────────────────────────────────

    @Test
    fun `clearing an empty target is a no-op rather than a write`() {
        val decision = WriteGate.decide(
            newValue = "",
            current = StoredValue(null),
            ledger = null,
            policy = WritePolicy.OVERWRITE,
            sourceLastUpdated = null,
            clearing = true,
        )

        assertTrue(decision is Decision.Skip)
        assertEquals(SkipReason.UNCHANGED, (decision as Decision.Skip).reason)
    }

    @Test
    fun `clearing removes a value this mapping wrote`() {
        val decision = WriteGate.decide(
            newValue = "",
            current = StoredValue("true"),
            ledger = LedgerEntry("m1", "true", writtenAt = 1_000L),
            policy = WritePolicy.OVERWRITE,
            sourceLastUpdated = 2_000L,
            clearing = true,
        )

        assertEquals(Decision.Write, decision)
    }

    /** Erasure must be no easier than any other write, so the manual-edit guard applies unchanged. */
    @Test
    fun `clearing never removes a value a human put there`() {
        val decision = WriteGate.decide(
            newValue = "",
            current = StoredValue("true"),
            ledger = LedgerEntry("m1", "POS", writtenAt = 1_000L),
            policy = WritePolicy.OVERWRITE,
            sourceLastUpdated = 2_000L,
            clearing = true,
        )

        assertTrue(decision is Decision.Skip)
        assertEquals(SkipReason.HUMAN_EDITED, (decision as Decision.Skip).reason)
    }

    /**
     * The combination worth knowing about: clearing only matters when the target holds something,
     * which is precisely what SKIP_IF_PRESENT declines to touch. ConfigValidator warns about it.
     */
    @Test
    fun `the default policy does not permit clearing`() {
        val decision = WriteGate.decide(
            newValue = "",
            current = StoredValue("true"),
            ledger = LedgerEntry("m1", "true", writtenAt = 1_000L),
            policy = WritePolicy.SKIP_IF_PRESENT,
            sourceLastUpdated = 2_000L,
            clearing = true,
        )

        assertTrue(decision is Decision.Skip)
        assertEquals(SkipReason.TARGET_PRESENT, (decision as Decision.Skip).reason)
    }
}
