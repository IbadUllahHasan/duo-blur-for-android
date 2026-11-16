package app.duoflow.ui

import app.duoflow.Tunables

private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t.coerceIn(0f, 1f)
private fun inverseLerp(a: Float, b: Float, v: Float) = ((v - a) / (b - a)).coerceIn(0f, 1f)

// Each pair of functions below is a "simple" perceptual dial: [*T] reads the
// dial's 0..1 position back out of the real, already-persisted parameters
// (so Advanced always stays the source of truth), and [apply*] writes a
// dragged dial position back into those same parameters. No new state is
// stored for the dial itself — it's purely a friendlier way to move values
// that already existed.

internal fun sensitivityT(t: Tunables) = inverseLerp(22f, 6f, t.activateDeg)
internal fun applySensitivity(t: Tunables, dial: Float) = t.copy(
    activateDeg = lerp(22f, 6f, dial),
    fullTiltDeg = lerp(48f, 18f, dial)
)

internal fun blurT(t: Tunables) = inverseLerp(15f, 65f, t.maxBlurPx)
internal fun applyBlur(t: Tunables, dial: Float, rayTraced: Boolean): Tunables {
    val updated = t.copy(maxBlurPx = lerp(15f, 65f, dial))
    return if (rayTraced) {
        updated.copy(
            maxBlurRadiusPx = lerp(20f, 80f, dial),
            blurPerMm = lerp(0.8f, 3.2f, dial)
        )
    } else {
        updated
    }
}

internal fun shadowT(t: Tunables) = inverseLerp(0.25f, 0.75f, t.maxDim)
internal fun applyShadow(t: Tunables, dial: Float, rayTraced: Boolean): Tunables {
    val updated = t.copy(maxDim = lerp(0.25f, 0.75f, dial))
    return if (rayTraced) {
        updated.copy(
            maxDarken = lerp(0.35f, 0.95f, dial),
            darkenPerMm = lerp(0.008f, 0.045f, dial)
        )
    } else {
        updated
    }
}

internal fun depthT(t: Tunables, rayTraced: Boolean) = if (rayTraced) {
    inverseLerp(450f, 150f, t.viewDistanceMm)
} else {
    inverseLerp(0.15f, 0.75f, t.perspectiveStrength)
}

internal fun applyDepth(t: Tunables, dial: Float, rayTraced: Boolean): Tunables = if (rayTraced) {
    t.copy(viewDistanceMm = lerp(450f, 150f, dial))
} else {
    t.copy(
        perspectiveStrength = lerp(0.15f, 0.75f, dial),
        maxShrink = lerp(0.04f, 0.22f, dial)
    )
}

internal fun hapticStrengthT(t: Tunables) = t.blurHapticsEngageStrength
internal fun applyHapticStrength(t: Tunables, dial: Float) = t.copy(
    blurHapticsEngageStrength = dial,
    blurHapticsReleaseStrength = dial,
    blurHapticsTimeoutStrength = dial
)
