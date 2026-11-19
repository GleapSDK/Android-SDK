---
paths:
  - "gleap/src/main/**"
---

# Code in the customer's app

This code runs inside apps we do not own, next to their own libraries, threads, themes and R8 rules, on devices from Android 5 to the newest release. For new code and the code you change; do not rewrite old code to match. Each rule names the code that already does it right.

## Leave the app as it was

- **The main thread is the app's.** Nothing slow runs on it: no network, no Android Keystore, no file or bitmap work beyond a frame. `Gleap.initialize` returns at once; the stored session loads on Gleap's own thread first in line (`GleapSessionController`). A public call made right after `initialize` queues until the session is ready instead of blocking.
- **No leaks.** Activities, views and contexts are held weakly or only while they are shown (`GleapWidgetBridge`, `GleapActivityTracker`). Lifecycle callbacks, receivers and listeners the SDK registers are removed when its feature stops. LeakCanary in `:app` shows leaks while trying a change by hand.
- **The app's look stays the app's.** Overlays, banners and the Messenger use the SDK's own themes and views; never change the app's window flags, theme, status bar or insets except on the SDK's own activity (`GleapMainActivity`) or the overlay views it adds and removes.
- **Merged into the app:** the manifest, resources and dependencies. No new permission, exported component, intent filter or `uses-feature`; resource names start with `gleap_`; no new runtime dependency (AGENTS.md, Stop and ask). The `FileProvider` authority stays `${applicationId}.gleap.files`, so two apps never clash.
- **R8 in the app:** the library ships no consumer rules. Code that the app's R8 might strip or rename because nothing calls it directly (reflection, names in strings, WebView bridges beyond `@JavascriptInterface` methods) needs a consumer rule: stop and ask.

## Every Android version

- `minSdk 21`: a call to a newer API sits behind `Build.VERSION.SDK_INT >= Build.VERSION_CODES.X` in the same method, or in a method annotated `@RequiresApi(X)` that is only called behind that check. Lint's `NewApi` fails anything else. A behaviour that differs per version (edge to edge from 35, predictive back, foreground-service and notification permissions) is decided in one place and tested per version on a device where it matters.
- New Android behaviour applies by the app's `targetSdkVersion`, not ours: read it from `getApplicationInfo().targetSdkVersion` (as `GleapMainActivity.usesKeyboardPaddingOnly` does) instead of assuming it.

## WebViews and the Messenger

- Pages in the SDK's WebViews (widget, banner, modal) reach the bridges `GleapWidgetBridge`, `GleapBanner` and `GleapModal`, from every frame. A bridge method parses its JSON defensively, ignores unknown commands, and hops to the main thread before it touches views. It never opens a URL, file or intent from a message without the checks of `GleapExternalLinks` and `GleapProtectedFiles`.
- Messages the SDK sends are built in `GleapWidgetMessages`. The Messenger deploys on its own and old SDKs stay in apps for years: add commands and fields, never rename or remove one, and announce a new ability through the capabilities the SDK sends (`session-update`) instead of assuming a Messenger version.
- `setJavaScriptEnabled(true)` only on the SDK's own WebViews, which load only Gleap's pages; any other navigation leaves through `GleapExternalLinks`.

## The user's data

- What a screenshot, replay or network log captures goes through the masking (`GleapCapture.maskView`, `FLAG_SECURE` windows) and `GleapNetworkLogSanitizer` before it leaves the device. A new source of captured data applies both.
- Credentials (identity token, user hash, grant, file session, auth tokens) live in memory or `GleapPreferencesHelper` and go only to the configured region's API or files host (`GleapRegion`). Never log them, never put them in a URL.
- A request carries what the API needs and nothing more: no device identifiers beyond what `PhoneMeta` already sends, no app data the app did not pass in.
