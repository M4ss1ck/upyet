# Releasing UpYet

How a version actually goes out. `AGENTS.md` is the engineering contract and states the *rules*
(`upyet.version` is the single source of truth, never hand-edit `versionCode`); this is the procedure.
Everything Play asks for that is not the build is in [`STORE.md`](STORE.md).

UpYet ships through two channels:

- **GitHub Releases** — where it is distributed today. A signed universal APK attached to a `vX.Y.Z` tag.
- **Google Play** — see [Play submission](#play-submission) below.

F-Droid is a good fit for a GPL-3.0 local-only app and is deliberately not set up: it needs a
`fastlane/metadata/android/` layout and a reproducible build, which is its own piece of work.

## Before every release

Run the validation commands from `AGENTS.md` §18 and get them all green:

```bash
./gradlew spotlessApply
./gradlew spotlessCheck compileDebugAndroidTestKotlin test lint assembleDebug
```

Then, on a device or emulator:

```bash
./gradlew connectedDebugAndroidTest
```

Run the core-path scenarios from [`TESTING.md`](TESTING.md) and record the pass in
[`VERIFICATION-LOG.md`](VERIFICATION-LOG.md). A release with no entry in that log is a release nobody
checked.

## Cutting the version

1. Move the `[Unreleased]` entries in `CHANGELOG.md` into a new `## [X.Y.Z] - YYYY-MM-DD` section. Leave
   an empty `[Unreleased]` behind.
2. Edit `upyet.version` in `gradle.properties`. That is the only version edit — `versionCode`, the APK
   filename and the version shown in Settings all derive from it.
3. Commit both together on a `release-X.Y.Z` branch: `chore(release): X.Y.Z`, and open a PR. Nothing is
   pushed to `main` directly.
4. Once the PR is merged, tag the merge commit on `main`, not the branch commit (a squash merge gives it a
   new hash):

   ```bash
   git switch main && git pull --ff-only
   git log -1 --oneline            # must be the chore(release): X.Y.Z merge
   git tag vX.Y.Z && git push origin vX.Y.Z
   ```

## Building the artifact

```bash
./scripts/build-android-release.sh
```

Produces and verifies `app/build/outputs/apk/release/upyet-X.Y.Z-release.apk`: R8-minified, signed, and
checked with `apksigner` for a v2 signature and the expected `dev.upyet` package.

Signing material lives in `~/.config/upyet/android-signing/` — created on first run, reused after, and
never in the repository. **Back it up.** Losing that keystore means no existing install can ever be
upgraded, and on Play it means the app is unrecoverable unless Play App Signing holds the upload key.
In CI, export `UPYET_ANDROID_KEYSTORE`, `UPYET_ANDROID_KEY_ALIAS` and `UPYET_ANDROID_KEYSTORE_PASSWORD`
instead.

## GitHub release

```bash
gh release create vX.Y.Z \
  app/build/outputs/apk/release/upyet-X.Y.Z-release.apk \
  --title "UpYet X.Y.Z" \
  --notes-file <(sed -n '/^## \[X.Y.Z\]/,/^## \[/p' CHANGELOG.md | sed '$d')
```

Release notes are the changelog section for that version and nothing else — do not write them twice and
let the two drift. The one exception is Play's "What's new", which is too short for the section and gets a
summary paragraph of it (see [Every release after that](#every-release-after-that)).

## Play submission

### One-time setup

1. **Turn on GitHub Pages** so the privacy policy URL resolves. In the repository:
   **Settings → Pages → Build and deployment → Source: Deploy from a branch → Branch: `main`, folder:
   `/docs` → Save.** Wait for the build, then confirm <https://m4ss1ck.github.io/upyet/privacy/> loads.
   The `docs/_config.yml` exclude list is what keeps `ARCHITECTURE.md`, `TESTING.md` and
   `VERIFICATION-LOG.md` off the public site; if you add an internal document to `docs/`, add it there too.
2. **Create the app** in Play Console. Category: Tools. Free.
3. **App content**, working through every section with [`STORE.md`](STORE.md) open:
   - Privacy policy URL
   - Data Safety — "no data collected, no data shared", with the reasoning ready if challenged
   - Camera permission justification and prominent-disclosure description
   - `USE_FULL_SCREEN_INTENT` justification
   - `USE_EXACT_ALARM` justification
   - Content rating questionnaire
4. **Store listing** — title, short and full description from `STORE.md`; graphics from its shot list,
   captured on a real device.
5. Enable **Play App Signing** and upload the release key.

### Every release after that

Play wants an **App Bundle**, not the APK:

```bash
./gradlew bundleRelease
```

Upload `app/build/outputs/bundle/release/app-release.aab`, then write the release notes and re-check two
things that silently go stale.

**Release notes ("What's new").** Play caps them at 500 characters per language, so the changelog section
does not fit and is not pasted. Write one paragraph per listing language (the listing is English only
today) that says what that version's `CHANGELOG.md` section says, the "Changed" entries first: a user who
upgrades is told what moved only here and in the changelog, never in the app. Paste it into **Release →
Release notes** for the language. Check its length before pasting:

```bash
printf '%s' "<the paragraph>" | wc -m    # must print 500 or less
```

The paragraph for each release lives here, newest first, so the next one has a model and the wording is
reviewed in the PR that cuts the version.

- **0.3.1**:

  > UpYet 0.3.1: a timer's +1 min is now a clear outlined button, and the time flashes when you tap it. An
  > alarm can now have no snooze: pick Off in its snooze list and it rings with Dismiss only. Settings ›
  > Default snooze can be Off too, for alarms you create afterwards; existing alarms keep their snooze. A
  > running timer now shows when it will ring, on its row and in its notification.

- **0.3.0** (the drawer move, map #5 decision 2):

  > UpYet is now a full Clock app. Four tabs, Alarms, World clock, Timer and Stopwatch, replace the old three,
  > and change with a tap instead of a swipe. History, Settings and Reliability moved into the ☰ menu at the
  > top left, which shows a red dot when a reliability check needs you; nothing was lost. Timers ring like
  > alarms, on the lock screen too, and can record the same silent video when you turn that on per timer or
  > in Settings › Timers. Off by default.

Then the two checks:

- **Data Safety** — still accurate? It changes the moment the app gains a permission or a way for data to
  leave. Adding `INTERNET` for any reason invalidates the entire declaration.
- **Target API level** — Play enforces a minimum that rises annually. `targetSdk` is in
  `app/build.gradle.kts`.

## After the release

Announce nothing until the APK on the tag installs over the previous version on a real device. An upgrade
that fails to install is the one failure the emulator will not show you.
