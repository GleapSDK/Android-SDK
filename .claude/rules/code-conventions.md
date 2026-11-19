---
paths:
  - "gleap/**/*.java"
  - "gleap/src/main/res/**"
  - "gleap/src/main/AndroidManifest.xml"
---

# Code conventions

For new files and the code you change; do not rewrite other code to match. Copy the examples below, not your neighbours: `Gleap.java`, `GleapCaptureCoordinator.java`, `GleapCaptureBar.java`, `GleapMainActivity.java` and `GleapSessionController.java` are far over every limit. `quality/lint-new.sh` checks the mechanical part on what your branch adds (Android Lint with `quality/lint.xml`, Checkstyle with `quality/checkstyle.xml`); the rest is on you and the review.

Sources: [Android API guidelines](https://android.googlesource.com/platform/developers/docs/+/refs/heads/master/api-guidelines/index.md) (written for the platform, the parts that fit a library), [Kotlin-Java interop guide](https://developer.android.com/kotlin/interop), [Google Java Style](https://google.github.io/styleguide/javaguide.html) (for naming and structure; the code is formatted like Android Studio's defaults, four spaces).

- **Size:** aim for files under 400 lines and never write a new one over 700. Methods 80 lines, counted without blank lines. A file or method already too big must not grow; split by responsibility. New capture logic goes in a new `GleapCapture*` class, new widget commands in a method `GleapWidgetBridge` calls, new public calls on `Gleap` delegate to a package-private class at once.
- **Parameters:** at most four; beyond that, one object. Callback parameters (a SAM interface) go last, so Kotlin callers can pass a trailing lambda.
- **Visibility:** package-private by default, `final class` with a private constructor for a holder of static helpers. `public` only for the API (AGENTS.md, Public API); a test reaches package-private code from the same package.
- **Public API:** every new public method carries `@NonNull` or `@Nullable` (`androidx.annotation`) on parameters and return type, and a Javadoc comment that says what it does, from which thread it may be called, and when the callback runs. Getters start with `get`/`is`, setters with `set`, so Kotlin sees properties. No Kotlin hard keywords (`in`, `is`, `object`, `fun`, `when`, `val`, `var`, `typealias`) as names.
- **Threads:** the public API may be called from any thread. UI and SDK state change on the main thread (`GleapMainThread.post`), API requests run on `GleapExecutor` through `GleapHttp`, nothing blocks the main thread (no network, no Android Keystore, no file reads, no `Thread.sleep`). Callbacks into the app run on the main thread. A field read from more than one thread is `volatile`, final or guarded by a lock next to it.
- **Lifecycle:** never keep an `Activity`, `View` or `Context` other than the application context in a static field or a long-lived object; use a `WeakReference` (as `GleapWidgetBridge`). Every listener, receiver and callback the SDK registers is removed where it is no longer needed.
- **Failures:** the SDK never crashes the app. A public method on `Gleap` runs its body in `GleapErrors.guard("methodName", () -> ...)`, which hands anything thrown to the app's `ErrorCallback`; a bridge message or a callback into the SDK catches at its entry and logs with `GleapLog.w/e(message, error)`. Deeper code lets exceptions travel up. An empty `catch` says why in a comment. No `printStackTrace` and no `System.out`.
- **Logging:** `GleapLog` (tag `Gleap`): never `android.util.Log` directly. Log ids and states, never tokens, user data, request bodies or URLs with query strings.
- **Constants:** a closed set of values that reaches the Messenger, the API or the store is a set of `static final String` constants or an enum with the string kept explicit. Add values, never rename one: old SDK versions and the Messenger keep sending the old ones.
- **Comments:** a Javadoc comment on every public type and method. Other comments say why, in English, in one or two lines. No ticket or PR numbers, people's names, dates or decision histories: those belong in the PR.
- **Dependencies:** no new library (runtime or test) without asking first; every runtime dependency lands in every customer's app.

## One job, one system

| Job | Use | Not in new code |
|---|---|---|
| Logcat | `GleapLog` | `android.util.Log`, `printStackTrace`, `System.out` |
| API requests | `GleapHttp` (`HttpURLConnection`, timeouts, the session headers) on `GleapExecutor`, retries through `GleapRetry` | a second HTTP client, OkHttp for API calls, `AsyncTask`, a `new Thread` per request |
| Errors at the API boundary | `GleapErrors.guard` (public methods), `GleapErrors.report` | a bare `try { } catch (Exception e) { }` |
| Main thread | `GleapMainThread.post` / `postDelayed` | `new Handler(Looper.getMainLooper())`, `runOnUiThread` on a stored activity |
| JSON | `org.json` (`JSONObject`, `JSONArray`) | Gson, Moshi, Jackson |
| Persistent state | `KeyValueStore` / `GleapPreferencesHelper` (encrypted) | a new `SharedPreferences` file, files for small state |
| Messages to the Messenger | `GleapWidgetMessages` | JSON strings built by hand elsewhere |
| WebSocket | `GleapWebSocketListener` (OkHttp) | a second socket |

`quality/checkstyle.xml` fails a new `android.util.Log` or `AsyncTask` import and a new `printStackTrace` or `System.out`.

## Examples to copy

- A small package-private class with one job, its seams for tests and its tests: `GleapRetry` with `GleapRetryTest`, `GleapNetworkLogSanitizer` with its test.
- Main-thread hops that unit tests can run: `GleapMainThread`.
- A bridge that holds its activity weakly and validates its input: `GleapWidgetBridge`.
- A public method on `Gleap` that only delegates, guarded: `setWidgetClosedCallback`; with its Javadoc: `setUserJwtProvider`.
