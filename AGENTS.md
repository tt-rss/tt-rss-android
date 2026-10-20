# AGENTS.md

## Project

- Java Android client for Tiny Tiny RSS. No Kotlin.
- One Gradle module: `:org.fox.ttrss`. Its directory name and package contain dots.
- The app sends JSON POST requests to `<ttrss_url>/api/` on a TT-RSS server.

## Build and test

- Use JDK 21. CI uses Temurin 21.
- Install the Android SDK for `compileSdk 37`. The platform package is `platforms;android-37.0`; build-tools is `37.0.0`. Set `ANDROID_HOME` or `sdk.dir` in `local.properties` (gitignored).
- Full CI check: `./gradlew --no-daemon check assembleDebug`. The signed tag job uses `./gradlew --no-daemon check assembleSigned`.
- Run all JVM tests: `./gradlew :org.fox.ttrss:testDebugUnitTest`
- Run one test class: `./gradlew :org.fox.ttrss:testDebugUnitTest --tests "org.fox.ttrss.HeadlinesPageLoaderTest"`
- Lint fails on errors (`abortOnError=true`). The report is `org.fox.ttrss/build/reports/lint-results-debug.xml`.
- Debug APK output: `org.fox.ttrss/build/outputs/apk/debug/org.fox.ttrss-debug.apk`. Its application ID is `org.fox.ttrss.debug`.
- Debug signing reads `DEBUG_KEYSTORE_PATH`, then `~/.android/debug.keystore`. CI restores `DEBUG_KEYSTORE_BASE64` into that path.
- `versionCode` comes from the git commit date (`yyyyMMdd`). `versionName` comes from `YY.MM` plus the short commit hash. A build needs git history.
- The `signed` and `branch` build types need `-PSIGNING_STORE_FILE=...` and related signing properties.

## CI

- `.github/workflows/validate-pr.yml` runs on pull requests to `master`. It runs the checks and uploads no APK.
- `.github/workflows/build-debug-apk.yml` runs on `master` pushes, `v*` tags, and manual dispatch. A `master` push creates release `YY.MM-<shortsha>` with the debug APK. A `v*` tag creates a release with the signed APK.
- In `android-actions/setup-android`, set `packages: 'platform-tools'`. The default `tools` package no longer exists.
- Install `platforms;android-37.0` and `build-tools;37.0.0`. `platforms;android-37` does not exist.
- Do not use `yes | sdkmanager --licenses` in workflows. GitHub runs bash with `pipefail`, and `yes` fails on SIGPIPE. The setup action accepts licenses.
- Required secrets are documented in `.github/CI.md`: `DEBUG_KEYSTORE_BASE64`, `SIGNING_STORE_BASE64`, `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS`, `SIGNING_KEY_PASSWORD`.
- Artifacts are kept for 14 days. Report uploads use `continue-on-error`, so they cannot block a release.

## Architecture

- `Application` is the process singleton. It holds the session ID, API level, and article model.
- The app persists the session ID but sets `m_sessionValid` to false after process start. Always revalidate the session with login. Do not trust a stored session alone.
- `ApiCommon.performRequest()` is the single HTTP path. It uses OkHttp with retries, Gson, Basic auth, and progress callbacks. Models implement `ApiCaller`.
- Network models such as `FeedsModel` and `RootCategoriesModel` use a single-thread executor. They post results to `LiveData` on the main thread.
- UI flow: `LaunchActivity` starts `MasterActivity` (feeds drawer and headlines) or `DetailActivity` (article). `OnlineActivity` owns login and API-level checks. `CommonActivity` holds shared helpers.
- Tablet layouts are in `layout-sw600dp-land/`. The `force_phone_layout` preference selects phone layouts.
- `HeadlinesPageLoader` handles pagination invalidation (`first_id_changed`) with rescans. Do not change this logic without its tests.
- Classes in `types/` are Gson models and `Parcelable`. Keep both roles in mind when you add fields.
- Article HTML is parsed with Jsoup and shown in WebViews.

## Testing

- JVM tests are in `src/test/java`. They use JUnit 4, Mockito, and `InstantTaskExecutorRule`.
- The repo has no instrumentation tests.
- CI is the full verification path when the local Android SDK is missing.
- `unitTests.returnDefaultValues = true`. Calls to `android.*` return defaults. Mock Android classes with Mockito.
- Keep pure logic testable through callbacks. `HeadlinesPageLoader` takes a request interface so tests avoid the network.

## Conventions

- Java files use tabs with width 4. Gradle files use 4 spaces. Use UTF-8 and LF line endings.
- ErrorProne runs on main and test compilation. Build errors block the build. See the disabled check list in `org.fox.ttrss/build.gradle`.
- Edit `res/values/strings.xml` for new strings. Do not edit `res/values-*/strings.xml`; Weblate manages translations.
- Add all user-visible text as string resources. Lint checks translations but disables missing/extra translation errors.
- PRs target `master`. CI ignores changes under `utils/`, dotfiles, and `*.md`. Changes under `.github/` trigger CI.
