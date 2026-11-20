package org.dhis2.community.relationships

import org.dhis2.community.common.readCommunityConfig
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
) {

    fun getRelationshipConfig(): RelationshipConfig =
        d2.readCommunityConfig("relationships", RelationshipConfig(emptyList()))

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
            val programIcon = programIcon(relationship.relatedProgram.programUid)
            d2.trackedEntityModule().trackedEntityInstances()
                .byUid().`in`(relatedTeiUids)
                .withTrackedEntityAttributeValues()
                .blockingGet().map {
                    mapToCmtModel(it, relationship, programIcon)
                }
        } else {
            emptyList()
        }
    }

    /** The related program's style icon; resolved once per list rather than per TEI. */
    private fun programIcon(programUid: String): String? =
        d2.programModule().programs()
            .uid(programUid)
            .blockingGet()
            ?.style()?.icon()

    private fun mapToCmtModel(
        tei: TrackedEntityInstance,
        relationship: org.dhis2.community.relationships.Relationship,
        programIcon: String?
    ): CmtRelationshipViewModel {
        val isHead = relationship.headAttribute?.let { headAttribute ->
            tei.trackedEntityAttributeValues()
                ?.firstOrNull { it.trackedEntityAttribute() == headAttribute }
                ?.value() == "true"
        } ?: false

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
            iconName = programIcon.toString(),
            isHead = isHead
        )
    }

    fun searchEntities(
        relationship: org.dhis2.community.relationships.Relationship,
        keyword: String
    ): CmtRelationshipTypeViewModel {
        val programIcon = programIcon(relationship.relatedProgram.programUid)
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
                mapToCmtModel(it, relationship, programIcon)
            }

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

        // Once the TEI exists, any later failure would leave it as an orphan (a bare TEI with no
        // enrollment that still syncs), so roll it back before propagating the error.
        return try {
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

            teiUid to enrollmentUid
        } catch (error: Exception) {
            deleteTeiBestEffort(teiUid)
            throw error
        }
    }

    fun promoteToHead(
        relationship: org.dhis2.community.relationships.Relationship,
        householdTeiUid: String,
        newHeadTeiUid: String
    ): Result<Unit> {
        val headAttribute = relationship.headAttribute
            ?: return Result.failure(IllegalStateException("Relationship has no headAttribute configured"))

        return try {
            val members = getRelatedTeis(
                teiUid = householdTeiUid,
                relationshipTypeUid = relationship.access.targetRelationshipUid,
                relationship = relationship
            )

            members
                .filter { it.isHead && it.uid != newHeadTeiUid }
                .forEach { previousHead ->
                    d2.trackedEntityModule().trackedEntityAttributeValues()
                        .value(headAttribute, previousHead.uid)
                        .blockingSet("false")
                }

            d2.trackedEntityModule().trackedEntityAttributeValues()
                .value(headAttribute, newHeadTeiUid)
                .blockingSet("true")

            // headPromotionAttributes is household-attribute -> member-attribute (same
            // convention as attributeMappings); promotion runs it in reverse.
            applyAttributeMappings(
                sourceTeiUid = newHeadTeiUid,
                targetTeiUid = householdTeiUid,
                mappings = relationship.headPromotionAttributes.mapNotNull { mapping ->
                    val householdAttribute = mapping.sourceAttribute
                    if (householdAttribute.isNullOrBlank()) {
                        null
                    } else {
                        AttributeMapping(
                            sourceAttribute = mapping.targetAttribute,
                            targetAttribute = householdAttribute,
                            defaultValue = null
                        )
                    }
                }
            )

            Result.success(Unit)
        } catch (error: Exception) {
            Timber.e(error, "Error promoting TEI to household head")
            Result.failure(error)
        }
    }

    fun promoteToNewHousehold(
        membershipRelationship: org.dhis2.community.relationships.Relationship,
        parentRelationshipType: String,
        parentTeiUid: String,
        targetProgram: String,
        targetTeiType: String,
        autoIncrementAttribute: String?,
        oldHouseholdTeiUid: String,
        memberTeiUid: String
    ): Result<Pair<String, String>> {
        // Tracks how far we got so a mid-way failure can be unwound. The new household is only
        // safe to delete on rollback until the member has actually been moved onto it; after that
        // point, deleting it would leave the member with a dangling relationship, so we prefer to
        // leave the (recoverable) state as-is and surface the error.
        var createdHouseholdUid: String? = null
        var memberMoved = false

        return try {
            val orgUnit = d2.trackedEntityModule()
                .trackedEntityInstances()
                .uid(oldHouseholdTeiUid)
                .blockingGet()
                ?.organisationUnit()
                ?: throw IllegalStateException("Organisation unit not found for household $oldHouseholdTeiUid")

            val newHouseholdUid = d2.trackedEntityModule().trackedEntityInstances().blockingAdd(
                TrackedEntityInstanceCreateProjection.builder()
                    .organisationUnit(orgUnit)
                    .trackedEntityType(targetTeiType)
                    .build()
            )
            createdHouseholdUid = newHouseholdUid

            val enrollmentUid = d2.enrollmentModule().enrollments().blockingAdd(
                EnrollmentCreateProjection.builder()
                    .trackedEntityInstance(newHouseholdUid)
                    .program(targetProgram)
                    .organisationUnit(orgUnit)
                    .build()
            )
            d2.enrollmentModule().enrollments().uid(enrollmentUid).setEnrollmentDate(Date())
            d2.enrollmentModule().enrollments().uid(enrollmentUid).setIncidentDate(Date())

            // Same identity attributes used for in-place head promotion, copied
            // member -> new household instead of member -> existing household.
            applyAttributeMappings(
                sourceTeiUid = memberTeiUid,
                targetTeiUid = newHouseholdUid,
                mappings = membershipRelationship.headPromotionAttributes.mapNotNull { mapping ->
                    val householdAttribute = mapping.sourceAttribute
                    if (householdAttribute.isNullOrBlank()) {
                        null
                    } else {
                        AttributeMapping(
                            sourceAttribute = mapping.targetAttribute,
                            targetAttribute = householdAttribute,
                            defaultValue = null
                        )
                    }
                }
            )

            if (autoIncrementAttribute != null) {
                val siblingCount = d2.relationshipModule().relationships()
                    .byRelationshipType().eq(parentRelationshipType)
                    .byItem(RelationshipHelper.teiItem(parentTeiUid))
                    .blockingGetUids()
                    .size
                d2.trackedEntityModule().trackedEntityAttributeValues()
                    .value(autoIncrementAttribute, newHouseholdUid)
                    .blockingSet((siblingCount + 1).toString())
            }

            // Attach the new household to the same parent (Community) as the old one
            createAndAddRelationship(
                selectedTeiUid = parentTeiUid,
                relationshipTypeUid = parentRelationshipType,
                teiUid = newHouseholdUid,
                relationshipSide = RelationshipConstraintSide.FROM
            ).getOrThrow()

            // Attach the member to the new household first, so if this fails we can still roll the
            // new household back cleanly. Only once this succeeds do we drop the old link.
            createAndAddRelationship(
                selectedTeiUid = memberTeiUid,
                relationshipTypeUid = membershipRelationship.access.targetRelationshipUid,
                teiUid = newHouseholdUid,
                relationshipSide = RelationshipConstraintSide.FROM
            ).getOrThrow()
            memberMoved = true

            membershipRelationship.headAttribute?.let { headAttribute ->
                d2.trackedEntityModule().trackedEntityAttributeValues()
                    .value(headAttribute, memberTeiUid)
                    .blockingSet("true")
            }

            // Destructive step last: drop the member's link to the old household.
            deleteRelationship(
                relationshipType = membershipRelationship.access.targetRelationshipUid,
                teiUid = oldHouseholdTeiUid,
                relatedTeiUid = memberTeiUid
            ).getOrThrow()

            Result.success(newHouseholdUid to enrollmentUid)
        } catch (error: Exception) {
            Timber.e(error, "Error promoting TEI to head of a new household")
            if (createdHouseholdUid != null && !memberMoved) {
                deleteTeiBestEffort(createdHouseholdUid)
            }
            Result.failure(error)
        }
    }

    /** Best-effort removal of a just-created TEI when a later step of its creation failed. */
    private fun deleteTeiBestEffort(teiUid: String) {
        try {
            d2.trackedEntityModule().trackedEntityInstances().uid(teiUid).blockingDelete()
        } catch (e: Exception) {
            Timber.e(e, "Failed to roll back orphan TEI $teiUid")
        }
    }

    private fun applyAttributeMappings(
        sourceTeiUid: String,
        targetTeiUid: String,
        mappings: List<AttributeMapping>
    ) {
        if (sourceTeiUid.isBlank() || mappings.isNullOrEmpty()) return

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
