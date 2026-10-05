# CLAUDE.md: ondevice-doc-scanner

Instructions for future Claude Code sessions in this repo.

## First steps
1. Read `TECHNICAL_PLAN.md` completely before changing anything. Work only on the milestone you were asked to do; stop when its acceptance criteria are met.
2. Re-verify library/plugin/Action versions against official sources before adding or bumping any dependency (the plan lists the sources). Do not use versions from memory.

## Hard rules
- Do not add real data, real personal documents, real patient data or secrets. Synthetic data only.
- Never commit: keystores, `google-services.json`, `local.properties`, `.env`, `.idea/`, build output.
- Clean-room project: do not use code, schemas, names or data from any employer.
- Do not publish artifacts anywhere (Maven, stores). Release automation only via the documented workflows.
- CI workflows: least-privilege `permissions`, `concurrency` with cancel-in-progress, never `pull_request_target`, pin or version-check third-party actions.
- The manifest must never declare `INTERNET`. No telemetry.

## Architecture rules
- `domain` is pure Kotlin (no Android, Compose or Room).
- `vision` hides the C++/JNI code behind Kotlin interfaces that use domain types; keep a fake for tests.
- `feature-*` modules depend on `domain` + `core:*`; no feature-to-feature dependencies; `app` is the only composition root.
- AppFunctions code is isolated in its module, guarded for API 36+, feature-flagged and off by default.
- Dispatchers injected; camera analysis drops frames (`STRATEGY_KEEP_ONLY_LATEST`), never queues them.

## Build and test commands
(To be confirmed in M0; update this section as soon as the wrapper exists.)
```
./gradlew ktlintCheck detekt lint     # style, static analysis, Android lint
./gradlew testDebugUnitTest           # unit tests
./gradlew :app:assembleDebug          # debug APK
```

## Working style
- Small commits with clear messages; one milestone per session.
- Tests accompany logic changes (`runTest`, Turbine for Flows). Inject dispatchers and clocks.
- Record decisions as ADRs in `docs/adr/`.
- Report exactly what you ran and the result; if something failed or was skipped, say so.
