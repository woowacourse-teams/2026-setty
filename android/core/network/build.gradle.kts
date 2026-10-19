plugins {
    alias(libs.plugins.setty.android.library)
    alias(libs.plugins.setty.android.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "cloud.setty.setty.core.network"

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation(libs.retrofit)
    implementation(libs.kotlinx.serialization.json)
}
