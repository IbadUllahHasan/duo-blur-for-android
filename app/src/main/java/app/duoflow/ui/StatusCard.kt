package app.duoflow.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.duoflow.GlassRadii
import app.duoflow.GlassSurface
import app.duoflow.GlassSwitch
import app.duoflow.Tunables
import kotlin.math.roundToInt

@Composable
internal fun StatusCard(
    running: Boolean,
    effectActive: Boolean,
    deviationDeg: Float,
    tunables: Tunables,
    canDrawOverlays: Boolean,
    onToggle: () -> Unit,
    onRecalibrate: () -> Unit,
    onGrantOverlay: () -> Unit
) {
    val (label, dot) = when {
        effectActive -> "Effect active" to MaterialTheme.colorScheme.primary
        running -> "Watching for tilt" to Color(0xFF34C759) // Apple systemGreen
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
                GlassSwitch(
                    checked = running,
                    onCheckedChange = { onToggle() }
                )
            }

            if (!canDrawOverlays) {
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "\"Display over other apps\" is off — the effect can't draw without it.",
                        style = MaterialTheme.typography.bodySmall,
                        // Was a hardcoded #FFD79A — a pale cream picked for a
                        // dark card, effectively invisible on a light one.
                        // Found while auditing item 4's claim that the
                        // segmented control was hardcoded (it wasn't); this
                        // one actually was, and is the same defect class, so
                        // it is fixed here rather than left to be re-reported.
                        // Apple systemOrange resolves per theme instead.
                        // NOT isSystemInDarkTheme(): ThemeMode can pin Light
                        // while the system is in night mode, and this has to
                        // follow the *resolved* theme. colorScheme.background
                        // is pinned per theme, so its luminance is that signal
                        // without threading a new parameter or composition
                        // local through for one string.
                        color = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) {
                            Color(0xFFFF9F0A) // Apple systemOrange dark
                        } else {
                            Color(0xFFC2410C) // darkened orange — systemOrange itself is ~2.5:1 on white
                        },
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(onClick = onRecalibrate, enabled = running) { Text("Recalibrate") }
            }
        }
    }
}
