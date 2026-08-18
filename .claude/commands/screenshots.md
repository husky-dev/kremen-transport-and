---
description: Regenerate the Play Store screenshots (3 screens × en-US/uk × phone and 10" tablet)
argument-hint: "[locales] [form factors] — e.g. uk, or \"en-US uk\" phone"
allowed-tools: Bash, Read, Edit, Glob, Grep
---

Regenerate the Play Store listing screenshots.

Arguments (both optional, quoted if they contain spaces): $ARGUMENTS
- first = locales, from `en-US uk` (default both)
- second = form factors, from `phone tablet` (default both)

They pass straight through to `scripts/screenshots.sh`, so `/screenshots uk phone` shoots one
cell. With no arguments, shoot the whole matrix.

## Run it

```sh
make emulator          # only if nothing is attached; wait for sys.boot_completed = 1
make screenshots       # = make install && scripts/screenshots.sh
```

Or, for a subset, `make install` then `scripts/screenshots.sh <locales> <forms>`.

The full matrix takes roughly fifteen minutes — most of it is four framework restarts. Do not
assume it is hung. Never lower the waits to speed it up; every one of them is load-bearing (see
Failure modes).

## What it produces

Twelve PNGs, which `make images-push` uploads with no further arguments:

```
fastlane/metadata/android/{en-US,uk}/images/phoneScreenshots/{01_map,02_routes,03_stop}.png
fastlane/metadata/android/{en-US,uk}/images/tenInchScreenshots/{01_map,02_routes,03_stop}.png
```

Phone is 1080×2160, tablet 2560×1600 landscape. Filenames set the carousel order. Light theme
throughout — Play serves one static set regardless of the viewer's theme, so there is no dark
variant to produce.

## How it works, and what must stay true

Screenshots are **generated, not hand-captured**, and that is not a stylistic choice: the live API
moves every vehicle every 5 s and expires predictions within minutes, so hand-captures cannot be
reproduced and re-shooting for the next release would change the whole listing for no reason.

`scripts/screenshots.sh` launches `com.krementransport.screenshot.ScreenshotActivity`, which lives
only in `app/src/debug/` — it is not the launcher, and it is absent from release. It serves the
four API endpoints from the captured fixtures through an OkHttp interceptor (`FixtureApi.kt`) and
takes its camera, language, selected routes and open sheet from intent extras.

Three invariants to preserve when changing any of this:

1. **No step may tap a coordinate.** Every screen is reached by setting state — `initialPickerOpen`
   for the route list, `MapViewModel.select(MapTarget.Stop(sid))` for the stop sheet. A driver that
   taps pixels breaks the moment a control moves. If you need a new screen, add an extra and reach
   it through the view model, do not add `input tap`.
2. **The production seams stay defaulted.** `AppContainer(context, api = ApiClient())` and
   `MapScreen`'s `initialCamera` / `initialPickerOpen` are the *only* things `main` gives up for
   this, and each defaults to the previous behaviour. Do not add screenshot-only branches to
   `main`, and do not let fixtures reach the release build — verify with
   `unzip -l app/build/outputs/bundle/release/app-release.aab | grep -i "screenshot\|routes.json"`,
   which must be empty.
3. **Fixtures stay single-sourced.** `routes.json`, `buses.json` and `locations.json` are copied
   out of `app/src/test/resources` by the `screenshotFixtures` Gradle task, so `DecodingTest`'s
   assertions (38 routes, 319 vehicles, the 1994→433 stop fold) keep guarding the screenshots too.
   Only `app/src/debug/assets/prediction.json` is authored, and only because the captured one holds
   four arrivals all in one direction, which renders a lopsided sheet.

## Tuned constants, and why they are what they are

All in `scripts/screenshots.sh` unless noted. None is arbitrary; changing one usually breaks
something listed under Failure modes.

| Constant | Value | Why |
|---|---|---|
| `PHONE_ZOOM` | `15.1` | `MapDetail.from` needs ≥ 15f before it draws stops at all. `MapGeometry.DefaultZoom` is 14f, so the app's own camera gives bare polylines. |
| `TABLET_ZOOM` | `16.0` | 1706 dp of width covers ~4× the ground 411 dp does at equal zoom; at 15.1 the streets are unreadable. |
| `TABLET_OVERVIEW_ZOOM` | `14.8` | Only for the tablet's routes shot — see the pane note in Failure modes. |
| `CENTER_LAT/LNG` | `49.10146, 33.43154` | Prospekt Svobody, where all four selected routes run together and the fixture puts eleven vehicles in frame. Found by scoring every vehicle position in `buses.json` for vehicles + distinct routes + stops in view. **Not** `MapGeometry.CityCenter`, which frames a quiet corner. |
| `STOP_SID` | `306` | «Центральний ринок» — the only stop served by all four selected routes, so the arrivals sheet is full in either language. |
| `STOP_LAT/LNG` | `49.0608, 33.41794` | South of the stop, so its marker clears the sheet. |
| `PHONE_ROUTES` | `16,7,2,10` | `SelectionRepository.DefaultRouteIds`. Four busy central lines — a legible map rather than 200 markers. |
| `TABLET_ROUTES` | 12 ids | The wider map and permanent pane carry more. Do **not** select all 38: it blanks the map (see below). |
| `TileSettleMillis` | `3_500` (ScreenshotActivity.kt) | The ready marker only says the *data* arrived; the Maps SDK exposes no "drawn" callback. |
| `SETTLE` | 3 phone / 8 tablet | 2560×1600 of tiles keep arriving well after that. |

## Failure modes seen in practice

Each of these actually happened; the mitigation is already in the script, so **do not undo it**.

- **Play rejects a screenshot whose long side exceeds 2× its short side.** The Pixel 8 AVD is
  1080×2400 — 20:9 — so a raw `screencap` is not a legal upload. Phone shots are cropped to
  1080×2160 with `sips -c 2160 1080`, which fixes the ratio and drops the status bar in one pass.
  ImageMagick is not installed on this machine; `sips --cropOffset` does not work, so centre-crop
  is the tool available.
- **Google Maps labels its tiles from the *device* locale, not the per-app one.** Set only the app
  language and the Ukrainian listing gets Ukrainian buttons over Latin street names. The script
  sets `persist.sys.locale` and restarts the framework — which is why locale is the outer loop and
  why the run needs `adb root`.
- **The emulator's Maps renderer degrades over a session** and starts returning a flat beige
  rectangle for every capture; relaunching the app does not clear it. Hence a framework restart
  before each form factor (three captures per restart is fine, six is not). A blank map is gated on
  compressed size — ~130–180 KB blank against 660 KB–1.2 MB drawn. Colour statistics do *not*
  separate them, because a drawn map is itself mostly flat background with thin lines over it.
- **`pm clear` fails for a while after a framework restart** even once `sys.boot_completed` is 1 and
  `pm path` resolves. It reports "Failed" on stdout, so redirecting its output to `/dev/null` made
  runs die with no message at all. It is retried, and the script has an `ERR` trap so nothing can
  fail silently again.
- **Past `ExpandedWidthDp` (840 dp) the route picker is already a permanent pane**, so
  `initialPickerOpen` changes nothing on tablet and the map and routes shots come out identical.
  The tablet's routes shot pulls the camera back instead. Selecting all 38 routes was tried and is
  worse: it blanks the map and pushes SystemUI into an ANR.
- **`am` writes `--ef` as a Float and `--ed` as a Double.** `lat`/`lng` are read with
  `getDoubleExtra`, so `--ef lat` silently falls back to the default centre and the camera never
  moves. `zoom` is a Float and correctly uses `--ef`.
- **Demo mode must be re-entered after every `wm size` change**, or SystemUI leaves the pre-resize
  bar painted over the edge-to-edge map. Re-entering without `exit` first stacks duplicate wifi/3G
  glyphs from the AVD's two subscriptions.
- **`ScreenshotActivity` must use `@style/Theme.KremenTransport`, not `.Starting`.** It does not
  call `installSplashScreen()`, and AppCompat throws outright on a non-AppCompat theme.
- **`AppLocale.applyOnce` must run *after* `super.onCreate`.** Before it, AppCompat's delegate does
  not exist and the call is silently dropped — the locale simply never applies. Preferences are
  still seeded *before* `super.onCreate` so the first pass reads the right value.

## Verify before pushing

`scripts/verify_screenshots.sh` runs automatically at the end of every capture and re-checks all of
it: dimensions, Play's 2× aspect rule, the ≥ 2-per-slot minimum, and the blank-map size floor. It
is also safe to run on its own.

Then look at the images. The automated checks cannot tell you that the uk set is actually in
Ukrainian — confirm Cyrillic UI *and* Cyrillic street labels on the uk shots, English chrome on the
en-US ones (route names stay Ukrainian in both; they come from the API), the outbound/inbound split
visible on `03_stop`, and the side pane on all three tablet shots.

Also confirm the harness stayed opt-in: `make run` must still launch `MainActivity` against the
live API, showing "Завантаження маршрутів…" rather than painting instantly from fixtures.

## Uploading

```sh
make images-push
```

This will fail with `Invalid request - This app has no data safety declaration` until Play Console
→ Policy and programs → App content → Data safety is filled in. It cannot be set through the API,
and it blocks every supply push, not just images.

## Changing the set

- **Another locale**: add the metadata directory, extend `LOCALES`, and add the `set_device_locale`
  case. Play's Ukrainian locale is `uk`, not `uk-UA` — a `uk-UA` directory uploads its strings and
  then fails the commit with a bare `Invalid request`.
- **7" tablet**: one more size in the form-factor loop (`1200x1920` at density `320` → 600×960 dp)
  writing to `sevenInchScreenshots`, plus a `map_drawn` floor for it.
- **A different stop**: it must be served by every route in `PHONE_ROUTES`, or the sheet renders
  half empty. Re-derive from `routes.json` rather than guessing, and rewrite
  `app/src/debug/assets/prediction.json` to match the new `sid` — `StationContent` groups on each
  prediction's own `reverse` flag, so keep entries on both sides of it.
- **A fourth screen**: add an extra to `ScreenshotActivity`, reach the state through the view model
  (invariant 1), and give it a `map_drawn` floor if a map is visible in it.

Keep `CLAUDE.md`'s "Store screenshots" section and this command in step with any change.
