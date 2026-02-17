package org.dhis2.mobile.login.accounts.data.credentials

data class TestingCredentials(
    val server: String,
    var username: String,
    var password: String,
)

val defaultTestingCredentials =
    listOf(
        TestingCredentials(
            server = "https://ccdev.org/ichis",
            username = "",
            password = "",
        ),
        TestingCredentials(
            server = "https://ccdev.org/chistest",
            username = "",
            password = "",
        ),
        /*TestingCredentials(
            server = "https://project.ccdev.org/chisdev",
            username = "",
            password = "",
        ),*/
    )

val trainingTestingCredentials =
    listOf(
        TestingCredentials(
            server = "https://play.dhis2.org/demo",
            username = "android",
            password = "Android123",
        ),
    )
