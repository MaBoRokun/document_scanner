plugins {
    id("docscanner.android-library")
    id("com.google.devtools.ksp")
}

// Isolated on purpose: this is an alpha library for Android 16+ only, off by default (see docs/adr/008-appfunctions.md).
dependencies {
    implementation(project(":domain"))
    implementation(project(":core:common"))
    implementation(libs.androidx.appfunctions)
    ksp(libs.androidx.appfunctions.compiler)

    testImplementation(testFixtures(project(":domain")))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)

    androidTestImplementation(testFixtures(project(":domain")))
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}
