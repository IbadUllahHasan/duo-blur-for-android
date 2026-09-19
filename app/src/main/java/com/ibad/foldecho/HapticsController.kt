package com.ibad.foldecho

import android.content.Context
import android.os.VibrationEffect
import android.os.VibratorManager

/**
 * Wraps the platform's composition-primitives API (available since API 30,
 * safely below this app's minSdk 31) with a plain amplitude-based fallback
 * for devices whose actuator can't do primitives. Two independent event
 * groups, matching the two haptics toggles in the tuning panel: light
 * "interface" ticks for UI interaction, and the "fold" cues tied to the
 * tilt gesture itself (engage / release / safety auto-release).
 *
 * Switch flips do **not** come through here on API 34+. They go through
 * View.performHapticFeedback(HapticFeedbackConstants.TOGGLE_ON/TOGGLE_OFF)
 * instead, so they get the OEM's own tuned toggle haptic (Samsung's
 * vibration HAL, for one) rather than a generic primitive we picked. See
 * MainActivity.rememberToggleHaptic. [toggle] below is only the pre-34
 * fallback, for devices where those constants don't exist yet.
 */
class HapticsController(context: Context) {
    private val vibrator = context.getSystemService(VibratorManager::class.java)?.defaultVibrator

    private val supportsPrimitives = vibrator?.areAllPrimitivesSupported(
        VibrationEffect.Composition.PRIMITIVE_TICK,
        VibrationEffect.Composition.PRIMITIVE_CLICK,
        VibrationEffect.Composition.PRIMITIVE_QUICK_RISE,
        VibrationEffect.Composition.PRIMITIVE_THUD
    ) ?: false

    /**
     * Interface feedback strength. Deliberately well above the 0.2f this
     * used to run at: at that level the tick was inaudible-by-touch on most
     * actuators, which read as "the haptics are broken" rather than
     * "the haptics are subtle".
     */
    private val interfaceStrength = 0.7f

    /** A definite tap for slider releases, accordion headers and segmented-control picks. */
    fun interfaceTick() =
        fire(VibrationEffect.Composition.PRIMITIVE_CLICK, interfaceStrength, fallbackMs = 14L)

    /**
     * Pre-34 stand-in for TOGGLE_ON / TOGGLE_OFF: a firmer click going on, a
     * lighter tick coming off, so the two directions stay distinguishable
     * the way the platform constants are.
     */
    fun toggle(on: Boolean) = if (on) {
        fire(VibrationEffect.Composition.PRIMITIVE_CLICK, interfaceStrength, fallbackMs = 16L)
    } else {
        fire(VibrationEffect.Composition.PRIMITIVE_TICK, interfaceStrength * 0.8f, fallbackMs = 11L)
    }

    /** The fold effect engaging — a crisp "catch," like a hinge closing. */
    fun engage(strength: Float) = fire(VibrationEffect.Composition.PRIMITIVE_QUICK_RISE, strength, fallbackMs = 20L)

    /** The fold effect releasing back to neutral — a softer settle. */
    fun release(strength: Float) = fire(VibrationEffect.Composition.PRIMITIVE_THUD, strength, fallbackMs = 15L)

    /** The safety auto-release firing because a gesture was held too long — deliberately distinct from [release]. */
    fun timeoutAlert(strength: Float) {
        val vibrator = vibrator ?: return
        val scale = strength.coerceIn(0.1f, 1f)
        if (supportsPrimitives) {
            val composition = VibrationEffect.startComposition()
            repeat(3) { composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, scale) }
            vibrator.vibrate(composition.compose())
        } else {
            val amplitude = scaleAmplitude(strength)
            vibrator.vibrate(
                VibrationEffect.createWaveform(
                    longArrayOf(0, 12, 40, 12, 40, 12),
                    intArrayOf(0, amplitude, 0, amplitude, 0, amplitude),
                    -1
                )
            )
        }
    }

    private fun fire(primitive: Int, strength: Float, fallbackMs: Long) {
        val vibrator = vibrator ?: return
        if (supportsPrimitives) {
            vibrator.vibrate(
                VibrationEffect.startComposition()
                    .addPrimitive(primitive, strength.coerceIn(0.1f, 1f))
                    .compose()
            )
        } else {
            vibrator.vibrate(VibrationEffect.createOneShot(fallbackMs, scaleAmplitude(strength)))
        }
    }

    private fun scaleAmplitude(strength: Float): Int =
        (strength.coerceIn(0.05f, 1f) * 255).toInt().coerceIn(1, 255)
}
