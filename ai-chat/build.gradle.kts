import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSetTree

plugins {
    kotlin("multiplatform")
    alias(libs.plugins.compose)
    id("com.android.library")
    alias(libs.plugins.kotlin.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xcontext-parameters")
    }

    androidTarget {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        instrumentedTestVariant.sourceSetTree.set(KotlinSourceSetTree.test)
    }

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.ui)
            implementation(compose.material3)
            api(compose.materialIconsExtended)
            implementation(compose.components.resources)
            implementation(libs.navigation.compose)
            implementation(libs.lifecycle.runtime.compose)
            implementation(project(":commonskmm"))
            implementation(libs.kotlin.serialization.json)
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.composeVM)
            implementation(libs.dhis2.mobile.designsystem)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.test.kotlinCoroutines)
            implementation(libs.test.turbine)
            implementation(libs.test.mockitoKotlin)
        }

        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.coreKtx)
            implementation(libs.androidx.work)
            implementation(project(":commons"))
            implementation(libs.dhis2.android.sdk)
            implementation(libs.koin.android)
            implementation(libs.koin.androidx.compose)
            implementation(libs.network.okhttp)
            implementation(libs.network.okhttp.logging)
            implementation(libs.network.retrofit)
            implementation(libs.network.retrofit.converter.gson)
            implementation(libs.database.room.runtime)
            implementation(libs.database.room.ktx)
            implementation(libs.markdown.markwon.core)
            implementation(libs.markdown.markwon.tables)
            implementation(libs.markdown.markwon.strikethrough)
        }

        androidUnitTest.dependencies {
            implementation(libs.test.junit)
            implementation(libs.test.kotlinCoroutines)
        }
    }
}

android {
    namespace = "org.dhis2.mobile.aichat"
    compileSdk = libs.versions.sdk.get().toInt()

    sourceSets["main"].manifest.srcFile("src/androidMain/AndroidManifest.xml")

    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        val remoteDefaultUrl = "https://ccdev.org/ai/"
        val configuredBaseUrl =
            System.getenv("AI_CHAT_BASE_URL")
                ?: System.getProperty("ai.chat.baseUrl")
                ?: remoteDefaultUrl

        val localhostFallback = "http://10.0.2.2:8080/"

        buildConfigField("String", "AI_CHAT_BASE_URL", "\"$configuredBaseUrl\"")
        buildConfigField("String", "AI_CHAT_BASE_URL_LOCALHOST", "\"$localhostFallback\"")
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    flavorDimensions += listOf("default")
    productFlavors {
        create("dhis2") { dimension = "default" }
        create("dhis2PlayServices") { dimension = "default" }
        create("dhis2Training") { dimension = "default" }
    }
}

dependencies {
    add("kspAndroid", libs.database.room.compiler)
    implementation(libs.network.okhttp.logging)
}
