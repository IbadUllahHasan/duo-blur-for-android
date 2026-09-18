package com.ibad.foldecho

import android.content.Context
import android.content.SharedPreferences

/**
 * The knobs that only real on-device testing can settle. Stored in prefs so
 * the control panel can move them while the service is running, instead of
 * needing a rebuild per adjustment.
 */
data class Tunables(
    val activateDeg: Float = 12f,
    val fullTiltDeg: Float = 30f,
    /** 0..1. Maps to camera distance (FrameProcessor) — how much depth the tilt reveals, not how far the frame leans. */
    val perspectiveStrength: Float = 0.4f,
    val maxBlurPx: Float = 40f,
    val maxDim: Float = 0.55f,
    /** Fraction shrunk at full tilt (scale = 1 - maxShrink). Conservative default — this is the parameter most likely to need on-device tuning. */
    val maxShrink: Float = 0.1f,
    /** Flips tilt-to-lean direction. The "correct" sign depends on the device's sensor axis convention, so this is a runtime toggle rather than a code edit. */
    val flipTiltDirection: Boolean = false
) {
    /** Once active, the effect holds until tilt falls well back toward neutral, so it can't flicker at the boundary. */
    val releaseDeg: Float get() = activateDeg * 0.6f
}

object FoldEchoSettings {
    private const val PREFS = "foldecho_tunables"
    private const val KEY_ACTIVATE = "activate_deg"
    private const val KEY_FULL_TILT = "full_tilt_deg"
    private const val KEY_PERSPECTIVE = "perspective_strength"
    private const val KEY_BLUR = "max_blur_px"
    private const val KEY_DIM = "max_dim"
    private const val KEY_SHRINK = "max_shrink"
    private const val KEY_FLIP = "flip_tilt_direction"

    fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(context: Context): Tunables {
        val prefs = prefs(context)
        val defaults = Tunables()
        return Tunables(
            activateDeg = prefs.getFloat(KEY_ACTIVATE, defaults.activateDeg),
            fullTiltDeg = prefs.getFloat(KEY_FULL_TILT, defaults.fullTiltDeg),
            perspectiveStrength = prefs.getFloat(KEY_PERSPECTIVE, defaults.perspectiveStrength),
            maxBlurPx = prefs.getFloat(KEY_BLUR, defaults.maxBlurPx),
            maxDim = prefs.getFloat(KEY_DIM, defaults.maxDim),
            maxShrink = prefs.getFloat(KEY_SHRINK, defaults.maxShrink),
            flipTiltDirection = prefs.getBoolean(KEY_FLIP, defaults.flipTiltDirection)
        )
    }

    fun save(context: Context, tunables: Tunables) {
        prefs(context).edit()
            .putFloat(KEY_ACTIVATE, tunables.activateDeg)
            .putFloat(KEY_FULL_TILT, tunables.fullTiltDeg)
            .putFloat(KEY_PERSPECTIVE, tunables.perspectiveStrength)
            .putFloat(KEY_BLUR, tunables.maxBlurPx)
            .putFloat(KEY_DIM, tunables.maxDim)
            .putFloat(KEY_SHRINK, tunables.maxShrink)
            .putBoolean(KEY_FLIP, tunables.flipTiltDirection)
            .apply()
    }
}
