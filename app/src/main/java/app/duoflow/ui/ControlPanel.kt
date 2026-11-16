package app.duoflow.ui

import android.os.Build
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.duoflow.Tunables
import kotlin.math.roundToInt

@Composable
internal fun ControlPanel(
    scrollState: ScrollState,
    running: Boolean,
    effectActive: Boolean,
    deviationDeg: Float,
    tunables: Tunables,
    canDrawOverlays: Boolean,
    onToggle: () -> Unit,
    onRecalibrate: () -> Unit,
    onGrantOverlay: () -> Unit,
    onTunablesChange: (Tunables) -> Unit,
    onResetTunables: () -> Unit
) {
    // Ray-traced fold is AGSL, which is API 33. On 31/32 OverlayController
    // silently runs the classic renderer no matter what this preference says,
    // so the panel has to show *classic's* tunables there — otherwise the card
    // offers sliders that cannot reach the renderer actually drawing.
    val rayTraced = tunables.foldShaderEnabled &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    Column(
        modifier = Modifier
            .fillMaxSize()
            // Ahead of .verticalScroll, so the inset shrinks the scroll
            // *viewport* rather than becoming scrollable padding: the wordmark
            // starts below the status bar/notch and the last card clears the
            // gesture pill, at every scroll position. safeDrawing rather than
            // systemBars because systemBars alone excludes the display cutout,
            // and the spec names the notch explicitly. Only MeshGradientBackground,
            // which is a sibling outside this Column, extends behind the bars.
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                DuoFlowWordmark()
                Text(
                    "Tilt-driven Duo effect, system-wide",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                GlassEffectsToggleButton(
                    enabled = tunables.glassEffectsEnabled,
                    onToggle = { onTunablesChange(tunables.copy(glassEffectsEnabled = it)) }
                )
                ThemeToggleButton(
                    mode = tunables.themeMode,
                    onModeChange = { onTunablesChange(tunables.copy(themeMode = it)) }
                )
            }
        }

        Spacer(Modifier.height(20.dp))
        StatusCard(
            running = running,
            effectActive = effectActive,
            deviationDeg = deviationDeg,
            tunables = tunables,
            canDrawOverlays = canDrawOverlays,
            onToggle = onToggle,
            onRecalibrate = onRecalibrate,
            onGrantOverlay = onGrantOverlay
        )

        Spacer(Modifier.height(28.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Tuning",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(Modifier.width(6.dp))
                InfoTooltip(
                    text = "Adjustments apply immediately, even while the effect is running. " +
                        "Each slider's switch turns that parameter off without losing its value.",
                    glyph = "?"
                )
            }
            TextButton(onClick = onResetTunables) { Text("Reset to defaults") }
        }
        Spacer(Modifier.height(14.dp))

        TuningGroup {
            ModeSelector(
                usingFold = tunables.foldShaderEnabled,
                onSelect = { onTunablesChange(tunables.copy(foldShaderEnabled = it)) }
            )
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "This device is on Android 12 — ray-traced fold needs 13 or newer, " +
                        "so the classic renderer runs regardless of which is selected here.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFFF9500) // Apple systemOrange
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        TuningGroup {
            TuningSlider(
                label = "Sensitivity",
                readout = "${(sensitivityT(tunables) * 100).roundToInt()}%",
                value = sensitivityT(tunables),
                range = 0f..1f,
                help = "How easily the effect triggers — deliberate (needs a firm tilt) to easy " +
                    "(fires at the slightest tilt). Moves the activation threshold and full-tilt " +
                    "point together."
            ) { onTunablesChange(applySensitivity(tunables, it)) }

            Spacer(Modifier.height(4.dp))
            TuningToggle(
                label = "Flip tilt direction",
                help = "Turn this on if the frame ever leans the opposite way from how you tilt the phone.",
                checked = tunables.flipTiltDirection
            ) { onTunablesChange(tunables.copy(flipTiltDirection = it)) }

            AdvancedSection {
                TuningSlider(
                    label = "Activation threshold",
                    readout = "${tunables.activateDeg.roundToInt()}°",
                    value = tunables.activateDeg,
                    range = 4f..30f,
                    help = "How far from neutral before the effect kicks in."
                ) { onTunablesChange(tunables.copy(activateDeg = it)) }

                TuningSlider(
                    label = "Full-tilt point",
                    readout = "${tunables.fullTiltDeg.roundToInt()}°",
                    value = tunables.fullTiltDeg,
                    range = 15f..60f,
                    help = "Deviation at which the effect reaches full strength."
                ) { onTunablesChange(tunables.copy(fullTiltDeg = it)) }
            }
        }

        Spacer(Modifier.height(16.dp))
        TuningGroup {
            TuningSlider(
                label = "Blur",
                readout = "${(blurT(tunables) * 100).roundToInt()}%",
                value = blurT(tunables),
                range = 0f..1f,
                help = "How out-of-focus the tilted content gets, from a light haze to a heavy " +
                    "frost. Moves every blur-related parameter together."
            ) { onTunablesChange(applyBlur(tunables, it, rayTraced)) }

            AdvancedSection {
                TuningSlider(
                    label = "Peak blur radius",
                    readout = "${tunables.maxBlurPx.roundToInt()}px",
                    value = tunables.maxBlurPx,
                    range = 0f..80f,
                    help = "Peak blur radius at full tilt, graded from none at the hinge edge to full strength at the far edge.",
                    enabled = tunables.maxBlurPxEnabled,
                    onEnabledChange = { onTunablesChange(tunables.copy(maxBlurPxEnabled = it)) }
                ) { onTunablesChange(tunables.copy(maxBlurPx = it)) }

                if (rayTraced) {
                    Spacer(Modifier.height(6.dp))
                    SectionHeader("Ray-traced fold")

                    TuningSlider(
                        label = "Blur per mm",
                        readout = "${"%.1f".format(tunables.blurPerMm)}px",
                        value = tunables.blurPerMm,
                        range = 0f..6f,
                        help = "Blur radius gained per mm of gap between the glass and the plane.",
                        enabled = tunables.blurPerMmEnabled,
                        onEnabledChange = { onTunablesChange(tunables.copy(blurPerMmEnabled = it)) }
                    ) { onTunablesChange(tunables.copy(blurPerMm = it)) }

                    TuningSlider(
                        label = "Max blur radius",
                        readout = "${tunables.maxBlurRadiusPx.roundToInt()}px",
                        value = tunables.maxBlurRadiusPx,
                        range = 0f..96f,
                        help = "Ceiling on that radius, so a steep tilt can't melt the whole frame.",
                        enabled = tunables.maxBlurRadiusEnabled,
                        onEnabledChange = { onTunablesChange(tunables.copy(maxBlurRadiusEnabled = it)) }
                    ) { onTunablesChange(tunables.copy(maxBlurRadiusPx = it)) }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        TuningGroup {
            TuningSlider(
                label = "Shadow",
                readout = "${(shadowT(tunables) * 100).roundToInt()}%",
                value = shadowT(tunables),
                range = 0f..1f,
                help = "How dark the tilted content gets, from barely dimmed to nearly black. " +
                    "Moves every darkening parameter together."
            ) { onTunablesChange(applyShadow(tunables, it, rayTraced)) }

            AdvancedSection {
                TuningSlider(
                    label = "Dim",
                    readout = "${(tunables.maxDim * 100).roundToInt()}%",
                    value = tunables.maxDim,
                    range = 0f..0.9f,
                    help = "How dark the frame goes at full tilt.",
                    enabled = tunables.maxDimEnabled,
                    onEnabledChange = { onTunablesChange(tunables.copy(maxDimEnabled = it)) }
                ) { onTunablesChange(tunables.copy(maxDim = it)) }

                if (rayTraced) {
                    Spacer(Modifier.height(6.dp))
                    SectionHeader("Ray-traced fold")

                    TuningSlider(
                        label = "Darken per mm",
                        readout = "${"%.1f".format(tunables.darkenPerMm * 100)}%",
                        value = tunables.darkenPerMm,
                        range = 0f..0.1f,
                        help = "Light lost per mm of that same gap — frosted glass absorbing as it scatters.",
                        enabled = tunables.darkenPerMmEnabled,
                        onEnabledChange = { onTunablesChange(tunables.copy(darkenPerMmEnabled = it)) }
                    ) { onTunablesChange(tunables.copy(darkenPerMm = it)) }

                    TuningSlider(
                        label = "Max darken",
                        readout = "${(tunables.maxDarken * 100).roundToInt()}%",
                        value = tunables.maxDarken,
                        range = 0f..1f,
                        help = "Ceiling on that loss, so the far edge keeps some detail before it goes black.",
                        enabled = tunables.maxDarkenEnabled,
                        onEnabledChange = { onTunablesChange(tunables.copy(maxDarkenEnabled = it)) }
                    ) { onTunablesChange(tunables.copy(maxDarken = it)) }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        TuningGroup {
            TuningSlider(
                label = "Depth",
                readout = "${(depthT(tunables, rayTraced) * 100).roundToInt()}%",
                value = depthT(tunables, rayTraced),
                range = 0f..1f,
                help = "How much 3D depth the tilt reveals — flat and subtle, or a steep, dramatic recede."
            ) { onTunablesChange(applyDepth(tunables, it, rayTraced)) }

            AdvancedSection {
                if (rayTraced) {
                    TuningSlider(
                        label = "View distance",
                        readout = "${tunables.viewDistanceMm.roundToInt()}mm",
                        value = tunables.viewDistanceMm,
                        range = 100f..600f,
                        help = "How far the eye sits from the content plane. Closer is a more extreme perspective.",
                        enabled = tunables.viewDistanceEnabled,
                        onEnabledChange = { onTunablesChange(tunables.copy(viewDistanceEnabled = it)) }
                    ) { onTunablesChange(tunables.copy(viewDistanceMm = it)) }
                } else {
                    TuningSlider(
                        label = "Perspective",
                        readout = "${(tunables.perspectiveStrength * 100).roundToInt()}%",
                        value = tunables.perspectiveStrength,
                        range = 0f..1f,
                        help = "How much depth the tilt reveals (camera distance), not how far the frame leans.",
                        enabled = tunables.perspectiveEnabled,
                        onEnabledChange = { onTunablesChange(tunables.copy(perspectiveEnabled = it)) }
                    ) { onTunablesChange(tunables.copy(perspectiveStrength = it)) }

                    TuningSlider(
                        label = "Recede",
                        readout = "${(tunables.maxShrink * 100).roundToInt()}%",
                        value = tunables.maxShrink,
                        range = 0f..0.3f,
                        help = "How much the frame shrinks at full tilt, paired with the lean — " +
                            "sells a plane receding into distance rather than a flat zoom.",
                        enabled = tunables.maxShrinkEnabled,
                        onEnabledChange = { onTunablesChange(tunables.copy(maxShrinkEnabled = it)) }
                    ) { onTunablesChange(tunables.copy(maxShrink = it)) }

                    TuningSlider(
                        label = "Motion Softness",
                        readout = "${(tunables.motionSoftness * 100).roundToInt()}%",
                        value = tunables.motionSoftness,
                        range = 0f..1f,
                        help = "Spring damping on the lean/recede motion. Low is a light settle; high overshoots and bounces.",
                        enabled = tunables.motionSoftnessEnabled,
                        onEnabledChange = { onTunablesChange(tunables.copy(motionSoftnessEnabled = it)) }
                    ) { onTunablesChange(tunables.copy(motionSoftness = it)) }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        TuningGroup {
            AdvancedSection(title = "Finishing touches") {
                TuningSlider(
                    label = "Edge Fade",
                    readout = "${(tunables.edgeFadeStrength * 100).roundToInt()}%",
                    value = tunables.edgeFadeStrength,
                    range = 0f..1f,
                    help = "Alpha gradient from opaque center to transparent edge, strength scaling with tilt.",
                    enabled = tunables.edgeFadeEnabled,
                    onEnabledChange = { onTunablesChange(tunables.copy(edgeFadeEnabled = it)) }
                ) { onTunablesChange(tunables.copy(edgeFadeStrength = it)) }

                TuningSlider(
                    label = "Corner Radius",
                    readout = "${(tunables.cornerRadiusStrength * 100).roundToInt()}% · " +
                        "${(tunables.cornerRadiusStrength * tunables.cornerRadiusBaseDp).roundToInt()}dp",
                    value = tunables.cornerRadiusStrength,
                    range = 0f..1f,
                    help = "Fraction of this device's actual screen-corner radius " +
                        "(${tunables.cornerRadiusBaseDp.roundToInt()}dp detected). 100% matches the real corners.",
                    enabled = tunables.cornerRadiusEnabled,
                    onEnabledChange = { onTunablesChange(tunables.copy(cornerRadiusEnabled = it)) }
                ) { onTunablesChange(tunables.copy(cornerRadiusStrength = it)) }
            }
        }

        Spacer(Modifier.height(16.dp))
        TuningGroup {
            SectionHeader("Haptics")

            TuningToggle(
                label = "Interface haptics",
                help = "Taps when you drag sliders or flip switches in this app. Switches use the system's own toggle feedback. Doesn't affect the fold effect itself.",
                checked = tunables.uiHapticsEnabled
            ) { onTunablesChange(tunables.copy(uiHapticsEnabled = it)) }

            Spacer(Modifier.height(4.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            Spacer(Modifier.height(4.dp))

            TuningToggle(
                label = "Fold haptics",
                help = "A tactile cue when the fold effect engages, releases, or auto-resets after being held too long.",
                checked = tunables.blurHapticsEnabled
            ) { onTunablesChange(tunables.copy(blurHapticsEnabled = it)) }

            TuningSlider(
                label = "Haptic strength",
                readout = "${(hapticStrengthT(tunables) * 100).roundToInt()}%",
                value = hapticStrengthT(tunables),
                range = 0f..1f,
                help = "Overall intensity of the fold haptics, applied across engage, release, and the auto-reset alert together.",
                enabled = tunables.blurHapticsEnabled
            ) { onTunablesChange(applyHapticStrength(tunables, it)) }

            AdvancedSection {
                TuningSlider(
                    label = "Engage",
                    readout = "${(tunables.blurHapticsEngageStrength * 100).roundToInt()}%",
                    value = tunables.blurHapticsEngageStrength,
                    range = 0f..1f,
                    help = "Cue when the fold effect engages, as you tilt past the activation threshold.",
                    enabled = tunables.blurHapticsEnabled && tunables.blurHapticsEngageEnabled,
                    onEnabledChange = { onTunablesChange(tunables.copy(blurHapticsEngageEnabled = it)) }
                ) { onTunablesChange(tunables.copy(blurHapticsEngageStrength = it)) }

                TuningSlider(
                    label = "Release",
                    readout = "${(tunables.blurHapticsReleaseStrength * 100).roundToInt()}%",
                    value = tunables.blurHapticsReleaseStrength,
                    range = 0f..1f,
                    help = "Cue when the effect lets go, as you tilt back toward neutral.",
                    enabled = tunables.blurHapticsEnabled && tunables.blurHapticsReleaseEnabled,
                    onEnabledChange = { onTunablesChange(tunables.copy(blurHapticsReleaseEnabled = it)) }
                ) { onTunablesChange(tunables.copy(blurHapticsReleaseStrength = it)) }

                TuningSlider(
                    label = "Auto-release alert",
                    readout = "${(tunables.blurHapticsTimeoutStrength * 100).roundToInt()}%",
                    value = tunables.blurHapticsTimeoutStrength,
                    range = 0f..1f,
                    help = "A distinct cue when the safety timeout releases a fold that's been held too long.",
                    enabled = tunables.blurHapticsEnabled && tunables.blurHapticsTimeoutEnabled,
                    onEnabledChange = { onTunablesChange(tunables.copy(blurHapticsTimeoutEnabled = it)) }
                ) { onTunablesChange(tunables.copy(blurHapticsTimeoutStrength = it)) }
            }
        }

        Spacer(Modifier.height(24.dp))
        Text(
            "While enabled, the system's screen-recording indicator stays visible — " +
                "that's the cost of holding one capture session open instead of asking " +
                "for consent on every tilt. Apps that block screenshots (banking, " +
                "password managers, DRM video) will show black instead of their content.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(24.dp))
    }
}
