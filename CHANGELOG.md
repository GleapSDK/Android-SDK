# Changelog

## 18.2.0

### Added

- Color scheme: `Gleap.getInstance().setColorScheme("auto")` matches the widget to the app's dark / light mode. `auto` follows the night mode of the current activity (so `AppCompatDelegate.setDefaultNightMode` is respected) and switches live, `light` / `dark` force a scheme, `default` (or null) removes the override so the dashboard setting applies again.
- `setColorScheme(colorScheme, lightBackgroundColor, darkBackgroundColor)` sets the backgrounds (`#rrggbb`) used when the dashboard background doesn't match the active scheme (defaults `#ffffff` / `#18181b`, or the dashboard's light / dark background colors). A dashboard background that already fits the scheme is kept.
- The dashboard's color scheme setting (`colorScheme`, `lightBackgroundColor`, `darkBackgroundColor` in the widget config) is applied the same way; `setColorScheme` overrides it.
- The scheme applies to the widget, its loading screen, the in-app notifications and modals. Callable before or after `Gleap.initialize`.
- Built-in OkHttp network logs: add `new GleapOkHttpInterceptor()` to your `OkHttpClient` (`addInterceptor`, or `addNetworkInterceptor` to also see the headers OkHttp adds). It logs method, url, headers, status, duration and text bodies (JSON, XML, text and forms, up to 150 KB each) of the newest 30 requests, including failed ones with their error. The response body is copied while your app reads it, so requests and responses are never changed or delayed; binary and streaming bodies (event streams, NDJSON, gRPC) are left out.
- `Gleap.getInstance().attachNetworkLogs(JSONArray)` and `attachConsoleLogs(JSONArray)` for the React Native, Flutter and Capacitor SDKs. Each call replaces the previously attached list; the entries are sent together with the SDK's own logs.
- `RequestType.HEAD` and `RequestType.OPTIONS`.

### Changed

- `GleapMainActivity` now handles `uiMode` configuration changes itself: a dark mode switch re-themes the open widget instead of recreating the activity (which left the widget on its loading background).

### Fixed

- The props to ignore and the blacklist for network logs (dashboard, `setNetworkLogPropsToIgnore`, `setNetworkLogsBlacklist`) were never applied on Android. They now are, when a ticket is sent: headers with that name are removed, as are keys in JSON bodies at any depth (`user.password` also works as a path), form fields and url query parameters. Names match case-insensitively. In JSON bodies cut at the 150 KB limit, the values of those keys are masked instead. The authorization, proxy-authorization, cookie and set-cookie headers are always masked, and requests to gleap.io and gleap.ai are never sent.
- Once 25 requests were logged, every new request was dropped and the oldest ones were kept. The network log now keeps the newest 30.
- Starting a conversation emptied the network logs and the `Gleap.log` messages, so a later bug report had none. Both are now kept; `Gleap.log` keeps the newest 500 messages.
- `attachNetworkLogs(Networklog[])` added the logs again on every call instead of replacing them as documented.
- Network log entries now carry the start time of the request and its method, and failed requests are marked as failed with their error.
- Console logs: errors and warnings were often sent as info and cut in the wrong place, Android 5 and 6 sent no logcat output at all, and lines from December read in January got the wrong year. The newest 500 logcat lines are now read correctly, off the main thread, and long lines are shortened (1000 characters, 5000 for errors).
- The SDK started a `logcat` process at launch that never exited.

### Notes

- If you use the separate `io.gleap:gleap-okhttp-interceptor` artifact, remove it: `io.gleap.GleapOkHttpInterceptor` is now part of the SDK under the same name, so `import io.gleap.GleapOkHttpInterceptor;` and `.addInterceptor(new GleapOkHttpInterceptor())` keep working. With both dependencies the build fails with a duplicate class error for `io.gleap.GleapOkHttpInterceptor`.
- `attachNetworkLogs(null)` now needs a cast, e.g. `attachNetworkLogs((Networklog[]) null)`, because of the new `JSONArray` overload.

## 18.1.0

### Added

- Env data controls: `Gleap.getInstance().setEnvDataPropsToIgnore(new String[]{"deviceName", "batteryLevel"})` removes individual env data keys (the device, OS, screen, locale, battery and memory details shown under the Env data tab of a ticket) from every ticket and conversation before it is sent. Each call replaces the previous list; an empty array resets it.
- `Gleap.getInstance().setDisableEnvData(true)` stops collecting env data entirely (tickets arrive with an empty Env data tab); `setDisableEnvData(false)` turns it back on.
- Both can be called before or after `Gleap.initialize` and apply to the next ticket. The per-form "Exclude data → Env data" switch in the dashboard keeps working as before.

## 18.0.0

### Added

- Data regions: `Gleap.getInstance().setRegion("eu" | "us")` sets the API url, the websocket url and the realtime host at once. Call it before `Gleap.initialize`. The default region stays `eu`; unknown regions are ignored with a warning.
- `setRealtimeHost(String)`, `setBannerUrl(String)` and `setModalUrl(String)` to complete the manual host setters next to `setApiUrl`, `setWSApiUrl` and `setFrameUrl`. A manual setter called after `setRegion` overrides that single host.
- The realtime host is passed to the widget with the `session-update` message (`realtimeHost`), matching the JavaScript SDK.

### Fixed

- `setApiUrl` was silently ignored for the session, identify and session update requests when the classes had been loaded before the url was set. The API url is now resolved per request.
- The banner and modal hosts were hardcoded. They are now read from the configuration (`setBannerUrl` / `setModalUrl`), including their navigation guards.

### Notes

- The static widget hosts (`messenger-app.gleap.io/appnew`, `outboundmedia.gleap.io`, `sdk.gleap.io`) are global and are not changed by `setRegion`.
