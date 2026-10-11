plugins {
    alias(libs.plugins.setty.android.library)
    alias(libs.plugins.setty.android.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.aksworns22.setty.core.network"
    compileSdk = 37

    defaultConfig {
        minSdk = 24
    }

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation(libs.retrofit)
    implementation(libs.kotlinx.serialization.json)
}
