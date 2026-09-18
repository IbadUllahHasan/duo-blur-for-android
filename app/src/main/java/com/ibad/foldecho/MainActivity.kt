package com.ibad.foldecho

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
                    FoldEchoScreen()
                }
            }
        }
    }
}

/**
 * Recreates the LOOK of the iPhone Duo fold transition on a phone that has
 * no hinge to read. There is no second physical screen here, so "Screen A"
 * and "Screen B" are two full-screen layers that cross-fade and blur into
 * each other as `fold` moves from 0f (fully A) to 1f (fully B).
 *
 * The blur peaks at the midpoint (fold = 0.5) on a sine curve, which is
 * what gives the "dissolves as it crosses the hinge, reassembles on the
 * other side" look, instead of a flat linear cross-fade.
 */
@Composable
fun FoldEchoScreen() {
    val fold = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // Real Duo hardware triggers this off the hinge angle sensor. We don't
    // have one, so a fast rotation spike from the accelerometer stands in
    // for "the user just flipped the phone" — toggle-able, off by default.
    var accelTriggerEnabled by remember { mutableStateOf(false) }
    DisposableEffect(accelTriggerEnabled) {
        if (!accelTriggerEnabled) return@DisposableEffect onDispose {}

        val sensorManager = context.getSystemService(SensorManager::class.java)
        val sensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        var lastMagnitude = 0f

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]
                val magnitude = sqrt(x * x + y * y + z * z)
                val delta = abs(magnitude - lastMagnitude)
                lastMagnitude = magnitude

                // Tune against your own phone: a firm flip in the hand
                // should clear this, a phone resting on a desk should not.
                if (delta > 18f) {
                    val target = if (fold.value < 0.5f) 1f else 0f
                    scope.launch { fold.animateTo(target, animationSpec = tween(500)) }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        sensorManager?.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        onDispose { sensorManager?.unregisterListener(listener) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectHorizontalDragGestures { _, dragAmount ->
                    val delta = dragAmount / size.width.toFloat()
                    scope.launch {
                        fold.snapTo((fold.value + delta).coerceIn(0f, 1f))
                    }
                }
            }
    ) {
        val progress = fold.value
        val blurCurve = sin(progress.coerceIn(0f, 1f) * PI.toFloat())
        val maxBlur = 28.dp

        ScreenLayer(
            label = "Screen A",
            gradient = listOf(Color(0xFF1E3A8A), Color(0xFF3B82F6)),
            alpha = 1f - smoothstep(progress),
            blurRadius = maxBlur * blurCurve,
            scale = 1f - 0.06f * blurCurve
        )
        ScreenLayer(
            label = "Screen B",
            gradient = listOf(Color(0xFF7C2D92), Color(0xFFDB2777)),
            alpha = smoothstep(progress),
            blurRadius = maxBlur * blurCurve,
            scale = 1f - 0.06f * blurCurve
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Drag left/right to fold  ·  fold = ${"%.2f".format(progress)}",
                color = Color.White,
                fontSize = 13.sp
            )
            Spacer(Modifier.height(12.dp))
            Row {
                Button(onClick = {
                    val target = if (progress < 0.5f) 1f else 0f
                    scope.launch { fold.animateTo(target, animationSpec = tween(600)) }
                }) {
                    Text("Auto Flip")
                }
                Spacer(Modifier.width(12.dp))
                Button(onClick = { accelTriggerEnabled = !accelTriggerEnabled }) {
                    Text(if (accelTriggerEnabled) "Tilt-trigger: ON" else "Tilt-trigger: OFF")
                }
            }
        }
    }
}

@Composable
private fun ScreenLayer(
    label: String,
    gradient: List<Color>,
    alpha: Float,
    blurRadius: Dp,
    scale: Float
) {
    if (alpha <= 0.01f) return
    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                this.alpha = alpha
                scaleX = scale
                scaleY = scale
            }
            .blur(blurRadius)
            .background(Brush.linearGradient(gradient)),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = Color.White.copy(alpha = 0.9f), fontSize = 28.sp)
    }
}

/** Eases the crossfade so it isn't perfectly linear against the drag/animation. */
private fun smoothstep(x: Float): Float {
    val t = x.coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}
