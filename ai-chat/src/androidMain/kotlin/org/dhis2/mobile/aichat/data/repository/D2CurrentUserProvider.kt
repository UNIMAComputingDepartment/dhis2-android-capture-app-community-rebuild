package org.dhis2.mobile.aichat.data.repository

import org.dhis2.mobile.aichat.domain.model.UserOrgUnit
import org.dhis2.mobile.aichat.domain.model.UserProgram
import org.dhis2.mobile.aichat.domain.repository.CurrentUserProvider
import org.hisp.dhis.android.core.D2
import org.hisp.dhis.android.core.arch.repositories.scope.RepositoryScope
import org.hisp.dhis.android.core.organisationunit.OrganisationUnit

class D2CurrentUserProvider(
    private val d2: D2,
) : CurrentUserProvider {
    override suspend fun username(): String = d2.userModule().user().blockingGet()?.username() ?: "unknown"

    override suspend fun captureOrgUnits(): List<UserOrgUnit> =
        d2
            .organisationUnitModule()
            .organisationUnits()
            .byOrganisationUnitScope(OrganisationUnit.Scope.SCOPE_DATA_CAPTURE)
            .orderByDisplayName(RepositoryScope.OrderByDirection.ASC)
            .blockingGet()
            .map { orgUnit ->
                UserOrgUnit(
                    id = orgUnit.uid(),
                    displayName = orgUnit.displayName() ?: orgUnit.uid(),
                )
            }

    override suspend fun availablePrograms(): List<UserProgram> =
        d2
            .programModule()
            .programs()
            .orderByDisplayName(RepositoryScope.OrderByDirection.ASC)
            .blockingGet()
            .map { program ->
                UserProgram(
                    id = program.uid(),
                    displayName = program.displayName() ?: program.uid(),
                )
            }
}
