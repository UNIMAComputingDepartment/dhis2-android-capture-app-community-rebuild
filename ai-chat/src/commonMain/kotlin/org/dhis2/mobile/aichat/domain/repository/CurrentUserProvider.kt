package org.dhis2.mobile.aichat.domain.repository

import org.dhis2.mobile.aichat.domain.model.UserOrgUnit
import org.dhis2.mobile.aichat.domain.model.UserProgram

interface CurrentUserProvider {
    suspend fun username(): String

    suspend fun captureOrgUnits(): List<UserOrgUnit>

    suspend fun availablePrograms(): List<UserProgram>
}
