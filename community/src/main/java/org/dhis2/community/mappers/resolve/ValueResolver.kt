package org.dhis2.community.mappers.resolve

import org.dhis2.community.mappers.models.EnrollmentScope
import org.dhis2.community.mappers.models.EventPropertyName
import org.dhis2.community.mappers.models.TriggerContext
import org.dhis2.community.mappers.models.ValueAddress
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** A value read from a [ValueAddress], with the event it came from for diagnostics. */
data class ResolvedValue(
    val value: String?,
    val lastUpdated: Long? = null,
    val eventUid: String? = null,
) {
    val isPresent: Boolean get() = !value.isNullOrBlank()

    companion object {
        val ABSENT = ResolvedValue(null)
    }
}

/**
 * Reads a [ValueAddress] for one TEI. Read-only by construction and stateless — this is the whole of
 * the engine's planning half that touches the tracker, which is what lets `plan()` be free of side
 * effects and safe to call from anywhere.
 */
class ValueResolver(private val data: TrackerDataSource) {

    fun resolve(address: ValueAddress, context: TriggerContext): ResolvedValue = when (address) {
        is ValueAddress.Constant -> ResolvedValue(address.value)

        is ValueAddress.Attribute -> data.attributeValue(context.teiUid, address.uid).let {
            ResolvedValue(it.value, it.lastUpdated)
        }

        // Attributes and enrollment properties carry no event selector, so they read the most recent
        // enrollment of the program.
        is ValueAddress.EnrollmentProperty -> resolveEnrollmentProperty(address, context)

        is ValueAddress.DataElement -> resolveDataElement(address, context)

        is ValueAddress.EventProperty -> resolveEventProperty(address, context)
    }

    /**
     * Reads a date off the event itself.
     *
     * The event is chosen by the same [EventPicker] rules as a data element, so a `where` condition can
     * express "the date of the visit at which X was recorded" — the date is the event's, not any
     * field's. [Pick] variants that filter on having a value are meaningless here (the property is not
     * a data element), so no data element uid is passed to the picker.
     */
    private fun resolveEventProperty(
        address: ValueAddress.EventProperty,
        context: TriggerContext,
    ): ResolvedValue {
        val enrollments = scopedEnrollments(address.programUid, address.event.enrollment, context)
        if (enrollments.isEmpty()) return ResolvedValue.ABSENT

        val chosen = EventPicker.pick(
            candidates = data.events(enrollments.map { it.uid }, address.stageUid),
            selector = address.event,
            dataElementUid = null,
            triggeringEventUid = context.sourceEventUid,
        ) ?: return ResolvedValue.ABSENT

        val value = when (address.property.uppercase()) {
            EventPropertyName.EVENT_DATE -> chosen.eventDate?.let(::formatDate)
            EventPropertyName.DUE_DATE -> chosen.dueDate?.let(::formatDate)
            EventPropertyName.COMPLETED_DATE -> chosen.completedDate?.let(::formatDate)
            else -> null
        }

        return ResolvedValue(value, chosen.lastUpdated ?: chosen.created, chosen.uid)
    }

    private fun resolveEnrollmentProperty(
        address: ValueAddress.EnrollmentProperty,
        context: TriggerContext,
    ): ResolvedValue {
        val enrollment = scopedEnrollments(address.programUid, EnrollmentScope.MOST_RECENT, context)
            .lastOrNull()
            ?: return ResolvedValue.ABSENT

        val value = when (address.property.uppercase()) {
            "ENROLLMENT_DATE", "ENROLLMENTDATE" -> enrollment.enrollmentDate?.let(::formatDate)
            "INCIDENT_DATE", "INCIDENTDATE" -> enrollment.incidentDate?.let(::formatDate)
            "ORG_UNIT", "ORGUNIT" -> enrollment.orgUnitUid
            "STATUS" -> enrollment.status
            else -> null
        }
        return ResolvedValue(value, enrollment.lastUpdated)
    }

    private fun resolveDataElement(
        address: ValueAddress.DataElement,
        context: TriggerContext,
    ): ResolvedValue {
        val enrollments = scopedEnrollments(address.programUid, address.event.enrollment, context)
        if (enrollments.isEmpty()) return ResolvedValue.ABSENT

        val chosen = EventPicker.pick(
            candidates = data.events(enrollments.map { it.uid }, address.stageUid),
            selector = address.event,
            dataElementUid = address.uid,
            triggeringEventUid = context.sourceEventUid,
        ) ?: return ResolvedValue.ABSENT

        return ResolvedValue(
            value = chosen.valueOf(address.uid),
            lastUpdated = chosen.lastUpdated ?: chosen.created,
            eventUid = chosen.uid,
        )
    }

    /**
     * The enrollments an address may read from, oldest first, narrowed by [scope].
     *
     * [EnrollmentScope.TRIGGERING] and [EnrollmentScope.ACTIVE] fall back to the most recent
     * enrollment when they would otherwise match nothing, so a config written for the event-save
     * trigger still resolves under the enrollment-created trigger instead of silently going empty.
     */
    private fun scopedEnrollments(
        programUid: String,
        scope: EnrollmentScope,
        context: TriggerContext,
    ): List<EnrollmentRef> {
        val all = data.enrollments(context.teiUid, programUid)
            .sortedWith(compareBy({ it.enrollmentDate ?: 0L }, { it.uid }))
        if (all.isEmpty()) return emptyList()

        return when (scope) {
            EnrollmentScope.ALL -> all

            EnrollmentScope.ACTIVE -> all
                .filter { it.status.equals("ACTIVE", ignoreCase = true) }
                .ifEmpty { all.takeLast(1) }

            EnrollmentScope.TRIGGERING -> context.sourceEnrollmentUid
                ?.let { uid -> all.filter { it.uid == uid } }
                ?.ifEmpty { all.takeLast(1) }
                ?: all.takeLast(1)

            EnrollmentScope.MOST_RECENT -> all.takeLast(1)
        }
    }

    private fun formatDate(epochMillis: Long): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(epochMillis))
}
