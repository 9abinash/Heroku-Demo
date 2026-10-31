# Verification record

Written in a sandbox with a JDK, no Android SDK, no Gradle and no access to Google/Maven/Gradle hosts. A Kotlin 2.0.21 compiler
and an API 34 `android.jar` were downloaded from GitHub; a real `org.json` was built from source.

| Area | Status | Evidence |
|---|---|---|
| `:domain` (eligibility, language, screen time, PIN throttle, channel parsing, durations, PIN policy) | **Compiled and tested** | 69 tests pass. Includes every scenario listed in the build brief. |
| Test-suite strength | **Mutation-checked** | 7 deliberate breaks of fail-closed rules (unknown allowed, conflict ignored, disabled channel allowed, empty allowlist falls through, parent block ignored, language mismatch ignored, title-language treated as audio evidence) were each caught. |
| YouTube JSON parsing + API client | **Compiled and tested** | 24 JVM tests on a real `org.json`, incl. "incomplete API data can never become playable". |
| Keystore PIN vault, API-key store, YouTube client, player/WebView host | **Type-checked** against the real `android.jar` | Not executed. `PinManagerInstrumentedTest` exists for a device. |
| Room entities/DAOs, repositories, DI, ViewModels | **Type-checked** with minimal stand-in stubs for Room and Lifecycle | Room's annotation processor (SQL validation) did not run. |
| Compose UI (13 files), `MainActivity`, navigation | **NOT compiled, NOT run** | Only a mechanical check that every used Compose symbol is imported. |
| Instrumented/UI tests (`androidTest`) | **Written, NOT run** | |
| Gradle build, APK | **Not performed** | No APK exists. |

## Review fixes made while writing (found by reading/compiling, not by users)
`data class` with its own `copy`; `return` inside an expression-body function; redundant DAO parameter; implicit-this callable
references; a navigation dead-end after onboarding (no CHILD destination under PARENT); key-status indicator tied to audit-log size.

## Assumptions taken from memory, not verified in this session
`channels.list` `forHandle`/`forUsername` parameters; `X-Android-Package` / `X-Android-Cert` headers for restricted keys; the exact
Referer format accepted by the embedded player; dependency versions in `gradle/libs.versions.toml`; Compose/Material3 API
signatures for the pinned BOM. Re-check each against current documentation on first build.

## First things to do on a real machine
1. `./gradlew :domain:test :app:testDebugUnitTest` – should match the counts above.
2. `./gradlew :app:assembleDebug` – fix any Compose compile errors.
3. Run `:app:connectedDebugAndroidTest` on a device/emulator.
4. Manual: add a real channel, confirm playback works (no error 153), confirm tapping a YouTube suggestion/logo cannot leave the vetted video.
