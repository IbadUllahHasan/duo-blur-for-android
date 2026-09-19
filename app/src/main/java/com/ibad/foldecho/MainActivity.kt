package com.ibad.foldecho

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze
import kotlin.math.roundToInt

/**
 * Control panel for the system-wide effect: turn it on, see what the sensor is
 * actually reading, and tune the parameters the spec said could only be
 * settled on real hardware — live, while the service is running.
 */
class MainActivity : ComponentActivity() {
    private lateinit var projectionManager: MediaProjectionManager
    private lateinit var haptics: HapticsController
    private var tunables by mutableStateOf(Tunables())
    private var canDrawOverlays by mutableStateOf(false)

    private val requestCapture =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val data = result.data
            if (result.resultCode == Activity.RESULT_OK && data != null) {
                ContextCompat.startForegroundService(
                    this,
                    Intent(this, FoldEchoService::class.java).apply {
                        putExtra(FoldEchoService.EXTRA_RESULT_CODE, result.resultCode)
                        putExtra(FoldEchoService.EXTRA_RESULT_DATA, data)
                    }
                )
            }
        }

    private val requestNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        projectionManager = getSystemService(MediaProjectionManager::class.java)
        haptics = HapticsController(this)
        tunables = FoldEchoSettings.load(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            val darkTheme = isSystemInDarkTheme()
            MaterialTheme(colorScheme = if (darkTheme) FoldEchoDarkColors else FoldEchoLightColors) {
                val running by FoldEchoState.running.collectAsState()
                val effectActive by FoldEchoState.effectActive.collectAsState()
                val deviation by FoldEchoState.deviationDeg.collectAsState()
                val hazeState = remember { HazeState() }

                CompositionLocalProvider(
                    LocalHaptics provides haptics,
                    LocalUiHapticsEnabled provides tunables.uiHapticsEnabled,
                    LocalGlassPalette provides if (darkTheme) DarkGlassPalette else LightGlassPalette,
                    LocalHazeState provides hazeState
                ) {
                    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        Box(Modifier.fillMaxSize()) {
                            AmbientBackground(Modifier.fillMaxSize().haze(state = hazeState))
                            ControlPanel(
                                running = running,
                                effectActive = effectActive,
                                deviationDeg = deviation,
                                tunables = tunables,
                                canDrawOverlays = canDrawOverlays,
                                onToggle = ::toggleService,
                                onRecalibrate = ::recalibrate,
                                onGrantOverlay = ::requestOverlayPermission,
                                onTunablesChange = ::updateTunables,
                                onResetTunables = ::resetTunables
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        canDrawOverlays = Settings.canDrawOverlays(this)
    }

    private fun toggleService() {
        if (FoldEchoState.running.value) {
            startService(Intent(this, FoldEchoService::class.java).setAction(FoldEchoService.ACTION_STOP))
            return
        }
        if (!Settings.canDrawOverlays(this)) {
            requestOverlayPermission()
            return
        }
        requestCapture.launch(projectionManager.createScreenCaptureIntent())
    }

    private fun recalibrate() {
        startService(Intent(this, FoldEchoService::class.java).setAction(FoldEchoService.ACTION_RECALIBRATE))
    }

    private fun requestOverlayPermission() {
        startActivity(
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
        )
    }

    private fun updateTunables(updated: Tunables) {
        tunables = updated
        FoldEchoSettings.save(this, updated)
    }

    private fun resetTunables() {
        tunables = FoldEchoSettings.reset(this)
    }
}

/** Dark palette — surfaces step up in tone (background < surface < surfaceContainer). Glass cards use their own translucent tint on top of this via GlassPalette, not these surface colors directly. */
private val FoldEchoDarkColors = darkColorScheme(
    primary = Color(0xFF9ED6FF),
    onPrimary = Color(0xFF00344E),
    primaryContainer = Color(0xFF00496D),
    onPrimaryContainer = Color(0xFFCDE5FF),
    secondary = Color(0xFFBAC8D8),
    tertiary = Color(0xFFD3BFE0),
    background = Color(0xFF0E0F13),
    onBackground = Color(0xFFE4E2E6),
    surface = Color(0xFF131318),
    onSurface = Color(0xFFE4E2E6),
    surfaceVariant = Color(0xFF42474E),
    onSurfaceVariant = Color(0xFFC2C7CE),
    surfaceContainer = Color(0xFF1B1C22),
    surfaceContainerHigh = Color(0xFF23252C),
    outline = Color(0xFF8C9199),
    outlineVariant = Color(0xFF42474E)
)

/** Light counterpart — not just an inverted dark theme; tuned separately so glass tint/specular actually read against a bright background instead of washing out. */
private val FoldEchoLightColors = lightColorScheme(
    primary = Color(0xFF00618A),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFC4E7FF),
    onPrimaryContainer = Color(0xFF001E2C),
    secondary = Color(0xFF4C6172),
    tertiary = Color(0xFF5F5470),
    background = Color(0xFFF6F8FA),
    onBackground = Color(0xFF1A1C1E),
    surface = Color(0xFFFAFCFE),
    onSurface = Color(0xFF1A1C1E),
    surfaceVariant = Color(0xFFDCE3E9),
    onSurfaceVariant = Color(0xFF41484D),
    surfaceContainer = Color(0xFFEFF2F5),
    surfaceContainerHigh = Color(0xFFE9ECEF),
    outline = Color(0xFF72787E),
    outlineVariant = Color(0xFFC1C7CD)
)

/** Resolved once in MainActivity.onCreate and handed down so any control can give a light tap without threading a parameter through every call site. Null only before composition ever runs. */
private val LocalHaptics = compositionLocalOf<HapticsController?> { null }

/** Mirrors Tunables.uiHapticsEnabled so controls can skip the tap without every caller checking it themselves. */
private val LocalUiHapticsEnabled = compositionLocalOf { true }

private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t.coerceIn(0f, 1f)
private fun inverseLerp(a: Float, b: Float, v: Float) = ((v - a) / (b - a)).coerceIn(0f, 1f)

// Each pair of functions below is a "simple" perceptual dial: [*T] reads the
// dial's 0..1 position back out of the real, already-persisted parameters
// (so Advanced always stays the source of truth), and [apply*] writes a
// dragged dial position back into those same parameters. No new state is
// stored for the dial itself — it's purely a friendlier way to move values
// that already existed.

private fun sensitivityT(t: Tunables) = inverseLerp(22f, 6f, t.activateDeg)
private fun applySensitivity(t: Tunables, dial: Float) = t.copy(
    activateDeg = lerp(22f, 6f, dial),
    fullTiltDeg = lerp(48f, 18f, dial)
)

private fun blurT(t: Tunables) = inverseLerp(15f, 65f, t.maxBlurPx)
private fun applyBlur(t: Tunables, dial: Float): Tunables {
    val updated = t.copy(maxBlurPx = lerp(15f, 65f, dial))
    return if (updated.foldShaderEnabled) {
        updated.copy(
            maxBlurRadiusPx = lerp(20f, 80f, dial),
            blurPerMm = lerp(0.8f, 3.2f, dial)
        )
    } else {
        updated
    }
}

private fun shadowT(t: Tunables) = inverseLerp(0.25f, 0.75f, t.maxDim)
private fun applyShadow(t: Tunables, dial: Float): Tunables {
    val updated = t.copy(maxDim = lerp(0.25f, 0.75f, dial))
    return if (updated.foldShaderEnabled) {
        updated.copy(
            maxDarken = lerp(0.35f, 0.95f, dial),
            darkenPerMm = lerp(0.008f, 0.045f, dial)
        )
    } else {
        updated
    }
}

private fun depthT(t: Tunables) = if (t.foldShaderEnabled) {
    inverseLerp(450f, 150f, t.viewDistanceMm)
} else {
    inverseLerp(0.15f, 0.75f, t.perspectiveStrength)
}

private fun applyDepth(t: Tunables, dial: Float): Tunables = if (t.foldShaderEnabled) {
    t.copy(viewDistanceMm = lerp(450f, 150f, dial))
} else {
    t.copy(
        perspectiveStrength = lerp(0.15f, 0.75f, dial),
        maxShrink = lerp(0.04f, 0.22f, dial)
    )
}

private fun hapticStrengthT(t: Tunables) = t.blurHapticsEngageStrength
private fun applyHapticStrength(t: Tunables, dial: Float) = t.copy(
    blurHapticsEngageStrength = dial,
    blurHapticsReleaseStrength = dial,
    blurHapticsTimeoutStrength = dial
)

@Composable
private fun ControlPanel(
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
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp)
    ) {
        DuoFlowWordmark()
        Text(
            "Tilt-driven Duo effect, system-wide",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

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
                    color = Color(0xFFFFD79A)
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
            ) { onTunablesChange(applyBlur(tunables, it)) }

            AdvancedSection {
                TuningSlider(
                    label = "Blur",
                    readout = "${tunables.maxBlurPx.roundToInt()}px",
                    value = tunables.maxBlurPx,
                    range = 0f..80f,
                    help = "Peak blur radius at full tilt, graded from none at the hinge edge to full strength at the far edge.",
                    enabled = tunables.maxBlurPxEnabled,
                    onEnabledChange = { onTunablesChange(tunables.copy(maxBlurPxEnabled = it)) }
                ) { onTunablesChange(tunables.copy(maxBlurPx = it)) }

                if (tunables.foldShaderEnabled) {
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
            ) { onTunablesChange(applyShadow(tunables, it)) }

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

                if (tunables.foldShaderEnabled) {
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
                readout = "${(depthT(tunables) * 100).roundToInt()}%",
                value = depthT(tunables),
                range = 0f..1f,
                help = "How much 3D depth the tilt reveals — flat and subtle, or a steep, dramatic recede."
            ) { onTunablesChange(applyDepth(tunables, it)) }

            AdvancedSection {
                if (tunables.foldShaderEnabled) {
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
                help = "Light taps when you drag sliders or flip switches in this app. Doesn't affect the fold effect itself.",
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
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
        )
        Spacer(Modifier.height(24.dp))
    }
}

/** The glass shell every tuning section shares — real backdrop blur of the ambient background via Haze, not a flat translucent color. */
@Composable
private fun TuningGroup(content: @Composable () -> Unit) {
    GlassSurface(shape = RoundedCornerShape(GlassRadii.card)) {
        Column(Modifier.padding(16.dp)) { content() }
    }
}

/**
 * A collapsed-by-default section for the raw parameters behind a card's
 * simple dial (or, with a custom [title], a whole card's only content, e.g.
 * "Finishing touches"). Spring-based expand/collapse via AnimatedVisibility.
 */
@Composable
private fun AdvancedSection(title: String = "Advanced", content: @Composable () -> Unit) {
    val haptics = LocalHaptics.current
    val uiHapticsEnabled = LocalUiHapticsEnabled.current
    var expanded by rememberSaveable { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val pressScale = rememberPressScale(interactionSource)
    Column {
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .scale(pressScale)
                .clip(RoundedCornerShape(GlassRadii.control))
                .clickable(interactionSource = interactionSource, indication = null) {
                    if (uiHapticsEnabled) haptics?.interfaceTick()
                    expanded = !expanded
                }
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                if (expanded) "▲" else "▼",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(spring(stiffness = Spring.StiffnessMedium)) +
                expandVertically(animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow)),
            exit = fadeOut(spring(stiffness = Spring.StiffnessMedium)) +
                shrinkVertically(animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium))
        ) {
            Column {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                Spacer(Modifier.height(4.dp))
                content()
            }
        }
    }
}

/** A floating "i"/"?" dot that opens a real Material3 tooltip popover on long-press, instead of a permanent subtitle. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InfoTooltip(text: String, glyph: String = "i") {
    val tooltipState = rememberTooltipState()
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(text) } },
        state = tooltipState
    ) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center
        ) {
            Text(glyph, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * "Duo" solid, then "Flow" typed out letter by letter with a small blur
 * radius growing across it — the same "none at the near edge, full strength
 * at the far edge" grading the app's own Blur slider applies to the frame,
 * just borrowed for the wordmark instead of a captured screenshot.
 */
@Composable
private fun DuoFlowWordmark() {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            "Duo",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground
        )
        "Flow".forEachIndexed { index, letter ->
            Text(
                letter.toString(),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.blur((index * 0.6f).dp)
            )
        }
    }
}

@Composable
private fun SectionHeader(label: String) {
    Text(
        label,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

/** A floating capsule with a spring-animated sliding indicator, instead of a full-width flat segmented row. */
@Composable
private fun ModeSelector(usingFold: Boolean, onSelect: (Boolean) -> Unit) {
    GlassSurface(shape = CircleShape, modifier = Modifier.fillMaxWidth()) {
        ModeSelectorContent(usingFold, onSelect)
    }
}

@Composable
private fun ModeSelectorContent(usingFold: Boolean, onSelect: (Boolean) -> Unit) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth().padding(4.dp)) {
        val optionWidth = maxWidth / 2
        val indicatorOffset by animateDpAsState(
            targetValue = if (usingFold) 0.dp else optionWidth,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
            label = "pillIndicator"
        )
        Box(
            Modifier
                .offset(x = indicatorOffset)
                .width(optionWidth)
                .fillMaxHeight()
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
        )
        Row(Modifier.fillMaxWidth()) {
            ModeOption(
                label = "Ray-traced fold",
                selected = usingFold,
                modifier = Modifier.weight(1f)
            ) { onSelect(true) }
            ModeOption(
                label = "Classic (lean/scale)",
                selected = !usingFold,
                modifier = Modifier.weight(1f)
            ) { onSelect(false) }
        }
    }
}

@Composable
private fun ModeOption(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val haptics = LocalHaptics.current
    val uiHapticsEnabled = LocalUiHapticsEnabled.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressScale = rememberPressScale(interactionSource)
    val foreground = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = modifier
            .scale(pressScale)
            .clip(CircleShape)
            .clickable(interactionSource = interactionSource, indication = null) {
                if (uiHapticsEnabled) haptics?.interfaceTick()
                onClick()
            }
            .padding(vertical = 11.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = foreground, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun StatusCard(
    running: Boolean,
    effectActive: Boolean,
    deviationDeg: Float,
    tunables: Tunables,
    canDrawOverlays: Boolean,
    onToggle: () -> Unit,
    onRecalibrate: () -> Unit,
    onGrantOverlay: () -> Unit
) {
    val haptics = LocalHaptics.current
    val uiHapticsEnabled = LocalUiHapticsEnabled.current
    val (label, dot) = when {
        effectActive -> "Effect active" to MaterialTheme.colorScheme.primary
        running -> "Watching for tilt" to Color(0xFF4ADE80)
        else -> "Off" to MaterialTheme.colorScheme.outline
    }

    GlassSurface(shape = RoundedCornerShape(GlassRadii.card)) {
        Column(Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(dot))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        label,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Medium
                    )
                }
                Switch(
                    checked = running,
                    onCheckedChange = {
                        if (uiHapticsEnabled) haptics?.interfaceTick()
                        onToggle()
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                        checkedTrackColor = MaterialTheme.colorScheme.primary
                    )
                )
            }

            if (!canDrawOverlays) {
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "\"Display over other apps\" is off — the effect can't draw without it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFFFD79A),
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = onGrantOverlay) { Text("Grant") }
                }
            }

            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "Tilt from neutral",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    if (running) "${"%.1f".format(deviationDeg)}°" else "—",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = (deviationDeg / tunables.fullTiltDeg).coerceIn(0f, 1f),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = dot,
                trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            )
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Triggers past ${tunables.activateDeg.roundToInt()}°",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
                TextButton(onClick = onRecalibrate, enabled = running) { Text("Recalibrate") }
            }
        }
    }
}

/**
 * A tuning row: label, live readout, and — for every slider except Activation
 * threshold and Full-tilt point, which always apply — a compact switch that
 * turns this one parameter off without discarding its dialed-in value.
 * [enabled] defaults to true and [onEnabledChange] to null so those two
 * sliders can call this without opting into a switch at all.
 */
@Composable
private fun TuningSlider(
    label: String,
    readout: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    help: String,
    enabled: Boolean = true,
    onEnabledChange: ((Boolean) -> Unit)? = null,
    onChange: (Float) -> Unit
) {
    val haptics = LocalHaptics.current
    val uiHapticsEnabled = LocalUiHapticsEnabled.current
    val contentAlpha = if (enabled) 1f else 0.4f
    Column(Modifier.padding(vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha)
                )
                Spacer(Modifier.width(6.dp))
                InfoTooltip(help)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    readout,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = contentAlpha)
                )
                if (onEnabledChange != null) {
                    Spacer(Modifier.width(8.dp))
                    Switch(
                        checked = enabled,
                        onCheckedChange = {
                            if (uiHapticsEnabled) haptics?.interfaceTick()
                            onEnabledChange(it)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                            checkedTrackColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }
            }
        }
        Slider(
            value = value,
            onValueChange = onChange,
            onValueChangeFinished = { if (uiHapticsEnabled) haptics?.interfaceTick() },
            valueRange = range,
            enabled = enabled,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary
            )
        )
    }
}

@Composable
private fun TuningToggle(
    label: String,
    help: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    val haptics = LocalHaptics.current
    val uiHapticsEnabled = LocalUiHapticsEnabled.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.width(6.dp))
            InfoTooltip(help)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = {
                if (uiHapticsEnabled) haptics?.interfaceTick()
                onChange(it)
            },
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.primary
            )
        )
    }
}
