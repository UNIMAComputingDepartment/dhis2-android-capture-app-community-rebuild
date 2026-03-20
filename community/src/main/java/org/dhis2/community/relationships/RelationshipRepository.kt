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
        Timber.d("getRelationshipConfig() called - START")
        return try {
            val entries = d2.dataStoreModule()
                .dataStore()
                .byNamespace()
                .eq("community_redesign")
                .blockingGet()

            Timber.d("Found ${entries.size} entries in 'community_redesign' namespace")

            val relationshipEntry = entries.firstOrNull { it.key() == "relationships" }

            if (relationshipEntry == null) {
                Timber.w("No relationships entry found in dataStore")
                return RelationshipConfig(emptyList())
            }

            Timber.d("Found relationships entry: ${relationshipEntry.key()}")

            val rawValue = relationshipEntry.value()
            Timber.d("Raw value type: ${rawValue?.javaClass?.name}")
            Timber.d("Raw relationship config value (first 200 chars): ${rawValue?.take(200)}")

            if (rawValue.isNullOrBlank()) {
                Timber.w("Relationship config value is null or blank")
                return RelationshipConfig(emptyList())
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
                Timber.w("Relationship config value does not start with '{': ${value.take(100)}")
                return RelationshipConfig(emptyList())
            }

            val config = Gson().fromJson(value, RelationshipConfig::class.java)
            Timber.d("Successfully parsed relationship config: ${config.relationships.size} relationships")
            config
        } catch (e: Exception) {
            Timber.e(e, "Error parsing relationship config")
            RelationshipConfig(emptyList())
        }
    }

    fun createAndAddRelationship(
        selectedTeiUid: String,
        relationshipTypeUid: String,
        teiUid: String,
        relationshipSide: RelationshipConstraintSide
    ): Result<String> {
        return try {
            // Fetch the relationship type with constraints
            val relationshipType = d2.relationshipModule()
                .relationshipTypes()
                .withConstraints()
                .uid(relationshipTypeUid)
                .blockingGet()
                ?: throw IllegalArgumentException("Relationship type not found: $relationshipTypeUid")

            // Get TEI types for both entities
            val teiType = d2.trackedEntityModule()
                .trackedEntityInstances()
                .uid(teiUid)
                .blockingGet()
                ?.trackedEntityType()
                ?: throw IllegalArgumentException("TEI not found: $teiUid")

            val selectedTeiType = d2.trackedEntityModule()
                .trackedEntityInstances()
                .uid(selectedTeiUid)
                .blockingGet()
                ?.trackedEntityType()
                ?: throw IllegalArgumentException("TEI not found: $selectedTeiUid")

            Timber.d("Current TEI type: $teiType, Selected TEI type: $selectedTeiType")
            Timber.d("Relationship from constraint: ${relationshipType.fromConstraint()?.trackedEntityType()?.uid()}")
            Timber.d("Relationship to constraint: ${relationshipType.toConstraint()?.trackedEntityType()?.uid()}")

            // Determine the correct direction based on TEI types and relationship constraints
            val (fromUid, toUid) = determineRelationshipDirection(
                teiUid = teiUid,
                teiType = teiType,
                selectedTeiUid = selectedTeiUid,
                selectedTeiType = selectedTeiType,
                relationshipType = relationshipType,
                providedSide = relationshipSide
            )

            Timber.d("Creating relationship: from=$fromUid, to=$toUid, type=$relationshipTypeUid")

            val relationship = RelationshipHelper.teiToTeiRelationship(
                fromUid, toUid, relationshipTypeUid
            )

            val relationshipUid = d2.relationshipModule().relationships().blockingAdd(relationship)
            Result.success(relationshipUid)
        } catch (error: Exception) {
            Timber.e(error, "Error creating relationship")
            Result.failure(error)
        }
    }

    private fun determineRelationshipDirection(
        teiUid: String,
        teiType: String,
        selectedTeiUid: String,
        selectedTeiType: String,
        relationshipType: org.hisp.dhis.android.core.relationship.RelationshipType,
        providedSide: RelationshipConstraintSide
    ): Pair<String, String> {
        val fromConstraintTeiType = relationshipType.fromConstraint()?.trackedEntityType()?.uid()
        val toConstraintTeiType = relationshipType.toConstraint()?.trackedEntityType()?.uid()

        // If relationship is bidirectional, use the provided side
        if (relationshipType.bidirectional() == true) {
            Timber.d("Relationship is bidirectional, using provided side: $providedSide")
            return when (providedSide) {
                RelationshipConstraintSide.FROM -> Pair(teiUid, selectedTeiUid)
                RelationshipConstraintSide.TO -> Pair(selectedTeiUid, teiUid)
            }
        }

        // Match TEI types with relationship constraints to determine correct direction
        return when {
            // Current TEI matches FROM constraint and selected TEI matches TO constraint
            teiType == fromConstraintTeiType && selectedTeiType == toConstraintTeiType -> {
                Timber.d("Current TEI matches FROM constraint, selected TEI matches TO constraint")
                Pair(teiUid, selectedTeiUid)
            }
            // Selected TEI matches FROM constraint and current TEI matches TO constraint
            selectedTeiType == fromConstraintTeiType && teiType == toConstraintTeiType -> {
                Timber.d("Selected TEI matches FROM constraint, current TEI matches TO constraint")
                Pair(selectedTeiUid, teiUid)
            }
            // Fallback to provided side if constraints don't match (shouldn't happen in valid data)
            else -> {
                Timber.w("Could not determine direction from constraints, using provided side: $providedSide")
                when (providedSide) {
                    RelationshipConstraintSide.FROM -> Pair(teiUid, selectedTeiUid)
                    RelationshipConstraintSide.TO -> Pair(selectedTeiUid, teiUid)
                }
            }
        }
    }

    fun deleteRelationship(relationshipType: String, teiUid: String, relatedTeiUid: String): Result<Unit> {
        return try {
            val uids = d2.relationshipModule()
                .relationships()
                .byRelationshipType().eq(relationshipType)
                .byItem(RelationshipHelper.teiItem(teiUid))
                .byItem(RelationshipHelper.teiItem(relatedTeiUid))
                .blockingGetUids()
            if (uids.size != 1) {
                throw IllegalStateException("Expected exactly one relationship to delete, found ${uids.size}")
            } else {
                Timber.d("Deleting relationship with UID: ${uids.first()}")
                d2.relationshipModule().relationships().uid(uids.first()).blockingDelete()
                Result.success(Unit)
            }
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
            maxCount = relationship.maxCount
            )

    }

    fun saveToEnroll(
        relationship: org.dhis2.community.relationships.Relationship,
        orgUnit: String,
        programUid: String,
        attributeIncrement: Pair<String, String>?,
        sourceTeiUid: String,
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

        applyAttributeMappings(
            sourceTeiUid = sourceTeiUid,
            targetTeiUid = teiUid,
            mappings = relationship.attributeMappings
        )

        // Handle auto-increment attributes if any
        if (attributeIncrement != null) {
            d2.trackedEntityModule().trackedEntityAttributeValues()
                .value(attributeIncrement.first, teiUid)
                .blockingSet(attributeIncrement.second)
        }

        return teiUid to enrollmentUid
    }

    private fun applyAttributeMappings(
        sourceTeiUid: String,
        targetTeiUid: String,
        mappings: List<AttributeMapping>
    ) {
        if (sourceTeiUid.isBlank() || mappings.isEmpty()) return

        val sourceTei = d2.trackedEntityModule()
            .trackedEntityInstances()
            .withTrackedEntityAttributeValues()
            .uid(sourceTeiUid)
            .blockingGet()

        val sourceAttributes = sourceTei?.trackedEntityAttributeValues()

        mappings.forEach { mapping ->
            val sourceValue = sourceAttributes?.firstOrNull {
                it.trackedEntityAttribute() == mapping.sourceAttribute
            }?.value()

            val valueToSet = sourceValue ?: mapping.defaultValue
            if (valueToSet != null) {
                d2.trackedEntityModule().trackedEntityAttributeValues()
                    .value(mapping.targetAttribute, targetTeiUid)
                    .blockingSet(valueToSet)
            }
        }
    }

}
