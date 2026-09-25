# Changelog

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
