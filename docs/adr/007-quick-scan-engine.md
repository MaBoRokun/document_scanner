# ADR-007: Quick scan engine (ML Kit Document Scanner)

Status: accepted (2026-10-15)

## Decision
Offer Google's ML Kit Document Scanner (`play-services-mlkit-document-scanner` 16.0.0) as an optional engine,
selected in Settings. The built-in pipeline stays the default. Quick scan is shown but disabled when
Google Play services is missing, and the scan screen falls back to the built-in scanner in that case.

## Why
- It is a turnkey UI with its own edge detection and cleanup filters; a useful comparison point next to the custom pipeline.
- Pages it returns go through the same `ScanDocument` pipeline (OCR, parsing, storage), so documents look the same.

## Consequences for privacy
- The scanner runs inside Google Play services, not in this app. Play services may download its model by itself.
- This app still never asks for the network: the library's `INTERNET` and `ACCESS_NETWORK_STATE` permissions are
  removed in the app manifest, and the build task `checkNoInternetPermission` fails if `INTERNET` returns.
- The README and the in-app privacy screen say so.

## Limits
Needs API 21+, Play services and about 1.7 GB RAM. It could not be run in CI or on the Play-less emulators used here;
the engine switch, the fallback and the page import are unit tested, the Google UI itself is not.

## Verification (2026-10-16)
On an Android 17 Google APIs emulator the engine could be selected, Scan launched Google's scanner from Play services, and
cancelling returned to the library. The scanner itself showed "Something went wrong, try again later" (its module could not be
fetched in the emulator), so importing pages that really came from Google's scanner was **not** exercised on a device.
