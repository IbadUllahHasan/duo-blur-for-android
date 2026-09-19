# Tuning reference

Full detail on every slider in the app's Tuning section, plus a record of
things that were investigated and ruled out (see the main
[README](../README.md) for setup and general usage).

## How the panel is organized

Each card below Mode leads with one **simple dial** — a single slider that
moves several real parameters together via a fixed low/high preset per
parameter (e.g. dragging "Blur" moves Blur, Blur per mm, and Max blur radius
at once). Nothing new is stored for the dial itself: it reads and writes the
same parameters listed in the Advanced section underneath it, which is
collapsed by default. Opening Advanced and adjusting a parameter by hand
always takes effect immediately — the simple dial is a friendlier front end
to the same values, not a separate setting that overrides them.

| Card | Simple dial | Advanced (collapsed by default) |
| --- | --- | --- |
| Mode | Ray-traced fold / Classic (lean/scale) segmented control | *(none — nothing to hide)* |
| Sensitivity | Sensitivity (deliberate ↔ easy trigger) + Flip tilt direction switch | Activation threshold, Full-tilt point |
| Blur | Blur (sharp ↔ heavily blurred) | Blur, and in ray-traced mode also Blur per mm, Max blur radius |
| Shadow | Shadow (bright ↔ nearly black) | Dim, and in ray-traced mode also Darken per mm, Max darken |
| Depth | Depth (flat ↔ steep recede) | Ray-traced: View distance. Classic: Perspective, Recede, Motion Softness |
| Finishing touches | *(none — just the accordion)* | Edge Fade, Corner Radius |
| Haptics | Interface haptics switch, Fold haptics switch, Haptic strength | Engage, Release, Auto-release alert (each independently strength + on/off) |

## Every tunable parameter

| Control | Default | Applies to | Toggle | What it does |
| --- | --- | --- | --- | --- |
| Activation threshold | 12° | Both | — | How far from neutral before the effect starts |
| Full-tilt point | 30° | Both | — | Deviation at which the effect hits full strength |
| Perspective | 40% | Classic | Yes | Camera distance (depth), not lean angle — the lean itself is a fixed, modest 8° swing |
| Recede | 10% | Classic | Yes | How much the frame shrinks at full tilt, coupled to the same curve as the lean |
| Motion Softness | 25% | Classic | Yes | Spring damping on the lean/recede motion — low settles cleanly, high overshoots and bounces before settling |
| View distance | 300mm | Ray-traced | Yes | How far the eye sits from the content plane — closer is a more extreme perspective |
| Blur per mm | 1.5px | Ray-traced | Yes | Blur radius gained per mm of glass-to-plane gap |
| Max blur radius | 48px | Ray-traced | Yes | Ceiling on that radius |
| Darken per mm | 2.0% | Ray-traced | Yes | Light lost per mm of that gap |
| Max darken | 80% | Ray-traced | Yes | Ceiling on that loss |
| Blur | 40px | Shared | Yes | Peak blur radius, graded from none at the hinge edge to full strength at the far edge in both modes |
| Dim | 55% | Shared | Yes | How dark the frame goes at full tilt |
| Edge Fade | 30% | Shared | Yes | Alpha gradient from opaque center to transparent edge, strength scaling with tilt |
| Corner Radius | 100% | Shared | Yes | Fraction of this device's actual screen-corner radius (detected via `Display.getRoundedCorner`, falling back to 24dp) |
| Flip tilt direction | off | Both | — (itself a switch) | Turn on if the frame ever leans the opposite way from how you tilt the phone |

Turning a slider's switch off treats that one parameter as having no effect
(View distance is the one exception — it falls back to its default instead,
since a ray-traced fold can't work with a zero eye distance) without
resetting or losing the value the slider is set to.

"Reset to defaults," next to the Tuning header, writes every value above
back to its starting point in one tap, including re-detecting the device's
actual corner radius rather than restoring whatever radius happened to be
resolved the first time the app ran.

## Haptics

Two independent categories, each with its own on/off switch:

- **Interface haptics** — a light tap when you drag a slider or flip a
  switch inside this app. Purely a UI nicety; has no bearing on the fold
  effect.
- **Fold haptics** — a tactile cue tied to the tilt gesture itself: a crisp
  "catch" when the effect engages, a softer cue when it releases, and a
  distinct triple-tick when the 8-second safety auto-release fires (see
  *Known limitations* in the README). A simple "Haptic strength" dial scales
  all three together; Advanced breaks them out individually, each with its
  own strength and on/off switch, same as any other Advanced slider.

Devices whose vibration actuator doesn't support Android's richer
composition-primitives API (introduced in Android 11) fall back to a plain
amplitude-scaled buzz automatically — no setting to configure for that.

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
- **Removing the "unknown sources" / Play Protect prompt on install.** Both
  are the OS's own warning for anything installed outside the Play Store, not
  a check against this app specifically, and there's no app-side signing,
  manifest flag, or permission that opts out of them — only Play Store
  distribution or an enterprise MDM allowlist would. See the README's
  *About the install-time warnings* section for what actually is fixable
  here.
