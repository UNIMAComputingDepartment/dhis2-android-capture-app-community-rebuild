package org.dhis2.community.mappers.models

sealed interface ValueAddress {
    data class Attribute(val uid: String) : ValueAddress
    data class DataElement(
        val programUid: String,
        val stageUid: String,
        val uid: String,
        val event: EventSelector,
    ) : ValueAddress
    data class EnrollmentProperty(val programUid: String, val property: String) : ValueAddress

    /**
     * A property of an event rather than a value inside it — currently its date.
     *
     * Exists because "when did this happen" is often only recorded as *the date of the visit at which
     * it was recorded*. A program may capture "BCG given" as a yes/no on a visit while another
     * program wants the date it was given; the date is not in any data element, it is the event's
     * own date. Combined with an [EventSelector] `where` condition this expresses "the date of the
     * visit where BCG was marked given" without inventing a value.
     *
     * Source-only: writing an event date would move a clinical record in time, which is not something
     * a field mapping should do silently.
     */
    data class EventProperty(
        val programUid: String,
        val stageUid: String,
        val property: String,
        val event: EventSelector = EventSelector(),
    ) : ValueAddress

    data class Constant(val value: String) : ValueAddress

    fun toAddressString(): String = when (this) {
        is Attribute -> "attr:$uid"
        is DataElement -> "de:$programUid:$stageUid:$uid:${event.select}"
        is EnrollmentProperty -> "enrollment:$programUid:$property"
        is EventProperty -> "event:$programUid:$stageUid:$property:${event.select}"
        is Constant -> "const:$value"
    }
}

/** Event properties readable as a mapping source. */
object EventPropertyName {
    const val EVENT_DATE = "EVENT_DATE"
    const val DUE_DATE = "DUE_DATE"
    const val COMPLETED_DATE = "COMPLETED_DATE"

    val all = listOf(EVENT_DATE, DUE_DATE, COMPLETED_DATE)
}

/**
 * Which event to read a stage-scoped value from — the configurable answer to "the data might be of
 * different events/incidents" (failure modes F4 and F5).
 *
 * Three independent questions: which enrollment(s) to look in ([enrollment]), which events qualify
 * ([where]), and which qualifying event wins ([select]).
 */
data class EventSelector(
    val select: Pick = Pick.LATEST_WITH_VALUE,
    val where: List<EventCondition> = emptyList(),
    val enrollment: EnrollmentScope = EnrollmentScope.MOST_RECENT,
    /** 0-based, used only by [Pick.NTH]. */
    val index: Int = 0,
)

enum class Pick {
    /** The event that fired this run. Zero ambiguity — prefer it whenever the trigger supplies one. */
    TRIGGERING,

    /** Newest event, whether or not it holds a value. */
    LATEST,

    /**
     * Newest event that actually holds a non-blank value. The default: on a repeatable stage the
     * newest event is often partially filled, and carrying its blank forward would erase good data
     * downstream (F5).
     */
    LATEST_WITH_VALUE,

    FIRST,
    FIRST_WITH_VALUE,

    /** The [EventSelector.index]-th qualifying event in chronological order. */
    NTH,
}

data class EventCondition(
    val dataElement: String,
    val op: ConditionOp,
    val value: String,
)

enum class ConditionOp {
    EQUALS,
    NOT_EQUALS,
    CONTAINS,
    NOT_CONTAINS,
    GREATER_THAN,
    LESS_THAN,
    IS_BLANK,
    IS_NOT_BLANK,

    /**
     * Matches a boolean-ish value regardless of how it is encoded (`true`/`1`/`yes`).
     *
     * Preferred over `EQUALS "true"` for BOOLEAN and TRUE_ONLY fields: a strict string compare against
     * the wrong encoding matches nothing, which shows up as a mapping that silently never fires rather
     * than as an error. Explicit ops beat making EQUALS quietly fuzzy.
     */
    IS_TRUE,
    IS_FALSE,
}

enum class AddressKind {
    ATTRIBUTE,
    DATA_ELEMENT,
    ENROLLMENT_PROPERTY,
    EVENT_PROPERTY,
    CONSTANT,
}

enum class EnrollmentScope {
    MOST_RECENT,
    ALL,
    ACTIVE,
    TRIGGERING,
}
