package com.ibad.foldecho

import android.content.Context
import android.content.SharedPreferences
import android.hardware.display.DisplayManager
import android.view.Display
import android.view.RoundedCorner

/**
 * Which colour scheme the app UI uses. [SYSTEM] follows the device's
 * light/dark setting; the other two pin it regardless, so the two glass
 * palettes can be compared side by side without leaving the app.
 */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * The knobs that only real on-device testing can settle. Stored in prefs so
 * the control panel can move them while the service is running, instead of
 * needing a rebuild per adjustment. Whether a mode-specific *section* applies
 * at all is decided by [foldShaderEnabled] alone — the control panel hides
 * the section that doesn't match the selected mode. Within a visible
 * section, most sliders also carry their own `*Enabled` flag so a single
 * parameter can be switched off without losing its dialed-in value or
 * touching anything else.
 */
data class Tunables(
    val activateDeg: Float = 12f,
    val fullTiltDeg: Float = 30f,

    /** Classic mode only. 0..1. Maps to camera distance (FrameProcessor) — how much depth the tilt reveals, not how far the frame leans. */
    val perspectiveStrength: Float = 0.4f,
    /** When false, [perspectiveStrength] is ignored and treated as 0 (no perspective depth) without losing the dialed-in value. */
    val perspectiveEnabled: Boolean = true,

    /** Shared by both render modes. */
    val maxBlurPx: Float = 40f,
    val maxBlurPxEnabled: Boolean = true,
    val maxDim: Float = 0.55f,
    val maxDimEnabled: Boolean = true,
    /** Shared. 0..1, strength scales with tilt magnitude just like maxDim. */
    val edgeFadeStrength: Float = 0.3f,
    val edgeFadeEnabled: Boolean = true,

    /** Classic mode only. Fraction shrunk at full tilt (scale = 1 - maxShrink). Conservative default — this is the parameter most likely to need on-device tuning. */
    val maxShrink: Float = 0.1f,
    val maxShrinkEnabled: Boolean = true,

    /** Shared. 0..1, a fraction OF [cornerRadiusBaseDp] — 1.0 means "as detected from the device," not an independent absolute radius. */
    val cornerRadiusStrength: Float = 1f,
    val cornerRadiusEnabled: Boolean = true,
    /** Resolved from the device's actual screen corners at load() time (falls back to [FALLBACK_CORNER_RADIUS_DP]); not itself user-facing, and never persisted since it's re-derived from the device every load. */
    val cornerRadiusBaseDp: Float = FALLBACK_CORNER_RADIUS_DP,

    /** Classic mode only. 0..1 damping for the rotation/scale spring. 0 settles cleanly with no overshoot; 1 lets it swing past the target and bounce back. */
    val motionSoftness: Float = 0.25f,
    /** When false, treated as 0 — the cleanest settle the spring allows, same as the low end of the slider. */
    val motionSoftnessEnabled: Boolean = true,

    /** The mode selector: true picks the ray-traced AGSL fold, false the classic lean/scale transform. Ignored (falls back to classic) below API 33, or if the shader won't compile. */
    val foldShaderEnabled: Boolean = true,
    /** Ray-traced mode only. How far the eye sits from the content plane, along its normal. */
    val viewDistanceMm: Float = 300f,
    /** When false, the shader uses the class default distance instead of this value — unlike the other ray-traced toggles, 0 isn't a usable "off" here since the ray math divides by it. */
    val viewDistanceEnabled: Boolean = true,
    /** Ray-traced mode only. Blur radius in px gained per mm of glass-to-plane gap. */
    val blurPerMm: Float = 1.5f,
    val blurPerMmEnabled: Boolean = true,
    val maxBlurRadiusPx: Float = 48f,
    val maxBlurRadiusEnabled: Boolean = true,
    /** Ray-traced mode only. Fraction of light lost per mm of gap. */
    val darkenPerMm: Float = 0.02f,
    val darkenPerMmEnabled: Boolean = true,
    val maxDarken: Float = 0.8f,
    val maxDarkenEnabled: Boolean = true,

    /** Flips tilt-to-lean direction. The "correct" sign depends on the device's sensor axis convention, so this is a runtime toggle rather than a code edit. */
    val flipTiltDirection: Boolean = false,

    /** App UI colour scheme. Purely cosmetic — no bearing on the fold effect itself. */
    val themeMode: ThemeMode = ThemeMode.SYSTEM,

    /** In-app UI feedback only (slider drags, switches) — no bearing on the fold effect itself. */
    val uiHapticsEnabled: Boolean = true,

    /** Tactile feedback tied to the fold effect itself: engaging, releasing, and the safety auto-release. */
    val blurHapticsEnabled: Boolean = true,
    val blurHapticsEngageStrength: Float = 0.65f,
    val blurHapticsEngageEnabled: Boolean = true,
    val blurHapticsReleaseStrength: Float = 0.5f,
    val blurHapticsReleaseEnabled: Boolean = true,
    val blurHapticsTimeoutStrength: Float = 0.85f,
    val blurHapticsTimeoutEnabled: Boolean = true
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
    private const val KEY_BLUR_ENABLED = "max_blur_px_enabled"
    private const val KEY_DIM = "max_dim"
    private const val KEY_DIM_ENABLED = "max_dim_enabled"
    private const val KEY_SHRINK = "max_shrink"
    private const val KEY_SHRINK_ENABLED = "max_shrink_enabled"
    private const val KEY_EDGE_FADE = "edge_fade_strength"
    private const val KEY_EDGE_FADE_ENABLED = "edge_fade_enabled"
    private const val KEY_CORNER_RADIUS = "corner_radius_strength"
    private const val KEY_CORNER_RADIUS_ENABLED = "corner_radius_enabled"
    private const val KEY_MOTION_SOFTNESS = "motion_softness"
    private const val KEY_MOTION_SOFTNESS_ENABLED = "motion_softness_enabled"
    private const val KEY_FOLD_SHADER_ENABLED = "fold_shader_enabled"
    private const val KEY_VIEW_DISTANCE_MM = "view_distance_mm"
    private const val KEY_VIEW_DISTANCE_ENABLED = "view_distance_enabled"
    private const val KEY_BLUR_PER_MM = "blur_per_mm"
    private const val KEY_BLUR_PER_MM_ENABLED = "blur_per_mm_enabled"
    private const val KEY_MAX_BLUR_RADIUS_PX = "max_blur_radius_px"
    private const val KEY_MAX_BLUR_RADIUS_ENABLED = "max_blur_radius_enabled"
    private const val KEY_DARKEN_PER_MM = "darken_per_mm"
    private const val KEY_DARKEN_PER_MM_ENABLED = "darken_per_mm_enabled"
    private const val KEY_MAX_DARKEN = "max_darken"
    private const val KEY_MAX_DARKEN_ENABLED = "max_darken_enabled"
    private const val KEY_FLIP = "flip_tilt_direction"
    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_UI_HAPTICS_ENABLED = "ui_haptics_enabled"
    private const val KEY_BLUR_HAPTICS_ENABLED = "blur_haptics_enabled"
    private const val KEY_BLUR_HAPTICS_ENGAGE_STRENGTH = "blur_haptics_engage_strength"
    private const val KEY_BLUR_HAPTICS_ENGAGE_ENABLED = "blur_haptics_engage_enabled"
    private const val KEY_BLUR_HAPTICS_RELEASE_STRENGTH = "blur_haptics_release_strength"
    private const val KEY_BLUR_HAPTICS_RELEASE_ENABLED = "blur_haptics_release_enabled"
    private const val KEY_BLUR_HAPTICS_TIMEOUT_STRENGTH = "blur_haptics_timeout_strength"
    private const val KEY_BLUR_HAPTICS_TIMEOUT_ENABLED = "blur_haptics_timeout_enabled"

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
            maxBlurPxEnabled = prefs.getBoolean(KEY_BLUR_ENABLED, defaults.maxBlurPxEnabled),
            maxDim = prefs.getFloat(KEY_DIM, defaults.maxDim),
            maxDimEnabled = prefs.getBoolean(KEY_DIM_ENABLED, defaults.maxDimEnabled),
            maxShrink = prefs.getFloat(KEY_SHRINK, defaults.maxShrink),
            maxShrinkEnabled = prefs.getBoolean(KEY_SHRINK_ENABLED, defaults.maxShrinkEnabled),
            edgeFadeStrength = prefs.getFloat(KEY_EDGE_FADE, defaults.edgeFadeStrength),
            edgeFadeEnabled = prefs.getBoolean(KEY_EDGE_FADE_ENABLED, defaults.edgeFadeEnabled),
            cornerRadiusStrength = prefs.getFloat(KEY_CORNER_RADIUS, defaults.cornerRadiusStrength),
            cornerRadiusEnabled = prefs.getBoolean(KEY_CORNER_RADIUS_ENABLED, defaults.cornerRadiusEnabled),
            cornerRadiusBaseDp = defaults.cornerRadiusBaseDp,
            motionSoftness = prefs.getFloat(KEY_MOTION_SOFTNESS, defaults.motionSoftness),
            motionSoftnessEnabled = prefs.getBoolean(KEY_MOTION_SOFTNESS_ENABLED, defaults.motionSoftnessEnabled),
            foldShaderEnabled = prefs.getBoolean(KEY_FOLD_SHADER_ENABLED, defaults.foldShaderEnabled),
            viewDistanceMm = prefs.getFloat(KEY_VIEW_DISTANCE_MM, defaults.viewDistanceMm),
            viewDistanceEnabled = prefs.getBoolean(KEY_VIEW_DISTANCE_ENABLED, defaults.viewDistanceEnabled),
            blurPerMm = prefs.getFloat(KEY_BLUR_PER_MM, defaults.blurPerMm),
            blurPerMmEnabled = prefs.getBoolean(KEY_BLUR_PER_MM_ENABLED, defaults.blurPerMmEnabled),
            maxBlurRadiusPx = prefs.getFloat(KEY_MAX_BLUR_RADIUS_PX, defaults.maxBlurRadiusPx),
            maxBlurRadiusEnabled = prefs.getBoolean(KEY_MAX_BLUR_RADIUS_ENABLED, defaults.maxBlurRadiusEnabled),
            darkenPerMm = prefs.getFloat(KEY_DARKEN_PER_MM, defaults.darkenPerMm),
            darkenPerMmEnabled = prefs.getBoolean(KEY_DARKEN_PER_MM_ENABLED, defaults.darkenPerMmEnabled),
            maxDarken = prefs.getFloat(KEY_MAX_DARKEN, defaults.maxDarken),
            maxDarkenEnabled = prefs.getBoolean(KEY_MAX_DARKEN_ENABLED, defaults.maxDarkenEnabled),
            flipTiltDirection = prefs.getBoolean(KEY_FLIP, defaults.flipTiltDirection),
            themeMode = readThemeMode(prefs, defaults.themeMode),
            uiHapticsEnabled = prefs.getBoolean(KEY_UI_HAPTICS_ENABLED, defaults.uiHapticsEnabled),
            blurHapticsEnabled = prefs.getBoolean(KEY_BLUR_HAPTICS_ENABLED, defaults.blurHapticsEnabled),
            blurHapticsEngageStrength = prefs.getFloat(KEY_BLUR_HAPTICS_ENGAGE_STRENGTH, defaults.blurHapticsEngageStrength),
            blurHapticsEngageEnabled = prefs.getBoolean(KEY_BLUR_HAPTICS_ENGAGE_ENABLED, defaults.blurHapticsEngageEnabled),
            blurHapticsReleaseStrength = prefs.getFloat(KEY_BLUR_HAPTICS_RELEASE_STRENGTH, defaults.blurHapticsReleaseStrength),
            blurHapticsReleaseEnabled = prefs.getBoolean(KEY_BLUR_HAPTICS_RELEASE_ENABLED, defaults.blurHapticsReleaseEnabled),
            blurHapticsTimeoutStrength = prefs.getFloat(KEY_BLUR_HAPTICS_TIMEOUT_STRENGTH, defaults.blurHapticsTimeoutStrength),
            blurHapticsTimeoutEnabled = prefs.getBoolean(KEY_BLUR_HAPTICS_TIMEOUT_ENABLED, defaults.blurHapticsTimeoutEnabled)
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
            .putBoolean(KEY_BLUR_ENABLED, tunables.maxBlurPxEnabled)
            .putFloat(KEY_DIM, tunables.maxDim)
            .putBoolean(KEY_DIM_ENABLED, tunables.maxDimEnabled)
            .putFloat(KEY_SHRINK, tunables.maxShrink)
            .putBoolean(KEY_SHRINK_ENABLED, tunables.maxShrinkEnabled)
            .putFloat(KEY_EDGE_FADE, tunables.edgeFadeStrength)
            .putBoolean(KEY_EDGE_FADE_ENABLED, tunables.edgeFadeEnabled)
            .putFloat(KEY_CORNER_RADIUS, tunables.cornerRadiusStrength)
            .putBoolean(KEY_CORNER_RADIUS_ENABLED, tunables.cornerRadiusEnabled)
            .putFloat(KEY_MOTION_SOFTNESS, tunables.motionSoftness)
            .putBoolean(KEY_MOTION_SOFTNESS_ENABLED, tunables.motionSoftnessEnabled)
            .putBoolean(KEY_FOLD_SHADER_ENABLED, tunables.foldShaderEnabled)
            .putFloat(KEY_VIEW_DISTANCE_MM, tunables.viewDistanceMm)
            .putBoolean(KEY_VIEW_DISTANCE_ENABLED, tunables.viewDistanceEnabled)
            .putFloat(KEY_BLUR_PER_MM, tunables.blurPerMm)
            .putBoolean(KEY_BLUR_PER_MM_ENABLED, tunables.blurPerMmEnabled)
            .putFloat(KEY_MAX_BLUR_RADIUS_PX, tunables.maxBlurRadiusPx)
            .putBoolean(KEY_MAX_BLUR_RADIUS_ENABLED, tunables.maxBlurRadiusEnabled)
            .putFloat(KEY_DARKEN_PER_MM, tunables.darkenPerMm)
            .putBoolean(KEY_DARKEN_PER_MM_ENABLED, tunables.darkenPerMmEnabled)
            .putFloat(KEY_MAX_DARKEN, tunables.maxDarken)
            .putBoolean(KEY_MAX_DARKEN_ENABLED, tunables.maxDarkenEnabled)
            .putBoolean(KEY_FLIP, tunables.flipTiltDirection)
            .putString(KEY_THEME_MODE, tunables.themeMode.name)
            .putBoolean(KEY_UI_HAPTICS_ENABLED, tunables.uiHapticsEnabled)
            .putBoolean(KEY_BLUR_HAPTICS_ENABLED, tunables.blurHapticsEnabled)
            .putFloat(KEY_BLUR_HAPTICS_ENGAGE_STRENGTH, tunables.blurHapticsEngageStrength)
            .putBoolean(KEY_BLUR_HAPTICS_ENGAGE_ENABLED, tunables.blurHapticsEngageEnabled)
            .putFloat(KEY_BLUR_HAPTICS_RELEASE_STRENGTH, tunables.blurHapticsReleaseStrength)
            .putBoolean(KEY_BLUR_HAPTICS_RELEASE_ENABLED, tunables.blurHapticsReleaseEnabled)
            .putFloat(KEY_BLUR_HAPTICS_TIMEOUT_STRENGTH, tunables.blurHapticsTimeoutStrength)
            .putBoolean(KEY_BLUR_HAPTICS_TIMEOUT_ENABLED, tunables.blurHapticsTimeoutEnabled)
            .apply()
    }

    /**
     * Stored by name rather than ordinal so reordering the enum can't silently
     * repoint an existing install, and an unrecognised value falls back to the
     * default instead of throwing.
     */
    private fun readThemeMode(prefs: SharedPreferences, default: ThemeMode): ThemeMode {
        val stored = prefs.getString(KEY_THEME_MODE, null) ?: return default
        return ThemeMode.values().firstOrNull { it.name == stored } ?: default
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
