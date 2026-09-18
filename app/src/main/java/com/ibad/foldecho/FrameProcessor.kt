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

        /** Signed fold angle for the ray-traced renderer, eased in by [intensity] so it doesn't pop at the activation threshold. */
        val foldTiltDeg: Float,
        /** 0 = the glass hinges on a vertical edge (left/right), 1 = a horizontal one (top/bottom). */
        val hingeAxis: Float,
        /** -1 = hinge on the left/top edge, +1 = right/bottom. */
        val hingeSide: Float
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
        // TiltTracker's "up" is sensor convention: positive means tilted
        // toward the top of the phone. View.rotationX's local axis runs the
        // other way (screen-space Y increases downward), so pitch needs an
        // extra negation roll doesn't — sensor-X and screen-X both point
        // right, so there's no equivalent flip for "rightward." This is
        // separate from `sign`, which both axes still share below for the
        // "content is a fixed plane" counter-rotation itself.
        //
        // Flagging the confidence level on this one: it's the third attempt
        // at this exact sign (see git log on this file), the first two both
        // reported wrong on-device. This time it's a specific, named cause
        // rather than another blind flip, but it still hasn't been verified
        // against real hardware from here. If up/down is still backwards,
        // "Flip tilt direction" corrects it immediately without a rebuild.
        val pitchSign = -sign
        val upward = pitchSign * (tiltUpDeg / tunables.fullTiltDeg).coerceIn(-1f, 1f)
        val rightward = sign * (tiltRightDeg / tunables.fullTiltDeg).coerceIn(-1f, 1f)
        val swing = MAX_ROTATION_DEG * intensity

        // Both axes counter-rotate against the raw tilt direction: the
        // content is anchored in a fixed plane and the phone is a moving
        // viewport onto it, not a rigid card taped to the screen. Tilting
        // left reveals black on the right; tilting up should reveal black on
        // the bottom the same way — that symmetry is already folded into
        // `upward` above via pitchSign, so both lines here read identically.
        val rotationXDeg = upward * swing
        val rotationYDeg = rightward * swing

        // Classic mode only in practice — OverlayController ignores this
        // whenever the fold shader is driving the geometry instead — but
        // computed unconditionally, since there's no separate enable flag
        // for it anymore, only the fold/classic mode selector.
        val strength = tunables.perspectiveStrength.coerceIn(0f, 1f)
        val cameraDistanceDp =
            MAX_CAMERA_DISTANCE_DP - strength * (MAX_CAMERA_DISTANCE_DP - MIN_CAMERA_DISTANCE_DP)

        // Driven by the same eased `intensity` as the rotation swing above,
        // so the two animate as one motion rather than drifting apart: the
        // frame leans AND recedes together, which is what sells "distant
        // fixed plane" instead of "flat zoom." The black behind it (the
        // overlay's own container background) does the rest.
        val scale = 1f - tunables.maxShrink.coerceIn(0f, 0.9f) * intensity

        val blurPx = tunables.maxBlurPx * intensity
        val dim = tunables.maxDim * intensity
        val edgeFadeAlpha = tunables.edgeFadeStrength.coerceIn(0f, 1f) * intensity

        // Which screen edge the glass hinges on: whichever axis currently
        // leans further, and which way. Uses the same pitchSign/sign as the
        // rotation above so the shader hinges on the same edge the classic
        // renderer pivots from, not an independently-guessed one.
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
            foldTiltDeg = axisTiltDeg * intensity,
            hingeAxis = if (pitchDominates) 1f else 0f,
            hingeSide = hingeSide
        )
    }
}
