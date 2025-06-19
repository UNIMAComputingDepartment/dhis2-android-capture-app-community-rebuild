package org.dhis2.community.workflow

import com.google.gson.Gson
import io.reactivex.Single
import org.dhis2.commons.bindings.trackedEntityTypeMainAttributes
import org.dhis2.community.relationships.RelationshipConfig
import org.hisp.dhis.android.core.enrollment.EnrollmentCreateProjection
import org.hisp.dhis.android.core.relationship.RelationshipHelper
import org.hisp.dhis.android.core.trackedentity.TrackedEntityInstanceCreateProjection
import java.util.Date

class WorkflowRepository(
    private val d2: org.hisp.dhis.android.core.D2
) {

    fun getWorkflowConfig(): WorkflowConfig {
        val entries = d2.dataStoreModule()
            .dataStore()
            .byNamespace()
            .eq("community_redesign")
            .blockingGet()

        return entries.firstOrNull { it.key() == "workflow" }
            ?.let { Gson().fromJson(it.value(), WorkflowConfig::class.java) }
            ?: WorkflowConfig(emptyList())
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

}