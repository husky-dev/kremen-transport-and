# Kremenchuk Transport — Android

Live map of Kremenchuk's buses, trolleybuses and minibuses. Native Android rewrite of the 1.4
React Native app, sibling to the SwiftUI [iOS app](../kremen-transport-ios).

Kotlin · Jetpack Compose · Material 3 · Google Maps · minSdk 26 · version 1.5
(`com.krementransport`)

## Features

- Every vehicle on the map, refreshed every 5 seconds
- Tap a stop for arrival times in both directions; tap a vehicle for its route, speed and heading
- Pick the routes you care about; the choice survives a relaunch
- Ukrainian and English, switchable in the app; light, dark and system themes
- Phone and tablet — past 840 dp the route list becomes a pane beside the map
- No account, no ads, location optional

## Getting started

Requires Android Studio (for its bundled JDK) and the Android SDK. There is no JDK on `PATH`, so
use `make`, which points Gradle at the right runtime.

```sh
cp keystore.properties.example keystore.properties   # only needed for a signed release build
make avd && make emulator                            # one-off device setup
make run
```

The Google Maps key lives in `local.properties` as `MAPS_API_KEY` (git-ignored) and is injected
as a manifest placeholder. CI passes it as `ORG_GRADLE_PROJECT_MAPS_API_KEY`.

```sh
make test      # JVM unit tests
make lint
make bundle    # release AAB
```

## Data

Read-only, unauthenticated API at `https://api.husky-dev.me/`, shared with the iOS and web apps.
It has a number of sharp edges — route renumbering, broken station endpoints, a stop id that is
returned for both directions. They are documented in [CLAUDE.md](CLAUDE.md); read it before
touching the data layer.

## Release

Store listing text and changelogs live in `fastlane/metadata/android`, one string per file. See
[fastlane/README.md](fastlane/README.md).
