plugins {
    id("docscanner.jvm-library")
    `java-test-fixtures`
}

// Pure Kotlin: no Android, Compose or Room (CONTRIBUTING.md architecture rules).
dependencies {
    api(libs.kotlinx.coroutines.core)
    testFixturesApi(libs.kotlinx.coroutines.core)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
}

kover.reports.verify.rule { minBound(90) }
