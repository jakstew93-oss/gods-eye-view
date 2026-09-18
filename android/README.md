# God's Eye Fold — live-feed Android test

This branch packages the original frontend as an Android WebView application.
The app has its own launcher icon and resizes without intentionally recreating
the page on fold/unfold or rotation. No API keys are required to open the globe;
network access is required for imagery and other remote assets.

## Download and install

Open this repository's Actions tab, enable workflows for your fork if GitHub
requests it, and select **Android Fold APK**. Open a successful run and download
**Gods-Eye-Fold-test-APK** under Artifacts. Extract the ZIP on your phone and tap
app-debug.apk. Allow installation from the app you used to open it.

This is a debug-signed personal test, not a Play Store release. CI's debug signing
key can change across runs, so installing a later build may require uninstalling
the previous one, which clears local settings and scenes.

## Features and limitations

- Bundled source-built globe, existing visual filters and preset locations.
- Touch targets enlarged, bounded scrollable trays and narrow-screen rails.
- Cover display, unfolded display, landscape and split-screen layouts use the
  actual WebView width; no unverified device dimensions are assumed.
- Fold/rotation config changes retain the current WebView. Android may still
  restart or kill the process under memory pressure.
- Civilian aircraft use adsb.lol within 250 nautical miles of the viewed area.
  Aircraft ages, heights and speeds preserve the existing frontend contract.
  Military contacts use adsb.lol's military endpoint; receiver coverage varies.
- Satellite catalogs use CelesTrak. Positions are orbital predictions, not live
  telemetry. Catalogs are cached for six hours, including across app restarts.
- Public USGS earthquakes already load directly in the frontend.
- The APK does **not** bundle a Node server. A fixed native HTTPS transport now
  serves aircraft and satellite routes. Other local /api requests return explicit
  JSON 503 responses: ships, cameras, terrain helpers and AI still need a server.
  Aircraft enrichment and historical track backfill are not added in this update.
- Native requests have timeouts, 8 MiB response caps, bounded caches and failure
  cooldowns; expired data is not silently presented as a new successful snapshot.
  Network access and reachable upstream providers are required.
- Tap **Connect** to enter an HTTPS address hosting your own complete frontend
  and provider APIs. Native Fold styles are also applied to that connected page.
  Empty address returns to the bundled globe.
- Voice is deferred until native microphone permission handling is implemented.
  Importing scene files uses Android's file picker. Scene export downloads and
  other download handlers are deferred. Native browser popups are not supported.
- No credentials or remote server are provisioned by this branch.

## Build locally

Use Node 24.14.x, JDK 17, Gradle 8.11.1, and Android SDK 35.
Run npm ci and npm run build from the repository root. Copy dist contents,
src/ui/styles/fold.css and LICENSE into android/app/src/main/assets
(rename LICENSE to LICENSE.txt), then:
```sh
gradle -p android :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
```

Upstream CI runs unit tests and package boundary checks. The Android workflow
runs the frontend build, native contract tests, public-feed network smoke tests,
Android compilation/lint and static-template layout checks. Layout screenshots
do not establish WebGL performance or real-device compatibility: test pinching,
rotation, folding, resuming and imagery on the phone before treating this as
validated. Upstream npm run test:track still needs the complete dev server and
has not been run for this Android wrapper.

Source copyright and third-party asset/data terms are retained from upstream.
See ../LICENSE and ../DATA_SOURCES.md.

### Version 0.3.0 live controls

Supported layers activate automatically on first launch of this version. Later layer choices are retained. Tap **Live** to enable aircraft, military aircraft and satellites and check public-feed connectivity inside the Android browser. The aircraft diagnostic samples Leicester; the globe feed follows the current view. The report includes HTTP failures and the displayed layer state. Server settings are available from this dialog or by holding Live.

The APK workflow also verifies all three feeds through an Android emulator WebView, in addition to JVM transport tests. When bundling manually, copy `android/live-controls.js` to `android/app/src/main/assets/live-controls.js` alongside the frontend and fold CSS.

### Version 0.4.0 dropdown menu

Tap **Menu ▾** to open Live feed status, Data layers, Location, Visual presets, Display settings, Scenes, Flight context, Cameras, Globe actions or Server settings. Controls start hidden and open one scrollable panel at a time. Close, tapping the globe or Android Back dismisses the panel. Hiding controls keeps active data layers running. Copy `android/menu-controls.js` to the bundled assets when building manually.
