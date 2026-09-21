<picture>
  <source media="(prefers-color-scheme: dark)" srcset="wordmark-dark.svg">
  <img src="wordmark-light.svg" alt="DuoFlow" width="280">
</picture>

**Make any Android phone do the iPhone Fold hinge animation.** Tilt your phone
and whatever's on screen freezes into a leaning, blurring snapshot, then
unfreezes when you tilt back. Works over any app, not just DuoFlow.

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![Android 12+](https://img.shields.io/badge/Android-12%2B-3DDC84)](https://developer.android.com/about/versions/12)
[![Latest release](https://img.shields.io/github/v/release/IbadUllahHasan/duo-blur-for-android)](../../releases)

DuoFlow recreates the iPhone Fold/Duo's fold-blur transition on a regular
(non-foldable) Android phone, using device tilt instead of a real hinge
sensor. Two rendering modes: a ray-traced geometric fold (Android 13+) and a
classic lean/scale transform as a fallback (Android 12+).

## Screenshots

| | |
|---|---|
| ![Classic mode, tilted left](screenshots/classic-tilt-left.jpg) | ![Ray-traced mode, tilted right](screenshots/raytraced-tilt-right.jpg) |
| Classic mode, tilted left — the near edge stays sharp, the far edge blurs. | Ray-traced mode, tilted right — same graduated effect, a different rendering model underneath. |
| ![Control panel, Tuning section](screenshots/control-panel-tuning.jpg) | ![Effect over real app content](screenshots/real-app-content.jpg) |
| Control panel, Tuning section — mode selector and live parameters. | The effect applied over real app content (Messages, Home, etc.), not a demo screen. |

> Screenshots show the effect mid-gesture. A short video clip is the natural
> next step for this section if there's interest — open an issue if you'd
> find it useful.

## How it works

Enable it, tilt the phone, and whatever's on screen freezes into a leaning,
graded-blur snapshot until you tilt back to neutral. Behind the scenes:

1. `TYPE_ROTATION_VECTOR` sensor reading → angular deviation from the pose the
   phone was in when the feature was enabled (the "neutral" pose).
2. Past the activation threshold (12° by default, configurable), one frame is
   captured per gesture via `MediaProjection` + a `VirtualDisplay`.
3. The frame is GPU-rendered two ways depending on mode — an AGSL
   `RuntimeShader` ray trace (Android 13+) that bends the captured frame
   through a tilted glass plane, or a `View` 3D transform (the classic
   lean/scale fallback).
4. Blur grades from nothing at the hinge edge to full strength at the far
   edge in both modes — that's the "dissolves as it crosses, reassembles on
   the other side" signature of the real hinge.
5. Tilt back to neutral → overlay lifts, real screen is back, nothing was
   touched.

Nothing about this modifies the system. It draws an overlay window while a
foreground service runs and stops touching anything the moment you disable it.

## Quick start

1. Install the APK (see the [Install](#install) section).
2. Open **DuoFlow**.
3. Tap **Enable**. If it's your first time, the app sends you to Android
   settings — toggle on **Display over other apps**, come back, tap
   **Enable** again.
4. Accept the one-time screen-capture consent dialog.
5. Hold the phone the way you normally would (this becomes your "neutral"
   pose).
6. Tilt. Anything on screen freezes into the fold effect.
7. Tilt back to neutral to release.

If it triggers too eagerly or not enough, the **Sensitivity** card in
Tuning is the first thing to look at — its simple dial maps the two
thresholds together. Tap **Recalibrate** if you change how you hold the
phone.

## Install

**Option A — GitHub Releases (recommended).** Download the APK from this
repo's [Releases page](../../releases) and sideload it: tapping the
downloaded file prompts for the "install unknown apps" permission, allow
it, then install. Release builds are signed with a stable key (see
[docs/RELEASE.md](docs/RELEASE.md) for the maintainer side), so you can
install a newer release over an older one without uninstalling first.

**Option B — Build from source via GitHub Actions** (no local Android
Studio needed):
1. **Actions** tab → "Build Debug APK" → "Run workflow" (or push to `main`,
   which triggers it automatically).
2. Once it finishes, open the run and download `DuoFlow.apk` from
   **Artifacts**.
3. Sideload it the same way as Option A.

Local Android Studio also works — open this folder, let Gradle sync, hit ▶.
See [CONTRIBUTING.md](CONTRIBUTING.md) for details.

<details>
<summary>About the install-time warnings</summary>

Sideloading any app outside the Play Store always shows Android's "unknown
sources" prompt and usually a Play Protect "unrecognized app" notice — the OS
asking you to vouch for a source it hasn't verified, not specific to this
project, and not fixable from the app side.

What *is* fixed as of this release is **debug-signed distribution**: release
builds now use `assembleRelease` with a real signing key instead of the
auto-generated debug keystore every `assembleDebug` build carries, which is
what several AV/security scanners flag regardless of what the app does.
Tapping "Install anyway" past Play Protect's notice is expected and safe for
an open-source app you can read the full source of right here.
</details>

## Tuning

Open the app and hit **Enable** — every parameter (activation threshold,
blur intensity, perspective depth, haptics, theme) is adjustable live while
the effect is running. The Tuning section uses a two-tier layout: a simple
**dial** per card moves several real parameters together via fixed presets,
and an **Advanced** section underneath exposes them individually. Adjusting
either takes effect immediately.

Most sliders carry their own on/off switch, so a single parameter can be
disabled without losing its saved value. **Recalibrate** (button on the main
screen, also a notification action) re-bases neutral to however you're
currently holding the phone.

A short haptic fires when the fold engages, a softer one when it releases,
and a distinct triple-tick if the 8-second safety auto-release fires because
a gesture was held too long. All three have independent on/off switches in
the Haptics card.

The theme toggle (top-right of the panel) cycles the app's own colour scheme
between **System** (follows the device), **Light**, and **Dark**. The two
pinned modes are separate materials, not one palette relit — glass opacity,
tint and the specular highlight all differ between them.

Full parameter reference, what each slider actually does, and the things
that were investigated and can't be done live in
**[docs/TUNING.md](docs/TUNING.md)**.

## Troubleshooting

- **Effect doesn't trigger at all.** Hit **Recalibrate** — your phone's
  neutral pose was probably set while you were already tilted. The effect
  measures deviation from *whenever you last calibrated*, not absolute level.
- **Effect triggers too easily / not enough.** Open Tuning → **Sensitivity**
  → drag the dial. Higher = harder to trigger, lower = easier.
- **Frame leans the opposite way from how I tilt.** Toggle **Flip tilt
  direction** in the Shared section. Different OEM sensors report axes
  differently, and that switch is the fix that doesn't need a code edit.
- **Effect looks laggy or stutters.** Switch the **Mode** card from
  *Ray-traced fold* to *Classic (lean/scale)* — the classic renderer is
  lighter on the GPU. The ray-traced path also won't compile on some device
  GPU drivers (it falls back automatically, but the warning is in logcat).
- **Stuck overlay that won't go away.** Bring the phone back to neutral, or
  flat on a table. If tilt stays past threshold for 8 seconds, the effect
  gives up automatically and waits for you to return to neutral — you get a
  distinct triple-tick haptic if haptics are on.
- **Banking apps / Netflix / password managers show black.** Expected — these
  set `FLAG_SECURE` and Android intentionally blacklists them from any
  screen capture, including ours. There's no fix from this app's side; see
  the [Known limitations](#known-limitations) section below.
- **The recording indicator stays on the whole time.** Expected — see the
  same section. The cost of not re-prompting for consent on every tilt.
- **"App not installed" when trying to update.** The previous APK was signed
  with a different key than this one. Uninstall the old one and install the
  new one fresh — this only happens when sideloading from a different source.

If something's broken that isn't on this list, please
[open an issue](../../issues) — there's a
[bug-report template](../../issues/new?template=bug_report.md), and the
short version is: device model, Android version, what you did, what you
expected, what happened. Screenshots or a screen recording help.

## Known limitations

- Requires Android 12 (API 31) or newer. Ray-traced mode additionally
  requires Android 13 (API 33) and may fall back to classic mode on some
  devices whose GPU driver rejects the AGSL shader.
- Shows the system's screen-recording indicator for as long as the feature
  is enabled, not just during a tilt — the cost of keeping one capture
  session open instead of re-prompting for consent on every gesture.
- Apps that set `FLAG_SECURE` (banking, Netflix, password managers) capture
  as black, not blurred — an Android-wide protection with no workaround from
  here.
- One frame is captured per gesture rather than continuous video. While the
  overlay is showing the processed frame, that frame is part of the screen,
  so re-capturing would just feed it back into the next frame. There's no
  public API to exclude one window from a `MediaProjection` mirror of the
  same display.
- The 8-second auto-release is a deliberate safety net, not the natural
  release path: the natural one is "tilt back to neutral." If you find the
  auto-release firing regularly, your Activation threshold is probably set
  too low.

## Feedback and bugs

Please [open an issue](../../issues). A short bug-report template asks for
device, Android version, and steps to reproduce — those three together
solve about 90% of what comes in. Feature requests and "what I actually
wanted this to do" notes are welcome too; the README and the tuning panel
have both been reshaped more than once from those.

## Credits

Ports the geometric ray-trace fold model from
[Atomicx7/Duo-animation](https://github.com/Atomicx7/Duo-animation),
which itself traces back to elijah-semyonov's SwiftUI/Metal
`DuoLikeAnimation`. Generalised here from a left/right hinge to all four
screen edges, and from a self-contained demo to capturing whatever's
actually on screen. The classic lean/scale fallback is independent and
written for this project's older-device compatibility.

## License

[MIT](LICENSE).
