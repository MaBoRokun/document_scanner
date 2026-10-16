# ADR-001: Dependency injection

Status: accepted (2026-10-15)

## Decision
Koin (4.2.2 verified on Maven Central 2026-10-15), with modules declared only in `app` (the composition root).

## Why
- Matches the author's other repos; no annotation processing, so builds stay fast and KSP is reserved for Room and AppFunctions.
- `domain` and `core:common` stay free of DI annotations: constructor injection with plain Kotlin classes.

## Alternatives
Hilt: compile-time graph validation, but adds KSP/Gradle plugin cost and ties modules to Android annotations.

## Consequences
Graph errors surface at runtime; mitigate with a `checkModules`-style unit test when Koin is added (M1). Re-verify the version when the dependency is first added.
