package org.dhis2.community.tasking.repositories

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.google.gson.Gson
import io.reactivex.Observable
import io.reactivex.subjects.PublishSubject
import org.dhis2.community.tasking.models.Task
import org.dhis2.community.tasking.models.TaskingConfig
import org.hisp.dhis.android.core.D2
import org.hisp.dhis.android.core.arch.repositories.scope.RepositoryScope
import org.hisp.dhis.android.core.enrollment.Enrollment
import org.hisp.dhis.android.core.enrollment.EnrollmentStatus
import org.hisp.dhis.android.core.organisationunit.OrganisationUnit
import org.hisp.dhis.android.core.trackedentity.TrackedEntityInstance
import java.text.SimpleDateFormat
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import javax.inject.Singleton

@Singleton
class TaskingRepository(
    private val d2: D2,
) {

    private val dataElementChangedSubject = PublishSubject.create<String>()
    fun observeDataElementChanges(): Observable<String> =
        dataElementChangedSubject.hide()

    private fun notifyDataElementChanged(dataElement: String) {
        dataElementChangedSubject.onNext(dataElement)
    }

    private var cachedConfig: TaskingConfig? = null

    val taskStatusAttributeUid =
        getTaskingConfig().taskProgramConfig.firstOrNull()?.statusUid ?: ""

    fun getCachedConfig() = cachedConfig
    fun getTaskingConfig(): TaskingConfig {
        cachedConfig?.let { return it }

        val entries = d2.dataStoreModule().dataStore()
            .byNamespace().eq("community_redesign")
            .blockingGet()

        val config = entries.firstOrNull { it.key() == "tasking" }
            ?.let { Gson().fromJson(it.value(), TaskingConfig::class.java) }
            ?: TaskingConfig(
                programTasks = emptyList(),
                taskProgramConfig = emptyList()
            )

        cachedConfig = config
        return config
    }

    fun getOrgUnit(taskTeiUid: String):String?{
        val tei = d2.trackedEntityModule().trackedEntityInstances()
            .uid(taskTeiUid)
            .blockingGet()
        return tei?.organisationUnit()
    }
    @RequiresApi(Build.VERSION_CODES.O)
    fun dueDateCalculation(
        taskConfig: TaskingConfig.ProgramTasks.TaskConfig,
        sourceTieUid: String?
    ): String?{
        if (taskConfig.period.anchor.uid.isNullOrBlank() || sourceTieUid.isNullOrBlank()) {
            val date =  Date().toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
            return date.plusDays(taskConfig.period.dueInDays.toLong()).toString()
        }

        val dateOfBirthValue : String? = d2.trackedEntityModule().trackedEntityAttributeValues()
            .value(taskConfig.period.anchor.uid, sourceTieUid?:"")
            .blockingGet()?.value()

        if (dateOfBirthValue.isNullOrBlank()) {
            val today = Date().toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
            return today.plusDays(taskConfig.period.dueInDays.toLong()).toString()
        }

        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val birthDate = dateFormat.parse(dateOfBirthValue)

        val dateOfBirth = birthDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDate()

        return dateOfBirth.plusDays(taskConfig.period.dueInDays.toLong()).toString()

    }
    @RequiresApi(Build.VERSION_CODES.O)
    fun calculateDueDate(
        taskConfig: TaskingConfig.ProgramTasks.TaskConfig,
        teiUid: String,
        programUid: String
    ): String? {
        val enrollment = getLatestEnrollment(teiUid, programUid) ?: return null

        val anchorDate = enrollment.incidentDate() ?: enrollment.enrollmentDate() ?: return null
        val localDate = anchorDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
        val dueDate = localDate.plusDays(taskConfig.period.dueInDays.toLong())

        Log.d("TaskingRepository", "DueDate for TEI=${teiUid} task=${taskConfig.name} is $dueDate")

        return dueDate.format(DateTimeFormatter.ISO_LOCAL_DATE)
    }

    fun getLatestEnrollment(teiUid: String, programUid: String): Enrollment? {
        val enrollment = d2.enrollmentModule().enrollments()
            .byTrackedEntityInstance().eq(teiUid)
            .byProgram().eq(programUid)
            .byStatus().eq(EnrollmentStatus.ACTIVE)
            .one()
            .blockingGet()
        //.maxByOrNull { it.enrollmentDate()?.time ?: 0 }

        return enrollment
    }

    fun getDateOfBirth(tieUid: String){

    }

    fun resolvedReference(
        trigger: TaskingConfig.ProgramTasks.TaskConfig.HasConditions,
        teiUid: String,
        attrOrDataElementUid: String,
        programUid: String
    ): String? {
        return trigger.condition.firstNotNullOfOrNull { cond ->

            val enrollment = getLatestEnrollment(teiUid, programUid)
                ?: return@firstNotNullOfOrNull null

            val events = d2.eventModule().events()
                .byEnrollmentUid().eq(enrollment.uid())
                .withTrackedEntityDataValues()
                .blockingGet()
            when (cond.lhs.ref) {
                "teiAttribute" -> d2.trackedEntityModule().trackedEntityAttributeValues()
                    .byTrackedEntityInstance().eq(teiUid)
                    .byTrackedEntityAttribute().eq(attrOrDataElementUid)
                    .one().blockingGet()?.value()

                "eventData" -> {


                    /**/

                    val latestEvent = events
                        .maxByOrNull { it.created()?: it.eventDate()?: Date(0) } // choose your ordering

                    // From the latest event, get the value of the desired data element
                    latestEvent
                        ?.trackedEntityDataValues()
                        ?.firstOrNull { it.dataElement() == attrOrDataElementUid }
                        ?.value()
                }

                "allEventsData" -> {
                    events.asSequence()
                        .flatMap { it.trackedEntityDataValues() ?: emptyList() }
                        .firstOrNull { it.dataElement() == attrOrDataElementUid }
                        ?.value()
                }


                "static" -> cond.lhs.uid
                else -> null
            }
        }
    }

    fun getProgramName(programUid: String): String {
        return d2.programModule().programs()
            .uid(programUid)
            .blockingGet()?.displayName() ?: ""
    }

    fun getSourceProgramIcon(sourceProgramUid: String) : String?{
        val program = d2.programModule().programs()
            .uid(sourceProgramUid).blockingGet()

        return program?.style()?.icon()
    }

    fun getSourceProgramColor(sourceProgramUid: String) : String?{
        val program = d2.programModule().programs()
            .uid(sourceProgramUid).blockingGet()

        return program?.style()?.color()
    }

    fun getTieByType(
        trackedEntityTypeUid: String,
        orgUnitUid: String,
        programUid: String
    ): List<TrackedEntityInstance> {
        var query = d2.trackedEntityModule().trackedEntityInstances()
            .byTrackedEntityType().eq(trackedEntityTypeUid)

        if (programUid.isNotEmpty()) query = query.byProgramUids(listOf(programUid))
        if (orgUnitUid.isNotEmpty()) query = query.byOrganisationUnitUid().eq(orgUnitUid)

        return query.blockingGet()
    }


    fun getTaskTei(
        orgUnitUid: String
    ): List<TrackedEntityInstance> {
        val taskTeiUid = cachedConfig?.taskProgramConfig?.firstOrNull()?.teiTypeUid
        if (taskTeiUid.isNullOrEmpty()) return emptyList()

        val programUid = getTaskingConfig().taskProgramConfig.firstOrNull()?.programUid

        val activeEnrollments = d2.enrollmentModule().enrollments()
            .byProgram().eq(programUid)
            .blockingGet()

        val activeTeiUids : Collection<String>? = activeEnrollments.map { it.trackedEntityInstance() as String }

        if (activeTeiUids.isNullOrEmpty()) return emptyList()

        return  d2.trackedEntityModule().trackedEntityInstances()
            .byUid().`in`(activeTeiUids)
            .withTrackedEntityAttributeValues()
            .orderByLastUpdated(RepositoryScope.OrderByDirection.DESC)
            .blockingGet()

        /*return d2.trackedEntityModule().trackedEntityInstances().byTrackedEntityType()
            .eq(taskTeiUid)
            .withTrackedEntityAttributeValues()
            .blockingGet()*/
    }

    fun getTasksPerOrgUnit(
//        tieTypeUid: String,
        orgUnitUid: String = currentOrgUnits.first(),
        //thisProgramUid: String,
    ): List<Task> {
        val teis = getTaskTei(orgUnitUid)

        // Find the config for this program
        val programConfig = getCachedConfig()?.taskProgramConfig?.firstOrNull()
        val taskConfig = getCachedConfig()?.programTasks?.firstOrNull() //{it.programUid == thisProgramUid}
        //taskingConfig.taskConfigs
        //.firstOrNull { it.programUid == programUid }

        return teis.map { tei ->
            Task(
                name = tei.getAttributeValue(programConfig?.taskNameUid) ?: "Unnamed Task",
                description = tei.getAttributeValue(programConfig?.description) ?: "",
                sourceProgramUid = tei.getAttributeValue(programConfig?.taskSourceProgramUid) ?: "",
                sourceEnrollmentUid = tei.getAttributeValue(programConfig?.taskSourceEnrollmentUid) ?: "",
                sourceProgramName = programConfig?.programName ?: "",
                sourceTeiUid = tei.getAttributeValue(programConfig?.taskSourceTeiUid)?: "",
                teiUid = tei.uid(),
                teiPrimary = tei.getAttributeValue(taskConfig?.teiView?.teiPrimaryAttribute) ?: "",
                teiSecondary = tei.getAttributeValue(taskConfig?.teiView?.teiSecondaryAttribute) ?: "",
                teiTertiary = tei.getAttributeValue(taskConfig?.teiView?.teiTertiaryAttribute) ?: "",
                dueDate = tei.getAttributeValue(programConfig?.dueDateUid) ?: "",
                priority = tei.getAttributeValue(programConfig?.priorityUid) ?: "Normal",
                status = tei.getAttributeValue(programConfig?.statusUid) ?: "OPEN",
                iconNane = getSourceProgramIcon(sourceProgramUid = tei.getAttributeValue(programConfig?.taskSourceProgramUid)?:"")
            )
        }
    }

    fun getAllTasks() : List<Task>{
        val taskTeiUid = getTaskingConfig().taskProgramConfig.firstOrNull()?.teiTypeUid
        val programUid = getTaskingConfig().taskProgramConfig.firstOrNull()?.programUid
        if (taskTeiUid.isNullOrEmpty()) return emptyList()

        val activeEnrollments = d2.enrollmentModule().enrollments()
            .byProgram().eq(programUid)
            .blockingGet()

        val activeTeiUids : Collection<String>? = activeEnrollments.map { it.trackedEntityInstance() as String }

        if (activeTeiUids.isNullOrEmpty()) return emptyList()

        val allTies = d2.trackedEntityModule().trackedEntityInstances()
            .byUid().`in`(activeTeiUids)
            .withTrackedEntityAttributeValues()
            .orderByLastUpdated(RepositoryScope.OrderByDirection.DESC)
            .blockingGet()


            /*d2.trackedEntityModule().trackedEntityInstances().byTrackedEntityType()
            .eq(taskTeiUid)
            .withTrackedEntityAttributeValues()
            .blockingGet()*/

        val programConfig = getCachedConfig()?.taskProgramConfig?.firstOrNull()
        val taskConfig = getCachedConfig()?.programTasks?.firstOrNull() //{it.programUid == thisProgramUid}


        return allTies.map { tei ->
            Task(
                name = tei.getAttributeValue(programConfig?.taskNameUid) ?: "Unnamed Task",
                description = tei.getAttributeValue(programConfig?.description) ?: "",
                sourceProgramUid = tei.getAttributeValue(programConfig?.taskSourceProgramUid) ?: "",
                sourceEnrollmentUid = tei.getAttributeValue(programConfig?.taskSourceEnrollmentUid)
                    ?: "",
                sourceProgramName = programConfig?.programName ?: "",
                sourceTeiUid = tei.getAttributeValue(programConfig?.taskSourceTeiUid)?: "",
                teiUid = tei.uid(),
                teiPrimary = tei.getAttributeValue(programConfig?.taskPrimaryAttrUid) ?: "",
                teiSecondary = tei.getAttributeValue(programConfig?.taskSecondaryAttrUid) ?: "",
                teiTertiary = tei.getAttributeValue(programConfig?.taskTertiaryAttrUid) ?: "",
                dueDate = tei.getAttributeValue(programConfig?.dueDateUid) ?: "",
                priority = tei.getAttributeValue(programConfig?.priorityUid) ?: "Normal",
                iconNane = getSourceProgramIcon(sourceProgramUid = (tei.getAttributeValue(programConfig?.taskSourceProgramUid)?:"")),
                status = tei.getAttributeValue(programConfig?.statusUid) ?: "OPEN",
            )
        }

    }

    fun TrackedEntityInstance.getAttributeValue(attributeUid: String?): String? {
        if (attributeUid.isNullOrEmpty()) return null
        return this.trackedEntityAttributeValues()
            ?.firstOrNull {it.trackedEntityAttribute() == attributeUid}
            ?.value()
    }


    fun updateTaskAttrValue(taskAttrUid: String?, newTaskAttrValue: String?, taskTieUid: String) {
        if (taskAttrUid != null)
            d2.trackedEntityModule().trackedEntityAttributeValues()
                .value(taskAttrUid, taskTieUid)
                .blockingSet(newTaskAttrValue?:"")
    }

    val currentOrgUnits = d2.organisationUnitModule().organisationUnits().byOrganisationUnitScope(
        OrganisationUnit.Scope.SCOPE_DATA_CAPTURE)
        .blockingGet().map { it.uid() }

    fun getAllTrackedEntityInstances(
        programUid: String,
        sourceTieUid: String?,
        sourceTieOrgUnit: String
    ): List<TrackedEntityInstance> {
        val enrollments = d2.enrollmentModule().enrollments()
            .byOrganisationUnit().eq(sourceTieOrgUnit)
            .byProgram().eq(programUid)
            .byTrackedEntityInstance().eq(sourceTieUid)
            .blockingGet()

        return enrollments.mapNotNull { uid ->
            d2.trackedEntityModule().trackedEntityInstances()
                .uid(uid.trackedEntityInstance())
                .blockingGet()
        }
    }

    fun getProgramDisplayName(programUid: String): String? {
        return try {
            d2.programModule().programs()
                .byUid().eq(programUid).one()
                .blockingGet()?.displayName()
        } catch (e: Exception) {
            Log.e("TaskingRepository", "Error fetching program display name for UID: $programUid", e)
            null
        }
    }

    fun isValidTeiEnrollment(teiUid: String, programUid: String, enrollmentUid: String): String? {
        val enrollments = d2.enrollmentModule().enrollments()
            .uid(enrollmentUid).blockingGet()?.trackedEntityInstance()
//            .byTrackedEntityInstance().eq(teiUid)
//            .byProgram().eq(programUid)
//            //.byStatus().eq(EnrollmentStatus.ACTIVE)
//            .blockingGet()
        return enrollments
    }
}