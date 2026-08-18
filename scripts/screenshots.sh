#!/usr/bin/env bash
#
# Produces the Play Store screenshots in fastlane/metadata/android/<locale>/images/.
#
# Everything that would otherwise vary between runs is pinned. The app is launched through the
# debug-only ScreenshotActivity, which serves the captured API fixtures and takes its camera,
# language and open sheet from intent extras — so nothing here taps coordinates, and the script
# does not break when a control moves.
#
# Needs a running emulator (`make emulator`) and the debug APK installed (`make install`).
#
# Usage:  make screenshots                      # the whole matrix
#         scripts/screenshots.sh uk             # one locale, both form factors
#         scripts/screenshots.sh uk phone       # one cell
#
set -euo pipefail

# `set -e` otherwise kills the run with no message at all — several adb subcommands report failure
# only through their exit status. Say where it died.
trap 'echo "screenshots.sh failed at line $LINENO: $BASH_COMMAND" >&2' ERR

# There is no adb on PATH in this project's shell (see the Makefile), so resolve it from the SDK.
ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
adb() { "$ANDROID_HOME/platform-tools/adb" "$@"; }

PKG=com.krementransport.debug
ACTIVITY="$PKG/com.krementransport.screenshot.ScreenshotActivity"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
META="$ROOT/fastlane/metadata/android"
WORK="$(mktemp -d)"

# Not the city centre the app itself opens on: this is the stretch of Prospekt Svobody where all
# four selected routes run together and the fixture has eleven vehicles in frame at once. Picked by
# scoring every vehicle position in buses.json for vehicles, distinct routes and stops in view.
CENTER_LAT=49.10146
CENTER_LNG=33.43154

# Just above MapDetail's 15f threshold: below it the map draws bare polylines with no stops at all.
# The tablet needs its own value — 1706 dp of width covers four times the ground a 411 dp phone
# does at the same zoom, which leaves streets and markers too small to read.
PHONE_ZOOM=15.1
TABLET_ZOOM=16.0

# «Центральний ринок» — the one stop served by all four selected routes, so the arrivals sheet is
# full in either language. The camera sits south of it so the marker clears the sheet.
STOP_SID=306
STOP_LAT=49.0608
STOP_LNG=33.41794

# Four busy central lines on the phone. The tablet's wider map and permanent side pane carry more.
PHONE_ROUTES="16,7,2,10"
TABLET_ROUTES="16,7,2,10,4,6,11,13,20,23,27,33"

# Past ExpandedWidthDp the picker is already a permanent pane, so `initialPickerOpen` changes
# nothing and the tablet's map and routes shots come out as the same picture. Pulling the camera
# back to the whole city for the routes shot is what separates them — still above MapDetail's
# 13.5f routes threshold, so vehicles keep their labels.
TABLET_OVERVIEW_ZOOM=14.8

LOCALES=${1:-"en-US uk"}
FORM_FACTORS=${2:-"phone tablet"}

ORIGINAL_LOCALE="$(adb shell getprop persist.sys.locale | tr -d '\r')"

cleanup() {
    adb shell am broadcast -a com.android.systemui.demo -e command exit >/dev/null 2>&1 || true
    adb shell wm size reset >/dev/null 2>&1 || true
    adb shell wm density reset >/dev/null 2>&1 || true
    if [ -n "$ORIGINAL_LOCALE" ]; then
        adb shell "setprop persist.sys.locale $ORIGINAL_LOCALE" >/dev/null 2>&1 || true
    fi
    rm -rf "$WORK"
}
trap cleanup EXIT

# --- device -------------------------------------------------------------------------------

adb wait-for-device
adb root >/dev/null 2>&1 || true
sleep 2
adb wait-for-device

# `sys.boot_completed` goes to 1 well before the package manager will answer, and the first
# `pm clear` after a framework restart then fails with no output at all — which is exactly how a
# run dies silently. Waiting for the package to resolve is the reliable signal.
wait_for_boot() {
    adb wait-for-device
    until [ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; do
        sleep 3
    done
    local deadline=$((SECONDS + 120))
    until adb shell pm path "$PKG" >/dev/null 2>&1; do
        [ $SECONDS -lt $deadline ] || { echo "package manager never came back" >&2; return 1; }
        sleep 3
    done
    sleep 5
}

# The Maps SDK labels its tiles from the *device* locale, not the per-app one — set only the app
# language and a Ukrainian listing gets Ukrainian buttons over Latin street names. Applying it
# needs a framework restart, which is why locale is the outer loop rather than the inner one.
set_device_locale() {
    adb shell "setprop persist.sys.locale $1"
}

# Unconditional, before every form factor rather than only when the locale changes. The emulator's
# Maps renderer degrades over a session and eventually returns a flat beige rectangle for every
# capture, which no amount of relaunching the app clears. Three captures is well inside what one
# restart survives; six is not.
restart_framework() {
    adb shell stop
    adb shell start
    wait_for_boot
}

# Animations off. Wiped by the framework restart, so this runs once per locale.
prepare_device() {
    adb shell settings put global window_animation_scale 0
    adb shell settings put global transition_animation_scale 0
    adb shell settings put global animator_duration_scale 0
    adb shell settings put global sysui_demo_allowed 1
}

# A fixed status bar: 12:00, full battery, no stray notification icons — without it the clock alone
# makes every re-run a diff. This has to follow every `wm size` change, not just every boot: the
# app draws edge-to-edge under the bar, and SystemUI leaves the pre-resize bar painted over the
# map otherwise.
demo_bar() {
    # Exit first: re-entering without it leaves the previous radio icons in place, and the AVD's
    # two mobile subscriptions then stack up as a row of duplicate wifi and 3G glyphs.
    adb shell am broadcast -a com.android.systemui.demo -e command exit >/dev/null
    adb shell am broadcast -a com.android.systemui.demo -e command enter >/dev/null
    adb shell am broadcast -a com.android.systemui.demo -e command clock -e hhmm 1200 >/dev/null
    adb shell am broadcast -a com.android.systemui.demo -e command battery -e level 100 -e plugged false >/dev/null
    adb shell am broadcast -a com.android.systemui.demo -e command network -e mobile hide -e wifi show -e level 4 >/dev/null
    adb shell am broadcast -a com.android.systemui.demo -e command notifications -e visible false >/dev/null
}

# --- capture ------------------------------------------------------------------------------

# Waits for the activity's own marker rather than sleeping a fixed amount: the routes payload is
# 565 KB, and the first launch after a framework restart takes several times as long as the rest.
wait_for_ready() {
    local deadline=$((SECONDS + 120))
    while [ $SECONDS -lt $deadline ]; do
        if adb logcat -d -s Screenshot:I 2>/dev/null | grep -q SCREENSHOT_READY; then
            return 0
        fi
        sleep 2
    done
    echo "timed out waiting for SCREENSHOT_READY" >&2
    adb logcat -d -s Screenshot:I AndroidRuntime:E | tail -30 >&2
    return 1
}

# `pm clear` is flaky for a while after a framework restart: it reports "Failed" on stdout and
# exits non-zero even though `pm path` already resolves. Swallowing that output is how a run ends
# up dying with no message at all, so retry, and surface the reason if it never takes.
clear_app() {
    local attempt
    for attempt in 1 2 3 4 5; do
        if adb shell pm clear "$PKG" 2>&1 | grep -q Success; then
            return 0
        fi
        sleep 3
    done
    echo "pm clear never succeeded for $PKG:" >&2
    adb shell pm clear "$PKG" >&2
    return 1
}

# Did the map actually draw? See the long note in verify_screenshots.sh: compressed size is what
# separates a drawn map from the renderer's flat beige rectangle, and it separates them by a wide
# enough margin to be a reliable gate. Kept in step with the floors that script enforces.
map_drawn() {
    local shot=$1 form=$2 screen=$3 floor
    case "$form/$screen" in
        phone/routes) return 0 ;;
        phone/*)      floor=400000 ;;
        tablet/*)     floor=600000 ;;
    esac
    [ "$(stat -f %z "$shot")" -ge "$floor" ]
}

capture() {
    local form=$1 locale=$2 screen=$3 index=$4 name=$5
    local lang routes extras out dir zoom SETTLE

    case "$locale" in
        uk) lang=uk ;;
        *)  lang=en ;;
    esac
    case "$form" in
        phone)  routes=$PHONE_ROUTES;  dir=phoneScreenshots;    zoom=$PHONE_ZOOM;  SETTLE=3 ;;
        tablet) routes=$TABLET_ROUTES; dir=tenInchScreenshots; zoom=$TABLET_ZOOM; SETTLE=8 ;;
    esac
    if [ "$form" = tablet ] && [ "$screen" = routes ]; then
        zoom=$TABLET_OVERVIEW_ZOOM
    fi

    extras="--es screen $screen --es lang $lang --es routes $routes --ef zoom $zoom"
    if [ "$screen" = stop ]; then
        # `am` writes --ef as a Float and --ed as a Double; the activity reads lat/lng with
        # getDoubleExtra, so --ef here would silently fall back to the default centre.
        extras="$extras --ei sid $STOP_SID --ed lat $STOP_LAT --ed lng $STOP_LNG"
    else
        extras="$extras --ed lat $CENTER_LAT --ed lng $CENTER_LNG"
    fi

    # A clean process every time: the activity seeds its preferences on create, and a warm one
    # would keep the previous cell's selection.
    out="$META/$locale/images/$dir/$(printf '%02d' "$index")_$name.png"
    mkdir -p "$(dirname "$out")"

    # Retried as a whole, because both failure modes are transient: a cold launch straight after a
    # framework restart sometimes never reaches the ready marker, and the emulator's Maps renderer
    # sometimes hands back a flat beige rectangle instead of a map. Relaunching clears both.
    local attempt
    for attempt in 1 2 3; do
        clear_app
        adb logcat -c >/dev/null 2>&1 || true
        # shellcheck disable=SC2086
        adb shell am start -W -n "$ACTIVITY" $extras >/dev/null

        if wait_for_ready; then
            # The ready marker only says the *data* arrived; tiles keep arriving after it, and
            # 2560x1600 of them take appreciably longer than a phone's worth.
            sleep "$SETTLE"
            adb exec-out screencap -p > "$out"

            if [ "$form" = phone ]; then
                # The Pixel 8 panel is 1080x2400 — 20:9. Play rejects any screenshot whose long
                # side is more than twice its short side, so the native capture is not a legal
                # upload. Cropping to 1080x2160 fixes the ratio and drops the status bar with it.
                sips -c 2160 1080 "$out" >/dev/null
            fi

            if map_drawn "$out" "$form" "$screen"; then
                echo "  ${out#"$ROOT"/}"
                return 0
            fi
            echo "  blank map on $locale/$form/$screen, retrying" >&2
        else
            echo "  no ready marker for $locale/$form/$screen, retrying" >&2
        fi
    done

    echo "gave up on $locale/$form/$screen after 3 attempts" >&2
    return 1
}

# --- matrix -------------------------------------------------------------------------------

for locale in $LOCALES; do
    case "$locale" in
        uk) set_device_locale uk-UA ;;
        *)  set_device_locale en-US ;;
    esac
    for form in $FORM_FACTORS; do
        restart_framework
        prepare_device

        case "$form" in
            phone)
                adb shell wm size reset
                adb shell wm density reset
                ;;
            tablet)
                # 1706x1066 dp, past MapScreen's ExpandedWidthDp of 840, so the route picker
                # renders as a permanent side pane instead of a bottom sheet — the large-screen
                # layout Play wants tablet shots to show. 2560x1600 is 1.6:1 and needs no crop.
                adb shell wm size 2560x1600
                adb shell wm density 240
                ;;
            *)
                echo "unknown form factor: $form" >&2
                exit 1
                ;;
        esac
        sleep 2
        demo_bar
        # SystemUI fades the demo bar in, and capturing mid-fade leaves a half-drawn clock ghosted
        # over an edge-to-edge map.
        sleep 4

        echo "$locale / $form"
        capture "$form" "$locale" map    1 map
        capture "$form" "$locale" routes 2 routes
        capture "$form" "$locale" stop   3 stop
    done
done

echo
"$ROOT/scripts/verify_screenshots.sh"
