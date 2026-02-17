package org.dhis2.mobile.login.accounts.data.credentials

data class TestingCredentials(
    val server: String,
    var username: String,
    var password: String,
)

val defaultTestingCredentials =
    listOf(
        TestingCredentials(
            server = "https://project.ccdev.org/chisdev",
            username = "android",
            password = "Android123",
        ),
        TestingCredentials(
            server = "https://ccdev.org/chistest",
            username = "android",
            password = "Android123",
        ),
        TestingCredentials(
            server = "https://ccdev.org/ichis",
            username = "android",
            password = "Android123",
        ),
    )

val trainingTestingCredentials =
    listOf(
        TestingCredentials(
            server = "https://ccdev.org/chistest",
            username = "android",
            password = "Android123",
        ),
        TestingCredentials(
            server = "https://ccdev.org/ichis",
            username = "android",
            password = "Android123",
        ),
    )

val productionDefaultCredentials =
    listOf(
        TestingCredentials(
            server = "https://ccdev.org/chistest",
            username = "",
            password = "",
        ),
        TestingCredentials(
            server = "https://ccdev.org/ichis",
            username = "",
            password = "",
        ),

    )