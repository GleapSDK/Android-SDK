---
paths:
  - "gleap/**/*.java"
---

# Testing

Tests come first and stay fixed while the code is written: an agent that may change a test will change it to get green.

- **New behaviour or a bug fix:** write the test first, from the requirement and not from the code. Use the `test-writer` subagent if you can start one. See it fail for the expected reason and commit it on its own, then write the code without touching it.
- **Refactor without a behaviour change:** tests stay unchanged. Nothing covers the code? Pin today's behaviour with a test first.
- **Never edit, skip or delete an existing test to make a change pass,** and never detect test inputs or hardcode answers. No `@Ignore`, retries or sleeps to make a test pass. If a test looks wrong, stop and report `BLOCKED` with the reason. A test changes only when the required behaviour changed; say so in the PR.

## What needs what

| Code | Test |
|---|---|
| A decision or calculation (payloads, sanitizing, retries and backoff, identity and grants, config, theme, URLs) | a unit test in `gleap/src/test/java/io/gleap/<Class>Test.java` |
| Requests the SDK sends and how it handles the answers (session, identify, events, tickets, uploads) | a unit test through `SdkTestEnvironment`: `FakeGleapServer` records each request and answers with what the test queued |
| Messages to and from the Messenger | a unit test on `GleapWidgetMessages` / the bridge's decision, plus an on-device test when the WebView matters |
| The WebView and widget page, activity lifecycle and rotation, the network stack, the file provider, anything `android.jar` stubs cannot run | an on-device test in `gleap/src/androidTest/java/io/gleap/<Feature>E2ETest.java` on `E2ETestBase` |
| Public API | a unit test that calls it as an app would, through `Gleap` |

Every new path that can drop, repeat or delay work gets a test: a request that fails once, one that keeps failing, an answer that never comes, a call before `Gleap.initialize`, a logout in the middle.

## How tests are written

- JUnit 4 (`org.junit.Test`, `Assert`), one class per behaviour area, test names state the behaviour: `retriesAFailedEventPostAfterTheBackoff`. Failure messages say what was called and what was wanted.
- Through the seams the code already has: `SdkTestEnvironment` (fake server, in-memory store, queued main-thread work via `GleapMainThread.setTestExecutor`, recorded retry delays), the `*ForTesting` setters, `FakeSharedPreferences`, `InMemoryKeyValueStore`. A class that needs a new seam gets a package-private `setXForTesting` / `resetForTesting`, and `SdkTestEnvironment` resets it.
- Unit tests run on `android.jar` stubs that return 0, `false` or `null` for every Android call (`returnDefaultValues`): a test that passes only because an Android call did nothing proves nothing. Move the decision out of the Android call and test that, or write an on-device test.
- No Mockito, Robolectric or other test library without asking.
- No sleep decides a result: run queued work explicitly (`runMainThreadTasks()`), record delays instead of waiting them out. On a device, wait for a condition with a deadline (`E2EEnvironment.waitUntil`, `waitFor`), and give it room for a slow CI emulator.
- Never a real Project ID, Gleap host or user data. On-device tests talk only to `E2EGleapServer`; `E2ENetworkGuard` fails a test that reaches any other host.
- Assert behaviour, not that code ran: the request the server got, the header it carried, the callback the app received, the state after a restart.

## What to run

- While you work: `./gradlew :gleap:testDebugUnitTest --tests 'io.gleap.<Class>Test'`.
- Before you finish: `./gradlew :gleap:testDebugUnitTest` (all of them: seconds), `sh quality/lint-new.sh`, and `./gradlew :app:assembleDebug` when the public API changed.
- On-device tests need a running emulator or device: `./gradlew :gleap:connectedDebugAndroidTest`. Ask before starting one; otherwise CI runs them.

If no test reaches the code you changed, that is not a pass: say in your report what no test covered.
