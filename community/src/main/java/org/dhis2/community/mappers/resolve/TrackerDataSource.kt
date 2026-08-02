package org.dhis2.community.mappers.resolve

/** An enrollment, reduced to what mapping needs. */
data class EnrollmentRef(
    val uid: String,
    val programUid: String,
    val orgUnitUid: String?,
    val enrollmentDate: Long?,
    val incidentDate: Long?,
    val status: String?,
    val lastUpdated: Long?,
)

/**
 * An event plus its data values.
 *
 * Values are carried inline because event *selection* depends on them — "the latest event that
 * actually has a value" and "the visit where visitType == ANC1" are both value predicates, so
 * deciding which event wins cannot be separated from reading it.
 */
data class EventRef(
    val uid: String,
    val enrollmentUid: String?,
    val programStageUid: String?,
    val eventDate: Long?,
    val dueDate: Long?,
    val completedDate: Long? = null,
    val created: Long?,
    val lastUpdated: Long?,
    val status: String?,
    val values: Map<String, String>,
) {
    /** Ordering key: event date when present, else creation, else epoch. Never null, so sorts are total. */
    val chronology: Long get() = eventDate ?: created ?: 0L

    fun valueOf(dataElementUid: String): String? =
        values[dataElementUid]?.takeIf { it.isNotBlank() }
}

/** A value read from the tracker, with the timestamp used by OVERWRITE_IF_SOURCE_NEWER. */
data class StoredValue(
    val value: String?,
    val lastUpdated: Long? = null,
) {
    val isPresent: Boolean get() = !value.isNullOrBlank()
}

/**
 * The tracker reads and writes mapping needs.
 *
 * Abstracted from D2 so the engine's planning half can be driven by fakes in plain JUnit tests — the
 * community module has no mocking framework, so testability has to come from the seams.
 */
interface TrackerDataSource {

    fun attributeValue(teiUid: String, attributeUid: String): StoredValue

    fun enrollments(teiUid: String, programUid: String): List<EnrollmentRef>

    /** Events of [stageUid] within any of [enrollmentUids], with their data values. */
    fun events(enrollmentUids: List<String>, stageUid: String): List<EventRef>

    fun event(eventUid: String): EventRef?

    fun dataValue(eventUid: String, dataElementUid: String): StoredValue

    // ─── Writes ──────────────────────────────────────────────────────────────

    /** A null [value] removes the stored value. */
    fun setAttributeValue(teiUid: String, attributeUid: String, value: String?)

    /** A null [value] removes the stored value. */
    fun setDataValue(eventUid: String, dataElementUid: String, value: String?)

    /** Creates an event for [stageUid] in [enrollmentUid], used only by TargetEventPolicy.CREATE_IF_MISSING. */
    fun createEvent(
        enrollmentUid: String,
        programUid: String,
        stageUid: String,
        teiUid: String,
        orgUnitUid: String,
    ): String
}
