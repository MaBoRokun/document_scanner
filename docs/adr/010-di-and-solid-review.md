# ADR 010: Dependency injection and SOLID review

Status: accepted

## Context
After the editor, categories and sharing work, the code was reviewed for dependency injection and the SOLID principles.

## Findings and decisions
**Dependency injection.** The app already used Koin (`app/AppModule.kt`, the only composition root): every
implementation is bound to a domain interface and view models are created with `koinViewModel`. Added `DiGraphTest`,
a plain unit test that verifies every definition in the module can be satisfied, so a missing binding fails the build
instead of crashing a screen. Limit: Koin's `verify` ignores constructor parameters that have default values.

**Single responsibility.**
- Export content rules (one document, several merged, one page) moved from `ExportViewModel` into the domain use case
  `PrepareExport`.
- The live overlay state moved from `ScanViewModel` into `LiveOverlay`; the view model keeps capture, review and saving.
- The largest screen files were split by responsibility (library header, list and bottom bar; page viewer and detail
  cards; editor preview and tools; camera screen and notices).

**Open/closed.** `DocumentClassifier` is now a list of `CategoryRule`s; a new category is a new rule, and the existing
ones are not edited. It is injected into `ScanDocument`.

**Liskov.** No change needed: the fakes honour the interface contracts (they back the same tests as the real
implementations where it matters, for example the Room repository tests).

**Interface segregation.** `DocumentRepository` was one wide interface. It is now `DocumentReader`, `DocumentWriter` and
`PageWriter`, with `DocumentRepository` as their union for the single implementation. Use cases and view models ask for
only what they use; Koin binds the Room implementation to all four types.

**Dependency inversion.** `NativePageRenderer` depended on the concrete `NativePageProcessor`. Both now depend on the
`BitmapOps` interface (`NativeBitmapOps` is the implementation).

## Not changed
- `CameraSession` is still created inside the camera screen. It is a thin wrapper over CameraX that needs the activity's
  lifecycle; a factory could be injected if it ever needs a second implementation.
- `DetailViewModel` talks to the reader and writer directly instead of one use case per action; the actions are single
  calls with no rules.
