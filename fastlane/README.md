# fastlane

Homebrew fastlane (`brew install fastlane`) — no Gemfile, matching the iOS repo.

Auth is a Google Play service account JSON at `fastlane/private/play_key.json`
(git-ignored). Create it in the Play Console under **Users and permissions** with the
*Release manager* role, then check it with:

```sh
fastlane run validate_play_store_json_key json_key:fastlane/private/play_key.json
```

| Lane | What it does |
|---|---|
| `metadata_pull` | Downloads the live listing into `fastlane/metadata/android` |
| `metadata_push` | Uploads listing text and changelogs only — no binary, no images |
| `images_push` | Uploads `metadata/android/*/images` (icon, feature graphic, screenshots) |
| `internal` | Builds a release AAB and uploads it to the internal track |
| `production` | Promotes internal to production at a 20% staged rollout |

`fastlane/metadata/android/<locale>/*.txt` is one string per file, so a listing change reads as
a diff. Locales are Play codes (`uk-UA`, `en-US`), not the app's `uk`/`en` resource qualifiers.

Play's own limits, which `supply` will not catch for you: title 30 characters, short description
80, full description 4000, a changelog 500.

Changelogs are named by **versionCode**, so `changelogs/1005000.txt` ships with version 1.5.
