package org.dhis2.community.relationships

import com.google.gson.Gson
import org.hisp.dhis.android.core.D2
import org.hisp.dhis.android.core.enrollment.EnrollmentCreateProjection
import org.hisp.dhis.android.core.enrollment.EnrollmentStatus
import org.hisp.dhis.android.core.relationship.Relationship
import org.hisp.dhis.android.core.relationship.RelationshipHelper
import org.hisp.dhis.android.core.relationship.RelationshipItem
import org.hisp.dhis.android.core.trackedentity.TrackedEntityInstance
import org.hisp.dhis.android.core.trackedentity.TrackedEntityInstanceCreateProjection
import timber.log.Timber
import java.util.Date

class RelationshipRepository(
    private val d2: D2,
    //private val res: ResourceManager

) {

    fun getRelationshipConfig(): RelationshipConfig {
        val entries = d2.dataStoreModule()
            .dataStore()
            .byNamespace()
            .eq("community_redesign")
            .blockingGet()

        return entries.firstOrNull { it.key() == "relationships" }
            ?.let { Gson().fromJson(it.value(), RelationshipConfig::class.java) }
            ?: RelationshipConfig(emptyList())
    }

    fun createAndAddRelationship(
        selectedTeiUid: String,
        relationshipTypeUid: String,
        teiUid: String,
        relationshipSide: RelationshipConstraintSide
    ): Result<String> {
        return try {
            val (fromUid, toUid) = when (relationshipSide) {
                RelationshipConstraintSide.FROM -> Pair(teiUid, selectedTeiUid)
                RelationshipConstraintSide.TO -> Pair(selectedTeiUid, teiUid)
            }

            val relationship = RelationshipHelper.teiToTeiRelationship(
                fromUid, toUid, relationshipTypeUid
            )

            val relationshipUid = d2.relationshipModule().relationships().blockingAdd(relationship)
            Result.success(relationshipUid)
        } catch (error: Exception) {
            Timber.e(error)
            Result.failure(error)
        }
    }

    fun deleteRelationship(relationshipUid: String): Result<Unit> {
        return try {
            d2.relationshipModule()
                .relationships()
                .uid(relationshipUid)
                .blockingDelete()
            Result.success(Unit)
        } catch (error: Exception) {
            Timber.e(error)
            Result.failure(error)
        }
    }

    fun getRelatedTeis(
        teiUid: String,
        relationshipTypeUid: String,
        relationship: org.dhis2.community.relationships.Relationship
    ): List<CmtRelationshipViewModel> {
        val relationships: List<Relationship> = d2.relationshipModule().relationships()
            .byRelationshipType().eq(relationshipTypeUid)
            .byItem(RelationshipHelper.teiItem(teiUid))
            .withItems()
            .blockingGet()

        // Extract related TEI UIDs
        val relatedTeiUids: List<String> = relationships.mapNotNull { relationship ->
            val from: RelationshipItem? = relationship.from()
            val to: RelationshipItem? = relationship.to()

            val fromTei = from?.trackedEntityInstance()?.trackedEntityInstance()
            val toTei = to?.trackedEntityInstance()?.trackedEntityInstance()

            when {
                fromTei == teiUid && toTei != null -> toTei
                toTei == teiUid && fromTei != null -> fromTei
                else -> null
            }
        }

        return if (relatedTeiUids.isNotEmpty()) {
            d2.trackedEntityModule().trackedEntityInstances()
                .byUid().`in`(relatedTeiUids)
                .withTrackedEntityAttributeValues()
                .blockingGet().map {
                    mapToCmtModel(it, relationship)
                }
        } else {
            emptyList()
        }
    }

    private fun mapToCmtModel(
        tei: TrackedEntityInstance,
        relationship: org.dhis2.community.relationships.Relationship
    ): CmtRelationshipViewModel {
        val teiTypeUid = tei.trackedEntityType()



        val program = d2.programModule().programs()
            .uid(relationship.relatedProgram.programUid)
            //.withStyle()
            .blockingGet()

        val iconName = program?.style()?.icon()

        /*if (teiTypeUid != null) {
            try {
                val trackedEntityType = d2.trackedEntityModule()
                    .trackedEntityTypes()
                    //.uid(teiTypeUid)
                    .blockingGet()
                iconName = trackedEntityType?.style()?.icon()
                //iconName = getDrawableResource(iconNameFromServer)
            } catch (e: Exception) {
                Timber.e(e, "Error fetching TEI type icon")
            }
        }*/
        Timber.e("The Icon name: $iconName")


        return CmtRelationshipViewModel(
            uid = tei.uid()!!,
            primaryAttribute = tei.trackedEntityAttributeValues()
                ?.firstOrNull {
                    it.trackedEntityAttribute() == relationship.view.teiPrimaryAttribute
                }?.value() ?: "",
            secondaryAttribute = tei.trackedEntityAttributeValues()
                ?.firstOrNull {
                    it.trackedEntityAttribute() == relationship.view.teiSecondaryAttribute
                }?.value() ?: "",
            tertiaryAttribute = tei.trackedEntityAttributeValues()
                ?.firstOrNull {
                    it.trackedEntityAttribute() == relationship.view.teiTertiaryAttribute
                }?.value() ?: "",
            programUid = relationship.relatedProgram.programUid,
            enrollmentUid = d2.enrollmentModule()
                .enrollments()
                .byTrackedEntityInstance().eq(tei.uid()!!)
                .byProgram().eq(relationship.relatedProgram.programUid)
                .blockingGet().firstOrNull()?.uid() ?: "",
            //iconResId = res.getObjectStyleDrawableResource(iconName, R.drawable.ic_default_icon)
            iconName = iconName.toString()

        )
    }

    fun searchEntities(
        relationship: org.dhis2.community.relationships.Relationship,
        keyword: String
    ): CmtRelationshipTypeViewModel {
        val teis = d2.trackedEntityModule()
            .trackedEntityInstances()
            .byProgramUids(listOf(relationship.relatedProgram.programUid))
            .withTrackedEntityAttributeValues()
            .blockingGet()
            .filter {
                it.trackedEntityAttributeValues()?.any {
                    it.value()?.contains(keyword, ignoreCase = true) == true
                } == true
            }.map {
                mapToCmtModel(it, relationship)
            }

        val program = d2.programModule().programs()
            .uid(relationship.relatedProgram.programUid)
            .blockingGet()

        val iconName = program?.style()?.icon()

        return CmtRelationshipTypeViewModel(
            uid = relationship.access.targetRelationshipUid,
            name = "",
            description = "",
            relatedTeis = teis,
            relatedProgramName = relationship.relatedProgram.teiTypeName,
            relatedProgramUid = relationship.relatedProgram.programUid,
            //icon = iconName.toString()

            )

    }

    fun saveToEnroll(
        relationship: org.dhis2.community.relationships.Relationship,
        orgUnit: String,
        programUid: String,
        attributeIncrement: Pair<String, String>?,
    ): Pair<String?, String?> {
        val teiType = relationship.relatedProgram.teiTypeUid

        val teiUid = d2.trackedEntityModule().trackedEntityInstances().blockingAdd(
            TrackedEntityInstanceCreateProjection.builder()
                .organisationUnit(orgUnit)
                .trackedEntityType(teiType)
                .build()
        )

        val enrollmentUid = d2.enrollmentModule().enrollments().blockingAdd(
            EnrollmentCreateProjection.builder()
                .trackedEntityInstance(teiUid)
                .program(programUid)
                .organisationUnit(orgUnit)
                .build()
        )

        d2.enrollmentModule().enrollments()
            .uid(enrollmentUid)
            .setEnrollmentDate(Date())
        d2.enrollmentModule().enrollments()
            .uid(enrollmentUid)
            .setIncidentDate(Date())

        // Handle auto-increment attributes if any
        if (attributeIncrement != null) {
            d2.trackedEntityModule().trackedEntityAttributeValues()
                .value(attributeIncrement.first, teiUid)
                .blockingSet(attributeIncrement.second)
        }

        return teiUid to enrollmentUid
    }

}