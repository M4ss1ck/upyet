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
./gradlew spotlessCheck test lint assembleDebug
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
3. Commit both together: `chore(release): X.Y.Z`.
4. Tag it: `git tag vX.Y.Z && git push origin main vX.Y.Z`.

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
let the two drift.

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

Upload `app/build/outputs/bundle/release/app-release.aab`, paste the changelog section as the release
notes, and re-check two things that silently go stale:

- **Data Safety** — still accurate? It changes the moment the app gains a permission or a way for data to
  leave. Adding `INTERNET` for any reason invalidates the entire declaration.
- **Target API level** — Play enforces a minimum that rises annually. `targetSdk` is in
  `app/build.gradle.kts`.

## After the release

Announce nothing until the APK on the tag installs over the previous version on a real device. An upgrade
that fails to install is the one failure the emulator will not show you.
