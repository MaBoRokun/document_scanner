# ADR-004: Edge detector approach

Status: accepted (2026-10-15)

## Decision
A classical C++ pipeline (grayscale, blur, adaptive threshold/Canny, contour approximation, largest convex quadrilateral, confidence from area/aspect/edge strength) behind the Kotlin `EdgeDetector` interface. It is stateless per call, so it is re-entrant.

## Why
- No openly licensed corner-detection model is known (risk R5); the classical pipeline needs none and runs at camera frame rate.
- The Kotlin interface allows a fake detector in unit tests.

## Alternatives
LiteRT learned corner model (2.2.0): optional later as a validator or replacement, only with an openly licensed model recorded in `docs/`.

## Consequences
CMake and NDK arrive in M3 (NDK 28.2.13676358, the AGP 9.4 default); debug builds limit ABIs to keep CI fast (R4).
