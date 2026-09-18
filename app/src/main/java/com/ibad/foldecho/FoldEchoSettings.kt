package com.ibad.foldecho

import android.content.Context
import android.content.SharedPreferences
import android.hardware.display.DisplayManager
import android.view.Display
import android.view.RoundedCorner

/**
 * The knobs that only real on-device testing can settle. Stored in prefs so
 * the control panel can move them while the service is running, instead of
 * needing a rebuild per adjustment. Every visual effect (not the activation
 * thresholds, which gate whether the feature runs at all) also carries an
 * `*Enabled` flag so it can be switched off to A/B it, without losing its
 * dialed-in slider value.
 */
data class Tunables(
    val activateDeg: Float = 12f,
    val fullTiltDeg: Float = 30f,

    /** 0..1. Maps to camera distance (FrameProcessor) — how much depth the tilt reveals, not how far the frame leans. */
    val perspectiveStrength: Float = 0.4f,
    val perspectiveEnabled: Boolean = true,

    val maxBlurPx: Float = 40f,
    val blurEnabled: Boolean = true,

    val maxDim: Float = 0.55f,
    val dimEnabled: Boolean = true,

    /** Fraction shrunk at full tilt (scale = 1 - maxShrink). Conservative default — this is the parameter most likely to need on-device tuning. */
    val maxShrink: Float = 0.1f,
    val shrinkEnabled: Boolean = true,

    /** 0..1, strength scales with tilt magnitude just like maxDim. */
    val edgeFadeStrength: Float = 0.3f,
    val edgeFadeEnabled: Boolean = true,

    /** 0..1, a fraction OF [cornerRadiusBaseDp] — 1.0 means "as detected from the device," not an independent absolute radius. */
    val cornerRadiusStrength: Float = 1f,
    val cornerRadiusEnabled: Boolean = true,
    /** Resolved from the device's actual screen corners at load() time (falls back to [FALLBACK_CORNER_RADIUS_DP]); not itself user-facing, and never persisted since it's re-derived from the device every load. */
    val cornerRadiusBaseDp: Float = FALLBACK_CORNER_RADIUS_DP,

    /** 0..1 damping for the rotation/scale spring. 0 settles cleanly with no overshoot; 1 lets it swing past the target and bounce back. */
    val motionSoftness: Float = 0.25f,
    val motionSoftnessEnabled: Boolean = true,

    /** Swaps the lean/scale transform for the ray-traced AGSL fold. Ignored below API 33, or if the shader won't compile. */
    val foldShaderEnabled: Boolean = true,
    /** How far the eye sits from the content plane, along its normal. */
    val viewDistanceMm: Float = 300f,
    /** Blur radius in px gained per mm of glass-to-plane gap. */
    val blurPerMm: Float = 1.5f,
    val maxBlurRadiusPx: Float = 48f,
    /** Fraction of light lost per mm of gap. */
    val darkenPerMm: Float = 0.02f,
    val maxDarken: Float = 0.8f,

    /** Flips tilt-to-lean direction. The "correct" sign depends on the device's sensor axis convention, so this is a runtime toggle rather than a code edit. */
    val flipTiltDirection: Boolean = false
) {
    /** Once active, the effect holds until tilt falls well back toward neutral, so it can't flicker at the boundary. */
    val releaseDeg: Float get() = activateDeg * 0.6f

    companion object {
        const val FALLBACK_CORNER_RADIUS_DP = 24f
    }
}

object FoldEchoSettings {
    private const val PREFS = "foldecho_tunables"
    private const val KEY_ACTIVATE = "activate_deg"
    private const val KEY_FULL_TILT = "full_tilt_deg"
    private const val KEY_PERSPECTIVE = "perspective_strength"
    private const val KEY_PERSPECTIVE_ENABLED = "perspective_enabled"
    private const val KEY_BLUR = "max_blur_px"
    private const val KEY_BLUR_ENABLED = "blur_enabled"
    private const val KEY_DIM = "max_dim"
    private const val KEY_DIM_ENABLED = "dim_enabled"
    private const val KEY_SHRINK = "max_shrink"
    private const val KEY_SHRINK_ENABLED = "shrink_enabled"
    private const val KEY_EDGE_FADE = "edge_fade_strength"
    private const val KEY_EDGE_FADE_ENABLED = "edge_fade_enabled"
    private const val KEY_CORNER_RADIUS = "corner_radius_strength"
    private const val KEY_CORNER_RADIUS_ENABLED = "corner_radius_enabled"
    private const val KEY_MOTION_SOFTNESS = "motion_softness"
    private const val KEY_MOTION_SOFTNESS_ENABLED = "motion_softness_enabled"
    private const val KEY_FOLD_SHADER_ENABLED = "fold_shader_enabled"
    private const val KEY_VIEW_DISTANCE_MM = "view_distance_mm"
    private const val KEY_BLUR_PER_MM = "blur_per_mm"
    private const val KEY_MAX_BLUR_RADIUS_PX = "max_blur_radius_px"
    private const val KEY_DARKEN_PER_MM = "darken_per_mm"
    private const val KEY_MAX_DARKEN = "max_darken"
    private const val KEY_FLIP = "flip_tilt_direction"

    fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(context: Context): Tunables {
        val prefs = prefs(context)
        val defaults = Tunables(cornerRadiusBaseDp = defaultCornerRadiusDp(context))
        return Tunables(
            activateDeg = prefs.getFloat(KEY_ACTIVATE, defaults.activateDeg),
            fullTiltDeg = prefs.getFloat(KEY_FULL_TILT, defaults.fullTiltDeg),
            perspectiveStrength = prefs.getFloat(KEY_PERSPECTIVE, defaults.perspectiveStrength),
            perspectiveEnabled = prefs.getBoolean(KEY_PERSPECTIVE_ENABLED, defaults.perspectiveEnabled),
            maxBlurPx = prefs.getFloat(KEY_BLUR, defaults.maxBlurPx),
            blurEnabled = prefs.getBoolean(KEY_BLUR_ENABLED, defaults.blurEnabled),
            maxDim = prefs.getFloat(KEY_DIM, defaults.maxDim),
            dimEnabled = prefs.getBoolean(KEY_DIM_ENABLED, defaults.dimEnabled),
            maxShrink = prefs.getFloat(KEY_SHRINK, defaults.maxShrink),
            shrinkEnabled = prefs.getBoolean(KEY_SHRINK_ENABLED, defaults.shrinkEnabled),
            edgeFadeStrength = prefs.getFloat(KEY_EDGE_FADE, defaults.edgeFadeStrength),
            edgeFadeEnabled = prefs.getBoolean(KEY_EDGE_FADE_ENABLED, defaults.edgeFadeEnabled),
            cornerRadiusStrength = prefs.getFloat(KEY_CORNER_RADIUS, defaults.cornerRadiusStrength),
            cornerRadiusEnabled = prefs.getBoolean(KEY_CORNER_RADIUS_ENABLED, defaults.cornerRadiusEnabled),
            cornerRadiusBaseDp = defaults.cornerRadiusBaseDp,
            motionSoftness = prefs.getFloat(KEY_MOTION_SOFTNESS, defaults.motionSoftness),
            motionSoftnessEnabled = prefs.getBoolean(KEY_MOTION_SOFTNESS_ENABLED, defaults.motionSoftnessEnabled),
            foldShaderEnabled = prefs.getBoolean(KEY_FOLD_SHADER_ENABLED, defaults.foldShaderEnabled),
            viewDistanceMm = prefs.getFloat(KEY_VIEW_DISTANCE_MM, defaults.viewDistanceMm),
            blurPerMm = prefs.getFloat(KEY_BLUR_PER_MM, defaults.blurPerMm),
            maxBlurRadiusPx = prefs.getFloat(KEY_MAX_BLUR_RADIUS_PX, defaults.maxBlurRadiusPx),
            darkenPerMm = prefs.getFloat(KEY_DARKEN_PER_MM, defaults.darkenPerMm),
            maxDarken = prefs.getFloat(KEY_MAX_DARKEN, defaults.maxDarken),
            flipTiltDirection = prefs.getBoolean(KEY_FLIP, defaults.flipTiltDirection)
        )
    }

    fun save(context: Context, tunables: Tunables) {
        // cornerRadiusBaseDp is device-derived, not user input, so it's deliberately not persisted here.
        prefs(context).edit()
            .putFloat(KEY_ACTIVATE, tunables.activateDeg)
            .putFloat(KEY_FULL_TILT, tunables.fullTiltDeg)
            .putFloat(KEY_PERSPECTIVE, tunables.perspectiveStrength)
            .putBoolean(KEY_PERSPECTIVE_ENABLED, tunables.perspectiveEnabled)
            .putFloat(KEY_BLUR, tunables.maxBlurPx)
            .putBoolean(KEY_BLUR_ENABLED, tunables.blurEnabled)
            .putFloat(KEY_DIM, tunables.maxDim)
            .putBoolean(KEY_DIM_ENABLED, tunables.dimEnabled)
            .putFloat(KEY_SHRINK, tunables.maxShrink)
            .putBoolean(KEY_SHRINK_ENABLED, tunables.shrinkEnabled)
            .putFloat(KEY_EDGE_FADE, tunables.edgeFadeStrength)
            .putBoolean(KEY_EDGE_FADE_ENABLED, tunables.edgeFadeEnabled)
            .putFloat(KEY_CORNER_RADIUS, tunables.cornerRadiusStrength)
            .putBoolean(KEY_CORNER_RADIUS_ENABLED, tunables.cornerRadiusEnabled)
            .putFloat(KEY_MOTION_SOFTNESS, tunables.motionSoftness)
            .putBoolean(KEY_MOTION_SOFTNESS_ENABLED, tunables.motionSoftnessEnabled)
            .putBoolean(KEY_FOLD_SHADER_ENABLED, tunables.foldShaderEnabled)
            .putFloat(KEY_VIEW_DISTANCE_MM, tunables.viewDistanceMm)
            .putFloat(KEY_BLUR_PER_MM, tunables.blurPerMm)
            .putFloat(KEY_MAX_BLUR_RADIUS_PX, tunables.maxBlurRadiusPx)
            .putFloat(KEY_DARKEN_PER_MM, tunables.darkenPerMm)
            .putFloat(KEY_MAX_DARKEN, tunables.maxDarken)
            .putBoolean(KEY_FLIP, tunables.flipTiltDirection)
            .apply()
    }

    /** Writes every tunable back to its class default (device-detected corner radius included) and returns it, so the caller can update its in-memory state without a separate load(). */
    fun reset(context: Context): Tunables {
        val defaults = Tunables(cornerRadiusBaseDp = defaultCornerRadiusDp(context))
        save(context, defaults)
        return defaults
    }

    /**
     * DisplayManager (not Context.getDisplay()) because this runs from
     * FoldEchoService as well as MainActivity — a plain Service context isn't
     * associated with a Display, and Context.getDisplay() throws on one.
     */
    private fun defaultCornerRadiusDp(context: Context): Float {
        val display = context.getSystemService(DisplayManager::class.java)
            ?.getDisplay(Display.DEFAULT_DISPLAY)
        val radiusPx = display?.getRoundedCorner(RoundedCorner.POSITION_TOP_LEFT)?.radius
            ?: return Tunables.FALLBACK_CORNER_RADIUS_DP
        val radiusDp = radiusPx / context.resources.displayMetrics.density
        return if (radiusDp > 0f) radiusDp else Tunables.FALLBACK_CORNER_RADIUS_DP
    }
}
