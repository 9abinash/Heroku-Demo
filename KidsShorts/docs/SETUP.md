# Setup, build and install

## 1. Prerequisites
- Android Studio (current stable) with Android SDK Platform 35, **or** JDK 17 + the Android command-line tools.
- A device or emulator with Android 8.0+ (API 26) and a recent **Android System WebView / Chrome**.
- A YouTube Data API v3 key (free) – see below.

## 2. Get a YouTube Data API key (every family should use its own)
1. Open Google Cloud Console and create a project.
2. *APIs & Services → Library →* enable **YouTube Data API v3**.
3. *APIs & Services → Credentials → Create credentials → API key*.
4. **Restrict the key** (important):
   - *API restrictions:* YouTube Data API v3 only.
   - *Application restrictions:* Android apps → add package `com.kidsshorts` and the **SHA-1 fingerprint** of the
     certificate that signs your build. For a debug build get it with
     `./gradlew signingReport` (look for the `debug` variant SHA1) or
     `keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android`.
     The app sends `X-Android-Package` and `X-Android-Cert` headers so restricted keys work.
5. Give the key to the app in **one** of two ways:
   - **In the app (recommended):** Parent area → Data & privacy → paste key. Stored encrypted with an Android Keystore key.
   - **Personal debug builds only:** copy `local.properties.example` to `local.properties` and set `YOUTUBE_API_KEY=...`.
     This bakes the key into the APK. Never commit it and never publish such an APK
     (YouTube's Developer Policies forbid shipping credentials in open source).

### Quota
Default 10,000 units/day. Costs used by this app: `channels.list` 1, `playlistItems.list` 1, `videos.list` 1 (per call of
up to 50 IDs). Syncing one channel (4 pages = up to 200 newest uploads, plus stale re-checks) is roughly **8–12 units**,
so a family with a dozen channels refreshing twice a day is far inside the free quota. The app never calls
`search.list` (100 units per call). If quota is exhausted the app says so, stops syncing, and keeps serving already-cached
videos that still pass every rule. A public release would need a quota-extension / compliance audit from Google.

## 3. Build
```bash
./gradlew :domain:test            # pure-Kotlin rules: eligibility, language, screen time, PIN throttle
./gradlew :app:testDebugUnitTest  # YouTube JSON parsing + client
./gradlew :app:assembleDebug      # -> app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:connectedDebugAndroidTest   # instrumented tests (device/emulator required)
```
If `./gradlew` is missing the wrapper jar, run `gradle wrapper --gradle-version 8.9` once.

## 4. Install
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```
Or open the project in Android Studio and press Run.

## 5. First run
1. Read the welcome screen, create a parent PIN (4–8 digits) and **write down the one-time recovery code**.
2. Data & privacy → add your API key.
3. Allowed languages → tick the languages (nothing is allowed until you do).
4. Approved channels → Add → paste a channel link/@handle/ID → *Look up* → *Approve*.
5. Video review → approve/confirm videos whose language YouTube didn't label.
6. Back to child mode.

## Version pins
`gradle/libs.versions.toml` pins a mutually compatible set (AGP 8.7.3, Kotlin 2.0.21, Compose BOM 2024.10.01, Room 2.6.1…).
They were chosen from versions known to work together, not looked up at build time; bump them deliberately.
