#!/usr/bin/env bash
#
# Asserts Play's screenshot rules before `make images-push` finds out the slow way.
#
# The one that actually bites here is the aspect cap: the Pixel 8 AVD is 1080x2400, which is 20:9,
# and Play rejects anything whose long side is more than twice its short side. A raw screencap is
# not a legal upload.
#
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
META="$ROOT/fastlane/metadata/android"
status=0
count=0

while IFS= read -r shot; do
    width=$(sips -g pixelWidth "$shot" | awk '/pixelWidth/ {print $2}')
    height=$(sips -g pixelHeight "$shot" | awk '/pixelHeight/ {print $2}')
    short=$((width < height ? width : height))
    long=$((width < height ? height : width))
    rel=${shot#"$ROOT"/}
    count=$((count + 1))

    if [ "$short" -lt 320 ] || [ "$long" -gt 3840 ]; then
        echo "FAIL ${width}x${height}  $rel  (each side must be 320-3840 px)"
        status=1
    elif [ "$long" -gt $((short * 2)) ]; then
        echo "FAIL ${width}x${height}  $rel  (long side exceeds 2x the short side)"
        status=1
    else
        echo "ok   ${width}x${height}  $rel"
    fi
done < <(find "$META" -path '*/images/*Screenshots/*.png' | sort)

if [ "$count" -eq 0 ]; then
    echo "no screenshots found under $META" >&2
    exit 1
fi

# Play requires at least two screenshots in any device slot that has any at all.
while IFS= read -r dir; do
    n=$(find "$dir" -name '*.png' | wc -l | tr -d ' ')
    if [ "$n" -lt 2 ]; then
        echo "FAIL ${dir#"$ROOT"/} holds $n screenshot(s); Play requires at least 2"
        status=1
    fi
done < <(find "$META" -type d -name '*Screenshots' | sort)

# The emulator's Maps renderer wedges every so often and hands back a flat beige rectangle where
# the map should be. The app is up, the capture succeeds, and nothing else in the pipeline
# notices — but a blank map on a store listing is the worst outcome here.
#
# Colour statistics do not separate the two cases: a drawn map is itself mostly flat background
# with thin lines over it. Compressed size does, by a wide margin — a blank capture lands around
# 130-180 KB where a drawn one is 660 KB to 1.2 MB. The phone's route list is exempt because its
# sheet covers the map completely, so it is legitimately small.
while IFS= read -r shot; do
    case "$shot" in
        */phoneScreenshots/02_routes.png) continue ;;
        */phoneScreenshots/*)   floor=400000 ;;
        */tenInchScreenshots/*) floor=600000 ;;
        *)                      continue ;;
    esac
    bytes=$(stat -f %z "$shot")
    if [ "$bytes" -lt "$floor" ]; then
        echo "FAIL ${bytes} bytes  ${shot#"$ROOT"/}  (under ${floor} — the map did not draw)"
        status=1
    fi
done < <(find "$META" -path '*/images/*Screenshots/*.png' | sort)

exit $status
