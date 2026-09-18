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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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

                Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF0B0B0F)) {
                    ControlPanel(
                        running = running,
                        effectActive = effectActive,
                        deviationDeg = deviation,
                        tunables = tunables,
                        canDrawOverlays = canDrawOverlays,
                        onToggle = ::toggleService,
                        onRecalibrate = ::recalibrate,
                        onGrantOverlay = ::requestOverlayPermission,
                        onTunablesChange = ::updateTunables
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
}

private val FoldEchoColors = darkColorScheme(
    primary = Color(0xFF7DD3FC),
    onPrimary = Color(0xFF06283D),
    surface = Color(0xFF15161D),
    onSurface = Color(0xFFE8E8EE)
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
    onTunablesChange: (Tunables) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Text("FoldEcho", fontSize = 28.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
        Text(
            "Tilt-driven Duo effect, system-wide",
            fontSize = 13.sp,
            color = Color.White.copy(alpha = 0.6f)
        )

        Spacer(Modifier.height(20.dp))
        StatusCard(running, effectActive, deviationDeg, tunables)

        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = onToggle, modifier = Modifier.weight(1f)) {
                Text(if (running) "Disable" else "Enable")
            }
            Spacer(Modifier.width(12.dp))
            OutlinedButton(onClick = onRecalibrate, enabled = running, modifier = Modifier.weight(1f)) {
                Text("Recalibrate")
            }
        }

        if (!canDrawOverlays) {
            Spacer(Modifier.height(12.dp))
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF3B2A12))) {
                Column(Modifier.padding(14.dp)) {
                    Text(
                        "\"Display over other apps\" is off — the effect can't draw without it.",
                        fontSize = 13.sp,
                        color = Color(0xFFFFD79A)
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = onGrantOverlay) { Text("Grant permission") }
                }
            }
        }

        Spacer(Modifier.height(28.dp))
        Text("Tuning", fontSize = 17.sp, fontWeight = FontWeight.Medium, color = Color.White)
        Text(
            "Adjustments apply immediately, even while the effect is running.",
            fontSize = 12.sp,
            color = Color.White.copy(alpha = 0.55f)
        )
        Spacer(Modifier.height(12.dp))

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

        TuningSlider(
            label = "Perspective",
            readout = "${(tunables.perspectiveStrength * 100).roundToInt()}%",
            value = tunables.perspectiveStrength,
            range = 0f..1f,
            help = "How much depth the tilt reveals (camera distance), not how far the frame leans."
        ) { onTunablesChange(tunables.copy(perspectiveStrength = it)) }

        TuningSlider(
            label = "Blur",
            readout = "${tunables.maxBlurPx.roundToInt()}px",
            value = tunables.maxBlurPx,
            range = 0f..80f,
            help = "Peak blur radius at full tilt."
        ) { onTunablesChange(tunables.copy(maxBlurPx = it)) }

        TuningSlider(
            label = "Dim",
            readout = "${(tunables.maxDim * 100).roundToInt()}%",
            value = tunables.maxDim,
            range = 0f..0.9f,
            help = "How dark the frame goes at full tilt."
        ) { onTunablesChange(tunables.copy(maxDim = it)) }

        Spacer(Modifier.height(8.dp))
        TuningToggle(
            label = "Flip tilt direction",
            help = "If the frame leans the wrong way for how you tilt the phone, " +
                "toggle this instead of editing code — the correct sign depends " +
                "on this device's sensor axis convention.",
            checked = tunables.flipTiltDirection
        ) { onTunablesChange(tunables.copy(flipTiltDirection = it)) }

        Spacer(Modifier.height(24.dp))
        Text(
            "While enabled, the system's screen-recording indicator stays visible — " +
                "that's the cost of holding one capture session open instead of asking " +
                "for consent on every tilt. Apps that block screenshots (banking, " +
                "password managers, DRM video) will show black instead of their content.",
            fontSize = 12.sp,
            color = Color.White.copy(alpha = 0.45f)
        )
        Spacer(Modifier.height(24.dp))
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
        effectActive -> "Effect active" to Color(0xFF7DD3FC)
        running -> "Watching for tilt" to Color(0xFF4ADE80)
        else -> "Off" to Color(0xFF6B7280)
    }

    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF15161D))) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(dot))
                Spacer(Modifier.width(10.dp))
                Text(label, fontSize = 16.sp, color = Color.White, fontWeight = FontWeight.Medium)
            }

            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Tilt from neutral", fontSize = 13.sp, color = Color.White.copy(alpha = 0.6f))
                Text(
                    if (running) "${"%.1f".format(deviationDeg)}°" else "—",
                    fontSize = 13.sp,
                    color = Color.White
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
                trackColor = Color.White.copy(alpha = 0.1f)
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Triggers past ${tunables.activateDeg.roundToInt()}°",
                fontSize = 11.sp,
                color = Color.White.copy(alpha = 0.4f)
            )
        }
    }
}

@Composable
private fun TuningSlider(
    label: String,
    readout: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    help: String,
    onChange: (Float) -> Unit
) {
    Column(Modifier.padding(vertical = 6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, fontSize = 14.sp, color = Color.White)
            Text(readout, fontSize = 14.sp, color = Color(0xFF7DD3FC))
        }
        Slider(value = value, onValueChange = onChange, valueRange = range)
        Text(help, fontSize = 11.sp, color = Color.White.copy(alpha = 0.45f))
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
            Text(label, fontSize = 14.sp, color = Color.White)
            Text(help, fontSize = 11.sp, color = Color.White.copy(alpha = 0.45f))
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
