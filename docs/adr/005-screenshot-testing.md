# ADR-005: Screenshot testing

Status: accepted (2026-10-15); adopted in M9

## Decision
Roborazzi (1.76.0 on Maven Central 2026-10-15) on Robolectric, so screenshot tests run on the JVM in CI without an emulator.

## Why
- The official Compose Preview Screenshot Testing plugin (`com.android.compose.screenshot`) is still alpha (0.0.1-alpha16 on Google Maven 2026-10-15).
- Host tests keep CI cheap and deterministic.

## Alternatives
Compose Preview Screenshot Testing: first-party, adopt later once stable. Paparazzi: view-based focus, weaker Compose and Robolectric reuse.

## Consequences
Baselines live in the repo (synthetic content only). Re-verify the version and AGP 9.4 compatibility before adding it.

## Outcome (M9)
Roborazzi 1.76.0 works with AGP 9.4 and Kotlin 2.4 without its Gradle plugin: the tests call `captureRoboImage` with explicit
paths under `src/test/screenshots`, and `docscanner.android-library` sets the `roborazzi.test.verify` / `record` system
properties (`-Proborazzi.record` re-records). Baselines cover the quad overlay (three confidence levels), the library screens,
the receipt detail and the adaptive layouts at compact and expanded widths. Fixture timestamps are midday UTC so rendered dates
do not depend on the time zone; font rendering across operating systems has not been compared (no CI run was possible here),
so if a runner shows tiny pixel differences, re-record on the runner or add a `changeThreshold`.
