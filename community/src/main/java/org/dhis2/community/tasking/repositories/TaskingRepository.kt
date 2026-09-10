package org.dhis2.community.tasking.repositories

import com.google.gson.Gson
import org.dhis2.community.common.COMMUNITY_NAMESPACE
import org.dhis2.community.tasking.models.Task
import org.dhis2.community.tasking.models.TaskingConfig
import org.dhis2.community.tasking.utils.Constants
import org.hisp.dhis.android.core.D2
import org.hisp.dhis.android.core.arch.repositories.scope.RepositoryScope
import org.hisp.dhis.android.core.enrollment.Enrollment
import org.hisp.dhis.android.core.enrollment.EnrollmentCreateProjection
import org.hisp.dhis.android.core.enrollment.EnrollmentStatus
import org.hisp.dhis.android.core.event.Event
import org.hisp.dhis.android.core.maintenance.D2Error
import org.hisp.dhis.android.core.organisationunit.OrganisationUnit
import org.hisp.dhis.android.core.trackedentity.TrackedEntityInstance
import org.hisp.dhis.android.core.trackedentity.TrackedEntityInstanceCreateProjection
import timber.log.Timber
import java.util.Collections
import java.util.Date
import javax.inject.Singleton

@Singleton
class TaskingRepository(
    internal val d2: D2,
) {

    private var cachedConfig: TaskingConfig? = null

    val taskStatusAttributeUid: String
        get() = getTaskingConfig().taskProgramConfig.firstOrNull()?.statusUid.orEmpty()
    val taskProgressAttributeUid: String
        get() = getTaskingConfig().taskProgramConfig.firstOrNull()?.taskProgressUid.orEmpty()

    private val programDisplayNames = mutableMapOf<String, String?>()

    // program uid + data element uid -> the stage declaring that data element. Resolving this
    // costs one query for every stage of the program, and the periodic sweep asks for the same
    // handful of data elements once per TEI per task config, so memoise it. Metadata does not
    // change while a sweep is running.
    private val stageForDataElement = mutableMapOf<Pair<String, String>, String?>()

    // Both cleared by invalidateTaskCache() on every task write - see getTasks()/getAllTasks().
    private var cachedTasks: List<Task>? = null
    private var cachedAllTasks: List<Task>? = null

    fun getCachedConfig() = cachedConfig

    fun getTaskProgramUids(): List<String> =
        getTaskingConfig()
            .taskProgramConfig
            .mapNotNull { config ->
                config.programUid.trim().takeIf { uid -> uid.isNotEmpty() }
            }

    fun getTaskProgramUid(): String? = getTaskProgramUids().firstOrNull()

    @Synchronized
    fun getTaskingConfig(): TaskingConfig {
        Timber.d("getTaskingConfig() called - START")
        cachedConfig?.takeIf { it.hasAnyConfig() }?.let {
            Timber.d("Returning cached tasking config")
            return it
        }

        val config = try {
            val entries = d2.dataStoreModule().dataStore()
                .byNamespace().eq(COMMUNITY_NAMESPACE)
                .blockingGet()

            Timber.d("Found ${entries.size} entries in '$COMMUNITY_NAMESPACE' namespace")

            val taskingEntry = entries.firstOrNull { it.key() == "tasking" }

            if (taskingEntry == null) {
                Timber.w("No tasking entry found in dataStore")
                return TaskingConfig(
                    programTasks = emptyList(),
                    taskProgramConfig = emptyList()
                )
            }

            Timber.d("Found tasking entry: ${taskingEntry.key()}")

            val rawValue = taskingEntry.value()
            Timber.d("Raw value type: ${rawValue?.javaClass?.name}")
            Timber.d("Raw tasking config value (first 200 chars): ${rawValue?.take(200)}")

            if (rawValue.isNullOrBlank()) {
                Timber.w("Tasking config value is null or blank")
                return TaskingConfig(
                    programTasks = emptyList(),
                    taskProgramConfig = emptyList()
                )
            }

            // Extract JSON from JsonWrapper format if needed
            val value = if (rawValue.startsWith("JsonWrapper(json=")) {
                Timber.d("Detected JsonWrapper format, extracting JSON")
                // Remove "JsonWrapper(json=" prefix and trailing ")"
                rawValue.removePrefix("JsonWrapper(json=").removeSuffix(")")
            } else {
                rawValue
            }

            Timber.d("Extracted value (first 200 chars): ${value.take(200)}")

            if (!value.trim().startsWith("{")) {
                Timber.w("Tasking config value does not start with '{': ${value.take(100)}")
                return TaskingConfig(
                    programTasks = emptyList(),
                    taskProgramConfig = emptyList()
                )
            }

            val parsed = Gson().fromJson(value, TaskingConfig::class.java)
            val config = parsed.sanitized()
            Timber.d("Successfully parsed tasking config: ${config.taskProgramConfig.size} task programs")
            config
        } catch (e: Exception) {
            Timber.e(e, "Error parsing tasking config")
            TaskingConfig(
                programTasks = emptyList(),
                taskProgramConfig = emptyList()
            )
        }

        // Avoid permanently pinning an empty config due to startup timing races.
        if (config.hasAnyConfig()) {
            cachedConfig = config
        }

        return config
    }

    private fun TaskingConfig.hasAnyConfig(): Boolean {
        return programTasks.isNotEmpty() || taskProgramConfig.isNotEmpty()
    }

    /**
     * Drops structurally-incomplete task configs so evaluation never dereferences a null
     * [Reference][TaskingConfig.ProgramTasks.TaskConfig.Reference].
     *
     * Gson builds these data classes by reflection, bypassing the Kotlin constructor — so a
     * `condition` whose JSON omits `lhs`/`rhs`, or a `dueDate` missing `anchor`, ends up holding a
     * real `null` in a field declared non-null (the same hazard documented on [Period.rules]).
     * The engine later reads `.ref` on those references and throws
     * `NullPointerException: ... Reference.getRef() on a null object reference`. A malformed entry
     * can't be evaluated meaningfully anyway, so we log and drop it rather than crash the worker.
     */
    private fun TaskingConfig.sanitized(): TaskingConfig {
        val safePrograms = (programTasks ?: emptyList()).map { program ->
            val configs = program.taskConfigs ?: emptyList()
            val validConfigs = configs.filter { it.isStructurallyValid() }
            val dropped = configs.size - validConfigs.size
            if (dropped > 0) {
                Timber.w(
                    "Dropped $dropped malformed task config(s) with null references in program " +
                        "'${program.programUid}'"
                )
            }
            program.copy(taskConfigs = validConfigs)
        }
        return copy(programTasks = safePrograms)
    }

    @Suppress("SENSELESS_COMPARISON")
    private fun TaskingConfig.ProgramTasks.TaskConfig.Reference?.isPresent(): Boolean =
        this != null && this.ref != null

    private fun TaskingConfig.ProgramTasks.TaskConfig.Condition?.isValid(): Boolean =
        this != null && this.lhs.isPresent() && this.rhs.isPresent()

    private fun TaskingConfig.ProgramTasks.TaskConfig.DueDateSpec?.isValid(): Boolean =
        this != null && this.anchor.isPresent()

    /** A (possibly null) condition list is valid only if every element resolves both references. */
    private fun List<TaskingConfig.ProgramTasks.TaskConfig.Condition>?.allValid(): Boolean =
        this == null || this.all { it.isValid() }

    @Suppress("SENSELESS_COMPARISON")
    private fun TaskingConfig.ProgramTasks.TaskConfig.isStructurallyValid(): Boolean {
        val trigger = this.trigger ?: return false
        if (!trigger.condition.allValid()) return false

        val period = this.period ?: return false
        if (!period.default.isValid()) return false
        period.rules?.forEach { rule ->
            if (rule == null) return false
            if (!rule.condition.allValid()) return false
            if (!rule.dueDate.isValid()) return false
        }

        val completion = this.completion ?: return false
        if (!completion.condition.allValid()) return false

        return true
    }

    fun getOrgUnit(taskTeiUid: String): String? {
        val tei = d2.trackedEntityModule().trackedEntityInstances()
            .uid(taskTeiUid)
            .blockingGet()
        return tei?.organisationUnit()
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

    fun getProgramName(programUid: String): String {
        return d2.programModule().programs()
            .uid(programUid)
            .blockingGet()?.displayName() ?: ""
    }

    fun getSourceProgramIcon(sourceProgramUid: String): String? {
        val program = d2.programModule().programs()
            .uid(sourceProgramUid).blockingGet()

        return program?.style()?.icon()
    }

    fun getSourceProgramColor(sourceProgramUid: String): String? {
        val program = d2.programModule().programs()
            .uid(sourceProgramUid).blockingGet()

        return program?.style()?.color()
    }


    fun getTaskTei(): List<TrackedEntityInstance> {
        val taskTeiUid = getTaskingConfig().taskProgramConfig.firstOrNull()?.teiTypeUid
        if (taskTeiUid.isNullOrEmpty()) return emptyList()

        val programUid = getTaskingConfig().taskProgramConfig.firstOrNull()?.programUid

        val activeEnrollments = d2.enrollmentModule().enrollments()
            .byProgram().eq(programUid)
            .blockingGet()

        val activeTeiUids =
            activeEnrollments.map { it.trackedEntityInstance() as String }

        if (activeTeiUids.isNullOrEmpty()) return emptyList()

        return d2.trackedEntityModule().trackedEntityInstances()
            .byUid().`in`(activeTeiUids)
            .withTrackedEntityAttributeValues()
            .orderByLastUpdated(RepositoryScope.OrderByDirection.DESC)
            .blockingGet()
    }

    /**
     * Loads every task TEI with its attribute values. Callers ask for this once per TEI in the
     * periodic sweep, so it is memoised - but only until something writes a task. Any write path
     * must call [invalidateTaskCache], so a caller can never observe a task it or a previous
     * iteration has already updated. Freshness is enforced here rather than assumed at call sites.
     */
    @Synchronized
    fun getTasks(): List<Task> {
        cachedTasks?.let { return it }

        val programConfig = getTaskingConfig().taskProgramConfig.firstOrNull()

        return getTaskTei()
            .map { tei: TrackedEntityInstance -> teiToTask(tei, programConfig) }
            .also { cachedTasks = it }
    }

    /**
     * Clears every derived view of task state. Must be called by anything that writes a task
     * attribute value or changes which TEIs are enrolled in the task program - those are the only
     * two inputs [teiToTask] reads. Enrollment *status* changes do not need it: neither loader
     * filters enrollments by status and no Task field is derived from the enrollment.
     */
    @Synchronized
    fun invalidateTaskCache() {
        cachedTasks = null
        cachedAllTasks = null
    }

    @Synchronized
    fun getAllTasks(): List<Task> {
        cachedAllTasks?.let { return it }

        val taskTeiUid = getTaskingConfig().taskProgramConfig.firstOrNull()?.teiTypeUid
        val programUid = getTaskingConfig().taskProgramConfig.firstOrNull()?.programUid
        if (taskTeiUid.isNullOrEmpty()) return emptyList()

        val activeEnrollments = d2.enrollmentModule().enrollments()
            .byProgram().eq(programUid)
            .blockingGet()

        val activeTeiUids =
            activeEnrollments.map { it.trackedEntityInstance() as String }

        if (activeTeiUids.isNullOrEmpty()) return emptyList()

        val allTies = d2.trackedEntityModule().trackedEntityInstances()
            .byUid().`in`(activeTeiUids)
            .withTrackedEntityAttributeValues()
            .orderByLastUpdated(RepositoryScope.OrderByDirection.DESC)
            .blockingGet()

        val programConfig = getTaskingConfig().taskProgramConfig.firstOrNull()

        return allTies
            .map { tei: TrackedEntityInstance -> teiToTask(tei, programConfig) }
            .also { cachedAllTasks = it }
    }

    fun TrackedEntityInstance.getAttributeValue(attributeUid: String?): String? {
        if (attributeUid.isNullOrEmpty()) return null
        return this.trackedEntityAttributeValues()
            ?.firstOrNull { it.trackedEntityAttribute() == attributeUid }
            ?.value()
    }

    private fun teiToTask(
        tei: TrackedEntityInstance,
        programConfig: TaskingConfig.TaskProgramConfig?
    ): Task {
        return Task(
            name = tei.getAttributeValue(programConfig?.taskNameUid) ?: "Unnamed Task",
            description = tei.getAttributeValue(programConfig?.description) ?: "",
            sourceProgramUid = tei.getAttributeValue(programConfig?.taskSourceProgramUid) ?: "",
            sourceEnrollmentUid = tei.getAttributeValue(programConfig?.taskSourceEnrollmentUid)
                ?: "",
            sourceProgramName = programConfig?.programName ?: "",
            sourceTeiUid = tei.getAttributeValue(programConfig?.taskSourceTeiUid) ?: "",
            teiUid = tei.uid(),
            teiPrimary = tei.getAttributeValue(programConfig?.taskPrimaryAttrUid) ?: "",
            teiSecondary = tei.getAttributeValue(programConfig?.taskSecondaryAttrUid) ?: "",
            teiTertiary = tei.getAttributeValue(programConfig?.taskTertiaryAttrUid) ?: "",
            dueDate = tei.getAttributeValue(programConfig?.dueDateUid) ?: "",
            priority = tei.getAttributeValue(programConfig?.priorityUid) ?: Constants.MEDIUM,
            iconNane = getSourceProgramIcon(
                sourceProgramUid = (tei.getAttributeValue(programConfig?.taskSourceProgramUid)
                    ?: "")
            ),
            status = tei.getAttributeValue(programConfig?.statusUid) ?: Constants.OPEN,
            sourceEventUid = tei.getAttributeValue(programConfig?.taskSourceEventUid) ?: "",
            progress = tei.getAttributeValue(programConfig?.taskProgressUid)?.toFloatOrNull() ?: 0f
        )
    }


    fun updateTaskAttrValue(taskAttrUid: String?, newTaskAttrValue: String?, taskTieUid: String, retries: Int = 0) {
        if (taskAttrUid.isNullOrBlank()) {
            Timber.tag("TaskingRepository").w("Skipping task attribute update because attribute UID is blank")
            return
        }

        try {
            val valueRepository = d2.trackedEntityModule().trackedEntityAttributeValues()
                .value(taskAttrUid, taskTieUid)

            // Every sweep recomputes progress for every open task and most of the time it is
            // identical. Writing it back anyway cost three things: a database write, an
            // invalidation that made the task caches useless, and a TO_POST flag that re-uploaded
            // an unchanged task on the next sync. Only write a value that actually differs.
            val currentValue = valueRepository.blockingGet()?.value()
            val nextValue = newTaskAttrValue ?: ""
            if (currentValue == nextValue) return

            valueRepository.blockingSet(nextValue)
            invalidateTaskCache()
        } catch (ex: Exception) {
            Timber.tag("TaskingRepository").e(ex, "Error updating task attribute value")
            if (retries < 3 && taskTieUid.isNotEmpty()) {
                updateTaskAttrValue(taskAttrUid, newTaskAttrValue, taskTieUid, retries + 1)
            }
        }

    }

    val currentOrgUnits = d2.organisationUnitModule().organisationUnits().byOrganisationUnitScope(
        OrganisationUnit.Scope.SCOPE_DATA_CAPTURE
    )
        .blockingGet().map { it.uid() }

    fun getProgramDisplayName(programUid: String): String? {
        programDisplayNames[programUid]?.let { return it }
        return try {
            programDisplayNames[programUid] = d2.programModule().programs()
                .byUid().eq(programUid).one()
                .blockingGet()?.displayName()
            programDisplayNames[programUid]
        } catch (e: Exception) {
            Timber.tag("TaskingRepository")
                .e(e, "Error fetching program display name for UID: $programUid")
            null
        }
    }


    @Synchronized
    fun createTask(
        task: Task,
        sourceTeiOrgUnitUid: String,
        taskTEITypeUid: String,
        taskProgramUid: String
    ): Boolean {
        val newTeiUid = try {
            d2.trackedEntityModule().trackedEntityInstances().blockingAdd(
                TrackedEntityInstanceCreateProjection.builder()
                    .organisationUnit(sourceTeiOrgUnitUid)
                    .trackedEntityType(taskTEITypeUid)
                    .build()
            )
        } catch (e: D2Error) {
            Timber.tag("CreationEvaluator")
                .e("TEI creation failed: code=${e.errorCode()} desc=${e.errorDescription()}")
            return false
        }

        val taskProgramConfig = this.getTaskingConfig().taskProgramConfig.firstOrNull()
        this.updateTaskAttrValue(taskProgramConfig?.statusUid ?: "", Constants.OPEN, newTeiUid)
        this.updateTaskAttrValue(taskProgramConfig?.taskNameUid ?: "", task.name, newTeiUid)
        this.updateTaskAttrValue(taskProgramConfig?.priorityUid ?: "", task.priority, newTeiUid)
        this.updateTaskAttrValue(taskProgramConfig?.dueDateUid ?: "", task.dueDate, newTeiUid)
        this.updateTaskAttrValue(
            taskProgramConfig?.taskTertiaryAttrUid ?: "",
            task.teiTertiary,
            newTeiUid
        )
        this.updateTaskAttrValue(
            taskProgramConfig?.taskSecondaryAttrUid ?: "",
            task.teiSecondary,
            newTeiUid
        )
        this.updateTaskAttrValue(
            taskProgramConfig?.taskPrimaryAttrUid ?: "",
            task.teiPrimary,
            newTeiUid
        )
        this.updateTaskAttrValue(
            taskProgramConfig?.taskSourceProgramUid ?: "",
            task.sourceProgramUid,
            newTeiUid
        )
        this.updateTaskAttrValue(
            taskProgramConfig?.taskSourceEnrollmentUid ?: "",
            task.sourceEnrollmentUid,
            newTeiUid
        )
        this.updateTaskAttrValue(
            taskProgramConfig?.taskSourceTeiUid ?: "",
            task.sourceTeiUid,
            newTeiUid
        )
        this.updateTaskAttrValue(
            taskProgramConfig?.taskSourceEventUid ?: "",
            task.sourceEventUid,
            newTeiUid
        )

        try {
            val enrollmentUid = d2.enrollmentModule().enrollments().blockingAdd(
                EnrollmentCreateProjection.builder()
                    .trackedEntityInstance(newTeiUid)
                    .program(taskProgramUid)
                    .organisationUnit(sourceTeiOrgUnitUid)
                    .build()
            )
            if (enrollmentUid.isEmpty()) {
                Timber.tag("CreationEvaluator").e("Enrollment creation failed: enrollmentUid is null")
                deleteOrphanTei(newTeiUid)
                return false
            }
            val today = Date()
            d2.enrollmentModule().enrollments().uid(enrollmentUid).setEnrollmentDate(today)
            d2.enrollmentModule().enrollments().uid(enrollmentUid).setIncidentDate(today)
            d2.enrollmentModule().enrollments().uid(enrollmentUid)
                .setStatus(EnrollmentStatus.ACTIVE)
            // The enrollment is what makes this TEI visible to the task loaders, and
            // updateTaskAttrValue returns early without invalidating when a configured attribute
            // uid is blank - so never rely on the attribute writes above having done it.
            invalidateTaskCache()
            return true
        } catch (e: D2Error) {
            Timber.tag("CreationEvaluator")
                .e("Enrollment failed: code=${e.errorCode()} desc=${e.errorDescription()}")
            // The task TEI was already created; without the enrollment it is an orphan that
            // would sync as a bare TEI, so roll it back.
            deleteOrphanTei(newTeiUid)
            return false
        }
    }

    /** Best-effort removal of a just-created TEI when a later step of its creation failed. */
    private fun deleteOrphanTei(teiUid: String) {
        try {
            d2.trackedEntityModule().trackedEntityInstances().uid(teiUid).blockingDelete()
        } catch (e: Exception) {
            Timber.tag("CreationEvaluator").e(e, "Failed to roll back orphan TEI $teiUid")
        } finally {
            invalidateTaskCache()
        }
    }

    fun getEventDate(eventUid: String): Date? {
        val event = d2.eventModule().events().uid(eventUid).blockingGet()
        return event?.eventDate() ?: event?.dueDate() ?: event?.created()
    }

    fun getLatestEvent(
        programUid: String,
        dataElementUid: String,
        enrollmentUd: String,
        eventUid: String?
    ): Event? {
        val stageUid = stageForDataElement.getOrPut(programUid to dataElementUid) {
            d2.programModule().programStages().byProgramUid()
                .eq(programUid).blockingGet()
                .firstOrNull {
                    d2.programModule().programStageDataElements()
                        .byProgramStage().eq(it.uid())
                        .byDataElement().eq(dataElementUid)
                        .blockingGet().isNotEmpty()
                }?.uid()
        }

        if (stageUid == null) {
            Timber.tag("TaskingRepository").w(
                "getLatestEvent: no stage of program $programUid declares data element " +
                    "$dataElementUid as a stage data element — cannot search for it"
            )
            return null
        }

        val events = if (eventUid == null)
            d2.eventModule().events()
                .byEnrollmentUid().eq(enrollmentUd)
                .byProgramStageUid().eq(stageUid)
                .withTrackedEntityDataValues()
                .blockingGet()
        else
            d2.eventModule().events()
                .byUid().eq(eventUid)
                .withTrackedEntityDataValues()
                .blockingGet()

        fun hasTargetValue(event: Event): Boolean =
            event.trackedEntityDataValues()?.any { it.dataElement() == dataElementUid } == true

        val result = events.maxByOrNull {
            it.created() ?: it.eventDate() ?: Date(0)
        }

        if (result == null) {
            Timber.tag("TaskingRepository").w(
                "getLatestEvent: stage ${stageUid} declares $dataElementUid but no matching " +
                    "event found (enrollment=$enrollmentUd, eventUid=$eventUid, candidates=${events.size})"
            )
        } else if (!hasTargetValue(result)) {
            val eventsWithValue = events.filter { hasTargetValue(it) }
            Timber.tag("TaskingRepository").w(
                "getLatestEvent: picked event ${result.uid()} for $dataElementUid has no value; " +
                    "${events.size} candidate event(s) in stage ${stageUid}, " +
                    "${eventsWithValue.size} of them do have a value " +
                    "(${eventsWithValue.map { it.uid() + "@" + (it.eventDate() ?: it.created()) }})"
            )
        }

        return result
    }

    fun countEventsByDataValue(
        programUid: String,
        dataElementUid: String,
        enrollmentUid: String,
        stageUid: String? = null,
        expectedValue: String? = null
    ): Int {
        try {
            val stage = if (!stageUid.isNullOrBlank()) {
                d2.programModule().programStages()
                    .byUid().eq(stageUid)
                    .one()
                    .blockingGet()
            } else {
                d2.programModule().programStages().byProgramUid()
                    .eq(programUid).blockingGet()
                    .firstOrNull {
                        d2.programModule().programStageDataElements()
                            .byProgramStage().eq(it.uid())
                            .byDataElement().eq(dataElementUid)
                            .blockingGet()
                            .isNotEmpty()
                    }
            } ?: return 0

            val events = d2.eventModule().events()
                .byEnrollmentUid().eq(enrollmentUid)
                .byProgramStageUid().eq(stage.uid())
                .withTrackedEntityDataValues()
                .blockingGet()

            val count = events.count { ev ->
                ev.trackedEntityDataValues()?.any { dv ->
                    dv.dataElement() == dataElementUid && (expectedValue == null || dv.value() == expectedValue)
                } ?: false
            }

            return count
        } catch (e: Exception) {
            Timber.tag("TaskingRepository").e(e, "Error counting events for data element: $dataElementUid")
            return 0
        }
    }

    fun getTasksForPgEnrollment(enrollmentUID: String): List<Task> {
        return getAllTasks().filter { it.sourceEnrollmentUid == enrollmentUID }
    }

    fun getEnrollment(enrollmentUid: String) =
        d2.enrollmentModule().enrollments().uid(enrollmentUid).blockingGet()


    fun getPeriodEventsForProgramStage(
        programUid: String,
        stageUid: String,
        teiUid: String,
        startDate: Date,
        endDate: Date,
    ): List<Event> {

        val occurredEvents =  d2.eventModule().events()
            .byProgramUid().eq(programUid)
            .byProgramStageUid().eq(stageUid)
            .byTrackedEntityInstanceUids(Collections.singletonList(teiUid))
            .byEventDate().after(startDate)
            .byEventDate().before(endDate)
            .blockingGet()

        val scheduledEvents = d2.eventModule().events()
            .byProgramUid().eq(programUid)
            .byProgramStageUid().eq(stageUid)
            .byTrackedEntityInstanceUids(Collections.singletonList(teiUid))
            .byDueDate().after(startDate)
            .byDueDate().before(endDate)
            .byEventDate().isNull
            .blockingGet()

        return (occurredEvents + scheduledEvents).distinctBy { it.uid() }

    }

    fun getTeiUidsWithActiveEnrollmentForProgram(programUid: String): List<String> {
        return d2.enrollmentModule().enrollments()
            .byProgram().eq(programUid)
            .byStatus().eq(EnrollmentStatus.ACTIVE)
            .blockingGet()
            .map { it.trackedEntityInstance() as String }
    }

    fun getEnrollmentLatestEvent(enrollmentUid: String, programUid: String): Event? {
        val events = d2.eventModule().events()
            .byEnrollmentUid().eq(enrollmentUid)
            .byProgramUid().eq(programUid)
            .withTrackedEntityDataValues()
            .blockingGet()

        return events
            .maxByOrNull {
                it.created() ?: it.eventDate() ?: Date(0)
            }
    }

    fun hasAccessToProgram(programUid: String): Boolean {
        val program = d2.programModule().programs()
            .byUid().eq(programUid)
            .one()
            .blockingGet()
        return program != null
    }

    fun hasDataAccessToTasking(): Boolean {
        val taskProgramUid = getTaskProgramUid() ?: return false
        val program = d2.programModule().programs().uid(taskProgramUid).blockingGet()
        val dataAccess = program?.access()?.data()
        return dataAccess?.read() == true && dataAccess.write() == true
    }
}
