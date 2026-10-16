pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "document_scanner"
include(
    ":app",
    ":core:common",
    ":core:designsystem",
    ":domain",
    ":data",
    ":vision",
    ":ocr",
    ":feature-scan",
    ":feature-library",
    ":feature-export",
    ":feature-editor",
    ":feature-settings",
    ":appfunctions",
)
