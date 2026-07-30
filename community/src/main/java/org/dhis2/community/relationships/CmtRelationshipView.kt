package org.dhis2.community.relationships

/**
 * Host-side callbacks the [CmtRelationshipPresenter] needs from the screen it drives.
 *
 * Declaring them here (rather than referencing the app's dashboard contract) keeps the presenter —
 * and the whole community relationships feature — inside `:community`, so an upstream core upgrade
 * touches only the thin adapter in the app that implements this interface.
 */
interface CmtRelationshipView {
    fun goToEnrollment(programUid: String, enrollmentUid: String)

    fun goToTeiDashboard(teiUid: String, programUid: String, enrollmentUid: String)

    fun confirmRelationshipRemove(type: String, teiUid: String)

    fun confirmPromoteToHead(type: String, teiUid: String)

    fun confirmPromoteToNewHousehold(type: String, teiUid: String)

    fun refreshDashboardHeader()

    /**
     * Presents the capture org-unit picker for [programUid] and invokes [onSelected] with the
     * chosen org-unit uid. Only reached when more than one capture org unit is available; the
     * single-org-unit case is auto-selected by the presenter without involving the view.
     */
    fun showOrgUnitTreeSelector(programUid: String, onSelected: (orgUnitUid: String) -> Unit)
}
