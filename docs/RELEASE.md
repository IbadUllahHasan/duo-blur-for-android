# Cutting a release

This file is for the maintainer. Users looking to install DuoFlow should read
the top-level [README](../README.md) instead.

## How releases work

Pushing a tag matching `v*.*.*` (e.g. `v1.0.0`) runs
`.github/workflows/release.yml`, which builds an APK and publishes it to this
repo's Releases page automatically. The workflow can also be started by hand
from **Actions → "Release" → "Run workflow"**.

The APK's `versionName` and `versionCode` are derived from the tag itself
(e.g. `v1.2.3` → versionName `1.2.3`, versionCode `10203`) — see
`app/build.gradle.kts`. Nothing in the repo is bumped by hand for releases.

## Falling back to a debug-signed build

With no signing secrets configured, the workflow still completes — it falls
back to `assembleDebug` and logs a warning in the run. A release goes out as a
debug-signed APK. Sideload-into-Android will install it the same way; the only
practical difference is that some AV/security scanners flag debug-signed APKs
more aggressively than release-signed ones (see the README's *About the
install-time warnings* section for why that's a sideload-wide thing, not
specific to this project).

## Setting up a properly signed release (recommended)

Add these four repository secrets once, under **Settings → Secrets and
variables → Actions → New repository secret**:

| Secret | Value |
| --- | --- |
| `RELEASE_KEYSTORE_BASE64` | The release keystore, base64-encoded |
| `RELEASE_KEYSTORE_PASSWORD` | Its store password |
| `RELEASE_KEY_ALIAS` | Its key alias |
| `RELEASE_KEY_PASSWORD` | Its key password |

With all four set, the next release run will use `assembleRelease` with the
keystore and produce a properly signed APK. No further configuration is
needed per-release.

### Generating a keystore

A keystore usable for this project was generated as part of setting it up and
sent separately (not committed here — a signing key in the repo would let
anyone resign a malicious update), along with these exact four values. **Back
that keystore file up somewhere safe** — if it's ever lost, every future
release has to switch to a new key, and Android will refuse to let anyone
upgrade from an app installed with the old one without uninstalling it first.

To generate a different one instead:

```
keytool -genkeypair -v -keystore release.keystore -alias duoflow \
  -keyalg RSA -keysize 2048 -validity 10000
base64 -w0 release.keystore   # paste this output as RELEASE_KEYSTORE_BASE64
```

Then set the four secrets from the values used above and tag the next commit
to trigger the signed workflow.
