package org.dhis2.community.medicalHistory.repository

import android.os.Build
import androidx.annotation.RequiresApi
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.dhis2.commons.bindings.dataElement
import org.dhis2.community.medicalHistory.models.MedicalHistoryConfig
import org.hisp.dhis.android.core.D2
import org.hisp.dhis.android.core.enrollment.EnrollmentStatus
import org.hisp.dhis.android.core.event.Event
import org.hisp.dhis.android.core.event.EventCreateProjection
import org.hisp.dhis.android.core.event.EventStatus
import java.time.LocalDate
import java.time.ZoneId
import java.util.Calendar
import java.util.Date

class MHRepository(
    private val d2: D2
) {
    fun getMedicalHistoryConfigs(): MedicalHistoryConfig {
        return try {
            val entries = d2.dataStoreModule()
                .dataStore()
                .byNamespace()
                .eq("community_redesign")
                .blockingGet()

            val entry = entries.firstOrNull { it.key() == "medicalHistory" }
            val rawValue = entry?.value()

            if (rawValue.isNullOrBlank()) {
                return MedicalHistoryConfig(
                    medicalHistoryConfig = emptyList(),
                    baseProgram = emptyList()
                )
            }

            val cleanedValue = when {
                rawValue.startsWith("JsonWrapper(json=") ->
                    rawValue.removePrefix("JsonWrapper(json=").removeSuffix(")")

                rawValue.startsWith("\"") ->
                    Gson().fromJson(rawValue, String::class.java)

                else -> rawValue
            }

            if (!cleanedValue.trim().startsWith("{")) {
                return MedicalHistoryConfig(
                    medicalHistoryConfig = emptyList(),
                    baseProgram = emptyList()
                )
            }

            val config = Gson().fromJson(cleanedValue, MedicalHistoryConfig::class.java)

            MedicalHistoryConfig(
                medicalHistoryConfig = config.medicalHistoryConfig,
                baseProgram = config.baseProgram
            )

        } catch (e: Exception) {
            e.printStackTrace()
            MedicalHistoryConfig(
                medicalHistoryConfig = emptyList(),
                baseProgram = emptyList()
            )
        }
    }

     fun updateSummaryValues(
        teiUid: String,
        baseProgramUid: String,
        summaries: Map<String, String>
    ) {

        val baseProgramStageUid =
            getMedicalHistoryConfigs().baseProgram.firstOrNull()
                ?.baseProgramStageUid

        val eventUid = getLatestEvent(
            teiUid = teiUid,
            programUid = baseProgramUid,
            programStageUid = baseProgramStageUid
        )?.uid().toString()

        summaries.forEach { (deUid, newValue) ->
            d2.trackedEntityModule().trackedEntityDataValues()
                .value(eventUid, deUid)
                .blockingSet(newValue)
        }
    }

    private fun getLatestEvent(
        teiUid: String,
        programUid: String,
        programStageUid: String? = null
    ): Event? {
        val enrollments = d2.enrollmentModule().enrollments()
            .byTrackedEntityInstance().eq(teiUid)
            .byProgram().eq(programUid)
            .blockingGet()

        if (enrollments.isEmpty()) return null

        val enrollmentUids = enrollments.map { it.uid() }

        val events = d2.eventModule().events()
            .byEnrollmentUid().`in`(enrollmentUids)
            .byProgramStageUid().eq(programStageUid)
            .blockingGet()
        return events
            .filter { it.eventDate() != null || it.created() != null }
            .maxByOrNull { it.eventDate() ?: it.created()!! }
    }

    fun getLatestValueFromProgram(
        teiUid: String,
        programUid: String,
        deUid: String,
        programStageUid: String?
    ): String? {

        val event = getLatestEvent(teiUid, programUid, programStageUid)

        val dataValue = d2.trackedEntityModule().trackedEntityDataValues()
            .value(event?.uid() ?: "", deUid)
            .blockingGet()?.value()

        if (dataValue.isNullOrBlank()) {
            return null
        }
        return dataValue
    }

    fun getDataElementDisplayName(
        deUid: String
    ): String {
        return d2.dataElement(deUid)
            ?.displayFormName().toString()
    }

    fun getTeiUidsWithActiveEnrollmentForProgram(programUid: String): List<String> {
        return d2.enrollmentModule().enrollments()
            .byProgram().eq(programUid)
            .byStatus().eq(EnrollmentStatus.ACTIVE)
            .blockingGet()
            .map { it.trackedEntityInstance() as String }
    }


    fun quarterDatesCalculator(): Pair<LocalDate, LocalDate> {
        val today = LocalDate.now()

        val adjustMonth = (today.monthValue - 1 + 12) % 12
        val currentQuarter = (adjustMonth / 3) + 1

        val quarterStartMonth = (currentQuarter - 1) * 3 + 1

        val quarterStart = LocalDate.of(today.year, quarterStartMonth, 1)
        val quarterEnd = quarterStart.plusMonths(3).minusDays(1)

        return quarterStart to quarterEnd
    }

    fun eventExistInQuarter(
        teiUid: String,
        programUid: String,
        programStageUid: String,
        startDate: Date,
        endDate: Date
    ): Boolean {

        val enrollment = d2.enrollmentModule().enrollments()
            .byTrackedEntityInstance().eq(teiUid)
            .byProgram().eq(programUid)
            .byStatus().eq(EnrollmentStatus.ACTIVE)
            .one().blockingGet()?.uid()?: return false

        return d2.eventModule().events()
            .byEnrollmentUid().eq(enrollment)
            .byProgramStageUid().eq(programStageUid)
            .byEventDate().afterOrEqual(startDate)
            .byEventDate().beforeOrEqual(endDate)
            .one()
            .blockingExists()
    }

    fun createNewEvent(
        teiUid: String,
        programUid: String,
        programStageUid: String,
        eventDate: Date
    ): String {
        val enrollmentUid = d2.enrollmentModule()
            .enrollments()
            .byTrackedEntityInstance().eq(teiUid)
            .byProgram().eq(programUid)
            .byStatus().eq(EnrollmentStatus.ACTIVE)
            .one()
            .blockingGet()
            ?.uid()
            ?: throw IllegalStateException(
                "No active enrollment found for TEI $teiUid"
            )

        val eventUid = d2.eventModule()
            .events()
            .blockingAdd(
                EventCreateProjection.builder()
                    .enrollment(enrollmentUid)
                    .program(programUid)
                    .programStage(programStageUid)
                    .organisationUnit(
                        d2.enrollmentModule()
                            .enrollments()
                            .uid(enrollmentUid)
                            .blockingGet()!!
                            .organisationUnit()
                    )
                    .build()
            )

        d2.eventModule()
            .events()
            .uid(eventUid)
            .setEventDate(eventDate)

        d2.eventModule()
            .events()
            .uid(eventUid)
            .setStatus(EventStatus.ACTIVE)

        return eventUid
    }


}