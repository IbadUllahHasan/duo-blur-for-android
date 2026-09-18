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

This is a demo/prototype you drive by touch or a flip gesture — not a
system-wide skin over your actual home screen or app switcher. Doing that for
real (intercepting the *actual* unlock/app-switch transitions system-wide)
needs an Accessibility Service or a custom launcher, which is a materially
bigger project — worth doing as a v2 once this core visual is working.

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

## Where to take it next
- Swap the flat gradient `ScreenLayer`s for real content (your actual home
  screen widgets, wallpaper, an app list) to make it feel less like a demo
  and more like your phone
- Tune the `delta > 18f` threshold in `MainActivity.kt` against your own
  phone's accelerometer noise
- If you want this reacting to real app switches or the lock screen, look
  into `AccessibilityService` (detects window/app changes) or building it
  as a custom launcher — both bigger scope than this file
