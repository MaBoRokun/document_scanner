plugins {
    id("docscanner.android-library")
    id("docscanner.android-compose")
}

android {
    testOptions.unitTests.isIncludeAndroidResources = true
}

dependencies {
    // Icons and Material 3 are part of the design system's public surface: features draw with them.
    api(platform(libs.androidx.compose.bom))
    api(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.core.ktx)
    implementation(project(":core:common"))
    implementation(project(":domain"))

    // Screenshot tests on the JVM (ADR-005): Robolectric native graphics + Roborazzi.
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    // Compose tests use Espresso; 3.7 is the first that runs on Android 17 (older ones call a removed InputManager API).
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    debugImplementation(platform(libs.androidx.compose.bom))
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
