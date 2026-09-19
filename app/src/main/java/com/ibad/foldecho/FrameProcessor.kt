package com.ibad.foldecho

import kotlin.math.abs

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
        val edgeFadeAlpha: Float,

        /** Fold angle magnitude for the ray-traced renderer, eased in by [intensity] so it doesn't pop at the activation threshold. Sign doesn't matter — the shader only reads its absolute value; direction comes from [directionUp]/[directionRight] instead. */
        val foldTiltDeg: Float,
        /**
         * 0 = a vertical edge (left/right), 1 = a horizontal one (top/bottom).
         * A discrete axis pick used only by the classic renderer's graded-blur
         * mask (OverlayController.blurMaskFor), which draws a straight
         * gradient and so needs one edge to align it to. The ray-traced
         * shader's own hinge is continuous now — see [directionUp]/
         * [directionRight] — and no longer reads this.
         */
        val hingeAxis: Float,
        /** -1 = hinge on the left/top edge, +1 = right/bottom. Same graded-blur-mask-only scope as [hingeAxis]. */
        val hingeSide: Float,

        /**
         * -1..1, continuous (not axis-snapped like [hingeAxis]/[hingeSide]).
         * The classic renderer's pivot sweeps by these directly, so it moves
         * smoothly as tilt direction changes instead of jumping between four
         * fixed points whenever pitch/roll trade off which one dominates.
         */
        val directionUp: Float,
        val directionRight: Float
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

    fun effectFor(deviationDeg: Float, tiltUpDeg: Float, tiltRightDeg: Float, tunables: Tunables): Effect {
        val span = (tunables.fullTiltDeg - tunables.activateDeg).coerceAtLeast(1f)
        val raw = ((deviationDeg - tunables.activateDeg) / span).coerceIn(0f, 1f)
        val intensity = raw * raw * (3f - 2f * raw)

        val sign = if (tunables.flipTiltDirection) -1f else 1f
        // pitchSign matches roll's own sign convention: `upward` and
        // `rightward` both feed the pivot (applyHingePivot) and the
        // ray-traced shader's hinge-side pick the same way, and that pairing
        // is confirmed correct on-device for both axes — so `upward` itself
        // is not the thing to flip.
        val pitchSign = -sign
        val upward = pitchSign * (tiltUpDeg / tunables.fullTiltDeg).coerceIn(-1f, 1f)
        val rightward = sign * (tiltRightDeg / tunables.fullTiltDeg).coerceIn(-1f, 1f)
        val swing = MAX_ROTATION_DEG * intensity

        // Classic mode's own lean transform, separate from `upward`/
        // `rightward` above. Roll's rotationYDeg matches `rightward`'s sign
        // directly and is confirmed correct; pitch's rotationXDeg needed the
        // opposite relationship to read correctly on-device — View's
        // rotationX and rotationY don't share a sign convention the way
        // sensor pitch/roll do, so this negation is specific to the classic
        // renderer's lean and does not apply to `upward` itself (which stays
        // shared, correctly, with the pivot and the ray-traced shader).
        val rotationXDeg = -upward * swing
        val rotationYDeg = rightward * swing

        // Classic mode only in practice — OverlayController ignores this
        // whenever the fold shader is driving the geometry instead — but
        // computed unconditionally, since there's no separate enable flag
        // for it anymore, only the fold/classic mode selector. Disabling the
        // "Perspective" toggle treats strength as 0 (camera all the way out,
        // i.e. no perspective) without losing the dialed-in slider value.
        val strength = (if (tunables.perspectiveEnabled) tunables.perspectiveStrength else 0f).coerceIn(0f, 1f)
        val cameraDistanceDp =
            MAX_CAMERA_DISTANCE_DP - strength * (MAX_CAMERA_DISTANCE_DP - MIN_CAMERA_DISTANCE_DP)

        // Driven by the same eased `intensity` as the rotation swing above,
        // so the two animate as one motion rather than drifting apart: the
        // frame leans AND recedes together, which is what sells "distant
        // fixed plane" instead of "flat zoom." The black behind it (the
        // overlay's own container background) does the rest.
        val shrink = if (tunables.maxShrinkEnabled) tunables.maxShrink else 0f
        val scale = 1f - shrink.coerceIn(0f, 0.9f) * intensity

        val blurPx = (if (tunables.maxBlurPxEnabled) tunables.maxBlurPx else 0f) * intensity
        val dim = (if (tunables.maxDimEnabled) tunables.maxDim else 0f) * intensity
        val edgeFadeStrength = if (tunables.edgeFadeEnabled) tunables.edgeFadeStrength else 0f
        val edgeFadeAlpha = edgeFadeStrength.coerceIn(0f, 1f) * intensity

        // Which screen edge the graded-blur mask (classic renderer only)
        // hinges on: whichever axis currently leans further, and which way —
        // a discrete pick, since a straight gradient can only align to one
        // edge at a time. The ray-traced shader doesn't use this anymore;
        // its hinge sweeps continuously off directionUp/directionRight
        // below, which is what avoids the jump this discrete choice would
        // otherwise cause when pitch and roll trade off which one dominates.
        val signedUp = pitchSign * tiltUpDeg
        val signedRight = sign * tiltRightDeg
        val pitchDominates = abs(signedUp) > abs(signedRight)
        val axisTiltDeg = if (pitchDominates) signedUp else signedRight
        // Hinge on the near edge, so the gap — and the black it eventually
        // opens up — grows on the far side, matching which way the transform
        // renderer leans.
        val hingeSide = if (axisTiltDeg >= 0f) -1f else 1f

        return Effect(
            rotationXDeg = rotationXDeg,
            rotationYDeg = rotationYDeg,
            scale = scale,
            cameraDistanceDp = cameraDistanceDp,
            blurPx = blurPx,
            dim = dim,
            edgeFadeAlpha = edgeFadeAlpha,
            foldTiltDeg = deviationDeg * intensity,
            hingeAxis = if (pitchDominates) 1f else 0f,
            hingeSide = hingeSide,
            directionUp = upward,
            directionRight = rightward
        )
    }
}
