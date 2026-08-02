package org.dhis2.community.mappers.transform

import org.dhis2.community.mappers.models.AgeUnit
import org.dhis2.community.mappers.models.FailReason
import org.dhis2.community.mappers.models.RoundMode
import org.dhis2.community.mappers.models.TransformSpec
import org.dhis2.community.mappers.models.UnmatchedPolicy
import org.dhis2.community.mappers.models.OptionAction
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.Period
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit

/**
 * Outcome of a single transform step.
 *
 * The three-way split is what enforces the "no silent coercion" principle: a transform that cannot
 * produce a definitively correct value returns [Failure], never a best guess. [Empty] is the
 * distinct, benign "there is nothing to carry" case.
 */
sealed interface TransformResult {
    data class Success(val value: String) : TransformResult

    /** Nothing to carry. The target is left exactly as it stands. */
    object Empty : TransformResult

    /**
     * Deliberately remove the target's value — as distinct from [Empty], which leaves it alone.
     *
     * Only ever produced by an explicit instruction in config, never inferred from a missing value.
     */
    object Clear : TransformResult

    data class Failure(val reason: FailReason, val detail: String) : TransformResult
}

/** Reference data a transform may need that is not part of the value itself. */
data class TransformContext(
    /** Reference date for age derivations. Injected rather than read from the clock, so tests are deterministic. */
    val asOf: LocalDate = LocalDate.now(),
)

/**
 * Pure transform implementations: `(String, TransformContext) -> TransformResult`.
 *
 * Everything here is deliberately free of D2, Android, and the clock, so the entire value-conversion
 * surface — the part that turns right data into wrong data when it misbehaves — is unit-testable
 * with plain JUnit.
 */
object Transforms {

    fun apply(spec: TransformSpec, input: String, context: TransformContext): TransformResult =
        when (spec) {
            TransformSpec.Trim -> trim(input)
            is TransformSpec.ScaleUnit -> scale(input, spec.factor, spec.offset)
            is TransformSpec.TranslateOptions -> translateOptions(input, spec.map, spec.unmatchedPolicy)
            TransformSpec.ToNumber -> toNumber(input)
            TransformSpec.ToInteger -> toInteger(input)
            is TransformSpec.Round -> round(input, spec.decimals, spec.mode)
            is TransformSpec.AgeFrom -> ageFrom(input, spec.unit, context.asOf)
            is TransformSpec.FormatDate -> formatDate(input, spec.pattern)
            is TransformSpec.Truncate -> truncate(input, spec.maxLength)
            is TransformSpec.BooleanAs -> booleanAs(input, spec.trueCode, spec.falseCode)
            is TransformSpec.Presence -> presence(input, spec.trueCode)
        }

    // ─── Individual transforms ────────────────────────────────────────────────

    private fun trim(input: String): TransformResult {
        val trimmed = input.trim()
        return if (trimmed.isEmpty()) TransformResult.Empty else TransformResult.Success(trimmed)
    }

    private fun scale(input: String, factor: Double, offset: Double): TransformResult {
        val value = input.trim().toDoubleOrNull()
            ?: return TransformResult.Failure(
                FailReason.TYPE_MISMATCH,
                "Cannot scale non-numeric value '$input'",
            )
        return TransformResult.Success(formatNumber(value * factor + offset))
    }

    /**
     * Translates an option code from one option set's vocabulary into another's.
     *
     * Matching is by exact code only — never by display name. Name matching is how failure mode F2
     * ("1" meaning Male in one program and Positive in another) slips through, because two unrelated
     * option sets very often have coincidentally similar labels.
     */
    private fun translateOptions(
        input: String,
        map: Map<String, String>,
        unmatched: UnmatchedPolicy,
    ): TransformResult {
        val code = input.trim()
        if (code.isEmpty()) return TransformResult.Empty

        map[code]?.let { target ->
            // The right-hand side names either a code or an outcome. An outcome is how a program
            // says "there is no code for this here", per value rather than for the whole map.
            return when (OptionAction.normalise(target)) {
                OptionAction.BLANK -> TransformResult.Empty
                OptionAction.CLEAR -> TransformResult.Clear
                OptionAction.FAIL -> TransformResult.Failure(
                    FailReason.UNMAPPED_OPTION,
                    "Option code '$code' is deliberately not carried into this program",
                )
                else -> TransformResult.Success(target)
            }
        }

        return when (unmatched) {
            UnmatchedPolicy.SKIP -> TransformResult.Empty
            UnmatchedPolicy.FAIL -> TransformResult.Failure(
                FailReason.UNMAPPED_OPTION,
                "Option code '$code' has no mapping (known: ${map.keys.sorted().joinToString()})",
            )
        }
    }

    private fun toNumber(input: String): TransformResult {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return TransformResult.Empty
        val value = trimmed.toDoubleOrNull()
            ?: return TransformResult.Failure(
                FailReason.TYPE_MISMATCH,
                "'$trimmed' is not a number",
            )
        return TransformResult.Success(formatNumber(value))
    }

    /**
     * Strict integer conversion. `"12.5"` fails rather than truncating to `12` — silently dropping
     * the fractional part of a clinical measurement is failure mode F1.
     */
    private fun toInteger(input: String): TransformResult {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return TransformResult.Empty

        trimmed.toLongOrNull()?.let { return TransformResult.Success(it.toString()) }

        val asDouble = trimmed.toDoubleOrNull()
            ?: return TransformResult.Failure(FailReason.TYPE_MISMATCH, "'$trimmed' is not a number")

        return if (asDouble % 1.0 == 0.0) {
            TransformResult.Success(asDouble.toLong().toString())
        } else {
            TransformResult.Failure(
                FailReason.LOSSY_CONVERSION,
                "'$trimmed' would lose precision as an integer; add an explicit ROUND transform to accept this",
            )
        }
    }

    private fun round(input: String, decimals: Int, mode: RoundMode): TransformResult {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return TransformResult.Empty
        val value = trimmed.toBigDecimalOrNull()
            ?: return TransformResult.Failure(FailReason.TYPE_MISMATCH, "'$trimmed' is not a number")

        val rounding = when (mode) {
            RoundMode.HALF_UP -> RoundingMode.HALF_UP
            RoundMode.FLOOR -> RoundingMode.FLOOR
            RoundMode.CEILING -> RoundingMode.CEILING
        }
        val scaled = value.setScale(decimals.coerceAtLeast(0), rounding)
        return TransformResult.Success(
            if (decimals <= 0) scaled.toBigInteger().toString() else scaled.stripTrailingZeros().toPlainString(),
        )
    }

    /**
     * Derives an age from a date. Inherently lossy in reverse, which is why a binding carrying this
     * derivation defaults to WRITE_ONLY (failure mode F6) — see `ConfigValidator`.
     */
    private fun ageFrom(input: String, unit: AgeUnit, asOf: LocalDate): TransformResult {
        val date = parseDate(input)
            ?: return TransformResult.Failure(
                FailReason.TYPE_MISMATCH,
                "'$input' is not a date; cannot derive an age from it",
            )
        if (date.isAfter(asOf)) {
            return TransformResult.Failure(
                FailReason.TYPE_MISMATCH,
                "Date '$input' is after the reference date $asOf; a negative age is never valid data",
            )
        }
        val amount = when (unit) {
            AgeUnit.YEARS -> Period.between(date, asOf).years.toLong()
            AgeUnit.MONTHS -> ChronoUnit.MONTHS.between(date, asOf)
            AgeUnit.DAYS -> ChronoUnit.DAYS.between(date, asOf)
        }
        return TransformResult.Success(amount.toString())
    }

    private fun formatDate(input: String, pattern: String): TransformResult {
        val date = parseDate(input)
            ?: return TransformResult.Failure(FailReason.TYPE_MISMATCH, "'$input' is not a date")
        return try {
            TransformResult.Success(
                date.format(java.time.format.DateTimeFormatter.ofPattern(pattern)),
            )
        } catch (e: IllegalArgumentException) {
            TransformResult.Failure(FailReason.INVALID_CONFIG, "Invalid date pattern '$pattern': ${e.message}")
        }
    }

    private fun truncate(input: String, maxLength: Int): TransformResult {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return TransformResult.Empty
        return TransformResult.Success(if (trimmed.length <= maxLength) trimmed else trimmed.take(maxLength))
    }

    private fun booleanAs(input: String, trueCode: String, falseCode: String): TransformResult {
        val normalised = input.trim().lowercase()
        if (normalised.isEmpty()) return TransformResult.Empty

        // Either side may name an outcome rather than a code. That is what lets a boolean write into
        // a TRUE_ONLY field, where the only way to record "no" is to remove the tick.
        fun encode(code: String): TransformResult = when (OptionAction.normalise(code)) {
            OptionAction.BLANK -> TransformResult.Empty
            OptionAction.CLEAR -> TransformResult.Clear
            OptionAction.FAIL -> TransformResult.Failure(
                FailReason.UNMAPPED_OPTION,
                "'$input' is deliberately not carried into this program",
            )
            else -> TransformResult.Success(code)
        }

        return when (normalised) {
            "true", "1", "yes", "y" -> encode(trueCode)
            "false", "0", "no", "n" -> encode(falseCode)
            else -> TransformResult.Failure(
                FailReason.TYPE_MISMATCH,
                "'$input' is not recognisably boolean",
            )
        }
    }

    /**
     * Asserts that something happened, on the evidence that a value for it exists.
     *
     * Returns [TransformResult.Empty] rather than a false code for a blank input: "no date recorded in
     * the source" is not evidence that the event did not occur, and writing a definite "no" on that
     * basis would fabricate a clinical finding.
     */
    private fun presence(input: String, trueCode: String): TransformResult =
        if (input.isBlank()) TransformResult.Empty else TransformResult.Success(trueCode)

    // ─── Shared helpers ──────────────────────────────────────────────────────

    /**
     * Parses the date shapes the SDK actually produces: a plain `yyyy-MM-dd`, or an ISO datetime
     * whose date part we take. Strict — `2024-02-31` is rejected rather than rolled over to March.
     */
    fun parseDate(raw: String): LocalDate? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        val datePart = trimmed.substringBefore('T').trim()
        return try {
            LocalDate.parse(datePart)
        } catch (e: DateTimeParseException) {
            null
        }
    }

    /** Renders a double without a gratuitous trailing `.0`, so integers stay integral in the payload. */
    fun formatNumber(value: Double): String {
        if (value.isNaN() || value.isInfinite()) return value.toString()
        return if (value % 1.0 == 0.0 && kotlin.math.abs(value) < 1e15) {
            value.toLong().toString()
        } else {
            BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()
        }
    }
}
