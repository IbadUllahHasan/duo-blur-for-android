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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import kotlin.math.roundToInt

/**
 * Control panel for the system-wide effect: turn it on, see what the sensor is
 * actually reading, and tune the parameters the spec said could only be
 * settled on real hardware — live, while the service is running.
 */
class MainActivity : ComponentActivity() {
    private lateinit var projectionManager: MediaProjectionManager
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
        tunables = FoldEchoSettings.load(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            MaterialTheme(colorScheme = FoldEchoColors) {
                val running by FoldEchoState.running.collectAsState()
                val effectActive by FoldEchoState.effectActive.collectAsState()
                val deviation by FoldEchoState.deviationDeg.collectAsState()

                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
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

/** A dark, tonal Material You-style palette — surfaces step up in tone (background < surface < surfaceContainer) instead of the flat single-surface-color scheme this used to be. */
private val FoldEchoColors = darkColorScheme(
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
        StatusCard(running, effectActive, deviationDeg, tunables)

        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = onToggle,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(if (running) "Disable" else "Enable")
            }
            Spacer(Modifier.width(12.dp))
            OutlinedButton(
                onClick = onRecalibrate,
                enabled = running,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("Recalibrate")
            }
        }

        if (!canDrawOverlays) {
            Spacer(Modifier.height(12.dp))
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF3B2A12))
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text(
                        "\"Display over other apps\" is off — the effect can't draw without it.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFFFD79A)
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = onGrantOverlay, shape = RoundedCornerShape(12.dp)) {
                        Text("Grant permission")
                    }
                }
            }
        }

        Spacer(Modifier.height(28.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Tuning",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground
            )
            TextButton(onClick = onResetTunables) { Text("Reset to defaults") }
        }
        Text(
            "Adjustments apply immediately, even while the effect is running. Each slider's " +
                "switch turns that parameter off without losing its value.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
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
            Spacer(Modifier.height(6.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

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

        Spacer(Modifier.height(16.dp))
        if (tunables.foldShaderEnabled) {
            TuningGroup {
                SectionHeader("Ray-traced fold")

                TuningSlider(
                    label = "View distance",
                    readout = "${tunables.viewDistanceMm.roundToInt()}mm",
                    value = tunables.viewDistanceMm,
                    range = 100f..600f,
                    help = "How far the eye sits from the content plane. Closer is a more extreme perspective.",
                    enabled = tunables.viewDistanceEnabled,
                    onEnabledChange = { onTunablesChange(tunables.copy(viewDistanceEnabled = it)) }
                ) { onTunablesChange(tunables.copy(viewDistanceMm = it)) }

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
        } else {
            TuningGroup {
                SectionHeader("Classic (lean/scale)")

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

        Spacer(Modifier.height(16.dp))
        TuningGroup {
            SectionHeader("Shared")

            TuningSlider(
                label = "Blur",
                readout = "${tunables.maxBlurPx.roundToInt()}px",
                value = tunables.maxBlurPx,
                range = 0f..80f,
                help = "Peak blur radius at full tilt, graded from none at the hinge edge to full strength at the far edge.",
                enabled = tunables.maxBlurPxEnabled,
                onEnabledChange = { onTunablesChange(tunables.copy(maxBlurPxEnabled = it)) }
            ) { onTunablesChange(tunables.copy(maxBlurPx = it)) }

            TuningSlider(
                label = "Dim",
                readout = "${(tunables.maxDim * 100).roundToInt()}%",
                value = tunables.maxDim,
                range = 0f..0.9f,
                help = "How dark the frame goes at full tilt.",
                enabled = tunables.maxDimEnabled,
                onEnabledChange = { onTunablesChange(tunables.copy(maxDimEnabled = it)) }
            ) { onTunablesChange(tunables.copy(maxDim = it)) }

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

            Spacer(Modifier.height(4.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            Spacer(Modifier.height(4.dp))
            TuningToggle(
                label = "Flip tilt direction",
                help = "Turn this on if the frame ever leans the opposite way from how you tilt the phone.",
                checked = tunables.flipTiltDirection
            ) { onTunablesChange(tunables.copy(flipTiltDirection = it)) }
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

/** The tonal card shell every tuning section shares — one consistent rounded surface instead of bare Columns with a text label above them. */
@Composable
private fun TuningGroup(content: @Composable () -> Unit) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(Modifier.padding(16.dp)) { content() }
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

@Composable
private fun ModeSelector(usingFold: Boolean, onSelect: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(4.dp)
    ) {
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

@Composable
private fun ModeOption(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val background = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
    val foreground = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(background)
            .clickable(onClick = onClick)
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
    tunables: Tunables
) {
    val (label, dot) = when {
        effectActive -> "Effect active" to MaterialTheme.colorScheme.primary
        running -> "Watching for tilt" to Color(0xFF4ADE80)
        else -> "Off" to MaterialTheme.colorScheme.outline
    }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(Modifier.padding(18.dp)) {
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
            Text(
                "Triggers past ${tunables.activateDeg.roundToInt()}°",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            )
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
    val contentAlpha = if (enabled) 1f else 0.4f
    Column(Modifier.padding(vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha)
            )
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
                        onCheckedChange = onEnabledChange,
                        modifier = Modifier.scale(0.75f),
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
            valueRange = range,
            enabled = enabled,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary
            )
        )
        Text(
            help,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha * 0.85f)
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(help, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.primary
            )
        )
    }
}
