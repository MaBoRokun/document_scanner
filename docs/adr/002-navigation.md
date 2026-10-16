# ADR-002: Navigation

Status: accepted (2026-10-15)

## Decision
Navigation 3 (`androidx.navigation3`, stable 1.2.0 confirmed on Google Maven 2026-10-15; 1.3.0 is alpha and is not used).

## Why
- Back stack is plain state owned by the app, which fits list-detail on large screens and Material3 adaptive scenes.
- Each feature module exposes a destination key and a content function; only `app` assembles the back stack, so features never depend on each other.

## Alternatives
Navigation Compose 2.x: mature but graph-based and harder to combine with adaptive scenes.

## Consequences
Added when the first multi-screen flow lands (M1). Re-verify the version then.
