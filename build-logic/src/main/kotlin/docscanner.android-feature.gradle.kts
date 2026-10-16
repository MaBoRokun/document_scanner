// Feature modules see only domain + core; never another feature (CONTRIBUTING.md architecture rules).
plugins {
    id("docscanner.android-library")
    id("docscanner.android-compose")
}

dependencies {
    "implementation"(project(":domain"))
    "implementation"(project(":core:common"))
    "implementation"(project(":core:designsystem"))
}
