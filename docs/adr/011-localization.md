# ADR 011: Languages

Status: accepted

## Context
The app must be usable in English, German, French, Portuguese and Spanish. All UI text was hard-coded in Kotlin.

## Decision
- UI text lives in each module's `res/values/strings.xml` (English, the default) with `values-de`, `values-fr`,
  `values-pt` and `values-es`. The Portuguese is neutral and leans Brazilian. Counts use `plurals`.
- `domain` stays free of Android: `DocumentCategory.label` and the other domain labels remain English (they are search
  keys, not UI). The UI maps each value to a string resource (`DocumentCategory.labelRes`, `PageTint.labelRes`).
- View models never hold display text. Errors and snackbar messages are a `@StringRes` plus the cause (`detail`);
  the screen resolves them with the current locale.
- The system picks the language. `res/xml/locales_config.xml` lists the five languages, so Android 13+ shows them under
  Settings > Apps > Doc Scanner > Language; `androidResources.localeFilters` keeps other library languages out of the APK.
- OCR needs no change: ML Kit's Latin recognizer reads all five languages.

## Known limits
- The default title of a new document ("Scan 2026-10-15"), the PDF title of a multi-document share and the "Receipt
  80 mm" paper name are stored or built in `domain` and stay English.
- The category suggestion under the search field matches the English names only.
- Translations were written without a native-speaker review.
