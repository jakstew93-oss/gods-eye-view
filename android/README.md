# God's Eye Fold — first Android test

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
- The APK does **not** bundle a Node server. Local /api requests return explicit
  JSON 503 responses. Server-backed aircraft, ships, cameras, satellite proxies,
  terrain helpers, voice and AI features must not be advertised as operational
  in bundled mode. Some direct-source data may work independently.
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
gradle -p android :app:assembleDebug :app:lintDebug
```

Upstream CI runs unit tests and package boundary checks. The Android workflow
runs the frontend build, Android compilation/lint and static-template layout checks. Layout screenshots
do not establish WebGL performance or real-device compatibility: test pinching,
rotation, folding, resuming and imagery on the phone before treating this as
validated. Upstream npm run test:track still needs the complete dev server and
has not been run for this Android wrapper.

Source copyright and third-party asset/data terms are retained from upstream.
See ../LICENSE and ../DATA_SOURCES.md.
