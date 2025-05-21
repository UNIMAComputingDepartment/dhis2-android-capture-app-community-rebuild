package org.dhis2.community.relationships.data.tei

import org.hisp.dhis.android.core.D2
import org.hisp.dhis.android.core.relationship.Relationship
import org.hisp.dhis.android.core.relationship.RelationshipHelper
import org.hisp.dhis.android.core.relationship.RelationshipItem
import org.hisp.dhis.android.core.trackedentity.TrackedEntityInstance

class RelatedTeiRepository(private val d2: D2) {

    fun getRelatedTeis(
        teiUid: String,
        relationshipTypeUid: String
    ): List<TrackedEntityInstance> {
        val relationships: List<Relationship> = d2.relationshipModule().relationships()
            .byRelationshipType().eq(relationshipTypeUid)
            .byItem(RelationshipHelper.teiItem(teiUid))
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
                .blockingGet()
        } else {
            emptyList()
        }
    }


}
