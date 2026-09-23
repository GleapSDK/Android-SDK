# Gleap Android SDK

![Gleap Android SDK Intro](https://raw.githubusercontent.com/GleapSDK/Gleap-iOS-SDK/main/Resources/GleapHeaderImage.png)

Add AI-native customer support, live chat, in-app bug reporting, a help center and surveys to your Android apps with [Gleap](https://www.gleap.ai). Gleap is an Intercom alternative for software teams that connects customer conversations and feedback with product development.

[SDK documentation](https://docs.gleap.ai/documentation/android/README) · [Website](https://www.gleap.ai) · [Plans and pricing](https://www.gleap.ai/pricing)

## Docs & Examples

Checkout our [documentation](https://docs.gleap.ai/documentation/android/README) for full reference.

## Installation with Maven

Open your project in your favorite IDE. (e.g. Android Studio). Open the **build.gradle** of your project.

**Scroll down to the dependencies**

```
dependencies {
...
}
```

**Add the Gleap SDK to your dependencies**

```
dependencies {
...

implementation group: 'io.gleap', name: 'gleap-android-sdk', version: '18.0.0'
}
```

Sync the gradle file to start the download of the library.

The Gleap SDK is almost installed successfully.
Let's carry on with the initialization 🎉

Open your MainApplication


**Import the Gleap SDK**

Import the Gleap SDK by adding the following import below your other imports.

```
import io.gleap.Gleap;
```

**Initialize the SDK**

The last step is to initialize the Gleap SDK by adding the following Code to the ```onCreate``` method:

```
Gleap.initialize("YOUR_API_KEY", this);
```

(Your API key can be found in the project settings within Gleap)

## Data regions

Gleap projects are hosted in the EU by default. If your project lives in the US region, select the region **before** calling `Gleap.initialize`:

```
Gleap.getInstance().setRegion("us");
Gleap.initialize("YOUR_API_KEY", this);
```

`setRegion` accepts `"eu"` (default) or `"us"` (case-insensitive) and sets all regional hosts at once. Unknown values are ignored with a warning.

| Region | API url | WebSocket url | Realtime host |
|--------|---------|---------------|---------------|
| `eu` (default) | `https://api.eu.gleap.ai` | `wss://ws.eu.gleap.ai` | `sockets.eu.gleap.ai` |
| `us` | `https://api.us.gleap.ai` | `wss://ws.us.gleap.ai` | `sockets.us.gleap.ai` |

**Order matters:** call `setRegion` first, then `Gleap.initialize`. The manual setters (`setApiUrl`, `setWSApiUrl`, `setRealtimeHost`) are still available — a manual setter called after `setRegion` overrides that single host.

The static widget hosts are global and are **not** changed by the region: the messenger frame (`messenger-app.gleap.io/appnew`), banners and modals (`outboundmedia.gleap.io`) and SDK assets (`sdk.gleap.io`). Self-hosted setups can override them with `setFrameUrl`, `setBannerUrl` and `setModalUrl`.
