package org.dhis2.community.workflow

import com.google.gson.Gson
import org.hisp.dhis.android.core.enrollment.EnrollmentCreateProjection
import org.hisp.dhis.android.core.relationship.RelationshipHelper
import org.hisp.dhis.android.core.trackedentity.TrackedEntityInstanceCreateProjection
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

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


}