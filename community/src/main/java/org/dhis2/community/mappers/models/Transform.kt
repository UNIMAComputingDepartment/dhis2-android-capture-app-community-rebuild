package org.dhis2.community.mappers.models

sealed interface TransformSpec {
    object Trim : TransformSpec
    data class ScaleUnit(val factor: Double, val offset: Double = 0.0) : TransformSpec
    data class TranslateOptions(
        val map: Map<String, String>,
        val unmatchedPolicy: UnmatchedPolicy = UnmatchedPolicy.SKIP,
    ) : TransformSpec
    object ToNumber : TransformSpec
    object ToInteger : TransformSpec
    data class Round(val decimals: Int, val mode: RoundMode = RoundMode.HALF_UP) : TransformSpec
    data class AgeFrom(val unit: AgeUnit, val asOf: String) : TransformSpec
    data class FormatDate(val pattern: String) : TransformSpec
    data class Truncate(val maxLength: Int) : TransformSpec
    data class BooleanAs(val trueCode: String, val falseCode: String) : TransformSpec

    /**
     * Collapses "there is a value" into [trueCode]. A blank input yields no value rather than a
     * negative, so this can assert that something happened but never that it did not.
     */
    data class Presence(val trueCode: String) : TransformSpec
}

enum class UnmatchedPolicy { SKIP, FAIL }

/**
 * Outcomes an option map may name on the right-hand side, instead of a code.
 *
 * Programs do not merely encode the same fact differently — they disagree about which findings can
 * be expressed at all. A program with no "unknown" code has three honest responses to an unknown
 * result: record it as something else, record nothing, or report that it could not be carried. Only
 * the first is a code, so the other two need names.
 *
 * Reserved rather than structured because the map stays `Map<String, String>`, which keeps the simple
 * case simple in both JSON and the form. The `@` prefix is not a legal DHIS2 option code, and
 * [ConfigValidator] warns if an option set somehow contains one anyway.
 */
object OptionAction {
    /**
     * Write nothing and leave the target as it stands.
     *
     * Not the same as clearing it: a mapping never erases a value it did not put there, which is the
     * same rule that stops a blank source overwriting real data.
     */
    const val BLANK = "@BLANK"

    /** Report that this value could not be carried, even where the binding otherwise skips. */
    const val FAIL = "@FAIL"

    /**
     * Remove whatever the target holds.
     *
     * Distinct from [BLANK], which leaves the field as it stands. Clearing exists because some fields
     * have no way to say "no": a DHIS2 TRUE_ONLY field holds a tick or nothing at all, so absence
     * *is* the negative. Without this, a box ticked from a source that later reads false would stay
     * ticked for ever — the mapping would be unable to correct data it had itself written.
     *
     * It is still a write, and passes through [org.dhis2.community.mappers.write.WriteGate] like any
     * other: the manual-edit guard holds, and the conflict policy must permit overwriting. Under the
     * default SKIP_IF_PRESENT a clear does nothing, which the validator warns about.
     */
    const val CLEAR = "@CLEAR"

    val all = listOf(BLANK, FAIL, CLEAR)

    /**
     * The action [value] names, or null if it names a code.
     *
     * Case-insensitive: an author hand-editing the JSON who writes `@clear` means the action, and
     * matching only the exact casing would quietly treat it as a literal option code instead — the
     * silent misinterpretation this whole layer exists to prevent. No DHIS2 option code may contain
     * `@`, so nothing legitimate is shadowed.
     */
    fun normalise(value: String?): String? =
        value?.trim()?.uppercase()?.takeIf { it in all }

    fun isAction(value: String?) = normalise(value) != null
}

enum class RoundMode { HALF_UP, FLOOR, CEILING }

enum class AgeUnit { YEARS, MONTHS, DAYS }
