package org.dhis2.mobile.aichat

import org.dhis2.mobile.aichat.domain.model.UserOrgUnit
import org.dhis2.mobile.aichat.domain.model.UserProgram
import org.dhis2.mobile.aichat.domain.repository.CurrentUserProvider

class FakeCurrentUserProvider(
    private val value: String = "tester",
    private val orgUnits: List<UserOrgUnit> = emptyList(),
    private val programs: List<UserProgram> = emptyList(),
) : CurrentUserProvider {
    override suspend fun username(): String = value

    override suspend fun captureOrgUnits(): List<UserOrgUnit> = orgUnits

    override suspend fun availablePrograms(): List<UserProgram> = programs
}
