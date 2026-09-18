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
    val maxRotationDeg: Float = 6f,
    val maxBlurPx: Float = 40f,
    val maxDim: Float = 0.55f
) {
    /** Once active, the effect holds until tilt falls well back toward neutral, so it can't flicker at the boundary. */
    val releaseDeg: Float get() = activateDeg * 0.6f
}

object FoldEchoSettings {
    private const val PREFS = "foldecho_tunables"
    private const val KEY_ACTIVATE = "activate_deg"
    private const val KEY_FULL_TILT = "full_tilt_deg"
    private const val KEY_ROTATION = "max_rotation_deg"
    private const val KEY_BLUR = "max_blur_px"
    private const val KEY_DIM = "max_dim"

    fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(context: Context): Tunables {
        val prefs = prefs(context)
        val defaults = Tunables()
        return Tunables(
            activateDeg = prefs.getFloat(KEY_ACTIVATE, defaults.activateDeg),
            fullTiltDeg = prefs.getFloat(KEY_FULL_TILT, defaults.fullTiltDeg),
            maxRotationDeg = prefs.getFloat(KEY_ROTATION, defaults.maxRotationDeg),
            maxBlurPx = prefs.getFloat(KEY_BLUR, defaults.maxBlurPx),
            maxDim = prefs.getFloat(KEY_DIM, defaults.maxDim)
        )
    }

    fun save(context: Context, tunables: Tunables) {
        prefs(context).edit()
            .putFloat(KEY_ACTIVATE, tunables.activateDeg)
            .putFloat(KEY_FULL_TILT, tunables.fullTiltDeg)
            .putFloat(KEY_ROTATION, tunables.maxRotationDeg)
            .putFloat(KEY_BLUR, tunables.maxBlurPx)
            .putFloat(KEY_DIM, tunables.maxDim)
            .apply()
    }
}
