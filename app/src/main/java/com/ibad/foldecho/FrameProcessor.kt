package com.ibad.foldecho

/**
 * Turns a tilt reading into the effect parameters. Perspective is expressed as
 * View rotationX/rotationY rather than a bitmap Matrix: those are real
 * GPU-accelerated 3D transforms, where a perspective Matrix forces every draw
 * of a full-screen bitmap down Skia's software path — which is what made the
 * effect stutter.
 */
object FrameProcessor {

    data class Effect(
        val rotationXDeg: Float,
        val rotationYDeg: Float,
        val scale: Float,
        val blurPx: Float,
        val dim: Float
    )

    /** Scale up slightly so the edges revealed by the 3D rotation stay covered. */
    private const val MAX_OVERSCAN = 0.05f

    fun effectFor(deviationDeg: Float, tiltUpDeg: Float, tiltRightDeg: Float, tunables: Tunables): Effect {
        val span = (tunables.fullTiltDeg - tunables.activateDeg).coerceAtLeast(1f)
        val raw = ((deviationDeg - tunables.activateDeg) / span).coerceIn(0f, 1f)
        val intensity = raw * raw * (3f - 2f * raw)

        val upward = (tiltUpDeg / tunables.fullTiltDeg).coerceIn(-1f, 1f)
        val rightward = (tiltRightDeg / tunables.fullTiltDeg).coerceIn(-1f, 1f)
        val swing = tunables.maxRotationDeg * intensity

        // Flip either sign here if the image leans the wrong way on your phone.
        return Effect(
            rotationXDeg = upward * swing,
            rotationYDeg = -rightward * swing,
            scale = 1f + MAX_OVERSCAN * intensity,
            blurPx = tunables.maxBlurPx * intensity,
            dim = tunables.maxDim * intensity
        )
    }
}
