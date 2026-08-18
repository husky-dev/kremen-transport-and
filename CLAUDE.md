# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

Native Android app (Kotlin + Jetpack Compose + Google Maps) showing Kremenchuk's buses and
trolleybuses live on a map. Version 1.5 — a full native rewrite of the React Native 1.4 app that
still sits in `/Users/husky/Projects/kremen-transport-mob`. Its iOS sibling is
`/Users/husky/Projects/kremen-transport-ios` (SwiftUI, same version, same backend) and is the
best reference for *behaviour*; the web app `/Users/husky/Projects/kremen-transport-web` uses the
same API. **No app's design should be copied** — this one follows Material 3.

## Build and test

**There is no JDK on `PATH`.** Every Gradle call needs Android Studio's bundled runtime. The
`Makefile` exports it — prefer `make`, and do not install a second JDK to "fix" this.

```sh
make build       # debug APK
make test        # JVM unit tests
make lint
make run         # build, install, launch on the running emulator
make bundle      # release AAB, the artifact Play receives
make avd         # one-off: the SDK ships a system image but no device
make emulator
```

Anything raw needs the same two exports:

```sh
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
export ANDROID_HOME="$HOME/Library/Android/sdk"
./gradlew :app:testDebugUnitTest --tests '*RouteNumberTest*'
```

Toolchain: Gradle 9.7, **AGP 9.x**, `compileSdk` 37, `minSdk` 26, `targetSdk` 36. AGP 9 has
built-in Kotlin support, so there is **no `org.jetbrains.kotlin.android` plugin** — applying it
is a hard error. The Compose and serialization plugins are still applied separately.

## Verifying UI changes

The emulator is the only way to see this app: it is a map, and neither previews nor unit tests
tell you whether it is right. `adb` drives it end to end.

```sh
adb emu geo fix 33.42282 49.07041            # drop the user into Kremenchuk
adb shell cmd uimode night yes|no            # system dark mode (the splash follows this)
adb shell cmd locale get-app-locales com.krementransport.debug
adb shell wm size 2560x1600 && adb shell wm density 240   # tablet; `reset` to undo
adb exec-out screencap -p > shot.png
```

The splash is on screen for well under a second. To catch it, force-stop, launch in the
background and screenshot in a tight loop — the first frame is usually the old surface, the
second onward is the splash.

## API

Read-only and unauthenticated at `https://api.husky-dev.me/` (`data/api/ApiClient.kt`). Several
properties of this backend are not visible from the code and have bitten before:

- **There is no websocket and no marker-image service.** The 1.4 app used
  `wss://api.kremen.dev/transport/realtime` and `/img/transport/bus/pin`; that host is dead and
  the new one 404s both. All movement comes from polling; all markers are drawn client-side.
- **`transport/stations?rids=` and `transport/routes/{rid}/stations` are broken upstream** — they
  return one station per route. Stops must only ever be read from the `stations` array embedded
  in `transport/routes`.
- **Poll intervals are tied to the backend's own cadence**, which rebuilds vehicles every 10 s and
  routes hourly. Positions 5 s, roster 60 s, routes 1 h, stop predictions 5 s while the sheet is
  open. Polling faster buys nothing.
- **`transport/buses/locations` only moves vehicles it already knows about.** A vehicle that
  appears mid-session shows up as an unknown `tid`; only a full roster fetch can name it, which is
  why `VehicleRepository` refetches (with a 30 s cooldown) on an unknown id.
- **Route IDs were renumbered** (now 1–42). Anything carried over from the 1.4 app — cached data,
  default selections — is meaningless against this API. Hence `selection.routeIDs.v2` in
  `SelectionRepository`.

Data quirks encoded in the models:

- `type` is `"B"`/`"T"`. **Group by `route.type`, never by string-matching the number** — the web
  app's `number.indexOf('Т')` misclassifies the Latin-`T` routes. A *vehicle's* `type` is derived
  server-side from a free-text name and is unreliable; prefer the route's. A vehicle's `name` is
  a fleet label (`"02 Рута BI6227IM"`), never a route number.
- Route numbers mix Cyrillic `Т` and Latin `T`, hyphens and stray spaces (`"Т 1+"`, `"3-б"`,
  `"T15Б"`). `RouteNumber` normalises for badges, sorting and search — search deliberately
  tolerates the Т/T mix-up so a Latin keyboard finds Cyrillic routes.
- `path` is `[lat, lng]`, **lat first**. A single malformed pair must not sink the 565 KB payload,
  so `RouteDto.path` stays a raw `JsonArray` and is parsed per element.
- `speed` is `-1` when unknown. `updated_at` is ISO-8601 with *or* without fractional seconds.
- **A stop's `sid` maps 1:1 to a coordinate (1994 station entries fold to 433 stops), but the API
  returns the same `sid` for both travel directions.** The stop therefore carries no usable
  direction. `StationContent` splits arrivals on each *prediction's* `reverse` flag; the web app's
  `reverse !== directionForward` filter would drop about half the real arrivals here.

## Architecture

### Repositories, deliberately separate

Splitting them is the point — a 5-second position tick must not invalidate the polyline layer or
the route picker.

| Repository | Owns | Changes |
|---|---|---|
| `TransportRepository` | routes, deduplicated stops | launch, then hourly |
| `VehicleRepository` | live vehicles keyed by `tid` | every 5 s |
| `SelectionRepository` | selected route ids, show-offline | user action |
| `PredictionRepository` | one stop's arrivals, via `StationViewModel` | every 5 s while the sheet is open |

`StationViewModel` is scoped to the sheet so its poll can never outlive the UI that asked for it.
Polling goes through `util/Poller.kt`, driven from `MapScreen` by `LifecycleResumeEffect` —
cancelling the scope on pause *is* the shutdown mechanism.

Dependencies come from a hand-rolled `AppContainer` on the `Application`. A DI framework would
earn its keep across feature modules; this is one screen and four singletons.

### Launch path

Never block the map on the 565 KB routes download. `TransportRepository.load()` reads
`RouteCache` (raw bytes in `filesDir`, plus ETag/Last-Modified) and paints immediately, then
revalidates conditionally; a 304 only touches the timestamp.

### Map performance contract

`ui/map/TransportMap.kt` is the only file that knows how the map is drawn. Selecting all 38
routes would mean ~200 vehicles, 433 stops and 38 polylines of up to 640 points. Three mechanisms
keep that bounded, and changes to the map layer must preserve them:

- `MapViewport` culls to the padded visible bounds and caps vehicles/stops, recomputed only once
  the camera settles (debounced on `cameraPositionState.isMoving`).
- `MapDetail` gates stops and vehicle labels by zoom, with hysteresis so markers don't strobe at
  a threshold.
- `RoutePathCache` memoises Douglas–Peucker-simplified paths per route per detail level.

**Markers are pre-rendered bitmaps (`MarkerBitmaps`), never `MarkerComposable`** — a composition
per marker per frame will not hold frame rate at this count. The cache is bounded **by bytes**,
not entries: a 40 dp marker is ~57 KB at xxhdpi. Heading is quantised into 16 buckets, which is
what keeps the key space finite; the fin has to be baked in because the SDK's own `rotation`
would spin the route number with it (the 1.4 app did exactly that and left half the numbers
upside down). The fin's orbit follows an ellipse around the badge, because the badge is far wider
than it is tall and a fixed radius sits *inside* a wide one like `15Б`.

## Localization

Ukrainian is the development language, so it lives in `res/values/strings.xml` and English in
`res/values-en/`. A device in any third language therefore falls back to Ukrainian, as on iOS.
Keys are symbolic (`routes_title`, not a Ukrainian sentence) so a missing translation is loud.
Ukrainian plurals need `one/few/many/other`.

**In-app language switching is the one place this app deliberately does more than the iOS one**
(iOS owns per-app language; Android does not). It has a trap:

`AppCompatDelegate.setApplicationLocales` **recreates the activity**. Driving it from a
`LaunchedEffect` keyed on the stored preference makes the recreated activity re-apply it, and if
the platform has not persisted the value yet that loops forever — the visible symptom is a black
map that never finishes its first draw, and `MapsInitializer` logging every few hundred
milliseconds. It also cannot be called from `Application.onCreate`: AppCompat is not initialised
there and the call is silently dropped. `data/prefs/AppLocale.kt` has the only two safe call
sites: `applyOnce` from `MainActivity.onCreate` (guarded per process) and the language tap
itself. There is no `autoStoreLocales` service — DataStore is the single source of truth.

## Release notes

- Package `com.krementransport`, `versionName` 1.5, `versionCode` **1005000**.
  The scheme is major + 3-digit minor + 3-digit patch, inherited from
  `kremen-transport-mob/scripts/version_sync.py`; 1.4.2 shipped as 1004002. **Confirm the highest
  live versionCode in the Play Console before the first upload** — the RN build had an ABI-split
  path that multiplied codes by 1000, and if any such APK shipped, 1005000 would be too low.
  Checked on 2026-08-18 via `fastlane run google_play_track_version_codes`: internal 1004002,
  alpha/beta 1000012, production empty. No inflated codes ever shipped, so 1005000 is safe.
- Signing reads `keystore.properties` (git-ignored, see `keystore.properties.example`). Absent, the
  release build falls back to the debug key so a clean checkout still builds — and Play rejects it,
  which is the intended failure.
- The Maps API key comes from `local.properties` / `ORG_GRADLE_PROJECT_MAPS_API_KEY`, injected as
  a manifest placeholder. **The key inherited from the 1.4 app is committed in plaintext in
  `kremen-transport-mob`'s git history**, as is a second one in the web repo. It should be
  restricted to `com.krementransport` plus the release SHA-1, or rotated. Never commit it here.
- The Maps SDK still reaches for Apache HTTP classes removed from the platform in API 28. The
  `<uses-library android:name="org.apache.http.legacy" android:required="false"/>` in the manifest
  is load-bearing: without it the renderer throws `NoClassDefFoundError` on
  `org.apache.http.ProtocolVersion` and the map never draws.
- R8 is on for release. `proguard-rules.pro` keeps the kotlinx.serialization companions; without
  them every payload fails to decode in release while debug is fine. Smoke-test the release APK
  on a device after touching those rules — the debug build will not catch it.
- Not carried over from the RN app: CodePush/App Center, Sentry, the `ru` locale.

## Icon and splash

Both are generated from `kremen-transport-ios/App/Resources/AppIcon.icon/Assets/Bus.svg` — a
single absolute-cubic path with `fill-rule="evenodd"` (→ `android:fillType="evenOdd"`).

The glyph's furthest point sits 401/512 from the centre of its 1024 viewport, and the
adaptive-icon and splash safe circle is 341/512. Everything is therefore scaled **0.84** about the
centre; at natural size the roof corners and wheels are clipped by the circular mask.

The splash follows `values-night`, i.e. the **system** theme — it is drawn before the app runs, so
an in-app Light override cannot reach it. White bus on `#3E7FE8` in light, `#3E7FE8` bus on black
in dark, matching the iOS launch screen and the bus-stop sign it is named after.

## Play listing (fastlane)

See `fastlane/README.md`. Store text lives in the repo next to the app's own localizations, one
string per file, so a listing change reads as a diff. Changelogs are named by versionCode.

Three things about `supply` that are not visible from the lane definitions:

- **Play's Ukrainian listing locale is `uk`, not `uk-UA`.** A `uk-UA` directory uploads its
  strings and then fails the commit with a bare `Invalid request`. The live locales are `en-US`,
  `uk` and a stale `ru-RU` inherited from the RN app (the app itself no longer ships `ru`).
- **A changelog belongs to a release, not to the listing.** `metadata_push` therefore skips
  changelogs unless given a `version_code:` that Play already has; the 1005000 text goes up
  attached to the AAB in the `internal` lane. Without that, supply dies with
  `Could not find release for version code '' to update changelog`.
- **`supply init` defaults to the production track**, which is empty here, so `metadata_pull`
  passes `--track internal`.

**The Data safety declaration blocks every commit until it is filled in** (Play Console → Policy
and programs → App content → Data safety). Any push — text, images or binary — ends in
`Invalid request - This app has no data safety declaration`. It cannot be set through the API.

```sh
make metadata        # download the live listing
make metadata-push   # upload text and changelogs (no binary, no images)
make images-push     # upload the icon and screenshots
make internal        # build the AAB and upload to the internal track
make production      # promote internal to production at 20% rollout
```

## Store screenshots

`make screenshots` regenerates all twelve — three screens × `en-US`/`uk` × phone and 10" tablet —
into `fastlane/metadata/android/<locale>/images/{phone,tenInch}Screenshots/`, where `images_push`
picks them up. It needs a running emulator and takes about fifteen minutes.

They are generated, not hand-captured, because the live API makes hand-captures unrepeatable:
vehicles move every 5 s and predictions expire, so re-shooting for the next release would change
the whole listing for no reason. `scripts/screenshots.sh` drives
`com.krementransport.screenshot.ScreenshotActivity` — debug-source-set only, not the launcher, and
absent from release. It serves the API from the captured fixtures via an OkHttp interceptor and
takes its camera, language and open sheet from intent extras, so **no step taps a coordinate** and
nothing breaks when a control moves. The three `main` seams it needs are all defaulted parameters:
`AppContainer(context, api)` and `MapScreen`'s `initialCamera` / `initialPickerOpen`.

Five things about this that are not visible from the scripts:

- **Play rejects a screenshot whose long side exceeds twice its short side.** The Pixel 8 AVD is
  1080×2400 — 20:9 — so a raw `screencap` is not a legal upload. Phone shots are cropped to
  1080×2160, which fixes the ratio and drops the status bar together.
- **The Maps SDK labels tiles from the *device* locale, not the per-app one.** Setting only the
  app language gives a Ukrainian listing Ukrainian buttons over Latin street names. The script
  sets `persist.sys.locale` and restarts the framework, which is why locale is the outer loop.
- **`MapGeometry.DefaultZoom` (14f) is below the 15f `MapDetail` needs to draw stops**, so the
  app's own default camera produces bare polylines. The shots pass an explicit camera, and the
  tablet needs its own value — 1706 dp of width covers four times the ground 411 dp does.
- **The emulator's Maps renderer degrades over a session** and starts returning a flat beige
  rectangle for every capture, which relaunching the app does not clear. Hence a framework restart
  before each form factor. A blank map is caught by compressed size — colour statistics do not
  separate it, because a drawn map is itself mostly flat background with thin lines over it.
- **Past `ExpandedWidthDp` the route picker is already a permanent pane**, so `initialPickerOpen`
  changes nothing on tablet and the map and routes shots come out identical. The tablet's routes
  shot pulls the camera back instead.

`scripts/verify_screenshots.sh` re-checks every rule above and runs at the end of each capture.

## Tests

`app/src/test/resources/*.json` are real payloads captured from the live API and shared with the
iOS suite. `DecodingTest` asserts against their actual shape (38 routes, 319 vehicles, the
1994→433 stop fold), so an upstream shape change fails here rather than on a user's map. Refresh
a fixture by re-fetching the endpoint and updating the counts.
