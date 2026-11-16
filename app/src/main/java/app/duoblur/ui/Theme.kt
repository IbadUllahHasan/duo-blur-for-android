package app.duoblur.ui

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import app.duoblur.HapticsController

/**
 * Dark palette, remapped to Apple's iOS system-color values. `systemBlue` is
 * live-verified (fetched a real iOS design-guideline dataset); the rest are
 * extremely stable, independently well-known standard values unchanged since
 * iOS 13, not each individually re-fetched here — the best third-party
 * hex-reference sites were blocked by this environment's network policy and
 * Apple's own docs are JS-rendered with no literal hex reachable.
 * `background`/`onBackground` are pure black/white, matching Apple's `label`
 * and unified with GlassPalette.backgroundBase rather
 * than adding a third near-but-not-quite-matching near-black to the app.
 * `onSurfaceVariant`/`outlineVariant` use Apple's real alpha-based label
 * hierarchy (a base colour + alpha, not a distinct hue) rather than a flat
 * hex. `primaryContainer`/`onPrimaryContainer`/`surfaceVariant` have no Apple
 * equivalent and aren't read anywhere in this app today (confirmed by grep) —
 * reasonable Material3-convention values, low risk either way.
 */
internal val DuoBlurDarkColors = darkColorScheme(
    primary = Color(0xFF0A84FF),              // systemBlue dark
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF003C6B),
    onPrimaryContainer = Color(0xFFCDE5FF),
    secondary = Color(0xFF5E5CE6),            // systemIndigo dark
    tertiary = Color(0xFF64D2FF),             // systemTeal dark
    background = Color(0xFF05060A),           // DarkGlassPalette.backgroundBase — the mesh's opaque base
    onBackground = Color(0xFFFFFFFF),         // Apple `label` dark
    surface = Color(0xFF1C1C1E),              // Apple secondarySystemBackground dark
    onSurface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFF2C2C2E),
    onSurfaceVariant = Color(0xFFEBEBF5).copy(alpha = 0.78f), // Apple secondaryLabel dark, alpha raised — see the light scheme's note
    surfaceContainer = Color(0xFF2C2C2E),     // Apple tertiarySystemBackground dark
    surfaceContainerHigh = Color(0xFF3A3A3C),
    outline = Color(0xFF38383A),              // Apple opaqueSeparator dark
    outlineVariant = Color(0xFF545458).copy(alpha = 0.65f) // Apple separator dark
)

/** Light counterpart, same Apple remap — see [DuoBlurDarkColors]'s doc comment for sourcing. */
internal val DuoBlurLightColors = lightColorScheme(
    primary = Color(0xFF007AFF),              // systemBlue light — live-verified
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD6E8FF),
    onPrimaryContainer = Color(0xFF00305A),
    secondary = Color(0xFF5856D6),            // systemIndigo light
    tertiary = Color(0xFF5AC8FA),             // systemTeal light
    background = Color(0xFFFBF8F3),           // LightGlassPalette.backgroundBase — the mesh's warm off-white base
    onBackground = Color(0xFF000000),         // Apple `label` light
    surface = Color(0xFFF2F2F7),              // Apple secondarySystemBackground light
    onSurface = Color(0xFF000000),
    surfaceVariant = Color(0xFFE5E5EA),
    // Apple's real secondaryLabel is #3C3C43 @0.6, and that is what was here.
    // It is calibrated for opaque iOS backgrounds; over a *translucent* glass
    // card the effective contrast drops again, which is what made light-mode
    // body text "barely readable". Raised to 0.85 rather than kept nominally
    // Apple-accurate, and the two call sites that used to multiply this by a
    // further 0.8 no longer do.
    onSurfaceVariant = Color(0xFF3C3C43).copy(alpha = 0.85f),
    surfaceContainer = Color(0xFFF2F2F7),     // Apple's tertiarySystemBackground light is #FFFFFF, same as background — reuses secondarySystemBackground instead
    surfaceContainerHigh = Color(0xFFE5E5EA),
    outline = Color(0xFFC6C6C8),              // Apple opaqueSeparator light
    outlineVariant = Color(0xFF3C3C43).copy(alpha = 0.29f) // Apple separator light
)

/** Resolved once in MainActivity.onCreate and handed down so any control can give a light tap without threading a parameter through every call site. Null only before composition ever runs. */
internal val LocalHaptics = compositionLocalOf<HapticsController?> { null }

/** Mirrors Tunables.uiHapticsEnabled so controls can skip the tap without every caller checking it themselves. */
internal val LocalUiHapticsEnabled = compositionLocalOf { true }

/**
 * Feedback for switch flips, routed through the platform instead of our own
 * Vibrator.
 *
 * HapticFeedbackConstants.TOGGLE_ON / TOGGLE_OFF (API 34) is what the OS maps
 * onto the device's dedicated toggle haptic — on a Samsung that is their
 * vibration HAL's own tuned toggle waveform, which is why it feels like the
 * rest of the system and a raw VibrationEffect primitive does not. Compose's
 * LocalHapticFeedback can't reach these (it only exposes LongPress and
 * TextHandleMove), so this goes through the host View directly.
 *
 * performHapticFeedback returning false means the system or the user has touch
 * feedback switched off, so there is deliberately no fallback in that case —
 * only pre-34 devices, which have no such constant at all, drop back to
 * HapticsController.
 *
 * Module-visible so GlassSwitch in Glass.kt can fire this directly.
 * LocalHaptics and LocalUiHapticsEnabled are module-visible too: the tuning
 * controls read them from other files, and this function still closes over
 * them here.
 */
@Composable
internal fun rememberToggleHaptic(): (Boolean) -> Unit {
    val view = LocalView.current
    val haptics = LocalHaptics.current
    val uiHapticsEnabled = LocalUiHapticsEnabled.current
    return { on ->
        if (uiHapticsEnabled) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                view.performHapticFeedback(
                    if (on) HapticFeedbackConstants.TOGGLE_ON else HapticFeedbackConstants.TOGGLE_OFF
                )
            } else {
                haptics?.toggle(on)
            }
        }
    }
}
