package org.dhis2.community.workflow

import com.google.gson.Gson
import org.hisp.dhis.android.core.enrollment.EnrollmentCreateProjection
import org.hisp.dhis.android.core.enrollment.EnrollmentStatus
import org.hisp.dhis.android.core.relationship.RelationshipHelper
import org.hisp.dhis.android.core.trackedentity.TrackedEntityInstance
import org.hisp.dhis.android.core.trackedentity.TrackedEntityInstanceCreateProjection
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private const val AUTO_ENROLL_COMBINATION_OR = "or"
private const val SOURCE_TYPE_ATTRIBUTE = "attribute"
private const val SOURCE_TYPE_TEI_ATTRIBUTE = "tei_attribute"
private const val SOURCE_TYPE_DATA_ELEMENT = "data_element"
private const val SOURCE_TYPE_DATA_ELEMENT_ALT = "dataelement"
private const val SOURCE_TYPE_EVENT_DATA = "event_data"

class WorkflowRepository(
    private val d2: org.hisp.dhis.android.core.D2
) {

    fun getWorkflowConfig(): WorkflowConfig {
        Timber.d("getWorkflowConfig() called - START")
        return try {
            val entries = d2.dataStoreModule()
                .dataStore()
                .byNamespace()
                .eq("community_redesign")
                .blockingGet()

            Timber.d("Found ${entries.size} entries in 'community_redesign' namespace")

            val workflowEntry = entries.firstOrNull { it.key() == "workflow" }

            if (workflowEntry == null) {
                Timber.w("No workflow entry found in dataStore")
                return WorkflowConfig(emptyList())
            }

            Timber.d("Found workflow entry: ${workflowEntry.key()}")

            val rawValue = workflowEntry.value()
            Timber.d("Raw value type: ${rawValue?.javaClass?.name}")
            Timber.d("Raw workflow config value (first 200 chars): ${rawValue?.take(200)}")

            if (rawValue.isNullOrBlank()) {
                Timber.w("Workflow config value is null or blank")
                return WorkflowConfig(emptyList())
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
                Timber.w("Workflow config value does not start with '{': ${value.take(100)}")
                return WorkflowConfig(emptyList())
            }

            val config = Gson().fromJson(value, WorkflowConfig::class.java)
            Timber.d("Successfully parsed workflow config: ${config.teiCreatablePrograms.size} creatable programs")
            config
        } catch (e: Exception) {
            Timber.e(e, "Error parsing workflow config")
            WorkflowConfig(emptyList())
        }
    }

    fun getTeiAttributes(teiUid: String): Map<String, String> {

        val attributeValues = d2.trackedEntityModule()
            .trackedEntityAttributeValues()
            .byTrackedEntityInstance().eq(teiUid)
            .blockingGet()

        return attributeValues.mapNotNull { attributeValue ->
            val attributeUid = attributeValue.trackedEntityAttribute()
            val value = attributeValue.value()?.trim()
            if (!attributeUid.isNullOrBlank() && !value.isNullOrEmpty()) {
                attributeUid to value
            } else {
                null
            }
        }.toMap()
    }

    fun addMemberToHousehold(memberTeiUid: String, householdUid: String, relationshipType: String) {
        d2.relationshipModule().relationships().blockingAdd(
            RelationshipHelper.teiToTeiRelationship(
                memberTeiUid,
                householdUid,
                relationshipType
            )
        )
    }



    fun searchTeiByAttributes(
        teiType: String,
        attributes: List<Pair<String, String>>
    ): TrackedEntityInstance? {

        if (attributes.isEmpty()) return null

        // 1️⃣ Get TEI UIDs that match each attribute
        val matchingUidSets = attributes.map { (attributeUid, value) ->

            d2.trackedEntityModule()
                .trackedEntityAttributeValues()
                .byTrackedEntityAttribute().eq(attributeUid)
                .byValue().eq(value)
                .blockingGet()
                .mapNotNull { it.trackedEntityInstance() }
                .toSet()
        }

        // 2️⃣ Intersect all sets (AND logic)
        val intersectedUids = matchingUidSets
            .reduce { acc, set -> acc.intersect(set) }

        if (intersectedUids.isEmpty()) return null


        // 3️⃣ Fetch TEI and ensure correct TEI type
         return d2.trackedEntityModule()
            .trackedEntityInstances()
            .byUid().`in`(intersectedUids.toList())
            .byTrackedEntityType().eq(teiType)
            .blockingGet()
            .firstOrNull()


    }


    fun autoCreateEntity(
        teiUid: String,
        autoCreationConfig: EntityAutoCreationConfig,
    ): Pair<String, String> {
        val sourceTei = d2.trackedEntityModule()
            .trackedEntityInstances()
            .withTrackedEntityAttributeValues()
            .uid(teiUid)
            .blockingGet()

        val orgUnit = sourceTei?.organisationUnit()

        if (orgUnit == null) throw Throwable("Organisation Unit Not Found")

        val targetTeiUid = d2.trackedEntityModule()
            .trackedEntityInstances()
            .blockingAdd(
                TrackedEntityInstanceCreateProjection.builder()
                    .organisationUnit(orgUnit)
                    .trackedEntityType(autoCreationConfig.targetTeiType)
                    .build()
            )

        val enrollmentUid = d2.enrollmentModule().enrollments().blockingAdd(
            EnrollmentCreateProjection.builder()
                .trackedEntityInstance(targetTeiUid)
                .program(autoCreationConfig.targetProgram)
                .organisationUnit(orgUnit)
                .build()
        )

        d2.enrollmentModule().enrollments()
            .uid(enrollmentUid)
            .setEnrollmentDate(Date())
        d2.enrollmentModule().enrollments()
            .uid(enrollmentUid)
            .setIncidentDate(Date())

        // Copy main attributes from source TEI to target TEI
        autoCreationConfig.attributesMappings.forEach { pair ->
            val sourceValue = sourceTei.trackedEntityAttributeValues()?.find {
                it.trackedEntityAttribute() == pair.sourceAttribute
            }?.value() ?: pair.defaultValue
            if (sourceValue != null)
                d2.trackedEntityModule().trackedEntityAttributeValues()
                    .value(pair.targetAttribute, targetTeiUid)
                    .blockingSet(sourceValue)
        }

        d2.relationshipModule().relationships().blockingAdd(
            RelationshipHelper.teiToTeiRelationship(
                teiUid, targetTeiUid, autoCreationConfig.relationshipType
            )
        )

        return targetTeiUid to enrollmentUid
    }

    fun enrollAblePrograms(programUids: List<String>, trackedEntityId: String): List<String> {

        val entity = d2.trackedEntityModule()
            .trackedEntityInstances()
            .withTrackedEntityAttributeValues()
            .uid(trackedEntityId)
            .blockingGet()

        val attributes = entity?.trackedEntityAttributeValues() ?: return emptyList()

        val programEnrollmentControl = getWorkflowConfig().programEnrollmentControl

        return programUids
            .filter { programUid ->
                programEnrollmentControl.none { control ->
                    val attribute = attributes.find {
                        it.trackedEntityAttribute() == control.attributeUid }

                    val attributeValue = attribute?.value() ?: return@none false

                    // Check if value is date and convert it years
                    val isDate = attributeValue.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))
                    val valueToCheck = if (isDate) {
                        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                        val birthDate = dateFormat.parse(attributeValue)
                        val now = Calendar.getInstance().time
                        val diffMillis = now.time - birthDate!!.time
                        val years = diffMillis / (365.2425 * 24 * 60 * 60 * 1000)
                        years.toString()
                    } else {
                        attributeValue
                    }

                    control.programUid == programUid &&
                            !control.isConditionMet(valueToCheck)
                }
            }
    }

    fun evaluateAutoEnrollment(
        triggerProgramUid: String,
        teiUid: String,
        enrollmentUid: String?,
        eventUid: String? = null,
    ): List<String> {
        val config = getWorkflowConfig()
        val autoEnrollmentRules = config.autoEnrollment.filter { it.triggerProgram == triggerProgramUid }
        if (autoEnrollmentRules.isEmpty()) {
            return emptyList()
        }

        val tei = d2.trackedEntityModule()
            .trackedEntityInstances()
            .withTrackedEntityAttributeValues()
            .uid(teiUid)
            .blockingGet() ?: return emptyList()

        val orgUnitUid = tei.organisationUnit() ?: return emptyList()
        val teiAttributes = tei.trackedEntityAttributeValues()
            ?.mapNotNull { attributeValue ->
                val attributeUid = attributeValue.trackedEntityAttribute()
                val value = attributeValue.value()?.trim()
                if (!attributeUid.isNullOrBlank() && !value.isNullOrEmpty()) {
                    attributeUid to value
                } else {
                    null
                }
            }
            ?.toMap()
            ?: emptyMap()

        val enrolledPrograms = mutableListOf<String>()

        autoEnrollmentRules.forEach { rule ->
            if (!isAutoEnrollmentConditionMet(rule, teiAttributes, triggerProgramUid, enrollmentUid, eventUid)) {
                return@forEach
            }

            if (hasBlockingEnrollment(teiUid, rule.targetProgram)) {
                Timber.d("Skipping auto-enrollment for program ${rule.targetProgram}; enrollment already exists")
                return@forEach
            }

            try {
                val newEnrollmentUid = d2.enrollmentModule().enrollments().blockingAdd(
                    EnrollmentCreateProjection.builder()
                        .trackedEntityInstance(teiUid)
                        .program(rule.targetProgram)
                        .organisationUnit(orgUnitUid)
                        .build()
                )

                d2.enrollmentModule().enrollments().uid(newEnrollmentUid).setEnrollmentDate(Date())
                d2.enrollmentModule().enrollments().uid(newEnrollmentUid).setIncidentDate(Date())
                d2.enrollmentModule().enrollments().uid(newEnrollmentUid).setStatus(EnrollmentStatus.ACTIVE)
                enrolledPrograms.add(rule.targetProgram)
                Timber.d("Auto-enrolled TEI $teiUid into program ${rule.targetProgram}")
            } catch (e: Exception) {
                Timber.e(e, "Failed auto-enrolling TEI $teiUid into program ${rule.targetProgram}")
            }
        }

        return enrolledPrograms
    }

    private fun isAutoEnrollmentConditionMet(
        rule: AutoEnrollmentConfig,
        teiAttributes: Map<String, String>,
        triggerProgramUid: String,
        enrollmentUid: String?,
        eventUid: String?,
    ): Boolean {
        if (rule.conditions.isEmpty()) {
            return true
        }

        val results = rule.conditions.map { condition ->
            val actualValue = resolveAutoEnrollmentValue(
                condition = condition,
                teiAttributes = teiAttributes,
                triggerProgramUid = triggerProgramUid,
                enrollmentUid = enrollmentUid,
                eventUid = eventUid,
            )

            evaluateWorkflowCondition(
                actualValue = actualValue,
                expectedValue = condition.value,
                condition = condition.condition,
            )
        }

        return if (rule.combination.equals(AUTO_ENROLL_COMBINATION_OR, ignoreCase = true)) {
            results.any { it }
        } else {
            results.all { it }
        }
    }

    private fun resolveAutoEnrollmentValue(
        condition: AutoEnrollmentCondition,
        teiAttributes: Map<String, String>,
        triggerProgramUid: String,
        enrollmentUid: String?,
        eventUid: String?,
    ): String? {
        return when (condition.sourceType.lowercase()) {
            SOURCE_TYPE_ATTRIBUTE,
            SOURCE_TYPE_TEI_ATTRIBUTE,
            -> teiAttributes[condition.sourceUid]

            SOURCE_TYPE_DATA_ELEMENT,
            SOURCE_TYPE_DATA_ELEMENT_ALT,
            SOURCE_TYPE_EVENT_DATA,
            -> getLatestDataElementValue(
                enrollmentUid = enrollmentUid,
                triggerProgramUid = triggerProgramUid,
                dataElementUid = condition.sourceUid,
                programStageUid = condition.programStageUid,
                eventUid = eventUid,
            )

            else -> null
        }
    }

    private fun getLatestDataElementValue(
        enrollmentUid: String?,
        triggerProgramUid: String,
        dataElementUid: String,
        programStageUid: String?,
        eventUid: String?,
    ): String? {
        if (!eventUid.isNullOrBlank()) {
            val event = d2.eventModule().events()
                .byUid().eq(eventUid)
                .withTrackedEntityDataValues()
                .one()
                .blockingGet()
            val matchesStage = programStageUid.isNullOrBlank() || event?.programStage() == programStageUid
            if (event?.program() == triggerProgramUid && matchesStage) {
                val eventValue = event.trackedEntityDataValues()
                    ?.firstOrNull { it.dataElement() == dataElementUid }
                    ?.value()
                if (!eventValue.isNullOrBlank()) {
                    return eventValue
                }
            }
        }

        if (enrollmentUid.isNullOrBlank()) {
            return null
        }

        val events = if (programStageUid.isNullOrBlank()) {
            d2.eventModule().events()
                .byEnrollmentUid().eq(enrollmentUid)
                .withTrackedEntityDataValues()
                .blockingGet()
        } else {
            d2.eventModule().events()
                .byEnrollmentUid().eq(enrollmentUid)
                .byProgramStageUid().eq(programStageUid)
                .withTrackedEntityDataValues()
                .blockingGet()
        }

        val latestEventWithValue = events
            .filter { event ->
                event.trackedEntityDataValues()?.any { it.dataElement() == dataElementUid } == true
            }
            .maxByOrNull { it.lastUpdated() ?: it.created() ?: it.eventDate() ?: Date(0) }

        return latestEventWithValue
            ?.trackedEntityDataValues()
            ?.firstOrNull { it.dataElement() == dataElementUid }
            ?.value()
    }

    private fun hasBlockingEnrollment(
        teiUid: String,
        targetProgramUid: String,
    ): Boolean {
        val activeEnrollmentExists = d2.enrollmentModule().enrollments()
            .byTrackedEntityInstance().eq(teiUid)
            .byProgram().eq(targetProgramUid)
            .byStatus().eq(EnrollmentStatus.ACTIVE)
            .one()
            .blockingExists()

        if (activeEnrollmentExists) {
            return true
        }

        val onlyEnrollOnce = d2.programModule().programs()
            .uid(targetProgramUid)
            .blockingGet()
            ?.onlyEnrollOnce() == true

        if (!onlyEnrollOnce) {
            return false
        }

        return d2.enrollmentModule().enrollments()
            .byTrackedEntityInstance().eq(teiUid)
            .byProgram().eq(targetProgramUid)
            .one()
            .blockingExists()
    }

}