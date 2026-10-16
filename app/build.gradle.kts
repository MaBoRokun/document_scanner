plugins {
    id("com.android.application")
    id("docscanner.android-compose")
}

android {
    namespace = "io.github.maborokun.docscanner"
    compileSdk = 37
    defaultConfig {
        applicationId = "io.github.maborokun.docscanner"
        minSdk = 26
        targetSdk = 37
        versionCode = 3
        versionName = "1.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    // The app is translated into these languages; other languages from libraries are not packaged.
    androidResources.localeFilters += listOf("en", "de", "fr", "pt", "es")

    signingConfigs {
        // Release signing only when the CI release job provides the keystore via env (owner-added secrets).
        System.getenv("KEYSTORE_PATH")?.let { keystorePath ->
            create("release") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
        }
    }
}

// Privacy gate (CONTRIBUTING.md): fail the build if any merged manifest asks for the network.
androidComponents {
    onVariants { variant ->
        val manifest = variant.artifacts.get(com.android.build.api.artifact.SingleArtifact.MERGED_MANIFEST)
        val gate =
            tasks.register("check${variant.name.replaceFirstChar { it.uppercase() }}NoInternetPermission") {
                inputs.file(manifest)
                doLast {
                    val xml =
                        manifest.get().asFile.readText().replace(
                            Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL),
                            "",
                        )
                    check(!Regex("""<uses-permission[^>]*android\.permission\.INTERNET""").containsMatchIn(xml)) {
                        "The merged manifest of ${variant.name} declares INTERNET"
                    }
                }
            }
        tasks.named("check") { dependsOn(gate) }
    }
}

// The only composition root: it may see every module.
dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:designsystem"))
    implementation(project(":domain"))
    implementation(project(":data"))
    implementation(project(":vision"))
    implementation(project(":ocr"))
    implementation(project(":feature-scan"))
    implementation(project(":feature-library"))
    implementation(project(":feature-export"))
    implementation(project(":feature-editor"))
    implementation(project(":feature-settings"))
    implementation(project(":appfunctions"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.koin.android)
    implementation(libs.play.services.base)
    implementation(libs.koin.androidx.compose)
    testImplementation(libs.junit)
    testImplementation(libs.koin.test)
    testImplementation(kotlin("reflect"))
}
