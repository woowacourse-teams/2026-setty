plugins {
    alias(libs.plugins.setty.android.library)
    alias(libs.plugins.setty.android.library.compose)
    alias(libs.plugins.setty.android.hilt)
}

android {
    namespace = "com.aksworns22.setty.devtools"
}

dependencies {
    api(libs.okhttp)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.datastore.preferences)
}
