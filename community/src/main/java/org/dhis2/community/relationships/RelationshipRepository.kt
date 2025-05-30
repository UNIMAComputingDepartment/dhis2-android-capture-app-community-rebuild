package org.dhis2.community.relationships

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Person
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import com.google.gson.Gson
import org.dhis2.commons.bindings.trackedEntityTypeForTei
import org.hisp.dhis.android.core.D2
import org.hisp.dhis.android.core.analytics.trackerlinelist.TrackerLineListItem.CreatedBy.id
import org.hisp.dhis.android.core.relationship.Relationship
import org.hisp.dhis.android.core.relationship.RelationshipHelper
import org.hisp.dhis.android.core.relationship.RelationshipItem
import org.hisp.dhis.android.core.relationship.RelationshipType
import org.hisp.dhis.android.core.trackedentity.TrackedEntityInstance
import timber.log.Timber

class RelationshipRepository(
    private val d2: D2
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
                }?.value() ?: ""
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


        val trackedEntityTypeUid: String? = try {
            val program = d2.programModule().programs()
                .uid(relationship.relatedProgram.programUid)
                .blockingGet()
            program?.trackedEntityType()?.uid()
        } catch (e: Exception) {
            Timber.e(
                e,
                "Error fetching TrackedEntityType UID: ${relationship.access.targetRelationshipUid}"
            )
            null
        }

        var teiIconPainter: ImageVector = Icons.AutoMirrored.Filled.HelpOutline

        if (trackedEntityTypeUid != null) {
            val trackedEntityType = d2.trackedEntityModule().trackedEntityTypes()
                .uid(trackedEntityTypeUid)
                .blockingGet()

            if (trackedEntityType != null && trackedEntityType.style()?.icon() != null) {
                val iconName = trackedEntityType.style()!!.icon()!!
                teiIconPainter = getPainterForIconName(iconName)

            }

        }

        return CmtRelationshipTypeViewModel(
            uid = relationship.access.targetRelationshipUid,
            name = "",
            description = "",
            icon = teiIconPainter,
            relatedTeis = teis
        )

    }

    private fun getPainterForIconName(iconName: String): ImageVector {

        return when (iconName.lowercase()) {
            "dhis2_ic_profile_person", "person" -> Icons.Filled.Person // Example from dhis2-android-commons
            "dhis2_ic_tracker_event", "event" -> Icons.Filled.Event // Example
            "dhis2_ic_user_outlined", "user" -> Icons.Filled.AccountCircle
            else -> {
                Icons.AutoMirrored.Filled.HelpOutline
            }
        }
    }
}