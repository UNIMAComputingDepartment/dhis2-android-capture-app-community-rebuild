package org.dhis2.community.household

import javax.inject.Inject
import org.hisp.dhis.android.core.D2

class HouseholdUtils @Inject constructor(
    private val d2: D2
) {
    private val householdRelationType = "tobefilled"
    private val householdMemberCodeAttributeUid = "tobefilled"
    private val isHeadAttributeUid = "tobefilled"

    fun assignHouseholdData(
        householdTeiUid: String,
        householdMemberTeiUid: String,
        isHead: Boolean
    ){

        //counting members already in the household
        val relationships =  d2.relationshipModule().relationships().blockingGet()
        val memberCount = relationships.count {
            it.relationshipType() == householdRelationType &&
                    it.from()?.trackedEntityInstance()?.trackedEntityInstance() == householdTeiUid
        }

        val nextMemberCode = memberCount + 1

        //setting the member code (eg 1, 2, 3, etc)
        d2.trackedEntityModule().trackedEntityAttributeValues()
            .value(householdMemberCodeAttributeUid,householdMemberTeiUid)
            .blockingSet(nextMemberCode.toString())

        if (isHead){
            //setting the is head attribute to true for the head of the household
            d2.trackedEntityModule().trackedEntityAttributeValues()
                .value(isHeadAttributeUid, householdMemberTeiUid)
                .blockingSet("true")
        }
    }
}