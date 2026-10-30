import java.util.Properties

plugins {
    alias(libs.plugins.setty.android.application)
    alias(libs.plugins.setty.android.application.compose)
    alias(libs.plugins.setty.android.hilt)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.google.services)
}

val localProperties = Properties()

localProperties.load(
    rootProject.file("local.properties").inputStream()
)

android {
    namespace = "com.setty.android"

    defaultConfig {
        applicationId = "com.setty.android"
        versionCode = 2
        versionName = "1.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField(
            "String",
            "BASE_URL",
            "\"${localProperties.getProperty("BASE_URL")}\""
        )

        buildConfigField(
            "String",
            "TOSS_CLIENT_KEY",
            "\"${
                localProperties.getProperty(
                    "TOSS_CLIENT_KEY",
                    "test_gck_docs_Ovk5rk1EwkEbP0W43n07xlzm"
                )
            }\""
        )
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.material3)
    implementation(libs.retrofit)
    implementation(libs.logging.interceptor)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.converter.kotlinx.serialization)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.androidx.appcompat)
    implementation(libs.toss.payments)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
    implementation(libs.firebase.config)
    implementation(libs.play.app.update)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(project(":devtools"))
}
