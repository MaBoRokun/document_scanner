plugins {
    id("docscanner.android-library")
}

// The synthetic receipts from tools/generate_synthetic.py drive the on-device OCR test.
androidComponents {
    onVariants { variant ->
        variant.androidTest
            ?.sources
            ?.assets
            ?.addStaticSourceDirectory(rootProject.file("fixtures/synthetic").path)
    }
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":core:common"))
    // Bundled Latin model (ADR-003): works offline, nothing is downloaded at runtime.
    implementation(libs.mlkit.text.recognition)

    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
}
