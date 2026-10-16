plugins {
    id("com.android.library")
    id("org.jetbrains.kotlinx.kover")
}

android {
    // :feature-scan -> io.github.maborokun.docscanner.feature.scan
    namespace = "io.github.maborokun.docscanner." +
        project.path.removePrefix(":").replace(':', '.').replace('-', '.')
    compileSdk = 37
    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    // Headless emulators for instrumented tests: ./gradlew pixel6api34DebugAndroidTest (Android 14), and
    // ./gradlew pixel6api37DebugAndroidTest (Android 17: the first image on which this app's app functions index).
    testOptions.managedDevices.localDevices {
        create("pixel6api34") {
            device = "Pixel 6"
            apiLevel = 34
            systemImageSource = "aosp"
        }
        create("pixel6api37") {
            device = "Pixel 6"
            apiLevel = 37
            systemImageSource = "google"
        }
    }
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

dependencies {
    testImplementation(libs.findLibrary("junit").get())
}

// Screenshot tests (Roborazzi) compare against committed baselines; pass -Proborazzi.record to re-record them.
tasks.withType<Test>().configureEach {
    val record = project.hasProperty("roborazzi.record")
    systemProperty("roborazzi.test.record", record.toString())
    systemProperty("roborazzi.test.verify", (!record).toString())
}
