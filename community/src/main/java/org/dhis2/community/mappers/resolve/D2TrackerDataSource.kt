package org.dhis2.community.mappers.resolve

import org.hisp.dhis.android.core.D2
import org.hisp.dhis.android.core.event.EventCreateProjection
import org.hisp.dhis.android.core.event.EventStatus
import java.util.Date

/** D2-backed [TrackerDataSource]. The only place the mapping engine touches the SDK's data modules. */
class D2TrackerDataSource(private val d2: D2) : TrackerDataSource {

    override fun attributeValue(teiUid: String, attributeUid: String): StoredValue {
        val stored = d2.trackedEntityModule().trackedEntityAttributeValues()
            .value(attributeUid, teiUid)
            .blockingGet()
        return StoredValue(stored?.value(), stored?.lastUpdated()?.time)
    }

    override fun enrollments(teiUid: String, programUid: String): List<EnrollmentRef> =
        d2.enrollmentModule().enrollments()
            .byTrackedEntityInstance().eq(teiUid)
            .byProgram().eq(programUid)
            .byDeleted().isFalse
            .blockingGet()
            .map { enrollment ->
                EnrollmentRef(
                    uid = enrollment.uid(),
                    programUid = enrollment.program() ?: programUid,
                    orgUnitUid = enrollment.organisationUnit(),
                    enrollmentDate = enrollment.enrollmentDate()?.time,
                    incidentDate = enrollment.incidentDate()?.time,
                    status = enrollment.status()?.name,
                    lastUpdated = enrollment.lastUpdated()?.time,
                )
            }

    override fun events(enrollmentUids: List<String>, stageUid: String): List<EventRef> {
        if (enrollmentUids.isEmpty()) return emptyList()
        return d2.eventModule().events()
            .byEnrollmentUid().`in`(enrollmentUids)
            .byProgramStageUid().eq(stageUid)
            .byDeleted().isFalse
            .withTrackedEntityDataValues()
            .blockingGet()
            .map { it.toEventRef() }
    }

    override fun event(eventUid: String): EventRef? =
        d2.eventModule().events()
            .byUid().eq(eventUid)
            .withTrackedEntityDataValues()
            .one()
            .blockingGet()
            ?.toEventRef()

    override fun dataValue(eventUid: String, dataElementUid: String): StoredValue {
        val stored = d2.trackedEntityModule().trackedEntityDataValues()
            .value(eventUid, dataElementUid)
            .blockingGet()
        return StoredValue(stored?.value(), stored?.lastUpdated()?.time)
    }

    override fun setAttributeValue(teiUid: String, attributeUid: String, value: String?) {
        d2.trackedEntityModule().trackedEntityAttributeValues()
            .value(attributeUid, teiUid)
            .blockingSet(value)
    }

    override fun setDataValue(eventUid: String, dataElementUid: String, value: String?) {
        d2.trackedEntityModule().trackedEntityDataValues()
            .value(eventUid, dataElementUid)
            .blockingSet(value)
    }

    override fun createEvent(
        enrollmentUid: String,
        programUid: String,
        stageUid: String,
        teiUid: String,
        orgUnitUid: String,
    ): String {
        val eventUid = d2.eventModule().events().blockingAdd(
            EventCreateProjection.builder()
                .enrollment(enrollmentUid)
                .program(programUid)
                .programStage(stageUid)
                .organisationUnit(orgUnitUid)
                .build(),
        )
        d2.eventModule().events().uid(eventUid).setEventDate(Date())
        d2.eventModule().events().uid(eventUid).setStatus(EventStatus.ACTIVE)
        return eventUid
    }

    private fun org.hisp.dhis.android.core.event.Event.toEventRef() = EventRef(
        uid = uid(),
        enrollmentUid = enrollment(),
        programStageUid = programStage(),
        eventDate = eventDate()?.time,
        dueDate = dueDate()?.time,
        completedDate = completedDate()?.time,
        created = created()?.time,
        lastUpdated = lastUpdated()?.time,
        status = status()?.name,
        values = trackedEntityDataValues()
            ?.mapNotNull { dataValue ->
                val uid = dataValue.dataElement()
                val value = dataValue.value()
                if (!uid.isNullOrBlank() && value != null) uid to value else null
            }
            ?.toMap()
            .orEmpty(),
    )
}
