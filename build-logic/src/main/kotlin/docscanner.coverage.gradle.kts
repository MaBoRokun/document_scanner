plugins {
    id("org.jetbrains.kotlinx.kover")
}

// Every module that applies Kover feeds the project-wide report.
subprojects {
    plugins.withId("org.jetbrains.kotlinx.kover") { rootProject.dependencies.add("kover", this@subprojects) }
}

// Overall coverage of what host tests can reach. Left out on purpose, because only instrumented tests exercise it:
// Compose screens, the camera session, JNI/native adapters, ML Kit and platform adapters, generated code, wiring.
kover {
    reports {
        filters {
            excludes {
                annotatedBy("androidx.compose.runtime.Composable")
                classes(
                    "*ComposableSingletons*",
                    "*ScreenKt*",
                    "*ScreensKt*",
                    "*.BuildConfig",
                    "*_Impl*",
                    "*Factory",
                    "*_AppFunctionInventory*",
                    "*DocumentSearchService",
                    "io.github.maborokun.docscanner.vision.*",
                    "io.github.maborokun.docscanner.ocr.*",
                    "io.github.maborokun.docscanner.feature.scan.CameraSession*",
                    "io.github.maborokun.docscanner.feature.scan.QuickScanKt*",
                    "io.github.maborokun.docscanner.data.AppPrivateImageStore*",
                    "io.github.maborokun.docscanner.data.AndroidPdfExporter*",
                    "io.github.maborokun.docscanner.data.PreferencesSettingsRepository*",
                    "io.github.maborokun.docscanner.app.*",
                    "io.github.maborokun.docscanner.AppModuleKt*",
                    "io.github.maborokun.docscanner.DocScannerApp*",
                    "io.github.maborokun.docscanner.MainActivity*",
                    "io.github.maborokun.docscanner.Screen*",
                    "io.github.maborokun.docscanner.appfunctions.AppFunctionsController*",
                )
            }
        }
        verify { rule { minBound(75) } }
    }
}
