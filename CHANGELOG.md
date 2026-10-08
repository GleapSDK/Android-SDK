# Changelog

## 19.2.1
Surveys: questions asking for the email, name or phone of an identified contact are skipped (with the Surveys 2.0 "Skip when known" option). The contact's phone number is now read from the session (it was dropped before), and an open widget gets the new session after every `identify`, `updateContact` and `clearIdentity`. Surveys wait for an `identify` that is queued or running (at most 3 seconds) before they open, so they start with the identified contact.
Surveys show nothing (no loader, dim or page) until the survey has something to show; after 1.2 seconds the loader appears as before. A survey with nothing left to ask closes without anything ever being visible. Other widget opens are unchanged.

## 19.2.0
Surveys 2.0: `FeedbackWillBeSentCallback`, `FeedbackSentCallback` and `OutboundSentCallback` now fire when a Surveys 2.0 survey is completed (the survey saves its own answers and reports completion as `outbound-sent`; until now only `flow-started` was handled). `FeedbackSent` gets the answers, as for a sent legacy survey; `OutboundSent` gets `outboundId` and `formData` plus `outbound`, `surveyId`, `responseId`, `endingId` and `status: "completed"`. The `outbound-<id>-submitted` event is tracked as for legacy surveys, and a response is reported once per process even if the survey reports it again. The SDK announces `surveyCallbacks: true` in `config-update`, so the widget no longer sends it the legacy fallback. Callback answer values are Surveys 2.0 values (rating 1 to 5, priority as a choice id); migrated surveys keep their legacy field names.
Server-triggered surveys (ping and WebSocket actions) now pass `resume`, `resumeData` and `outboundAction` to the survey, like the JavaScript SDK, so a resumed survey keeps its answers.
Full-screen Surveys 2.0 surveys go edge to edge: transparent system bars (icon colors follow the survey background), the survey draws its own safe areas from the insets the SDK sends, and only the keyboard is kept clear. Legacy surveys and older widgets keep the previous layout.
Card surveys dim evenly: the bands kept clear for the system bars and the keyboard get the same dim as the rest of the screen instead of a second, lighter tone.
Slow survey loads are no longer closed after 15 seconds. The widget is only closed after 30 seconds when the page never answered.

## 19.1.0
Capture requests ("show me the issue"): when a workflow, an AI agent or a teammate asks the customer for a screenshot or a screen recording in a conversation, the customer answers from the card in the widget and the SDK captures the app itself: the activity, its dialogs and bottom sheets and SurfaceView content such as maps or video, without MediaProjection, a foreground service or any new permission. A small dark bar appears over the app (the customer can drag it anywhere; it remembers where it was put) with Capture or Start, and while recording only a red dot, the time and a round Stop. Screenshots open in the widget's markup editor. Recordings are H.264 MP4 videos at 2 to 8 frames per second, up to 3 minutes, leave out the time the app spent in the background and are shown in a dark preview (Retake, Send) before they are uploaded. Password fields, card-number and one-time-code fields (Android 8+), windows with `FLAG_SECURE` and views passed to `Gleap.getInstance().maskView(View)` are blacked out in every screenshot and frame; a frame whose masks can't be worked out is dropped instead of being sent. In-app messages that arrive during a capture are shown after it. Every text in the bar and the preview comes from the widget's translations.
Logs requests: workflows, AI agents and teammates can collect the app's console logs, network logs, custom data, environment data and events in the background, the same data a bug report carries. The SDK takes the request from its socket or the ping answer, uploads the logs once (gzipped) and tries a failing upload three times before reporting it failed.
New: `setCaptureEnabled(boolean)` (off: the widget only offers a file upload, and a running capture stops), `setRemoteLogCollectionEnabled(boolean)` (off: logs requests are answered as unsupported and captures are sent without logs), `maskView(View)` / `unmaskView(View)` and `setLogFlushHandler(GleapLogFlushHandler)`, which the React Native, Flutter and Capacitor SDKs use to hand over their buffered logs first.
Screenshots work again on Android 7.1 with hardware acceleration (the SDK returned no screenshot there). Attachment uploads are copied in chunks instead of reading whole files into memory.

## 19.0.0

### Added

- Authenticated conversation files (opt-in per project): a verified `identifyUser` (with the user hash) now gets a short-lived file session, which is kept in memory only, passed to the widget so it can show protected files, refreshed 5 minutes before it expires (and when the app comes back to the foreground) and revoked by `clearIdentity`. An identify with unchanged data is still sent while the file session is missing or expired.
- `Gleap.getInstance().openProtectedFileFromUrl(url)` opens the conversation of a protected file from an emailed link (`?gleapFile=<file id>`) once the user is identified; returns `false` for a URL without a valid file id.
- Color scheme: `Gleap.getInstance().setColorScheme("auto")` matches the widget to the app's dark / light mode. `auto` follows the night mode of the current activity (so `AppCompatDelegate.setDefaultNightMode` is respected) and switches live, `light` / `dark` force a scheme; any other value is treated as `auto`.
- Dark mode uses the dark colors set in the dashboard (header colors, UI color and background: `darkHeaderColor`, `darkHeaderColor2`, `darkHeaderColor3`, `darkColor`, `darkBackgroundColor`), which replace the regular ones. Without dark colors the widget keeps its normal colors.
- Dark mode also uses the dark logo, header image and composer glow set in the dashboard (`darkLogo`, `darkBgImage`, `darkAurora`); an empty dark logo or header image means none in dark mode. Configs saved before these fields existed keep the regular ones.
- `setColorScheme(colorScheme, lightBackgroundColor, darkBackgroundColor)` optionally overrides the background (`#rrggbb`) used in light / dark mode, taking precedence over the dashboard's colors.
- The dashboard's color scheme setting (`colorScheme` in the widget config) applies the same way until `setColorScheme` is called, which overrides it.
- `setColorScheme` only takes effect when "Adapt to dark / light mode" is enabled in the dashboard. While it is disabled (`colorScheme` missing or `default`), the widget is never themed and keeps the dashboard colors, whatever scheme the app sets.
- The scheme applies to the widget, its loading screen, the in-app notifications and modals. Callable before or after `Gleap.initialize`.
- Built-in OkHttp network logs: add `new GleapOkHttpInterceptor()` to your `OkHttpClient` (`addInterceptor`, or `addNetworkInterceptor` to also see the headers OkHttp adds). It logs method, url, headers, status, duration and text bodies (JSON, XML, text and forms, up to 150 KB each) of the newest 30 requests, including failed ones with their error. The response body is copied while your app reads it, so requests and responses are never changed or delayed; binary and streaming bodies (event streams, NDJSON, gRPC) are left out.
- `Gleap.getInstance().attachNetworkLogs(JSONArray)` and `attachConsoleLogs(JSONArray)` for the React Native, Flutter and Capacitor SDKs. Each call replaces the previously attached list; the entries are sent together with the SDK's own logs.
- `RequestType.HEAD` and `RequestType.OPTIONS`.

### Changed

- `GleapMainActivity` now handles `uiMode` configuration changes itself: a dark mode switch re-themes the open widget instead of recreating the activity (which left the widget on its loading background).

### Fixed

- After a ticket was sent from the widget, `isOpened()` returned `false` while the widget was still on screen, so `Gleap.close()` did nothing. The widget now stays marked open until it actually closes.
- The props to ignore and the blacklist for network logs (dashboard, `setNetworkLogPropsToIgnore`, `setNetworkLogsBlacklist`) were never applied on Android. They now are, when a ticket is sent: headers with that name are removed, as are keys in JSON bodies at any depth (`user.password` also works as a path), form fields and url query parameters. Names match case-insensitively. In JSON bodies cut at the 150 KB limit, the values of those keys are masked instead. The authorization, proxy-authorization, cookie and set-cookie headers are always masked, and requests to gleap.io and gleap.ai are never sent.
- Once 25 requests were logged, every new request was dropped and the oldest ones were kept. The network log now keeps the newest 30.
- Starting a conversation emptied the network logs and the `Gleap.log` messages, so a later bug report had none. Both are now kept; `Gleap.log` keeps the newest 500 messages.
- `attachNetworkLogs(Networklog[])` added the logs again on every call instead of replacing them as documented.
- Network log entries now carry the start time of the request and its method, and failed requests are marked as failed with their error.
- Console logs: errors and warnings were often sent as info and cut in the wrong place, Android 5 and 6 sent no logcat output at all, and lines from December read in January got the wrong year. The newest 500 logcat lines are now read correctly, off the main thread, and long lines are shortened (1000 characters, 5000 for errors).
- The SDK started a `logcat` process at launch that never exited.
- A failed `identifyUser` (offline, a timeout, a rate limit or a server error) deleted the stored session and user, and the identify was lost. Both are now kept and the identify runs again with the next session load or when the network comes back; only an identify the API rejects (e.g. an invalid user hash) still clears the session.
- `clearIdentity` did not cancel an `identifyUser` that was waiting for the session, so the logged-out user was identified again on the new session. Answers to requests still in flight during the logout could also bring the previous session back.
- The widget could stay marked as open when its screenshot failed (PixelCopy error, low memory): every `open*` call was ignored, `isOpened()` stayed true, notifications, surveys, banners and modals were dropped and shake stayed off until the app restarted. The widget now opens without a screenshot, and tickets without a screenshot are sent.
- Silent crash reports were only sent together with a screenshot, so they were lost without an activity on screen or when the screenshot failed. They are now sent right away when the screenshot is excluded (the default), otherwise with or without one.
- `sendSilentCrashReport`'s `excludeData` only removed the screenshot: console logs, network logs, custom data, metadata, the event log and attachments were still sent. Every key is now applied, and `replays`, the key used by the dashboard and the React Native, Flutter and Capacitor SDKs, now also excludes the replay on Android.
- A ticket's data leaked into later ones: a crash report sent after a survey answer was posted as another answer to that survey, widget tickets took over the priority of the last crash report, and an action's excluded data stayed excluded for all later tickets.
- The widget could not be closed after its activity was recreated (font size, language or window size changed while it was open). It now loads again and closes normally.
- When the session loaded without an activity on screen (e.g. the app was started from a push), the push group was never registered and the WebSocket and `InitializationDone` were skipped; unregistering after a logout was dropped the same way.
- Events tracked while earlier events were being sent were lost, and every WebSocket reconnect sent `sessionStarted` again. `sessionStarted` is now sent once per session start or identify.
- The event pings (`/sessions/ping`, which deliver `trackEvent`s, page views and the session start) could flood the API: without a session (e.g. after an identify the API rejected) the SDK kept pinging every 3 s, failed pings were retried every 3 s with all queued events, and session starts pushed the queue past its limit of 500 events so it grew without bound. Pings now only go out with a session, one at a time, with the SDK's 15 s connect and 30 s read timeouts, and carry the oldest 100 events or about 256 KB at most; the rest follows right after the ping is delivered. After a 408, a 429, a 5xx or a network error the events stay queued (any other error answer, e.g. 400 or 413, drops them, so they cannot hold back the queue) and the next ping waits 3 s, then 6, 12, 24, 48 and at most 60 s (±20 % jitter), or longer when the answer has a `Retry-After` (seconds or an HTTP date, up to 5 minutes). Any 2xx answer delivers the events and brings back the 3 s interval. The queue keeps at most 500 events; a session start is kept and the oldest other events make room.
- `attachCustomData` replaced all custom data instead of merging into it as documented; later changes to the passed object also changed the tickets' data.
- Links in the widget, banners and modals did nothing on Android 11+ unless the app declared matching `<queries>`.
- The SDK overrode Material's `ThemeOverlay.MaterialComponents.Light.BottomSheetDialog` with an empty style, so bottom sheet dialogs in apps using Gleap lost their transparent background and slide animation.
- Requests had no timeouts, so one connection that never answered held back every later request. They now give up after 15 s without a connection and 30 s without an answer (60 s for tickets and uploads). The SDK's requests also no longer wait in the app's `AsyncTask` queue, nor hold it up.
- The config request is now retried on server errors, and WebSocket reconnects back off from 5 s to at most 60 s instead of retrying every 5 s on one of OkHttp's threads.
- Crashes: a replay interval of 0 in the project settings crashed the app at start; saving the widget's state without a WebView, and `finishImageUpload` without a pending file picker, threw a `NullPointerException`.
- Tickets from the widget named `GleapMainActivity` as the last screen; they now name the app screen the widget was opened over. `buildMode` was always `RELEASE`; it now follows the app (`DEBUG` for debuggable builds). Without an active network the device data failed to collect, and RAM values were sent as 0 on devices set to Arabic or Persian.
- `setLanguage(null)` broke the widget; null or empty now means the device language.
- Replays kept their oldest frames after the first minute and the dashboard played them backwards. They now keep the newest frames in the order they were taken.
- Banners and modals stopped responding after their page tried to show a JavaScript dialog.
- App activities with "Gleap" in their class name got no feedback button, banner or page views.
- The screenshot and replay images of every ticket stayed in the app's cache directory; they are now deleted after the upload. Attachment files are closed after reading, the event log sent with tickets keeps the newest 500 events, and the overlay no longer keeps destroyed activities in memory.
- The SDK wrote a `descriptionEditText` key into a SharedPreferences file named `prefs`, which may be the app's own. It no longer touches that file.
- Security: links from the widget, banners and modals with the schemes `intent:`, `file:`, `content:`, `javascript:` and `data:` are no longer opened, neither through the widget's `open-url` nor as navigations. http(s), `mailto:`, `tel:`, `gleap:` smart links and app deep links open as before.
- Security: the widget's WebView is only granted the microphone and the camera, once the app holds the Android permission. Other WebView permission requests (protected media ids, MIDI devices, ...) are denied.
- The WebSocket only reconnected after a connection failure: when the server closed it cleanly (e.g. during a deploy), the SDK stayed disconnected until the next session start or network change. It now reconnects with the same backoff (5 s up to 60 s, now ±20 % so devices do not reconnect in step), but not after the SDK closed it itself (`clearIdentity`, a new session).
- A ticket answered with a 2xx other than 201 (e.g. 200), or a file upload answered with a 2xx other than 200, counted as failed: the app's `FeedbackSendingFailedCallback` ran instead of `FeedbackSentCallback` and the widget showed an error. Any 2xx now counts as sent.
- OkHttp network logs: request bodies over 150 KB were logged as `[body not captured]`. Like response bodies, they now keep the first 150 KB with the `… [truncated, N bytes]` marker, and ignored keys in the cut JSON are masked. One-shot bodies and bodies without a length are still not written. A cut body whose masked values made it longer than 150 KB also no longer gets a second, wrong byte count.
- `openConversations(showBackButton)` hid the back button when asked to show it. It now matches iOS and the other `open…(showBackButton)` methods; `openConversations()` still shows it.
- `setConfigLoadedCallback` / `setInitializedCallback` set after the config was loaded were never called, as the config is loaded (and the callbacks fire) once per process. Like on iOS, a callback set later is now called once with the loaded config, posted to the main thread, and calling `Gleap.initialize` again with the same SDK key (e.g. after a React Native reload) calls the set callbacks again. Nothing is called before the config was loaded.

### Notes

- If you use the separate `io.gleap:gleap-okhttp-interceptor` artifact, you can remove it: `io.gleap.GleapOkHttpInterceptor` is now part of the SDK under the same name, so `import io.gleap.GleapOkHttpInterceptor;` and `.addInterceptor(new GleapOkHttpInterceptor())` keep working. While it is still declared, Gradle upgrades it to its empty 19.0.0 release, so the build does not fail with a duplicate class error.
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
