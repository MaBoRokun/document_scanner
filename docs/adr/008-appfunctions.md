# ADR-008: AppFunctions (assistant search)

Status: accepted (2026-10-15); re-check on every AppFunctions release (risk R1)

## Findings when implementing (docs read 2026-10-15)
- Latest is `androidx.appfunctions:appfunctions` and `appfunctions-compiler` **1.0.0-alpha13** (the plan said alpha12).
  `appfunctions-service` stopped at alpha09 and is not needed: everything is in `appfunctions`.
- alpha13 added `@AppFunctionDeclaration` (with `isEnabled`, `isDescribedByKDoc`) next to the old `AppFunction`
  interface, and removed `AppFunctionContext` from the public surface. Functions live in an abstract class annotated
  `@AppFunctionServiceEntryPoint(serviceName, appFunctionXmlFileName)`; KSP generates the service and an XML description.
- The official guide still shows alpha10 names; the API shape was read from the alpha13 artifact itself.
- Requires Android 16 (API 36). The system indexes the service from the manifest and calls it from assistants.

## Decision
- One function, `searchDocuments(query)`, in its own module `:appfunctions`, declared `isEnabled = false`.
- It returns `DocumentSummary` only: title, scan date, total, currency and page count. No images, text or paths
  (a unit test pins the field list).
- A Settings switch (off by default, disabled below Android 16) calls `AppFunctionManager.setAppFunctionEnabled`;
  the app applies the stored value at every start and retries briefly while the system is still indexing.
- The service gets its search object from `AppFunctionsHost`, set in `Application.onCreate`, because the system
  creates the service itself and may start a fresh process.

## Consequences
Alpha API: expect renames. All API 36 code sits behind `@RequiresApi(36)` and a version guard, so older devices never
load it. Verification is described below and in the README.

## Verification (2026-10-16)
- Android 16.0 (API 36, AOSP image from mid-2025): the system did not index the function. That platform build predates the
  `android.app.appfunctions.v2` metadata the alpha13 library declares, and `cmd app_function` has no shell implementation there.
- Android 17 (API 37.0, Google APIs image): `searchAppFunctions` returns `BaseDocumentSearchService#searchDocuments`, it is
  `enabledByDefault=false`, `AppFunctionsController.apply(true/false)` flips it, `adb shell cmd app_function list-app-functions`
  shows the full metadata, and `execute-app-function` after enabling it in Settings returns a successful (empty) result for an
  empty library. `AppFunctionRegistrationTest` automates the index and enable checks.
- The system indexer also requires an `app_metadata` XML resource (`AppFunctionAppMetadata` element with `description` and
  `displayDescription`), which the official guide does not describe; the format was taken from the android/snippets sample.
- `DocumentSearchTest` pins that results carry only title, date, total, currency and page count.
