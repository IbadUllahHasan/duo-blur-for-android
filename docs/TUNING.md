# Tuning reference

Full detail on every slider in the app's Tuning section, plus a record of
things that were investigated and ruled out (see the main
[README](../README.md) for setup and general usage).

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
