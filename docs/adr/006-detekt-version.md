# ADR-006: detekt version (risk R3)

Status: accepted (2026-10-15)

## Decision
detekt 2.0.0-alpha.6 (plugin id `dev.detekt`), configured in `config/detekt/detekt.yml` on top of the default rules.

## Why
- The 1.23.x line predates Kotlin 2.4; the 2.0 alpha builds and passes on this repo (`./gradlew detekt` green on 2026-10-15).
- Composable functions are exempted from `FunctionNaming` via `ignoreAnnotated`.

## Alternatives
detekt 1.23.8: stable, but needs a Kotlin-version workaround.

## Consequences
An alpha linter can change rule names between releases; Dependabot bumps are reviewed, and falling back to 1.23.8 is a one-line catalog change plus a plugin-id swap.
