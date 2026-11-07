# Gleap Android SDK

![Gleap Android SDK Intro](https://raw.githubusercontent.com/GleapSDK/Gleap-iOS-SDK/main/Resources/GleapHeaderImage.png)

Add AI-native customer support, live chat, in-app bug reporting, a help center and surveys to your Android apps with [Gleap](https://www.gleap.ai). Gleap is an Intercom alternative for software teams that connects customer conversations and feedback with product development.

[SDK documentation](https://docs.gleap.ai/en/articles/466-android-sdk-setup) · [Website](https://www.gleap.ai) · [Plans and pricing](https://www.gleap.ai/pricing)

## Docs & Examples

Checkout our [documentation](https://docs.gleap.ai/en/articles/466-android-sdk-setup) for full reference.

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

implementation group: 'io.gleap', name: 'gleap-android-sdk', version: '19.5.0'
}
```

Sync the gradle file to start the download of the library.

The Gleap SDK is almost installed successfully.
Let's carry on with the initialization 🎉

Open your MainApplication


**Import the Gleap SDK**

Import the Gleap SDK by adding the following import below your other imports.

```java
import io.gleap.Gleap;
```

**Initialize the SDK**

The last step is to initialize the Gleap SDK by adding the following Code to the ```onCreate``` method:

```java
Gleap.initialize("YOUR_PROJECT_ID", this);
```

In Kotlin:

```kotlin
import io.gleap.Gleap

Gleap.initialize("YOUR_PROJECT_ID", this)
```

Your Project ID is in the [Gleap dashboard](https://app.gleap.io) under **Settings → Project → General**. It used to be called "SDK key" or "API key": the value is the same, so existing keys keep working.

## Data regions

Gleap projects are hosted in the EU by default. If your project lives in the US region, select the region **before** calling `Gleap.initialize`:

```
Gleap.getInstance().setRegion("us");
Gleap.initialize("YOUR_PROJECT_ID", this);
```

`setRegion` accepts `"eu"` (default) or `"us"` (case-insensitive) and sets all regional hosts at once. Unknown values are ignored with a warning.

| Region | API url | WebSocket url | Realtime host |
|--------|---------|---------------|---------------|
| `eu` (default) | `https://api.eu.gleap.ai` | `wss://ws.eu.gleap.ai` | `sockets.eu.gleap.ai` |
| `us` | `https://api.us.gleap.ai` | `wss://ws.us.gleap.ai` | `sockets.us.gleap.ai` |

**Order matters:** call `setRegion` first, then `Gleap.initialize`. The manual setters (`setApiUrl`, `setWSApiUrl`, `setRealtimeHost`) are still available — a manual setter called after `setRegion` overrides that single host.

The static widget hosts are global and are **not** changed by the region: the messenger frame (`messenger-app.gleap.io/appnew`), banners and modals (`outboundmedia.gleap.io`) and SDK assets (`sdk.gleap.io`). Self-hosted setups can override them with `setFrameUrl`, `setBannerUrl` and `setModalUrl`.

## Env data

With every ticket the SDK sends env data (device model, OS version, screen size, locale, battery and memory state, …), shown under the **Env data** tab in Gleap. To leave out individual keys, pass them to `setEnvDataPropsToIgnore`; to stop collecting env data entirely, use `setDisableEnvData`:

```
Gleap.getInstance().setEnvDataPropsToIgnore(new String[]{"deviceName", "batteryLevel"});
Gleap.getInstance().setDisableEnvData(true);
```

Both can be called at any time and apply to the next ticket. Each `setEnvDataPropsToIgnore` call replaces the previous list, an empty array resets it. `setDisableEnvData(false)` turns the collection back on.

## Dark mode

The widget uses the colors set in the Gleap dashboard. To match your app's dark / light mode, enable "Adapt to dark / light mode" in the dashboard and set a color scheme:

```
Gleap.getInstance().setColorScheme("auto");
Gleap.getInstance().setColorScheme("dark", null, "#121212");
```

Until `setColorScheme` is called, the dashboard's color scheme applies. `auto` follows the app's night mode (including `AppCompatDelegate.setDefaultNightMode`) and switches live, `light` / `dark` force a scheme; any other value is treated as `auto`. Dark mode uses the dark colors set in the dashboard (header colors, UI color and background) and also the dark logo, header image and composer glow set there; without dark colors the widget keeps its normal colors. The optional background colors passed to `setColorScheme` override the dashboard's background in light / dark mode. Can be called before or after `Gleap.initialize`.

`setColorScheme` only takes effect when "Adapt to dark / light mode" is enabled in the dashboard. While it is disabled, the widget always keeps the dashboard colors, whatever scheme the app sets.

## Inline banners

Inline banners (the banner style set in the Gleap dashboard) are shown at the top of the screen; in edge-to-edge apps they cover the status bar. To keep your content below them, listen for the banner height and pad your content by it (dp from the top of the content view, status bar included; 0 when no inline banner is shown, floating banners stay at 0). `Gleap.getInstance().getBannerHeight()` returns the current value.

```java
Gleap.getInstance().setBannerHeightChangedCallback(height -> {
    int px = Math.round(height * getResources().getDisplayMetrics().density);
    contentView.setPadding(0, px, 0, 0);
});
```

Without it nothing changes: banners overlay the app as before.

## Customer identity (JWT)

Sign users in with an identity token. Your backend signs a short HS256 JWT with an identity key from **Settings → Security → Customer identity → Identity keys** (put the key id in the `kid` header; the secret never leaves your server). The `userId` claim is required. Optional claims: `email`, `name`, `company` (`{ id, name }`), `plan`, and provider ids such as `stripeCustomerId`. Set `exp` to at most one hour. Everything in the token lands on the contact as signed data the app cannot change.

Pass the token to `setUserJwt` after login and on every app start of a logged-in user; no separate identify call is needed. `setUserJwt` keeps the token in memory only. `setUserJwtProvider` (optional) fetches a fresh token when it expires, and also signs the session again after an app restart: when the server reports Signed mode without a valid grant and no token was passed to `setUserJwt`, the provider is asked once per app start and its token is sent like one passed to `setUserJwt` (a null token sends nothing). The provider can be set before or after `Gleap.initialize`. Call `clearIdentity` on logout. `setAuthTokens` hands Kai your own API tokens for this user. See [Android user identity](https://docs.gleap.ai/en/articles/467-android-sdk-user-identity) and [Customer identity](https://docs.gleap.ai/en/articles/709-customer-identity).

```java
// After login and on every app start of a logged-in user (token from your backend)
Gleap.getInstance().setUserJwt(token);

// Optional: called when the token has expired or is about to, and once per app start to restore
// a signed session when no token was passed; pass null when it failed
Gleap.getInstance().setUserJwtProvider(callback ->
    myBackend.fetchGleapToken(token -> callback.onToken(token))
);

// Optional: tokens API tools may use to call your backend as this user (a null value removes one)
Map<String, String> tokens = new HashMap<>();
tokens.put("backend", accessToken);
Gleap.getInstance().setAuthTokens(tokens);

// On logout
Gleap.getInstance().clearIdentity();
```

`identifyContact(userId, properties)` without a token creates an unsigned contact. Older integrations that pass a user hash to identify keep working; see [Upgrade to identity tokens](https://docs.gleap.ai/en/articles/16752008-upgrade-to-identity-tokens).

## Authenticated conversation files

When "Authenticated conversation files" is enabled for your project, conversation files can only be opened by a user with a signed identity. Identify the user with `Gleap.getInstance().setUserJwt(token)` on every app start.

The SDK then receives a short-lived file session (15 minutes), which it keeps in memory only (never on disk), passes to the widget and refreshes before it expires. `clearIdentity` revokes it.

Emails about protected files link to your app's URL with a `gleapFile` query parameter. If your app handles that URL (e.g. as an App Link), pass it to Gleap; after the user is identified, the SDK opens the file's conversation if the user may see it:

```
Uri link = getIntent().getData();
if (link != null) {
    Gleap.getInstance().openProtectedFileFromUrl(link.toString());
}
```

`openProtectedFileFromUrl` returns `false` when the URL has no valid `gleapFile` parameter. Keep the parameter through your app's login flow.

## Releasing

Releases are published to Maven Central (`io.gleap:gleap-android-sdk`) by GitHub Actions ([`.github/workflows/release.yml`](.github/workflows/release.yml)):

1. Bump `VERSION_NAME` (and `VERSION_CODE`) in `gradle.properties` and add a `## X.Y.Z` section to `CHANGELOG.md`; merge to `master`.
2. Tag the merged commit with the plain version (no `v`) and push the tag:
   ```
   git tag X.Y.Z && git push origin X.Y.Z
   ```

The workflow checks that the tag equals `VERSION_NAME` and is on `master`, runs the unit tests, publishes and releases the signed artifacts through the Central Portal (no manual step), waits until the POM is on repo1.maven.org and creates the GitHub release with the `CHANGELOG.md` section as notes. Running the workflow by hand with a `version` is a dry run: same checks, tests and signing, published only to the runner's local Maven repository.

Repository secrets:

| Secret | Value |
|--------|-------|
| `MAVEN_CENTRAL_USERNAME` | Central Portal user token username |
| `MAVEN_CENTRAL_PASSWORD` | Central Portal user token password |
| `SIGNING_KEY` | ASCII-armored private key (`gpg --armor --export-secret-keys <key id>`) |
| `SIGNING_KEY_ID` | Short (8 character) key id |
| `SIGNING_KEY_PASSWORD` | Passphrase of the key |

Publishing from a Mac still works with `./gradlew :gleap:publishAndReleaseToMavenCentral --no-configuration-cache` (or `:gleap:publishToMavenLocal` to check the artifacts). It reads from `~/.gradle/gradle.properties`:

- `mavenCentralUsername` / `mavenCentralPassword`: the Central Portal user token, i.e. the values of the former `NEXUS_USERNAME` / `NEXUS_PASSWORD`, which are no longer read.
- `signing.keyId`, `signing.password`, `signing.secretKeyRingFile`: unchanged, used whenever no `signingInMemoryKey` is set.

The empty `io.gleap:gleap-okhttp-interceptor` artifact is released from [GleapSDK/OKHttpInterceptor](https://github.com/GleapSDK/OKHttpInterceptor), not from this repository.
