plugins {
    id("docscanner.android-library")
}

android {
    ndkVersion = "28.2.13676358" // AGP 9.4 default; see TECHNICAL_PLAN.md section 5
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
    buildTypes {
        // Debug builds only need the emulator and a modern phone ABI, which keeps CI fast (risk R4).
        debug { ndk { abiFilters += listOf("arm64-v8a", "x86_64") } }
    }
}

// The synthetic images from tools/generate_synthetic.py drive the instrumented detector tests.
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

    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.kotlinx.coroutines.core)
}
