# Gleap Android SDK

The library apps add to get Gleap: the Messenger (live chat, help center, news, AI agent), bug reports with screenshots, replays and network logs, banners, modals, surveys, push actions and protected conversation files. Java only (no Kotlin sources), Java 8 language level, min SDK 21, compile and target SDK 35, AGP 8.8.0 and Gradle 8.10.2 through the wrapper, Groovy build scripts. Runtime dependencies: appcompat 1.3.0, material 1.3.0, okhttp 4.12.0.

- `gleap/`: the SDK, published as `io.gleap:gleap-android-sdk`. Package `io.gleap` (Android namespace `gleap.io.gleap`), callbacks in `io.gleap.callbacks`. `Gleap` is the entry point (`Gleap.initialize(projectId, application)`, `Gleap.getInstance()`); most work happens in package-private classes: the session and identity (`GleapSessionController`, `GleapIdentifyService`, `GleapUserJwt`, the encrypted store `GleapPreferencesHelper`), API requests (`GleapHttp` on `GleapExecutor`'s serial thread), the Messenger in a WebView (`GleapMainActivity`, `GleapWidgetBridge`, `GleapWidgetMessages`), capture and replays (`GleapCapture*`, `GleapFrameRecorder`, `GleapWindowCapture`), network logs (`GleapOkHttpInterceptor`, `GleapNetworkLogSanitizer`), the WebSocket (`GleapWebSocketListener`, OkHttp), protected files (`GleapFileAccess`, `GleapProtectedFiles`, `GleapFileProvider`).
- `app/`: a sample app that uses the SDK as `project(':gleap')`. Not published; CI builds it, so a change that breaks how apps call the SDK shows there.
- `quality/`: the checks of `quality/lint-new.sh` (below).

How it ships:

- **Maven Central:** pushing a tag `X.Y.Z` equal to `VERSION_NAME` in `gradle.properties` runs `.github/workflows/release.yml`: unit tests, signed publish and release through the Central Portal, a GitHub release with the `CHANGELOG.md` section (README.md, Releasing). Apps pin a version, so every SDK version stays in use for years. A published version cannot be replaced: a broken release is fixed by a new one.
- **The Flutter and React Native SDKs** wrap this SDK and compile against it (`android/build.gradle` there pins the version).
- **The Messenger** is a web app that deploys on its own. It runs in the SDK's WebView and talks to it by messages (`GleapWidgetBridge` in, `GleapWidgetMessages` out), so every SDK version still out there talks to today's Messenger.

Terms: a conversation is a ticket, the end user is a session (contact), the project's key is the Project ID (formerly SDK key or API key).

## Commands

```bash
sh .githooks/install.sh                    # once per clone: git push runs quality/lint-new.sh
./gradlew :gleap:testDebugUnitTest          # unit tests: 271 in about a second, a few seconds with a warm Gradle
./gradlew :gleap:testDebugUnitTest --tests 'io.gleap.GleapRetryTest'    # one class (or 'io.gleap.GleapRetryTest.someTest')
sh quality/lint-new.sh                      # Android Lint and Checkstyle on what this branch adds compared with origin/master
sh quality/lint-new.sh --api                # plus japicmp: this build's public classes against the base's build
./gradlew :gleap:assembleRelease            # the AAR: gleap/build/outputs/aar/gleap-release.aar
./gradlew :app:assembleDebug                # the sample app against the SDK
./gradlew :gleap:connectedDebugAndroidTest  # on-device tests; needs a running emulator: ask first
```

JDK 17 (CI and the release use Temurin 17); `/usr/libexec/java_home -v 17` finds one on a Mac. The Android SDK comes from `ANDROID_HOME` or `sdk.dir` in `local.properties`; `quality/lint-new.sh` and the hooks fall back to `~/Library/Android/sdk`. Nothing else to install: Checkstyle 12.3.1 and japicmp 0.27.0 come through Gradle (`quality/quality.init.gradle`), Android Lint with AGP.

`quality/lint-new.sh` judges only what the branch adds: lint findings beyond the ones `quality/lint-baseline.xml` lists for old code, Checkstyle findings on lines the branch added or changed, and with `--api` incompatible changes to the classes in `quality/public-api.txt`. Old code is never cleaned up to make it pass, and the baseline never grows to hide a new finding. The full lint report of a run is `gleap/build/reports/lint-results-debug.html`, for reading.

CI (`.github/workflows/ci.yml`) runs the unit tests, the release AAR and the sample app on every PR and push to `master`, and the on-device tests on an emulator. `.github/workflows/quality.yml` runs `quality/lint-new.sh --api` on PRs with the base branch's `quality/`.

Done means `quality/lint-new.sh` and the unit tests pass, `./gradlew :app:assembleDebug` too when the public API changed, `--api` when a public class changed, and CI is green. Say which commands you ran and what they printed.

New worktree: JDK 17, the Android SDK and `sh .githooks/install.sh` are all it needs; the scripts find the SDK without a `local.properties`. Never read or copy `local.properties`, `~/.gradle/gradle.properties` or a keystore: they can hold the signing and Maven Central credentials.

`git push` runs `quality/lint-new.sh` (`.githooks/pre-push`). Agents fix what it reports and push again; they never use `--no-verify`.

## Code

New files and the code you change follow `.claude/rules/code-conventions.md`, code that runs in the customer's app follows `.claude/rules/host-app.md`, and tests follow `.claude/rules/testing.md`. Read them before your first edit: old code here is no precedent.

## Public API

The public API is what apps and the Flutter and React Native SDKs build against: `quality/public-api.txt` lists it. `Gleap` with all its public methods and nested types, everything in `io.gleap.callbacks`, the enums and classes the wrappers use (`APPLICATIONTYPE`, `GleapActivationMethod`, `GleapLogFlushHandler`, `GleapLogLevel`, `GleapSessionProperties`, `GleapUserJwtProvider`, `PrefillHelper`, `SurveyType`) and the types in `Gleap`'s signatures (`Networklog`, `RequestType`). The manifest entries the SDK merges into the app (`GleapMainActivity`, `GleapFileProvider` with the authority `${applicationId}.gleap.files`) and the behaviour behind every public method count too.

Every other public class in `io.gleap` is public for technical reasons only and is **not API**: `GleapSessionController`, `GleapIdentifyService`, `GleapUpdateSessionService`, `GleapWebSocketListener`, `GleapHelper`, `GleapWebViewMessage`, `NetworkBuffer`, `OpenPushAction`, `PixelCopyTask`, `Gleap.GleapListener`, the `Gleap*Exception` classes (kept: older apps may still catch them), `GleapOkHttpInterceptor` (apps add it to their OkHttp client: keep its constructor and `intercept`), and the manifest classes `GleapMainActivity` and `GleapFileProvider`. Do not make more classes public; new code is package-private unless it is API.

- **Never remove, rename or change the meaning of the API in a minor or patch version.** Add instead: a new method, an overload, a new callback interface. Adding a method to an existing callback interface breaks every app that implements it: add a new interface, or a `default` method only with a reason.
- **Deprecate, don't delete:** keep the behaviour, mark it `@Deprecated` with a Javadoc `@deprecated` naming what to use instead, and say so in `CHANGELOG.md`. Removal needs a major version, which is a product decision: stop and ask.
- **A change to the API updates `CHANGELOG.md` (## Unreleased) and, for a new call, `README.md` in the same PR.** New public methods carry `@NonNull`/`@Nullable` (`androidx.annotation`) and a Javadoc comment; `quality/lint-new.sh` checks both.
- **New behaviour is opted into:** from a dashboard setting or a new call, never because the SDK was updated. A missing setting keeps today's behaviour.

## Security

- **Credentials:** the identity token (`setUserJwt`), the user hash, the grant (`Gleap-Grant`), the file session (`X-File-Session`) and the auth tokens are kept in memory or in the encrypted store (`GleapPreferencesHelper`), never in a log line, a URL, a crash report or a network log, and never sent to a host other than the configured API or files host of the region (`GleapRegion`).
- **WebView input is untrusted.** Every page in the SDK's WebViews can reach its JavaScript bridges, third-party frames included. A bridge message is validated like a request from the internet: no file path, intent or URL from it is used unchecked, and links leave through `GleapExternalLinks`, which blocks dangerous schemes.
- **Files** reach other apps only through `GleapFileProvider` (read-only, from the SDK's cache folder), never as `file://` URIs.
- **Network logs and screenshots** go through `GleapNetworkLogSanitizer` and the masking of masked and `FLAG_SECURE` views (`GleapCapture.maskView`); a new capture path keeps both.
- **No new permission, exported component or manifest entry** without asking: they merge into every customer's app.

## Traps

- `gleap/build.gradle` sets Java 8: no `var`, records, switch expressions or text blocks. Min SDK 21 without core library desugaring: `java.time`, streams, `Optional`, `List.of`, `Map.getOrDefault` and others crash on old devices. Lint's `NewApi` check catches the APIs; guard newer platform calls with `Build.VERSION.SDK_INT`.
- Unit tests run against `android.jar` stubs with `unitTests.returnDefaultValues = true`: every Android call returns 0, `false` or `null` instead of failing. `TextUtils.isEmpty(null)` is `false` there, `Looper` and `Handler` do nothing. Tests use the SDK's seams (`*ForTesting`, `GleapMainThread.setTestExecutor`), not the framework.
- The unit tests' `org.json` is the `org.json:json` artifact, not Android's copy, and a few edge cases behave differently. Code that depends on such an edge case gets an on-device test.
- `VERSION_NAME` in `gradle.properties` is the SDK version the requests report (`BuildConfig.VERSION_NAME`) and what the release tag must equal: never change it outside a release.
- The library sets no consumer ProGuard/R8 rules and builds without minify. New code reached only by reflection (or by name from the WebView) needs a consumer rule, which changes the published AAR: stop and ask.
- Resources merge into the customer's app (`android.nonTransitiveRClass=false`): new resource names start with `gleap_`, and a resource is never renamed or removed while code or layouts in old versions might expect it.
- The publishing plugin (`com.vanniktech.maven.publish` 0.34.0) supports Gradle 8.10 and AGP 8.8 at most: moving Gradle, AGP or the plugin is a change of its own. An AGP upgrade brings new lint checks: regenerate the baseline in that PR with `rm quality/lint-baseline.xml && ./gradlew -I quality/quality.init.gradle :gleap:lintDebug -PqualityLintBaseline -Dlint.baselines.continue=true`, and never at any other time.
- `quality/quality.init.gradle` configures lint through `finalizeDsl`: a plain `android { lint { } }` from an init script runs before `gleap/build.gradle`'s own `lint { }` block, which then wins.
- `:app` compiles against SDK 34 and targets 36, with its own old dependencies (okhttp 4.2.2, LeakCanary in debug). It is a sample: do not take its versions or patterns into the SDK.
- `release.yml` publishes with `--no-configuration-cache`; the configuration cache is off for the whole build. Turning it on is a change of its own.

## Tests

New behaviour and bug fixes start with a test that fails; refactors leave tests unchanged. Full rules: `.claude/rules/testing.md`.

- JUnit 4 (`junit:junit:4.13.2`), `gleap/src/test/java/io/gleap/<Class>Test.java`, through `SdkTestEnvironment` (fake API server `FakeGleapServer`, in-memory store, queued main-thread work, recorded retry delays). No Mockito or Robolectric: ask before adding a test dependency.
- What only a device can show (the WebView and the widget page, activity lifecycle and rotation, the network stack, the file provider) is an on-device test in `gleap/src/androidTest` on `E2ETestBase`: the fake Gleap server on the device (`E2EGleapServer`) and a network guard that fails the test if anything leaves the device. CI runs them on an emulator.
- Never edit, skip or delete an existing test to make a change pass. If one looks wrong, stop and report `BLOCKED` with the reason.

## Customer requests

A ticket is one customer's view. Never special-case one customer in code: no project, organisation or user ids, app ids, names or domains in conditions. If the only fix that works is specific to one customer, stop and ask.

## Stop and ask

Keep going while a step needs no input. Stop and ask before: a version bump, a tag or anything that publishes (`publish*`, `*MavenCentral*`, `gh release`); a change to `release.yml`, `gradle.properties`, any `build.gradle`, `settings.gradle`, the wrapper, ProGuard files or the manifest; a new runtime or test dependency; Kotlin sources (they add the Kotlin standard library to every customer's app); removing or changing anything in the public API; changing a message the SDK and the Messenger exchange; a new permission; starting an emulator or touching a device.

## Production

Merging to `master` publishes nothing. A release is a human pushing the version tag (README.md, Releasing); the workflow checks the tag against `VERSION_NAME` and takes the release notes from `CHANGELOG.md`. A published version is final: to undo one, release a new version.
