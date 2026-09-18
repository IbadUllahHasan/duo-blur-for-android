# FoldEcho

A recreation of the *look* of the iPhone Duo's fold transition (blur + dissolve
between two screens), built for a regular (non-foldable) Samsung phone.

## Be clear about what this is and isn't
There's no hinge sensor on your phone, so this can't literally intercept a
real fold like the Galaxy Z Fold recreation did. What it does instead:
- Two full-screen layers ("Screen A" / "Screen B") that cross-fade
- Blur peaks in the middle of the transition on a sine curve, then clears —
  same visual signature as the Duo effect
- Driven by a horizontal drag gesture (stand-in for "fold angle")
- Optional accelerometer trigger: a fast flip of the phone in your hand
  auto-plays the transition, as a stand-in for the hinge sensor

The screen above is a demo/prototype you drive by touch or a flip gesture.
There's also a real, system-wide mode now (see below) that captures actual
screen pixels and warps/blurs/dims them live, on top of whatever app you're
in, driven by continuous device tilt.

## System-wide tilt mode
This is the real thing, not a demo: `FoldEchoService` keeps one
`MediaProjection` session alive for as long as you enable it, watches
`TYPE_ROTATION_VECTOR` for tilt deviation from a calibrated neutral pose,
and — only while tilt is past threshold — grabs live frames, applies a
perspective warp + blur + dim, and shows them in a full-screen overlay on
top of whatever app is in front. Tap "Enable System-Wide FoldEcho" on the
main screen; it'll ask for the "display over other apps" permission once,
then the one-time screen-capture consent dialog.

Decisions made on the spec's open questions, all easy to retune in code
(`FrameProcessor.kt` / `TiltTracker.kt`) once you've felt it on a real phone:
- **Frame-pull rate:** 200ms (~5fps) while active — `FRAME_INTERVAL_MS` in
  `FoldEchoService.kt`. Raise it only if the effect looks laggy in testing.
- **FLAG_SECURE windows:** not special-cased. Banking apps, password
  managers, and DRM video will show a black rectangle under the effect —
  accepted as an Android-wide protection, not worth an Accessibility Service
  just to detect and skip those windows.
- **Neutral pose / thresholds:** auto-calibrates to whatever orientation the
  phone is in when you enable the feature (assumes you're holding it
  normally at that moment). Activates at 12° of deviation, deactivates at
  7° (hysteresis, to avoid flicker at the boundary) — `ACTIVATE_THRESHOLD_DEG`
  / `DEACTIVATE_THRESHOLD_DEG` in `FrameProcessor.kt`. Tap "Recalibrate" on
  the persistent notification to re-baseline without restarting the service.
- **Warp intensity:** kept subtle on purpose — max corner-shift of 5% of
  screen width/height at full tilt (30°) — `MAX_WARP_SHIFT_FRACTION` in
  `FrameProcessor.kt`. This is the parameter most likely to look gimmicky if
  overdone, so nudge it up gradually.

Known trade-offs, carried over from the spec:
- The system's screen-recording indicator stays visible the entire time
  this is enabled, not just during a tilt — that's the cost of keeping one
  `MediaProjection` session alive instead of re-prompting for consent on
  every gesture.
- Continuous capture + per-frame GPU work costs real battery. The pipeline
  is gated to "only while tilt is past threshold," but expect it to be
  heavier than the touch-driven demo above.
- `MediaProjection`/`VirtualDisplay` behavior has real OEM-specific quirks —
  tune thresholds and blur radius against your actual phone, not just these
  defaults.
- This is a personal-sideload feature. If it's ever meant for the Play
  Store, `MediaProjection`'s screen-capture disclosure requirements apply.

The original touch/flip-driven demo (no capture, no permissions, no
indicator) still exists above it and works the same as always.

## Requirements
- A Samsung running Android 12 (API 31) or newer
- A GitHub account
- No local Android Studio / SDK install needed if you use the cloud build below

## Option A — Cloud build via GitHub Actions (no local install)
This repo includes `.github/workflows/build.yml`, which builds the APK on
GitHub's own servers whenever you push to `main`.

1. Create a new repository on GitHub and upload the contents of this
   `FoldEcho` folder to it (web "Add file → Upload files" drag-and-drop
   works fine for a project this size — no `git` install required).
2. Make sure the workflow file ends up at the path
   `.github/workflows/build.yml` in the repo (GitHub sometimes needs you to
   upload the `.github` folder separately since some tools hide dotfolders).
3. Go to the **Actions** tab on your repo. A run should start automatically
   after the upload; if not, click "Build Debug APK" → "Run workflow".
4. Wait for the green checkmark (a couple of minutes).
5. Open the completed run, scroll to **Artifacts**, download
   `FoldEcho-debug-apk` (it's a zip containing `app-debug.apk`).
6. Get that APK onto your phone (email it to yourself, Google Drive, etc.)
   and tap it. Android will ask permission to "install unknown apps" for
   whichever app opened the file — allow it, then install.

No developer mode, no USB debugging, no cable needed for this path.

## Option B — Local Android Studio
1. Open this folder in Android Studio ("Open" → select the `FoldEcho` folder).
2. If Android Studio says the Gradle wrapper is missing, let it generate one
   (it will offer to on first sync) — that's expected, this skeleton doesn't
   ship the wrapper jar.
3. Let Gradle sync (needs internet access to Google's Maven + Maven Central).
4. Run ▶ on your device or an emulator.
5. Drag left/right anywhere on screen to fold. Try "Auto Flip", then toggle
   "Tilt-trigger: ON" and flip the phone over in your hand.
6. Or scroll down and tap "Enable System-Wide FoldEcho" for the real,
   capture-based version described above.

## Where to take it next
- Swap the flat gradient `ScreenLayer`s for real content (your actual home
  screen widgets, wallpaper, an app list) in the touch/flip demo to make it
  feel less like a demo and more like your phone
- Tune the `delta > 18f` threshold in `MainActivity.kt` against your own
  phone's accelerometer noise (touch/flip demo) or the tilt thresholds in
  `FrameProcessor.kt` (system-wide mode)
- Special-case `FLAG_SECURE` windows in the system-wide mode if the
  black-rectangle edge case turns out to be more common than expected in
  daily use
