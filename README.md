# FoldEcho

A recreation of the *look* of the iPhone Duo's fold transition — captured
screen content that leans, blurs and dims — on a regular (non-foldable)
Android phone, driven by device tilt instead of a hinge angle.

## What it actually does
Enable it, tilt the phone, and whatever is on screen freezes into a warped,
blurred, dimmed snapshot until you bring the phone back to neutral. It works
over any app, because it captures real pixels through `MediaProjection`
rather than blurring in place.

The app itself is a control panel: status, a live readout of how far from
neutral the phone currently is, and sliders for every parameter that only
real on-device testing can settle.

## How it works
- **`FoldEchoService`** — foreground service holding the pipeline together.
  Keeps one `MediaProjection` alive for as long as the feature is enabled, so
  Android doesn't re-prompt for consent on every gesture.
- **`TiltTracker`** — `TYPE_ROTATION_VECTOR`, measuring the angle between the
  screen's normal now and at the calibrated neutral pose. Any direction counts
  the same, and there's no ±180° wrap to jump on. Runs on a background thread.
- **`CaptureSession`** — the single `VirtualDisplay` this projection is allowed
  to create, built once at startup and parked between gestures by detaching its
  surface. A gesture grabs exactly one frame and freezes it; re-reading the
  screen mid-gesture would capture the overlay itself and feed it back.
- **`OverlayController`** — hardware-accelerated `TYPE_APPLICATION_OVERLAY`
  window, hosting two interchangeable renderers (below). Blur comes from
  `RenderEffect`, dimming from a scrim — all GPU-side, none of it re-drawing
  the bitmap per frame.

## The two renderers
**Ray-traced fold** (default, Android 13+) — an AGSL `RuntimeShader` in
`res/raw/duo_fold.agsl`, ported from the model in
[Atomicx7/Duo-animation](https://github.com/Atomicx7/Duo-animation) and
generalised from its left/right hinge to all four screen edges. The captured
frame is a fixed plane in world space; the eye sits still on that plane's
normal; the glass rotates around whichever screen edge the dominant tilt axis
hinges on. Per pixel it casts a ray from the eye through the glass onto the
plane, blurring and darkening in proportion to the glass-to-plane gap, and
returning black where the ray misses the plane entirely.

**Lean/scale transform** (fallback) — View `rotationX`/`rotationY` plus
`cameraDistance` and a coupled scale-down, sprung via `SpringAnimation`.

minSdk stays at **31**, so the shader path is gated at runtime three ways: the
device must be on API 33+, the AGSL must actually compile, and the toggle must
be on. Any of those failing drops cleanly back to the transform renderer —
which is why that renderer is kept rather than deleted. The compile check
isn't hypothetical; the original's author documents the shader silently
failing on some devices, which is also why it sticks to minimal AGSL
constructs (no `break`/`continue`, no bool locals, no helper functions).

The frame leans *against* the phone's tilt, not with it — the content is
meant to read as a fixed plane behind a moving viewport (like looking through
a tilting window), not a rigid card taped to the screen. Because which sign
counts as "against" depends on how a given device's sensors report axes,
there's a "Flip tilt direction" switch in the app rather than a constant
you'd have to find and edit per phone.

While the effect is up it blocks touches on purpose: you're looking at a
snapshot, so tapping "through" it would hit things you can't see. If tilt
somehow never returns to neutral, the overlay gives up after 8 seconds rather
than stranding you.

## Tuning
Everything below is adjustable from the app while the service runs, and all of
it wants real-device testing:

| Control | Default | What it does |
| --- | --- | --- |
| Activation threshold | 12° | How far from neutral before the effect starts |
| Full-tilt point | 30° | Deviation at which the effect hits full strength |
| Perspective | 40% | Camera distance (depth), not lean angle — the lean itself is a fixed, modest 8° swing |
| Blur | 40px | Peak blur radius |
| Dim | 55% | How dark it goes |
| Recede | 10% | How much the frame shrinks at full tilt, coupled to the same curve as the lean |
| Edge Fade | 30% | Alpha gradient from opaque center to transparent edge, strength scaling with tilt |
| Corner Radius | 100% | Fraction of *this device's* actual screen-corner radius (detected via `Display.getRoundedCorner`, falling back to 24dp) — 100% matches the real corners exactly |
| Motion Softness | 25% | Spring damping on the lean/recede motion — low settles cleanly, high overshoots and bounces before settling |
| Use ray-traced fold | on | Ray-traced shader vs. the lean/scale transform |
| View distance | 300mm | How far the eye sits from the content plane — closer is a more extreme perspective |
| Blur per mm | 1.5px | Blur radius gained per mm of glass-to-plane gap |
| Max blur radius | 48px | Ceiling on that radius |
| Darken per mm | 2.0% | Light lost per mm of that gap |
| Max darken | 80% | Ceiling on that loss |
| Flip tilt direction | off | Reverses which way the frame leans, for devices whose sensor axes come out backwards |

Perspective, Recede and Motion Softness drive the lean/scale renderer only —
with the ray-traced fold on, its own projection replaces them. Blur still
applies either way: it runs first and the fold samples the blurred content.
The five millimetre-based fold parameters are converted to the shader's pixel
units against the display's physical density (`xdpi`), so they mean real
physical distance rather than pixels.

"Reset to defaults," next to the "Tuning" header, writes every value above
back to its starting point in one tap — including re-detecting the device's
actual corner radius, rather than restoring whatever radius happened to be
resolved the first time the app ran.

Every slider above except Activation threshold and Full-tilt point has its
own on/off switch next to it, so you can A/B an effect without losing the
value you'd dialed in. Those two are excluded on purpose — they're what
decides whether the feature ever activates at all, so there's no coherent
"off" state for them short of disabling the whole feature.

The effect releases at 60% of the activation threshold, so it can't flicker at
the boundary. "Recalibrate" (in the app or on the notification) re-baselines
neutral to however you're holding the phone right now.

If the frame leans the *wrong* way when you tilt, use the "Flip tilt
direction" switch — don't edit code for this, it's exactly what the switch is
for.

## Things that were investigated and can't be done
Recording these so they don't get re-attempted:

- **Hiding the system bars while the effect runs.** Not possible from this
  app. `TYPE_APPLICATION_OVERLAY` is specifically barred from drawing over or
  controlling the status and navigation bars — an intentional Android 8.0
  security decision with no app-level workaround. The bars already render
  *above* the overlay for the same reason. Doing this would need the app to
  be the foreground Activity (which it isn't — it's an overlay over other
  apps), or the signature-level `STATUS_BAR` permission, or root.

- **Continuous live capture behind the fold.** The single-frame path avoids
  capturing itself by grabbing its frame *before* the overlay is shown, then
  parking the VirtualDisplay. That trick doesn't extend to continuous
  capture: while the overlay is up it is part of the screen, so it lands in
  the next frame and compounds. There's no public API to exclude one window
  from a MediaProjection mirror of the same display. `FLAG_SECURE` on the
  overlay blanks it in the capture, but since the overlay covers the screen
  the capture becomes entirely black. Hiding the overlay per captured frame
  works but strobes the real screen at the capture rate. Android 14's
  single-app capture would exclude the overlay, but the user picks the app at
  consent time and it doesn't follow app switches, which defeats a
  system-wide effect.

## Known constraints
- The system's screen-recording indicator stays visible the whole time the
  feature is enabled, not just during a tilt. That's the price of holding one
  capture session open instead of re-prompting per gesture.
- Apps that set `FLAG_SECURE` (banking, password managers, DRM video) capture
  as black — an Android-wide protection, not a bug to fix here. The effect
  skips the overlay entirely if no frame arrives.
- Capture plus GPU work costs battery. The `VirtualDisplay` is parked whenever
  the effect isn't running, so the cost is per-gesture rather than constant.
- `MediaProjection`/`VirtualDisplay` behavior varies by OEM. Expect to tune
  against your actual phone.
- Personal sideload. Play Store distribution would bring `MediaProjection`
  disclosure requirements with it.

## Requirements
- Android 12 (API 31) or newer
- A GitHub account if you want the cloud build

## Option A — Cloud build via GitHub Actions (no local install)
`.github/workflows/build.yml` builds the APK on GitHub's servers on every push
to `main`, or on demand.

1. Go to the **Actions** tab → "Build Debug APK" → "Run workflow" (or just push
   to `main`).
2. Wait for the green checkmark (a couple of minutes).
3. Open the completed run, scroll to **Artifacts**, download
   `FoldEcho-debug-apk` (a zip containing `app-debug.apk`).
4. Get that APK onto your phone (email, Drive, etc.) and tap it. Android will
   ask permission to "install unknown apps" for whichever app opened the file —
   allow it, then install.

No developer mode, no USB debugging, no cable needed for this path.

## Option B — Local Android Studio
1. Open this folder in Android Studio.
2. If it says the Gradle wrapper is missing, let it generate one — this
   skeleton doesn't ship the wrapper jar.
3. Let Gradle sync (needs Google's Maven + Maven Central).
4. Run ▶ on your device.

## First run
1. Tap **Enable**.
2. Grant "display over other apps" if prompted, then tap Enable again.
3. Accept the screen-capture consent dialog.
4. Hold the phone the way you normally would, then tilt.

If it triggers too eagerly or not enough, drag the activation threshold while
watching the live tilt readout — it shows exactly what the sensor sees.
