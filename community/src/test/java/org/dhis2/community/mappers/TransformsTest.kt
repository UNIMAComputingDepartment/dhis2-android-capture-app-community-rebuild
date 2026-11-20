package org.dhis2.community.mappers

import org.dhis2.community.mappers.models.AgeUnit
import org.dhis2.community.mappers.models.FailReason
import org.dhis2.community.mappers.models.RoundMode
import org.dhis2.community.mappers.models.TransformSpec
import org.dhis2.community.mappers.models.OptionAction
import org.dhis2.community.mappers.models.UnmatchedPolicy
import org.dhis2.community.mappers.transform.TransformContext
import org.dhis2.community.mappers.transform.TransformResult
import org.dhis2.community.mappers.transform.Transforms
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Tests for the value-conversion layer.
 *
 * These matter more than their size suggests: every case here is a way a mapping could turn correct
 * data into plausible-looking wrong data, so the assertions are mostly about *refusing* to convert
 * rather than converting.
 */
class TransformsTest {

    private val context = TransformContext(asOf = LocalDate.of(2026, 7, 30))

    private fun apply(spec: TransformSpec, input: String) = Transforms.apply(spec, input, context)

    private fun success(spec: TransformSpec, input: String): String {
        val result = apply(spec, input)
        assertTrue("expected success but got $result", result is TransformResult.Success)
        return (result as TransformResult.Success).value
    }

    private fun failure(spec: TransformSpec, input: String): FailReason {
        val result = apply(spec, input)
        assertTrue("expected failure but got $result", result is TransformResult.Failure)
        return (result as TransformResult.Failure).reason
    }

    // ─── Numeric strictness (F1) ─────────────────────────────────────────────

    @Test
    fun `integer conversion refuses to truncate a fractional value`() {
        // Truncating 12.5 to 12 is a silent data change, so it fails instead.
        assertEquals(FailReason.LOSSY_CONVERSION, failure(TransformSpec.ToInteger, "12.5"))
    }

    @Test
    fun `integer conversion accepts a whole number written as a decimal`() {
        assertEquals("12", success(TransformSpec.ToInteger, "12.0"))
    }

    @Test
    fun `number conversion rejects non-numeric text rather than defaulting to zero`() {
        assertEquals(FailReason.TYPE_MISMATCH, failure(TransformSpec.ToNumber, "not a number"))
    }

    @Test
    fun `number conversion keeps integral values integral`() {
        assertEquals("120", success(TransformSpec.ToNumber, "120.00"))
    }

    @Test
    fun `explicit rounding is how a lossy conversion is opted into`() {
        assertEquals("12.5", success(TransformSpec.Round(1, RoundMode.HALF_UP), "12.46"))
        assertEquals("13", success(TransformSpec.Round(0, RoundMode.HALF_UP), "12.5"))
        assertEquals("12", success(TransformSpec.Round(0, RoundMode.FLOOR), "12.9"))
    }

    // ─── Unit scaling (F3) ───────────────────────────────────────────────────

    @Test
    fun `scaling converts haemoglobin from g per L to g per dL`() {
        // The 10x error this prevents is the canonical example: 120 g/L is 12 g/dL, not 120.
        assertEquals("12", success(TransformSpec.ScaleUnit(0.1), "120"))
    }

    @Test
    fun `scaling refuses a non-numeric value`() {
        assertEquals(FailReason.TYPE_MISMATCH, failure(TransformSpec.ScaleUnit(0.1), "high"))
    }

    // ─── Option translation (F2) ─────────────────────────────────────────────

    @Test
    fun `option translation maps codes by exact code only`() {
        val spec = TransformSpec.TranslateOptions(mapOf("1" to "POSITIVE", "2" to "NEGATIVE"))
        assertEquals("POSITIVE", success(spec, "1"))
        assertEquals("NEGATIVE", success(spec, "2"))
    }

    @Test
    fun `an unmapped option code fails rather than passing through`() {
        // Passing "9" through unchanged would write a code that means something else entirely in the
        // target's option set.
        val spec = TransformSpec.TranslateOptions(
            mapOf("1" to "POSITIVE"),
            UnmatchedPolicy.FAIL,
        )
        assertEquals(FailReason.UNMAPPED_OPTION, failure(spec, "9"))
    }

    @Test
    fun `an unmapped option code can be configured to skip instead`() {
        val spec = TransformSpec.TranslateOptions(mapOf("1" to "POSITIVE"), UnmatchedPolicy.SKIP)
        assertEquals(TransformResult.Empty, apply(spec, "9"))
    }

    // ─── Age derivation (F6) ─────────────────────────────────────────────────

    @Test
    fun `age is derived from a birth date against the injected reference date`() {
        assertEquals("34", success(TransformSpec.AgeFrom(AgeUnit.YEARS, "TODAY"), "1992-01-15"))
        assertEquals("7", success(TransformSpec.AgeFrom(AgeUnit.MONTHS, "TODAY"), "2025-12-15"))
    }

    @Test
    fun `age respects a birthday that has not yet occurred this year`() {
        // 2026-07-30 reference, birthday 31 July: still 33, not 34.
        assertEquals("33", success(TransformSpec.AgeFrom(AgeUnit.YEARS, "TODAY"), "1992-07-31"))
    }

    @Test
    fun `a future birth date fails rather than yielding a negative age`() {
        assertEquals(
            FailReason.TYPE_MISMATCH,
            failure(TransformSpec.AgeFrom(AgeUnit.YEARS, "TODAY"), "2030-01-01"),
        )
    }

    @Test
    fun `age derivation rejects a value that is not a date`() {
        assertEquals(
            FailReason.TYPE_MISMATCH,
            failure(TransformSpec.AgeFrom(AgeUnit.YEARS, "TODAY"), "34"),
        )
    }

    // ─── Dates ───────────────────────────────────────────────────────────────

    @Test
    fun `date formatting takes the date part of a datetime`() {
        assertEquals(
            "2026-03-04",
            success(TransformSpec.FormatDate("yyyy-MM-dd"), "2026-03-04T13:45:00.000"),
        )
    }

    @Test
    fun `an impossible calendar date is rejected rather than rolled over`() {
        // Lenient parsing would turn 31 February into 2 or 3 March — a fabricated date.
        assertEquals(FailReason.TYPE_MISMATCH, failure(TransformSpec.FormatDate("yyyy-MM-dd"), "2026-02-31"))
    }

    // ─── Booleans and text ───────────────────────────────────────────────────

    @Test
    fun `boolean recognises the common encodings`() {
        val spec = TransformSpec.BooleanAs("YES", "NO")
        assertEquals("YES", success(spec, "true"))
        assertEquals("YES", success(spec, "1"))
        assertEquals("NO", success(spec, "false"))
        assertEquals("NO", success(spec, "no"))
    }

    @Test
    fun `boolean rejects a value it cannot interpret`() {
        assertEquals(FailReason.TYPE_MISMATCH, failure(TransformSpec.BooleanAs("YES", "NO"), "maybe"))
    }

    // ─── Presence: date implies it happened ──────────────────────────────────

    @Test
    fun `presence turns a recorded date into a positive flag`() {
        assertEquals("true", success(TransformSpec.Presence("true"), "2026-03-04"))
    }

    @Test
    fun `presence never asserts a negative from a missing value`() {
        // "No date recorded in the source" is not evidence the vaccination did not happen. Writing a
        // definite "no" on that basis would fabricate a clinical finding, so this skips instead.
        assertEquals(TransformResult.Empty, apply(TransformSpec.Presence("true"), ""))
        assertEquals(TransformResult.Empty, apply(TransformSpec.Presence("true"), "   "))
    }

    @Test
    fun `presence emits the target's own code for the positive value`() {
        assertEquals("Y", success(TransformSpec.Presence("Y"), "2026-03-04"))
    }

    @Test
    fun `blank input is empty rather than a failure`() {
        assertEquals(TransformResult.Empty, apply(TransformSpec.Trim, "   "))
        assertEquals(TransformResult.Empty, apply(TransformSpec.ToNumber, ""))
    }

    // ─── Per-value outcomes in an option map ─────────────────────────────────

    @Test
    fun `an option mapped to BLANK produces nothing rather than a value`() {
        val spec = TransformSpec.TranslateOptions(
            mapOf("POSITIVE" to "POS", "UNKNOWN" to OptionAction.BLANK),
            UnmatchedPolicy.FAIL,
        )

        assertEquals(TransformResult.Success("POS"), Transforms.apply(spec, "POSITIVE", TransformContext()))
        // Empty is the benign "nothing to carry" outcome, so the target is left as it stands rather
        // than being overwritten or reported as a fault.
        assertEquals(TransformResult.Empty, Transforms.apply(spec, "UNKNOWN", TransformContext()))
    }

    @Test
    fun `an option mapped to FAIL reports even when the map otherwise skips`() {
        val spec = TransformSpec.TranslateOptions(
            mapOf("UNKNOWN" to OptionAction.FAIL),
            UnmatchedPolicy.SKIP,
        )

        val result = Transforms.apply(spec, "UNKNOWN", TransformContext())
        assertTrue(result is TransformResult.Failure)
        // Something with no entry at all still follows the map-wide policy.
        assertEquals(TransformResult.Empty, Transforms.apply(spec, "POSITIVE", TransformContext()))
    }
}
