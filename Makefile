# There is no JDK on PATH — every Gradle invocation has to point at Android Studio's bundled
# runtime. Prefer `make`; do not "fix" this by installing a second JDK and hoping they agree.
export JAVA_HOME := /Applications/Android Studio.app/Contents/jbr/Contents/Home
export ANDROID_HOME := $(HOME)/Library/Android/sdk
export PATH := $(ANDROID_HOME)/platform-tools:$(ANDROID_HOME)/emulator:$(ANDROID_HOME)/cmdline-tools/latest/bin:$(PATH)

PKG      := com.krementransport
DEBUG    := $(PKG).debug
ACTIVITY := $(PKG)/$(PKG).MainActivity
AVD      := kremen_phone
SYSIMG   := system-images;android-36;google_apis;arm64-v8a
APK      := app/build/outputs/apk/debug/app-debug.apk
AAB      := app/build/outputs/bundle/release/app-release.aab

.PHONY: build test lint release bundle install run stop logcat screenshot avd emulator clean metadata metadata-push images-push internal production

build:
	./gradlew :app:assembleDebug

test:
	./gradlew :app:testDebugUnitTest

lint:
	./gradlew :app:lintDebug

release:
	./gradlew :app:assembleRelease

# What Play receives. Signed from keystore.properties; without that file Gradle falls back to
# the debug key and Play will reject the upload — deliberately, rather than silently.
bundle:
	./gradlew :app:bundleRelease

install: build
	adb install -r $(APK)

run: install
	adb shell am start -n $(DEBUG)/$(PKG).MainActivity

stop:
	adb shell am force-stop $(DEBUG)

logcat:
	adb logcat -s AndroidRuntime:E TransportRepository:* VehicleRepository:* PredictionRepository:*

screenshot:
	adb exec-out screencap -p > shot.png

# One-off: the SDK ships the system image but no device.
avd:
	avdmanager create avd -n $(AVD) -k "$(SYSIMG)" -d pixel_8 --force

emulator:
	emulator -avd $(AVD) -no-snapshot-save -no-boot-anim &

clean:
	./gradlew clean

# Play listing text, mirrored into fastlane/metadata/android (see fastlane/Fastfile)
metadata:
	fastlane metadata_pull

metadata-push:
	fastlane metadata_push

images-push:
	fastlane images_push

internal:
	fastlane internal

production:
	fastlane production
