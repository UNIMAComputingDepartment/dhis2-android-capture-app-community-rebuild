package org.dhis2.community.mappers.models

/**
 * What the DHIS2 metadata says about a field, as read from the SDK at runtime.
 *
 * Principle 2 of the design: metadata is the source of truth for types. Config never declares a
 * value type — it declares *meaning* (unit, option coding, concept) — so a config that disagrees
 * with the server's metadata is a validation error rather than a silent mis-conversion.
 */
data class FieldMeta(
    val uid: String,
    val name: String,
    val valueType: DhisValueType,
    val optionSetUid: String? = null,
    /** Codes of the field's option set, empty when it has none. */
    val optionCodes: List<String> = emptyList(),
) {
    val hasOptionSet: Boolean get() = optionSetUid != null && optionCodes.isNotEmpty()
}

/**
 * Mirror of the DHIS2 `ValueType` enum.
 *
 * Deliberately duplicated rather than reusing the SDK enum so the transform and validation layers —
 * the whole correctness surface — stay free of SDK types and testable with plain JUnit. Mapping from
 * the SDK enum happens once, at the [org.dhis2.community.mappers.config.D2MetadataLookup] boundary.
 */
enum class DhisValueType {
    TEXT,
    LONG_TEXT,
    LETTER,
    PHONE_NUMBER,
    EMAIL,
    BOOLEAN,
    TRUE_ONLY,
    DATE,
    DATETIME,
    TIME,
    NUMBER,
    UNIT_INTERVAL,
    PERCENTAGE,
    INTEGER,
    INTEGER_POSITIVE,
    INTEGER_NEGATIVE,
    INTEGER_ZERO_OR_POSITIVE,
    ORGANISATION_UNIT,
    AGE,
    URL,
    USERNAME,
    MULTI_TEXT,

    /** Types that carry binary or structured payloads, plus anything the SDK adds later. */
    UNSUPPORTED,
    ;

    val isNumeric: Boolean
        get() = this in setOf(
            NUMBER, UNIT_INTERVAL, PERCENTAGE,
            INTEGER, INTEGER_POSITIVE, INTEGER_NEGATIVE, INTEGER_ZERO_OR_POSITIVE,
        )

    val isInteger: Boolean
        get() = this in setOf(INTEGER, INTEGER_POSITIVE, INTEGER_NEGATIVE, INTEGER_ZERO_OR_POSITIVE)

    /** DHIS2 stores AGE as a date, so it groups with the date types. */
    val isDateLike: Boolean get() = this in setOf(DATE, DATETIME, AGE)

    val isBooleanLike: Boolean get() = this in setOf(BOOLEAN, TRUE_ONLY)

    val isTextLike: Boolean
        get() = this in setOf(TEXT, LONG_TEXT, LETTER, PHONE_NUMBER, EMAIL, URL, USERNAME, MULTI_TEXT)

    /**
     * Types this engine refuses to map. File resources, images, coordinates and org-unit references
     * are not values that can be meaningfully carried between programs by string transformation, and
     * attempting it would produce a payload the server rejects — or worse, accepts as nonsense.
     */
    val isMappable: Boolean
        get() = this != UNSUPPORTED && this != ORGANISATION_UNIT

    /** Integer subtypes constrain sign; used to reject a value the server would refuse. */
    fun permits(value: Double): Boolean = when (this) {
        INTEGER_POSITIVE -> value > 0
        INTEGER_NEGATIVE -> value < 0
        INTEGER_ZERO_OR_POSITIVE -> value >= 0
        UNIT_INTERVAL -> value in 0.0..1.0
        PERCENTAGE -> value in 0.0..100.0
        else -> true
    }
}
