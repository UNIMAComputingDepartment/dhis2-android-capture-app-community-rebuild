package org.dhis2.community.enrollmentfilters.models

import org.hisp.dhis.android.core.common.ValueType

/**
 * How a single filterable attribute is presented and evaluated. Derived from the attribute's
 * [ValueType] (see [FilterWidget.forAttribute]) unless overridden in the datastore config.
 *
 * KEYWORD / OPTIONS / BOOLEAN are pushed into the SDK query (eq/like/in). NUMBER_RANGE /
 * AGE_RANGE / DATE_RANGE are evaluated client-side because the SDK's public search API does not
 * expose range operators on attributes.
 */
enum class FilterWidget {
    KEYWORD,
    OPTIONS,
    BOOLEAN,
    NUMBER_RANGE,
    AGE_RANGE,
    DATE_RANGE,
    ;

    val isRange: Boolean
        get() = this == NUMBER_RANGE || this == AGE_RANGE || this == DATE_RANGE

    companion object {
        /**
         * Default widget for an attribute. An attribute backed by an option set is always OPTIONS
         * regardless of value type. Returns null for value types we can't meaningfully filter.
         */
        fun forAttribute(valueType: ValueType?, hasOptionSet: Boolean): FilterWidget? {
            if (hasOptionSet) return OPTIONS
            return when (valueType) {
                ValueType.NUMBER,
                ValueType.UNIT_INTERVAL,
                ValueType.PERCENTAGE,
                ValueType.INTEGER,
                ValueType.INTEGER_POSITIVE,
                ValueType.INTEGER_NEGATIVE,
                ValueType.INTEGER_ZERO_OR_POSITIVE,
                -> NUMBER_RANGE

                ValueType.AGE -> AGE_RANGE

                ValueType.DATE,
                ValueType.DATETIME,
                -> DATE_RANGE

                ValueType.BOOLEAN,
                ValueType.TRUE_ONLY,
                -> BOOLEAN

                ValueType.TEXT,
                ValueType.LONG_TEXT,
                ValueType.LETTER,
                ValueType.PHONE_NUMBER,
                ValueType.EMAIL,
                ValueType.USERNAME,
                ValueType.URL,
                -> KEYWORD

                else -> null
            }
        }

        fun fromOverride(name: String?): FilterWidget? =
            name?.trim()?.uppercase()?.let { raw ->
                entries.firstOrNull { it.name == raw }
            }
    }
}

/** A single option of an option-set-backed attribute. Attribute values store the option [code]. */
data class FilterOption(
    val code: String,
    val label: String,
)

/** A program attribute that can be filtered, fully resolved and ready to render/evaluate. */
data class FilterableAttribute(
    val uid: String,
    val label: String,
    val widget: FilterWidget,
    val valueType: ValueType?,
    val optionSetUid: String? = null,
    val options: List<FilterOption> = emptyList(),
)

/**
 * The current selection for one attribute. Each variant knows whether it is [isActive]; an inactive
 * constraint (e.g. empty keyword, both range bounds null) is treated as "no filter".
 */
sealed interface AttributeConstraint {
    fun isActive(): Boolean

    data class Keyword(val text: String) : AttributeConstraint {
        override fun isActive() = text.isNotBlank()
    }

    data class Options(val codes: Set<String>) : AttributeConstraint {
        override fun isActive() = codes.isNotEmpty()
    }

    data class Bool(val value: Boolean?) : AttributeConstraint {
        override fun isActive() = value != null
    }

    data class NumberRange(val min: Double?, val max: Double?) : AttributeConstraint {
        override fun isActive() = min != null || max != null
    }

    data class AgeRange(val minYears: Int?, val maxYears: Int?) : AttributeConstraint {
        override fun isActive() = minYears != null || maxYears != null
    }

    /** Bounds are inclusive ISO dates (yyyy-MM-dd). */
    data class DateRange(val from: String?, val to: String?) : AttributeConstraint {
        override fun isActive() = !from.isNullOrBlank() || !to.isNullOrBlank()
    }
}
