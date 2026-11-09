# Building and installing Fluxie

Fluxie has two editions (Gradle product flavors, dimension `edition`):

| Edition | Application id | What it contains |
|---|---|---|
| `full` | `com.fluxie.browser` | The Compose browser (tabs, bookmarks, history, downloads, find in page, settings, content filter) **and** the Lite screen, switchable in Settings > Mode. |
| `lite` | `com.fluxie.browser.lite` | Only the Lite browser, built from plain Android Views. No Compose, Room, DataStore, Navigation, AppCompat or androidx.webkit are packaged. |

Both can be installed side by side.

## Requirements
- JDK 17, Android SDK with platform 37 and build-tools 37.0.0 (see `app/build.gradle`), network access for Gradle.
- `ANDROID_HOME` (or `local.properties` with `sdk.dir=...`).

## Build
```
./gradlew assembleFullDebug     # app/build/outputs/apk/full/debug/
./gradlew assembleLiteDebug     # app/build/outputs/apk/lite/debug/
./gradlew test                  # unit tests of both editions
```
Release builds (minified, unsigned until you configure signing):
```
./gradlew assembleFullRelease assembleLiteRelease
```

## Install
```
adb install -r app/build/outputs/apk/full/debug/app-full-debug.apk
adb install -r app/build/outputs/apk/lite/debug/app-lite-debug.apk
```
(Check the exact file names in the output folders.)

## Measure APK size and cold start
```
tools/measure.sh 10
```
Needs one connected device or emulator. It prints APK sizes and the median cold-start `TotalTime`
for: full (Normal mode), full (Lite mode) and lite.

## Where the code lives
- `app/src/main` — shared by both editions: URL/search/blocklist logic (`common/`), the Lite browser (`lite/`), resources.
- `app/src/full` — Compose app, Room database, settings, DataStore (full edition only).
- `app/src/lite` — manifest and `is_lite_edition` flag of the lite edition.
- `app/src/test` — unit tests that run for both editions; `app/src/testFull` — Compose/Room/DataStore tests.

## Build with GitHub Actions (no local setup)
`.github/workflows/build.yml` builds both editions on every push, pull request, or manual run.
1. Push the project to GitHub (the workflow file must be at the repository root).
2. Open the **Actions** tab > **Build Fluxie** (use **Run workflow** to start it by hand).
3. When the run is green, download **Fluxie-full-debug** or **Fluxie-lite-debug** from the run's *Artifacts*, unzip, and install the `.apk` on your phone.

These are debug-signed APKs: they install directly but are not store releases. A signed release build needs a keystore stored in repository secrets.
