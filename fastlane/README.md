fastlane documentation
----

# Installation

Make sure you have the latest version of the Xcode command line tools installed:

```sh
xcode-select --install
```

For _fastlane_ installation instructions, see [Installing _fastlane_](https://docs.fastlane.tools/#installing-fastlane)

# Available Actions

## Android

### android metadata_pull

```sh
[bundle exec] fastlane android metadata_pull
```

Download the live Play listing into fastlane/metadata/android

### android metadata_push

```sh
[bundle exec] fastlane android metadata_push
```

Upload fastlane/metadata/android back to Play (no binary, no images)

### android images_push

```sh
[bundle exec] fastlane android images_push
```

Upload the listing images and screenshots in fastlane/metadata/android

### android internal

```sh
[bundle exec] fastlane android internal
```

Build a release AAB and upload it to the internal track

### android production

```sh
[bundle exec] fastlane android production
```

Promote the internal build to production. Rolls out to a fraction first.

----

This README.md is auto-generated and will be re-generated every time [_fastlane_](https://fastlane.tools) is run.

More information about _fastlane_ can be found on [fastlane.tools](https://fastlane.tools).

The documentation of _fastlane_ can be found on [docs.fastlane.tools](https://docs.fastlane.tools).
