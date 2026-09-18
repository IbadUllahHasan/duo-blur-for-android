package com.ibad.foldecho

import android.graphics.Matrix

/**
 * Pure math for turning a tilt reading into the three effect parameters —
 * perspective warp matrix, blur radius, dim alpha — kept free of the
 * capture/overlay plumbing so the warp/blur "feel" can be tuned on a real
 * device without touching MediaProjection or WindowManager code.
 *
 * Thresholds picked as a starting point per the spec's open decisions; all
 * four constants below are the ones flagged there as needing on-device
 * tuning rather than a guessable value.
 */
object FrameProcessor {

    /** Deviation (degrees) above which the effect activates. */
    const val ACTIVATE_THRESHOLD_DEG = 12f

    /** Deviation (degrees) below which an already-active effect turns off. Lower than [ACTIVATE_THRESHOLD_DEG] as hysteresis, so it doesn't flicker right at the boundary. */
    const val DEACTIVATE_THRESHOLD_DEG = 7f

    /** Kept subtle per the spec's warning that this is the parameter most likely to look gimmicky. */
    private const val MAX_WARP_SHIFT_FRACTION = 0.05f
    private const val MAX_BLUR_PX = 40f
    private const val MAX_DIM_ALPHA = 0.55f

    /** 0 at [ACTIVATE_THRESHOLD_DEG], 1 at [TiltTracker.NORMALIZE_DEG] deviation. */
    fun intensity(deviationDeg: Float): Float {
        val range = TiltTracker.NORMALIZE_DEG - ACTIVATE_THRESHOLD_DEG
        val t = ((deviationDeg - ACTIVATE_THRESHOLD_DEG) / range).coerceIn(0f, 1f)
        return smoothstep(t)
    }

    /** A "tilted card" perspective: the near edge pulls in, the far edge pushes out, keyed to tilt direction. */
    fun warpMatrix(width: Float, height: Float, dPitch: Float, dRoll: Float, intensity: Float): Matrix {
        val shiftX = width * MAX_WARP_SHIFT_FRACTION * intensity * dRoll
        val shiftY = height * MAX_WARP_SHIFT_FRACTION * intensity * dPitch

        val src = floatArrayOf(
            0f, 0f,
            width, 0f,
            0f, height,
            width, height
        )
        val dst = floatArrayOf(
            0f + shiftX, 0f + shiftY,
            width - shiftX, 0f + shiftY,
            0f - shiftX, height - shiftY,
            width + shiftX, height - shiftY
        )

        val matrix = Matrix()
        matrix.setPolyToPoly(src, 0, dst, 0, 4)
        return matrix
    }

    fun blurRadiusPx(intensity: Float): Float = MAX_BLUR_PX * intensity

    fun dimAlpha(intensity: Float): Float = MAX_DIM_ALPHA * intensity

    private fun smoothstep(t: Float): Float = t * t * (3f - 2f * t)
}
