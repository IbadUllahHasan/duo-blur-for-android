package com.ibad.foldecho

/**
 * Turns a tilt reading into the effect parameters. Perspective is expressed as
 * View rotationX/rotationY rather than a bitmap Matrix: those are real
 * GPU-accelerated 3D transforms, where a perspective Matrix forces every draw
 * of a full-screen bitmap down Skia's software path — which is what made the
 * effect stutter.
 *
 * Corner radius and motion softness aren't computed here: corner radius is a
 * static property of the card (it doesn't depend on tilt at all), and motion
 * softness is about HOW a value is approached over time — a spring, driven by
 * OverlayController — not a value in itself. Both are read straight off
 * Tunables by OverlayController instead of flowing through this Effect.
 */
object FrameProcessor {

    data class Effect(
        val rotationXDeg: Float,
        val rotationYDeg: Float,
        val scale: Float,
        /** In dp — OverlayController converts to px against the view's own density. */
        val cameraDistanceDp: Float,
        val blurPx: Float,
        val dim: Float,
        /** 0..1 alpha for the edge-fade overlay; scales with tilt magnitude just like [dim]. */
        val edgeFadeAlpha: Float
    )

    /**
     * The rotation swing itself is fixed and modest. Depth reads through
     * camera distance — how close the virtual eye is — not through how far
     * the frame swings: a wide swing with a close camera looks like a
     * flipping card, a small swing with a close camera looks like a window
     * with real depth behind it, which is the effect wanted here.
     */
    private const val MAX_ROTATION_DEG = 8f

    /** Camera-distance range the "Perspective" slider (0..1 strength) maps into. Lower distance = more dramatic depth. */
    private const val MIN_CAMERA_DISTANCE_DP = 300f
    private const val MAX_CAMERA_DISTANCE_DP = 3000f

    /** Android's own default camera distance (1280 * density, in px) expressed in dp — density cancels out, so this is what "Perspective" disabled falls back to. */
    private const val NEUTRAL_CAMERA_DISTANCE_DP = 1280f

    fun effectFor(deviationDeg: Float, tiltUpDeg: Float, tiltRightDeg: Float, tunables: Tunables): Effect {
        val span = (tunables.fullTiltDeg - tunables.activateDeg).coerceAtLeast(1f)
        val raw = ((deviationDeg - tunables.activateDeg) / span).coerceIn(0f, 1f)
        val intensity = raw * raw * (3f - 2f * raw)

        val sign = if (tunables.flipTiltDirection) -1f else 1f
        val upward = sign * (tiltUpDeg / tunables.fullTiltDeg).coerceIn(-1f, 1f)
        val rightward = sign * (tiltRightDeg / tunables.fullTiltDeg).coerceIn(-1f, 1f)
        val swing = MAX_ROTATION_DEG * intensity

        // Both axes counter-rotate against the raw tilt direction, on
        // purpose and identically: the content is anchored in a fixed plane
        // and the phone is a moving viewport onto it, not a rigid card taped
        // to the screen. Tilting left reveals black on the right; tilting up
        // should reveal black on the bottom the same way — so pitch and roll
        // need the same sign convention here, not one negated relative to
        // the other.
        val rotationXDeg = upward * swing
        val rotationYDeg = rightward * swing

        val cameraDistanceDp = if (tunables.perspectiveEnabled) {
            val strength = tunables.perspectiveStrength.coerceIn(0f, 1f)
            MAX_CAMERA_DISTANCE_DP - strength * (MAX_CAMERA_DISTANCE_DP - MIN_CAMERA_DISTANCE_DP)
        } else {
            NEUTRAL_CAMERA_DISTANCE_DP
        }

        // Driven by the same eased `intensity` as the rotation swing above,
        // so the two animate as one motion rather than drifting apart: the
        // frame leans AND recedes together, which is what sells "distant
        // fixed plane" instead of "flat zoom." The black behind it (the
        // overlay's own container background) does the rest.
        val scale = if (tunables.shrinkEnabled) {
            1f - tunables.maxShrink.coerceIn(0f, 0.9f) * intensity
        } else {
            1f
        }

        val blurPx = if (tunables.blurEnabled) tunables.maxBlurPx * intensity else 0f
        val dim = if (tunables.dimEnabled) tunables.maxDim * intensity else 0f
        val edgeFadeAlpha = if (tunables.edgeFadeEnabled) {
            tunables.edgeFadeStrength.coerceIn(0f, 1f) * intensity
        } else {
            0f
        }

        return Effect(
            rotationXDeg = rotationXDeg,
            rotationYDeg = rotationYDeg,
            scale = scale,
            cameraDistanceDp = cameraDistanceDp,
            blurPx = blurPx,
            dim = dim,
            edgeFadeAlpha = edgeFadeAlpha
        )
    }
}
