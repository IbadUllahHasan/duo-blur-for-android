# FoldEcho — iPhone Fold transition on any Android phone

*4 screenshots below show the effect in action.*

## What this is
FoldEcho recreates the look of the iPhone Fold/Duo's fold-blur transition —
captured screen content that leans, blurs and dims as if hinging on an edge —
on a regular (non-foldable) Android phone, using device tilt as the trigger
instead of a real hinge sensor. It has two rendering modes: a ray-traced
geometric fold (more accurate, needs Android 13+) and a classic lean/scale
transform (fast, works back to Android 12) as a fallback.

## How it works
Enable it, tilt the phone, and whatever's on screen freezes into a leaning,
graded-blur snapshot until you tilt back to neutral — over any app, because
it captures real pixels via `MediaProjection` rather than blurring in place.
The pipeline is: `TYPE_ROTATION_VECTOR` sensor reading → one frozen frame
captured per gesture → GPU rendering, either an AGSL `RuntimeShader` doing a
real per-pixel ray trace, or View 3D transforms as a fallback → blur that
intensifies away from the hinge edge in both modes. Every parameter below is
tunable live, in-app, while the effect is running — nothing here needed a
rebuild to adjust. Nothing about this modifies the system: it draws an
overlay window while the app's foreground service is running, and stops
touching anything the moment you disable it.

## Known limitations
- Requires Android 12 (API 31) or newer.
- Shows the system's screen-recording indicator for as long as the feature
  is enabled, not just during a tilt — the cost of keeping one capture
  session open instead of re-prompting for consent on every gesture.
- Apps that set `FLAG_SECURE` (banking, Netflix, password managers) capture
  as black, not blurred — an Android-wide protection with no workaround from
  here.
- Ray-traced mode needs API 33+ for AGSL `RuntimeShader` support, and even
  there it can fail to compile on a given device's GPU driver; either case
  falls back to the classic lean/scale renderer automatically.
- Sensor calibration ("Recalibrate," in the app or on the notification) is
  needed on first launch, and again any time holding the phone at an angle
  becomes your new "neutral" pose — the effect measures deviation from
  whatever pose was neutral when last calibrated, not absolute level.

## Screenshots
Add these under `screenshots/` in the repo with the filenames below —
they're referenced but not included in this commit.

| | |
|---|---|
| ![Classic mode, tilted left](screenshots/classic-tilt-left.png) | ![Ray-traced mode, tilted right](screenshots/raytraced-tilt-right.png) |
| Classic mode, tilted left — the near edge stays sharp, the far edge blurs. | Ray-traced mode, tilted right — same graduated effect, a different rendering model underneath. |
| ![Control panel, Tuning section](screenshots/control-panel-tuning.png) | ![Effect over real app content](screenshots/real-app-content.png) |
| Control panel, Tuning section — mode selector and live parameters. | The effect applied over real app content (Messages, Home, etc.), not a demo screen. |

## Install
**Option A — GitHub Releases.** Download the latest APK from this repo's
Releases page, then sideload it — tapping the downloaded file will prompt
for the "install unknown apps" permission for whichever app opened it;
allow it, then install.

**Option B — Build from source via GitHub Actions**, no local Android
Studio required:
1. Go to the **Actions** tab → "Build Debug APK" → "Run workflow" (or push
   to `main`, which triggers it automatically).
2. Once it finishes, open the run and download `FoldEcho-debug-apk` from
   **Artifacts** (a zip containing `app-debug.apk`).
3. Sideload it the same way as Option A.

Local Android Studio also works (`Open` this folder, let Gradle sync, run ▶),
if you'd rather build and iterate on-device directly.

## Tuning
Once installed, open the app. "Enable" starts it — you'll be asked for the
"display over other apps" permission and then the one-time screen-capture
consent dialog. The Tuning section is where every parameter above lives:
activation threshold, blur intensity, perspective depth, and everything else
that only real on-device testing can settle, adjustable in real time while
the effect is running. Hit "Recalibrate" to reset the neutral pose to
however you're currently holding the phone.

## Credits
This project ports the geometric ray-trace fold model from
[Atomicx7/Duo-animation](https://github.com/Atomicx7/Duo-animation) (itself
a Kotlin/AGSL port of elijah-semyonov's `DuoLikeAnimation`, originally
SwiftUI/Metal), generalized from that project's single left/right hinge to
all four screen edges. The classic lean/scale fallback is an independent
approximation built for this project, for older-device compatibility rather
than ported from anywhere.

## Things that were investigated and can't be done
Recording these so they don't get re-attempted:

- **Hiding the system bars while the effect runs.** Not possible from this
  app. `TYPE_APPLICATION_OVERLAY` is specifically barred from drawing over or
  controlling the status and navigation bars — an intentional Android 8.0
  security decision with no app-level workaround. The bars already render
  *above* the overlay for the same reason. Doing this would need the app to
  be the foreground Activity (which it isn't — it's an overlay over other
  apps), the signature-level `STATUS_BAR` permission, or root.
- **Continuous live capture behind the fold**, instead of one frozen frame
  per gesture. The single-frame path avoids capturing itself by grabbing its
  frame *before* the overlay is shown, then parking the `VirtualDisplay`.
  That doesn't extend to continuous capture: while the overlay is up it's
  part of the screen, so it lands in the next frame and compounds. There's
  no public API to exclude one window from a `MediaProjection` mirror of the
  same display.

## Everything else that's tunable
| Control | Default | Applies to | What it does |
| --- | --- | --- | --- |
| Activation threshold | 12° | Both | How far from neutral before the effect starts |
| Full-tilt point | 30° | Both | Deviation at which the effect hits full strength |
| Perspective | 40% | Classic | Camera distance (depth), not lean angle — the lean itself is a fixed, modest 8° swing |
| Recede | 10% | Classic | How much the frame shrinks at full tilt, coupled to the same curve as the lean |
| Motion Softness | 25% | Classic | Spring damping on the lean/recede motion — low settles cleanly, high overshoots and bounces before settling |
| View distance | 300mm | Ray-traced | How far the eye sits from the content plane — closer is a more extreme perspective |
| Blur per mm | 1.5px | Ray-traced | Blur radius gained per mm of glass-to-plane gap |
| Max blur radius | 48px | Ray-traced | Ceiling on that radius |
| Darken per mm | 2.0% | Ray-traced | Light lost per mm of that gap |
| Max darken | 80% | Ray-traced | Ceiling on that loss |
| Blur | 40px | Shared | Peak blur radius, graded from none at the hinge edge to full strength at the far edge in both modes |
| Dim | 55% | Shared | How dark the frame goes at full tilt |
| Edge Fade | 30% | Shared | Alpha gradient from opaque center to transparent edge, strength scaling with tilt |
| Corner Radius | 100% | Shared | Fraction of this device's actual screen-corner radius (detected via `Display.getRoundedCorner`, falling back to 24dp) |
| Flip tilt direction | off | Both | Reverses which way the frame leans, for devices whose sensor axes come out backwards |

"Reset to defaults," next to the Tuning header, writes every value above
back to its starting point in one tap, including re-detecting the device's
actual corner radius rather than restoring whatever radius happened to be
resolved the first time the app ran.
