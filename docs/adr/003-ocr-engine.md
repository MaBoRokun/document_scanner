# ADR-003: OCR engine

Status: accepted (2026-10-15); confirmed at M5

## Decision
ML Kit Text Recognition v2 with the **bundled** Latin model (`com.google.mlkit:text-recognition`, 16.0.1 on Google Maven 2026-10-15), hidden behind a `TextRecognizer` interface in `:ocr`.

## Why
- Fully offline: the model ships in the APK, so the manifest never needs `INTERNET` and nothing is downloaded at runtime (risk R6).
- The interface keeps a fake for unit tests and leaves room to swap engines.

## Alternatives
Play-services (unbundled) variant: smaller APK but downloads the model through Play services, which conflicts with the privacy goal. Tesseract via NDK: more control, much more build work.

## Consequences
APK grows by the model size. At M5 re-check the artifact version, supported scripts and licence.

## Outcome (M5)
`com.google.mlkit:text-recognition:16.0.1` bundled model works offline on the emulator and reads all five synthetic
receipts correctly (merchant, date, total, currency; see `MlKitTextRecognizerTest`). Its transitive manifest adds
`INTERNET` and `ACCESS_NETWORK_STATE`; both are removed in the app manifest and guarded by a build check.
Recognised lines are regrouped into printed rows (`joinIntoRows`) because ML Kit returns receipts column by column.
