package org.dhis2.community.mappers.resolve

import org.dhis2.community.mappers.models.ConditionOp
import org.dhis2.community.mappers.models.EventCondition
import org.dhis2.community.mappers.models.EventSelector
import org.dhis2.community.mappers.models.Pick

/**
 * Chooses which event a stage-scoped value is read from — failure modes F4 ("wrong event chosen") and
 * F5 ("latest event has a blank value").
 *
 * Entirely pure: candidates in, one event out. Every judgement about *which incident* a value belongs
 * to is therefore unit-testable without a database, which matters because picking the wrong event
 * produces data that looks perfectly plausible and is simply untrue.
 */
object EventPicker {

    /**
     * @param candidates events of the relevant stage, in any order
     * @param dataElementUid the field being read; drives the `*_WITH_VALUE` picks
     * @param triggeringEventUid the event that fired this run, if any
     */
    fun pick(
        candidates: List<EventRef>,
        selector: EventSelector,
        dataElementUid: String?,
        triggeringEventUid: String? = null,
    ): EventRef? {
        val qualifying = candidates
            .filter { event -> selector.where.all { matches(event, it) } }
            .sortedWith(chronological)

        if (qualifying.isEmpty()) return null

        fun hasValue(event: EventRef): Boolean =
            dataElementUid == null || event.valueOf(dataElementUid) != null

        return when (selector.select) {
            Pick.TRIGGERING -> triggeringEventUid?.let { uid -> qualifying.firstOrNull { it.uid == uid } }

            Pick.LATEST -> qualifying.lastOrNull()

            Pick.LATEST_WITH_VALUE -> qualifying.lastOrNull(::hasValue)

            Pick.FIRST -> qualifying.firstOrNull()

            Pick.FIRST_WITH_VALUE -> qualifying.firstOrNull(::hasValue)

            Pick.NTH -> qualifying.getOrNull(selector.index)
        }
    }

    /**
     * Total ordering, oldest first.
     *
     * Ties are broken by lastUpdated and then uid rather than left to the SDK's row order, so the same
     * data always yields the same choice. A selection that silently depends on query order is a
     * wrong-data bug that only shows up on someone else's device.
     */
    private val chronological: Comparator<EventRef> = compareBy(
        { it.chronology },
        { it.lastUpdated ?: 0L },
        { it.uid },
    )

    private fun matches(event: EventRef, condition: EventCondition): Boolean {
        val actual = event.values[condition.dataElement]?.trim()
        val expected = condition.value.trim()

        return when (condition.op) {
            ConditionOp.IS_BLANK -> actual.isNullOrEmpty()
            ConditionOp.IS_NOT_BLANK -> !actual.isNullOrEmpty()

            ConditionOp.IS_TRUE -> asBoolean(actual) == true
            ConditionOp.IS_FALSE -> asBoolean(actual) == false

            ConditionOp.EQUALS -> actual == expected
            // A missing value is not "not equal to X" — it is unknown. Treating absence as a match
            // would silently widen the filter to every event that never recorded the field.
            ConditionOp.NOT_EQUALS -> actual != null && actual != expected

            ConditionOp.CONTAINS -> actual != null && actual.contains(expected, ignoreCase = true)
            ConditionOp.NOT_CONTAINS -> actual != null && !actual.contains(expected, ignoreCase = true)

            ConditionOp.GREATER_THAN -> compareNumbers(actual, expected) { a, b -> a > b }
            ConditionOp.LESS_THAN -> compareNumbers(actual, expected) { a, b -> a < b }
        }
    }

    /** Recognises the encodings DHIS2 uses for boolean values; null when the value is not boolean-ish. */
    private fun asBoolean(raw: String?): Boolean? = when (raw?.trim()?.lowercase()) {
        "true", "1", "yes", "y" -> true
        "false", "0", "no", "n" -> false
        else -> null
    }

    private inline fun compareNumbers(
        actual: String?,
        expected: String,
        compare: (Double, Double) -> Boolean,
    ): Boolean {
        val a = actual?.toDoubleOrNull() ?: return false
        val b = expected.toDoubleOrNull() ?: return false
        return compare(a, b)
    }
}
