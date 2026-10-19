plugins {
    alias(libs.plugins.setty.android.library)
    alias(libs.plugins.setty.android.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.setty.android.core.network"

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation(libs.retrofit)
    implementation(libs.kotlinx.serialization.json)
}
